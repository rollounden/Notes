package dev.apex.notes.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.apex.notes.NotesApp
import dev.apex.notes.data.Note
import dev.apex.notes.markdown.markdownPreview
import dev.apex.notes.ui.MainActivity

class NotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotesWidget()
}

/**
 * Responsive widget. Small: quick "new note" tile with a pinned count. Larger: scrollable list of
 * pinned notes (falls back to most recent). Tapping a row deep-links into that note.
 */
class NotesWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val MEDIUM = DpSize(180.dp, 110.dp)
        private val TALL = DpSize(180.dp, 220.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = NotesApp.from(context).repository
        val notes = repo.getTop(limit = 12)
        val pinnedCount = notes.count { it.isPinned }

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                Shell(openApp(context)) {
                    when {
                        size.width < MEDIUM.width -> SmallFace(context, notes, pinnedCount)
                        else -> ListFace(context, notes, pinnedCount, compact = size.height < TALL.height)
                    }
                }
            }
        }
    }

    @Composable
    private fun Shell(onClick: Action, content: @Composable () -> Unit) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(14.dp)
                .clickable(onClick),
        ) { content() }
    }

    @Composable
    private fun SmallFace(context: Context, notes: List<Note>, pinnedCount: Int) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Header(context, title = "Notes")
            Spacer(GlanceModifier.defaultWeight())
            val first = notes.firstOrNull()
            if (first == null) {
                Muted("Tap + to start")
            } else {
                Text(
                    first.displayTitle(),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    modifier = GlanceModifier.clickable(openNote(context, first.id)),
                )
                Spacer(GlanceModifier.height(2.dp))
                Muted(if (pinnedCount > 0) "$pinnedCount pinned" else "Latest")
            }
        }
    }

    @Composable
    private fun ListFace(context: Context, notes: List<Note>, pinnedCount: Int, compact: Boolean) {
        val shown = if (pinnedCount > 0) notes.filter { it.isPinned } else notes
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Header(context, title = if (pinnedCount > 0) "Pinned" else "Recent")
            Spacer(GlanceModifier.height(8.dp))
            if (shown.isEmpty()) {
                Muted("Nothing here yet. Tap + to jot something down.")
            } else {
                LazyColumn {
                    items(shown.take(if (compact) 3 else 12), itemId = { it.id }) { note ->
                        NoteRow(note, openNote(context, note.id))
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(context: Context, title: String) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier
                    .size(30.dp)
                    .cornerRadius(15.dp)
                    .background(GlanceTheme.colors.primary)
                    .clickable(newNote(context)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+",
                    style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }

    @Composable
    private fun NoteRow(note: Note, action: Action) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(action),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.isPinned) {
                    Box(
                        GlanceModifier
                            .size(6.dp)
                            .cornerRadius(3.dp)
                            .background(GlanceTheme.colors.primary),
                    ) {}
                    Spacer(GlanceModifier.width(8.dp))
                }
                Text(
                    note.displayTitle(),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
            val preview = note.previewLine()
            if (preview.isNotBlank() && note.title.isNotBlank()) {
                Text(
                    preview,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
    }

    @Composable
    private fun Muted(text: String) {
        Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 2)
    }

    private fun Note.displayTitle(): String =
        title.ifBlank { previewLine().ifBlank { if (isChecklist) "Checklist" else "Note" } }

    private fun Note.previewLine(): String = if (isChecklist) {
        val open = items.filter { !it.isChecked }
        when {
            items.isEmpty() -> ""
            open.isEmpty() -> "All ${items.size} done"
            else -> open.first().text + if (open.size > 1) "  +${open.size - 1}" else ""
        }
    } else {
        markdownPreview(body).lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun openApp(context: Context): Action = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    )

    private fun openNote(context: Context, id: Long): Action = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = MainActivity.ACTION_OPEN_NOTE
            putExtra(MainActivity.EXTRA_NOTE_ID, id)
        }
    )

    private fun newNote(context: Context): Action = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = MainActivity.ACTION_NEW_NOTE
        }
    )
}
