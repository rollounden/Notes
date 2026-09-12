package dev.apex.notes.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.apex.notes.NotesApp
import dev.apex.notes.backup.BackupFile
import dev.apex.notes.backup.BackupManager
import dev.apex.notes.backup.ImportMode
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { BackupManager(context, NotesApp.from(context).repository) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<BackupFile?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = runCatching { manager.export(uri) }
            busy = false
            snackbar.showSnackbar(
                result.fold(
                    onSuccess = { "Exported $it note${if (it == 1) "" else "s"}" },
                    onFailure = { "Export failed: ${it.message}" },
                )
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = runCatching { manager.peek(uri) }
            busy = false
            result.fold(
                onSuccess = { pendingImport = it },
                onFailure = { snackbar.showSnackbar("Couldn't read backup: ${it.message}") },
            )
        }
    }

    fun runImport(file: BackupFile, mode: ImportMode) {
        pendingImport = null
        scope.launch {
            busy = true
            val result = runCatching { manager.import(file, mode) }
            busy = false
            snackbar.showSnackbar(
                result.fold(
                    onSuccess = {
                        buildString {
                            append("Imported ${it.imported} note${if (it.imported == 1) "" else "s"}")
                            if (it.skippedDuplicates > 0) append(", skipped ${it.skippedDuplicates} already present")
                        }
                    },
                    onFailure = { "Import failed: ${it.message}" },
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & restore") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Notes never leave this device on their own. A backup is a plain JSON file you " +
                        "choose where to save, so it works with GrapheneOS storage scopes and needs no permissions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ActionCard(
                icon = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                title = "Export",
                body = "Save every note, including archived ones, to a JSON file.",
                buttonLabel = "Export to file",
                enabled = !busy,
                onClick = { exportLauncher.launch(manager.suggestedFileName()) },
            )

            ActionCard(
                icon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                title = "Import",
                body = "Restore from a backup. You'll be asked whether to merge with, or replace, the notes already here.",
                buttonLabel = "Choose backup file",
                enabled = !busy,
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
                tonal = true,
            )

            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Text("Working…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    pendingImport?.let { file ->
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(file.exportedAt))
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import ${file.notes.size} note${if (file.notes.size == 1) "" else "s"}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup from $date.")
                    Text(
                        "Merge keeps your current notes and adds the ones from the file (exact duplicates are skipped). " +
                            "Replace deletes everything here first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { runImport(file, ImportMode.REPLACE) }) { Text("Replace") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { runImport(file, ImportMode.MERGE) }) { Text("Merge") }
                }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActionCard(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    buttonLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tonal: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                icon()
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (tonal) {
                FilledTonalButton(onClick = onClick, enabled = enabled) { Text(buttonLabel) }
            } else {
                Button(onClick = onClick, enabled = enabled) { Text(buttonLabel) }
            }
        }
    }
}
