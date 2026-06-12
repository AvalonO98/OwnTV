@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.owntv.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.player.OwnTVPlayer

/** Combined results of a global query (each list bounded). */
data class SearchResults(
    val channels: List<ChannelEntity> = emptyList(),
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
) {
    val isEmpty: Boolean get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

/** Phase 11 — cross-section search over a profile's channels, movies and series. */
class SearchViewModel(
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val historyDao: HistoryDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    val player: OwnTVPlayer,
) : ViewModel() {

    private data class Ctx(val profileId: Long, val sourceIds: List<Long>)
    private val ctx = MutableStateFlow(Ctx(-1L, emptyList()))

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    init {
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            ctx.value = Ctx(pid, sourceDao.sourceIdsForProfile(pid))
        }
    }

    val results: StateFlow<SearchResults> = _query
        .map { it.trim() }
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.length < 2) {
                flowOf(SearchResults())
            } else {
                flowOf(search(q))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun setQuery(q: String) { _query.value = q }

    private suspend fun search(q: String): SearchResults {
        val ids = ctx.value.sourceIds.ifEmpty { return SearchResults() }
        // Respect this profile's customizations: hidden channels never surface, renames are shown.
        val cust = customize.observe(ctx.value.profileId, MediaType.LIVE).first()
        return SearchResults(
            channels = channelDao.searchList(q, ids, LIMIT)
                .filter { CustomizeKeys.channel(it) !in cust.hiddenItems }
                .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch },
            movies = movieDao.searchList(q, ids, LIMIT),
            series = seriesDao.searchList(q, ids, LIMIT),
        )
    }

    fun playChannel(channel: ChannelEntity) {
        player.play(channel.streamUrl, title = channel.name, logoUrl = channel.logoUrl, isLive = true)
        record(MediaType.LIVE, channel.id)
    }

    fun playMovie(movie: MovieEntity) {
        player.play(movie.streamUrl, title = movie.name, year = movie.year?.toString(), isLive = false)
        record(MediaType.MOVIE, movie.id)
    }

    private fun record(type: MediaType, itemId: Long) {
        viewModelScope.launch {
            historyDao.record(WatchHistoryEntity(profileId = ctx.value.profileId, mediaType = type, itemId = itemId))
        }
    }

    private companion object {
        const val LIMIT = 40
    }
}
