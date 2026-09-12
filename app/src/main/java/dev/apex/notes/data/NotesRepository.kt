package dev.apex.notes.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import dev.apex.notes.widget.NotesWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotesRepository(private val context: Context, private val dao: NotesDao) {

    val activeNotes: Flow<List<Note>> = dao.observeActive().map { list -> list.map { it.toDomain() } }
    val archivedNotes: Flow<List<Note>> = dao.observeArchived().map { list -> list.map { it.toDomain() } }

    suspend fun getNote(id: Long): Note? = dao.getNote(id)?.toDomain()

    suspend fun getPinned(): List<Note> = dao.getPinned().map { it.toDomain() }

    /** Pinned first, then most recent; used by the widget. */
    suspend fun getTop(limit: Int): List<Note> = dao.getTop(limit).map { it.toDomain() }

    suspend fun getAll(): List<Note> = dao.getAll().map { it.toDomain() }

    /** Persist a note (insert when id == 0). Returns the persisted id. */
    suspend fun save(note: Note): Long {
        val now = System.currentTimeMillis()
        val entity = note.copy(updatedAt = now).toEntity()
        val items = if (note.isChecklist) {
            note.items.filter { it.text.isNotBlank() }.mapIndexed { index, item ->
                ChecklistItemEntity(noteId = note.id, text = item.text, isChecked = item.isChecked, position = index)
            }
        } else emptyList()
        val id = dao.upsert(entity, items)
        refreshWidget()
        return id
    }

    suspend fun setArchived(ids: List<Long>, archived: Boolean) {
        if (ids.isEmpty()) return
        dao.setArchived(ids, archived, System.currentTimeMillis())
        refreshWidget()
    }

    suspend fun setPinned(ids: List<Long>, pinned: Boolean) {
        if (ids.isEmpty()) return
        dao.setPinned(ids, pinned)
        refreshWidget()
    }

    suspend fun setColor(ids: List<Long>, color: NoteColor) {
        if (ids.isEmpty()) return
        dao.setColor(ids, color.ordinal)
        refreshWidget()
    }

    suspend fun delete(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.deleteNotes(ids)
        refreshWidget()
    }

    /** Toggle a checklist item straight from a list card / widget without opening the editor. */
    suspend fun setItemChecked(noteId: Long, itemId: Long, checked: Boolean) {
        dao.setItemChecked(itemId, checked)
        dao.touch(noteId, System.currentTimeMillis())
        refreshWidget()
    }

    /** Used by backup import. */
    suspend fun replaceAll(notes: List<Note>) {
        dao.deleteAllNotes()
        notes.forEach { insertRaw(it) }
        refreshWidget()
    }

    suspend fun importMerge(notes: List<Note>) {
        notes.forEach { insertRaw(it) }
        refreshWidget()
    }

    private suspend fun insertRaw(note: Note) {
        val entity = note.copy(id = 0).toEntity()
        val items = note.items.mapIndexed { index, item ->
            ChecklistItemEntity(noteId = 0, text = item.text, isChecked = item.isChecked, position = index)
        }
        dao.upsert(entity, items)
    }

    private suspend fun refreshWidget() {
        runCatching { NotesWidget().updateAll(context) }
    }
}
