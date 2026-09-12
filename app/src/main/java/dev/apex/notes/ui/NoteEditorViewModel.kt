package dev.apex.notes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.apex.notes.data.ChecklistItem
import dev.apex.notes.data.Note
import dev.apex.notes.data.NoteColor
import dev.apex.notes.data.NotesRepository
import dev.apex.notes.markdown.looksLikeMarkdown
import dev.apex.notes.markdown.toggleTaskLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Holds the note being edited. Compose `mutableStateOf` is used (rather than StateFlow) so text
 * field updates are applied synchronously and typing never drops characters.
 */
class NoteEditorViewModel(
    private val repo: NotesRepository,
    private val noteId: Long,
    startAsChecklist: Boolean,
    initialText: String?,
) : ViewModel() {

    var note by mutableStateOf(
        Note(
            isChecklist = startAsChecklist,
            body = if (!startAsChecklist) initialText.orEmpty() else "",
            items = if (startAsChecklist) {
                val lines = initialText?.lines()?.filter { it.isNotBlank() }.orEmpty()
                if (lines.isEmpty()) listOf(ChecklistItem(id = nextTempId())) else lines.map { ChecklistItem(nextTempId(), it.trim()) }
            } else emptyList(),
        )
    )
        private set

    var loaded by mutableStateOf(noteId == 0L)
        private set

    var previewMode by mutableStateOf(false)

    /** Id of the checklist item that should grab keyboard focus next. */
    var pendingFocusId by mutableStateOf<Long?>(null)

    /** Set once the note is gone (deleted/archived) so the screen can pop. */
    var finished by mutableStateOf(false)
        private set

    private var saveJob: Job? = null
    private var dirty = false

    init {
        if (noteId != 0L) {
            viewModelScope.launch {
                repo.getNote(noteId)?.let { loadedNote ->
                    note = loadedNote
                    // Existing text notes that contain markdown open in preview, which is what you
                    // usually want when re-reading; tapping the body switches to edit.
                    previewMode = !loadedNote.isChecklist && looksLikeMarkdown(loadedNote.body)
                }
                if (note.isChecklist && note.items.isEmpty()) {
                    note = note.copy(items = listOf(ChecklistItem(nextTempId())))
                }
                loaded = true
            }
        } else if (startAsChecklist) {
            pendingFocusId = note.items.firstOrNull()?.id
        }
    }

    /* ---------------------------------- editing ---------------------------------- */

    fun setTitle(value: String) = edit { copy(title = value) }
    fun setBody(value: String) = edit { copy(body = value) }
    fun togglePin() = edit { copy(isPinned = !isPinned) }
    fun setColor(color: NoteColor) = edit { copy(color = color) }

    fun toggleMarkdownTask(line: Int) = edit { copy(body = toggleTaskLine(body, line)) }

    fun setItemText(id: Long, text: String) = edit {
        copy(items = items.map { if (it.id == id) it.copy(text = text) else it })
    }

    fun setItemChecked(id: Long, checked: Boolean) = edit {
        copy(items = items.map { if (it.id == id) it.copy(isChecked = checked) else it })
    }

    /** Insert a blank item after [afterId] (or at the end) and focus it. */
    fun addItem(afterId: Long? = null) {
        val fresh = ChecklistItem(id = nextTempId())
        edit {
            val index = items.indexOfFirst { it.id == afterId }
            val list = items.toMutableList()
            if (index < 0) list.add(fresh) else list.add(index + 1, fresh)
            copy(items = list)
        }
        pendingFocusId = fresh.id
    }

    /** Remove an item; focus moves to the previous unchecked item when there is one. */
    fun removeItem(id: Long) {
        val current = note.items
        val index = current.indexOfFirst { it.id == id }
        val previous = current.take(index).lastOrNull { !it.isChecked } ?: current.getOrNull(index + 1)
        edit { copy(items = items.filterNot { it.id == id }) }
        if (note.items.isEmpty()) {
            val fresh = ChecklistItem(id = nextTempId())
            edit { copy(items = listOf(fresh)) }
            pendingFocusId = fresh.id
        } else {
            pendingFocusId = previous?.id
        }
    }

    /** Reorder within the underlying list so that [movingId] lands where [targetId] currently is. */
    fun moveItem(movingId: Long, targetId: Long) = edit {
        val list = items.toMutableList()
        val from = list.indexOfFirst { it.id == movingId }
        val to = list.indexOfFirst { it.id == targetId }
        if (from < 0 || to < 0 || from == to) return@edit this
        val item = list.removeAt(from)
        list.add(to, item)
        copy(items = list)
    }

    fun uncheckAll() = edit { copy(items = items.map { it.copy(isChecked = false) }) }
    fun deleteChecked() = edit { copy(items = items.filterNot { it.isChecked }.ifEmpty { listOf(ChecklistItem(nextTempId())) }) }

    fun convertToChecklist() = edit {
        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val checked = line.startsWith("- [x]", ignoreCase = true) || line.startsWith("[x]", ignoreCase = true)
            val text = line.removePrefix("- [x]").removePrefix("- [X]").removePrefix("- [ ]")
                .removePrefix("[x]").removePrefix("[X]").removePrefix("[ ]")
                .removePrefix("- ").removePrefix("* ").trim()
            ChecklistItem(nextTempId(), text, checked)
        }.ifEmpty { listOf(ChecklistItem(nextTempId())) }
        previewMode = false
        copy(isChecklist = true, items = lines, body = "")
    }

    fun convertToText() = edit {
        val text = items.filter { it.text.isNotBlank() }
            .joinToString("\n") { (if (it.isChecked) "- [x] " else "- [ ] ") + it.text }
        copy(isChecklist = false, body = text, items = emptyList())
    }

    /* ---------------------------------- lifecycle ---------------------------------- */

    fun archive() = viewModelScope.launch {
        val id = persist()
        if (id != 0L) repo.setArchived(listOf(id), true)
        finished = true
    }

    fun delete() = viewModelScope.launch {
        saveJob?.cancel()
        dirty = false
        if (note.id != 0L) repo.delete(listOf(note.id))
        finished = true
    }

    /**
     * Flush pending edits now (called on pause/back). Runs in a scope that outlives this ViewModel
     * so popping the screen cannot cancel the write. Empty notes are discarded.
     */
    fun flush() {
        saveJob?.cancel()
        if (!dirty) return
        persistScope.launch { persist() }
    }

    private suspend fun persist(): Long {
        val snapshot = note
        if (snapshot.isEmpty) {
            if (snapshot.id != 0L) {
                repo.delete(listOf(snapshot.id))
                note = note.copy(id = 0L)
            }
            dirty = false
            return 0L
        }
        val id = repo.save(snapshot)
        // Back on Main after Room's IO hop; `note` may have newer keystrokes, so copy from it.
        note = note.copy(id = id, updatedAt = System.currentTimeMillis())
        dirty = false
        return id
    }

    private inline fun edit(block: Note.() -> Note) {
        note = note.block()
        dirty = true
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            persist()
        }
    }

    override fun onCleared() {
        super.onCleared()
        flush()
    }

    companion object {
        private var tempCounter = -1L
        fun nextTempId(): Long = tempCounter--

        /** Main-thread scope so `note` is only ever written from one thread; Room does its own IO. */
        private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
