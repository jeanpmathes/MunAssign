# Reservations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add organizer-managed reservations, delegation overview, reservation usage reconciliation, and HS/Uni accept-capacity counters.

**Architecture:** Reservations are conference-owned domain objects with persisted delegate usage links. Manual links are organizer intent; automatic links are stored rows maintained by a deterministic reconciliation service after reservation, delegation, delegate, or MyMUN update changes. UI state is derived from the loaded conference graph and exposed through focused view-model mappers for the board, top bar, delegation overview, reservation manager, and delegate detail.

**Tech Stack:** Kotlin Multiplatform domain/data/app modules, SQLite via Exposed, Compose Multiplatform Desktop, ViewModel + StateFlow, Gradle with Temurin 24.

---

## File Structure

- Create `domain/src/main/kotlin/org/munika/assignment/domain/model/ReservationId.kt`: value object for reservation IDs.
- Create `domain/src/main/kotlin/org/munika/assignment/domain/model/ReservationDelegateLinkId.kt`: value object for reservation usage link IDs.
- Create `domain/src/main/kotlin/org/munika/assignment/domain/model/ReservationLinkSource.kt`: enum for `Manual` and `DelegationAuto`.
- Create `domain/src/main/kotlin/org/munika/assignment/domain/model/ReservationDelegateLink.kt`: domain model for one consuming delegate.
- Create `domain/src/main/kotlin/org/munika/assignment/domain/model/Reservation.kt`: domain model for one organizer-managed reservation.
- Create `domain/src/main/kotlin/org/munika/assignment/domain/model/ReservationReconciler.kt`: pure reconciliation logic for automatic reservation links.
- Modify `domain/src/main/kotlin/org/munika/assignment/domain/model/Conference.kt`: own reservations and expose reservation/capacity helpers.
- Modify `domain/src/main/kotlin/org/munika/assignment/domain/model/Delegate.kt`: expose current reservation link.
- Modify `domain/src/main/kotlin/org/munika/assignment/domain/repository/ConferenceRepository.kt`: add reservation operations.
- Create `domain/src/test/kotlin/org/munika/assignment/domain/model/ReservationReconcilerTest.kt`: pure reconciliation tests.
- Create `data/src/main/kotlin/org/munika/assignment/data/db/tables/ReservationTable.kt`: Exposed reservation table.
- Create `data/src/main/kotlin/org/munika/assignment/data/db/tables/ReservationDelegateLinkTable.kt`: Exposed reservation link table.
- Create `data/src/main/kotlin/org/munika/assignment/data/db/model/ReservationRow.kt`: reservation row DTO.
- Create `data/src/main/kotlin/org/munika/assignment/data/db/model/ReservationDelegateLinkRow.kt`: reservation link row DTO.
- Modify `data/src/main/kotlin/org/munika/assignment/data/db/tables/MunAssignTables.kt`: register reservation tables.
- Modify `data/src/main/kotlin/org/munika/assignment/data/db/DatabaseFactory.kt`: migration version 7 creates reservation tables.
- Modify `data/src/main/kotlin/org/munika/assignment/data/db/model/ConferenceGraphRows.kt`: include reservation rows.
- Modify `data/src/main/kotlin/org/munika/assignment/data/db/mapper/ConferenceGraphMapper.kt`: attach reservations and reservation links.
- Modify `data/src/main/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImpl.kt`: load reservation rows, implement reservation writes, and reconcile after relevant writes.
- Test `data/src/test/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImplTest.kt`: repository persistence and update behavior.
- Create `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/ReservationUiModels.kt`: reservation manager UI state.
- Create `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/DelegationOverviewUiModels.kt`: delegation overview UI state.
- Modify `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/AssignmentBoardUiModels.kt`: add reservation reminders and accept capacity summaries.
- Modify `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/DelegateDetailUiModels.kt`: add reservation summary/actions.
- Modify `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/MainViewModel.kt`: add delegation/reservation state and callbacks.
- Modify `app/src/jvmMain/kotlin/org/munika/assignment/App.kt`: wire callbacks.
- Modify `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/MainScreen.kt`: add top-bar access, capacity stats, panels, and dialogs.
- Create `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/DelegationOverviewPanel.kt`: non-modal delegation overview panel.
- Create `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/dialog/ReservationManagerDialog.kt`: compact reservation management modal.
- Modify `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/CommitteeColumn.kt`: display reservation reminders.
- Modify `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/DelegateSidePanel.kt`: display and edit delegate reservation.
- Test `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/ReservationUiModelsTest.kt`: reservation manager state.
- Test `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/DelegationOverviewUiModelsTest.kt`: delegation overview state.
- Modify `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/AssignmentBoardUiModelsTest.kt`: capacity and reminder tests.
- Modify `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/DelegateDetailUiModelsTest.kt`: delegate reservation summary tests.
- Modify `docs/DESIGN.md`: document reservation product rules and UI.
- Modify `docs/DATA_MODEL.md`: document reservation persistence and update behavior.
- Modify `docs/TODO.md`: remove or rewrite the reservation item.

---

### Task 1: Domain Reservation Model And Reconciler

**Files:**
- Create: `domain/src/main/kotlin/org/munika/assignment/domain/model/ReservationId.kt`
- Create: `domain/src/main/kotlin/org/munika/assignment/domain/model/ReservationDelegateLinkId.kt`
- Create: `domain/src/main/kotlin/org/munika/assignment/domain/model/ReservationLinkSource.kt`
- Create: `domain/src/main/kotlin/org/munika/assignment/domain/model/ReservationDelegateLink.kt`
- Create: `domain/src/main/kotlin/org/munika/assignment/domain/model/Reservation.kt`
- Create: `domain/src/main/kotlin/org/munika/assignment/domain/model/ReservationReconciler.kt`
- Modify: `domain/src/main/kotlin/org/munika/assignment/domain/model/Conference.kt`
- Modify: `domain/src/main/kotlin/org/munika/assignment/domain/model/Delegate.kt`
- Test: `domain/src/test/kotlin/org/munika/assignment/domain/model/ReservationReconcilerTest.kt`

- [ ] **Step 1: Write failing reconciler tests**

Create `ReservationReconcilerTest.kt`:

```kotlin
package org.munika.assignment.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReservationReconcilerTest {
    @Test
    fun `manual link consumes active delegate regardless of payment and accepted status`() {
        val graph = reservationGraph()
        val unpaid = graph.delegate(
            id = "unpaid",
            firstName = "Alex",
            lastName = "Manual",
            paymentStatus = PaymentStatus.Unpaid,
            applicationStatus = "Pending",
            submittedAt = null,
            highSchooler = true,
        )
        val reservation = graph.reservation(id = "reservation", highSchool = 1, university = 0)
        val manual = graph.link("manual", reservation, unpaid, ReservationLinkSource.Manual)

        val result = ReservationReconciler.reconcile(graph.conference, listOf(manual))

        assertEquals(listOf(manual), result.links)
        assertEquals(0, reservation.remainingHighSchoolCount)
    }

    @Test
    fun `deactivated delegate does not consume manual reservation`() {
        val graph = reservationGraph()
        val delegate = graph.delegate(id = "delegate", active = false, highSchooler = true)
        val reservation = graph.reservation(id = "reservation", highSchool = 1, university = 0)
        val manual = graph.link("manual", reservation, delegate, ReservationLinkSource.Manual)

        val result = ReservationReconciler.reconcile(graph.conference, listOf(manual))

        assertTrue(result.links.isEmpty())
        assertEquals(listOf("manual"), result.removedLinkIds.map { it.value })
    }

    @Test
    fun `delegation auto links active paid accepted delegates by submission date`() {
        val graph = reservationGraph()
        val delegation = graph.delegation("delegation")
        val later = graph.delegate(id = "later", firstName = "Later", submittedAt = Instant.parse("2026-01-03T00:00:00Z"), delegation = delegation, highSchooler = true)
        val earlier = graph.delegate(id = "earlier", firstName = "Earlier", submittedAt = Instant.parse("2026-01-01T00:00:00Z"), delegation = delegation, highSchooler = true)
        graph.delegate(id = "unpaid", paymentStatus = PaymentStatus.Unpaid, submittedAt = Instant.parse("2026-01-02T00:00:00Z"), delegation = delegation, highSchooler = true)
        val reservation = graph.reservation(id = "reservation", highSchool = 1, university = 0, delegation = delegation)

        val result = ReservationReconciler.reconcile(graph.conference, emptyList())

        assertEquals(listOf(earlier.id), result.links.map { it.delegate.id })
        assertFalse(result.links.any { it.delegate === later })
        assertEquals(reservation.id, result.links.single().reservation.id)
        assertEquals(ReservationLinkSource.DelegationAuto, result.links.single().source)
    }

    @Test
    fun `auto fill skips delegate already consuming another reservation`() {
        val graph = reservationGraph()
        val delegation = graph.delegation("delegation")
        val first = graph.delegate(id = "first", submittedAt = Instant.parse("2026-01-01T00:00:00Z"), delegation = delegation, highSchooler = true)
        val second = graph.delegate(id = "second", submittedAt = Instant.parse("2026-01-02T00:00:00Z"), delegation = delegation, highSchooler = true)
        val manualReservation = graph.reservation(id = "manual-reservation", highSchool = 1, university = 0)
        val autoReservation = graph.reservation(id = "auto-reservation", highSchool = 1, university = 0, delegation = delegation)
        val manual = graph.link("manual", manualReservation, first, ReservationLinkSource.Manual)

        val result = ReservationReconciler.reconcile(graph.conference, listOf(manual))

        assertEquals(listOf(first.id, second.id), result.links.map { it.delegate.id })
        assertEquals(autoReservation.id, result.links.single { it.source == ReservationLinkSource.DelegationAuto }.reservation.id)
    }

    @Test
    fun `valid reduction trims excess automatic links`() {
        val graph = reservationGraph()
        val delegation = graph.delegation("delegation")
        val first = graph.delegate(id = "first", submittedAt = Instant.parse("2026-01-01T00:00:00Z"), delegation = delegation, highSchooler = true)
        val second = graph.delegate(id = "second", submittedAt = Instant.parse("2026-01-02T00:00:00Z"), delegation = delegation, highSchooler = true)
        val reservation = graph.reservation(id = "reservation", highSchool = 1, university = 0, delegation = delegation)
        val staleFirst = graph.link("auto-first", reservation, first, ReservationLinkSource.DelegationAuto)
        val staleSecond = graph.link("auto-second", reservation, second, ReservationLinkSource.DelegationAuto)

        val result = ReservationReconciler.reconcile(graph.conference, listOf(staleFirst, staleSecond))

        assertEquals(listOf(first.id), result.links.map { it.delegate.id })
        assertEquals(listOf("auto-second"), result.removedLinkIds.map { it.value })
    }

    private fun reservationGraph(): ReservationGraphFixture = ReservationGraphFixture()
}
```

Add the `ReservationGraphFixture` helper in the same test file below the test class. Keep it local to the test file:

```kotlin
private class ReservationGraphFixture {
    val conference = Conference(
        id = ConferenceId("conference"),
        name = "KAMUN",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    fun delegation(id: String): Delegation {
        val delegation = Delegation(
            id = DelegationId(id),
            conference = conference,
            name = id,
            country = null,
            expectedDelegateCount = null,
            currentDelegateCountFromMymun = null,
        )
        conference.delegations += delegation
        return delegation
    }

    fun delegate(
        id: String,
        firstName: String = id,
        lastName: String = "Delegate",
        paymentStatus: PaymentStatus = PaymentStatus.Paid,
        applicationStatus: String? = "Accepted",
        submittedAt: Instant? = Instant.parse("2026-01-01T00:00:00Z"),
        active: Boolean = true,
        delegation: Delegation? = null,
        highSchooler: Boolean = false,
    ): Delegate {
        val delegate = Delegate(
            id = DelegateId(id),
            conference = conference,
            name = PersonName(firstName, lastName),
            email = null,
            profile = DelegateProfile(
                country = null,
                city = null,
                streetAddress = null,
                zipCode = null,
                diet = null,
                phone = null,
                nationality = null,
                dateOfBirth = null,
                delegationNameFromMymun = delegation?.name,
                applicationStatus = applicationStatus,
            ),
            paymentStatus = paymentStatus,
            active = active,
            isHeadDelegate = false,
            delegation = delegation,
            submittedAt = submittedAt,
        )
        conference.delegates += delegate
        val highSchoolQuestion = conference.questions.firstOrNull()
            ?: Question(
                id = QuestionId("high-school"),
                questionText = "High school?",
                role = StandardQuestionRoles.HighSchoolerStatus,
            ).also { conference.questions += it }
        delegate.answers += QuestionAnswer(
            id = QuestionAnswerId("answer-$id"),
            delegate = delegate,
            question = highSchoolQuestion,
            rawAnswer = if (highSchooler) "Yes" else "No",
        )
        return delegate
    }

    fun reservation(
        id: String,
        highSchool: Int,
        university: Int,
        delegation: Delegation? = null,
    ): Reservation {
        val reservation = Reservation(
            id = ReservationId(id),
            conference = conference,
            name = id,
            highSchoolCount = highSchool,
            universityCount = university,
            delegation = delegation,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        conference.reservations += reservation
        return reservation
    }

    fun link(
        id: String,
        reservation: Reservation,
        delegate: Delegate,
        source: ReservationLinkSource,
    ): ReservationDelegateLink =
        ReservationDelegateLink(
            id = ReservationDelegateLinkId(id),
            reservation = reservation,
            delegate = delegate,
            source = source,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        ).also {
            reservation.delegateLinks += it
            delegate.reservationLink = it
        }
}
```

- [ ] **Step 2: Run domain tests and confirm failure**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :domain:test --tests org.munika.assignment.domain.model.ReservationReconcilerTest --stacktrace
```

Expected: compile failure for missing reservation model and reconciler types.

- [ ] **Step 3: Add reservation domain types**

Create `ReservationId.kt`:

```kotlin
package org.munika.assignment.domain.model

/**
 * Stable application ID for an organizer-managed reservation.
 */
@JvmInline
value class ReservationId(val value: String)
```

Create `ReservationDelegateLinkId.kt`:

```kotlin
package org.munika.assignment.domain.model

/**
 * Stable application ID for a persisted reservation usage link.
 */
@JvmInline
value class ReservationDelegateLinkId(val value: String)
```

Create `ReservationLinkSource.kt`:

```kotlin
package org.munika.assignment.domain.model

/**
 * Explains why one delegate consumes a reservation.
 */
enum class ReservationLinkSource {
    Manual,
    DelegationAuto,
}
```

Create `ReservationDelegateLink.kt`:

```kotlin
package org.munika.assignment.domain.model

import java.time.Instant

/**
 * Persisted usage of one reservation by one delegate.
 *
 * Manual links are organizer choices. DelegationAuto links are maintained by reconciliation.
 */
class ReservationDelegateLink(
    override val id: ReservationDelegateLinkId,
    val reservation: Reservation,
    val delegate: Delegate,
    val source: ReservationLinkSource,
    val createdAt: Instant,
    var updatedAt: Instant,
) : DomainObject<ReservationDelegateLinkId>
```

Create `Reservation.kt`:

```kotlin
package org.munika.assignment.domain.model

import java.time.Instant

/**
 * Organizer-managed capacity promise for high-school and/or university delegates.
 *
 * Reservations are not slot assignments. A linked delegation only drives automatic usage.
 */
class Reservation(
    override val id: ReservationId,
    val conference: Conference,
    val name: String,
    val highSchoolCount: Int,
    val universityCount: Int,
    val delegation: Delegation? = null,
    val delegateLinks: MutableList<ReservationDelegateLink> = mutableListOf(),
    val createdAt: Instant,
    var updatedAt: Instant,
) : DomainObject<ReservationId> {
    init {
        require(name.isNotBlank()) { "Reservation name must not be blank." }
        require(highSchoolCount >= 0) { "High-school reservation count must not be negative." }
        require(universityCount >= 0) { "University reservation count must not be negative." }
        require(highSchoolCount + universityCount > 0) { "Reservation must reserve at least one delegate." }
        require(delegation == null || delegation.conference === conference) {
            "Reservation delegation must belong to the same conference."
        }
    }

    val activeLinks: List<ReservationDelegateLink>
        get() = delegateLinks.filter { it.delegate.active }

    val usedHighSchoolCount: Int
        get() = activeLinks.count { it.delegate.isHighSchooler }

    val usedUniversityCount: Int
        get() = activeLinks.count { !it.delegate.isHighSchooler }

    val remainingHighSchoolCount: Int
        get() = (highSchoolCount - usedHighSchoolCount).coerceAtLeast(0)

    val remainingUniversityCount: Int
        get() = (universityCount - usedUniversityCount).coerceAtLeast(0)
}
```

- [ ] **Step 4: Attach reservations to conference and delegates**

Modify `Conference` constructor to add reservations:

```kotlin
val reservations: MutableList<Reservation> = mutableListOf(),
```

Add these properties to `Conference`:

```kotlin
val remainingHighSchoolReservationCount: Int
    get() = reservations.sumOf { it.remainingHighSchoolCount }

val remainingUniversityReservationCount: Int
    get() = reservations.sumOf { it.remainingUniversityCount }
```

Add this property to `Delegate`:

```kotlin
var reservationLink: ReservationDelegateLink? = null
    internal set
```

- [ ] **Step 5: Implement pure reconciliation**

Create `ReservationReconciler.kt`:

```kotlin
package org.munika.assignment.domain.model

import java.time.Instant

/**
 * Maintains automatic reservation usage links from current conference data.
 */
object ReservationReconciler {
    fun reconcile(
        conference: Conference,
        existingLinks: List<ReservationDelegateLink>,
        now: Instant = Instant.now(),
        idFactory: () -> ReservationDelegateLinkId = { ReservationDelegateLinkId("reservation-link-${System.nanoTime()}") },
    ): ReservationReconciliationResult {
        val keptManual = existingLinks
            .filter { it.source == ReservationLinkSource.Manual && it.delegate.active }
            .distinctBy { it.delegate.id }

        val consumedDelegateIds = keptManual.mapTo(mutableSetOf()) { it.delegate.id }
        val nextLinks = keptManual.toMutableList()

        conference.reservations.forEach { reservation ->
            val delegation = reservation.delegation ?: return@forEach
            var highSchoolRemaining = reservation.highSchoolCount -
                nextLinks.count { it.reservation === reservation && it.delegate.isHighSchooler }
            var universityRemaining = reservation.universityCount -
                nextLinks.count { it.reservation === reservation && !it.delegate.isHighSchooler }

            delegation.delegates
                .asSequence()
                .filter { it.active }
                .filter { it.paymentStatus == PaymentStatus.Paid }
                .filter { it.hasActionableApplicationStatus }
                .filterNot { it.id in consumedDelegateIds }
                .sortedWith(delegateReservationOrder())
                .forEach { delegate ->
                    val isHighSchooler = delegate.isHighSchooler
                    val canUseBucket = if (isHighSchooler) highSchoolRemaining > 0 else universityRemaining > 0
                    if (!canUseBucket) return@forEach

                    val reused = existingLinks.firstOrNull {
                        it.source == ReservationLinkSource.DelegationAuto &&
                            it.reservation === reservation &&
                            it.delegate === delegate
                    }
                    val link = reused ?: ReservationDelegateLink(
                        id = idFactory(),
                        reservation = reservation,
                        delegate = delegate,
                        source = ReservationLinkSource.DelegationAuto,
                        createdAt = now,
                        updatedAt = now,
                    )
                    nextLinks += link
                    consumedDelegateIds += delegate.id
                    if (isHighSchooler) highSchoolRemaining-- else universityRemaining--
                }
        }

        val nextIds = nextLinks.mapTo(mutableSetOf()) { it.id }
        return ReservationReconciliationResult(
            links = nextLinks,
            removedLinkIds = existingLinks.filterNot { it.id in nextIds }.map { it.id },
        )
    }

    private val Delegate.hasActionableApplicationStatus: Boolean
        get() {
            val normalizedStatus = profile.applicationStatus?.trim()?.lowercase().orEmpty()
            return normalizedStatus == "accepted" || normalizedStatus == "assigned"
        }

    private fun delegateReservationOrder(): Comparator<Delegate> =
        compareBy<Delegate> { it.submittedAt ?: Instant.MAX }
            .thenBy { it.displayName }
            .thenBy { it.id.value }
}

data class ReservationReconciliationResult(
    val links: List<ReservationDelegateLink>,
    val removedLinkIds: List<ReservationDelegateLinkId>,
)
```

- [ ] **Step 6: Run domain tests and fix identity attachment**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :domain:test --tests org.munika.assignment.domain.model.ReservationReconcilerTest --stacktrace
```

Expected: any remaining failures are from object graph attachment. Fix by ensuring mapper/repository-created links always add themselves to `reservation.delegateLinks` and assign `delegate.reservationLink`.

- [ ] **Step 7: Commit domain model**

```powershell
git add domain/src/main/kotlin/org/munika/assignment/domain/model domain/src/test/kotlin/org/munika/assignment/domain/model/ReservationReconcilerTest.kt
git commit -m "Add reservation domain model"
```

---

### Task 2: Persistence Tables, Mapping, And Repository API

**Files:**
- Modify: `domain/src/main/kotlin/org/munika/assignment/domain/repository/ConferenceRepository.kt`
- Create: `data/src/main/kotlin/org/munika/assignment/data/db/tables/ReservationTable.kt`
- Create: `data/src/main/kotlin/org/munika/assignment/data/db/tables/ReservationDelegateLinkTable.kt`
- Create: `data/src/main/kotlin/org/munika/assignment/data/db/model/ReservationRow.kt`
- Create: `data/src/main/kotlin/org/munika/assignment/data/db/model/ReservationDelegateLinkRow.kt`
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/tables/MunAssignTables.kt`
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/DatabaseFactory.kt`
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/model/ConferenceGraphRows.kt`
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/mapper/ConferenceGraphMapper.kt`
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImpl.kt`
- Test: `data/src/test/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImplTest.kt`

- [ ] **Step 1: Add failing repository tests for create and manual links**

Append tests to `ConferenceRepositoryImplTest`:

```kotlin
@Test
fun `reservation can be created and loaded`() = runTest {
    val repository = testRepository()
    val conference = repository.createConference("KAMUN")

    val updated = repository.createReservation(
        conferenceId = conference.id.value,
        name = "Berlin School",
        highSchoolCount = 5,
        universityCount = 0,
        delegationId = null,
    )

    assertEquals("Berlin School", updated.reservations.single().name)
    assertEquals(5, updated.reservations.single().highSchoolCount)
}

@Test
fun `manual reservation link survives reload`() = runTest {
    val repository = seededRepositoryWithDelegate()
    val conference = repository.getConference("conference") ?: error("conference missing")
    val delegate = conference.delegates.single()
    val withReservation = repository.createReservation(
        conferenceId = conference.id.value,
        name = "Manual",
        highSchoolCount = 1,
        universityCount = 0,
        delegationId = null,
    )

    repository.assignDelegateReservation(
        conferenceId = conference.id.value,
        delegateId = delegate.id.value,
        reservationId = withReservation.reservations.single().id.value,
    )

    val reloaded = repository.getConference(conference.id.value) ?: error("conference missing")
    assertEquals("Manual", reloaded.delegates.single().reservationLink?.reservation?.name)
    assertEquals(ReservationLinkSource.Manual, reloaded.delegates.single().reservationLink?.source)
}
```

If the existing repository test helpers use different names, adapt the fixture calls only; keep the assertions and repository method names from this plan.

- [ ] **Step 2: Run data tests and confirm failure**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :data:test --tests org.munika.assignment.data.db.repository.ConferenceRepositoryImplTest --stacktrace
```

Expected: compile failure for missing repository methods and tables.

- [ ] **Step 3: Add repository interface methods**

Add to `ConferenceRepository`:

```kotlin
/**
 * Creates an organizer-managed reservation and reconciles automatic usage.
 */
suspend fun createReservation(
    conferenceId: String,
    name: String,
    highSchoolCount: Int,
    universityCount: Int,
    delegationId: String?,
): Conference

/**
 * Updates one organizer-managed reservation and reconciles automatic usage.
 */
suspend fun updateReservation(
    conferenceId: String,
    reservationId: String,
    name: String,
    highSchoolCount: Int,
    universityCount: Int,
    delegationId: String?,
): Conference

/**
 * Deletes one reservation and its usage links.
 */
suspend fun deleteReservation(conferenceId: String, reservationId: String): Conference

/**
 * Assigns one delegate to one reservation manually, replacing any previous reservation link.
 */
suspend fun assignDelegateReservation(
    conferenceId: String,
    delegateId: String,
    reservationId: String,
): Conference

/**
 * Removes one delegate's manual reservation link.
 */
suspend fun removeDelegateReservation(conferenceId: String, delegateId: String): Conference
```

- [ ] **Step 4: Add tables and row DTOs**

Create `ReservationTable.kt`:

```kotlin
package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object ReservationTable : Table("reservations") {
    val id = varchar("id", 36)
    val conferenceId = varchar("conference_id", 36).references(ConferenceTable.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val highSchoolCount = integer("high_school_count")
    val universityCount = integer("university_count")
    val delegationId = varchar("delegation_id", 36).nullable()
    val createdAtEpochMillis = long("created_at_epoch_millis")
    val updatedAtEpochMillis = long("updated_at_epoch_millis")

    override val primaryKey = PrimaryKey(id)
}
```

Create `ReservationDelegateLinkTable.kt`:

```kotlin
package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object ReservationDelegateLinkTable : Table("reservation_delegate_links") {
    val id = varchar("id", 36)
    val reservationId = varchar("reservation_id", 36).references(ReservationTable.id, onDelete = ReferenceOption.CASCADE)
    val delegateId = varchar("delegate_id", 36).references(DelegateTable.id, onDelete = ReferenceOption.CASCADE)
    val source = varchar("source", 32)
    val createdAtEpochMillis = long("created_at_epoch_millis")
    val updatedAtEpochMillis = long("updated_at_epoch_millis")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("ux_reservation_delegate_links_delegate", delegateId)
    }
}
```

Create `ReservationRow.kt`:

```kotlin
package org.munika.assignment.data.db.model

data class ReservationRow(
    val id: String,
    val conferenceId: String,
    val name: String,
    val highSchoolCount: Int,
    val universityCount: Int,
    val delegationId: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
```

Create `ReservationDelegateLinkRow.kt`:

```kotlin
package org.munika.assignment.data.db.model

data class ReservationDelegateLinkRow(
    val id: String,
    val reservationId: String,
    val delegateId: String,
    val source: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
```

- [ ] **Step 5: Register schema and graph rows**

Add both tables to `MunAssignTables` after `DelegationTable` and `DelegateTable` is acceptable as long as references exist:

```kotlin
ReservationTable,
ReservationDelegateLinkTable,
```

Add migration version 7 to `DatabaseFactory.migrations`:

```kotlin
SchemaMigration(version = 7, name = "add_reservations") {
    SchemaUtils.create(ReservationTable, ReservationDelegateLinkTable)
}
```

Import `ReservationTable` and `ReservationDelegateLinkTable`.

Add to `ConferenceGraphRows` constructor:

```kotlin
val reservations: List<ReservationRow>,
val reservationLinks: List<ReservationDelegateLinkRow>,
```

Update all `ConferenceGraphRows(...)` calls to pass `reservations = emptyList()` and `reservationLinks = emptyList()` until `loadConferenceGraph` is updated.

- [ ] **Step 6: Map reservations into domain graph**

In `ConferenceGraphMapper.toDomain`, after delegations and delegates exist, create reservations:

```kotlin
val reservationsById = rows.reservations.associate { row ->
    val reservation = Reservation(
        id = ReservationId(row.id),
        conference = conference,
        name = row.name,
        highSchoolCount = row.highSchoolCount,
        universityCount = row.universityCount,
        delegation = row.delegationId?.let { delegationsById[it] },
        createdAt = row.createdAtEpochMillis.toInstant(),
        updatedAt = row.updatedAtEpochMillis.toInstant(),
    )
    conference.reservations += reservation
    row.id to reservation
}

rows.reservationLinks.forEach { row ->
    val reservation = requireNotNull(reservationsById[row.reservationId]) {
        "Reservation link ${row.id} references missing reservation ${row.reservationId}."
    }
    val delegate = requireNotNull(delegatesById[row.delegateId]) {
        "Reservation link ${row.id} references missing delegate ${row.delegateId}."
    }
    val link = ReservationDelegateLink(
        id = ReservationDelegateLinkId(row.id),
        reservation = reservation,
        delegate = delegate,
        source = ReservationLinkSource.valueOf(row.source),
        createdAt = row.createdAtEpochMillis.toInstant(),
        updatedAt = row.updatedAtEpochMillis.toInstant(),
    )
    reservation.delegateLinks += link
    delegate.reservationLink = link
}
```

- [ ] **Step 7: Load rows and implement repository writes**

In `ConferenceRepositoryImpl.loadConferenceGraph`, select reservation rows for the conference and reservation link rows for those reservations. Add `toReservationRow()` and `toReservationDelegateLinkRow()` helpers mirroring existing row helpers.

Implement `createReservation`, `updateReservation`, `deleteReservation`, `assignDelegateReservation`, and `removeDelegateReservation` in `ConferenceRepositoryImpl`. Use `requireReservationInConference`, `requireDelegateInConference`, and `requireDelegationInConference` helpers with messages that name the invalid id.

For `createReservation`, validate:

```kotlin
require(name.trim().isNotBlank()) { "Reservation name must not be blank." }
require(highSchoolCount >= 0) { "High-school reservation count must not be negative." }
require(universityCount >= 0) { "University reservation count must not be negative." }
require(highSchoolCount + universityCount > 0) { "Reservation must reserve at least one delegate." }
```

For `updateReservation`, reject reducing below manual usage:

```kotlin
val manualLinks = currentManualReservationLinks(reservationId)
val manualHighSchool = manualLinks.count { it.delegateIsHighSchooler() }
val manualUniversity = manualLinks.size - manualHighSchool
require(highSchoolCount >= manualHighSchool) {
    "Reservation cannot be reduced below manually linked high-school delegates."
}
require(universityCount >= manualUniversity) {
    "Reservation cannot be reduced below manually linked university delegates."
}
```

After each successful write, call `reconcileReservationLinks(conferenceId)` and then `loadConferenceGraph(conferenceId)`.
`removeDelegateReservation` removes only manual links. If the delegate currently has a
`DelegationAuto` link, reject with `IllegalArgumentException` because automatic links are maintained
by reconciliation.

- [ ] **Step 8: Persist reconciliation results**

Implement private `reconcileReservationLinks(conferenceId: String)` in `ConferenceRepositoryImpl`:

1. Load the conference graph.
2. Collect current links from `conference.reservations.flatMap { it.delegateLinks }`.
3. Call `ReservationReconciler.reconcile(conference, currentLinks) { ReservationDelegateLinkId(UUID.randomUUID().toString()) }`.
4. Delete `ReservationDelegateLinkTable` rows whose ids are in `removedLinkIds`.
5. Upsert result links: update existing ids, insert new ids.

Call this method after:

- `createReservation`;
- `updateReservation`;
- `deleteReservation`;
- `assignDelegateReservation`;
- successful manual `removeDelegateReservation`;
- `deactivateDelegate`;
- `reactivateDelegate`;
- the successful MyMUN update write path before returning the update result.

- [ ] **Step 9: Run repository tests**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :data:test --tests org.munika.assignment.data.db.repository.ConferenceRepositoryImplTest --stacktrace
```

Expected: PASS for repository tests.

- [ ] **Step 10: Commit persistence work**

```powershell
git add domain/src/main/kotlin/org/munika/assignment/domain/repository/ConferenceRepository.kt data/src/main/kotlin data/src/test/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImplTest.kt
git commit -m "Persist reservation usage links"
```

---

### Task 3: UI Model State For Reservations, Delegations, And Capacity

**Files:**
- Create: `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/ReservationUiModels.kt`
- Create: `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/DelegationOverviewUiModels.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/AssignmentBoardUiModels.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/DelegateDetailUiModels.kt`
- Test: `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/ReservationUiModelsTest.kt`
- Test: `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/DelegationOverviewUiModelsTest.kt`
- Modify: `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/AssignmentBoardUiModelsTest.kt`
- Modify: `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/DelegateDetailUiModelsTest.kt`

- [ ] **Step 1: Write failing UI model tests**

Create `ReservationUiModelsTest.kt` with tests for:

```kotlin
@Test
fun `reservation manager groups manual and automatic consumers`() {
    val conference = reservationUiConference()
    val manager = conference.toReservationManagerUiState()

    assertEquals(1, manager.reservations.single().manualConsumers.size)
    assertEquals(1, manager.reservations.single().automaticConsumers.size)
}

@Test
fun `reservation manager reports remaining counts`() {
    val conference = reservationUiConference()
    val summary = conference.toReservationManagerUiState().reservations.single()

    assertEquals(4, summary.remainingHighSchoolCount)
    assertEquals(0, summary.remainingUniversityCount)
}
```

Create `DelegationOverviewUiModelsTest.kt` with:

```kotlin
@Test
fun `delegation overview shows linked reservations and active paid accepted count`() {
    val conference = reservationUiConference()
    val overview = conference.toDelegationOverviewUiState()

    assertEquals("Berlin School", overview.delegations.single().name)
    assertEquals(2, overview.delegations.single().activePaidAcceptedDelegateCount)
    assertEquals(1, overview.delegations.single().linkedReservations.size)
}
```

Append board test:

```kotlin
@Test
fun `board committee shows matching reservation reminder`() {
    val conference = reservationUiConference()
    val board = conference.toAssignmentBoardUiState()

    assertEquals("HS reservations", board.committees.first { it.isHighSchoolCommittee }.reservationReminder?.label)
    assertNull(board.committees.first { !it.isHighSchoolCommittee }.reservationReminder)
}
```

Append delegate detail test:

```kotlin
@Test
fun `delegate detail shows current reservation`() {
    val conference = reservationUiConference()
    val delegateId = conference.delegates.first { it.reservationLink != null }.id.value

    val detail = conference.toDelegateDetailUiState(delegateId)

    assertEquals("Berlin School", detail?.reservation?.name)
}
```

- [ ] **Step 2: Run UI model tests and confirm failure**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:jvmTest --tests org.munika.assignment.ui.viewmodel.* --stacktrace
```

Expected: compile failure for missing UI model types and mappers.

- [ ] **Step 3: Add reservation UI models**

Create `ReservationUiModels.kt`:

```kotlin
package org.munika.assignment.ui.viewmodel

import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Reservation
import org.munika.assignment.domain.model.ReservationLinkSource

data class ReservationManagerUiState(
    val reservations: List<ReservationSummaryUiState>,
    val delegations: List<ReservationDelegationOptionUiState>,
)

data class ReservationSummaryUiState(
    val id: String,
    val name: String,
    val highSchoolCount: Int,
    val universityCount: Int,
    val remainingHighSchoolCount: Int,
    val remainingUniversityCount: Int,
    val delegationId: String?,
    val delegationName: String?,
    val manualConsumers: List<ReservationConsumerUiState>,
    val automaticConsumers: List<ReservationConsumerUiState>,
)

data class ReservationConsumerUiState(
    val delegateId: String,
    val delegateName: String,
    val isHighSchooler: Boolean,
    val sourceLabel: String,
)

data class ReservationDelegationOptionUiState(
    val id: String,
    val label: String,
)

fun Conference.toReservationManagerUiState(): ReservationManagerUiState =
    ReservationManagerUiState(
        reservations = reservations.map { it.toSummaryUiState() }.sortedBy { it.name },
        delegations = delegations.map {
            ReservationDelegationOptionUiState(
                id = it.id.value,
                label = it.displayLabel,
            )
        }.sortedBy { it.label },
    )

private fun Reservation.toSummaryUiState(): ReservationSummaryUiState =
    ReservationSummaryUiState(
        id = id.value,
        name = name,
        highSchoolCount = highSchoolCount,
        universityCount = universityCount,
        remainingHighSchoolCount = remainingHighSchoolCount,
        remainingUniversityCount = remainingUniversityCount,
        delegationId = delegation?.id?.value,
        delegationName = delegation?.displayLabel,
        manualConsumers = delegateLinks.consumerStates(ReservationLinkSource.Manual),
        automaticConsumers = delegateLinks.consumerStates(ReservationLinkSource.DelegationAuto),
    )

private fun List<org.munika.assignment.domain.model.ReservationDelegateLink>.consumerStates(
    source: ReservationLinkSource,
): List<ReservationConsumerUiState> =
    filter { it.source == source && it.delegate.active }
        .map {
            ReservationConsumerUiState(
                delegateId = it.delegate.id.value,
                delegateName = it.delegate.displayName,
                isHighSchooler = it.delegate.isHighSchooler,
                sourceLabel = if (source == ReservationLinkSource.Manual) "Manual" else "Delegation",
            )
        }
        .sortedBy { it.delegateName }
```

- [ ] **Step 4: Add delegation overview UI models**

Create `DelegationOverviewUiModels.kt`:

```kotlin
package org.munika.assignment.ui.viewmodel

import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.Delegation
import org.munika.assignment.domain.model.PaymentStatus

data class DelegationOverviewUiState(
    val delegations: List<DelegationOverviewRowUiState>,
)

data class DelegationOverviewRowUiState(
    val id: String,
    val name: String,
    val country: String?,
    val expectedDelegateCount: Int?,
    val currentDelegateCountFromMymun: Int?,
    val activePaidAcceptedDelegateCount: Int,
    val linkedReservations: List<DelegationReservationUiState>,
    val delegates: List<DelegationDelegateUiState>,
)

data class DelegationReservationUiState(
    val id: String,
    val name: String,
    val usedCount: Int,
    val totalCount: Int,
)

data class DelegationDelegateUiState(
    val id: String,
    val name: String,
    val reservationName: String?,
    val isHighSchooler: Boolean,
)

fun Conference.toDelegationOverviewUiState(): DelegationOverviewUiState =
    DelegationOverviewUiState(
        delegations = delegations.map { it.toOverviewRow(this) }.sortedBy { it.name },
    )

private fun Delegation.toOverviewRow(conference: Conference): DelegationOverviewRowUiState {
    val linkedReservations = conference.reservations.filter { it.delegation === this }
    return DelegationOverviewRowUiState(
        id = id.value,
        name = name,
        country = country,
        expectedDelegateCount = expectedDelegateCount,
        currentDelegateCountFromMymun = currentDelegateCountFromMymun,
        activePaidAcceptedDelegateCount = delegates.count { it.active && it.paymentStatus == PaymentStatus.Paid && it.hasActionableApplicationStatus() },
        linkedReservations = linkedReservations.map {
            DelegationReservationUiState(
                id = it.id.value,
                name = it.name,
                usedCount = it.activeLinks.size,
                totalCount = it.highSchoolCount + it.universityCount,
            )
        }.sortedBy { it.name },
        delegates = delegates.map {
            DelegationDelegateUiState(
                id = it.id.value,
                name = it.displayName,
                reservationName = it.reservationLink?.reservation?.name,
                isHighSchooler = it.isHighSchooler,
            )
        }.sortedBy { it.name },
    )
}

private fun Delegate.hasActionableApplicationStatus(): Boolean {
    val normalizedStatus = profile.applicationStatus?.trim()?.lowercase().orEmpty()
    return normalizedStatus == "accepted" || normalizedStatus == "assigned"
}
```

- [ ] **Step 5: Add board reminder and capacity UI models**

Modify `CommitteeBoardUiState`:

```kotlin
val reservationReminder: ReservationReminderUiState?,
```

Add:

```kotlin
data class ReservationReminderUiState(
    val label: String,
    val totalCount: Int,
    val remainingCount: Int,
)
```

In `Conference.toAssignmentBoardUiState`, compute:

```kotlin
val highSchoolReservations = reservations.sumOf { it.highSchoolCount }
val remainingHighSchoolReservations = reservations.sumOf { it.remainingHighSchoolCount }
val universityReservations = reservations.sumOf { it.universityCount }
val remainingUniversityReservations = reservations.sumOf { it.remainingUniversityCount }
```

Pass a reminder for each committee:

```kotlin
reservationReminder = if (committee.isHighSchoolCommittee && highSchoolReservations > 0) {
    ReservationReminderUiState("HS reservations", highSchoolReservations, remainingHighSchoolReservations)
} else if (!committee.isHighSchoolCommittee && universityReservations > 0) {
    ReservationReminderUiState("Uni reservations", universityReservations, remainingUniversityReservations)
} else {
    null
}
```

- [ ] **Step 6: Add delegate detail reservation summary**

Add to `DelegateDetailUiState`:

```kotlin
val reservation: DelegateReservationUiState?,
```

Add:

```kotlin
data class DelegateReservationUiState(
    val id: String,
    val name: String,
    val sourceLabel: String,
)
```

In the delegate detail mapper:

```kotlin
reservation = reservationLink?.let {
    DelegateReservationUiState(
        id = it.reservation.id.value,
        name = it.reservation.name,
        sourceLabel = if (it.source == ReservationLinkSource.Manual) "Manual" else "Delegation",
    )
}
```

- [ ] **Step 7: Run UI model tests**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:jvmTest --tests org.munika.assignment.ui.viewmodel.* --stacktrace
```

Expected: PASS for UI model tests.

- [ ] **Step 8: Commit UI model work**

```powershell
git add app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel
git commit -m "Add reservation UI models"
```

---

### Task 4: Main ViewModel Wiring

**Files:**
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/MainViewModel.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/App.kt`
- Test: `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/MainViewModelTest.kt`

- [ ] **Step 1: Add failing ViewModel tests**

Append tests to `MainViewModelTest`:

```kotlin
@Test
fun `open delegation overview sets panel state`() = runTest {
    val viewModel = testViewModel()

    viewModel.openDelegationOverview()

    assertTrue(viewModel.state.value.delegationOverviewOpen)
}

@Test
fun `create reservation refreshes selected conference`() = runTest {
    val repository = FakeConferenceRepository.withConference()
    val viewModel = testViewModel(repository = repository)
    val conferenceId = viewModel.state.value.selectedConferenceId ?: error("conference missing")

    viewModel.createReservation(conferenceId, "Berlin School", 5, 0, null)
    advanceUntilIdle()

    assertEquals("Berlin School", viewModel.state.value.selectedConference?.reservations?.single()?.name)
}
```

- [ ] **Step 2: Run ViewModel tests and confirm failure**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:jvmTest --tests org.munika.assignment.ui.viewmodel.MainViewModelTest --stacktrace
```

Expected: compile failure for missing state and methods.

- [ ] **Step 3: Add main state fields**

Add to `MainUiState`:

```kotlin
val delegationOverviewOpen: Boolean = false,
val reservationManagerOpen: Boolean = false,
val reservationManagerDelegationId: String? = null,
val reservationActionError: String? = null,
```

- [ ] **Step 4: Add open and close methods**

Add to `MainViewModel`:

```kotlin
fun openDelegationOverview(): Unit {
    _state.update { it.copy(delegationOverviewOpen = true) }
}

fun closeDelegationOverview(): Unit {
    _state.update { it.copy(delegationOverviewOpen = false) }
}

fun openReservationManager(delegationId: String? = null): Unit {
    _state.update {
        it.copy(
            reservationManagerOpen = true,
            reservationManagerDelegationId = delegationId,
            reservationActionError = null,
        )
    }
}

fun closeReservationManager(): Unit {
    _state.update {
        it.copy(
            reservationManagerOpen = false,
            reservationManagerDelegationId = null,
            reservationActionError = null,
        )
    }
}

fun clearReservationActionError(): Unit {
    _state.update { it.copy(reservationActionError = null) }
}
```

- [ ] **Step 5: Add reservation write methods**

Add repository-wrapping methods with the same loading/error pattern as assignment actions:

```kotlin
fun createReservation(
    conferenceId: String,
    name: String,
    highSchoolCount: Int,
    universityCount: Int,
    delegationId: String?,
) {
    scope.launch {
        _state.update { it.copy(loading = true, reservationActionError = null) }
        try {
            val conference = repository.createReservation(conferenceId, name, highSchoolCount, universityCount, delegationId)
            _state.update { it.copy(selectedConference = conference, loading = false) }
        } catch (exception: IllegalArgumentException) {
            _state.update { it.copy(loading = false, reservationActionError = exception.message ?: "Reservation could not be saved.") }
        } catch (exception: Exception) {
            _state.update { it.copy(loading = false, reservationActionError = "Reservation update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}") }
        }
    }
}
```

Add matching methods for `updateReservation`, `deleteReservation`, `assignDelegateReservation`, and `removeDelegateReservation`. If `removeDelegateReservation` rejects an automatic link, expose the repository message through `reservationActionError`.

- [ ] **Step 6: Wire through App**

Add new parameters to `App` -> `MainScreen`:

```kotlin
onOpenDelegationOverview = viewModel::openDelegationOverview,
onCloseDelegationOverview = viewModel::closeDelegationOverview,
onOpenReservationManager = viewModel::openReservationManager,
onCloseReservationManager = viewModel::closeReservationManager,
onCreateReservation = viewModel::createReservation,
onUpdateReservation = viewModel::updateReservation,
onDeleteReservation = viewModel::deleteReservation,
onAssignDelegateReservation = viewModel::assignDelegateReservation,
onRemoveDelegateReservation = viewModel::removeDelegateReservation,
onClearReservationActionError = viewModel::clearReservationActionError,
```

- [ ] **Step 7: Run ViewModel tests**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:jvmTest --tests org.munika.assignment.ui.viewmodel.MainViewModelTest --stacktrace
```

Expected: PASS for ViewModel tests.

- [ ] **Step 8: Commit ViewModel wiring**

```powershell
git add app/src/jvmMain/kotlin/org/munika/assignment/App.kt app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/MainViewModel.kt app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/MainViewModelTest.kt
git commit -m "Wire reservation actions through view model"
```

---

### Task 5: Compose Delegation Overview And Reservation Manager

**Files:**
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/MainScreen.kt`
- Create: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/DelegationOverviewPanel.kt`
- Create: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/dialog/ReservationManagerDialog.kt`

- [ ] **Step 1: Add DelegationOverviewPanel**

Create `DelegationOverviewPanel.kt`:

```kotlin
package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.munika.assignment.ui.viewmodel.DelegationOverviewUiState

@Composable
fun DelegationOverviewPanel(
    overview: DelegationOverviewUiState,
    onOpenReservationManager: (delegationId: String?) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(420.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Delegations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close delegations")
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(overview.delegations, key = { it.id }) { delegation ->
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(delegation.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                delegation.country,
                                delegation.expectedDelegateCount?.let { "expected $it" },
                                delegation.currentDelegateCountFromMymun?.let { "current $it" },
                                "${delegation.activePaidAcceptedDelegateCount} active paid accepted",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        delegation.linkedReservations.forEach { reservation ->
                            Text(
                                "${reservation.name}: ${reservation.usedCount}/${reservation.totalCount} used",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        OutlinedButton(onClick = { onOpenReservationManager(delegation.id) }) {
                            Icon(Icons.Filled.EventSeat, contentDescription = null)
                            Text("Reservations")
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Add ReservationManagerDialog**

Create `ReservationManagerDialog.kt`:

```kotlin
package org.munika.assignment.ui.screen.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.munika.assignment.ui.viewmodel.ReservationManagerUiState

@Composable
fun ReservationManagerDialog(
    manager: ReservationManagerUiState,
    error: String?,
    initialDelegationId: String?,
    onDismiss: () -> Unit,
    onCreateReservation: (name: String, highSchoolCount: Int, universityCount: Int, delegationId: String?) -> Unit,
) {
    var createOpen by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reservations") },
        text = {
            Column(modifier = Modifier.width(680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(manager.reservations, key = { it.id }) { reservation ->
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(reservation.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "HS ${reservation.highSchoolCount - reservation.remainingHighSchoolCount}/${reservation.highSchoolCount} · " +
                                    "Uni ${reservation.universityCount - reservation.remainingUniversityCount}/${reservation.universityCount}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            reservation.delegationName?.let { Text("Delegation: $it", style = MaterialTheme.typography.bodySmall) }
                            if (reservation.manualConsumers.isNotEmpty()) {
                                Text("Manual: ${reservation.manualConsumers.joinToString { it.delegateName }}", style = MaterialTheme.typography.labelMedium)
                            }
                            if (reservation.automaticConsumers.isNotEmpty()) {
                                Text("Delegation: ${reservation.automaticConsumers.joinToString { it.delegateName }}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { createOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Create reservation")
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } },
    )

    if (createOpen) {
        ReservationEditorDialog(
            initialDelegationId = initialDelegationId,
            onDismiss = { createOpen = false },
            onSave = { name, highSchoolCount, universityCount, delegationId ->
                onCreateReservation(name, highSchoolCount, universityCount, delegationId)
                createOpen = false
            },
        )
    }
}

@Composable
private fun ReservationEditorDialog(
    initialDelegationId: String?,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var highSchoolCount by remember { mutableStateOf("0") }
    var universityCount by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create reservation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(highSchoolCount, { highSchoolCount = it.filter(Char::isDigit) }, label = { Text("High school") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(universityCount, { universityCount = it.filter(Char::isDigit) }, label = { Text("University") }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, highSchoolCount.toIntOrNull() ?: 0, universityCount.toIntOrNull() ?: 0, initialDelegationId) }) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

- [ ] **Step 3: Wire panels into MainScreen**

In `MainScreen`, add parameters for delegation/reservation callbacks from Task 4.

Add a top-bar icon button near the delegate list button:

```kotlin
TooltipIconButton(
    tooltip = "Open delegations",
    icon = Icons.Filled.Groups,
    onClick = onOpenDelegationOverview,
    enabled = !state.loading && state.selectedConference != null,
)
```

Import `Icons.Filled.Groups`.

Inside `ConferenceWorkspace`, render:

```kotlin
if (delegationOverviewOpen) {
    DelegationOverviewPanel(
        overview = conference.toDelegationOverviewUiState(),
        onOpenReservationManager = onOpenReservationManager,
        onClose = onCloseDelegationOverview,
    )
}
```

After question configuration dialog rendering, render:

```kotlin
if (state.reservationManagerOpen && selectedConference != null) {
    ReservationManagerDialog(
        manager = selectedConference.toReservationManagerUiState(),
        error = state.reservationActionError,
        initialDelegationId = state.reservationManagerDelegationId,
        onDismiss = onCloseReservationManager,
        onCreateReservation = { name, hs, uni, delegationId ->
            onCreateReservation(selectedConference.id.value, name, hs, uni, delegationId)
        },
    )
}
```

- [ ] **Step 4: Run app compile**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:compileKotlinJvm --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit Compose management surfaces**

```powershell
git add app/src/jvmMain/kotlin/org/munika/assignment/ui/screen
git commit -m "Add reservation management UI"
```

---

### Task 6: Board, Top Bar, And Delegate Detail UI

**Files:**
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/MainScreen.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/CommitteeColumn.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/DelegateSidePanel.kt`

- [ ] **Step 1: Add accept-capacity top-bar stats**

In `ConferenceStats`, compute:

```kotlin
val highSchoolSlots = conference.committees.filter { it.isHighSchoolCommittee }.sumOf { it.slots.size }
val universitySlots = conference.committees.filterNot { it.isHighSchoolCommittee }.sumOf { it.slots.size }
val acceptedActivePaid = conference.delegates.filter {
    it.active && it.paymentStatus == PaymentStatus.Paid && it.hasActionableApplicationStatus()
}
val acceptedHighSchool = acceptedActivePaid.count { it.isHighSchooler }
val acceptedUniversity = acceptedActivePaid.count { !it.isHighSchooler }
val canAcceptHighSchool = highSchoolSlots - acceptedHighSchool - conference.remainingHighSchoolReservationCount
val canAcceptUniversity = universitySlots - acceptedUniversity - conference.remainingUniversityReservationCount
```

Add a `StatGroup`:

```kotlin
StatGroup(
    label = "Can accept",
    value = "$canAcceptHighSchool HS · $canAcceptUniversity Uni",
    tooltip = "Remaining acceptance capacity after active paid accepted delegates and remaining reservation capacity.",
)
```

- [ ] **Step 2: Add committee reservation reminder**

In `CommitteeColumn.CommitteeHeader`, after metrics, render:

```kotlin
committee.reservationReminder?.let { reminder ->
    BoardTooltip("Open reservations for this delegate type.") {
        Button(
            onClick = onOpenReservationManager,
            enabled = !loading,
        ) {
            Text("${reminder.label}: ${reminder.remainingCount}/${reminder.totalCount} open")
        }
    }
}
```

Thread `onOpenReservationManager: () -> Unit` from `AssignmentBoard` to `CommitteeColumn`.

- [ ] **Step 3: Add delegate detail reservation control**

In `DelegateSidePanel`, near the assignment/profile summary, render:

```kotlin
detail.reservation?.let { reservation ->
    DetailSection("Reservation") {
        Text("${reservation.name} (${reservation.sourceLabel})")
        TextButton(onClick = { onAssignReservation(detail.id) }) {
            Text("Change")
        }
        if (reservation.sourceLabel == "Manual") {
            TextButton(onClick = { onRemoveReservation(detail.id) }) {
                Text("Remove")
            }
        }
    }
} ?: DetailSection("Reservation") {
    Text("No reservation")
    TextButton(onClick = { onAssignReservation(detail.id) }) {
        Text("Assign reservation")
    }
}
```

If `DetailSection` has a different local signature, adapt this snippet to the existing local section component and keep the same visible behavior.

- [ ] **Step 4: Run app compile and UI model tests**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:compileKotlinJvm :app:jvmTest --tests org.munika.assignment.ui.viewmodel.* --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit board and detail UI**

```powershell
git add app/src/jvmMain/kotlin/org/munika/assignment/ui/screen app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel
git commit -m "Show reservation capacity in assignment UI"
```

---

### Task 7: Documentation And Final Verification

**Files:**
- Modify: `docs/DESIGN.md`
- Modify: `docs/DATA_MODEL.md`
- Modify: `docs/TODO.md`

- [ ] **Step 1: Update design docs**

In `docs/DESIGN.md`, add product rules:

```markdown
- Reservations are organizer-managed capacity promises and are not tied to slots.
- Reservations can be linked to delegations or manually linked to delegates.
- Deactivated delegates never consume reservation capacity.
- HS/Uni accept-capacity counters subtract active paid accepted delegates and remaining reservation capacity.
```

In `docs/DATA_MODEL.md`, add reservation ownership and tables:

```markdown
- `Reservation` stores organizer-managed HS/Uni capacity promises.
- `ReservationDelegateLink` stores current manual or automatic reservation usage.
- MyMUN updates preserve reservations and rebuild automatic delegation usage links.
```

Add core tables:

```markdown
- `reservations`
- `reservation_delegate_links`
```

In `docs/TODO.md`, replace the reservation item with any remaining polish discovered during implementation. If the implementation covers the listed item completely, remove the item.

- [ ] **Step 2: Run full tests**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :domain:test :data:test :app:jvmTest --stacktrace
```

Expected: PASS.

- [ ] **Step 3: Run compile**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:compileKotlinJvm --stacktrace
```

Expected: PASS.

- [ ] **Step 4: Run diff hygiene**

Run:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 5: Commit docs and verification fixes**

```powershell
git add docs/DESIGN.md docs/DATA_MODEL.md docs/TODO.md
git commit -m "Document reservation behavior"
```

---

## Self-Review

Spec coverage:

- Standalone reservations: Task 1 and Task 2.
- Delegation overview: Task 3 and Task 5.
- Reservation-to-delegation assignment: Task 2 repository methods and Task 5 manager UI.
- Manual delegate reservation assignment: Task 2 repository methods, Task 4 wiring, Task 6 delegate detail.
- Stored manual and automatic usage rows: Task 1 model and Task 2 persistence.
- Automatic handling by submission date: Task 1 reconciler and Task 2 persistence call sites.
- Deactivation removes consumption: Task 1 tests and Task 2 reconciliation call from deactivation.
- HS/Uni capacity counters: Task 3 UI models and Task 6 top bar.
- Committee reminders: Task 3 board state and Task 6 committee UI.
- MyMUN update preservation and rebuild: Task 2 repository update call site and tests.
- Docs update: Task 7.

Placeholder scan:

- This plan contains no placeholder sections. Code snippets use concrete class, method, and file names.

Type consistency:

- Domain model uses `Reservation`, `ReservationDelegateLink`, `ReservationLinkSource`, `ReservationId`, and `ReservationDelegateLinkId` consistently.
- Repository methods in Task 2 match ViewModel methods in Task 4.
- UI model mapper names in Task 3 match Compose calls in Task 5 and Task 6.
