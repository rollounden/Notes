package dev.apex.notes.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    @ColumnInfo(name = "is_checklist") val isChecklist: Boolean = false,
    val color: Int = 0,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("note_id")],
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "note_id") val noteId: Long,
    val text: String,
    @ColumnInfo(name = "is_checked") val isChecked: Boolean = false,
    val position: Int,
)

data class NoteWithItems(
    @Embedded val note: NoteEntity,
    @Relation(parentColumn = "id", entityColumn = "note_id")
    val items: List<ChecklistItemEntity>,
) {
    fun toDomain(): Note = Note(
        id = note.id,
        title = note.title,
        body = note.body,
        isChecklist = note.isChecklist,
        color = NoteColor.fromIndex(note.color),
        isPinned = note.isPinned,
        isArchived = note.isArchived,
        createdAt = note.createdAt,
        updatedAt = note.updatedAt,
        items = items.sortedBy { it.position }.map {
            ChecklistItem(id = it.id, text = it.text, isChecked = it.isChecked)
        },
    )
}
