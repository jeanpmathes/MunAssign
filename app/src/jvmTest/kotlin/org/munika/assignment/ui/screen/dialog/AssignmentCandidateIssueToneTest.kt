package org.munika.assignment.ui.screen.dialog

import kotlin.test.Test
import kotlin.test.assertEquals
import org.munika.assignment.ui.viewmodel.DelegateStatusTone

class AssignmentCandidateIssueToneTest {
    @Test
    fun `assignment candidate warning badges use filled warning tone`() {
        val badge = assignmentCandidateIssueBadge(count = 1, isWarning = true)

        assertEquals("1 W", badge.label)
        assertEquals(DelegateStatusTone.Yellow, badge.tone)
        assertEquals(true, badge.filled)
    }

    @Test
    fun `assignment candidate notice badges use filled neutral tone`() {
        val badge = assignmentCandidateIssueBadge(count = 2, isWarning = false)

        assertEquals("2 N", badge.label)
        assertEquals(DelegateStatusTone.Notice, badge.tone)
        assertEquals(true, badge.filled)
    }
}
