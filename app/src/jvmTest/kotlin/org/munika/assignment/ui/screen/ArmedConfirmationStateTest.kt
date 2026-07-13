package org.munika.assignment.ui.screen

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmedConfirmationStateTest {
    @Test
    fun `confirmation requires reveal followed by explicit arming`() {
        val initial = ArmedConfirmationState()

        assertFalse(initial.revealed)
        assertFalse(initial.confirmationEnabled)
        assertFalse(initial.setArmed(true).confirmationEnabled)

        val revealed = initial.reveal()

        assertTrue(revealed.revealed)
        assertFalse(revealed.confirmationEnabled)
        assertTrue(revealed.setArmed(true).confirmationEnabled)
    }

    @Test
    fun `revealing the safeguard always resets its armed state`() {
        val armed = ArmedConfirmationState(revealed = true, armed = true)

        val revealedAgain = armed.reveal()

        assertTrue(revealedAgain.revealed)
        assertFalse(revealedAgain.armed)
        assertFalse(revealedAgain.confirmationEnabled)
    }
}
