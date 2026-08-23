package org.munika.assignment.ui.screen.component

import kotlin.test.Test
import kotlin.test.assertEquals

class PreferencePositionSelectorTest {
    @Test
    fun `search matches committee or position and excludes positions already used`() {
        val options = listOf(
            PreferencePositionOptionUiState("france", "GA - France"),
            PreferencePositionOptionUiState("germany", "GA - Germany"),
            PreferencePositionOptionUiState("brazil", "WHO - Brazil"),
        )

        assertEquals(
            listOf("germany"),
            filterPreferencePositionOptions(options, setOf("france"), "germ").map { it.id },
        )
        assertEquals(
            listOf("brazil"),
            filterPreferencePositionOptions(options, setOf("france"), "who").map { it.id },
        )
    }
}
