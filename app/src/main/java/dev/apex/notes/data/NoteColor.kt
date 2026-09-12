package dev.apex.notes.data

import androidx.compose.ui.graphics.Color

/**
 * Colour labels for notes. Index 0 is "no colour" so the default DB value of 0 means unlabelled.
 * Tones are picked so they read well against both light and dark Material You surfaces.
 */
enum class NoteColor(val label: String, private val light: Long, private val dark: Long) {
    NONE("None", 0x00000000, 0x00000000),
    RED("Red", 0xFFFFDAD6, 0xFF5C2B29),
    ORANGE("Orange", 0xFFFFDDB8, 0xFF5A3A17),
    YELLOW("Yellow", 0xFFFFF0B3, 0xFF574A12),
    GREEN("Green", 0xFFCDEFC7, 0xFF244B27),
    TEAL("Teal", 0xFFB9EEE9, 0xFF124B47),
    BLUE("Blue", 0xFFD3E4FF, 0xFF1E3A5F),
    PURPLE("Purple", 0xFFEADDFF, 0xFF3E2E5E),
    PINK("Pink", 0xFFFFD8EC, 0xFF5B2A44);

    fun container(isDark: Boolean): Color? =
        if (this == NONE) null else Color(if (isDark) dark else light)

    /** A saturated swatch used for pickers and filter chips. */
    fun swatch(isDark: Boolean): Color = when (this) {
        NONE -> Color.Transparent
        else -> Color(if (isDark) dark else light)
    }

    companion object {
        fun fromIndex(index: Int): NoteColor = entries.getOrElse(index) { NONE }
    }
}
