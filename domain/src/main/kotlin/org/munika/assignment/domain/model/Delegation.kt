package org.munika.assignment.domain.model

/**
 * Delegation context imported from MyMUN.
 *
 * Delegations help organizers interpret delegate groups but do not own assignment state.
 */
class Delegation(
    override val id: DelegationId,
    val conference: Conference,
    val name: String,
    val country: String?,
    val expectedDelegateCount: Int?,
    val currentDelegateCountFromMymun: Int?,
) : DomainObject<DelegationId> {
    var headDelegate: Delegate? = null
        private set

    val delegates: MutableList<Delegate> = mutableListOf()

    init {
        require(name.isNotBlank()) { "Delegation name must not be blank." }
    }

    val delegateCount: Int
        get() = delegates.size

    val displayLabel: String
        get() = listOfNotNull(name, country).joinToString(" - ")

    fun addDelegate(delegate: Delegate) {
        require(delegate.conference === conference) { "Delegate must belong to the delegation conference." }
        delegate.delegation?.takeIf { it !== this }?.delegates?.remove(delegate)
        if (delegate !in delegates) {
            delegates += delegate
        }
        delegate.delegation = this
    }

    fun setHeadDelegate(delegate: Delegate?) {
        if (delegate != null) {
            require(delegate in delegates) { "Head delegate must belong to the delegation." }
        }
        headDelegate = delegate
    }
}
