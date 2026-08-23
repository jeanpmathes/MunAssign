package org.munika.assignment.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.munika.assignment.ui.viewmodel.ConferenceListAvailability
import org.munika.assignment.ui.viewmodel.ConferenceListItem
import org.munika.assignment.ui.viewmodel.ConferenceListStorageKind

class ConferenceManagementUiModelTest {
    @Test
    fun `only available synchronized conferences show an inline reload action`() {
        assertEquals(emptyList(), conference(storageKind = ConferenceListStorageKind.Local).inlineManagementActions())
        assertEquals(
            listOf(ConferenceManagementAction.Reload),
            conference(storageKind = ConferenceListStorageKind.Synchronized).inlineManagementActions(),
        )
        assertEquals(
            emptyList(),
            conference(
                storageKind = ConferenceListStorageKind.Synchronized,
                availability = ConferenceListAvailability.Missing,
            ).inlineManagementActions(),
        )
    }

    @Test
    fun `row overflow owns all non-reload conference actions`() {
        val localActions = conference(storageKind = ConferenceListStorageKind.Local).overflowManagementActions()
        val synchronizedActions = conference(
            storageKind = ConferenceListStorageKind.Synchronized,
        ).overflowManagementActions()

        assertFalse(localActions.any { it.action == ConferenceManagementAction.Reload })
        assertEquals(
            listOf(
                ConferenceManagementAction.Export,
                ConferenceManagementAction.Replace,
                ConferenceManagementAction.TrustedFilenames,
                ConferenceManagementAction.ResetPreferenceCorrections,
                ConferenceManagementAction.Rename,
                ConferenceManagementAction.MoveToSynchronized,
                ConferenceManagementAction.Remove,
            ),
            localActions.map { it.action },
        )
        assertEquals(
            listOf(
                ConferenceManagementAction.Export,
                ConferenceManagementAction.Replace,
                ConferenceManagementAction.TrustedFilenames,
                ConferenceManagementAction.ResetPreferenceCorrections,
                ConferenceManagementAction.Rename,
                ConferenceManagementAction.Locate,
                ConferenceManagementAction.Remove,
            ),
            synchronizedActions.map { it.action },
        )
        assertTrue(synchronizedActions.all { it.enabled })
    }

    @Test
    fun `unavailable synchronized menu retains recovery actions only`() {
        val actions = conference(
            storageKind = ConferenceListStorageKind.Synchronized,
            availability = ConferenceListAvailability.Invalid,
        ).overflowManagementActions().associate { it.action to it.enabled }

        assertFalse(actions.getValue(ConferenceManagementAction.Export))
        assertFalse(actions.getValue(ConferenceManagementAction.Replace))
        assertFalse(actions.getValue(ConferenceManagementAction.TrustedFilenames))
        assertFalse(actions.getValue(ConferenceManagementAction.ResetPreferenceCorrections))
        assertFalse(actions.getValue(ConferenceManagementAction.Rename))
        assertTrue(actions.getValue(ConferenceManagementAction.Locate))
        assertTrue(actions.getValue(ConferenceManagementAction.Remove))
    }

    @Test
    fun `add conference popup has the four requested tabs`() {
        assertEquals(
            listOf(
                ConferenceAddTab.CreateLocal,
                ConferenceAddTab.ImportLocal,
                ConferenceAddTab.CreateSynchronized,
                ConferenceAddTab.ConnectSynchronized,
            ),
            ConferenceAddTab.entries,
        )
    }

    private fun conference(
        storageKind: ConferenceListStorageKind,
        availability: ConferenceListAvailability = ConferenceListAvailability.Available,
    ): ConferenceListItem = ConferenceListItem(
        id = "conference-id",
        name = "KAMUN",
        displayOrder = 0,
        storageKind = storageKind,
        availability = availability,
    )
}
