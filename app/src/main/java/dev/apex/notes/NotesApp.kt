package dev.apex.notes

import android.app.Application
import dev.apex.notes.data.NotesDatabase
import dev.apex.notes.data.NotesRepository

class NotesApp : Application() {

    val database: NotesDatabase by lazy { NotesDatabase.build(this) }
    val repository: NotesRepository by lazy { NotesRepository(this, database.notesDao()) }

    companion object {
        fun from(context: android.content.Context): NotesApp =
            context.applicationContext as NotesApp
    }
}
