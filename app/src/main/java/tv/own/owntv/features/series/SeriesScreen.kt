package tv.own.owntv.features.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import tv.own.owntv.features.live.LiveKey
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.features.shell.components.CategoryRail
import tv.own.owntv.features.shell.components.PreviewPane
import tv.own.owntv.features.shell.components.RailCategory
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.MoveOrderOverlay
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.PosterCard
import tv.own.owntv.ui.components.ProgressRing
import tv.own.owntv.ui.components.ResumeDialog
import tv.own.owntv.ui.components.longPressMenuGuard
import androidx.compose.foundation.layout.width
import tv.own.owntv.ui.components.SearchBar
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.ui.components.SortChip
import tv.own.owntv.ui.components.formatCount
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.PreviewPanelFill
import tv.own.owntv.ui.components.roundedPanel
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
private fun SeriesContextMenu(
    title: String,
    isFavorite: Boolean,
    canMove: Boolean,
    isHistory: Boolean,
    hasTmdbDetails: Boolean,
    onShowDetails: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMove: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f))
            .longPressMenuGuard(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(440.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            OwnTVButton(
                if (isFavorite) "Remove from Favourites" else "Add to Favourites",
                onClick = onToggleFavorite, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.STAR,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (canMove) OwnTVButton("Move", onClick = onMove, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            if (isHistory) OwnTVButton("Remove from History", onClick = onRemoveFromHistory, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            OwnTVButton("Download all episodes", onClick = onDownload, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.DOWNLOADS, modifier = Modifier.fillMaxWidth())
            if (hasTmdbDetails) {
                OwnTVButton("TMDB Details", onClick = onShowDetails, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.MENU, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(4.dp))
            OwnTVButton("Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
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
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val selectedSeries by vm.selectedSeries.collectAsStateWithLifecycle()
    val selectedSeriesMeta by vm.selectedSeriesMeta.collectAsStateWithLifecycle()
    val metadataMode by vm.metadataMode.collectAsStateWithLifecycle()
    val series = vm.series.collectAsLazyPagingItems()
    val moveState by vm.moveState.collectAsStateWithLifecycle()
    var contextSeries by remember { mutableStateOf<tv.own.owntv.core.database.entity.SeriesEntity?>(null) }
    // Fullscreen TMDB details window (§11.1); null = closed.
    var detailsSeries by remember { mutableStateOf<tv.own.owntv.core.database.entity.SeriesEntity?>(null) }
    // Id + list position of the series the context menu was opened on. The id re-focuses the same item
    // when it survives (Favourite/Download/Cancel); when the item is REMOVED (Remove from history, or
    // un-Favourite while on the Favorites category), it's gone from the paged list, so we re-focus the
    // nearest surviving neighbour by position instead of escaping to the CategoryRail.
    var contextSeriesId by remember { mutableStateOf<Long?>(null) }
    var contextSeriesIndex by remember { mutableStateOf(-1) }
    val contextFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    val selectedIndex = railItems.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val selectedItem = railItems.getOrNull(selectedIndex)
    val gridSelFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val firstItemFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Back from a show's episodes: scroll the grid to the poster you opened, then focus it. It may be
    // far down and not composed, so without scrolling the focus request fails and focus falls to the
    // sidebar (the same scroll-then-focus fix Movies uses).
    LaunchedEffect(restoreSelected, series.itemCount) {
        if (restoreSelected && series.itemCount > 0) {
            val sel = selectedSeries
            val idx = if (sel != null) series.itemSnapshotList.items.indexOfFirst { it.id == sel.id } else -1
            if (idx >= 0) {
                runCatching { gridState.scrollToItem(idx) }
                kotlinx.coroutines.delay(60)
                runCatching { gridSelFocus.requestFocus() }
            } else {
                runCatching { firstItemFocus.requestFocus() }
            }
            onRestoredSelected()
        }
    }
    // Closing the long-press context menu must return focus inside this pane, never the CategoryRail.
    //   - Item still present (Favourite toggle / Download / Cancel): re-focus the same item by id.
    //   - Item removed (Remove from history, or un-Favourite on the Favorites category): the paged
    //     list no longer contains it, so focus the NEAREST surviving neighbour by position (the item
    //     that slid into the removed slot, else the new last item, else first item). Only if the whole
    //     category is now empty do we let focus leave (there's nothing here to land on).
    LaunchedEffect(contextSeries) {
        if (contextSeries != null) return@LaunchedEffect
        // Opening the TMDB Details window closes the menu; let the window keep focus (it traps focus and
        // refocuses the series on close), don't yank it back to the grid here.
        if (detailsSeries != null) return@LaunchedEffect
        val targetId = contextSeriesId
        if (targetId == null) { contextSeriesIndex = -1; return@LaunchedEffect }
        val items = series.itemSnapshotList.items
        val idx = items.indexOfFirst { it?.id == targetId }
        if (idx >= 0) {
            runCatching {
                if (viewMode == SettingsRepository.VodViewMode.LIST) listState.scrollToItem(idx)
                else gridState.scrollToItem(idx)
            }
            withFrameNanos { }
            runCatching { contextFocus.requestFocus() }
        } else {
            withFrameNanos { }
            val settled = series.itemSnapshotList.items.filterNotNull()
            if (settled.isEmpty()) {
                runCatching { firstItemFocus.requestFocus() }
            } else {
                val neighbor = settled.getOrNull(contextSeriesIndex.coerceAtLeast(0)) ?: settled.last()
                val neighborIdx = items.indexOfFirst { it?.id == neighbor.id }.coerceAtLeast(0)
                runCatching {
                    if (viewMode == SettingsRepository.VodViewMode.LIST) listState.scrollToItem(neighborIdx)
                    else gridState.scrollToItem(neighborIdx)
                }
                contextSeriesId = neighbor.id
                withFrameNanos { }
                runCatching { contextFocus.requestFocus() }
            }
        }
        contextSeriesIndex = -1
    }

    Row(modifier = modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CategoryRail(
            categories = railItems.map { RailCategory(it.abbr, it.title, it.icon) },
            selectedIndex = selectedIndex,
            onSelect = { idx -> railItems.getOrNull(idx)?.let { vm.select(it.key) } },
        )

        Column(
            modifier = Modifier
                .weight(1.8f)
                .fillMaxSize()
                .roundedPanel(fillColor = ContentPanelFill)
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
                // Held Up/Down can outrun the lazy grid's composition and escape this pane
                // (landing on the top bar) — trap vertical exits; Left/Right/Back leave normally.
                .trapVerticalFocusExit()
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
                Spacer(Modifier.width(10.dp))
                tv.own.owntv.ui.components.OwnTVButton(
                    label = viewMode.label,
                    onClick = vm::toggleViewMode,
                    icon = if (viewMode == SettingsRepository.VodViewMode.GRID) OwnTVIcon.MENU else OwnTVIcon.SERIES,
                    style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY,
                )
            }
            Spacer(Modifier.height(14.dp))

            if (series.itemCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isNotBlank()) "No series found for “${searchQuery.trim()}”" else "No series here.",
                        style = MaterialTheme.typography.bodyLarge, color = OwnTVTheme.colors.onSurfaceVariant,
                    )
                }
            } else if (viewMode == SettingsRepository.VodViewMode.LIST) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(series.itemCount) { index ->
                        val s = series[index]
                        if (s != null) {
                            SeriesListRow(
                                series = s,
                                isFavorite = favoriteIds.contains(s.id),
                                modifier = when {
                                    s.id == contextSeriesId -> Modifier.focusRequester(contextFocus)
                                    s.id == selectedSeries?.id -> Modifier.focusRequester(gridSelFocus)
                                    index == 0 -> Modifier.focusRequester(firstItemFocus)
                                    else -> Modifier
                                },
                                onFocus = { vm.onSeriesFocused(s) },
                                onClick = { vm.openSeries(s) },
                                onLongClick = { contextSeries = s; contextSeriesId = s.id; contextSeriesIndex = index },
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
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
                                    s.id == contextSeriesId -> Modifier.focusRequester(contextFocus)
                                    s.id == selectedSeries?.id -> Modifier.focusRequester(gridSelFocus)
                                    index == 0 -> Modifier.focusRequester(firstItemFocus)
                                    else -> Modifier
                                },
                                onFocus = { vm.onSeriesFocused(s) },
                                onClick = { vm.openSeries(s) },
                                onLongClick = { contextSeries = s; contextSeriesId = s.id; contextSeriesIndex = index },
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize().roundedPanel(fillColor = PreviewPanelFill).padding(Dimens.GapLarge)) {
            val s = selectedSeries
            if (s == null) {
                PreviewPane(hint = "Focus a series, press OK to view episodes.")
            } else {
                // Gap-fill merge (§7.1/§4.1): provider wins unless the mode is TMDB-only.
                val meta = selectedSeriesMeta?.takeIf { it.seriesId == s.id }?.cache
                val tmdbWins = metadataMode.tmdbWins
                val providerPoster = s.posterUrl?.takeIf { it.isNotBlank() }
                val tmdbPoster = tv.own.owntv.core.metadata.MetadataImages.poster(meta?.posterPath)
                val art = (if (tmdbWins) tmdbPoster ?: providerPoster else providerPoster ?: tmdbPoster)
                    ?: s.backdropUrl?.takeIf { it.isNotBlank() }
                    ?: tv.own.owntv.core.metadata.MetadataImages.backdrop(meta?.backdropPath)
                val plot = if (tmdbWins) meta?.overview ?: s.plot?.takeIf { it.isNotBlank() }
                    else s.plot?.takeIf { it.isNotBlank() } ?: meta?.overview
                val year = if (tmdbWins) meta?.year ?: s.year else s.year ?: meta?.year
                val rating = if (tmdbWins) meta?.rating?.takeIf { it > 0 } ?: s.rating?.takeIf { it > 0 }
                    else s.rating?.takeIf { it > 0 } ?: meta?.rating?.takeIf { it > 0 }
                val genres = jsonStringList(meta?.genresJson)
                val cast = jsonStringList(meta?.castJson)
                Column(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Dimens.CardCorner)).background(OwnTVTheme.colors.panel).verticalScroll(rememberScrollState()).padding(Dimens.GapLarge),
                ) {
                    // Tall portrait poster (like the list / a phone screen), centred in the pane.
                    Box(modifier = Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(OwnTVTheme.colors.surfaceContainerLowest),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!art.isNullOrBlank()) {
                                AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                OwnTVIcon(OwnTVIcon.SERIES, tint = OwnTVTheme.colors.onSurfaceVariant, modifier = Modifier.height(48.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(s.name, style = MaterialTheme.typography.titleLarge, color = OwnTVTheme.colors.onSurface)
                    val metaBits = listOfNotNull(year?.toString(), rating?.let { "★ %.1f".format(it) })
                    if (metaBits.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(metaBits.joinToString("  •  "), style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.onSurfaceVariant)
                    }
                    if (genres.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(genres.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = OwnTVTheme.colors.primary)
                    }
                    if (!plot.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(plot, style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.onSurfaceVariant, maxLines = 8)
                    }
                    if (cast.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Cast", style = MaterialTheme.typography.labelMedium, color = OwnTVTheme.colors.onSurface)
                        Spacer(Modifier.height(2.dp))
                        Text(cast.take(6).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = OwnTVTheme.colors.onSurfaceVariant, maxLines = 2)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Press OK on the poster to view episodes.", style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.primary)
                }
            }
        }
    }

    // Long-press a series → context menu.
    contextSeries?.let { s ->
        val cacheForS = selectedSeriesMeta?.takeIf { it.seriesId == s.id }?.cache
        SeriesContextMenu(
            title = s.name,
            isFavorite = favoriteIds.contains(s.id),
            canMove = selectedKey is LiveKey.Folder || selectedKey == LiveKey.Favorites,
            isHistory = selectedKey == LiveKey.History,
            hasTmdbDetails = metadataMode.enrich && cacheForS != null,
            onShowDetails = { contextSeries = null; detailsSeries = s },
            onToggleFavorite = { vm.toggleFavorite(s); contextSeries = null },
            onMove = { contextSeries = null; vm.enterMoveMode(s, selectedKey) },
            onRemoveFromHistory = { vm.removeFromHistory(s.id); contextSeries = null },
            onDownload = { vm.downloadSeries(s); contextSeries = null },
            onDismiss = { contextSeries = null },
        )
    }

    // Fullscreen TMDB details window (§11.1) — read-only, Back exits; refocus the series on close.
    LaunchedEffect(detailsSeries) {
        if (detailsSeries == null && contextSeriesId != null) {
            withFrameNanos { }
            runCatching { contextFocus.requestFocus() }
        }
    }
    detailsSeries?.let { s ->
        val cache = selectedSeriesMeta?.takeIf { it.seriesId == s.id }?.cache
        tv.own.owntv.features.shell.components.MediaDetailsScreen(
            details = buildSeriesDetails(s, cache, metadataMode.tmdbWins),
            onExit = { detailsSeries = null },
        )
    }

    // Move mode overlay.
    moveState?.let { ms ->
        MoveOrderOverlay(
            title = "Reorder series",
            itemNames = ms.items.map { it.name },
            activeIndex = ms.activeIndex,
            onMoveUp = vm::moveUp,
            onMoveDown = vm::moveDown,
            onCommit = vm::commitMove,
            onCancel = vm::cancelMove,
        )
    }
}

/** Parse a stored JSON array of strings (genres/cast); empty on null/blank/bad JSON. */
private fun jsonStringList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }.getOrDefault(emptyList())
}

/** Build the fullscreen TMDB-details payload for a series, applying the §7.1/§4.1 merge precedence. */
private fun buildSeriesDetails(
    s: SeriesEntity,
    meta: tv.own.owntv.core.database.entity.MetadataCacheEntity?,
    tmdbWins: Boolean,
): tv.own.owntv.features.shell.components.MediaDetailsUi {
    val providerPoster = s.posterUrl?.takeIf { it.isNotBlank() }
    val tmdbPoster = tv.own.owntv.core.metadata.MetadataImages.poster(meta?.posterPath)
    val poster = if (tmdbWins) tmdbPoster ?: providerPoster else providerPoster ?: tmdbPoster
    val backdrop = tv.own.owntv.core.metadata.MetadataImages.backdrop(meta?.backdropPath)
        ?: s.backdropUrl?.takeIf { it.isNotBlank() }
    val plot = if (tmdbWins) meta?.overview ?: s.plot else s.plot?.takeIf { it.isNotBlank() } ?: meta?.overview
    val year = if (tmdbWins) meta?.year ?: s.year else s.year ?: meta?.year
    val rating = if (tmdbWins) meta?.rating?.takeIf { it > 0 } ?: s.rating?.takeIf { it > 0 }
        else s.rating?.takeIf { it > 0 } ?: meta?.rating?.takeIf { it > 0 }
    val metaLine = listOfNotNull(year?.toString(), rating?.let { "★ %.1f".format(it) }).joinToString("  •  ")
    return tv.own.owntv.features.shell.components.MediaDetailsUi(
        title = s.name,
        backdropUrl = backdrop,
        posterUrl = poster,
        metaLine = metaLine,
        genres = jsonStringList(meta?.genresJson),
        plot = plot,
        cast = jsonStringList(meta?.castJson),
    )
}

/** Right-hand pane for the focused episode (Option B): 16:9 TMDB still, name, S/E · year · rating, plot. */
@Composable
private fun EpisodeDetailPane(
    episode: EpisodeEntity?,
    meta: tv.own.owntv.core.database.entity.MetadataCacheEntity?,
    tmdbWins: Boolean,
) {
    val colors = OwnTVTheme.colors
    if (episode == null) {
        PreviewPane(hint = "Focus an episode to see details.")
        return
    }
    val still = tv.own.owntv.core.metadata.MetadataImages.backdrop(meta?.backdropPath ?: meta?.posterPath)
    val title = if (tmdbWins) meta?.title?.takeIf { it.isNotBlank() } ?: episode.name else episode.name
    val plot = if (tmdbWins) meta?.overview ?: episode.plot?.takeIf { it.isNotBlank() }
        else episode.plot?.takeIf { it.isNotBlank() } ?: meta?.overview
    val bits = listOfNotNull(
        "S${episode.seasonNumber} · E${episode.episodeNumber}",
        meta?.year?.toString(),
        meta?.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
    )
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.GapLarge)) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerLowest),
            contentAlignment = Alignment.Center,
        ) {
            if (!still.isNullOrBlank()) {
                AsyncImage(model = still, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                OwnTVIcon(OwnTVIcon.SERIES, tint = colors.onSurfaceVariant, modifier = Modifier.height(40.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(bits.joinToString("  •  "), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        if (!plot.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(plot, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Text("OK to play  ·  long-press for options", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

/** Minimal long-press menu for an episode: Download (+ toast if already), TMDB Details when matched. */
@Composable
private fun EpisodeContextMenu(
    title: String,
    hasTmdbDetails: Boolean,
    onShowDetails: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).longPressMenuGuard(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(440.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            OwnTVButton("Download", onClick = onDownload, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.DOWNLOADS, modifier = Modifier.fillMaxWidth().focusRequester(focus))
            if (hasTmdbDetails) {
                OwnTVButton("TMDB Details", onClick = onShowDetails, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.MENU, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(4.dp))
            OwnTVButton("Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Build the fullscreen TMDB-details payload for an episode (still as the hero; no 2:3 poster). */
private fun buildEpisodeDetails(
    ep: EpisodeEntity,
    meta: tv.own.owntv.core.database.entity.MetadataCacheEntity?,
    tmdbWins: Boolean,
): tv.own.owntv.features.shell.components.MediaDetailsUi {
    val still = tv.own.owntv.core.metadata.MetadataImages.backdrop(meta?.backdropPath ?: meta?.posterPath)
    val title = if (tmdbWins) meta?.title?.takeIf { it.isNotBlank() } ?: ep.name else ep.name
    val plot = if (tmdbWins) meta?.overview ?: ep.plot else ep.plot?.takeIf { it.isNotBlank() } ?: meta?.overview
    val metaLine = listOfNotNull(meta?.year?.toString(), meta?.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) }).joinToString("  •  ")
    return tv.own.owntv.features.shell.components.MediaDetailsUi(
        title = title,
        subtitle = "S${ep.seasonNumber} · E${ep.episodeNumber}",
        backdropUrl = still,
        posterUrl = null,
        metaLine = metaLine,
        plot = plot,
    )
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
    val selectedEpisode by vm.selectedEpisode.collectAsStateWithLifecycle()
    val selectedEpisodeMeta by vm.selectedEpisodeMeta.collectAsStateWithLifecycle()
    val metadataMode by vm.metadataMode.collectAsStateWithLifecycle()
    val epListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val selFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val firstEpFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var initialFocused by remember { mutableStateOf(false) }
    var contextEpisode by remember { mutableStateOf<EpisodeEntity?>(null) }
    var detailsEpisode by remember { mutableStateOf<EpisodeEntity?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    BackHandler { vm.closeSeries() }

    val seasons = episodes.map { it.seasonNumber }.distinct().sorted()
    val activeSeason = if (seasons.contains(selectedSeason)) selectedSeason else seasons.firstOrNull() ?: 1
    val seasonEpisodes = episodes.filter { it.seasonNumber == activeSeason }

    // Opening a show: grab focus on the LAST-WATCHED episode if there is one (#22), else the first
    // episode (the grid that had focus is unmounted, so focus would otherwise die and fall back to the
    // sidebar). Waits for !loading so the seeded last-watched id/season from the VM is settled. When
    // entering via player-return, mark done WITHOUT focusing — the restore below owns focus.
    LaunchedEffect(loading, seasonEpisodes.isNotEmpty(), restoreFocus) {
        if (initialFocused) return@LaunchedEffect
        if (restoreFocus) { initialFocused = true; return@LaunchedEffect }
        if (!loading && seasonEpisodes.isNotEmpty()) {
            initialFocused = true
            val idx = lastPlayedId?.let { id -> seasonEpisodes.indexOfFirst { it.id == id } } ?: -1
            kotlinx.coroutines.delay(80)
            if (idx >= 0) {
                runCatching { epListState.scrollToItem(idx) }
                kotlinx.coroutines.delay(40)
                runCatching { selFocus.requestFocus() }
            } else {
                runCatching { firstEpFocus.requestFocus() }
            }
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
        // Same rounded content panel as the series grid — the episode list was the one view drawn
        // without a panel background.
        modifier = modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }
            .roundedPanel(fillColor = ContentPanelFill)
            .padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingV),
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
                // Option B (§11.1): episode list on the left, focused-episode detail pane on the right.
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1.4f).fillMaxHeight()) {
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
                                val epModifier = Modifier
                                    .then(if (ep.id == lastPlayedId) Modifier.focusRequester(selFocus) else Modifier)
                                    .then(if (index == 0) Modifier.focusRequester(firstEpFocus) else Modifier)
                                EpisodeRow(
                                    episode = ep,
                                    lastWatched = ep.id == lastPlayedId,
                                    onClick = { startEpisode(ep) },
                                    onFocus = { vm.onEpisodeFocused(ep) },
                                    onLongClick = { contextEpisode = ep },
                                    modifier = epModifier,
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(Dimens.CardCorner)).background(OwnTVTheme.colors.panel)) {
                        val ep = selectedEpisode
                        val meta = selectedEpisodeMeta?.takeIf { it.episodeId == ep?.id }?.cache
                        EpisodeDetailPane(ep, meta, metadataMode.tmdbWins)
                    }
                }
            }
        }
    }

    // Long-press an episode → context menu (Download idempotent + toast; TMDB Details when matched).
    contextEpisode?.let { ep ->
        val cacheForEp = selectedEpisodeMeta?.takeIf { it.episodeId == ep.id }?.cache
        val alreadyDownloaded = downloads[ep.id] != null
        EpisodeContextMenu(
            title = "S${ep.seasonNumber} · E${ep.episodeNumber}  ${ep.name}",
            hasTmdbDetails = metadataMode.enrich && cacheForEp != null,
            onShowDetails = { contextEpisode = null; detailsEpisode = ep },
            onDownload = {
                contextEpisode = null
                if (alreadyDownloaded) {
                    android.widget.Toast.makeText(context, "Already downloaded — check the Downloads menu.", android.widget.Toast.LENGTH_SHORT).show()
                } else vm.downloadEpisode(ep)
            },
            onDismiss = { contextEpisode = null },
        )
    }

    // Fullscreen TMDB details window for the episode (§11.1) — read-only, Back exits.
    detailsEpisode?.let { ep ->
        val cache = selectedEpisodeMeta?.takeIf { it.episodeId == ep.id }?.cache
        tv.own.owntv.features.shell.components.MediaDetailsScreen(
            details = buildEpisodeDetails(ep, cache, metadataMode.tmdbWins),
            onExit = { detailsEpisode = null },
        )
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
private fun EpisodeRow(
    episode: EpisodeEntity,
    lastWatched: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    // Row is now clean text (number + name + last-watched). Play = single-press; Download / TMDB Details
    // moved to long-press (§11.1). The focused episode drives the right detail pane.
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        LaunchedEffect(focused) { if (focused) onFocus() }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                Text(episode.episodeNumber.toString(), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
            Text(episode.name, style = MaterialTheme.typography.titleMedium, color = if (focused) colors.primary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            // Mark the episode you last watched so it's findable even when it isn't focused (#22).
            if (lastWatched) {
                Text(
                    "Last watched",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.primaryContainer).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** Compact one-line row used by the List view mode — fits many series on screen at once (#10). */
@Composable
private fun SeriesListRow(
    series: SeriesEntity,
    isFavorite: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        LaunchedEffect(focused) { if (focused) onFocus() }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(width = 44.dp, height = 62.dp).clip(RoundedCornerShape(6.dp)).background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (!series.posterUrl.isNullOrBlank()) {
                    AsyncImage(model = series.posterUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    OwnTVIcon(OwnTVIcon.SERIES, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    series.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (focused) colors.primary else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList {
                    series.year?.let { add(it.toString()) }
                    series.rating?.takeIf { it > 0 }?.let { add("★ %.1f".format(it)) }
                }.joinToString("  •  ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                }
            }
            if (isFavorite) {
                OwnTVIcon(OwnTVIcon.STAR, tint = colors.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
