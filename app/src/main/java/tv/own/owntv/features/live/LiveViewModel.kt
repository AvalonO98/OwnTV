@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.owntv.features.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.paging.filter
import androidx.paging.map
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.applyCustomizations
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.ui.components.OwnTVIcon

/** Layer-2 rail selection for Live TV. */
sealed interface LiveKey {
    data object Favorites : LiveKey
    data object History : LiveKey
    data object All : LiveKey
    data class Folder(val id: Long) : LiveKey
}

/** A rail entry. Favorites/History carry an [icon] (rendered instead of the abbreviation). */
data class LiveRailItem(val key: LiveKey, val abbr: String, val title: String, val icon: OwnTVIcon? = null)

/** Now-playing + up-next EPG for the focused channel (null entries when the guide is unavailable). */
data class EpgNowNext(val now: XtEpgEntry?, val next: XtEpgEntry?)

class LiveViewModel(
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val xtreamClient: XtreamClient,
    private val customize: CustomizationStore,
    val player: OwnTVPlayer,
) : ViewModel() {

    val livePreviewEnabled: StateFlow<Boolean> = settings.livePreviewEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** List ordering for this section (Playlist order vs A–Z), persisted in DataStore. */
    val sortMode: StateFlow<SettingsRepository.SortMode> = settings.sortLive
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.SortMode.PLAYLIST)

    fun toggleSort() {
        viewModelScope.launch {
            settings.setSortLive(
                if (sortMode.value == SettingsRepository.SortMode.PLAYLIST) SettingsRepository.SortMode.ALPHA
                else SettingsRepository.SortMode.PLAYLIST,
            )
        }
    }

    private val livePreviewAudio: StateFlow<Boolean> = settings.livePreviewAudio
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private data class Ctx(val profileId: Long, val sourceIds: List<Long>)

    private val ctx = MutableStateFlow(Ctx(-1L, emptyList()))

    private val _selected = MutableStateFlow<LiveKey>(LiveKey.All)
    val selectedKey: StateFlow<LiveKey> = _selected.asStateFlow()

    private val _search = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _search.asStateFlow()

    private val _previewChannel = MutableStateFlow<ChannelEntity?>(null)
    val previewChannel: StateFlow<ChannelEntity?> = _previewChannel.asStateFlow()

    private data class CachedEpg(val at: Long, val data: EpgNowNext)
    private val epgCache = HashMap<Long, CachedEpg>()

    /** Now/next for the focused channel — fetched (debounced) from the Xtream `get_short_epg` API. */
    val nowNext: StateFlow<EpgNowNext?> = _previewChannel
        .debounce(350)
        .distinctUntilChanged { a, b -> a?.id == b?.id }
        .mapLatest { ch -> ch?.let { loadEpg(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            ctx.value = Ctx(pid, sourceDao.sourceIdsForProfile(pid))
        }
    }

    /** This profile's hide/rename/reorder customizations for Live TV. */
    private val custom: StateFlow<SectionCustomizations> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(SectionCustomizations())
            else customize.observe(c.profileId, MediaType.LIVE)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SectionCustomizations())

    val railItems: StateFlow<List<LiveRailItem>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(defaultRail)
            else combine(categoryDao.observe(c.sourceIds, MediaType.LIVE), custom) { cats, cust ->
                defaultRail + cats.applyCustomizations(cust).map { (cat, name) ->
                    LiveRailItem(LiveKey.Folder(cat.id), abbreviate(name), name)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaultRail)

    val channels: Flow<PagingData<ChannelEntity>> = combine(
        _selected,
        ctx,
        _search.map { it.trim() }.debounce(300).distinctUntilChanged(),
        sortMode,
        custom,
    ) { key, c, query, sort, cust -> Args(key, c, query, sort, cust) }
        .flatMapLatest { (key, c, query, sort, cust) ->
            // Customizations are applied to each fresh PagingData inside the pager chain — a PagingData
            // that the UI already collected must never be re-transformed (Paging forbids re-collection,
            // which is why hiding a channel used to crash). A customization change re-creates the pager.
            Pager(PagingConfig(pageSize = 80, prefetchDistance = 40, initialLoadSize = 120, maxSize = 400)) {
                pagingSource(key, c, query, sort)
            }.flow.map { paging ->
                if (cust.hiddenItems.isEmpty() && cust.itemNames.isEmpty()) paging
                else paging
                    .filter { ch -> CustomizeKeys.channel(ch) !in cust.hiddenItems }
                    .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
            }
        }
        .cachedIn(viewModelScope)

    private data class Args(val key: LiveKey, val ctx: Ctx, val query: String, val sort: SettingsRepository.SortMode, val cust: SectionCustomizations)

    /** Hide the focused channel from all lists (undo via Settings → Customize → Hidden channels). */
    fun hideChannel(channel: ChannelEntity) {
        if (_previewChannel.value?.id == channel.id) stopPreview()
        viewModelScope.launch {
            customize.setItemHidden(ctx.value.profileId, MediaType.LIVE, CustomizeKeys.channel(channel), channel.name, true)
        }
    }

    /** Rename the channel for this profile (blank restores the provider's name). */
    fun renameChannel(channel: ChannelEntity, newName: String?) {
        viewModelScope.launch {
            customize.renameItem(ctx.value.profileId, MediaType.LIVE, CustomizeKeys.channel(channel), newName)
        }
    }

    val count: StateFlow<Int> = combine(_selected, ctx) { key, c -> key to c }
        .flatMapLatest { (key, c) -> countFlow(key, c) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val favoriteIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.LIVE) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val recentlyWatched: StateFlow<List<ChannelEntity>> = ctx
        .flatMapLatest { channelDao.recentlyWatched(it.profileId, 20) }
        .combine(custom) { list, cust ->
            list.filter { CustomizeKeys.channel(it) !in cust.hiddenItems }
                .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun select(key: LiveKey) {
        _selected.value = key
    }

    fun setSearchQuery(query: String) {
        _search.value = query
    }

    fun onChannelFocused(channel: ChannelEntity) {
        _previewChannel.value = channel
    }

    /** True when [channel] is already streaming healthily — then we only need to flip the mute. */
    private fun alreadyStreaming(channel: ChannelEntity): Boolean =
        player.currentMediaUrl == channel.streamUrl && player.error.value == null

    /** In-pane preview playback (no history) — triggered by the UI after the focus settles. */
    fun playPreview(channel: ChannelEntity) {
        // Same stream already running (e.g. back from fullscreen)? Just apply the preview mute —
        // reloading would open a second connection and trip strict providers' limits (HTTP 509).
        if (alreadyStreaming(channel)) {
            player.setMuted(!livePreviewAudio.value)
            return
        }
        player.play(channel.streamUrl, title = channel.name, logoUrl = channel.logoUrl, isLive = true, muted = !livePreviewAudio.value)
    }

    /** Explicit watch (e.g. going fullscreen): plays with sound and records history. */
    fun ensurePlaying(channel: ChannelEntity) {
        _previewChannel.value = channel
        // The preview is usually already streaming this exact channel — keep the connection and just
        // unmute, instead of reloading (which briefly doubles connections → 509 on 1-conn accounts).
        if (alreadyStreaming(channel)) {
            player.setMuted(false)
        } else {
            player.play(channel.streamUrl, title = channel.name, logoUrl = channel.logoUrl, isLive = true, muted = false)
        }
        viewModelScope.launch {
            historyDao.record(WatchHistoryEntity(profileId = ctx.value.profileId, mediaType = MediaType.LIVE, itemId = channel.id))
        }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            val pid = ctx.value.profileId
            if (favoriteIds.value.contains(channel.id)) {
                favoriteDao.remove(pid, MediaType.LIVE, channel.id)
            } else {
                favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
            }
        }
    }

    fun stopPreview() {
        player.stop()
        _previewChannel.value = null
    }

    /** Fetch now/next for [ch] (Xtream sources only), cached ~5 min to avoid re-hitting the API. */
    private suspend fun loadEpg(ch: ChannelEntity): EpgNowNext? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        epgCache[ch.id]?.takeIf { now - it.at < 5 * 60_000 }?.let { return@withContext it.data }
        val streamId = ch.remoteId ?: return@withContext null
        val source = sourceDao.getById(ch.sourceId) ?: return@withContext null
        if (source.type != SourceType.XTREAM) return@withContext null
        val entries = runCatching { xtreamClient.getShortEpg(source, streamId, limit = 6) }
            .getOrNull().orEmpty()
        val current = entries.firstOrNull { it.startMs <= now && it.stopMs > now }
            ?: entries.firstOrNull { it.stopMs > now }
        val next = entries.firstOrNull { it.startMs > (current?.startMs ?: now) }
        val result = EpgNowNext(current, next)
        epgCache[ch.id] = CachedEpg(now, result)
        result
    }

    private fun pagingSource(key: LiveKey, c: Ctx, query: String, sort: SettingsRepository.SortMode): PagingSource<Int, ChannelEntity> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        val playlist = sort == SettingsRepository.SortMode.PLAYLIST
        return if (query.isBlank()) {
            when (key) {
                LiveKey.All -> if (playlist) channelDao.pagingAllOriginal(ids) else channelDao.pagingAll(ids)
                LiveKey.Favorites -> channelDao.pagingFavorites(c.profileId)
                LiveKey.History -> channelDao.pagingHistory(c.profileId)
                is LiveKey.Folder -> if (playlist) channelDao.pagingByCategory(key.id) else channelDao.pagingByCategoryAlpha(key.id)
            }
        } else {
            when (key) {
                LiveKey.All -> channelDao.searchAll(query, ids)
                LiveKey.Favorites -> channelDao.searchFavorites(query, c.profileId)
                LiveKey.History -> channelDao.searchHistory(query, c.profileId)
                is LiveKey.Folder -> channelDao.searchInCategory(query, key.id)
            }
        }
    }

    private fun countFlow(key: LiveKey, c: Ctx): Flow<Int> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        return when (key) {
            LiveKey.All -> channelDao.countAll(ids)
            LiveKey.Favorites -> channelDao.countFavorites(c.profileId)
            LiveKey.History -> historyDao.count(c.profileId, MediaType.LIVE)
            is LiveKey.Folder -> channelDao.countByCategory(key.id)
        }
    }

    private companion object {
        val defaultRail = listOf(
            LiveRailItem(LiveKey.Favorites, "FAV", "Favorites", OwnTVIcon.STAR),
            LiveRailItem(LiveKey.History, "HIS", "History", OwnTVIcon.HISTORY),
            LiveRailItem(LiveKey.All, "ALL", "All Channels"),
        )
    }
}

/** Short 2–3 char rail label from a category name. */
private fun abbreviate(name: String): String {
    val cleaned = name.filter { it.isLetterOrDigit() || it == ' ' }.trim()
    val words = cleaned.split(' ').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "•"
        words.size == 1 -> words[0].take(3).uppercase()
        else -> words.take(3).joinToString("") { it.first().uppercase() }
    }
}
