package com.minimal.pissh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        val atusSpan = lines[0].spanStyles.find { it.start == "pi@host$ git ".length }
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
}
