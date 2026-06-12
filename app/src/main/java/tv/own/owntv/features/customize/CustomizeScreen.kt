package tv.own.owntv.features.customize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Settings → Customize: hide / rename / reorder categories per section, and unhide hidden Live
 * channels. Everything is per-profile and survives source re-syncs.
 */
@Composable
fun CustomizeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: CustomizeViewModel = koinViewModel()
    val section by vm.section.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val hiddenChannels by vm.hiddenChannels.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors
    var renaming by remember { mutableStateOf<CustomizeCatRow?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }

    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            // Spatial D-pad entry from the sidebar would land mid-list — route it to the first chip.
            // onEnter fires only for directional entry from outside (internal moves don't re-trigger it).
            .focusProperties { onEnter = { runCatching { firstFocus.requestFocus() } } }
            .focusGroup()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Text("Customize", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            "Hide, rename and reorder categories for this profile. Changes survive re-syncs.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // Section picker
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionChip("Live TV", section == MediaType.LIVE, Modifier.focusRequester(firstFocus)) { vm.selectSection(MediaType.LIVE) }
            SectionChip("Movies", section == MediaType.MOVIE) { vm.selectSection(MediaType.MOVIE) }
            SectionChip("Series", section == MediaType.SERIES) { vm.selectSection(MediaType.SERIES) }
        }
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            // Hidden Live channels first (channels are hidden from the Live preview pane) — kept on
            // top so they're findable even when a provider has hundreds of categories below.
            if (section == MediaType.LIVE && hiddenChannels.isNotEmpty()) {
                item {
                    Text("Hidden channels", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Unhide to bring a channel back to the lists.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(hiddenChannels.entries.sortedBy { it.value.lowercase() }, key = { "hid:${it.key}" }) { (key, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label.ifBlank { key },
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        OwnTVButton("Unhide", onClick = { vm.unhideChannel(key) }, style = OwnTVButtonStyle.SECONDARY)
                    }
                }
                item {
                    Spacer(Modifier.height(14.dp))
                    Text("Categories", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (rows.isEmpty()) {
                item {
                    Text(
                        "No categories in this section yet — add a source first.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(rows, key = { it.key }) { row ->
                CategoryRow(
                    row = row,
                    onMoveUp = { vm.move(row, up = true) },
                    onMoveDown = { vm.move(row, up = false) },
                    onRename = { renaming = row },
                    onToggleHidden = { vm.setCategoryHidden(row, !row.hidden) },
                )
            }
        }
    }

    renaming?.let { row ->
        TextInputDialog(
            title = "Rename category",
            initial = row.displayName,
            hint = "Only for this profile. Leave blank to restore “${row.originalName}”.",
            onConfirm = { vm.renameCategory(row, it.takeIf { t -> t.isNotBlank() }); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun SectionChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        selected = selected,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        selectedContainerColor = colors.primaryContainer,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = when {
                selected -> colors.onPrimaryContainer
                focused -> colors.primary
                else -> colors.onSurface
            },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun CategoryRow(
    row: CustomizeCatRow,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = if (row.hidden) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.hidden || row.renamed) {
                Text(
                    buildString {
                        if (row.hidden) append("Hidden")
                        if (row.renamed) {
                            if (row.hidden) append("  ·  ")
                            append("was “${row.originalName}”")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        OwnTVButton("↑", onClick = onMoveUp, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("↓", onClick = onMoveDown, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("Rename", onClick = onRename, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(6.dp))
        OwnTVButton(if (row.hidden) "Show" else "Hide", onClick = onToggleHidden, style = OwnTVButtonStyle.SECONDARY)
    }
}
