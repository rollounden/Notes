package dev.apex.notes.backup

import android.content.Context
import android.net.Uri
import dev.apex.notes.data.ChecklistItem
import dev.apex.notes.data.Note
import dev.apex.notes.data.NoteColor
import dev.apex.notes.data.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Wire format. Keep field names stable; bump [BackupFile.version] when they change. */
@Serializable
data class BackupFile(
    val app: String = "dev.apex.notes",
    val version: Int = 1,
    @SerialName("exported_at") val exportedAt: Long,
    val notes: List<BackupNote>,
)

@Serializable
data class BackupNote(
    val title: String,
    val body: String = "",
    @SerialName("is_checklist") val isChecklist: Boolean = false,
    val color: String = "NONE",
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    val items: List<BackupItem> = emptyList(),
)

@Serializable
data class BackupItem(val text: String, val checked: Boolean = false)

enum class ImportMode { MERGE, REPLACE }

data class ImportResult(val imported: Int, val skippedDuplicates: Int)

class BackupManager(private val context: Context, private val repo: NotesRepository) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun suggestedFileName(): String =
        "notes-backup-${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))}.json"

    /** Serialise every note (including archived) to the SAF-provided [uri]. Returns the count. */
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val notes = repo.getAll()
        val file = BackupFile(
            exportedAt = System.currentTimeMillis(),
            notes = notes.map { it.toBackup() },
        )
        val text = json.encodeToString(BackupFile.serializer(), file)
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("Could not open the chosen location for writing")
        notes.size
    }

    /** Read and validate a backup without touching the database. */
    suspend fun peek(uri: Uri): BackupFile = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Could not read the selected file")
        val parsed = json.decodeFromString(BackupFile.serializer(), text)
        require(parsed.app == "dev.apex.notes") { "This file was not made by Notes" }
        require(parsed.version <= 1) { "This backup is from a newer version of Notes" }
        parsed
    }

    suspend fun import(file: BackupFile, mode: ImportMode): ImportResult = withContext(Dispatchers.IO) {
        val incoming = file.notes.map { it.toDomain() }
        when (mode) {
            ImportMode.REPLACE -> {
                repo.replaceAll(incoming)
                ImportResult(incoming.size, 0)
            }
            ImportMode.MERGE -> {
                // Skip notes that already exist verbatim so re-importing the same file is idempotent.
                val existing = repo.getAll().map { it.fingerprint() }.toHashSet()
                val fresh = incoming.filter { it.fingerprint() !in existing }
                repo.importMerge(fresh)
                ImportResult(fresh.size, incoming.size - fresh.size)
            }
        }
    }

    private fun Note.fingerprint(): String =
        listOf(title, body, isChecklist, createdAt, items.joinToString("\u0001") { it.text + (if (it.isChecked) "1" else "0") })
            .joinToString("\u0000")

    private fun Note.toBackup() = BackupNote(
        title = title,
        body = body,
        isChecklist = isChecklist,
        color = color.name,
        isPinned = isPinned,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
        items = items.map { BackupItem(it.text, it.isChecked) },
    )

    private fun BackupNote.toDomain() = Note(
        id = 0,
        title = title,
        body = body,
        isChecklist = isChecklist,
        color = runCatching { NoteColor.valueOf(color) }.getOrDefault(NoteColor.NONE),
        isPinned = isPinned,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
        items = items.mapIndexed { index, item -> ChecklistItem(id = -(index + 1L), text = item.text, isChecked = item.checked) },
    )
}
