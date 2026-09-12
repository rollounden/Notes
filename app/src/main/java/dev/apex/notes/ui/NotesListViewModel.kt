package dev.apex.notes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.apex.notes.data.Note
import dev.apex.notes.data.NoteColor
import dev.apex.notes.data.NotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotesListState(
    val loaded: Boolean = false,
    val pinned: List<Note> = emptyList(),
    val others: List<Note> = emptyList(),
    val query: String = "",
    val colorFilter: NoteColor? = null,
    /** Colours in use across all active notes, for the filter chip row. */
    val availableColors: List<NoteColor> = emptyList(),
    val selection: Set<Long> = emptySet(),
    val totalCount: Int = 0,
) {
    val isSearching: Boolean get() = query.isNotBlank() || colorFilter != null
    val inSelectionMode: Boolean get() = selection.isNotEmpty()
}

class NotesListViewModel(private val repo: NotesRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val colorFilter = MutableStateFlow<NoteColor?>(null)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<NotesListState> = combine(
        repo.activeNotes, query, colorFilter, selection,
    ) { notes, q, color, sel ->
        val filtered = notes.filter { note ->
            (color == null || note.color == color) && (q.isBlank() || note.matches(q))
        }
        NotesListState(
            loaded = true,
            pinned = filtered.filter { it.isPinned },
            others = filtered.filter { !it.isPinned },
            query = q,
            colorFilter = color,
            availableColors = notes.map { it.color }.filter { it != NoteColor.NONE }.distinct().sortedBy { it.ordinal },
            selection = sel.filterTo(mutableSetOf()) { id -> notes.any { it.id == id } },
            totalCount = notes.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesListState())

    fun setQuery(value: String) = query.update { value }
    fun setColorFilter(color: NoteColor?) = colorFilter.update { if (it == color) null else color }
    fun clearFilters() {
        query.value = ""
        colorFilter.value = null
    }

    fun toggleSelected(id: Long) = selection.update { if (id in it) it - id else it + id }
    fun clearSelection() = selection.update { emptySet() }

    fun archive(ids: Collection<Long>, onDone: () -> Unit = {}) = mutate(ids) {
        repo.setArchived(it, true); onDone()
    }
    fun unarchive(ids: Collection<Long>) = viewModelScope.launch { repo.setArchived(ids.toList(), false) }
    fun setPinned(ids: Collection<Long>, pinned: Boolean) = mutate(ids) { repo.setPinned(it, pinned) }
    fun setColor(ids: Collection<Long>, color: NoteColor) = mutate(ids) { repo.setColor(it, color) }
    fun delete(ids: Collection<Long>) = mutate(ids) { repo.delete(it) }

    fun toggleItem(noteId: Long, itemId: Long, checked: Boolean) = viewModelScope.launch {
        repo.setItemChecked(noteId, itemId, checked)
    }

    /** Snapshot for undo of a destructive bulk action. */
    fun snapshot(ids: Collection<Long>): List<Note> {
        val all = state.value.pinned + state.value.others
        return all.filter { it.id in ids }
    }

    fun restore(notes: List<Note>) = viewModelScope.launch { repo.importMerge(notes) }

    private fun mutate(ids: Collection<Long>, block: suspend (List<Long>) -> Unit) = viewModelScope.launch {
        block(ids.toList())
        selection.update { it - ids.toSet() }
    }

    private fun Note.matches(q: String): Boolean {
        val needle = q.trim()
        return title.contains(needle, ignoreCase = true) ||
            body.contains(needle, ignoreCase = true) ||
            items.any { it.text.contains(needle, ignoreCase = true) }
    }
}
