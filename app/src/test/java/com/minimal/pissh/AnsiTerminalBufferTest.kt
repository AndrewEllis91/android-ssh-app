package com.minimal.pissh

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsiTerminalBufferTest {

    @Test
    fun `strips Raspberry Pi bash OSC window title and preserves text`() {
        val buffer = AnsiTerminalBuffer()
        val rawPrompt = "\u001B]0;pi@raspberrypi: ~\u0007\u001B[01;32mpi@raspberrypi\u001B[00m:\u001B[01;34m~\u001B[00m$ ls -la\r\ntotal 32"
        val lines = buffer.appendBytes(rawPrompt.toByteArray(Charsets.UTF_8))

        assertEquals(listOf("pi@raspberrypi:~$ ls -la", "total 32"), lines.map { it.text })
    }

    @Test
    fun `handles carriage return progress overwrites and backspace erase`() {
        val buffer = AnsiTerminalBuffer()
        buffer.appendBytes("Downloading 10%\rDownloading 100%\r\n".toByteArray(Charsets.UTF_8))
        val lines = buffer.appendBytes("docker pss\b\u001B[K".toByteArray(Charsets.UTF_8))

        assertEquals(listOf("Downloading 100%", "docker ps"), lines.map { it.text })
    }

    @Test
    fun `handles CSI 2J and bash Ctrl+L clear screen sequences`() {
        val buffer = AnsiTerminalBuffer()
        buffer.appendBytes("line 1\r\nline 2\r\n".toByteArray(Charsets.UTF_8))
        val linesAfter2J = buffer.appendBytes("\u001B[2J\u001B[Htop - 22:00:00".toByteArray(Charsets.UTF_8))
        assertEquals(listOf("top - 22:00:00"), linesAfter2J.map { it.text })
    }

    @Test
    fun `does not wipe previous command output when zsh sends CSI J on new prompt`() {
        val buffer = AnsiTerminalBuffer()
        buffer.appendBytes("command output line 1\r\ncommand output line 2\r\n".toByteArray(Charsets.UTF_8))
        val lines = buffer.appendBytes("pi@host$ \u001B[J".toByteArray(Charsets.UTF_8))
        assertEquals(listOf("command output line 1", "command output line 2", "pi@host$ "), lines.map { it.text })
    }

    @Test
    fun `parses zsh autosuggestion grey color and standard prompt colors`() {
        val buffer = AnsiTerminalBuffer()
        // Prompt in green + blue, and autosuggestion in color 8 (faint grey)
        val promptAndSuggestion = "\u001B[32mpi\u001B[m@\u001B[34mhost\u001B[m$ git st\u001B[38;5;8matus\u001B[m"
        val lines = buffer.appendBytes(promptAndSuggestion.toByteArray(Charsets.UTF_8))
        assertEquals("pi@host$ git status", lines[0].text)
        val atusSpan = lines[0].spanStyles.find { it.start == "pi@host$ git st".length }
        assertNotNull(atusSpan)
        assertEquals(FaintTerminalFgColor, atusSpan?.item?.color)
    }

    @Test
    fun `decodes multi-byte UTF-8 characters split across TCP packet boundaries`() {
        val buffer = AnsiTerminalBuffer()
        // '─' is U+2500 -> UTF-8 bytes: 0xE2, 0x94, 0x80
        val packet1 = byteArrayOf('A'.code.toByte(), 0xE2.toByte(), 0x94.toByte())
        val packet2 = byteArrayOf(0x80.toByte(), 'B'.code.toByte())

        buffer.appendBytes(packet1)
        val lines = buffer.appendBytes(packet2)
        assertEquals(listOf("A─B"), lines.map { it.text })
    }

    @Test
    fun `computes ASCII control codes from latched CTRL character`() {
        assertEquals(0x03.toByte(), AnsiTerminalBuffer.computeCtrlByte('c'))
        assertEquals(0x03.toByte(), AnsiTerminalBuffer.computeCtrlByte('C'))
        assertEquals(0x04.toByte(), AnsiTerminalBuffer.computeCtrlByte('d'))
        assertEquals(0x1A.toByte(), AnsiTerminalBuffer.computeCtrlByte('z'))
    }

    @Test
    fun `tracks cursor row and column as characters and newlines are processed`() {
        val buffer = AnsiTerminalBuffer()
        assertEquals(0, buffer.currentCursorRow)
        assertEquals(0, buffer.currentCursorCol)
        assertTrue(buffer.isCursorVisible)

        buffer.appendBytes("pi@raspberrypi:~$ ".toByteArray(Charsets.UTF_8))
        assertEquals(0, buffer.currentCursorRow)
        assertEquals(18, buffer.currentCursorCol)

        buffer.appendBytes("ls\r\n".toByteArray(Charsets.UTF_8))
        assertEquals(1, buffer.currentCursorRow)
        assertEquals(0, buffer.currentCursorCol)
    }

    @Test
    fun `handles cursor back and forward movement within a line`() {
        val buffer = AnsiTerminalBuffer()
        buffer.appendBytes("echo hello".toByteArray(Charsets.UTF_8))
        assertEquals(10, buffer.currentCursorCol)

        // Move cursor back 5 characters (CSI 5 D)
        buffer.appendBytes("\u001B[5D".toByteArray(Charsets.UTF_8))
        assertEquals(5, buffer.currentCursorCol)

        // Move cursor forward 2 characters (CSI 2 C)
        buffer.appendBytes("\u001B[2C".toByteArray(Charsets.UTF_8))
        assertEquals(7, buffer.currentCursorCol)

        // Move cursor to column 3 (CSI 3 G -> 0-based col 2)
        buffer.appendBytes("\u001B[3G".toByteArray(Charsets.UTF_8))
        assertEquals(2, buffer.currentCursorCol)
    }

    @Test
    fun `handles cursor up and down for multi-line block of text navigation and editing`() {
        val buffer = AnsiTerminalBuffer()
        buffer.appendBytes("first line\r\nsecond line\r\nthird line".toByteArray(Charsets.UTF_8))
        assertEquals(2, buffer.currentCursorRow)
        assertEquals(10, buffer.currentCursorCol)

        // Move cursor up 2 lines (CSI 2 A)
        buffer.appendBytes("\u001B[2A".toByteArray(Charsets.UTF_8))
        assertEquals(0, buffer.currentCursorRow)

        // Move cursor to beginning of line (col 0) and edit
        buffer.appendBytes("\rEDITED ".toByteArray(Charsets.UTF_8))
        val lines = buffer.snapshot()
        assertEquals("EDITED ine", lines[0].text)

        // Move cursor down 1 line (CSI 1 B)
        buffer.appendBytes("\u001B[1B".toByteArray(Charsets.UTF_8))
        assertEquals(1, buffer.currentCursorRow)
    }

    @Test
    fun `handles DEC cursor visibility mode 25h and 25l`() {
        val buffer = AnsiTerminalBuffer()
        assertTrue(buffer.isCursorVisible)

        // Hide cursor (CSI ? 25 l)
        buffer.appendBytes("\u001B[?25l".toByteArray(Charsets.UTF_8))
        assertFalse(buffer.isCursorVisible)

        // Show cursor (CSI ? 25 h)
        buffer.appendBytes("\u001B[?25h".toByteArray(Charsets.UTF_8))
        assertTrue(buffer.isCursorVisible)
    }

    @Test
    fun `handles cursor save and restore with ESC 7 and 8`() {
        val buffer = AnsiTerminalBuffer()
        buffer.appendBytes("hello\r\nworld".toByteArray(Charsets.UTF_8))
        assertEquals(1, buffer.currentCursorRow)
        assertEquals(5, buffer.currentCursorCol)

        // Save cursor position (ESC 7)
        buffer.appendBytes("\u001B7".toByteArray(Charsets.UTF_8))

        // Move to line 0, col 0
        buffer.appendBytes("\u001B[1A\r".toByteArray(Charsets.UTF_8))
        assertEquals(0, buffer.currentCursorRow)
        assertEquals(0, buffer.currentCursorCol)

        // Restore cursor position (ESC 8)
        buffer.appendBytes("\u001B8".toByteArray(Charsets.UTF_8))
        assertEquals(1, buffer.currentCursorRow)
        assertEquals(5, buffer.currentCursorCol)
    }

    @Test
    fun `buildLineWithCursor renders block cursor at end of line and inverts existing character`() {
        val cursorColor = Color(0xFF58A6FF)
        val cursorTextColor = Color(0xFF0D1117)

        // 1. Cursor at end of line
        val lineAtEnd = AnnotatedString("pi@raspberrypi:~$ ")
        val renderedAtEnd = buildLineWithCursor(lineAtEnd, cursorCol = 18, cursorColor = cursorColor, cursorTextColor = cursorTextColor)
        assertEquals("pi@raspberrypi:~$ █", renderedAtEnd.text)
        val endCursorSpan = renderedAtEnd.spanStyles.find { it.start == 18 && it.end == 19 }
        assertNotNull(endCursorSpan)
        assertEquals(cursorColor, endCursorSpan?.item?.color)

        // 2. Cursor in middle of line on non-space character
        val lineMid = AnnotatedString("echo hello")
        val renderedMid = buildLineWithCursor(lineMid, cursorCol = 5, cursorColor = cursorColor, cursorTextColor = cursorTextColor)
        assertEquals("echo hello", renderedMid.text)
        val midCursorSpan = renderedMid.spanStyles.find { it.start == 5 && it.end == 6 }
        assertNotNull(midCursorSpan)
        assertEquals(cursorColor, midCursorSpan?.item?.background)
        assertEquals(cursorTextColor, midCursorSpan?.item?.color)
        assertEquals(FontWeight.Bold, midCursorSpan?.item?.fontWeight)

        // 3. Cursor on a space character
        val renderedSpace = buildLineWithCursor(lineMid, cursorCol = 4, cursorColor = cursorColor, cursorTextColor = cursorTextColor)
        assertEquals("echo█hello", renderedSpace.text)
        val spaceCursorSpan = renderedSpace.spanStyles.find { it.start == 4 && it.end == 5 }
        assertNotNull(spaceCursorSpan)
        assertEquals(cursorColor, spaceCursorSpan?.item?.color)
    }
}
