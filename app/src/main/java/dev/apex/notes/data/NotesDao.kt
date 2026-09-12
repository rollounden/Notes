package dev.apex.notes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {

    @Transaction
    @Query("SELECT * FROM notes WHERE is_archived = 0 ORDER BY is_pinned DESC, updated_at DESC")
    fun observeActive(): Flow<List<NoteWithItems>>

    @Transaction
    @Query("SELECT * FROM notes WHERE is_archived = 1 ORDER BY updated_at DESC")
    fun observeArchived(): Flow<List<NoteWithItems>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNote(id: Long): NoteWithItems?

    @Transaction
    @Query("SELECT * FROM notes WHERE is_archived = 0 AND is_pinned = 1 ORDER BY updated_at DESC")
    suspend fun getPinned(): List<NoteWithItems>

    @Transaction
    @Query("SELECT * FROM notes ORDER BY updated_at DESC")
    suspend fun getAll(): List<NoteWithItems>

    @Transaction
    @Query("SELECT * FROM notes WHERE is_archived = 0 ORDER BY is_pinned DESC, updated_at DESC LIMIT :limit")
    suspend fun getTop(limit: Int): List<NoteWithItems>

    @Query("SELECT COUNT(*) FROM notes WHERE is_archived = 0 AND is_pinned = 1")
    suspend fun countPinned(): Int

    @Insert
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Insert
    suspend fun insertItems(items: List<ChecklistItemEntity>)

    @Query("DELETE FROM checklist_items WHERE note_id = :noteId")
    suspend fun deleteItemsFor(noteId: Long)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteNotes(ids: List<Long>)

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    @Query("UPDATE notes SET is_archived = :archived, updated_at = :now WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<Long>, archived: Boolean, now: Long)

    @Query("UPDATE notes SET is_pinned = :pinned WHERE id IN (:ids)")
    suspend fun setPinned(ids: List<Long>, pinned: Boolean)

    @Query("UPDATE notes SET color = :color WHERE id IN (:ids)")
    suspend fun setColor(ids: List<Long>, color: Int)

    @Query("UPDATE checklist_items SET is_checked = :checked WHERE id = :itemId")
    suspend fun setItemChecked(itemId: Long, checked: Boolean)

    @Query("UPDATE notes SET updated_at = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    /** Insert or update a note and replace its checklist items atomically. Returns the note id. */
    @Transaction
    suspend fun upsert(note: NoteEntity, items: List<ChecklistItemEntity>): Long {
        val id = if (note.id == 0L) insertNote(note) else {
            updateNote(note)
            note.id
        }
        deleteItemsFor(id)
        if (items.isNotEmpty()) {
            insertItems(items.mapIndexed { index, item -> item.copy(id = 0, noteId = id, position = index) })
        }
        return id
    }

    /**
     * Backup import. Runs as one transaction so a failure part-way (bad row, crash, full disk)
     * rolls back and the notes that were there before are untouched. With [replace] the existing
     * notes are deleted inside the same transaction, so "Replace" can never leave the DB empty.
     */
    @Transaction
    suspend fun importAll(notes: List<Pair<NoteEntity, List<ChecklistItemEntity>>>, replace: Boolean) {
        if (replace) deleteAllNotes()
        for ((note, items) in notes) upsert(note.copy(id = 0), items)
    }
}
