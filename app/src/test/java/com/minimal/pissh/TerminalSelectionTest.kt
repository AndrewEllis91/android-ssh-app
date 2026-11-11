package com.minimal.pissh

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSelectionTest {

    @Test
    fun `single line selection extracts exact substring`() {
        val lines = listOf(
            AnnotatedString("pi@raspberrypi:~$ uptime"),
            AnnotatedString(" 22:45:01 up 14 days,  3:12")
        )
        val selection = TerminalSelection(startRow = 0, startCol = 18, endRow = 0, endCol = 23)
        assertEquals("uptime", selection.extractText(lines))
        assertEquals(6, selection.characterCount(lines))
    }

    @Test
    fun `reversed single line selection normalizes bounds`() {
        val lines = listOf(AnnotatedString("hello world"))
        val selection = TerminalSelection(startRow = 0, startCol = 10, endRow = 0, endCol = 6)
        assertEquals("world", selection.extractText(lines))
    }

    @Test
    fun `multi line selection extracts and trims trailing whitespace on non-final lines`() {
        val lines = listOf(
            AnnotatedString("line 1 text   "),
            AnnotatedString("line 2 text   "),
            AnnotatedString("line 3 end")
        )
        val selection = TerminalSelection(startRow = 0, startCol = 7, endRow = 2, endCol = 5)
        val extracted = selection.extractText(lines)
        assertEquals("text\nline 2 text\nline 3", extracted)
    }

    @Test
    fun `reversed multi line selection normalizes row bounds`() {
        val lines = listOf(
            AnnotatedString("first row"),
            AnnotatedString("second row")
        )
        val selection = TerminalSelection(startRow = 1, startCol = 9, endRow = 0, endCol = 0)
        val extracted = selection.extractText(lines)
        assertEquals("first row\nsecond row", extracted)
    }

    @Test
    fun `empty lines and out of bounds coordinates are safely clamped`() {
        val lines = emptyList<AnnotatedString>()
        val selection = TerminalSelection(0, 0, 5, 10)
        assertEquals("", selection.extractText(lines))
        assertEquals(0, selection.characterCount(lines))
    }

    @Test
    fun `getSelectedColRange returns correct column range per row`() {
        val selection = TerminalSelection(startRow = 1, startCol = 5, endRow = 3, endCol = 10)

        // Row 0 is before selection
        assertNull(selection.getSelectedColRange(0, 20))

        // Row 1 starts at col 5 and extends to lineLength (20) to include newline cell
        assertEquals(5..20, selection.getSelectedColRange(1, 20))

        // Row 2 is completely selected including newline cell
        assertEquals(0..20, selection.getSelectedColRange(2, 20))

        // Row 3 ends at col 10
        assertEquals(0..10, selection.getSelectedColRange(3, 20))

        // Row 4 is after selection
        assertNull(selection.getSelectedColRange(4, 20))

        // Single-line selection clamps to valid character indices (0..lineLength - 1)
        val singleLine = TerminalSelection(startRow = 0, startCol = 0, endRow = 0, endCol = 25)
        assertEquals(0..4, singleLine.getSelectedColRange(0, 5))
        assertNull(singleLine.getSelectedColRange(0, 0))
    }

    @Test
    fun `extendWordSelection preserves initial word when dragging forward backward or inside word`() {
        // Initial word on row 2 spans columns 10..15
        val insideWord = extendWordSelection(2, 10, 15, dragRow = 2, dragCol = 12)
        assertEquals(2, insideWord.fromRow)
        assertEquals(10, insideWord.fromCol)
        assertEquals(2, insideWord.toRow)
        assertEquals(15, insideWord.toCol)

        // Dragging right on same row extends endCol while anchoring startCol at 10
        val dragRight = extendWordSelection(2, 10, 15, dragRow = 2, dragCol = 22)
        assertEquals(10, dragRight.fromCol)
        assertEquals(22, dragRight.toCol)

        // Dragging left on same row anchors end at 15 and extends start left to 4
        val dragLeft = extendWordSelection(2, 10, 15, dragRow = 2, dragCol = 4)
        assertEquals(4, dragLeft.fromCol)
        assertEquals(15, dragLeft.toCol)

        // Dragging down to row 4 anchors start at (2, 10)
        val dragDown = extendWordSelection(2, 10, 15, dragRow = 4, dragCol = 3)
        assertEquals(2, dragDown.fromRow)
        assertEquals(10, dragDown.fromCol)
        assertEquals(4, dragDown.toRow)
        assertEquals(3, dragDown.toCol)

        // Dragging up to row 0 anchors end at (2, 15)
        val dragUp = extendWordSelection(2, 10, 15, dragRow = 0, dragCol = 3)
        assertEquals(0, dragUp.fromRow)
        assertEquals(3, dragUp.fromCol)
        assertEquals(2, dragUp.toRow)
        assertEquals(15, dragUp.toCol)
    }

    @Test
    fun `contains correctly checks if row and col fall inside selection`() {
        val selection = TerminalSelection(startRow = 1, startCol = 5, endRow = 2, endCol = 10)
        assertFalse(selection.contains(0, 5))
        assertFalse(selection.contains(1, 4))
        assertTrue(selection.contains(1, 5))
        assertTrue(selection.contains(1, 20))
        assertTrue(selection.contains(2, 0))
        assertTrue(selection.contains(2, 10))
        assertFalse(selection.contains(2, 11))
        assertFalse(selection.contains(3, 0))
    }

    @Test
    fun `findWordBoundary selects full Linux paths and URLs`() {
        val text = "pi@raspberrypi:~$ cat /etc/systemd/system/pissh.service"
        // Tap on "systemd" (col 27)
        val (start, end) = findWordBoundary(text, 27)
        assertEquals("/etc/systemd/system/pissh.service", text.substring(start, end + 1))
    }

    @Test
    fun `findWordBoundary selects IP addresses with ports`() {
        val text = "Connecting to 192.168.1.150:22 via SSH"
        val (start, end) = findWordBoundary(text, 20)
        assertEquals("192.168.1.150:22", text.substring(start, end + 1))
    }

    @Test
    fun `findWordBoundary stops at quotes and brackets`() {
        val text = "echo \"hello world\" [SUCCESS]"
        // Tap on "hello"
        val (start, end) = findWordBoundary(text, 7)
        assertEquals("hello", text.substring(start, end + 1))

        // Tap on "SUCCESS"
        val (succStart, succEnd) = findWordBoundary(text, 22)
        assertEquals("SUCCESS", text.substring(succStart, succEnd + 1))
    }

    @Test
    fun `findWordBoundary selects whitespace runs when tapped on space`() {
        val text = "item1   item2"
        val (start, end) = findWordBoundary(text, 6)
        assertEquals("   ", text.substring(start, end + 1))
    }
}
