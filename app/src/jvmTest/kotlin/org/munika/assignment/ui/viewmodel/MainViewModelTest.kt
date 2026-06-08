package org.munika.assignment.ui.viewmodel

import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.munika.assignment.data.db.DatabaseFactory
import org.munika.assignment.data.db.repository.ConferenceRepositoryImpl
import org.munika.assignment.data.excel.MymunWorkbookUpdater
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.QuestionConfiguration
import org.munika.assignment.domain.repository.ConferenceRepository

class MainViewModelTest {
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

    private fun viewModelWith(repository: ConferenceRepository): MainViewModel {
        val dbPath = Files.createTempDirectory("munassign-main-viewmodel-test").resolve("data.db")
        val updaterRepository = ConferenceRepositoryImpl(DatabaseFactory.connect(dbPath))
        return MainViewModel(repository, MymunWorkbookUpdater(updaterRepository))
    }

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
    private val resolvedConference: Conference? = initialConference,
    private val resolveError: String? = null,
) : ConferenceRepository {
    var resolvedDelegateId: String? = null
        private set
    var resolvedQuestionId: String? = null
        private set
    var resolvedIssueKey: String? = null
        private set
    var resolvedJustification: String? = null
        private set

    override suspend fun listConferences(): List<Conference> = listOfNotNull(initialConference)

    override suspend fun createConference(name: String): Conference = unsupported()

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

    override suspend fun assignDelegateToSlot(
        conferenceId: String,
        slotId: String,
        delegateId: String,
        moveExistingPending: Boolean,
    ): Conference = unsupported()

    override suspend fun clearPendingSlot(conferenceId: String, slotId: String): Conference = unsupported()

    override suspend fun deactivateDelegate(conferenceId: String, delegateId: String): Conference = unsupported()

    override suspend fun reactivateDelegate(conferenceId: String, delegateId: String): Conference = unsupported()

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

    override suspend fun getConference(conferenceId: String): Conference? = initialConference

    private fun unsupported(): Nothing = error("Not needed for this test.")
}
