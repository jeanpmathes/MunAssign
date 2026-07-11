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
import org.munika.assignment.domain.model.DelegationId
import org.munika.assignment.domain.model.PositionId
import org.munika.assignment.domain.model.QuestionConfiguration
import org.munika.assignment.domain.model.QuestionId
import org.munika.assignment.domain.model.ReservationId
import org.munika.assignment.domain.model.SlotId
import org.munika.assignment.domain.repository.ConferenceRepository
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
        val viewModel = viewModelWith(FakeConferenceRepository(initialConference = conference)) { _, _ ->
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

        viewModel.replaceConference("conference", Path.of("replacement.munassign"))

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
        val viewModel = viewModelWith(FakeConferenceRepository(initialConference = conference)) { _, _ ->
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
        val viewModel = viewModelWith(FakeConferenceRepository(initialConference = conference)) { _, _ ->
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
        assertFalse(viewModel.state.value.loading)
        viewModel.close()
    }

    @Test
    fun `workbook exception emits concise failure notice`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val viewModel = viewModelWith(FakeConferenceRepository(initialConference = conference)) { _, _ ->
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
        workbookUpdate: suspend (String, Path) -> UpdateResult = { _, _ -> UpdateResult(true, emptyList()) },
    ): MainViewModel = MainViewModel(
        repository,
        WorkbookUpdateRunner { conferenceId, path -> workbookUpdate(conferenceId, path) },
        conferenceTransfer,
        markdownSafetyExport,
        lastSelectedConferenceStore,
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
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
    private val removeDelegateReservationError: String? = null,
    private val committeeModeConference: Conference? = initialConference,
    private val committeeModeResultDeferred: CompletableDeferred<Result<Conference>>? = null,
    private val committeeModeStarted: CompletableDeferred<Unit>? = null,
    private val positionMappedConference: Conference? = initialConference,
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
    var positionMappedConferenceId: String? = null
        private set
    var positionMappedPositionId: String? = null
        private set
    var positionMappedCommitteeId: String? = null
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

    override suspend fun assignDelegateToSlot(
        conferenceId: ConferenceId,
        slotId: SlotId,
        delegateId: DelegateId,
        moveExistingPending: Boolean,
    ): Conference = unsupported()

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

    override suspend fun getConference(conferenceId: ConferenceId): Conference? {
        getConferenceStarted?.complete(Unit)
        getConferenceDeferred?.let { return it.await() }
        return listConferences().firstOrNull { it.id == conferenceId }
    }

    private fun unsupported(): Nothing = error("Not needed for this test.")
}
