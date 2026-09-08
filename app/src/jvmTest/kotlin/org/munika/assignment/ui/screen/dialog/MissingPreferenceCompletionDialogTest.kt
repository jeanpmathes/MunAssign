package org.munika.assignment.ui.screen.dialog

import kotlin.test.Test
import kotlin.test.assertEquals
import org.munika.assignment.domain.repository.PreferenceValueOrigin
import org.munika.assignment.ui.screen.component.PreferencePositionOptionUiState

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

    @Test
    fun `selecting a used position swaps the complete cards`() {
        val cards = listOf(
            PreferenceCorrectionCardUiState("solomon", "GA - Solomon Islands", PreferenceValueOrigin.Mymun, false),
            PreferenceCorrectionCardUiState(null, null, null, false),
        )
        val ranks = listOf(1, 2)

        val result = selectPreferencePosition(
            cards = cards,
            preferenceRanks = ranks,
            targetIndex = 1,
            option = PreferencePositionOptionUiState(
                id = "solomon",
                label = "GA - Solomon Islands",
                currentRank = 1,
            ),
        )

        assertEquals(listOf(null, "solomon"), result.cards.map { it.positionId })
        assertEquals(false, result.cards.first().emptyAcknowledged)
        assertEquals(PreferenceValueOrigin.Mymun, result.cards.last().origin)
        assertEquals(1, result.swappedFromRank)
    }

    @Test
    fun `selecting a used position swaps two filled cards`() {
        val cards = listOf(
            PreferenceCorrectionCardUiState("france", "GA - France", PreferenceValueOrigin.Organizer, false),
            PreferenceCorrectionCardUiState("solomon", "GA - Solomon Islands", PreferenceValueOrigin.Mymun, false),
        )

        val result = selectPreferencePosition(
            cards = cards,
            preferenceRanks = listOf(1, 2),
            targetIndex = 0,
            option = PreferencePositionOptionUiState(
                id = "solomon",
                label = "GA - Solomon Islands",
                currentRank = 2,
            ),
        )

        assertEquals(listOf("solomon", "france"), result.cards.map { it.positionId })
        assertEquals(PreferenceValueOrigin.Mymun, result.cards.first().origin)
        assertEquals(PreferenceValueOrigin.Organizer, result.cards.last().origin)
        assertEquals(2, result.swappedFromRank)
    }

    @Test
    fun `selecting the current position is a no-op`() {
        val cards = listOf(
            PreferenceCorrectionCardUiState("solomon", "GA - Solomon Islands", PreferenceValueOrigin.Organizer, false),
        )

        val result = selectPreferencePosition(
            cards = cards,
            preferenceRanks = listOf(1),
            targetIndex = 0,
            option = PreferencePositionOptionUiState(
                id = "solomon",
                label = "GA - Solomon Islands",
                currentRank = 1,
            ),
        )

        assertEquals(cards, result.cards)
        assertEquals(null, result.swappedFromRank)
    }

    @Test
    fun `options identify the current rank after cards move`() {
        val options = listOf(
            PreferencePositionOptionUiState("france", "GA - France"),
            PreferencePositionOptionUiState("solomon", "GA - Solomon Islands"),
        )
        val cards = listOf(
            PreferenceCorrectionCardUiState("solomon", "GA - Solomon Islands", PreferenceValueOrigin.Mymun, false),
            PreferenceCorrectionCardUiState(null, null, null, false),
            PreferenceCorrectionCardUiState("france", "GA - France", PreferenceValueOrigin.Organizer, false),
        )

        val rankedOptions = preferencePositionOptionsWithCurrentRanks(
            options = options,
            cards = cards,
            preferenceRanks = listOf(1, 2, 3),
        )

        assertEquals(listOf(3, 1), rankedOptions.map { it.currentRank })
    }

    @Test
    fun `later swap replaces notices on the ranks it edits`() {
        val first = updatePreferenceSwapNotices(
            current = emptyMap(),
            editedRank = 3,
            swappedFromRank = 1,
        )
        val second = updatePreferenceSwapNotices(
            current = first,
            editedRank = 1,
            swappedFromRank = 2,
        )
        val cleared = updatePreferenceSwapNotices(
            current = second,
            editedRank = 2,
            swappedFromRank = null,
        )

        assertEquals(mapOf(2 to 1), second)
        assertEquals(emptyMap(), cleared)
    }

    @Test
    fun `moving cards clears notices for every rank whose content moves`() {
        val result = clearPreferenceSwapNoticesForMove(
            current = mapOf(1 to 3, 2 to 1, 4 to 2),
            preferenceRanks = listOf(1, 2, 3, 4),
            fromIndex = 0,
            toIndex = 2,
        )

        assertEquals(mapOf(4 to 2), result)
    }
}
