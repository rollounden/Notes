package dev.apex.notes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.apex.notes.data.Note
import dev.apex.notes.data.NoteColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    onOpenNote: (Long) -> Unit,
    onNewNote: (checklist: Boolean) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    val viewModel = repoViewModel { NotesListViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showColorSheet by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = state.inSelectionMode) { viewModel.clearSelection() }
    BackHandler(enabled = !state.inSelectionMode && fabExpanded) { fabExpanded = false }

    fun archiveWithUndo(ids: List<Long>) {
        val label = if (ids.size == 1) "Note archived" else "${ids.size} notes archived"
        viewModel.archive(ids) {
            scope.launch {
                val result = snackbar.showSnackbar(label, actionLabel = "Undo", withDismissAction = true)
                if (result == SnackbarResult.ActionPerformed) viewModel.unarchive(ids)
            }
        }
    }

    fun deleteWithUndo(ids: List<Long>) {
        val backup = viewModel.snapshot(ids)
        viewModel.delete(ids)
        scope.launch {
            val label = if (ids.size == 1) "Note deleted" else "${ids.size} notes deleted"
            val result = snackbar.showSnackbar(label, actionLabel = "Undo", withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(backup)
        }
    }

    Scaffold(
        topBar = {
            if (state.inSelectionMode) {
                SelectionTopBar(
                    count = state.selection.size,
                    allPinned = (state.pinned.filter { it.id in state.selection }.size == state.selection.size),
                    onClose = viewModel::clearSelection,
                    onPin = { pin -> viewModel.setPinned(state.selection, pin) },
                    onColor = { showColorSheet = true },
                    onArchive = { archiveWithUndo(state.selection.toList()) },
                    onDelete = { deleteWithUndo(state.selection.toList()) },
                )
            } else {
                SearchTopBar(
                    query = state.query,
                    onQueryChange = viewModel::setQuery,
                    onOpenArchive = onOpenArchive,
                    onOpenBackup = onOpenBackup,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!state.inSelectionMode) {
                NewNoteFab(
                    expanded = fabExpanded,
                    onToggle = { fabExpanded = !fabExpanded },
                    onNewText = { fabExpanded = false; onNewNote(false) },
                    onNewChecklist = { fabExpanded = false; onNewNote(true) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.availableColors.isNotEmpty() && !state.inSelectionMode) {
                ColorFilterRow(
                    colors = state.availableColors,
                    selected = state.colorFilter,
                    onSelect = viewModel::setColorFilter,
                )
            }
            when {
                !state.loaded -> Box(Modifier.fillMaxSize())
                state.totalCount == 0 -> EmptyState(
                    icon = { Icon(Icons.AutoMirrored.Outlined.Notes, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    title = "No notes yet",
                    subtitle = "Tap + to jot something down. Everything stays on this device.",
                )
                state.pinned.isEmpty() && state.others.isEmpty() -> EmptyState(
                    icon = { Icon(Icons.Filled.Search, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    title = "Nothing matches",
                    subtitle = "Try a different search or clear the colour filter.",
                )
                else -> NotesGrid(
                    pinned = state.pinned,
                    others = state.others,
                    selection = state.selection,
                    showSectionHeaders = state.pinned.isNotEmpty(),
                    onClick = { note ->
                        focusManager.clearFocus()
                        if (state.inSelectionMode) viewModel.toggleSelected(note.id) else onOpenNote(note.id)
                    },
                    onLongClick = { note -> viewModel.toggleSelected(note.id) },
                    onSwipeAway = { note -> archiveWithUndo(listOf(note.id)) },
                    onToggleItem = { note, itemId, checked -> viewModel.toggleItem(note.id, itemId, checked) },
                    swipeEnabled = !state.inSelectionMode,
                )
            }
        }
    }

    if (showColorSheet) {
        val selectedNotes = (state.pinned + state.others).filter { it.id in state.selection }
        val common = selectedNotes.map { it.color }.distinct().singleOrNull()
        ColorPickerSheet(
            selected = common,
            onSelect = { color ->
                viewModel.setColor(state.selection, color)
                showColorSheet = false
            },
            onDismiss = { showColorSheet = false },
        )
    }
}

/* ---------------------------------------------------------------------------------------- */

@Composable
fun NotesGrid(
    pinned: List<Note>,
    others: List<Note>,
    selection: Set<Long>,
    showSectionHeaders: Boolean,
    onClick: (Note) -> Unit,
    onLongClick: (Note) -> Unit,
    onSwipeAway: ((Note) -> Unit)?,
    onToggleItem: ((Note, Long, Boolean) -> Unit)?,
    swipeEnabled: Boolean = true,
    swipeIcon: ImageVector = Icons.Outlined.Archive,
    contentPadding: PaddingValues = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        if (showSectionHeaders && pinned.isNotEmpty()) {
            item(key = "hdr-pinned", span = StaggeredGridItemSpan.FullLine) { SectionHeader("Pinned") }
        }
        items(pinned, key = { it.id }) { note ->
            GridNote(note, note.id in selection, onClick, onLongClick, onSwipeAway, onToggleItem, swipeEnabled, swipeIcon)
        }
        if (showSectionHeaders && others.isNotEmpty()) {
            item(key = "hdr-others", span = StaggeredGridItemSpan.FullLine) { SectionHeader("Others") }
        }
        items(others, key = { it.id }) { note ->
            GridNote(note, note.id in selection, onClick, onLongClick, onSwipeAway, onToggleItem, swipeEnabled, swipeIcon)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GridNote(
    note: Note,
    selected: Boolean,
    onClick: (Note) -> Unit,
    onLongClick: (Note) -> Unit,
    onSwipeAway: ((Note) -> Unit)?,
    onToggleItem: ((Note, Long, Boolean) -> Unit)?,
    swipeEnabled: Boolean,
    swipeIcon: ImageVector,
) {
    val card: @Composable () -> Unit = {
        NoteCard(
            note = note,
            selected = selected,
            onClick = { onClick(note) },
            onLongClick = { onLongClick(note) },
            onToggleItem = if (onToggleItem != null && !selected) { id, checked -> onToggleItem(note, id, checked) } else null,
        )
    }
    if (onSwipeAway == null) {
        card()
        return
    }
    key(note.id) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) {
                    onSwipeAway(note)
                    true
                } else false
            },
            positionalThreshold = { total -> total * 0.45f },
        )
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                Box(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
                ) {
                    Icon(swipeIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            gesturesEnabled = swipeEnabled,
        ) {
            card()
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
fun EmptyState(icon: @Composable () -> Unit, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/* ---------------------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Surface(color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "Search your notes",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                                onClick = { menuOpen = false; onOpenArchive() },
                            )
                            DropdownMenuItem(
                                text = { Text("Backup & restore") },
                                leadingIcon = { Icon(Icons.Outlined.SaveAlt, null) },
                                onClick = { menuOpen = false; onOpenBackup() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    allPinned: Boolean,
    onClose: () -> Unit,
    onPin: (Boolean) -> Unit,
    onColor: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Clear selection") }
        },
        actions = {
            IconButton(onClick = { onPin(!allPinned) }) {
                Icon(
                    if (allPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (allPinned) "Unpin" else "Pin",
                )
            }
            IconButton(onClick = onColor) { Icon(Icons.Outlined.Palette, contentDescription = "Colour") }
            IconButton(onClick = onArchive) { Icon(Icons.Outlined.Archive, contentDescription = "Archive") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

@Composable
private fun ColorFilterRow(colors: List<NoteColor>, selected: NoteColor?, onSelect: (NoteColor?) -> Unit) {
    val dark = isSystemInDarkTheme()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
            )
        }
        items(colors.size) { index ->
            val color = colors[index]
            FilterChip(
                selected = selected == color,
                onClick = { onSelect(color) },
                label = { Text(color.label) },
                leadingIcon = { ColorDot(color.swatch(dark)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.container(dark) ?: MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun NewNoteFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    onNewText: () -> Unit,
    onNewChecklist: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FabMenuItem(label = "Checklist", icon = { Icon(Icons.Outlined.Checklist, null) }, onClick = onNewChecklist)
                FabMenuItem(label = "Text note", icon = { Icon(Icons.Outlined.Edit, null) }, onClick = onNewText)
            }
        }
        FloatingActionButton(onClick = onToggle) {
            val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = "fab")
            Icon(
                Icons.Filled.Add,
                contentDescription = if (expanded) "Close" else "New note",
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

@Composable
private fun FabMenuItem(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ) { icon() }
    }
}
