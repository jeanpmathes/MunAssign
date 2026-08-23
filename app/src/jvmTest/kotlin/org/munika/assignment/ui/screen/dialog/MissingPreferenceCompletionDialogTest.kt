package org.munika.assignment.ui.screen.dialog

import kotlin.test.Test
import kotlin.test.assertEquals
import org.munika.assignment.domain.repository.PreferenceValueOrigin

class MissingPreferenceCompletionDialogTest {
    @Test
    fun `moving a preference card preserves its value and empty state`() {
        val cards = listOf(
            PreferenceCorrectionCardUiState("france", "GA - France", PreferenceValueOrigin.Mymun, false),
            PreferenceCorrectionCardUiState(null, null, null, false),
            PreferenceCorrectionCardUiState("germany", "GA - Germany", PreferenceValueOrigin.Mymun, false),
        )

        val reordered = movePreferenceCorrectionCard(cards, fromIndex = 2, toIndex = 1)

        assertEquals(listOf("france", "germany", null), reordered.map { it.positionId })
        assertEquals(false, reordered.last().emptyAcknowledged)
        assertEquals(PreferenceValueOrigin.Mymun, reordered[1].origin)
    }
}
