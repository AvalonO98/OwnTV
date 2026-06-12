package tv.own.owntv.features.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.model.DownloadStatus
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.features.shell.components.CategoryRail
import tv.own.owntv.features.shell.components.PreviewPane
import tv.own.owntv.features.shell.components.RailCategory
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.PosterCard
import tv.own.owntv.ui.components.ProgressRing
import tv.own.owntv.ui.components.ResumeDialog
import androidx.compose.foundation.layout.width
import tv.own.owntv.ui.components.SearchBar
import tv.own.owntv.ui.components.SortChip
import tv.own.owntv.ui.components.formatCount
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.OwnTVTheme

@Composable
fun SeriesScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: SeriesViewModel = koinViewModel()
    val openedSeries by vm.openedSeries.collectAsStateWithLifecycle()

    // Track leaving a show so the grid can put focus back on the poster you came from (the episode
    // view that held focus is unmounted on Back — focus would otherwise die and land on the sidebar).
    var returnFromShow by remember { mutableStateOf(false) }
    LaunchedEffect(openedSeries) { if (openedSeries != null) returnFromShow = true }

    if (openedSeries != null) {
        EpisodeView(
            series = openedSeries!!,
            vm = vm,
            onFullscreen = onFullscreen,
            onChildFocused = onChildFocused,
            restoreFocus = restoreFocus,
            onRestored = onRestored,
            modifier = modifier,
        )
    } else {
        // Not in a show → nothing episode-specific to restore; clear the flag so it doesn't linger.
        if (restoreFocus) onRestored()
        SeriesGrid(
            vm = vm,
            onChildFocused = onChildFocused,
            restoreSelected = returnFromShow,
            onRestoredSelected = { returnFromShow = false },
            modifier = modifier,
        )
    }
}

@Composable
private fun SeriesGrid(
    vm: SeriesViewModel,
    onChildFocused: () -> Unit,
    restoreSelected: Boolean = false,
    onRestoredSelected: () -> Unit = {},
    modifier: Modifier,
) {
    val railItems by vm.railItems.collectAsStateWithLifecycle()
    val selectedKey by vm.selectedKey.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()
    val selectedSeries by vm.selectedSeries.collectAsStateWithLifecycle()
    val series = vm.series.collectAsLazyPagingItems()

    val selectedIndex = railItems.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val selectedItem = railItems.getOrNull(selectedIndex)
    val gridSelFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val firstItemFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    // Back from a show's episodes: refocus the poster you opened (the pane's onEnter prefers
    // gridSelFocus, so even when this request is intercepted it lands on the same poster).
    LaunchedEffect(restoreSelected, series.itemCount) {
        if (restoreSelected && series.itemCount > 0) {
            kotlinx.coroutines.delay(80)
            if (runCatching { gridSelFocus.requestFocus() }.isFailure) {
                runCatching { firstItemFocus.requestFocus() }
            }
            onRestoredSelected()
        }
    }

    Row(modifier = modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }) {
        CategoryRail(
            categories = railItems.map { RailCategory(it.abbr, it.title, it.icon) },
            selectedIndex = selectedIndex,
            onSelect = { idx -> railItems.getOrNull(idx)?.let { vm.select(it.key) } },
        )

        Column(
            modifier = Modifier
                .weight(1.5f)
                .fillMaxSize()
                // Entering this pane must land on a poster, never the search bar: prefer the
                // last-focused series, else the first one. onEnter fires only for directional entry
                // from outside (internal moves don't re-trigger it).
                .focusProperties {
                    onEnter = {
                        if (runCatching { gridSelFocus.requestFocus() }.isFailure) {
                            runCatching { firstItemFocus.requestFocus() }
                        }
                    }
                }
                .focusGroup()
                .padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingV),
        ) {
            Text("Series / ${selectedItem?.title ?: "All"}", style = MaterialTheme.typography.headlineLarge, color = OwnTVTheme.colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("${selectedItem?.abbr ?: "ALL"} (${formatCount(count)} series)", style = MaterialTheme.typography.titleMedium, color = OwnTVTheme.colors.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBar(query = searchQuery, onQueryChange = vm::setSearchQuery, placeholder = "Search ${selectedItem?.title ?: "series"}…", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                SortChip(mode = sortMode, onToggle = vm::toggleSort, playlistLabel = "Provider")
            }
            Spacer(Modifier.height(14.dp))

            if (series.itemCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isNotBlank()) "No series found for “${searchQuery.trim()}”" else "No series here.",
                        style = MaterialTheme.typography.bodyLarge, color = OwnTVTheme.colors.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(series.itemCount) { index ->
                        val s = series[index]
                        if (s != null) {
                            PosterCard(
                                posterUrl = s.posterUrl,
                                title = s.name,
                                rating = s.rating,
                                isFavorite = favoriteIds.contains(s.id),
                                modifier = when {
                                    s.id == selectedSeries?.id -> Modifier.focusRequester(gridSelFocus)
                                    index == 0 -> Modifier.focusRequester(firstItemFocus)
                                    else -> Modifier
                                },
                                onFocus = { vm.onSeriesFocused(s) },
                                onClick = { vm.openSeries(s) },
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize().padding(Dimens.GapLarge)) {
            val s = selectedSeries
            if (s == null) {
                PreviewPane(hint = "Focus a series, press OK to view episodes.")
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Dimens.CardCorner)).background(OwnTVTheme.colors.panel).padding(Dimens.GapLarge),
                ) {
                    Text(s.name, style = MaterialTheme.typography.titleLarge, color = OwnTVTheme.colors.onSurface)
                    if (s.year != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(s.year.toString(), style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.onSurfaceVariant)
                    }
                    val plot = s.plot
                    if (!plot.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(plot, style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.onSurfaceVariant, maxLines = 8)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Press OK on the poster to view episodes.", style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.primary)
                }
            }
        }
    }
}

@Composable
private fun EpisodeView(
    series: SeriesEntity,
    vm: SeriesViewModel,
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean,
    onRestored: () -> Unit,
    modifier: Modifier,
) {
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val loading by vm.episodesLoading.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val downloads by vm.episodeDownloads.collectAsStateWithLifecycle()
    val selectedSeason by vm.selectedSeason.collectAsStateWithLifecycle()
    val lastPlayedId by vm.lastPlayedEpisodeId.collectAsStateWithLifecycle()
    val epListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val selFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val firstEpFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var initialFocused by remember { mutableStateOf(false) }

    BackHandler { vm.closeSeries() }

    val seasons = episodes.map { it.seasonNumber }.distinct().sorted()
    val activeSeason = if (seasons.contains(selectedSeason)) selectedSeason else seasons.firstOrNull() ?: 1
    val seasonEpisodes = episodes.filter { it.seasonNumber == activeSeason }

    // Opening a show: grab focus on the first episode (the grid that had focus is unmounted, so
    // focus would otherwise die and fall back to the sidebar). When entering via player-return,
    // mark done WITHOUT focusing — the restore below owns focus, and this effect re-runs when
    // restoreFocus flips back to false (it must not steal focus to episode 1 then).
    LaunchedEffect(seasonEpisodes.isNotEmpty(), restoreFocus) {
        if (initialFocused) return@LaunchedEffect
        if (restoreFocus) { initialFocused = true; return@LaunchedEffect }
        if (seasonEpisodes.isNotEmpty()) {
            initialFocused = true
            kotlinx.coroutines.delay(80)
            runCatching { firstEpFocus.requestFocus() }
        }
    }

    // Resume flow: AUTO continues silently, ASK prompts (≥10s saved), NEVER starts from zero.
    val resumeMode by vm.resumeMode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var resumePrompt by remember { mutableStateOf<Pair<EpisodeEntity, Long>?>(null) }
    val startEpisode: (EpisodeEntity) -> Unit = { ep ->
        scope.launch {
            val pos = vm.savedPositionMs(ep)
            when {
                resumeMode == SettingsRepository.ResumeMode.ASK && pos >= 10_000 -> resumePrompt = ep to pos
                resumeMode == SettingsRepository.ResumeMode.AUTO && pos > 0 -> { vm.playEpisode(ep, pos); onFullscreen() }
                else -> { vm.playEpisode(ep, 0); onFullscreen() }
            }
        }
    }

    // Returning from fullscreen: scroll to and focus the episode you were watching.
    LaunchedEffect(restoreFocus, seasonEpisodes.size) {
        if (!restoreFocus) return@LaunchedEffect
        val idx = lastPlayedId?.let { id -> seasonEpisodes.indexOfFirst { it.id == id } } ?: -1
        if (idx >= 0) {
            runCatching { epListState.scrollToItem(idx) }
            kotlinx.coroutines.delay(60)
            runCatching { selFocus.requestFocus() }
        }
        onRestored()
    }

    Column(
        modifier = modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }.padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OwnTVButton(label = "Back", onClick = { vm.closeSeries() }, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.CHEVRON)
            Text(series.name, style = MaterialTheme.typography.headlineLarge, color = OwnTVTheme.colors.onSurface)
            Spacer(Modifier.weight(1f))
            OwnTVButton(
                label = if (favoriteIds.contains(series.id)) "Favorited" else "Favorite",
                onClick = { vm.toggleFavorite(series) },
                style = OwnTVButtonStyle.SECONDARY,
                icon = OwnTVIcon.STAR,
            )
        }
        Spacer(Modifier.height(16.dp))

        when {
            loading && episodes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                OwnTVSpinner(sizeDp = 48)
            }
            episodes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No episodes available.", style = MaterialTheme.typography.bodyLarge, color = OwnTVTheme.colors.onSurfaceVariant)
            }
            else -> {
                if (seasons.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        seasons.forEach { season ->
                            SeasonChip(season = season, selected = season == activeSeason) { vm.selectSeason(season) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                LazyColumn(state = epListState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(seasonEpisodes.size) { index ->
                        val ep = seasonEpisodes[index]
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val epModifier = Modifier.weight(1f)
                                .then(if (ep.id == lastPlayedId) Modifier.focusRequester(selFocus) else Modifier)
                                .then(if (index == 0) Modifier.focusRequester(firstEpFocus) else Modifier)
                            EpisodeRow(episode = ep, onClick = { startEpisode(ep) }, modifier = epModifier)
                            EpisodeDownloadBtn(download = downloads[ep.id]) { vm.downloadEpisode(ep) }
                        }
                    }
                }
            }
        }
    }

    resumePrompt?.let { (ep, pos) ->
        ResumeDialog(
            positionMs = pos,
            onResume = { resumePrompt = null; vm.playEpisode(ep, pos); onFullscreen() },
            onStartOver = { resumePrompt = null; vm.playEpisode(ep, 0); onFullscreen() },
            onDismiss = { resumePrompt = null },
        )
    }
}

@Composable
private fun SeasonChip(season: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        selected = selected,
        shape = CircleShape,
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        selectedContainerColor = colors.primaryContainer,
        contentAlignment = Alignment.Center,
    ) { _ ->
        Text(
            "Season $season",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun EpisodeDownloadBtn(download: DownloadEntity?, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    // Only actionable when not started (download) or failed (retry); otherwise it just reflects status.
    val actionable = download == null || download.status == DownloadStatus.FAILED
    FocusableSurface(
        onClick = onClick,
        enabled = actionable,
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.Center,
    ) { focused ->
        when (download?.status) {
            DownloadStatus.COMPLETED -> OwnTVIcon(OwnTVIcon.DOWNLOADS, tint = colors.primary, filled = true, modifier = Modifier.size(20.dp))
            DownloadStatus.FAILED -> OwnTVIcon(OwnTVIcon.REWIND, tint = Color(0xFFEF4444), filled = true, modifier = Modifier.size(18.dp))
            DownloadStatus.QUEUED -> ProgressRing(fraction = 0f, modifier = Modifier.size(24.dp))
            DownloadStatus.RUNNING, DownloadStatus.PAUSED -> {
                val frac = if (download.totalBytes > 0) download.downloadedBytes.toFloat() / download.totalBytes else 0f
                Box(contentAlignment = Alignment.Center) {
                    ProgressRing(fraction = frac, modifier = Modifier.size(30.dp))
                    Text("${(frac * 100).toInt()}", style = MaterialTheme.typography.labelSmall, color = colors.onSurface)
                }
            }
            null -> OwnTVIcon(OwnTVIcon.DOWNLOADS, tint = if (focused) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EpisodeRow(episode: EpisodeEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                Text(episode.episodeNumber.toString(), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
            Text(episode.name, style = MaterialTheme.typography.titleMedium, color = if (focused) colors.primary else colors.onSurface, modifier = Modifier.weight(1f))
            OwnTVIcon(OwnTVIcon.PLAY, tint = if (focused) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}
