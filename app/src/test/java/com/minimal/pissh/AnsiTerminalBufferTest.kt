package com.minimal.pissh

import org.junit.Assert.assertEquals
import org.junit.Test

class AnsiTerminalBufferTest {

    @Test
    fun `strips Raspberry Pi bash OSC window title and CSI color codes`() {
        val buffer = AnsiTerminalBuffer()
        val rawPrompt = "\u001B]0;pi@raspberrypi: ~\u0007\u001B[01;32mpi@raspberrypi\u001B[00m:\u001B[01;34m~\u001B[00m$ ls -la\r\ntotal 32"
        val lines = buffer.appendBytes(rawPrompt.toByteArray(Charsets.UTF_8))

        assertEquals(listOf("pi@raspberrypi:~$ ls -la", "total 32"), lines)
    }

    @Test
    fun `handles carriage return progress overwrites and backspace erase`() {
        val buffer = AnsiTerminalBuffer()
        buffer.appendBytes("Downloading 10%\rDownloading 100%\r\n".toByteArray(Charsets.UTF_8))
        val lines = buffer.appendBytes("docker pss\b\u001B[K".toByteArray(Charsets.UTF_8))

        assertEquals(listOf("Downloading 100%", "docker ps"), lines)
    }

    @Test
    fun `handles CSI 2J and bash Ctrl+L clear screen sequences`() {
        val buffer = AnsiTerminalBuffer()
        buffer.appendBytes("line 1\r\nline 2\r\n".toByteArray(Charsets.UTF_8))
        val linesAfter2J = buffer.appendBytes("\u001B[2J\u001B[Htop - 22:00:00".toByteArray(Charsets.UTF_8))
        assertEquals(listOf("top - 22:00:00"), linesAfter2J)

        val linesAfterCtrlL = buffer.appendBytes("\r\nmore output\u001B[H\u001B[Jpi@raspberrypi:~$ ".toByteArray(Charsets.UTF_8))
        assertEquals(listOf("pi@raspberrypi:~$ "), linesAfterCtrlL)
    }

    @Test
    fun `decodes multi-byte UTF-8 characters split across TCP packet boundaries`() {
        val buffer = AnsiTerminalBuffer()
        // '─' is U+2500 -> UTF-8 bytes: 0xE2, 0x94, 0x80
        val packet1 = byteArrayOf('A'.code.toByte(), 0xE2.toByte(), 0x94.toByte())
        val packet2 = byteArrayOf(0x80.toByte(), 'B'.code.toByte())

        buffer.appendBytes(packet1)
        val lines = buffer.appendBytes(packet2)
        assertEquals(listOf("A─B"), lines)
    }

    @Test
    fun `computes ASCII control codes from latched CTRL character`() {
        assertEquals(0x03.toByte(), AnsiTerminalBuffer.computeCtrlByte('c'))
        assertEquals(0x03.toByte(), AnsiTerminalBuffer.computeCtrlByte('C'))
        assertEquals(0x04.toByte(), AnsiTerminalBuffer.computeCtrlByte('d'))
        assertEquals(0x1A.toByte(), AnsiTerminalBuffer.computeCtrlByte('z'))
    }
}
