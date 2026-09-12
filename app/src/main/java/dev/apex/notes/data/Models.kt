package dev.apex.notes.data

/** UI-facing note. `id == 0` means it has not been persisted yet. */
data class Note(
    val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val isChecklist: Boolean = false,
    val color: NoteColor = NoteColor.NONE,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val items: List<ChecklistItem> = emptyList(),
) {
    val isEmpty: Boolean
        get() = title.isBlank() &&
            (if (isChecklist) items.all { it.text.isBlank() } else body.isBlank())

    /** Plain-text rendering used for share and search. */
    fun asPlainText(): String = buildString {
        if (title.isNotBlank()) appendLine(title).appendLine()
        if (isChecklist) {
            items.forEach { appendLine((if (it.isChecked) "[x] " else "[ ] ") + it.text) }
        } else {
            append(body)
        }
    }.trimEnd()

    fun toEntity(): NoteEntity = NoteEntity(
        id = id,
        title = title,
        body = body,
        isChecklist = isChecklist,
        color = color.ordinal,
        isPinned = isPinned,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

data class ChecklistItem(
    /** Stable key for Compose. Negative ids are client-side temporaries before first save. */
    val id: Long,
    val text: String = "",
    val isChecked: Boolean = false,
)
