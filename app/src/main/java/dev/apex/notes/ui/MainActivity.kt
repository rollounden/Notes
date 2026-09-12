package dev.apex.notes.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

/** Where the app should go because of an incoming intent (widget tap, shared text). */
sealed interface Destination {
    data class Note(val id: Long) : Destination
    data class New(val checklist: Boolean, val text: String?) : Destination
}

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_OPEN_NOTE = "dev.apex.notes.action.OPEN_NOTE"
        const val ACTION_NEW_NOTE = "dev.apex.notes.action.NEW_NOTE"
        const val EXTRA_NOTE_ID = "note_id"
    }

    private var pending by mutableStateOf<Destination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pending = intent?.toDestination()
        setContent {
            NotesTheme {
                NotesNavHost(
                    pending = pending,
                    onPendingConsumed = { pending = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.toDestination()?.let { pending = it }
    }

    private fun Intent.toDestination(): Destination? = when (action) {
        ACTION_OPEN_NOTE -> getLongExtra(EXTRA_NOTE_ID, 0L).takeIf { it != 0L }?.let { Destination.Note(it) }
        ACTION_NEW_NOTE -> Destination.New(checklist = false, text = null)
        Intent.ACTION_SEND -> {
            val text = getStringExtra(Intent.EXTRA_TEXT)
            val subject = getStringExtra(Intent.EXTRA_SUBJECT)
            val combined = listOfNotNull(subject?.takeIf { it.isNotBlank() }, text).joinToString("\n\n").ifBlank { null }
            if (combined != null) Destination.New(checklist = false, text = combined) else null
        }
        else -> null
    }
}

/** Shared text is handed to the editor out-of-band so it never has to be URL-encoded into a route. */
object SharedTextHolder {
    var text: String? = null
    fun take(): String? = text.also { text = null }
}

@Composable
private fun NotesNavHost(pending: Destination?, onPendingConsumed: () -> Unit) {
    val nav = rememberNavController()

    LaunchedEffect(pending) {
        when (pending) {
            null -> Unit
            is Destination.Note -> nav.openNote(pending.id)
            is Destination.New -> {
                SharedTextHolder.text = pending.text
                nav.newNote(pending.checklist, shared = pending.text != null)
            }
        }
        if (pending != null) onPendingConsumed()
    }

    NavHost(
        navController = nav,
        startDestination = "list",
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) + fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) + fadeOut(tween(300)) },
    ) {
        composable("list") {
            NotesListScreen(
                onOpenNote = nav::openNote,
                onNewNote = { checklist -> nav.newNote(checklist, shared = false) },
                onOpenArchive = { nav.navigate("archive") },
                onOpenBackup = { nav.navigate("backup") },
            )
        }
        composable("archive") {
            ArchiveScreen(onOpenNote = nav::openNote, onBack = { nav.popBackStack() })
        }
        composable("backup") {
            BackupScreen(onBack = { nav.popBackStack() })
        }
        composable(
            route = "note/{id}?checklist={checklist}&shared={shared}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("checklist") { type = NavType.BoolType; defaultValue = false },
                navArgument("shared") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val checklist = entry.arguments?.getBoolean("checklist") ?: false
            val shared = entry.arguments?.getBoolean("shared") ?: false
            val initialText = remember { if (shared) SharedTextHolder.take() else null }
            NoteEditorScreen(
                noteId = id,
                startAsChecklist = checklist,
                initialText = initialText,
                onBack = { nav.popBackStack() },
            )
        }
    }
}

private fun NavHostController.openNote(id: Long) {
    navigate("note/$id") { launchSingleTop = true }
}

private fun NavHostController.newNote(checklist: Boolean, shared: Boolean) {
    navigate("note/0?checklist=$checklist&shared=$shared")
}
