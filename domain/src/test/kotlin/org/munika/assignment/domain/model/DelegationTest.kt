package org.munika.assignment.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DelegationTest {
    @Test
    fun `adding a delegate connects both sides of the relationship`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val delegation = Delegation(DelegationId("delegation"), conference, "Team Ada", "UK", 2, 1)
        val delegate = delegate(conference, "delegate")

        delegation.addDelegate(delegate)

        assertSame(delegation, delegate.delegation)
        assertTrue(delegate in delegation.delegates)
    }

    @Test
    fun `head delegate must already belong to the delegation`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val delegation = Delegation(DelegationId("delegation"), conference, "Team Ada", "UK", 2, 1)

        assertFailsWith<IllegalArgumentException> {
            delegation.setHeadDelegate(delegate(conference, "delegate"))
        }
    }

    private fun delegate(conference: Conference, id: String): Delegate =
        Delegate(
            id = DelegateId(id),
            conference = conference,
            name = PersonName("Ada", "Lovelace"),
            email = "ada@example.test",
            profile = DelegateProfile(null, null, null, null, null, null, null, null, null, null),
            paymentStatus = PaymentStatus.Paid,
            isHeadDelegate = false,
        )
}
