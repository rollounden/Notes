package dev.apex.notes.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.apex.notes.data.ChecklistItem
import dev.apex.notes.markdown.MarkdownText
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long,
    startAsChecklist: Boolean,
    initialText: String?,
    onBack: () -> Unit,
) {
    val viewModel = repoViewModel(key = "editor-$noteId") {
        NoteEditorViewModel(it, noteId, startAsChecklist, initialText)
    }
    val note = viewModel.note
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    var showColorSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val background = note.color.container(dark) ?: MaterialTheme.colorScheme.surface

    // Flush edits when the screen leaves the foreground (home button, screen off, etc.).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun leave() {
        viewModel.flush()
        onBack()
    }

    BackHandler { leave() }
    LaunchedEffect(viewModel.finished) { if (viewModel.finished) onBack() }

    fun share() {
        val text = note.asPlainText()
        if (text.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (note.title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, note.title)
        }
        context.startActivity(Intent.createChooser(intent, "Share note"))
    }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = viewModel::togglePin) {
                        Icon(
                            if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (note.isPinned) "Unpin" else "Pin",
                        )
                    }
                    if (!note.isChecklist) {
                        IconButton(onClick = { viewModel.previewMode = !viewModel.previewMode }) {
                            Icon(
                                if (viewModel.previewMode) Icons.Outlined.Edit else Icons.Outlined.Visibility,
                                contentDescription = if (viewModel.previewMode) "Edit" else "Preview",
                            )
                        }
                    }
                    IconButton(onClick = { showColorSheet = true }) {
                        Icon(Icons.Outlined.Palette, contentDescription = "Colour")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = { menuOpen = false; share() },
                            )
                            if (note.isChecklist) {
                                DropdownMenuItem(
                                    text = { Text("Uncheck all") },
                                    leadingIcon = { Icon(Icons.Outlined.Checklist, null) },
                                    onClick = { menuOpen = false; viewModel.uncheckAll() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete checked items") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                    onClick = { menuOpen = false; viewModel.deleteChecked() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Convert to text") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Notes, null) },
                                    onClick = { menuOpen = false; viewModel.convertToText() },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Convert to checklist") },
                                    leadingIcon = { Icon(Icons.Outlined.Checklist, null) },
                                    onClick = { menuOpen = false; viewModel.convertToChecklist() },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                                onClick = { menuOpen = false; viewModel.archive() },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menuOpen = false; showDeleteDialog = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (note.id == 0L) "New note" else "Edited ${formatEdited(note.updatedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (!note.isChecklist && !viewModel.previewMode) {
                    Text(
                        "Markdown supported",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        },
    ) { padding ->
        if (!viewModel.loaded) return@Scaffold
        Crossfade(targetState = note.isChecklist, label = "mode") { checklist ->
            if (checklist) {
                ChecklistEditor(viewModel, padding)
            } else {
                TextEditor(viewModel, padding, autoFocus = noteId == 0L && initialText.isNullOrEmpty())
            }
        }
    }

    if (showColorSheet) {
        ColorPickerSheet(
            selected = note.color,
            onSelect = { viewModel.setColor(it); showColorSheet = false },
            onDismiss = { showColorSheet = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete note?") },
            text = { Text("This permanently removes the note. Archive it instead if you might want it later.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.delete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
        )
    }
}

/* ------------------------------------ text mode ------------------------------------ */

@Composable
private fun TextEditor(viewModel: NoteEditorViewModel, padding: PaddingValues, autoFocus: Boolean) {
    val note = viewModel.note
    val titleFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (autoFocus) titleFocus.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        TitleField(
            value = note.title,
            onValueChange = viewModel::setTitle,
            focusRequester = titleFocus,
            onNext = { if (viewModel.previewMode) viewModel.previewMode = false else bodyFocus.requestFocus() },
        )
        Spacer(Modifier.size(12.dp))
        if (viewModel.previewMode) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
                    .clickable { viewModel.previewMode = false },
            ) {
                if (note.body.isBlank()) {
                    Text(
                        "Nothing here yet. Tap to write.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    MarkdownText(note.body, onToggleTask = viewModel::toggleMarkdownTask)
                }
            }
        } else {
            PlainField(
                value = note.body,
                onValueChange = viewModel::setBody,
                placeholder = "Note",
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).focusRequester(bodyFocus),
            )
        }
        Spacer(Modifier.size(80.dp))
    }
}

/* ---------------------------------- checklist mode ---------------------------------- */

@Composable
private fun ChecklistEditor(viewModel: NoteEditorViewModel, padding: PaddingValues) {
    val note = viewModel.note
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState) { a, b -> viewModel.moveItem(a, b) }
    val unchecked = note.items.filter { !it.isChecked }
    val checked = note.items.filter { it.isChecked }
    var showChecked by remember { mutableStateOf(true) }
    val uncheckedKeys = remember(unchecked) { unchecked.map { it.id }.toSet() }
    val titleFocus = remember { FocusRequester() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 8.dp, end = 20.dp, bottom = 80.dp),
    ) {
        item(key = "title") {
            TitleField(
                value = note.title,
                onValueChange = viewModel::setTitle,
                focusRequester = titleFocus,
                onNext = { viewModel.pendingFocusId = unchecked.firstOrNull()?.id },
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            )
        }
        items(unchecked, key = { it.id }) { item ->
            ChecklistRow(
                item = item,
                viewModel = viewModel,
                modifier = Modifier.reorderableItem(reorder, item.id),
                handle = Modifier.reorderHandle(reorder, item.id) { uncheckedKeys },
            )
        }
        item(key = "add") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.addItem(unchecked.lastOrNull()?.id) }
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(24.dp))
                Text("List item", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (checked.isNotEmpty()) {
            item(key = "checked-header") {
                Column {
                    HorizontalDivider(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showChecked = !showChecked }
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        Icon(
                            if (showChecked) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(24.dp))
                        Text(
                            "${checked.size} ticked",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (showChecked) {
                items(checked, key = { it.id }) { item ->
                    ChecklistRow(item = item, viewModel = viewModel, modifier = Modifier, handle = null)
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(
    item: ChecklistItem,
    viewModel: NoteEditorViewModel,
    modifier: Modifier,
    handle: Modifier?,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(viewModel.pendingFocusId) {
        if (viewModel.pendingFocusId == item.id) {
            focusRequester.requestFocus()
            viewModel.pendingFocusId = null
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        if (handle != null) {
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = "Reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = handle.size(24.dp),
            )
        } else {
            Spacer(Modifier.size(24.dp))
        }
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = { viewModel.setItemChecked(item.id, it) },
        )
        PlainField(
            value = item.text,
            onValueChange = { viewModel.setItemText(item.id, it) },
            placeholder = "List item",
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
            ),
            singleLine = true,
            imeAction = ImeAction.Next,
            onImeAction = { viewModel.addItem(afterId = item.id) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && item.text.isEmpty()) {
                        viewModel.removeItem(item.id)
                        true
                    } else false
                },
        )
        IconButton(onClick = { viewModel.removeItem(item.id) }) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove item",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/* ------------------------------------ shared fields ------------------------------------ */

@Composable
private fun TitleField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlainField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "Title",
        textStyle = MaterialTheme.typography.headlineSmall,
        singleLine = false,
        imeAction = ImeAction.Next,
        onImeAction = onNext,
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
    )
}

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
) {
    val color = if (textStyle.color.isSpecified) textStyle.color else LocalContentColor.current
    Box(modifier) {
        if (value.isEmpty()) {
            Text(placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle.copy(color = color),
            singleLine = singleLine,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = { onImeAction?.invoke() },
                onDone = { onImeAction?.invoke() },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun formatEdited(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMillis
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
    }
}