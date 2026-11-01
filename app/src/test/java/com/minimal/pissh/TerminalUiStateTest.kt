package com.minimal.pissh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalUiStateTest {

    @Test
    fun `saveSuccessMessage is null by default`() {
        val state = TerminalUiState()
        assertNull(state.saveSuccessMessage)
    }

    @Test
    fun `saveSuccessMessage stores success confirmation text`() {
        val state = TerminalUiState(saveSuccessMessage = "Saved profile \"Home Pi\"")
        assertEquals("Saved profile \"Home Pi\"", state.saveSuccessMessage)
    }
}
