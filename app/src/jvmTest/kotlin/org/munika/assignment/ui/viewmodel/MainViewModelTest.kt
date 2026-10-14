package org.munika.assignment.ui.viewmodel

import java.nio.file.Files
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
import org.munika.assignment.LastSelectedConferenceStore
import org.munika.assignment.domain.model.AnswerIssueResolutionId
import org.munika.assignment.domain.model.CommitteeId
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.DelegateCommentId
import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.Delegation
import org.munika.assignment.domain.model.DelegationId
import org.munika.assignment.domain.model.MymunUpdateFilenameCandidate
import org.munika.assignment.domain.model.MymunUpdateFilenameSignatureId
import org.munika.assignment.domain.model.PaymentTier
import org.munika.assignment.domain.model.PositionId
import org.munika.assignment.domain.model.QuestionConfiguration
import org.munika.assignment.domain.model.QuestionId
import org.munika.assignment.domain.model.ReservationId
import org.munika.assignment.domain.model.SlotId
import org.munika.assignment.domain.repository.ConferenceRepository
import org.munika.assignment.domain.repository.MissingPreference
import org.munika.assignment.domain.repository.PreferenceCompletionSelection
import org.munika.assignment.domain.repository.PreferenceCorrectionSelection
import org.munika.assignment.domain.repository.PreferenceValueOrigin
import org.munika.assignment.domain.repository.UpdateIssue
import org.munika.assignment.domain.repository.UpdateIssueSeverity
import org.munika.assignment.domain.repository.UpdateResult
import org.munika.assignment.domain.repository.UpdateSummary
import org.munika.assignment.export.MarkdownSafetyExportFailure
import org.munika.assignment.export.MarkdownSafetyExportResult
import org.munika.assignment.export.MarkdownSafetyExportRunner
import org.munika.assignment.export.MarkdownSafetyExportTrigger

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
    fun `startup restores saved selected conference`() {
        val conferenceA = Conference(ConferenceId("conference-a"), "Conference A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val conferenceB = Conference(ConferenceId("conference-b"), "Conference B", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val store = tempLastSelectedConferenceStore()
        store.save("conference-b")

        val viewModel = viewModelWith(
            FakeConferenceRepository(initialConference = conferenceA, conferences = listOf(conferenceA, conferenceB)),
            lastSelectedConferenceStore = store,
        )

        waitUntil { viewModel.state.value.selectedConference === conferenceB }
        assertEquals("conference-b", viewModel.state.value.selectedConferenceId)
        assertEquals("conference-b", store.load())
        viewModel.close()
    }

    @Test
    fun `startup falls back to first conference when saved selected conference is missing`() {
        val conferenceA = Conference(ConferenceId("conference-a"), "Conference A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val conferenceB = Conference(ConferenceId("conference-b"), "Conference B", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val store = tempLastSelectedConferenceStore()
        store.save("missing-conference")

        val viewModel = viewModelWith(
            FakeConferenceRepository(initialConference = conferenceA, conferences = listOf(conferenceA, conferenceB)),
            lastSelectedConferenceStore = store,
        )

        waitUntil { viewModel.state.value.selectedConference === conferenceA }
        assertEquals("conference-a", viewModel.state.value.selectedConferenceId)
        assertEquals("conference-a", store.load())
        viewModel.close()
    }

    @Test
    fun `create conference failure surfaces as conference action error instead of crashing silently`() {
        val conferenceA = Conference(ConferenceId("conference-a"), "Conference A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val repository = FakeConferenceRepository(
            initialConference = conferenceA,
            createConferenceThrows = IllegalStateException("Database is locked."),
        )
        val viewModel = viewModelWith(repository)
        waitUntil { viewModel.state.value.selectedConference === conferenceA }

        assertTrue(viewModel.createConference("New Conference"))

        waitUntil { viewModel.state.value.conferenceActionError != null }
        assertTrue("Database is locked." in requireNotNull(viewModel.state.value.conferenceActionError))
        viewModel.close()
    }

    @Test
    fun `selecting another conference clears conference scoped UI state`() {
        val conferenceA = Conference(ConferenceId("conference-a"), "Conference A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val conferenceB = Conference(ConferenceId("conference-b"), "Conference B", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val store = tempLastSelectedConferenceStore()
        val viewModel = viewModelWith(
            FakeConferenceRepository(initialConference = conferenceA, conferences = listOf(conferenceA, conferenceB)),
            lastSelectedConferenceStore = store,
        )
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
        assertEquals("conference-b", store.load())
        viewModel.close()
    }

    @Test
    fun `creating a conference persists it as the last selected conference`() {
        val conferenceA = Conference(ConferenceId("conference-a"), "Conference A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val created = Conference(ConferenceId("conference-created"), "Created", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val store = tempLastSelectedConferenceStore()
        val repository = FakeConferenceRepository(
            initialConference = conferenceA,
            conferences = listOf(conferenceA),
            createdConferenceDeferred = CompletableDeferred(created),
            conferencesAfterCreate = listOf(conferenceA, created),
        )
        val viewModel = viewModelWith(repository, lastSelectedConferenceStore = store)
        waitUntil { viewModel.state.value.selectedConference === conferenceA }

        assertTrue(viewModel.createConference("Created"))

        waitUntil { viewModel.state.value.selectedConference === created }
        assertEquals("conference-created", store.load())
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
    fun `workbook success emits non-technical completion notice`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val viewModel = viewModelWith(FakeConferenceRepository(initialConference = conference)) { _, _, _ ->
            UpdateResult(true, emptyList(), UpdateSummary(delegatesCreated = 4))
        }
        waitUntil { viewModel.state.value.selectedConference === conference }

        viewModel.updateFromWorkbook(Path.of("conference.xlsx"))

        waitUntil { viewModel.state.value.workbookUpdateNotice != null }
        assertEquals("Update complete.", viewModel.state.value.workbookUpdateNotice?.message)
        assertFalse(viewModel.state.value.loading)
        viewModel.close()
    }

    @Test
    fun `conference export emits transfer notice`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val transfer = FakeConferenceTransferRunner()
        val viewModel = viewModelWith(FakeConferenceRepository(initialConference = conference), conferenceTransfer = transfer)
        waitUntil { viewModel.state.value.selectedConference === conference }

        viewModel.exportConference("conference", Path.of("kamun.munassign"))

        waitUntil { viewModel.state.value.transferNotice != null }
        assertEquals("conference", transfer.exportedConferenceId)
        assertEquals(Path.of("kamun.munassign"), transfer.exportDestinationPath)
        assertEquals("Conference exported.", viewModel.state.value.transferNotice?.message)
        assertFalse(viewModel.state.value.loading)
        viewModel.close()
    }

    @Test
    fun `startup loading remains visible until selected conference and safety export finish`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val conferenceLoadStarted = CompletableDeferred<Unit>()
        val loadedConference = CompletableDeferred<Conference?>()
        val exportStarted = CompletableDeferred<Unit>()
        val exportResult = CompletableDeferred<MarkdownSafetyExportResult>()
        val markdownExport = FakeMarkdownSafetyExportRunner(
            exportStarted = exportStarted,
            exportResultDeferred = exportResult,
        )
        val viewModel = viewModelWith(
            FakeConferenceRepository(
                initialConference = conference,
                getConferenceStarted = conferenceLoadStarted,
                getConferenceDeferred = loadedConference,
            ),
            markdownSafetyExport = markdownExport,
        )

        runBlocking { conferenceLoadStarted.await() }

        assertTrue(viewModel.state.value.startupLoading)
        assertEquals("conference", viewModel.state.value.selectedConferenceId)
        assertNull(viewModel.state.value.selectedConference)

        loadedConference.complete(conference)
        runBlocking { exportStarted.await() }

        assertTrue(viewModel.state.value.startupLoading)
        assertSame(conference, viewModel.state.value.selectedConference)

        exportResult.complete(MarkdownSafetyExportResult(Path.of("markdown-exports"), emptyList(), emptyList()))

        waitUntil { !viewModel.state.value.startupLoading }
        assertFalse(viewModel.state.value.loading)
        viewModel.close()
    }

    @Test
    fun `startup without conferences completes into empty conference state`() {
        val store = tempLastSelectedConferenceStore()
        store.save("missing-conference")
        val viewModel = viewModelWith(FakeConferenceRepository(), lastSelectedConferenceStore = store)

        waitUntil { !viewModel.state.value.startupLoading }

        assertTrue(viewModel.state.value.conferences.isEmpty())
        assertNull(viewModel.state.value.selectedConferenceId)
        assertNull(viewModel.state.value.selectedConference)
        assertNull(store.load())
        assertFalse(viewModel.state.value.loading)
        viewModel.close()
    }

    @Test
    fun `set payment tiers refreshes the selected conference`() {
        val original = Conference(ConferenceId("conference"), "Original", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val updated = Conference(ConferenceId("conference"), "Updated", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val repository = FakeConferenceRepository(initialConference = original, paymentTierConference = updated)
        val viewModel = viewModelWith(repository)
        waitUntil { viewModel.state.value.selectedConference === original }

        viewModel.setCommitteePaymentTier("committee", PaymentTier.Reduced)
        waitUntil { viewModel.state.value.selectedConference === updated }
        viewModel.setDelegatePaymentTier("delegate", PaymentTier.Full)
        waitUntil { repository.delegatePaymentTier == PaymentTier.Full }

        assertEquals(PaymentTier.Reduced, repository.committeePaymentTier)
        assertEquals("committee", repository.paymentTierCommitteeId)
        assertEquals("delegate", repository.paymentTierDelegateId)
        assertNull(viewModel.state.value.boardActionError)
        viewModel.close()
    }

    @Test
    fun `mismatching workbook waits for confirmation and stages signature`() {
        val trusted = MymunUpdateFilenameCandidate.fromFilename("KAMUN-Spring Summit.xlsx").withIdentity(
            MymunUpdateFilenameSignatureId("trusted"),
            Instant.EPOCH,
        )
        val conference = Conference(
            ConferenceId("conference"),
            "KAMUN",
            mymunUpdateFilenameSignatures = mutableListOf(trusted),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        var staged: MymunUpdateFilenameCandidate? = null
        var updateStarted = false
        val viewModel = viewModelWith(FakeConferenceRepository(initialConference = conference)) { _, _, candidate ->
            updateStarted = true
            staged = candidate
            UpdateResult(true, emptyList())
        }
        waitUntil { viewModel.state.value.selectedConference === conference }

        viewModel.updateFromWorkbook(Path.of("KAMUN-Autumn Summit.xlsx"))

        assertFalse(updateStarted)
        val pending = requireNotNull(viewModel.state.value.pendingWorkbookUpdate)
        assertEquals(listOf("Spring Summit"), pending.missingParts)
        assertEquals(listOf("Autumn Summit"), pending.unexpectedParts)

        viewModel.confirmPendingWorkbookUpdate()

        waitUntil { updateStarted }
        assertEquals(setOf("kamun", "autumn summit"), staged?.normalizedParts)
        viewModel.close()
    }

    @Test
    fun `empty workbook filename always waits and is never staged for trust`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        var staged: MymunUpdateFilenameCandidate? = MymunUpdateFilenameCandidate.fromFilename("sentinel.xlsx")
        val viewModel = viewModelWith(FakeConferenceRepository(initialConference = conference)) { _, _, candidate ->
            staged = candidate
            UpdateResult(true, emptyList())
        }
        waitUntil { viewModel.state.value.selectedConference === conference }

        viewModel.updateFromWorkbook(Path.of("mymun-2026-Applications-Export.xlsx"))

        assertTrue(viewModel.state.value.pendingWorkbookUpdate?.emptyCandidate == true)
        viewModel.confirmPendingWorkbookUpdate()
        waitUntil { viewModel.state.value.workbookUpdateNotice != null }
        assertNull(staged)
        viewModel.close()
    }

    @Test
    fun `trusted workbook match proceeds directly without staging another signature`() {
        val trusted = MymunUpdateFilenameCandidate.fromFilename("KAMUN-Spring Summit.xlsx").withIdentity(
            MymunUpdateFilenameSignatureId("trusted"),
            Instant.EPOCH,
        )
        val conference = Conference(
            ConferenceId("conference"),
            "KAMUN",
            mymunUpdateFilenameSignatures = mutableListOf(trusted),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        var updateStarted = false
        var staged: MymunUpdateFilenameCandidate? = MymunUpdateFilenameCandidate.fromFilename("sentinel.xlsx")
        val viewModel = viewModelWith(FakeConferenceRepository(initialConference = conference)) { _, _, candidate ->
            updateStarted = true
            staged = candidate
            UpdateResult(true, emptyList())
        }
        waitUntil { viewModel.state.value.selectedConference === conference }

        viewModel.updateFromWorkbook(Path.of("spring summit-kamun.xlsx"))

        waitUntil { updateStarted }
        assertNull(viewModel.state.value.pendingWorkbookUpdate)
        assertNull(staged)
        viewModel.close()
    }

    @Test
    fun `selecting another conference cancels pending workbook mismatch`() {
        val trusted = MymunUpdateFilenameCandidate.fromFilename("KAMUN-Spring Summit.xlsx").withIdentity(
            MymunUpdateFilenameSignatureId("trusted"),
            Instant.EPOCH,
        )
        val conferenceA = Conference(
            ConferenceId("conference-a"),
            "KAMUN",
            mymunUpdateFilenameSignatures = mutableListOf(trusted),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val conferenceB = Conference(
            ConferenceId("conference-b"),
            "Other",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val viewModel = viewModelWith(
            FakeConferenceRepository(
                initialConference = conferenceA,
                conferences = listOf(conferenceA, conferenceB),
            ),
        )
        waitUntil { viewModel.state.value.selectedConference === conferenceA }
        viewModel.updateFromWorkbook(Path.of("KAMUN-Autumn Summit.xlsx"))
        assertTrue(viewModel.state.value.pendingWorkbookUpdate != null)

        viewModel.selectConference("conference-b")

        assertNull(viewModel.state.value.pendingWorkbookUpdate)
        viewModel.close()
    }

    @Test
    fun `delegation quick reservation editor opens with a derived draft and can be cancelled`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        conference.delegations += Delegation(
            DelegationId("delegation"),
            conference,
            "School Team",
            null,
            5,
            null,
            isHighSchoolDelegation = true,
        )
        val viewModel = viewModelWith(FakeConferenceRepository(initialConference = conference))
        waitUntil { viewModel.state.value.selectedConference === conference }

        viewModel.openDelegationReservationEditor("delegation")

        val draft = requireNotNull(viewModel.state.value.delegationReservationDraft)
        assertEquals("School Team", draft.name)
        assertEquals(5, draft.highSchoolCount)
        assertEquals(0, draft.universityCount)
        assertEquals("delegation", draft.delegationId)

        viewModel.closeDelegationReservationEditor()

        assertNull(viewModel.state.value.delegationReservationDraft)
        assertNull(viewModel.state.value.delegationReservationActionError)
        viewModel.close()
    }

    @Test
    fun `successful delegation quick reservation returns to the delegation overview`() {
        val original = Conference(ConferenceId("conference"), "Original", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        original.delegations += Delegation(DelegationId("delegation"), original, "Alpha", null, 3, null)
        val reserved = Conference(ConferenceId("conference"), "Reserved", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val createStarted = CompletableDeferred<Unit>()
        val createResult = CompletableDeferred<Conference>()
        val repository = FakeConferenceRepository(
            initialConference = original,
            createdReservationConferenceDeferred = createResult,
            createReservationStarted = createStarted,
        )
        val viewModel = viewModelWith(repository)
        waitUntil { viewModel.state.value.selectedConference === original }
        viewModel.openDelegationOverview()
        viewModel.openDelegationReservationEditor("delegation")

        viewModel.createDelegationReservation(
            conferenceId = "conference",
            name = "Alpha",
            highSchoolCount = 0,
            universityCount = 3,
            delegationId = "delegation",
        )

        runBlocking { createStarted.await() }
        assertTrue(viewModel.state.value.loading)
        createResult.complete(reserved)
        waitUntil { viewModel.state.value.selectedConference === reserved }
        assertNull(viewModel.state.value.delegationReservationDraft)
        assertNull(viewModel.state.value.delegationReservationActionError)
        assertTrue(viewModel.state.value.delegationOverviewOpen)
        assertFalse(viewModel.state.value.reservationManagerOpen)
        assertEquals("Alpha", repository.createdReservationName)
        assertEquals(3, repository.createdReservationUniversityCount)
        viewModel.close()
    }

    @Test
    fun `failed delegation quick reservation keeps the editor open with its error`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        conference.delegations += Delegation(DelegationId("delegation"), conference, "Alpha", null, 3, null)
        val repository = FakeConferenceRepository(
            initialConference = conference,
            createReservationThrows = IllegalArgumentException("Reservation cannot be created."),
        )
        val viewModel = viewModelWith(repository)
        waitUntil { viewModel.state.value.selectedConference === conference }
        viewModel.openDelegationReservationEditor("delegation")

        viewModel.createDelegationReservation("conference", "Alpha", 0, 3, "delegation")

        waitUntil { viewModel.state.value.delegationReservationActionError != null }
        assertEquals("Reservation cannot be created.", viewModel.state.value.delegationReservationActionError)
        assertEquals("delegation", viewModel.state.value.delegationReservationDraft?.delegationId)
        assertSame(conference, viewModel.state.value.selectedConference)
        viewModel.close()
    }

    @Test
    fun `closing delegation overview persists review acknowledgement`() {
        val original = Conference(ConferenceId("conference"), "Original", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        original.delegations += Delegation(
            DelegationId("delegation"),
            original,
            "Alpha",
            null,
            3,
            null,
            needsReview = true,
        )
        val reviewed = Conference(ConferenceId("conference"), "Reviewed", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        reviewed.delegations += Delegation(DelegationId("delegation"), reviewed, "Alpha", null, 3, null)
        val repository = FakeConferenceRepository(initialConference = original, reviewedDelegationsConference = reviewed)
        val viewModel = viewModelWith(repository)
        waitUntil { viewModel.state.value.selectedConference === original }
        viewModel.openDelegationOverview()

        viewModel.closeDelegationOverview()

        assertFalse(viewModel.state.value.delegationOverviewOpen)
        waitUntil { viewModel.state.value.selectedConference === reviewed }
        assertFalse(requireNotNull(viewModel.state.value.selectedConference).delegations.single().needsReview)
        viewModel.close()
    }

    @Test
    fun `failed delegation review acknowledgement keeps markers and surfaces an error`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        conference.delegations += Delegation(
            DelegationId("delegation"),
            conference,
            "Alpha",
            null,
            3,
            null,
            needsReview = true,
        )
        val repository = FakeConferenceRepository(
            initialConference = conference,
            reviewDelegationsThrows = IllegalStateException("Database is locked."),
        )
        val viewModel = viewModelWith(repository)
        waitUntil { viewModel.state.value.selectedConference === conference }
        viewModel.openDelegationOverview()

        viewModel.closeDelegationOverview()

        waitUntil { viewModel.state.value.boardActionError != null }
        assertFalse(viewModel.state.value.delegationOverviewOpen)
        assertTrue(requireNotNull(viewModel.state.value.selectedConference).delegations.single().needsReview)
        assertTrue("Database is locked." in requireNotNull(viewModel.state.value.boardActionError))
        viewModel.close()
    }

    @Test
    fun `delegation school toggle refreshes the selected conference`() {
        val original = Conference(ConferenceId("conference"), "Original", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val updated = Conference(ConferenceId("conference"), "Updated", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val repository = FakeConferenceRepository(initialConference = original, delegationSchoolConference = updated)
        val viewModel = viewModelWith(repository)
        waitUntil { viewModel.state.value.selectedConference === original }

        viewModel.setDelegationHighSchoolStatus("delegation", isHighSchoolDelegation = true)

        waitUntil { viewModel.state.value.selectedConference === updated }
        assertNull(viewModel.state.value.boardActionError)
        assertEquals("delegation", repository.delegationSchoolDelegationId)
        assertEquals(true, repository.delegationSchoolIsHighSchool)
        viewModel.close()
    }

    @Test
    fun `startup markdown export writes all full conferences after initial load`() {
        val conferenceA = Conference(ConferenceId("conference-a"), "Conference A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val conferenceB = Conference(ConferenceId("conference-b"), "Conference B", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val markdownExport = FakeMarkdownSafetyExportRunner()
        val viewModel = viewModelWith(
            FakeConferenceRepository(initialConference = conferenceA, conferences = listOf(conferenceA, conferenceB)),
            markdownSafetyExport = markdownExport,
        )

        waitUntil { markdownExport.calls.any { it.trigger == MarkdownSafetyExportTrigger.Open } }

        val startupCall = markdownExport.calls.first { it.trigger == MarkdownSafetyExportTrigger.Open }
        assertEquals(listOf("conference-a", "conference-b"), startupCall.conferenceIds)
        assertNull(viewModel.state.value.transferNotice)
        viewModel.close()
    }

    @Test
    fun `close markdown export writes all currently known conferences before cancelling scope`() {
        val conferenceA = Conference(ConferenceId("conference-a"), "Conference A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val conferenceB = Conference(ConferenceId("conference-b"), "Conference B", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val markdownExport = FakeMarkdownSafetyExportRunner()
        val viewModel = viewModelWith(
            FakeConferenceRepository(initialConference = conferenceA, conferences = listOf(conferenceA, conferenceB)),
            markdownSafetyExport = markdownExport,
        )
        waitUntil { markdownExport.calls.any { it.trigger == MarkdownSafetyExportTrigger.Open } }

        viewModel.close()

        val closeCall = markdownExport.calls.first { it.trigger == MarkdownSafetyExportTrigger.Close }
        assertEquals(listOf("conference-a", "conference-b"), closeCall.conferenceIds)
    }

    @Test
    fun `startup markdown export failure emits concise notice without blocking load`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val markdownExport = FakeMarkdownSafetyExportRunner(
            failures = listOf(MarkdownSafetyExportFailure("KAMUN", "Disk is full.")),
        )
        val viewModel = viewModelWith(
            FakeConferenceRepository(initialConference = conference),
            markdownSafetyExport = markdownExport,
        )

        waitUntil { viewModel.state.value.transferNotice != null }

        assertEquals("Textual safety export failed for 1 conference.", viewModel.state.value.transferNotice?.message)
        assertFalse(viewModel.state.value.startupLoading)
        assertFalse(viewModel.state.value.loading)
        viewModel.close()
    }

    @Test
    fun `import as new selects imported conference and emits transfer notice`() {
        val existing = Conference(ConferenceId("existing"), "Existing", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val imported = Conference(ConferenceId("imported"), "Imported", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val repository = FakeConferenceRepository(initialConference = existing, conferences = listOf(existing, imported))
        val transfer = FakeConferenceTransferRunner(importedId = "imported")
        val store = tempLastSelectedConferenceStore()
        val viewModel = viewModelWith(repository, conferenceTransfer = transfer, lastSelectedConferenceStore = store)
        waitUntil { viewModel.state.value.selectedConference === existing }

        viewModel.importConferenceAsNew(Path.of("import.munassign"))

        waitUntil { viewModel.state.value.transferNotice != null }
        assertEquals(Path.of("import.munassign"), transfer.importSourcePath)
        assertEquals("imported", viewModel.state.value.selectedConferenceId)
        assertSame(imported, viewModel.state.value.selectedConference)
        assertEquals("imported", store.load())
        assertEquals("Conference imported.", viewModel.state.value.transferNotice?.message)
        assertFalse(viewModel.state.value.loading)
        viewModel.close()
    }

    @Test
    fun `replace selected keeps selected conference and emits transfer notice`() {
        val original = Conference(ConferenceId("conference"), "Local", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val replaced = Conference(ConferenceId("conference"), "Local", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH.plusSeconds(1))
        val repository = FakeConferenceRepository(initialConference = original, conferences = listOf(replaced))
        val transfer = FakeConferenceTransferRunner(replacedId = "conference")
        val store = tempLastSelectedConferenceStore()
        val viewModel = viewModelWith(repository, conferenceTransfer = transfer, lastSelectedConferenceStore = store)
        waitUntil { viewModel.state.value.selectedConference === replaced }

        viewModel.prepareReplaceConference("conference", Path.of("replacement.munassign"))

        val pending = viewModel.state.value.pendingConferenceReplace
        assertEquals("conference", pending?.conferenceId)
        assertEquals(Path.of("replacement.munassign"), pending?.sourcePath)
        assertNull(transfer.replacedConferenceId)

        viewModel.confirmPendingConferenceReplace()

        waitUntil { viewModel.state.value.transferNotice != null }
        assertEquals("conference", transfer.replacedConferenceId)
        assertEquals(Path.of("replacement.munassign"), transfer.replaceSourcePath)
        assertEquals("conference", viewModel.state.value.selectedConferenceId)
        assertSame(replaced, viewModel.state.value.selectedConference)
        assertEquals("conference", store.load())
        assertEquals("Conference replaced.", viewModel.state.value.transferNotice?.message)
        assertFalse(viewModel.state.value.loading)
        viewModel.close()
    }

    @Test
    fun `transfer failure emits concise transfer notice`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val transfer = FakeConferenceTransferRunner(exportError = IllegalStateException("Disk is full."))
        val viewModel = viewModelWith(FakeConferenceRepository(initialConference = conference), conferenceTransfer = transfer)
        waitUntil { viewModel.state.value.selectedConference === conference }

        viewModel.exportConference("conference", Path.of("kamun.munassign"))

        waitUntil { viewModel.state.value.transferNotice != null }
        assertEquals("Export failed: Disk is full.", viewModel.state.value.transferNotice?.message)
        assertFalse(viewModel.state.value.loading)
        viewModel.close()
    }

    @Test
    fun `workbook success with warnings emits warning count notice`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val viewModel = viewModelWith(FakeConferenceRepository(initialConference = conference)) { _, _, _ ->
            UpdateResult(
                success = true,
                issues = listOf(
                    UpdateIssue(UpdateIssueSeverity.Warning, "Unmapped answer"),
                    UpdateIssue(UpdateIssueSeverity.Warning, "Short explanation"),
                ),
                summary = UpdateSummary(warningCount = 2),
            )
        }
        waitUntil { viewModel.state.value.selectedConference === conference }

        viewModel.updateFromWorkbook(Path.of("conference.xlsx"))

        waitUntil { viewModel.state.value.workbookUpdateNotice != null }
        assertEquals("Update complete with 2 warnings.", viewModel.state.value.workbookUpdateNotice?.message)
        assertFalse(viewModel.state.value.loading)
        viewModel.close()
    }

    @Test
    fun `workbook failure emits concise failure notice`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val viewModel = viewModelWith(FakeConferenceRepository(initialConference = conference)) { _, _, _ ->
            UpdateResult(
                success = false,
                issues = listOf(UpdateIssue(UpdateIssueSeverity.Error, "Countries sheet is missing.")),
                summary = UpdateSummary(blockingErrorCount = 1),
            )
        }
        waitUntil { viewModel.state.value.selectedConference === conference }

        viewModel.updateFromWorkbook(Path.of("conference.xlsx"))

        waitUntil { viewModel.state.value.workbookUpdateNotice != null }
        assertEquals("Update failed: Countries sheet is missing.", viewModel.state.value.workbookUpdateNotice?.message)
        assertNull(viewModel.state.value.pendingPreferenceCompletion)
        assertFalse(viewModel.state.value.loading)
        viewModel.close()
    }

    @Test
    fun `workbook exception emits concise failure notice`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val viewModel = viewModelWith(FakeConferenceRepository(initialConference = conference)) { _, _, _ ->
            throw IllegalStateException("File could not be read.")
        }
        waitUntil { viewModel.state.value.selectedConference === conference }

        viewModel.updateFromWorkbook(Path.of("conference.xlsx"))

        waitUntil { viewModel.state.value.workbookUpdateNotice != null }
        assertEquals("Update failed: File could not be read.", viewModel.state.value.workbookUpdateNotice?.message)
        assertFalse(viewModel.state.value.loading)
        viewModel.close()
    }

    @Test
    fun `rejected workbook selection emits notice without changing loading state`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val viewModel = viewModelWith(FakeConferenceRepository(initialConference = conference))
        waitUntil { viewModel.state.value.selectedConference === conference }

        viewModel.rejectWorkbookUpdate("Drop only one Excel file.")

        assertEquals("Drop only one Excel file.", viewModel.state.value.workbookUpdateNotice?.message)
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
        val viewModel = viewModelWith(repository) { _, _, _ ->
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
        assertNull(viewModel.state.value.workbookUpdateNotice)
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
        val viewModel = viewModelWith(repository) { _, _, _ ->
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
        assertNull(viewModel.state.value.workbookUpdateNotice)
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

    @Test
    fun `resolve answer issue stores a non-validation failure as board error instead of crashing silently`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val repository = FakeConferenceRepository(initialConference = conference, resolveThrows = IllegalStateException("Database is locked."))
        val viewModel = viewModelWith(repository)

        viewModel.selectConference("conference")
        waitUntil { viewModel.state.value.selectedConference === conference }

        viewModel.resolveAnswerIssue(
            delegateId = "delegate",
            questionId = null,
            issueKey = "Warning:Check",
            justification = null,
        )

        waitUntil { viewModel.state.value.boardActionError != null }
        assertTrue("Issue could not be resolved" in requireNotNull(viewModel.state.value.boardActionError))
        assertTrue("Database is locked." in requireNotNull(viewModel.state.value.boardActionError))
        viewModel.close()
    }

    @Test
    fun `set position pending assignments refreshes selected conference`() {
        val original = Conference(ConferenceId("conference"), "Original", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val updated = Conference(ConferenceId("conference"), "Selected delegates", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val repository = FakeConferenceRepository(initialConference = original, positionSelectionConference = updated)
        val viewModel = viewModelWith(repository)
        waitUntil { viewModel.state.value.selectedConference === original }

        viewModel.setPositionPendingAssignments("position", listOf("delegate-a", "delegate-b"))

        waitUntil { viewModel.state.value.selectedConference === updated }
        assertEquals("position", repository.positionSelectionPositionId)
        assertEquals(listOf("delegate-a", "delegate-b"), repository.positionSelectionDelegateIds)
        assertNull(viewModel.state.value.boardActionError)
        viewModel.close()
    }

    @Test
    fun `successful workbook update opens missing preference completion and save closes it`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val repository = FakeConferenceRepository(initialConference = conference)
        val viewModel = viewModelWith(repository) { _, _, _ ->
            UpdateResult(
                success = true,
                issues = emptyList(),
                preferenceRanks = listOf(1, 2, 3),
                missingPreferences = listOf(MissingPreference(DelegateId("delegate"), 2)),
            )
        }
        waitUntil { viewModel.state.value.selectedConference === conference }

        viewModel.updateFromWorkbook(Path.of("conference.xlsx"))

        waitUntil { viewModel.state.value.pendingPreferenceCompletion != null }
        assertEquals(listOf(1, 2, 3), viewModel.state.value.pendingPreferenceCompletion?.preferenceRanks)
        assertEquals(mapOf("delegate" to setOf(2)), viewModel.state.value.pendingPreferenceCompletion?.missingRanksByDelegateId)

        val correction = PreferenceCorrectionSelection(
            DelegateId("delegate"),
            2,
            PositionId("position"),
            PreferenceValueOrigin.Organizer,
        )
        viewModel.savePreferenceCorrections(listOf(correction))

        waitUntil { viewModel.state.value.pendingPreferenceCompletion == null }
        assertEquals(listOf(correction), repository.savedPreferenceCorrections)
        viewModel.close()
    }

    @Test
    fun `cancelling missing preference completion records nothing`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val repository = FakeConferenceRepository(initialConference = conference)
        val viewModel = viewModelWith(repository) { _, _, _ ->
            UpdateResult(
                success = true,
                issues = emptyList(),
                preferenceRanks = listOf(1),
                missingPreferences = listOf(MissingPreference(DelegateId("delegate"), 1)),
            )
        }
        waitUntil { viewModel.state.value.selectedConference === conference }
        viewModel.updateFromWorkbook(Path.of("conference.xlsx"))
        waitUntil { viewModel.state.value.pendingPreferenceCompletion != null }

        viewModel.cancelPreferenceCompletion()

        assertNull(viewModel.state.value.pendingPreferenceCompletion)
        assertTrue(repository.savedPreferenceCompletions.isEmpty())
        viewModel.close()
    }

    @Test
    fun `startup safety export reports unavailable synchronized conferences without blocking local exports`() {
        val local = Conference(ConferenceId("local"), "Local", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val unavailable = ConferenceListItem(
            id = "shared",
            name = "Shared",
            displayOrder = 1,
            storageKind = ConferenceListStorageKind.Synchronized,
            availability = ConferenceListAvailability.Missing,
            path = "missing.sync.munassign",
        )
        val markdownExport = FakeMarkdownSafetyExportRunner()
        val viewModel = viewModelWith(
            repository = FakeConferenceRepository(initialConference = local),
            workspace = FakeConferenceWorkspaceRunner(
                entries = listOf(
                    ConferenceListItem(
                        id = local.id.value,
                        name = local.name,
                        displayOrder = 0,
                        storageKind = ConferenceListStorageKind.Local,
                        availability = ConferenceListAvailability.Available,
                    ),
                    unavailable,
                ),
            ),
            markdownSafetyExport = markdownExport,
        )

        waitUntil { viewModel.state.value.transferNotice != null }

        assertEquals(listOf("local"), markdownExport.calls.first().conferenceIds)
        assertEquals(
            "Skipped 1 unavailable synchronized conference.",
            viewModel.state.value.transferNotice?.message,
        )
        assertFalse(viewModel.state.value.startupLoading)
        viewModel.close()
    }

    @Test
    fun `synchronized import is inspected before offering connect or detached import`() {
        val shared = Conference(ConferenceId("shared"), "Shared", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val repository = FakeConferenceRepository(initialConference = shared)
        val workspace = FakeConferenceWorkspaceRunner(
            entries = listOf(shared.synchronizedListItem()),
            fileInspection = ConferenceFileInspection(ConferenceFileKind.SynchronizedConference),
            connectedConference = shared,
        )
        val transfer = FakeConferenceTransferRunner()
        val viewModel = viewModelWith(repository, conferenceTransfer = transfer, workspace = workspace)
        waitUntil { viewModel.state.value.selectedConference === shared }
        val sourcePath = Path.of("shared.sync.munassign")

        viewModel.importConferenceAsNew(sourcePath)

        assertEquals(sourcePath, viewModel.state.value.pendingSynchronizedImportPath)
        assertNull(transfer.importSourcePath)
        viewModel.connectPendingSynchronizedImport()
        waitUntil { workspace.connectedPath == sourcePath }
        assertNull(viewModel.state.value.pendingSynchronizedImportPath)
        viewModel.close()
    }

    @Test
    fun `manual reload can select and refresh any synchronized catalog entry`() {
        val local = Conference(ConferenceId("local"), "Local", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val shared = Conference(ConferenceId("shared"), "Shared", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val repository = FakeConferenceRepository(initialConference = local, conferences = listOf(local, shared))
        val workspace = FakeConferenceWorkspaceRunner(
            entries = listOf(
                ConferenceListItem(
                    id = local.id.value,
                    name = local.name,
                    displayOrder = 0,
                    storageKind = ConferenceListStorageKind.Local,
                    availability = ConferenceListAvailability.Available,
                ),
                shared.synchronizedListItem(displayOrder = 1),
            ),
            reloadedConference = shared,
        )
        val viewModel = viewModelWith(repository, workspace = workspace)
        waitUntil { viewModel.state.value.selectedConference === local }

        viewModel.reloadSynchronizedConference(shared.id.value)

        waitUntil { viewModel.state.value.selectedConference === shared }
        assertEquals(shared.id.value, workspace.reloadedConferenceId)
        assertEquals(shared.id.value, viewModel.state.value.selectedConferenceId)
        viewModel.close()
    }

    @Test
    fun `delayed synchronized reload does not replace a newer conference selection`() {
        val local = Conference(ConferenceId("local"), "Local", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val shared = Conference(ConferenceId("shared"), "Shared", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val other = Conference(ConferenceId("other"), "Other", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val reloadStarted = CompletableDeferred<Unit>()
        val reloadResult = CompletableDeferred<Conference?>()
        val repository = FakeConferenceRepository(initialConference = local, conferences = listOf(local, shared, other))
        val entries = listOf(
            ConferenceListItem(
                id = local.id.value,
                name = local.name,
                displayOrder = 0,
                storageKind = ConferenceListStorageKind.Local,
                availability = ConferenceListAvailability.Available,
            ),
            shared.synchronizedListItem(displayOrder = 1),
            ConferenceListItem(
                id = other.id.value,
                name = other.name,
                displayOrder = 2,
                storageKind = ConferenceListStorageKind.Local,
                availability = ConferenceListAvailability.Available,
            ),
        )
        val workspace = FakeConferenceWorkspaceRunner(
            entries = entries,
            reloadStarted = reloadStarted,
            reloadResult = reloadResult,
        )
        val viewModel = viewModelWith(repository, workspace = workspace)
        waitUntil { viewModel.state.value.selectedConference === local }

        viewModel.reloadSynchronizedConference(shared.id.value)
        runBlocking { reloadStarted.await() }
        viewModel.selectConference(other.id.value)
        waitUntil { viewModel.state.value.selectedConference === other }
        reloadResult.complete(shared)

        waitUntil { workspace.reloadCompleted }
        assertEquals(other.id.value, viewModel.state.value.selectedConferenceId)
        assertSame(other, viewModel.state.value.selectedConference)
        viewModel.close()
    }

    @Test
    fun `deleting selected conference persists fallback selection`() {
        val conferenceA = Conference(ConferenceId("conference-a"), "Conference A", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val conferenceB = Conference(ConferenceId("conference-b"), "Conference B", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val store = tempLastSelectedConferenceStore()
        val repository = FakeConferenceRepository(
            initialConference = conferenceA,
            conferences = listOf(conferenceA, conferenceB),
            conferencesAfterDelete = listOf(conferenceB),
        )
        val viewModel = viewModelWith(repository, lastSelectedConferenceStore = store)
        waitUntil { viewModel.state.value.selectedConference === conferenceA }

        viewModel.deleteConference("conference-a")

        waitUntil { viewModel.state.value.selectedConference === conferenceB }
        assertEquals("conference-b", viewModel.state.value.selectedConferenceId)
        assertEquals("conference-b", store.load())
        viewModel.close()
    }

    @Test
    fun `deleting final conference clears saved selection`() {
        val conference = Conference(ConferenceId("conference"), "Conference", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val store = tempLastSelectedConferenceStore()
        val repository = FakeConferenceRepository(
            initialConference = conference,
            conferences = listOf(conference),
            conferencesAfterDelete = emptyList(),
        )
        val viewModel = viewModelWith(repository, lastSelectedConferenceStore = store)
        waitUntil { viewModel.state.value.selectedConference === conference }

        viewModel.deleteConference("conference")

        waitUntil { !viewModel.state.value.loading && viewModel.state.value.conferences.isEmpty() }
        assertNull(viewModel.state.value.selectedConferenceId)
        assertNull(store.load())
        viewModel.close()
    }

    private fun viewModelWith(
        repository: ConferenceRepository,
        conferenceTransfer: ConferenceTransferRunner = FakeConferenceTransferRunner(),
        markdownSafetyExport: MarkdownSafetyExportRunner = FakeMarkdownSafetyExportRunner(),
        lastSelectedConferenceStore: LastSelectedConferenceStore = tempLastSelectedConferenceStore(),
        workspace: ConferenceWorkspaceRunner? = null,
        workbookUpdate: suspend (String, Path, MymunUpdateFilenameCandidate?) -> UpdateResult = { _, _, _ ->
            UpdateResult(true, emptyList())
        },
    ): MainViewModel = MainViewModel(
        repository,
        WorkbookUpdateRunner { conferenceId, path, signatureToTrust ->
            workbookUpdate(conferenceId, path, signatureToTrust)
        },
        conferenceTransfer,
        markdownSafetyExport,
        lastSelectedConferenceStore,
        workspace,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    private fun tempLastSelectedConferenceStore(): LastSelectedConferenceStore =
        LastSelectedConferenceStore(Files.createTempDirectory("munassign-last-selected").resolve("last-selected.properties"))

    private fun waitUntil(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            Thread.sleep(20)
        }
        check(predicate()) { "Timed out waiting for ViewModel state." }
    }
}

private fun Conference.synchronizedListItem(displayOrder: Int = 0): ConferenceListItem = ConferenceListItem(
    id = id.value,
    name = name,
    displayOrder = displayOrder,
    storageKind = ConferenceListStorageKind.Synchronized,
    availability = ConferenceListAvailability.Available,
    path = "$name.sync.munassign",
)

private class FakeConferenceWorkspaceRunner(
    private val entries: List<ConferenceListItem>,
    private val fileInspection: ConferenceFileInspection = ConferenceFileInspection(ConferenceFileKind.ConferenceTransfer),
    private val connectedConference: Conference? = null,
    private val reloadedConference: Conference? = null,
    private val reloadStarted: CompletableDeferred<Unit>? = null,
    private val reloadResult: CompletableDeferred<Conference?>? = null,
) : ConferenceWorkspaceRunner {
    var connectedPath: Path? = null
        private set
    var reloadedConferenceId: String? = null
        private set
    var reloadCompleted: Boolean = false
        private set

    override suspend fun listEntries(): List<ConferenceListItem> = entries

    override suspend fun createSynchronizedConference(name: String, destinationPath: Path): Conference =
        error("Not used by this test.")

    override suspend fun moveConferenceToSynchronized(conferenceId: String, destinationPath: Path): Conference =
        error("Not used by this test.")

    override suspend fun connectSynchronizedConference(path: Path): Conference {
        connectedPath = path
        return requireNotNull(connectedConference)
    }

    override suspend fun locateSynchronizedConference(conferenceId: String, path: Path): Conference =
        error("Not used by this test.")

    override suspend fun unlinkSynchronizedConference(conferenceId: String) = error("Not used by this test.")

    override suspend fun deleteSynchronizedConferenceFile(conferenceId: String) = error("Not used by this test.")

    override suspend fun reloadConference(conferenceId: String): Conference? {
        reloadedConferenceId = conferenceId
        reloadStarted?.complete(Unit)
        val result = reloadResult?.await() ?: reloadedConference
        reloadCompleted = true
        return result
    }

    override fun inspectFile(path: Path): ConferenceFileInspection = fileInspection

    override suspend fun importSynchronizedAsNew(path: Path): Conference = error("Not used by this test.")

    override suspend fun replaceFromSynchronized(conferenceId: String, path: Path): Conference =
        error("Not used by this test.")
}

private class FakeConferenceTransferRunner(
    private val importedId: String = "imported",
    private val replacedId: String = "replaced",
    private val exportError: Exception? = null,
    private val importError: Exception? = null,
) : ConferenceTransferRunner {
    var exportedConferenceId: String? = null
        private set
    var exportDestinationPath: Path? = null
        private set
    var importSourcePath: Path? = null
        private set
    var replacedConferenceId: String? = null
        private set
    var replaceSourcePath: Path? = null
        private set

    override suspend fun exportConference(conferenceId: String, destinationPath: Path) {
        exportError?.let { throw it }
        exportedConferenceId = conferenceId
        exportDestinationPath = destinationPath
    }

    override suspend fun importConferenceAsNew(sourcePath: Path): String {
        importError?.let { throw it }
        importSourcePath = sourcePath
        return importedId
    }

    override suspend fun replaceConference(targetConferenceId: String, sourcePath: Path): String {
        importError?.let { throw it }
        replacedConferenceId = targetConferenceId
        replaceSourcePath = sourcePath
        return replacedId
    }
}

private class FakeMarkdownSafetyExportRunner(
    private val failures: List<MarkdownSafetyExportFailure> = emptyList(),
    private val exportError: Exception? = null,
    private val exportStarted: CompletableDeferred<Unit>? = null,
    private val exportResultDeferred: CompletableDeferred<MarkdownSafetyExportResult>? = null,
) : MarkdownSafetyExportRunner {
    val calls: MutableList<MarkdownSafetyExportCall> = mutableListOf()

    override suspend fun exportAll(
        conferences: List<Conference>,
        trigger: MarkdownSafetyExportTrigger,
    ): MarkdownSafetyExportResult {
        exportError?.let { throw it }
        calls += MarkdownSafetyExportCall(
            conferenceIds = conferences.map { it.id.value },
            trigger = trigger,
        )
        exportStarted?.complete(Unit)
        exportResultDeferred?.let { return it.await() }
        return MarkdownSafetyExportResult(
            directory = Path.of("markdown-exports"),
            writtenFiles = emptyList(),
            failures = failures,
        )
    }
}

private data class MarkdownSafetyExportCall(
    val conferenceIds: List<String>,
    val trigger: MarkdownSafetyExportTrigger,
)

private class FakeConferenceRepository(
    private val initialConference: Conference? = null,
    private val conferences: List<Conference> = listOfNotNull(initialConference),
    private val createdConferenceDeferred: CompletableDeferred<Conference>? = null,
    private val createConferenceStarted: CompletableDeferred<Unit>? = null,
    private val conferencesAfterCreate: List<Conference>? = null,
    private val resolvedConference: Conference? = initialConference,
    private val resolveError: String? = null,
    private val resolveThrows: Exception? = null,
    private val createConferenceThrows: Exception? = null,
    private val createdReservationConference: Conference? = initialConference,
    private val createdReservationConferenceDeferred: CompletableDeferred<Conference>? = null,
    private val createReservationStarted: CompletableDeferred<Unit>? = null,
    private val createReservationThrows: Exception? = null,
    private val removeDelegateReservationError: String? = null,
    private val committeeModeConference: Conference? = initialConference,
    private val paymentTierConference: Conference? = initialConference,
    private val committeeModeResultDeferred: CompletableDeferred<Result<Conference>>? = null,
    private val committeeModeStarted: CompletableDeferred<Unit>? = null,
    private val positionMappedConference: Conference? = initialConference,
    private val positionSelectionConference: Conference? = initialConference,
    private val delegationSchoolConference: Conference? = initialConference,
    private val reviewedDelegationsConference: Conference? = initialConference,
    private val reviewDelegationsThrows: Exception? = null,
    private val getConferenceStarted: CompletableDeferred<Unit>? = null,
    private val getConferenceDeferred: CompletableDeferred<Conference?>? = null,
    private val conferencesAfterDelete: List<Conference>? = null,
) : ConferenceRepository {
    private var conferenceCreated: Boolean = false
    private var conferenceDeleted: Boolean = false
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
    var committeePaymentTier: PaymentTier? = null
        private set
    var delegatePaymentTier: PaymentTier? = null
        private set
    var paymentTierCommitteeId: String? = null
        private set
    var paymentTierDelegateId: String? = null
        private set
    var positionMappedConferenceId: String? = null
        private set
    var positionMappedPositionId: String? = null
        private set
    var positionMappedCommitteeId: String? = null
        private set
    var positionSelectionPositionId: String? = null
        private set
    var positionSelectionDelegateIds: List<String> = emptyList()
        private set
    var delegationSchoolDelegationId: String? = null
        private set
    var delegationSchoolIsHighSchool: Boolean? = null
        private set
    var savedPreferenceCompletions: List<PreferenceCompletionSelection> = emptyList()
        private set
    var savedPreferenceCorrections: List<PreferenceCorrectionSelection> = emptyList()
        private set

    override suspend fun listConferences(): List<Conference> =
        when {
            conferenceDeleted -> conferencesAfterDelete ?: conferences
            conferenceCreated -> conferencesAfterCreate ?: conferences
            else -> conferences
        }

    override suspend fun createConference(name: String): Conference {
        createConferenceStarted?.complete(Unit)
        createConferenceThrows?.let { throw it }
        val created = createdConferenceDeferred?.await() ?: unsupported()
        conferenceCreated = true
        return created
    }

    override suspend fun renameConference(conferenceId: ConferenceId, newName: String): Conference = unsupported()

    override suspend fun deleteConference(conferenceId: ConferenceId) {
        conferenceDeleted = true
    }

    override suspend fun reorderConferences(conferenceIdsInOrder: List<ConferenceId>): Unit = unsupported()

    override suspend fun deleteMymunUpdateFilenameSignature(
        conferenceId: ConferenceId,
        signatureId: MymunUpdateFilenameSignatureId,
    ): Conference {
        val conference = requireNotNull(conferences.firstOrNull { it.id == conferenceId })
        conference.mymunUpdateFilenameSignatures.removeAll { it.id == signatureId }
        return conference
    }

    override suspend fun setCommitteeSlotsPerPosition(
        conferenceId: ConferenceId,
        committeeId: CommitteeId,
        slotsPerPosition: Int,
    ): Conference = unsupported()

    override suspend fun setCommitteeHighSchoolStatus(
        conferenceId: ConferenceId,
        committeeId: CommitteeId,
        isHighSchoolCommittee: Boolean,
    ): Conference = unsupported()

    override suspend fun setCommitteeMode(
        conferenceId: ConferenceId,
        committeeId: CommitteeId,
        mode: CommitteeMode,
    ): Conference {
        committeeModeConferenceId = conferenceId.value
        committeeModeCommitteeId = committeeId.value
        committeeModeMode = mode
        committeeModeStarted?.complete(Unit)
        committeeModeResultDeferred?.let { return it.await().getOrThrow() }
        return requireNotNull(committeeModeConference)
    }

    override suspend fun setCommitteePaymentTier(
        conferenceId: ConferenceId,
        committeeId: CommitteeId,
        paymentTier: PaymentTier,
    ): Conference {
        paymentTierCommitteeId = committeeId.value
        committeePaymentTier = paymentTier
        return requireNotNull(paymentTierConference)
    }

    override suspend fun setDelegatePaymentTier(
        conferenceId: ConferenceId,
        delegateId: DelegateId,
        paymentTier: PaymentTier,
    ): Conference {
        paymentTierDelegateId = delegateId.value
        delegatePaymentTier = paymentTier
        return requireNotNull(paymentTierConference)
    }

    override suspend fun setPositionMappedCommittee(
        conferenceId: ConferenceId,
        positionId: PositionId,
        mappedCommitteeId: CommitteeId?,
    ): Conference {
        positionMappedConferenceId = conferenceId.value
        positionMappedPositionId = positionId.value
        positionMappedCommitteeId = mappedCommitteeId?.value
        return requireNotNull(positionMappedConference)
    }

    override suspend fun setDelegationHighSchoolStatus(
        conferenceId: ConferenceId,
        delegationId: DelegationId,
        isHighSchoolDelegation: Boolean,
    ): Conference {
        delegationSchoolDelegationId = delegationId.value
        delegationSchoolIsHighSchool = isHighSchoolDelegation
        return requireNotNull(delegationSchoolConference)
    }

    override suspend fun markDelegationsReviewed(conferenceId: ConferenceId): Conference {
        reviewDelegationsThrows?.let { throw it }
        return requireNotNull(reviewedDelegationsConference)
    }

    override suspend fun assignDelegateToSlot(
        conferenceId: ConferenceId,
        slotId: SlotId,
        delegateId: DelegateId,
        moveExistingPending: Boolean,
    ): Conference = unsupported()

    override suspend fun setPositionPendingAssignments(
        conferenceId: ConferenceId,
        positionId: PositionId,
        selectedDelegateIds: List<DelegateId>,
    ): Conference {
        positionSelectionPositionId = positionId.value
        positionSelectionDelegateIds = selectedDelegateIds.map { it.value }
        return requireNotNull(positionSelectionConference)
    }

    override suspend fun clearPendingSlot(conferenceId: ConferenceId, slotId: SlotId): Conference = unsupported()

    override suspend fun deactivateDelegate(conferenceId: ConferenceId, delegateId: DelegateId): Conference = unsupported()

    override suspend fun reactivateDelegate(conferenceId: ConferenceId, delegateId: DelegateId): Conference = unsupported()

    override suspend fun createReservation(
        conferenceId: ConferenceId,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: DelegationId?,
    ): Conference {
        createReservationThrows?.let { throw it }
        createdReservationConferenceId = conferenceId.value
        createdReservationName = name
        createdReservationHighSchoolCount = highSchoolCount
        createdReservationUniversityCount = universityCount
        createdReservationDelegationId = delegationId?.value
        createReservationStarted?.complete(Unit)
        createdReservationConferenceDeferred?.let { return it.await() }
        return requireNotNull(createdReservationConference)
    }

    override suspend fun updateReservation(
        conferenceId: ConferenceId,
        reservationId: ReservationId,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: DelegationId?,
    ): Conference = unsupported()

    override suspend fun deleteReservation(conferenceId: ConferenceId, reservationId: ReservationId): Conference = unsupported()

    override suspend fun assignDelegateReservation(
        conferenceId: ConferenceId,
        delegateId: DelegateId,
        reservationId: ReservationId,
    ): Conference = unsupported()

    override suspend fun removeDelegateReservation(conferenceId: ConferenceId, delegateId: DelegateId): Conference {
        removeDelegateReservationError?.let { throw IllegalArgumentException(it) }
        return requireNotNull(initialConference)
    }

    override suspend fun addDelegateComment(conferenceId: ConferenceId, delegateId: DelegateId, body: String): Conference = unsupported()

    override suspend fun updateDelegateComment(conferenceId: ConferenceId, commentId: DelegateCommentId, body: String): Conference =
        unsupported()

    override suspend fun deleteDelegateComment(conferenceId: ConferenceId, commentId: DelegateCommentId): Conference = unsupported()

    override suspend fun updateQuestionConfiguration(
        conferenceId: ConferenceId,
        configuration: QuestionConfiguration,
    ): Conference = unsupported()

    override suspend fun updateQuestionConfigurations(
        conferenceId: ConferenceId,
        configurations: List<QuestionConfiguration>,
    ): Conference = unsupported()

    override suspend fun reorderQuestionConfigurations(
        conferenceId: ConferenceId,
        questionIdsInDisplayOrder: List<QuestionId>,
    ): Conference = unsupported()

    override suspend fun resolveAnswerIssue(
        conferenceId: ConferenceId,
        delegateId: DelegateId,
        questionId: QuestionId?,
        issueKey: String,
        justification: String?,
    ): Conference {
        resolveThrows?.let { throw it }
        resolveError?.let { throw IllegalArgumentException(it) }
        resolvedDelegateId = delegateId.value
        resolvedQuestionId = questionId?.value
        resolvedIssueKey = issueKey
        resolvedJustification = justification
        return requireNotNull(resolvedConference)
    }

    override suspend fun updateAnswerIssueResolutionJustification(
        conferenceId: ConferenceId,
        resolutionId: AnswerIssueResolutionId,
        justification: String?,
    ): Conference = unsupported()

    override suspend fun unresolveAnswerIssue(conferenceId: ConferenceId, resolutionId: AnswerIssueResolutionId): Conference = unsupported()

    override suspend fun savePreferenceCompletions(
        conferenceId: ConferenceId,
        selections: List<PreferenceCompletionSelection>,
    ): Conference {
        savedPreferenceCompletions = selections
        return requireNotNull(initialConference)
    }

    override suspend fun clearPreferenceCompletion(
        conferenceId: ConferenceId,
        delegateId: DelegateId,
        rank: Int,
    ): Conference = requireNotNull(initialConference)

    override suspend fun savePreferenceCorrections(
        conferenceId: ConferenceId,
        selections: List<PreferenceCorrectionSelection>,
    ): Conference {
        savedPreferenceCorrections = selections
        return requireNotNull(initialConference)
    }

    override suspend fun resetPreferenceCorrections(conferenceId: ConferenceId): Conference =
        requireNotNull(initialConference)

    override suspend fun getConference(conferenceId: ConferenceId): Conference? {
        getConferenceStarted?.complete(Unit)
        getConferenceDeferred?.let { return it.await() }
        return listConferences().firstOrNull { it.id == conferenceId }
    }

    private fun unsupported(): Nothing = error("Not needed for this test.")
}
