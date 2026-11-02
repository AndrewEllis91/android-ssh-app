package com.minimal.pissh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `isEditorOpen is false by default so list view is default state`() {
        val state = TerminalUiState()
        assertFalse(state.isEditorOpen)
        assertFalse(state.isCreatingNewProfile)
        assertNull(state.editingProfile)
        assertNull(state.connectingProfileId)
    }

    @Test
    fun `activeProfile returns editingProfile when editing`() {
        val draft = HostProfile(id = 99, label = "Draft Server", host = "10.0.0.99")
        val saved = HostProfile(id = 1, label = "Saved Server", host = "10.0.0.1")
        val state = TerminalUiState(
            profiles = listOf(saved),
            selectedProfileIndex = 0,
            isEditorOpen = true,
            editingProfile = draft
        )
        assertEquals("Draft Server", state.activeProfile.label)
        assertEquals("10.0.0.99", state.activeProfile.host)
    }

    @Test
    fun `activeProfile returns selected profile from list when not editing`() {
        val profileA = HostProfile(id = 1, label = "Server A", host = "10.0.0.1")
        val profileB = HostProfile(id = 2, label = "Server B", host = "10.0.0.2")
        val state = TerminalUiState(
            profiles = listOf(profileA, profileB),
            selectedProfileIndex = 1,
            isEditorOpen = false,
            editingProfile = null
        )
        assertEquals("Server B", state.activeProfile.label)
        assertEquals("10.0.0.2", state.activeProfile.host)
    }
}

