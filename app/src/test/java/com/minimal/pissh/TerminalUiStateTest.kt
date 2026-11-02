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

    @Test
    fun `reordering profiles updates the list order in state`() {
        val p1 = HostProfile(id = 1, label = "P1", host = "1.1.1.1")
        val p2 = HostProfile(id = 2, label = "P2", host = "2.2.2.2")
        val p3 = HostProfile(id = 3, label = "P3", host = "3.3.3.3")
        val state = TerminalUiState(profiles = listOf(p1, p2, p3))

        val reordered = state.profiles.toMutableList().apply {
            val item = removeAt(0)
            add(1, item)
        }
        val updatedState = state.copy(profiles = reordered)
        assertEquals(listOf(2, 1, 3), updatedState.profiles.map { it.id })
    }

    @Test
    fun `deleting a profile removes it from state profiles list`() {
        val p1 = HostProfile(id = 1, label = "P1", host = "1.1.1.1")
        val p2 = HostProfile(id = 2, label = "P2", host = "2.2.2.2")
        val state = TerminalUiState(profiles = listOf(p1, p2), selectedProfileIndex = 1)

        val updatedProfiles = state.profiles.filter { it.id != 1 }
        val updatedState = state.copy(
            profiles = updatedProfiles,
            selectedProfileIndex = 0
        )
        assertEquals(1, updatedState.profiles.size)
        assertEquals(2, updatedState.profiles.first().id)
    }
}

