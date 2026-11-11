package com.minimal.pissh

import androidx.compose.ui.text.AnnotatedString

/**
 * Represents a text selection range in the monospace terminal buffer.
 * Coordinates are 0-indexed row and column positions.
 */
data class TerminalSelection(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int
) {
    val fromRow: Int get() = if (startRow < endRow || (startRow == endRow && startCol <= endCol)) startRow else endRow
    val fromCol: Int get() = if (startRow < endRow || (startRow == endRow && startCol <= endCol)) startCol else endCol
    val toRow: Int get() = if (startRow > endRow || (startRow == endRow && startCol > endCol)) startRow else endRow
    val toCol: Int get() = if (startRow > endRow || (startRow == endRow && startCol > endCol)) startCol else endCol

    /**
     * Returns true if the cell at [row], [col] is inside this selection range.
     */
    fun contains(row: Int, col: Int): Boolean {
        if (row < fromRow || row > toRow) return false
        if (row == fromRow && row == toRow) return col in fromCol..toCol
        if (row == fromRow) return col >= fromCol
        if (row == toRow) return col <= toCol
        return true
    }

    /**
     * Returns the selected column range for the given [row], or null if this row is not selected.
     * [lineLength] is the length of the text on this row.
     */
    fun getSelectedColRange(row: Int, lineLength: Int): IntRange? {
        if (row < fromRow || row > toRow) return null
        return when {
            fromRow == toRow -> {
                if (lineLength <= 0) return null
                val start = fromCol.coerceIn(0, lineLength - 1)
                val end = toCol.coerceIn(0, lineLength - 1)
                if (start <= end) start..end else null
            }
            row == fromRow -> {
                val start = fromCol.coerceIn(0, lineLength)
                start..lineLength
            }
            row == toRow -> {
                if (lineLength <= 0) 0..0
                else 0..toCol.coerceIn(0, lineLength - 1)
            }
            else -> {
                0..lineLength
            }
        }
    }

    /**
     * Extracts the selected text from [lines].
     * Trailing whitespace on non-final lines is trimmed to produce clean copied output.
     */
    fun extractText(lines: List<AnnotatedString>): String {
        if (lines.isEmpty()) return ""
        val fRow = fromRow.coerceIn(0, lines.lastIndex)
        val tRow = toRow.coerceIn(0, lines.lastIndex)
        val sb = StringBuilder()

        for (r in fRow..tRow) {
            val text = lines[r].text
            val colStart = if (r == fRow) fromCol.coerceIn(0, text.length) else 0
            val colEnd = if (r == tRow) (toCol + 1).coerceIn(0, text.length) else text.length

            if (colStart < colEnd && colStart < text.length) {
                val segment = text.substring(colStart, colEnd.coerceAtMost(text.length))
                if (r < tRow) {
                    sb.append(segment.trimEnd(' '))
                } else {
                    sb.append(segment)
                }
            }
            if (r < tRow) {
                sb.append('\n')
            }
        }
        return sb.toString()
    }

    fun characterCount(lines: List<AnnotatedString>): Int {
        return extractText(lines).length
    }
}

/**
 * Finds the start and end column bounds of the word at [col] in [text].
 * Recognizes alphanumeric characters and common terminal tokens (paths, IPs, URLs, flags).
 */
fun findWordBoundary(text: String, col: Int): Pair<Int, Int> {
    if (text.isEmpty()) return 0 to 0
    val safeCol = col.coerceIn(0, text.lastIndex)
    val ch = text[safeCol]

    // If tapped on whitespace, select whitespace sequence
    if (ch.isWhitespace()) {
        var start = safeCol
        while (start > 0 && text[start - 1].isWhitespace()) start--
        var end = safeCol
        while (end < text.lastIndex && text[end + 1].isWhitespace()) end++
        return start to end
    }

    // Terminal word delimiters: spaces, quotes, brackets, parens, braces
    fun isWordChar(c: Char): Boolean {
        return !c.isWhitespace() &&
            c != '"' && c != '\'' && c != '`' &&
            c != '(' && c != ')' &&
            c != '[' && c != ']' &&
            c != '{' && c != '}' &&
            c != '<' && c != '>'
    }

    var start = safeCol
    while (start > 0 && isWordChar(text[start - 1])) start--
    var end = safeCol
    while (end < text.lastIndex && isWordChar(text[end + 1])) end++
    return start to end
}

/**
 * Computes an updated [TerminalSelection] when dragging from an initial word selection
 * anchored at [anchorRow] with column bounds [anchorWordStart]..[anchorWordEnd] to ([dragRow], [dragCol]).
 * Preserves the full initial word whether the user drags forward, backward, or within the word.
 */
fun extendWordSelection(
    anchorRow: Int,
    anchorWordStart: Int,
    anchorWordEnd: Int,
    dragRow: Int,
    dragCol: Int
): TerminalSelection {
    return if (dragRow > anchorRow || (dragRow == anchorRow && dragCol >= anchorWordStart)) {
        val effectiveEndCol = if (dragRow == anchorRow) maxOf(dragCol, anchorWordEnd) else dragCol
        TerminalSelection(
            startRow = anchorRow,
            startCol = anchorWordStart,
            endRow = dragRow,
            endCol = effectiveEndCol
        )
    } else {
        TerminalSelection(
            startRow = anchorRow,
            startCol = anchorWordEnd,
            endRow = dragRow,
            endCol = dragCol
        )
    }
}

