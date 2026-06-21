package org.munika.assignment.ui.viewmodel

import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.QuestionConfiguration
import org.munika.assignment.domain.repository.ConferenceRepository
import org.munika.assignment.domain.repository.UpdateResult
import org.munika.assignment.domain.repository.UpdateSummary

class MainViewModelTest {
    @Test
    fun `delayed conference creation refreshes list without replacing a newer selection`() {
        val conferenceA = Conference(ConferenceId("conference-a"), "Conference A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val conferenceB = Conference(ConferenceId("conference-b"), "Conference B", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val created = Conference(ConferenceId("conference-created"), "Created", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val createStarted = CompletableDeferred<Unit>()
        val createResult = CompletableDeferred<Conference>()
        val repository = FakeConferenceRepository(
            initialConference = conferenceA,
            conferences = listOf(conferenceA, conferenceB),
            createdConferenceDeferred = createResult,
            createConferenceStarted = createStarted,
            conferencesAfterCreate = listOf(conferenceA, conferenceB, created),
        )
        val viewModel = viewModelWith(repository)
        waitUntil { viewModel.state.value.selectedConference === conferenceA }

        assertTrue(viewModel.createConference("Created"))
        runBlocking { createStarted.await() }
        viewModel.selectConference("conference-b")
        waitUntil { viewModel.state.value.selectedConference === conferenceB }
        createResult.complete(created)

        assertEquals(listOf("conference-a", "conference-b", "conference-created"), viewModel.state.value.conferences.map { it.id.value })
        assertEquals("conference-b", viewModel.state.value.selectedConferenceId)
        assertSame(conferenceB, viewModel.state.value.selectedConference)
        assertNull(viewModel.state.value.conferenceActionError)
        viewModel.close()
    }

    @Test
    fun `selecting another conference clears conference scoped UI state`() {
        val conferenceA = Conference(ConferenceId("conference-a"), "Conference A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val conferenceB = Conference(ConferenceId("conference-b"), "Conference B", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val viewModel = viewModelWith(FakeConferenceRepository(initialConference = conferenceA, conferences = listOf(conferenceA, conferenceB)))
        waitUntil { viewModel.state.value.selectedConference === conferenceA }
        viewModel.openDelegateDetail("delegate-a")
        viewModel.openDelegateList()
        viewModel.openQuestionConfiguration()
        viewModel.openReservationManager()

        viewModel.selectConference("conference-b")

        assertNull(viewModel.state.value.selectedDelegateId)
        assertFalse(viewModel.state.value.delegateListOpen)
        assertFalse(viewModel.state.value.questionConfigurationOpen)
        assertFalse(viewModel.state.value.reservationManagerOpen)
        viewModel.close()
    }

    @Test
    fun `delegation overview actions update open state`() {
        val viewModel = viewModelWith(FakeConferenceRepository())

        viewModel.openDelegationOverview()

        assertTrue(viewModel.state.value.delegationOverviewOpen)

        viewModel.closeDelegationOverview()

        assertFalse(viewModel.state.value.delegationOverviewOpen)
        viewModel.close()
    }

    @Test
    fun `reservation manager actions update scoped state`() {
        val viewModel = viewModelWith(FakeConferenceRepository())

        viewModel.openReservationManager(delegationId = "delegation")

        assertTrue(viewModel.state.value.reservationManagerOpen)
        assertEquals("delegation", viewModel.state.value.reservationManagerDelegationId)

        viewModel.closeReservationManager()

        assertFalse(viewModel.state.value.reservationManagerOpen)
        assertNull(viewModel.state.value.reservationManagerDelegationId)
        viewModel.close()
    }

    @Test
    fun `opening delegation from reservation manager closes manager and targets delegation overview`() {
        val viewModel = viewModelWith(FakeConferenceRepository())
        viewModel.openReservationManager(delegationId = "delegation")

        viewModel.openDelegationOverviewFor("delegation")

        assertFalse(viewModel.state.value.reservationManagerOpen)
        assertNull(viewModel.state.value.reservationManagerDelegationId)
        assertTrue(viewModel.state.value.delegationOverviewOpen)
        assertEquals("delegation", viewModel.state.value.delegationOverviewTargetId)

        viewModel.closeDelegationOverview()

        assertFalse(viewModel.state.value.delegationOverviewOpen)
        assertNull(viewModel.state.value.delegationOverviewTargetId)
        viewModel.close()
    }

    @Test
    fun `create reservation refreshes selected conference`() {
        val original = Conference(ConferenceId("conference"), "Original", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val reserved = Conference(ConferenceId("conference"), "Reserved", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val repository = FakeConferenceRepository(initialConference = original, createdReservationConference = reserved)
        val viewModel = viewModelWith(repository)

        viewModel.selectConference("conference")
        waitUntil { viewModel.state.value.selectedConference === original }

        viewModel.createReservation(
            conferenceId = "conference",
            name = "Alpha Hold",
            highSchoolCount = 2,
            universityCount = 1,
            delegationId = "delegation",
        )

        waitUntil { viewModel.state.value.selectedConference === reserved }
        assertSame(reserved, viewModel.state.value.selectedConference)
        assertNull(viewModel.state.value.reservationActionError)
        assertEquals("conference", repository.createdReservationConferenceId)
        assertEquals("Alpha Hold", repository.createdReservationName)
        assertEquals(2, repository.createdReservationHighSchoolCount)
        assertEquals(1, repository.createdReservationUniversityCount)
        assertEquals("delegation", repository.createdReservationDelegationId)
        viewModel.close()
    }

    @Test
    fun `delayed reservation update does not overwrite selected conference after selection changes`() {
        val conferenceA = Conference(ConferenceId("conference-a"), "Conference A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val conferenceB = Conference(ConferenceId("conference-b"), "Conference B", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val updatedA = Conference(ConferenceId("conference-a"), "Updated A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val createStarted = CompletableDeferred<Unit>()
        val createResult = CompletableDeferred<Conference>()
        val repository = FakeConferenceRepository(
            initialConference = conferenceA,
            conferences = listOf(conferenceA, conferenceB),
            createdReservationConferenceDeferred = createResult,
            createReservationStarted = createStarted,
        )
        val viewModel = viewModelWith(repository)

        waitUntil { viewModel.state.value.selectedConference === conferenceA }

        viewModel.createReservation(
            conferenceId = "conference-a",
            name = "Alpha Hold",
            highSchoolCount = 1,
            universityCount = 0,
            delegationId = null,
        )
        runBlocking { createStarted.await() }

        viewModel.selectConference("conference-b")
        waitUntil {
            viewModel.state.value.selectedConferenceId == "conference-b" &&
                viewModel.state.value.selectedConference === conferenceB
        }

        createResult.complete(updatedA)

        waitUntil { !viewModel.state.value.loading }
        assertEquals("conference-b", viewModel.state.value.selectedConferenceId)
        assertSame(conferenceB, viewModel.state.value.selectedConference)
        assertNull(viewModel.state.value.reservationActionError)
        viewModel.close()
    }

    @Test
    fun `remove delegate reservation stores validation message as delegate reservation error`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val repository = FakeConferenceRepository(
            initialConference = conference,
            removeDelegateReservationError = "Automatic reservation links are managed by delegation reconciliation.",
        )
        val viewModel = viewModelWith(repository)

        viewModel.selectConference("conference")
        waitUntil { viewModel.state.value.selectedConference === conference }

        viewModel.removeDelegateReservation(conferenceId = "conference", delegateId = "delegate")

        waitUntil {
            viewModel.state.value.delegateReservationActionError ==
                "Automatic reservation links are managed by delegation reconciliation."
        }
        assertNull(viewModel.state.value.reservationActionError)
        assertSame(conference, viewModel.state.value.selectedConference)
        viewModel.close()
    }

    @Test
    fun `set committee mode refreshes selected conference`() {
        val original = Conference(ConferenceId("conference"), "Original", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val updated = Conference(ConferenceId("conference"), "Mapped", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val repository = FakeConferenceRepository(initialConference = original, committeeModeConference = updated)
        val viewModel = viewModelWith(repository)

        viewModel.selectConference("conference")
        waitUntil { viewModel.state.value.selectedConference === original }

        viewModel.setCommitteeMode("committee", CommitteeMode.Mapped)

        waitUntil { viewModel.state.value.selectedConference === updated }
        assertSame(updated, viewModel.state.value.selectedConference)
        assertNull(viewModel.state.value.boardActionError)
        assertEquals("conference", repository.committeeModeConferenceId)
        assertEquals("committee", repository.committeeModeCommitteeId)
        assertEquals(CommitteeMode.Mapped, repository.committeeModeMode)
        viewModel.close()
    }

    @Test
    fun `delayed committee mutation success does not alter newly selected conference`() {
        val conferenceA = Conference(ConferenceId("conference-a"), "Conference A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val conferenceB = Conference(ConferenceId("conference-b"), "Conference B", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val updatedA = Conference(ConferenceId("conference-a"), "Updated A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val started = CompletableDeferred<Unit>()
        val result = CompletableDeferred<Result<Conference>>()
        val repository = FakeConferenceRepository(
            initialConference = conferenceA,
            conferences = listOf(conferenceA, conferenceB),
            committeeModeResultDeferred = result,
            committeeModeStarted = started,
        )
        val viewModel = viewModelWith(repository)
        waitUntil { viewModel.state.value.selectedConference === conferenceA }

        viewModel.setCommitteeMode("committee", CommitteeMode.Mapped)
        runBlocking { started.await() }
        viewModel.selectConference("conference-b")
        waitUntil { viewModel.state.value.selectedConference === conferenceB }
        result.complete(Result.success(updatedA))

        assertSame(conferenceB, viewModel.state.value.selectedConference)
        assertNull(viewModel.state.value.boardActionError)
        assertFalse(viewModel.state.value.loading)
        viewModel.close()
    }

    @Test
    fun `delayed committee mutation failure does not leak into newly selected conference`() {
        val conferenceA = Conference(ConferenceId("conference-a"), "Conference A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val conferenceB = Conference(ConferenceId("conference-b"), "Conference B", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val started = CompletableDeferred<Unit>()
        val result = CompletableDeferred<Result<Conference>>()
        val repository = FakeConferenceRepository(
            initialConference = conferenceA,
            conferences = listOf(conferenceA, conferenceB),
            committeeModeResultDeferred = result,
            committeeModeStarted = started,
        )
        val viewModel = viewModelWith(repository)
        waitUntil { viewModel.state.value.selectedConference === conferenceA }

        viewModel.setCommitteeMode("committee", CommitteeMode.Mapped)
        runBlocking { started.await() }
        viewModel.selectConference("conference-b")
        waitUntil { viewModel.state.value.selectedConference === conferenceB }
        result.complete(Result.failure(IllegalArgumentException("Conference A failed")))

        assertSame(conferenceB, viewModel.state.value.selectedConference)
        assertNull(viewModel.state.value.boardActionError)
        assertFalse(viewModel.state.value.loading)
        viewModel.close()
    }

    @Test
    fun `delayed workbook success does not alter newly selected conference`() {
        val conferenceA = Conference(ConferenceId("conference-a"), "Conference A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val conferenceB = Conference(ConferenceId("conference-b"), "Conference B", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val started = CompletableDeferred<Unit>()
        val result = CompletableDeferred<Result<UpdateResult>>()
        val repository = FakeConferenceRepository(initialConference = conferenceA, conferences = listOf(conferenceA, conferenceB))
        val viewModel = viewModelWith(repository) { _, _ ->
            started.complete(Unit)
            result.await().getOrThrow()
        }
        waitUntil { viewModel.state.value.selectedConference === conferenceA }

        viewModel.updateFromWorkbook(Path.of("conference-a.xlsx"))
        runBlocking { started.await() }
        viewModel.selectConference("conference-b")
        waitUntil { viewModel.state.value.selectedConference === conferenceB }
        result.complete(Result.success(UpdateResult(true, emptyList(), UpdateSummary(delegatesCreated = 4))))

        assertSame(conferenceB, viewModel.state.value.selectedConference)
        assertTrue(viewModel.state.value.updateIssues.isEmpty())
        assertNull(viewModel.state.value.updateSummary)
        assertFalse(viewModel.state.value.loading)
        viewModel.close()
    }

    @Test
    fun `delayed workbook failure does not leak into newly selected conference`() {
        val conferenceA = Conference(ConferenceId("conference-a"), "Conference A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val conferenceB = Conference(ConferenceId("conference-b"), "Conference B", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val started = CompletableDeferred<Unit>()
        val result = CompletableDeferred<Result<UpdateResult>>()
        val repository = FakeConferenceRepository(initialConference = conferenceA, conferences = listOf(conferenceA, conferenceB))
        val viewModel = viewModelWith(repository) { _, _ ->
            started.complete(Unit)
            result.await().getOrThrow()
        }
        waitUntil { viewModel.state.value.selectedConference === conferenceA }

        viewModel.updateFromWorkbook(Path.of("conference-a.xlsx"))
        runBlocking { started.await() }
        viewModel.selectConference("conference-b")
        waitUntil { viewModel.state.value.selectedConference === conferenceB }
        result.complete(Result.failure(IllegalStateException("Conference A workbook failed")))

        assertSame(conferenceB, viewModel.state.value.selectedConference)
        assertTrue(viewModel.state.value.updateIssues.isEmpty())
        assertNull(viewModel.state.value.updateSummary)
        assertFalse(viewModel.state.value.loading)
        viewModel.close()
    }

    @Test
    fun `set position mapped committee refreshes selected conference`() {
        val original = Conference(ConferenceId("conference"), "Original", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val updated = Conference(ConferenceId("conference"), "Mapped position", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val repository = FakeConferenceRepository(initialConference = original, positionMappedConference = updated)
        val viewModel = viewModelWith(repository)

        viewModel.selectConference("conference")
        waitUntil { viewModel.state.value.selectedConference === original }

        viewModel.setPositionMappedCommittee("position", "target-committee")

        waitUntil { viewModel.state.value.selectedConference === updated }
        assertSame(updated, viewModel.state.value.selectedConference)
        assertNull(viewModel.state.value.boardActionError)
        assertEquals("conference", repository.positionMappedConferenceId)
        assertEquals("position", repository.positionMappedPositionId)
        assertEquals("target-committee", repository.positionMappedCommitteeId)
        viewModel.close()
    }

    @Test
    fun `notice manager actions update scoped state`() {
        val viewModel = viewModelWith(FakeConferenceRepository())

        viewModel.openNoticeManager()

        assertTrue(viewModel.state.value.noticeManagerOpen)
        assertNull(viewModel.state.value.noticeManagerDelegateId)

        viewModel.openDelegateNoticeManager(delegateId = "delegate")

        assertTrue(viewModel.state.value.noticeManagerOpen)
        assertEquals("delegate", viewModel.state.value.noticeManagerDelegateId)

        viewModel.closeNoticeManager()

        assertFalse(viewModel.state.value.noticeManagerOpen)
        assertNull(viewModel.state.value.noticeManagerDelegateId)
        viewModel.close()
    }

    @Test
    fun `resolve answer issue refreshes selected conference`() {
        val original = Conference(ConferenceId("conference"), "Original", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val resolved = Conference(ConferenceId("conference"), "Resolved", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val repository = FakeConferenceRepository(initialConference = original, resolvedConference = resolved)
        val viewModel = viewModelWith(repository)

        viewModel.selectConference("conference")
        waitUntil { viewModel.state.value.selectedConference === original }

        viewModel.resolveAnswerIssue(
            delegateId = "delegate",
            questionId = "question",
            issueKey = "Warning:Check",
            justification = "Reviewed",
        )

        waitUntil { viewModel.state.value.selectedConference === resolved }
        assertSame(resolved, viewModel.state.value.selectedConference)
        assertNull(viewModel.state.value.boardActionError)
        assertEquals("delegate", repository.resolvedDelegateId)
        assertEquals("question", repository.resolvedQuestionId)
        assertEquals("Warning:Check", repository.resolvedIssueKey)
        assertEquals("Reviewed", repository.resolvedJustification)
        viewModel.close()
    }

    @Test
    fun `resolve answer issue stores validation message as board error`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val repository = FakeConferenceRepository(initialConference = conference, resolveError = "Issue is no longer present.")
        val viewModel = viewModelWith(repository)

        viewModel.selectConference("conference")
        waitUntil { viewModel.state.value.selectedConference === conference }

        viewModel.resolveAnswerIssue(
            delegateId = "delegate",
            questionId = null,
            issueKey = "Warning:Check",
            justification = null,
        )

        waitUntil { viewModel.state.value.boardActionError == "Issue is no longer present." }
        assertSame(conference, viewModel.state.value.selectedConference)
        viewModel.close()
    }

    private fun viewModelWith(
        repository: ConferenceRepository,
        workbookUpdate: suspend (String, Path) -> UpdateResult = { _, _ -> UpdateResult(true, emptyList()) },
    ): MainViewModel = MainViewModel(
        repository,
        WorkbookUpdateRunner { conferenceId, path -> workbookUpdate(conferenceId, path) },
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    private fun waitUntil(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            Thread.sleep(20)
        }
        check(predicate()) { "Timed out waiting for ViewModel state." }
    }
}

private class FakeConferenceRepository(
    private val initialConference: Conference? = null,
    private val conferences: List<Conference> = listOfNotNull(initialConference),
    private val createdConferenceDeferred: CompletableDeferred<Conference>? = null,
    private val createConferenceStarted: CompletableDeferred<Unit>? = null,
    private val conferencesAfterCreate: List<Conference>? = null,
    private val resolvedConference: Conference? = initialConference,
    private val resolveError: String? = null,
    private val createdReservationConference: Conference? = initialConference,
    private val createdReservationConferenceDeferred: CompletableDeferred<Conference>? = null,
    private val createReservationStarted: CompletableDeferred<Unit>? = null,
    private val removeDelegateReservationError: String? = null,
    private val committeeModeConference: Conference? = initialConference,
    private val committeeModeResultDeferred: CompletableDeferred<Result<Conference>>? = null,
    private val committeeModeStarted: CompletableDeferred<Unit>? = null,
    private val positionMappedConference: Conference? = initialConference,
) : ConferenceRepository {
    private var conferenceCreated: Boolean = false
    var resolvedDelegateId: String? = null
        private set
    var resolvedQuestionId: String? = null
        private set
    var resolvedIssueKey: String? = null
        private set
    var resolvedJustification: String? = null
        private set
    var createdReservationConferenceId: String? = null
        private set
    var createdReservationName: String? = null
        private set
    var createdReservationHighSchoolCount: Int? = null
        private set
    var createdReservationUniversityCount: Int? = null
        private set
    var createdReservationDelegationId: String? = null
        private set
    var committeeModeConferenceId: String? = null
        private set
    var committeeModeCommitteeId: String? = null
        private set
    var committeeModeMode: CommitteeMode? = null
        private set
    var positionMappedConferenceId: String? = null
        private set
    var positionMappedPositionId: String? = null
        private set
    var positionMappedCommitteeId: String? = null
        private set

    override suspend fun listConferences(): List<Conference> =
        if (conferenceCreated) conferencesAfterCreate ?: conferences else conferences

    override suspend fun createConference(name: String): Conference {
        createConferenceStarted?.complete(Unit)
        val created = createdConferenceDeferred?.await() ?: unsupported()
        conferenceCreated = true
        return created
    }

    override suspend fun renameConference(conferenceId: String, newName: String): Conference = unsupported()

    override suspend fun deleteConference(conferenceId: String): Unit = unsupported()

    override suspend fun reorderConferences(conferenceIdsInOrder: List<String>): Unit = unsupported()

    override suspend fun setCommitteeSlotsPerPosition(
        conferenceId: String,
        committeeId: String,
        slotsPerPosition: Int,
    ): Conference = unsupported()

    override suspend fun setCommitteeHighSchoolStatus(
        conferenceId: String,
        committeeId: String,
        isHighSchoolCommittee: Boolean,
    ): Conference = unsupported()

    override suspend fun setCommitteeMode(
        conferenceId: String,
        committeeId: String,
        mode: CommitteeMode,
    ): Conference {
        committeeModeConferenceId = conferenceId
        committeeModeCommitteeId = committeeId
        committeeModeMode = mode
        committeeModeStarted?.complete(Unit)
        committeeModeResultDeferred?.let { return it.await().getOrThrow() }
        return requireNotNull(committeeModeConference)
    }

    override suspend fun setPositionMappedCommittee(
        conferenceId: String,
        positionId: String,
        mappedCommitteeId: String?,
    ): Conference {
        positionMappedConferenceId = conferenceId
        positionMappedPositionId = positionId
        positionMappedCommitteeId = mappedCommitteeId
        return requireNotNull(positionMappedConference)
    }

    override suspend fun assignDelegateToSlot(
        conferenceId: String,
        slotId: String,
        delegateId: String,
        moveExistingPending: Boolean,
    ): Conference = unsupported()

    override suspend fun clearPendingSlot(conferenceId: String, slotId: String): Conference = unsupported()

    override suspend fun deactivateDelegate(conferenceId: String, delegateId: String): Conference = unsupported()

    override suspend fun reactivateDelegate(conferenceId: String, delegateId: String): Conference = unsupported()

    override suspend fun createReservation(
        conferenceId: String,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: String?,
    ): Conference {
        createdReservationConferenceId = conferenceId
        createdReservationName = name
        createdReservationHighSchoolCount = highSchoolCount
        createdReservationUniversityCount = universityCount
        createdReservationDelegationId = delegationId
        createReservationStarted?.complete(Unit)
        createdReservationConferenceDeferred?.let { return it.await() }
        return requireNotNull(createdReservationConference)
    }

    override suspend fun updateReservation(
        conferenceId: String,
        reservationId: String,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: String?,
    ): Conference = unsupported()

    override suspend fun deleteReservation(conferenceId: String, reservationId: String): Conference = unsupported()

    override suspend fun assignDelegateReservation(
        conferenceId: String,
        delegateId: String,
        reservationId: String,
    ): Conference = unsupported()

    override suspend fun removeDelegateReservation(conferenceId: String, delegateId: String): Conference {
        removeDelegateReservationError?.let { throw IllegalArgumentException(it) }
        return requireNotNull(initialConference)
    }

    override suspend fun addDelegateComment(conferenceId: String, delegateId: String, body: String): Conference = unsupported()

    override suspend fun updateDelegateComment(conferenceId: String, commentId: String, body: String): Conference = unsupported()

    override suspend fun deleteDelegateComment(conferenceId: String, commentId: String): Conference = unsupported()

    override suspend fun updateQuestionConfiguration(
        conferenceId: String,
        configuration: QuestionConfiguration,
    ): Conference = unsupported()

    override suspend fun reorderQuestionConfigurations(
        conferenceId: String,
        questionIdsInDisplayOrder: List<String>,
    ): Conference = unsupported()

    override suspend fun resolveAnswerIssue(
        conferenceId: String,
        delegateId: String,
        questionId: String?,
        issueKey: String,
        justification: String?,
    ): Conference {
        resolveError?.let { throw IllegalArgumentException(it) }
        resolvedDelegateId = delegateId
        resolvedQuestionId = questionId
        resolvedIssueKey = issueKey
        resolvedJustification = justification
        return requireNotNull(resolvedConference)
    }

    override suspend fun updateAnswerIssueResolutionJustification(
        conferenceId: String,
        resolutionId: String,
        justification: String?,
    ): Conference = unsupported()

    override suspend fun unresolveAnswerIssue(conferenceId: String, resolutionId: String): Conference = unsupported()

    override suspend fun getConference(conferenceId: String): Conference? = conferences.firstOrNull { it.id.value == conferenceId }

    private fun unsupported(): Nothing = error("Not needed for this test.")
}
