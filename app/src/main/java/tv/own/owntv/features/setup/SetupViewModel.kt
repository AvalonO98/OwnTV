package tv.own.owntv.features.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.database.entity.ProfileSourceCrossRef
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.sync.ImportStage
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.SyncResult
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.core.util.Pin
import tv.own.owntv.core.util.friendlySyncError
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.features.settings.data.SettingsRepository
import java.io.File

/**
 * Drives onboarding for a profile (first-run and "add profile"): create the profile, then add content
 * (new source, link an existing unlocked profile's playlists, restore a backup, or skip). The new
 * profile is only made active on [finish], so the wizard stays put until the user completes it.
 */
class SetupViewModel(
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val sourceRepository: SourceRepository,
    private val backup: BackupManager,
    private val settings: SettingsRepository,
    private val connectivity: ConnectivityObserver,
    private val importFinalizer: tv.own.owntv.core.sync.ImportFinalizer,
    private val epgRepository: tv.own.owntv.core.repository.EpgRepository,
    private val epgSourceStore: tv.own.owntv.core.epg.EpgSourceStore,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val catalogSyncScheduler: CatalogSyncScheduler,
) : ViewModel() {

    // Semi-auto EPG: after the first playlist imports, offer a one-tap guide sync (with a live count) if it
    // has a guide feed.
    private var pendingEpgSource: SourceEntity? = null
    private val _epgSync = MutableStateFlow<tv.own.owntv.features.settings.EpgSyncUi>(tv.own.owntv.features.settings.EpgSyncUi.Hidden)
    val epgSync: StateFlow<tv.own.owntv.features.settings.EpgSyncUi> = _epgSync.asStateFlow()

    fun syncPendingEpg() {
        val src = pendingEpgSource ?: return
        viewModelScope.launch {
            tv.own.owntv.features.settings.runSemiAutoEpgSync(src, epgRepository, epgSourceStore) { _epgSync.value = it }
        }
    }

    fun dismissPendingEpg() { pendingEpgSource = null; _epgSync.value = tv.own.owntv.features.settings.EpgSyncUi.Hidden }

    sealed interface ImportState {
        data object Idle : ImportState
        data object Running : ImportState
        /** Per-type breakdown (incl. EPG) shown on the onboarding "All set" screen. */
        data class Success(val summary: String) : ImportState
        data class Failed(val message: String) : ImportState
    }

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val _progress = MutableStateFlow<ImportStage?>(null)
    val progress: StateFlow<ImportStage?> = _progress.asStateFlow()

    private var createdProfileId = -1L
    private var importJob: Job? = null

    /** Creates the profile (not active yet); the rest of onboarding attaches content to it. */
    fun createProfile(name: String, avatarId: Int, isKids: Boolean, pin: String?, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            createdProfileId = profileDao.insert(
                ProfileEntity(
                    name = name.ifBlank { "Profile" },
                    avatarColor = 0,
                    avatarId = avatarId,
                    isKids = isKids,
                    pinHash = pin?.takeIf { it.isNotBlank() }?.let { Pin.hash(it) },
                ),
            )
            onCreated(createdProfileId)
        }
    }

    fun startXtream(
        name: String,
        server: String,
        username: String,
        password: String,
        userAgent: String = "",
        epgUrl: String = "",
        refreshOnStart: Boolean = false,
        syncLive: Boolean = true,
        syncMovies: Boolean = true,
        syncSeries: Boolean = true,
    ) {
        val priority = SyncContentTypes(syncLive, syncMovies, syncSeries)
        runImport(refreshOnStart, priority, enqueueRemainder = true) { profileId ->
            sourceRepository.addXtreamSource(
                profileId = profileId,
                name = name.ifBlank { "My IPTV" },
                serverUrl = server.trim(),
                username = username.trim(),
                password = password,
                userAgent = userAgent.trim().takeIf { it.isNotBlank() },
                epgUrl = epgUrl.trim().takeIf { it.isNotBlank() },
            )
        }
    }

    fun startM3u(name: String, url: String, userAgent: String = "", epgUrl: String = "", refreshOnStart: Boolean = false) =
        runImport(refreshOnStart) { profileId ->
            sourceRepository.addM3uSource(
                profileId = profileId,
                name = name.ifBlank { "My Playlist" },
                url = url.trim(),
                userAgent = userAgent.trim().takeIf { it.isNotBlank() },
                epgUrl = epgUrl.trim().takeIf { it.isNotBlank() },
            )
        }

    private fun runImport(
        refreshOnStart: Boolean = false,
        contentTypes: SyncContentTypes = SyncContentTypes(),
        enqueueRemainder: Boolean = false,
        addSource: suspend (Long) -> SourceEntity,
    ) {
        importJob?.cancel()
        val job = viewModelScope.launch {
            _state.value = ImportState.Running
            _progress.value = null
            var source: SourceEntity? = null
            try {
                val profileId = createdProfileId.takeIf { it > 0 } ?: ensureFallbackProfile()
                source = addSource(profileId)
                settings.setSourceRefresh(source.id, refreshOnStart)
                when (val result = sourceRepository.sync(source, onProgress = { _progress.value = it }, contentTypes = contentTypes)) {
                    is SyncResult.Success -> {
                        // Just the playlist content — EPG is added separately (Settings → EPG sources).
                        val counts = importFinalizer.finalize(source)
                        runCatching { launcherIntegrationRepository.refreshProfile(profileId) }
                        if (enqueueRemainder) enqueueRemainderSync(source, contentTypes)
                        _state.value = ImportState.Success(counts.summary(includeEpg = false).withWarnings(result))
                        if (epgRepository.guideUrl(source) != null) {
                            pendingEpgSource = source
                            _epgSync.value = tv.own.owntv.features.settings.EpgSyncUi.Ask(source.name)
                        }
                    }
                    is SyncResult.Failed -> {
                        cleanupFailedAdd(source)
                        _state.value = ImportState.Failed(friendlySyncError(result.message, connectivity.isOnlineNow()))
                    }
                    SyncResult.Cancelled -> {
                        cleanupFailedAdd(source)
                        _state.value = ImportState.Idle
                    }
                }
            } catch (c: CancellationException) {
                cleanupFailedAdd(source)
                _state.value = ImportState.Idle
                _progress.value = null
                throw c
            } catch (e: Exception) {
                cleanupFailedAdd(source)
                _state.value = ImportState.Failed(friendlySyncError(e.message, connectivity.isOnlineNow()))
            }
        }
        importJob = job
        job.invokeOnCompletion { if (importJob == job) importJob = null }
    }

    private fun enqueueRemainderSync(source: SourceEntity, priority: SyncContentTypes) {
        val remainder = SyncContentTypes().remainderAfter(priority)
        if (remainder.hasAny) {
            catalogSyncScheduler.enqueueSync(source.id, reason = "add_remainder", contentTypes = remainder)
        }
    }

    /** Playlists belonging to unlocked (no-PIN) profiles that aren't already on the new profile. */
    suspend fun availableExistingSources(): List<SourceEntity> {
        val unlocked = profileDao.getAllOnce().filter { it.pinHash == null && it.id != createdProfileId }.map { it.id }.toSet()
        if (unlocked.isEmpty()) return emptyList()
        val links = sourceDao.allLinks()
        val fromUnlocked = links.filter { it.profileId in unlocked }.map { it.sourceId }.toSet()
        val alreadyMine = links.filter { it.profileId == createdProfileId }.map { it.sourceId }.toSet()
        val wanted = fromUnlocked - alreadyMine
        return sourceDao.getAllOnce().filter { it.id in wanted }
    }

    /**
     * Link the chosen existing sources to the new profile (shared content, separate favorites/history),
     * then re-sync each one so its catalog is fresh — exactly like adding a brand-new source. Drives the
     * same [state]/[progress] as [runImport], so the wizard can show the import screen.
     */
    fun linkExisting(sourceIds: Set<Long>) {
        importJob?.cancel()
        val job = viewModelScope.launch {
            _state.value = ImportState.Running
            _progress.value = null
            try {
                val pid = createdProfileId.takeIf { it > 0 } ?: ensureFallbackProfile()
                sourceIds.forEach { sourceDao.link(ProfileSourceCrossRef(profileId = pid, sourceId = it)) }
                val sources = sourceDao.getAllOnce().filter { it.id in sourceIds }
                var total = tv.own.owntv.core.sync.SyncCounts(0, 0, 0, 0)
                var failure: String? = null
                val warnings = mutableListOf<String>()
                for (source in sources) {
                    when (val result = sourceRepository.sync(source, onProgress = { _progress.value = it })) {
                        is SyncResult.Success -> {
                            val c = importFinalizer.finalize(source)
                            total = tv.own.owntv.core.sync.SyncCounts(total.channels + c.channels, total.movies + c.movies, total.series + c.series, total.epg + c.epg)
                            result.warningSummary()?.let { warnings.add(it) }
                        }
                        is SyncResult.Failed -> failure = result.message
                        SyncResult.Cancelled -> {}
                    }
                }
                runCatching { launcherIntegrationRepository.refreshProfile(pid) }
                val summary = listOf(total.summary(includeEpg = true), *warnings.toTypedArray()).joinToString("\n")
                _state.value = failure?.let { ImportState.Failed(friendlySyncError(it, connectivity.isOnlineNow())) } ?: ImportState.Success(summary)
            } catch (c: CancellationException) {
                _state.value = ImportState.Idle
                _progress.value = null
                throw c
            } catch (e: Exception) {
                _state.value = ImportState.Failed(friendlySyncError(e.message, connectivity.isOnlineNow()))
            }
        }
        importJob = job
        job.invokeOnCompletion { if (importJob == job) importJob = null }
    }

    /** Restore everything from a backup file (replaces profiles & sources, then activates one). */
    fun importBackup(file: File, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.value = ImportState.Running
            backup.import(file).fold(
                onSuccess = { _state.value = ImportState.Success("Restored $it items. Re-sync your sources to load content."); onDone() },
                onFailure = { _state.value = ImportState.Failed(it.message ?: "Restore failed") },
            )
        }
    }

    private suspend fun ensureFallbackProfile(): Long {
        if (createdProfileId > 0) return createdProfileId
        createdProfileId = profileDao.insert(ProfileEntity(name = "Profile", avatarColor = 0, avatarId = 0))
        return createdProfileId
    }

    fun reset() {
        _state.value = ImportState.Idle
        _progress.value = null
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _state.value = ImportState.Idle
        _progress.value = null
    }

    /** Completes onboarding → makes the new profile active, routing the app into the shell. */
    fun finish(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            if (createdProfileId > 0) settings.setActiveProfile(createdProfileId)
            onDone()
        }
    }

    private suspend fun cleanupFailedAdd(source: SourceEntity?) {
        if (source == null) return
        withContext(NonCancellable) {
            catalogSyncScheduler.cancelSync(source.id)
            runCatching { sourceRepository.deleteSource(source) }
            runCatching { settings.setSourceRefresh(source.id, false) }
        }
    }

    private fun String.withWarnings(result: SyncResult.Success): String =
        result.warningSummary()?.let { "$this\n$it" } ?: this
}
