package dev.apex.notes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.apex.notes.data.Note
import dev.apex.notes.data.NotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArchiveViewModel(private val repo: NotesRepository) : ViewModel() {
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    data class State(val loaded: Boolean = false, val notes: List<Note> = emptyList(), val selection: Set<Long> = emptySet())

    val state = combine(repo.archivedNotes, selection) { notes, sel ->
        State(true, notes, sel.filterTo(mutableSetOf()) { id -> notes.any { it.id == id } })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun toggle(id: Long) = selection.update { if (id in it) it - id else it + id }
    fun clear() = selection.update { emptySet() }

    fun restore(ids: Collection<Long>) = viewModelScope.launch {
        repo.setArchived(ids.toList(), false)
        selection.update { it - ids.toSet() }
    }

    fun archiveAgain(ids: Collection<Long>) = viewModelScope.launch { repo.setArchived(ids.toList(), true) }

    fun snapshot(ids: Collection<Long>): List<Note> = state.value.notes.filter { it.id in ids }

    fun delete(ids: Collection<Long>) = viewModelScope.launch {
        repo.delete(ids.toList())
        selection.update { it - ids.toSet() }
    }

    fun undelete(notes: List<Note>) = viewModelScope.launch { repo.importMerge(notes) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(onOpenNote: (Long) -> Unit, onBack: () -> Unit) {
    val viewModel = repoViewModel { ArchiveViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf<List<Long>?>(null) }
    val selecting = state.selection.isNotEmpty()

    BackHandler(enabled = selecting) { viewModel.clear() }

    fun restoreWithUndo(ids: List<Long>) {
        viewModel.restore(ids)
        scope.launch {
            val label = if (ids.size == 1) "Note restored" else "${ids.size} notes restored"
            if (snackbar.showSnackbar(label, actionLabel = "Undo", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                viewModel.archiveAgain(ids)
            }
        }
    }

    fun deleteWithUndo(ids: List<Long>) {
        val backup = viewModel.snapshot(ids)
        viewModel.delete(ids)
        scope.launch {
            val label = if (ids.size == 1) "Note deleted" else "${ids.size} notes deleted"
            if (snackbar.showSnackbar(label, actionLabel = "Undo", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                viewModel.undelete(backup)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selecting) "${state.selection.size} selected" else "Archive") },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = viewModel::clear) { Icon(Icons.Filled.Close, contentDescription = "Clear selection") }
                    } else {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(onClick = { restoreWithUndo(state.selection.toList()) }) {
                            Icon(Icons.Outlined.Unarchive, contentDescription = "Restore")
                        }
                        IconButton(onClick = { confirmDelete = state.selection.toList() }) {
                            Icon(Icons.Outlined.DeleteForever, contentDescription = "Delete forever")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (selecting) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.loaded -> Unit
                state.notes.isEmpty() -> EmptyState(
                    icon = { Icon(Icons.Outlined.Archive, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    title = "Archive is empty",
                    subtitle = "Swipe a note away or use the menu to tuck it here without deleting it.",
                )
                else -> NotesGrid(
                    pinned = emptyList(),
                    others = state.notes,
                    selection = state.selection,
                    showSectionHeaders = false,
                    onClick = { note -> if (selecting) viewModel.toggle(note.id) else onOpenNote(note.id) },
                    onLongClick = { note -> viewModel.toggle(note.id) },
                    onSwipeAway = { note -> restoreWithUndo(listOf(note.id)) },
                    onToggleItem = null,
                    swipeEnabled = !selecting,
                    swipeIcon = Icons.Outlined.Unarchive,
                )
            }
        }
    }

    confirmDelete?.let { ids ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (ids.size == 1) "Delete note forever?" else "Delete ${ids.size} notes forever?") },
            text = { Text("You can undo from the snackbar for a few seconds, after that they are gone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; deleteWithUndo(ids) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}
