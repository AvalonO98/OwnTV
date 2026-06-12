@file:OptIn(FlowPreview::class) // debounce

package tv.own.owntv.features.epg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.util.friendlySyncError
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.player.OwnTVPlayer

data class EpgUiState(
    /** All channels with guide data in the window; each row loads its own programmes lazily. */
    val channels: List<ChannelEntity> = emptyList(),
    val windowStart: Long = 0,
    val windowEnd: Long = 0,
    val now: Long = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val canRefresh: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    /** "N channels · M programmes" once a guide is stored — the visible proof the EPG URL works. */
    val stats: String? = null,
)

/**
 * Drives the EPG guide grid. Loads the active profile's EPG-capable channels and the programmes in a
 * rolling [GRID_HOURS] window from the DB, and can re-download the bulk XMLTV guide via [EpgRepository].
 */
class EpgViewModel(
    private val settings: SettingsRepository,
    private val sourceRepository: SourceRepository,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val epgRepository: EpgRepository,
    private val connectivity: ConnectivityObserver,
    private val customize: CustomizationStore,
    private val historyDao: HistoryDao,
    val player: OwnTVPlayer,
) : ViewModel() {

    private val _state = MutableStateFlow(EpgUiState())
    val state: StateFlow<EpgUiState> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // Per-row programme cache (epg key → programmes in the current window). Rows re-read it instantly
    // when scrolled back into view; cleared whenever the window/data reloads.
    private val rowCache = java.util.concurrent.ConcurrentHashMap<String, List<EpgProgrammeEntity>>()
    @Volatile private var loadedSourceIds: List<Long> = emptyList()

    /** Synchronous cache peek — lets a re-composed row render instantly without a loading flash. */
    fun cachedProgrammes(channel: ChannelEntity): List<EpgProgrammeEntity>? =
        channel.epgChannelId?.trim()?.lowercase()?.let { rowCache[it] }

    /** Lazily loads one row's programmes (indexed query + cache) as the row scrolls into view. */
    suspend fun programmesFor(channel: ChannelEntity): List<EpgProgrammeEntity> {
        val key = channel.epgChannelId?.trim()?.lowercase() ?: return emptyList()
        rowCache[key]?.let { return it }
        val s = _state.value
        val list = epgDao.programmesForChannel(loadedSourceIds, key, s.windowStart, s.windowEnd)
        rowCache[key] = list
        return list
    }

    init {
        // Re-filter the grid as the user types (DB-level, so it searches ALL guide channels, not
        // just the visible rows). drop(1): the screen triggers the initial load itself.
        _query
            .drop(1)
            .debounce(300)
            .distinctUntilChanged()
            .onEach { load() }
            .launchIn(viewModelScope)
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    /** The channel last tuned from the guide — the screen refocuses its row after fullscreen exits. */
    var lastTunedChannelId: Long? = null
        private set

    /** Tune to a channel from the guide (fullscreen playback + history, like the Live list). */
    fun play(channel: ChannelEntity) {
        lastTunedChannelId = channel.id
        player.play(channel.streamUrl, title = channel.name, logoUrl = channel.logoUrl, isLive = true)
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            if (pid >= 0) historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            val pid = settings.activeProfileId.first()
            val sources = if (pid < 0) emptyList() else sourceRepository.observeSources(pid).first()
            val sourceIds = sources.map { it.id }
            val canRefresh = sources.any { epgRepository.hasGuide(it) }

            if (sourceIds.isEmpty()) {
                _state.value = EpgUiState(loading = false, canRefresh = false, message = "Add a source to see the guide.")
                return@launch
            }

            val now = System.currentTimeMillis()
            val windowStart = now - (now % HALF_HOUR_MS) // align to the half hour
            val windowEnd = windowStart + GRID_HOURS * 60L * 60 * 1000

            // Respect customizations: hidden channels stay out of the guide, renames show.
            val cust = customize.observe(pid, MediaType.LIVE).first()
            // ALL channels that actually have programmes in the window (matched case-insensitively —
            // XMLTV ids often differ from the panel's epg_channel_id in case). No row cap: each row
            // loads its own programmes lazily, so memory stays flat regardless of channel count.
            val q = _query.value.trim()
            val channels = channelDao.channelsWithGuide(sourceIds, windowStart, windowEnd, q, MAX_CHANNELS)
                .filter { CustomizeKeys.channel(it) !in cust.hiddenItems }
                .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
            val stored = epgDao.countForSources(sourceIds)

            // New window/data → drop the per-row cache so rows re-query the fresh window.
            rowCache.clear()
            loadedSourceIds = sourceIds

            val message = when {
                stored == 0 && canRefresh -> "No guide downloaded yet. Press Refresh to fetch the EPG."
                stored == 0 -> "Your sources don't provide an EPG guide."
                channels.isEmpty() && q.isNotBlank() -> "No guide channels found for “$q”."
                channels.isEmpty() ->
                    "Guide data is stored, but its channel ids don't match your channels' EPG ids — " +
                        "this EPG feed may belong to a different provider lineup. (If you just updated " +
                        "OwnTV, press Refresh once to re-download the guide.)"
                else -> null
            }
            // Visible proof the EPG feed works: how much guide data is actually stored.
            val stats = if (stored > 0) {
                val guideChannels = epgDao.countGuideChannels(sourceIds)
                "Guide loaded: $guideChannels channels · $stored programmes"
            } else null

            _state.value = EpgUiState(
                channels = channels, windowStart = windowStart, windowEnd = windowEnd, now = now,
                loading = false, refreshing = false, canRefresh = canRefresh, message = message,
                stats = stats,
            )
        }
    }

    fun refresh() {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true, message = "Downloading guide…")
            val pid = settings.activeProfileId.first()
            val sources = if (pid < 0) emptyList() else sourceRepository.observeSources(pid).first()
            var ok = false
            var lastError: String? = null
            for (source in sources.filter { epgRepository.hasGuide(it) }) {
                runCatching { epgRepository.refresh(source) }
                    .onSuccess { ok = true }
                    .onFailure { lastError = it.message }
            }
            if (!ok && lastError != null) {
                _state.value = _state.value.copy(
                    refreshing = false,
                    isError = true,
                    message = friendlySyncError(lastError, connectivity.isOnlineNow()),
                )
            } else {
                load() // reloads from DB (clears refreshing)
            }
        }
    }

    companion object {
        const val GRID_HOURS = 24
        private const val HALF_HOUR_MS = 30L * 60 * 1000
        // Generous safety bound only (rows load lazily, so this is about the channel list itself).
        private const val MAX_CHANNELS = 20_000
    }
}
