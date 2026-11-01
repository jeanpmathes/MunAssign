package org.munika.assignment.domain.model

/**
 * A two-person exchange that gives [sourceDelegate] a ranked position.
 *
 * Both occupants currently have eligible pending assignments outside their preferences.
 * The target delegate may remain outside their preferences after the exchange. This is a
 * snapshot for review; persistence must revalidate it against current assignments.
 */
class PendingAssignmentSwap internal constructor(
    val sourceSlot: Slot,
    val targetSlot: Slot,
    val sourceDelegate: Delegate,
    val targetDelegate: Delegate,
    val preferenceRank: Int,
)

/**
 * Finds direct exchanges without searching assignment chains.
 *
 * Eligible occupants are indexed once by position. Each source's ranked positions are then
 * inspected, so discovery scales with preferences and actual opportunities rather than all
 * possible pairs of conference delegates.
 */
fun Conference.pendingAssignmentSwaps(): Map<DelegateId, List<PendingAssignmentSwap>> {
    val occupants = slots.mapNotNull { slot ->
        val delegate = slot.delegate ?: return@mapNotNull null
        if (slot.isPending && delegate.isAssignmentEligible && !slot.matchesDelegatePreference) {
            slot to delegate
        } else {
            null
        }
    }
    val occupantsByPosition = occupants.groupBy { (slot, _) -> slot.position.id }
    return occupants.associate { (sourceSlot, sourceDelegate) ->
        val opportunities = sourceDelegate.preferences.flatMap { preference ->
            occupantsByPosition[preference.position.id].orEmpty().map { (targetSlot, targetDelegate) ->
                PendingAssignmentSwap(sourceSlot, targetSlot, sourceDelegate, targetDelegate, preference.rank)
            }
        }.sortedWith(
            compareBy<PendingAssignmentSwap> { it.preferenceRank }
                .thenBy { it.targetDelegate.displayName.lowercase() }
                .thenBy { it.targetDelegate.id.value }
                .thenBy { it.targetSlot.id.value },
        )
        sourceDelegate.id to opportunities
    }
}
