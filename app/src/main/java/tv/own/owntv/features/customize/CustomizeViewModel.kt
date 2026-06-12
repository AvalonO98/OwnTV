@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tv.own.owntv.features.customize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.features.settings.data.SettingsRepository

/** One category row in the Customize screen (hidden rows stay visible here, marked, for unhiding). */
data class CustomizeCatRow(
    val key: String,
    val originalName: String,
    val displayName: String,
    val hidden: Boolean,
    val renamed: Boolean,
)

/**
 * Drives Settings → Customize: per-profile hide / rename / reorder of categories (Live/Movies/Series)
 * and the unhide list for hidden Live channels. All edits live in [CustomizationStore] (DataStore),
 * so they survive re-syncs and never touch the DB schema.
 */
class CustomizeViewModel(
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val categoryDao: CategoryDao,
    private val customize: CustomizationStore,
) : ViewModel() {

    private data class Ctx(val profileId: Long, val sourceIds: List<Long>)

    private val ctx = MutableStateFlow(Ctx(-1L, emptyList()))

    private val _section = MutableStateFlow(MediaType.LIVE)
    val section: StateFlow<MediaType> = _section.asStateFlow()

    init {
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            ctx.value = Ctx(pid, sourceDao.sourceIdsForProfile(pid))
        }
    }

    fun selectSection(type: MediaType) {
        _section.value = type
    }

    /** Categories of the selected section, in their customized order, including hidden ones. */
    val rows: StateFlow<List<CustomizeCatRow>> = combine(_section, ctx) { s, c -> s to c }
        .flatMapLatest { (type, c) ->
            if (c.profileId < 0) flowOf(emptyList())
            else combine(
                categoryDao.observe(c.sourceIds, type),
                customize.observe(c.profileId, type),
            ) { cats, cust ->
                val orderIndex = cust.categoryOrder.withIndex().associate { (i, k) -> k to i }
                val keyed = cats.map { it to CustomizeKeys.category(it) }
                val (pinned, rest) = keyed.partition { (_, k) -> k in orderIndex }
                (pinned.sortedBy { (_, k) -> orderIndex.getValue(k) } + rest).map { (cat, key) ->
                    CustomizeCatRow(
                        key = key,
                        originalName = cat.name,
                        displayName = cust.categoryNames[key] ?: cat.name,
                        hidden = key in cust.hiddenCategories,
                        renamed = key in cust.categoryNames,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Hidden Live channels (key → label) so they can be unhidden from here. */
    val hiddenChannels: StateFlow<Map<String, String>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptyMap())
            else customize.observe(c.profileId, MediaType.LIVE).map { it.hiddenItems }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun setCategoryHidden(row: CustomizeCatRow, hidden: Boolean) {
        viewModelScope.launch {
            customize.setCategoryHidden(ctx.value.profileId, _section.value, row.key, hidden)
        }
    }

    /** Blank name restores the provider's original. */
    fun renameCategory(row: CustomizeCatRow, name: String?) {
        viewModelScope.launch {
            customize.renameCategory(ctx.value.profileId, _section.value, row.key, name)
        }
    }

    /** Moves a category one step up/down and persists the full resulting order. */
    fun move(row: CustomizeCatRow, up: Boolean) {
        val current = rows.value
        val index = current.indexOfFirst { it.key == row.key }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target < 0 || target > current.lastIndex) return
        val reordered = current.toMutableList().apply {
            val item = removeAt(index)
            add(target, item)
        }
        viewModelScope.launch {
            customize.setCategoryOrder(ctx.value.profileId, _section.value, reordered.map { it.key })
        }
    }

    fun unhideChannel(key: String) {
        viewModelScope.launch {
            customize.setItemHidden(ctx.value.profileId, MediaType.LIVE, key, "", false)
        }
    }
}
