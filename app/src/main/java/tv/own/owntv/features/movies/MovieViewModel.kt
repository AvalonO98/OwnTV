@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.owntv.features.movies

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.applyCustomizations
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProgressDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.PlaybackProgressEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.features.live.LiveRailItem
import tv.own.owntv.features.live.LiveKey
import tv.own.owntv.core.download.DownloadManager
import tv.own.owntv.core.storage.StorageAccess
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.ui.components.OwnTVIcon

class MovieViewModel(
    private val movieDao: MovieDao,
    private val categoryDao: CategoryDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val progressDao: ProgressDao,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val player: OwnTVPlayer,
    private val downloadManager: DownloadManager,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val contentOrderDao: ContentOrderDao,
    private val metadata: tv.own.owntv.core.metadata.MetadataRepository,
) : ViewModel() {

    data class MovieMoveState(val items: List<MovieEntity>, val activeIndex: Int, val contextKey: String)
    private val _moveState = MutableStateFlow<MovieMoveState?>(null)
    val moveState: StateFlow<MovieMoveState?> = _moveState.asStateFlow()

    private data class Ctx(val profileId: Long, val sourceIds: List<Long>)
    // Observe the active profile's sources reactively so adding/removing a playlist refreshes Movies
    // immediately (was read once at startup, so a new playlist showed nothing until app restart).
    private val ctx: StateFlow<Ctx> = activeProfileSources(settings, sourceDao)
        .map { aps -> Ctx(aps.profileId, aps.sourceIds) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(-1L, emptyList()))

    private val folderContextKeys: StateFlow<Map<Long, String>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptyMap())
            else categoryDao.observe(c.sourceIds, MediaType.MOVIE).map { cats ->
                cats.associateBy({ it.id }, { CustomizeKeys.category(it) })
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** List ordering for this section (Provider order vs A–Z), persisted in DataStore. */
    val sortMode: StateFlow<SettingsRepository.SortMode> = settings.sortMovies
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.SortMode.ALPHA)

    fun toggleSort() {
        viewModelScope.launch {
            // Cycle Provider → A–Z → Rating → Provider.
            settings.setSortMovies(
                when (sortMode.value) {
                    SettingsRepository.SortMode.PLAYLIST -> SettingsRepository.SortMode.ALPHA
                    SettingsRepository.SortMode.ALPHA -> SettingsRepository.SortMode.RATING
                    SettingsRepository.SortMode.RATING -> SettingsRepository.SortMode.PLAYLIST
                },
            )
        }
    }

    val viewMode: StateFlow<SettingsRepository.VodViewMode> = settings.vodViewMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.VodViewMode.GRID)

    fun toggleViewMode() {
        viewModelScope.launch {
            settings.setVodViewMode(
                if (viewMode.value == SettingsRepository.VodViewMode.GRID) SettingsRepository.VodViewMode.LIST
                else SettingsRepository.VodViewMode.GRID,
            )
        }
    }

    private val _selected = MutableStateFlow<LiveKey>(LiveKey.All)
    val selectedKey: StateFlow<LiveKey> = _selected.asStateFlow()

    // Bumped after a favourite/history mutation so the pager rebuilds its (manual, non-reactive)
    // PagingSource. Without this, unfavouriting on the Favorites category (or removing from History)
    // can leave the removed movie in the paged snapshot, which breaks focus restore (the stale row
    // disposes under focus). Behaviour is intermittent because Room's invalidation timing varies.
    private val _listRefresh = MutableStateFlow(0)
    private fun refreshList() { _listRefresh.value++ }

    private val _search = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _search.asStateFlow()

    private val _selectedMovie = MutableStateFlow<MovieEntity?>(null)
    val selectedMovie: StateFlow<MovieEntity?> = _selectedMovie.asStateFlow()

    /**
     * On-demand TMDB enrichment for the focused movie (plan §7.2: detail screens resolve lazily). Debounced
     * so scrolling fast doesn't fire a lookup per card; cached in Room so a second focus is instant. Null
     * when enrichment is off or no confident match — the UI then shows pure provider data (§7.1).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedMovieMeta: StateFlow<MovieMeta?> = _selectedMovie
        .distinctUntilChanged { a, b -> a?.id == b?.id }
        .debounce(350)
        .mapLatest { m ->
            if (m == null) null
            else MovieMeta(m.id, runCatching { metadata.resolveMovie(m) }.getOrNull())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** TMDB metadata tagged with the movie id it was resolved for, so the UI never shows stale meta on a
     *  different card during the debounce window. [cache] is null while resolving or on no match. */
    data class MovieMeta(val movieId: Long, val cache: tv.own.owntv.core.database.entity.MetadataCacheEntity?)

    /** Source mode (plan §4.1) — the detail pane uses it to flip provider/TMDB field precedence. */
    val metadataMode: StateFlow<tv.own.owntv.core.metadata.MetadataMode> = settings.metadataMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.metadata.MetadataMode.PROVIDER_PLUS_TMDB)

    private var playingMovie: MovieEntity? = null

    init {
        // Periodically persist resume position for the movie currently playing.
        viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                saveProgressNow()
            }
        }
    }

    val railItems: StateFlow<List<LiveRailItem>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(defaultRail)
            else combine(
                categoryDao.observe(c.sourceIds, MediaType.MOVIE),
                customize.observe(c.profileId, MediaType.MOVIE),
            ) { cats, cust ->
                defaultRail + cats.applyCustomizations(cust).map { (cat, name) ->
                    LiveRailItem(LiveKey.Folder(cat.id), name.take(3).uppercase(), name)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaultRail)

    val movies: Flow<PagingData<MovieEntity>> = combine(
        _selected, ctx, _search.map { it.trim() }.debounce(300).distinctUntilChanged(), sortMode, _listRefresh,
    ) { key, c, query, sort, _ -> Args(key, c, query, sort) }
        .flatMapLatest { (key, c, query, sort) ->
            Pager(PagingConfig(pageSize = 60, prefetchDistance = 30, initialLoadSize = 90, maxSize = 300)) {
                pagingSource(key, c, query, sort)
            }.flow
        }
        .cachedIn(viewModelScope)

    private data class Args(val key: LiveKey, val ctx: Ctx, val query: String, val sort: SettingsRepository.SortMode)

    val count: StateFlow<Int> = combine(_selected, ctx) { key, c -> key to c }
        .flatMapLatest { (key, c) -> countFlow(key, c) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val favoriteIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.MOVIE) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val selectedProgress: StateFlow<PlaybackProgressEntity?> = combine(_selectedMovie, ctx) { m, c -> m to c }
        .flatMapLatest { (m, c) ->
            if (m == null) flowOf(null) else progressDao.observe(c.profileId, MediaType.MOVIE, m.id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun select(key: LiveKey) { _selected.value = key }
    fun setSearchQuery(query: String) { _search.value = query }
    fun onMovieFocused(movie: MovieEntity) { _selectedMovie.value = movie }

    /** The user's resume preference (Always / Ask / Never) — the screen drives the prompt. */
    val resumeMode: StateFlow<SettingsRepository.ResumeMode> = settings.resumeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.ResumeMode.ASK)

    /** Saved resume position for [movie] (0 when none) — used by the screen to decide the prompt. */
    suspend fun savedPositionMs(movie: MovieEntity): Long =
        currentProfileId()?.let { progressDao.get(it, MediaType.MOVIE, movie.id)?.positionMs ?: 0 } ?: 0

    fun play(movie: MovieEntity, startPositionMs: Long = 0) {
        viewModelScope.launch {
            val pid = currentProfileId()
            val sourceUa = sourceDao.getById(movie.sourceId)?.userAgent
            Log.d(TAG, "play movieId=${movie.id} profile=$pid startPositionMs=$startPositionMs")
            player.play(
                movie.streamUrl,
                title = movie.name,
                year = movie.year?.toString(),
                isLive = false,
                startPositionMs = startPositionMs,
                userAgent = sourceUa,
            )
            playingMovie = movie
            if (pid != null) {
                runCatching {
                    historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.MOVIE, itemId = movie.id))
                }.onFailure { t ->
                    Log.w(TAG, "play history record failed movieId=${movie.id} profile=$pid", t)
                }
            }
        }
    }

    fun playById(movieId: Long, startPositionMs: Long = 0) {
        viewModelScope.launch {
            val movie = movieDao.getById(movieId) ?: return@launch
            play(movie, startPositionMs)
        }
    }

    suspend fun playByIdAsync(movieId: Long, startPositionMs: Long = 0): Boolean {
        val movie = movieDao.getById(movieId) ?: return false
        play(movie, startPositionMs)
        return true
    }

    /** Download states for the currently visible movies, keyed by movie id. */
    val downloadStates: StateFlow<Map<Long, DownloadEntity>> = ctx
        .flatMapLatest { c -> if (c.profileId < 0) flowOf(emptyList()) else downloadManager.observe(c.profileId) }
        .map { list -> list.filter { it.mediaType == MediaType.MOVIE }.associateBy { it.itemId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun download(movie: MovieEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            downloadManager.enqueue(
                profileId = pid,
                mediaType = MediaType.MOVIE,
                itemId = movie.id,
                title = movie.name,
                posterUrl = movie.posterUrl,
                streamUrl = movie.streamUrl,
                relativeDir = "Movies",
                fileName = "${StorageAccess.sanitize(movie.name)}.${movie.containerExt ?: StorageAccess.extOf(movie.streamUrl)}",
            )
        }
    }

    fun toggleFavorite(movie: MovieEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (favoriteIds.value.contains(movie.id)) favoriteDao.remove(pid, MediaType.MOVIE, movie.id)
            else favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.MOVIE, itemId = movie.id))
            refreshList() // the Favorites category uses a manual PagingSource — force a rebuild
        }
    }

    /** Persist the resume position if the player is actually playing the tracked movie. */
    fun saveProgressNow() {
        val m = playingMovie ?: return
        if (player.currentMediaUrl != m.streamUrl || !player.isPlaying.value) return
        val pos = player.position.value
        val dur = player.duration.value
        if (pos > 0 && dur > 0) {
            viewModelScope.launch {
                val pid = currentProfileId() ?: return@launch
                Log.d(TAG, "saveProgressNow movieId=${m.id} profile=$pid positionMs=$pos durationMs=$dur")
                runCatching {
                    progressDao.save(
                        PlaybackProgressEntity(profileId = pid, mediaType = MediaType.MOVIE, itemId = m.id, positionMs = pos, durationMs = dur),
                    )
                }.onFailure { t ->
                    Log.w(TAG, "saveProgressNow progress save failed movieId=${m.id} profile=$pid", t)
                }
                launcherIntegrationRepository.publishMovieProgress(pid, m.id, pos, dur)
            }
        }
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    fun enterMoveMode(movie: MovieEntity, key: LiveKey) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            val contextKey = when (key) {
                is LiveKey.Folder -> folderContextKeys.value[key.id] ?: return@launch
                LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                else -> return@launch
            }
            val items = when (key) {
                is LiveKey.Folder -> movieDao.snapshotByCategoryManual(key.id, pid, contextKey, 5000)
                LiveKey.Favorites -> movieDao.snapshotFavoritesManual(pid, contextKey, ctx.value.sourceIds.ifEmpty { listOf(-1L) }, 5000)
                else -> return@launch
            }
            val idx = items.indexOfFirst { it.id == movie.id }
            if (idx < 0) return@launch
            _moveState.value = MovieMoveState(items, idx, contextKey)
            settings.setSortMovies(SettingsRepository.SortMode.PLAYLIST)
        }
    }

    fun moveUp() {
        val s = _moveState.value ?: return
        if (s.activeIndex == 0) return
        val list = s.items.toMutableList()
        val i = s.activeIndex
        list[i - 1] = s.items[i]; list[i] = s.items[i - 1]
        _moveState.value = s.copy(items = list, activeIndex = i - 1)
    }

    fun moveDown() {
        val s = _moveState.value ?: return
        if (s.activeIndex == s.items.size - 1) return
        val list = s.items.toMutableList()
        val i = s.activeIndex
        list[i + 1] = s.items[i]; list[i] = s.items[i + 1]
        _moveState.value = s.copy(items = list, activeIndex = i + 1)
    }

    fun commitMove() {
        val s = _moveState.value ?: return
        _moveState.value = null
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            contentOrderDao.replaceContext(
                profileId = pid,
                type = MediaType.MOVIE,
                contextKey = s.contextKey,
                rows = s.items.mapIndexed { i, m ->
                    ContentOrderEntity(profileId = pid, mediaType = MediaType.MOVIE, contextKey = s.contextKey, itemId = m.id, position = i)
                },
            )
        }
    }

    fun cancelMove() { _moveState.value = null }

    fun removeFromHistory(movieId: Long) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            historyDao.remove(pid, MediaType.MOVIE, movieId)
            progressDao.clear(pid, MediaType.MOVIE, movieId)
            refreshList() // the History category uses a manual PagingSource — force a rebuild
        }
    }

    private fun pagingSource(key: LiveKey, c: Ctx, query: String, sort: SettingsRepository.SortMode): PagingSource<Int, MovieEntity> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        val playlist = sort == SettingsRepository.SortMode.PLAYLIST
        val rating = sort == SettingsRepository.SortMode.RATING
        return if (query.isBlank()) when (key) {
            LiveKey.All -> when {
                rating -> movieDao.pagingAllRating(ids)
                playlist -> movieDao.pagingAllOriginal(ids)
                else -> movieDao.pagingAll(ids)
            }
            LiveKey.Favorites -> movieDao.pagingFavoritesManual(c.profileId, ContentOrderEntity.FAV_CONTEXT, ids)
            LiveKey.History -> movieDao.pagingHistory(c.profileId, ids)
            is LiveKey.Folder ->
                if (rating) movieDao.pagingByCategoryRating(key.id)
                else movieDao.pagingByCategoryManual(key.id, c.profileId, folderContextKeys.value[key.id] ?: "")
        } else when (key) {
            LiveKey.All -> movieDao.searchAll(query, ids)
            LiveKey.Favorites -> movieDao.searchFavorites(query, c.profileId, ids)
            LiveKey.History -> movieDao.searchHistory(query, c.profileId, ids)
            is LiveKey.Folder -> movieDao.searchInCategory(query, key.id)
        }
    }

    private fun countFlow(key: LiveKey, c: Ctx): Flow<Int> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        return when (key) {
            LiveKey.All -> movieDao.countAll(ids)
            LiveKey.Favorites -> movieDao.countFavorites(c.profileId, ids)
            LiveKey.History -> movieDao.countHistory(c.profileId, ids)
            is LiveKey.Folder -> movieDao.countByCategory(key.id)
        }
    }

    private companion object {
        const val TAG = "OwnTVHome"
        val defaultRail = listOf(
            LiveRailItem(LiveKey.Favorites, "FAV", "Favorites", OwnTVIcon.STAR),
            LiveRailItem(LiveKey.History, "HIS", "History", OwnTVIcon.HISTORY),
            LiveRailItem(LiveKey.All, "ALL", "All Movies"),
        )
    }
}
