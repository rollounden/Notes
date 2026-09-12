package dev.apex.notes.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.apex.notes.NotesApp
import dev.apex.notes.data.NotesRepository

/** Obtain a ViewModel wired to the app's single repository, no DI framework needed. */
@Composable
inline fun <reified VM : ViewModel> repoViewModel(
    key: String? = null,
    crossinline create: (NotesRepository) -> VM,
): VM {
    val repo = NotesApp.from(LocalContext.current).repository
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(repo) } },
    )
}
