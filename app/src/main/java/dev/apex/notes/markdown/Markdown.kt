package dev.apex.notes.markdown

/**
 * A deliberately small Markdown subset, parsed into blocks. No external dependencies.
 *
 * Supported: `#`..`###` headings, paragraphs, `-`/`*`/`+` bullets, `1.` numbered lists,
 * `- [ ]` / `- [x]` tasks, `>` quotes, fenced ``` code blocks, `---` rules, and inline
 * **bold**, *italic*, `code`, ~~strike~~.
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val depth: Int, val text: String) : MdBlock
    data class Numbered(val depth: Int, val number: Int, val text: String) : MdBlock
    /** [line] is the index in the source so a tap can flip the checkbox in place. */
    data class Task(val depth: Int, val checked: Boolean, val text: String, val line: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data object Rule : MdBlock
}

private val headingRegex = Regex("""^(#{1,3})\s+(.*)$""")
private val bulletRegex = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val taskRegex = Regex("""^(\s*)[-*+]\s+\[([ xX])]\s*(.*)$""")
private val numberedRegex = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
private val ruleRegex = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")

fun parseMarkdown(source: String): List<MdBlock> {
    val lines = source.lines()
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    var inCode = false
    val code = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(paragraph.toString().trim())
            paragraph.setLength(0)
        }
    }

    lines.forEachIndexed { index, raw ->
        val line = raw.trimEnd()
        if (inCode) {
            if (line.trim().startsWith("```")) {
                blocks += MdBlock.Code(code.toString().trimEnd('\n'))
                code.setLength(0)
                inCode = false
            } else {
                code.append(raw).append('\n')
            }
            return@forEachIndexed
        }
        if (line.trim().startsWith("```")) {
            flushParagraph()
            inCode = true
            return@forEachIndexed
        }
        if (line.isBlank()) {
            flushParagraph()
            return@forEachIndexed
        }
        headingRegex.find(line)?.let {
            flushParagraph()
            blocks += MdBlock.Heading(it.groupValues[1].length, it.groupValues[2].trim())
            return@forEachIndexed
        }
        if (ruleRegex.matches(line)) {
            flushParagraph()
            blocks += MdBlock.Rule
            return@forEachIndexed
        }
        taskRegex.find(line)?.let {
            flushParagraph()
            blocks += MdBlock.Task(
                depth = it.groupValues[1].length / 2,
                checked = it.groupValues[2] != " ",
                text = it.groupValues[3],
                line = index,
            )
            return@forEachIndexed
        }
        bulletRegex.find(line)?.let {
            flushParagraph()
            blocks += MdBlock.Bullet(it.groupValues[1].length / 2, it.groupValues[2])
            return@forEachIndexed
        }
        numberedRegex.find(line)?.let {
            flushParagraph()
            blocks += MdBlock.Numbered(it.groupValues[1].length / 2, it.groupValues[2].toInt(), it.groupValues[3])
            return@forEachIndexed
        }
        if (line.startsWith(">")) {
            flushParagraph()
            val text = line.removePrefix(">").trim()
            val last = blocks.lastOrNull()
            if (last is MdBlock.Quote) {
                blocks[blocks.lastIndex] = MdBlock.Quote(last.text + "\n" + text)
            } else {
                blocks += MdBlock.Quote(text)
            }
            return@forEachIndexed
        }
        if (paragraph.isNotEmpty()) paragraph.append('\n')
        paragraph.append(line)
    }
    if (inCode) blocks += MdBlock.Code(code.toString().trimEnd('\n'))
    flushParagraph()
    return blocks
}

/** Flip the checkbox on the given source line (`[ ]` <-> `[x]`). */
fun toggleTaskLine(source: String, line: Int): String {
    val lines = source.lines().toMutableList()
    if (line !in lines.indices) return source
    val current = lines[line]
    lines[line] = when {
        current.contains("[ ]") -> current.replaceFirst("[ ]", "[x]")
        current.contains("[x]") -> current.replaceFirst("[x]", "[ ]")
        current.contains("[X]") -> current.replaceFirst("[X]", "[ ]")
        else -> current
    }
    return lines.joinToString("\n")
}

/** Plain-text rendering (markup stripped) for card previews and search snippets. */
fun markdownPreview(source: String): String {
    val out = StringBuilder()
    parseMarkdown(source).forEach { block ->
        when (block) {
            is MdBlock.Heading -> out.appendLine(stripInline(block.text))
            is MdBlock.Paragraph -> out.appendLine(stripInline(block.text))
            is MdBlock.Bullet -> out.appendLine("  ".repeat(block.depth) + "• " + stripInline(block.text))
            is MdBlock.Numbered -> out.appendLine("  ".repeat(block.depth) + "${block.number}. " + stripInline(block.text))
            is MdBlock.Task -> out.appendLine("  ".repeat(block.depth) + (if (block.checked) "☑ " else "☐ ") + stripInline(block.text))
            is MdBlock.Quote -> out.appendLine("“" + stripInline(block.text) + "”")
            is MdBlock.Code -> out.appendLine(block.text)
            MdBlock.Rule -> out.appendLine("—")
        }
    }
    return out.toString().trimEnd()
}

private val inlineMarkers = Regex("""(\*\*|__|~~|\*|_|`)""")

internal fun stripInline(text: String): String = text.replace(inlineMarkers, "")

/** True when the body uses any Markdown syntax worth rendering. */
fun looksLikeMarkdown(source: String): Boolean {
    if (source.isBlank()) return false
    val blocks = parseMarkdown(source)
    if (blocks.any { it !is MdBlock.Paragraph }) return true
    return Regex("""(\*\*[^*]+\*\*|`[^`]+`|~~[^~]+~~|(?<!\w)\*[^*\s][^*]*\*(?!\w)|(?<!\w)_[^_\s][^_]*_(?!\w))""").containsMatchIn(source)
}
