package org.munika.assignment.ui.screen.component

import kotlin.test.Test
import kotlin.test.assertEquals

class PreferencePositionSelectorTest {
    @Test
    fun `search matches committee or position and retains positions already used`() {
        val options = listOf(
            PreferencePositionOptionUiState("france", "GA - France", currentRank = 1),
            PreferencePositionOptionUiState("germany", "GA - Germany"),
            PreferencePositionOptionUiState("brazil", "WHO - Brazil"),
        )

        assertEquals(
            listOf("germany"),
            filterPreferencePositionOptions(options, "germ").map { it.id },
        )
        assertEquals(
            listOf("brazil"),
            filterPreferencePositionOptions(options, "who").map { it.id },
        )
        assertEquals(
            listOf("france"),
            filterPreferencePositionOptions(options, "france").map { it.id },
        )
    }

    @Test
    fun `used option explains the swap unless it belongs to the edited rank`() {
        val option = PreferencePositionOptionUiState("solomon", "GA - Solomon Islands", currentRank = 2)

        assertEquals(
            "Currently preference 2 - selecting swaps",
            preferencePositionUsageNotice(option, editingRank = 3),
        )
        assertEquals(null, preferencePositionUsageNotice(option, editingRank = 2))
    }
}
