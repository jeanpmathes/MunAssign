package org.munika.assignment.ui.screen

internal data class ArmedConfirmationState(
    val revealed: Boolean = false,
    val armed: Boolean = false,
) {
    val confirmationEnabled: Boolean
        get() = revealed && armed

    fun reveal(): ArmedConfirmationState = copy(revealed = true, armed = false)

    fun setArmed(armed: Boolean): ArmedConfirmationState = copy(armed = armed)
}
