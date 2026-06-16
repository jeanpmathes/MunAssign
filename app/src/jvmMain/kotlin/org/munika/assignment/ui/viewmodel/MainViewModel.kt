package org.munika.assignment.ui.viewmodel

import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.munika.assignment.data.excel.MymunWorkbookUpdater
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.QuestionConfiguration
import org.munika.assignment.domain.repository.ConferenceRepository
import org.munika.assignment.domain.repository.UpdateIssue
import org.munika.assignment.domain.repository.UpdateIssueSeverity
import org.munika.assignment.domain.repository.UpdateSummary

/**
 * Main screen state holder.
 */
class MainViewModel(
    private val repository: ConferenceRepository,
    private val workbookUpdater: MymunWorkbookUpdater,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        refreshConferences()
    }

    fun refreshConferences() {
        scope.launch {
            val conferences = repository.listConferences()
            _state.update { current ->
                val selectedId = current.selectedConferenceId?.takeIf { selected -> conferences.any { it.id.value == selected } }
                    ?: conferences.firstOrNull()?.id?.value
                current.copy(conferences = conferences, selectedConferenceId = selectedId, loading = false)
            }
            selectedIdOrNull()?.let { loadConference(it) }
        }
    }

    fun selectConference(conferenceId: String) {
        _state.update { it.copy(selectedConferenceId = conferenceId) }
        loadConference(conferenceId)
    }

    fun createConference(name: String): Boolean {
        val validationError = validateConferenceName(name)
        if (validationError != null) {
            _state.update { it.copy(conferenceActionError = validationError) }
            return false
        }
        scope.launch {
            val created = repository.createConference(name.trim())
            val conferences = repository.listConferences()
            _state.update {
                it.copy(
                    conferences = conferences,
                    selectedConferenceId = created.id.value,
                    selectedConference = created,
                    updateIssues = emptyList(),
                    conferenceActionError = null,
                )
            }
        }
        return true
    }

    fun renameConference(conferenceId: String, newName: String): Boolean {
        val existing = _state.value.conferences.firstOrNull { it.id.value == conferenceId }
        val validationError = validateConferenceName(newName, currentConferenceId = conferenceId, requireChangedFrom = existing?.name)
        if (validationError != null) {
            _state.update { it.copy(conferenceActionError = validationError) }
            return false
        }
        scope.launch {
            val renamed = repository.renameConference(conferenceId, newName.trim())
            val conferences = repository.listConferences()
            val selectedConference = if (_state.value.selectedConferenceId == conferenceId) renamed else _state.value.selectedConference
            _state.update {
                it.copy(
                    conferences = conferences,
                    selectedConference = selectedConference,
                    conferenceActionError = null,
                )
            }
        }
        return true
    }

    fun deleteConference(conferenceId: String) {
        if (_state.value.loading) return
        scope.launch {
            repository.deleteConference(conferenceId)
            val conferences = repository.listConferences()
            val previousSelectedId = _state.value.selectedConferenceId
            val nextId = when {
                previousSelectedId != conferenceId -> previousSelectedId?.takeIf { selected -> conferences.any { it.id.value == selected } }
                else -> conferences.firstOrNull()?.id?.value
            }
            val nextConference = nextId?.let { repository.getConference(it) }
            _state.update {
                it.copy(
                    conferences = conferences,
                    selectedConferenceId = nextId,
                    selectedConference = nextConference,
                    conferenceActionError = null,
                )
            }
        }
    }

    fun deleteSelectedConference() {
        selectedIdOrNull()?.let(::deleteConference)
    }

    fun reorderConferences(conferenceIdsInOrder: List<String>): Boolean {
        val knownIds = _state.value.conferences.map { it.id.value }
        if (conferenceIdsInOrder.toSet() != knownIds.toSet() || conferenceIdsInOrder.size != knownIds.size) {
            _state.update { it.copy(conferenceActionError = "Conference order is out of date. Reopen management and try again.") }
            return false
        }
        scope.launch {
            repository.reorderConferences(conferenceIdsInOrder)
            val conferences = repository.listConferences()
            _state.update { it.copy(conferences = conferences, conferenceActionError = null) }
        }
        return true
    }

    fun clearConferenceActionError() {
        _state.update { it.copy(conferenceActionError = null) }
    }

    fun clearBoardActionError() {
        _state.update { it.copy(boardActionError = null) }
    }

    fun openDelegateDetail(delegateId: String) {
        _state.update { it.copy(selectedDelegateId = delegateId, delegateReservationActionError = null) }
    }

    fun closeDelegateDetail() {
        _state.update { it.copy(selectedDelegateId = null, delegateReservationActionError = null) }
    }

    fun openDelegateList(): Unit {
        _state.update { it.copy(delegateListOpen = true) }
    }

    fun closeDelegateList(): Unit {
        _state.update { it.copy(delegateListOpen = false) }
    }

    fun openNoticeManager(): Unit {
        _state.update {
            it.copy(
                noticeManagerOpen = true,
                noticeManagerDelegateId = null,
            )
        }
    }

    fun openDelegateNoticeManager(delegateId: String): Unit {
        _state.update {
            it.copy(
                noticeManagerOpen = true,
                noticeManagerDelegateId = delegateId,
            )
        }
    }

    fun closeNoticeManager(): Unit {
        _state.update {
            it.copy(
                noticeManagerOpen = false,
                noticeManagerDelegateId = null,
            )
        }
    }

    fun openDelegationOverview(): Unit {
        _state.update { it.copy(delegationOverviewOpen = true, delegationOverviewTargetId = null) }
    }

    fun openDelegationOverviewFor(delegationId: String): Unit {
        _state.update {
            it.copy(
                delegationOverviewOpen = true,
                delegationOverviewTargetId = delegationId,
                reservationManagerOpen = false,
                reservationManagerDelegationId = null,
                reservationActionError = null,
            )
        }
    }

    fun closeDelegationOverview(): Unit {
        _state.update { it.copy(delegationOverviewOpen = false, delegationOverviewTargetId = null) }
    }

    fun openReservationManager(delegationId: String? = null): Unit {
        _state.update {
            it.copy(
                reservationManagerOpen = true,
                reservationManagerDelegationId = delegationId,
            )
        }
    }

    fun closeReservationManager(): Unit {
        _state.update {
            it.copy(
                reservationManagerOpen = false,
                reservationManagerDelegationId = null,
            )
        }
    }

    fun openQuestionConfiguration(): Unit {
        _state.update { it.copy(questionConfigurationOpen = true, questionConfigurationError = null) }
    }

    fun closeQuestionConfiguration(): Unit {
        _state.update { it.copy(questionConfigurationOpen = false, questionConfigurationError = null) }
    }

    fun updateDelegateListControls(controls: DelegateListControlsUiState): Unit {
        _state.update { it.copy(delegateListControls = controls) }
    }

    fun applyDelegateListPreset(preset: DelegateListPreset): Unit {
        _state.update { it.copy(delegateListControls = it.delegateListControls.applyPreset(preset)) }
    }

    fun navigateToBoardPosition(committeeId: String, positionId: String) {
        _state.update {
            it.copy(
                boardScrollRequest = BoardScrollRequest(committeeId = committeeId, positionId = positionId),
            )
        }
    }

    fun setCommitteeSlotsPerPosition(committeeId: String, slotsPerPosition: Int) {
        val selectedId = selectedIdOrNull() ?: return
        if (slotsPerPosition < 1) {
            _state.update { it.copy(boardActionError = "Committee slot count must be at least 1.") }
            return
        }
        scope.launch {
            _state.update { it.copy(loading = true, boardActionError = null) }
            try {
                val conference = repository.setCommitteeSlotsPerPosition(selectedId, committeeId, slotsPerPosition)
                _state.update {
                    it.copy(
                        selectedConference = conference,
                        loading = false,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                _state.update {
                    it.copy(
                        loading = false,
                        boardActionError = exception.message ?: "Slot count could not be changed.",
                    )
                }
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        boardActionError = "Slot count update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}",
                    )
                }
            }
        }
    }

    fun setCommitteeHighSchoolStatus(committeeId: String, isHighSchoolCommittee: Boolean) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            _state.update { it.copy(loading = true, boardActionError = null) }
            try {
                val conference = repository.setCommitteeHighSchoolStatus(selectedId, committeeId, isHighSchoolCommittee)
                _state.update {
                    it.copy(
                        selectedConference = conference,
                        loading = false,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                _state.update {
                    it.copy(
                        loading = false,
                        boardActionError = exception.message ?: "Committee high-school status could not be changed.",
                    )
                }
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        boardActionError = "Committee high-school status update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}",
                    )
                }
            }
        }
    }

    fun assignDelegateToSlot(slotId: String, delegateId: String, moveExistingPending: Boolean) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            _state.update { it.copy(loading = true, boardActionError = null) }
            try {
                val conference = repository.assignDelegateToSlot(
                    conferenceId = selectedId,
                    slotId = slotId,
                    delegateId = delegateId,
                    moveExistingPending = moveExistingPending,
                )
                _state.update {
                    it.copy(
                        selectedConference = conference,
                        loading = false,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                _state.update {
                    it.copy(
                        loading = false,
                        boardActionError = exception.message ?: "Assignment could not be changed.",
                    )
                }
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        boardActionError = "Assignment update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}",
                    )
                }
            }
        }
    }

    fun clearPendingSlot(slotId: String) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            _state.update { it.copy(loading = true, boardActionError = null) }
            try {
                val conference = repository.clearPendingSlot(selectedId, slotId)
                _state.update {
                    it.copy(
                        selectedConference = conference,
                        loading = false,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                _state.update {
                    it.copy(
                        loading = false,
                        boardActionError = exception.message ?: "Pending assignment could not be cleared.",
                    )
                }
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        boardActionError = "Assignment clear failed: ${exception.message ?: exception::class.simpleName.orEmpty()}",
                    )
                }
            }
        }
    }

    fun deactivateDelegate(delegateId: String) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            _state.update { it.copy(loading = true, boardActionError = null) }
            try {
                val conference = repository.deactivateDelegate(selectedId, delegateId)
                _state.update {
                    it.copy(
                        selectedConference = conference,
                        loading = false,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                _state.update {
                    it.copy(
                        loading = false,
                        boardActionError = exception.message ?: "Delegate could not be deactivated.",
                    )
                }
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        boardActionError = "Delegate update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}",
                    )
                }
            }
        }
    }

    fun reactivateDelegate(delegateId: String) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            _state.update { it.copy(loading = true, boardActionError = null) }
            try {
                val conference = repository.reactivateDelegate(selectedId, delegateId)
                _state.update {
                    it.copy(
                        selectedConference = conference,
                        loading = false,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                _state.update {
                    it.copy(
                        loading = false,
                        boardActionError = exception.message ?: "Delegate could not be reactivated.",
                    )
                }
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        boardActionError = "Delegate update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}",
                    )
                }
            }
        }
    }

    fun createReservation(
        conferenceId: String,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: String?,
    ): Unit {
        updateReservationState(conferenceId) {
            repository.createReservation(
                conferenceId = conferenceId,
                name = name,
                highSchoolCount = highSchoolCount,
                universityCount = universityCount,
                delegationId = delegationId,
            )
        }
    }

    fun updateReservation(
        conferenceId: String,
        reservationId: String,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: String?,
    ): Unit {
        updateReservationState(conferenceId) {
            repository.updateReservation(
                conferenceId = conferenceId,
                reservationId = reservationId,
                name = name,
                highSchoolCount = highSchoolCount,
                universityCount = universityCount,
                delegationId = delegationId,
            )
        }
    }

    fun deleteReservation(conferenceId: String, reservationId: String): Unit {
        updateReservationState(conferenceId) {
            repository.deleteReservation(
                conferenceId = conferenceId,
                reservationId = reservationId,
            )
        }
    }

    fun assignDelegateReservation(conferenceId: String, delegateId: String, reservationId: String): Unit {
        updateReservationState(conferenceId, ReservationActionSurface.DelegateDetail) {
            repository.assignDelegateReservation(
                conferenceId = conferenceId,
                delegateId = delegateId,
                reservationId = reservationId,
            )
        }
    }

    fun removeDelegateReservation(conferenceId: String, delegateId: String): Unit {
        updateReservationState(conferenceId, ReservationActionSurface.DelegateDetail) {
            repository.removeDelegateReservation(
                conferenceId = conferenceId,
                delegateId = delegateId,
            )
        }
    }

    fun clearReservationActionError(): Unit {
        _state.update { it.copy(reservationActionError = null, delegateReservationActionError = null) }
    }

    fun addDelegateComment(delegateId: String, body: String) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                val conference = repository.addDelegateComment(selectedId, delegateId, body)
                _state.update { it.copy(selectedConference = conference, boardActionError = null) }
            } catch (exception: IllegalArgumentException) {
                _state.update { it.copy(boardActionError = exception.message ?: "Comment could not be added.") }
            }
        }
    }

    fun updateDelegateComment(commentId: String, body: String) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                val conference = repository.updateDelegateComment(selectedId, commentId, body)
                _state.update { it.copy(selectedConference = conference, boardActionError = null) }
            } catch (exception: IllegalArgumentException) {
                _state.update { it.copy(boardActionError = exception.message ?: "Comment could not be updated.") }
            }
        }
    }

    fun deleteDelegateComment(commentId: String) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                val conference = repository.deleteDelegateComment(selectedId, commentId)
                _state.update { it.copy(selectedConference = conference, boardActionError = null) }
            } catch (exception: IllegalArgumentException) {
                _state.update { it.copy(boardActionError = exception.message ?: "Comment could not be removed.") }
            }
        }
    }

    fun resolveAnswerIssue(
        delegateId: String,
        questionId: String?,
        issueKey: String,
        justification: String?,
    ) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                val conference = repository.resolveAnswerIssue(
                    conferenceId = selectedId,
                    delegateId = delegateId,
                    questionId = questionId,
                    issueKey = issueKey,
                    justification = justification,
                )
                _state.update { it.copy(selectedConference = conference, boardActionError = null) }
            } catch (exception: IllegalArgumentException) {
                _state.update { it.copy(boardActionError = exception.message ?: "Issue could not be resolved.") }
            }
        }
    }

    fun updateAnswerIssueResolutionJustification(resolutionId: String, justification: String?) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                val conference = repository.updateAnswerIssueResolutionJustification(
                    conferenceId = selectedId,
                    resolutionId = resolutionId,
                    justification = justification,
                )
                _state.update { it.copy(selectedConference = conference, boardActionError = null) }
            } catch (exception: IllegalArgumentException) {
                _state.update { it.copy(boardActionError = exception.message ?: "Issue justification could not be updated.") }
            }
        }
    }

    fun unresolveAnswerIssue(resolutionId: String) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                val conference = repository.unresolveAnswerIssue(selectedId, resolutionId)
                _state.update { it.copy(selectedConference = conference, boardActionError = null) }
            } catch (exception: IllegalArgumentException) {
                _state.update { it.copy(boardActionError = exception.message ?: "Issue could not be reopened.") }
            }
        }
    }

    fun updateQuestionConfiguration(configuration: QuestionConfiguration) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                val conference = repository.updateQuestionConfiguration(selectedId, configuration)
                _state.update {
                    it.copy(
                        selectedConference = conference,
                        questionConfigurationError = null,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                _state.update {
                    it.copy(questionConfigurationError = exception.message ?: "Question configuration could not be saved.")
                }
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        questionConfigurationError = "Question configuration update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}",
                    )
                }
            }
        }
    }

    fun updateQuestionConfigurations(configurations: List<QuestionConfiguration>) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                var conference = _state.value.selectedConference
                configurations.forEach { configuration ->
                    conference = repository.updateQuestionConfiguration(selectedId, configuration)
                }
                conference = repository.reorderQuestionConfigurations(
                    selectedId,
                    configurations.sortedBy { it.displayOrder }.map { it.questionId.value },
                )
                _state.update {
                    it.copy(
                        selectedConference = conference,
                        questionConfigurationError = null,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                _state.update {
                    it.copy(questionConfigurationError = exception.message ?: "Question configuration could not be saved.")
                }
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        questionConfigurationError = "Question configuration update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}",
                    )
                }
            }
        }
    }

    fun requestScrollToPosition(committeeId: String, positionId: String) {
        _state.update { it.copy(boardScrollRequest = BoardScrollRequest(committeeId = committeeId, positionId = positionId)) }
    }

    fun updateFromWorkbook(path: Path) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val result = workbookUpdater.applyUpdate(selectedId, path)
                val conference = repository.getConference(selectedId)
                _state.update {
                    it.copy(
                        selectedConference = conference,
                        updateIssues = result.issues,
                        updateSummary = result.summary,
                        loading = false,
                    )
                }
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        updateIssues = listOf(
                            UpdateIssue(
                                severity = UpdateIssueSeverity.Error,
                                message = "Workbook update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}",
                                code = "update_exception",
                            ),
                        ),
                        updateSummary = UpdateSummary(blockingErrorCount = 1),
                        loading = false,
                    )
                }
            }
        }
    }

    private fun updateReservationState(
        expectedConferenceId: String,
        surface: ReservationActionSurface = ReservationActionSurface.Manager,
        action: suspend () -> Conference,
    ): Unit {
        scope.launch {
            _state.update { it.withReservationActionError(surface, null).copy(loading = true) }
            try {
                val conference = action()
                _state.update { current ->
                    val selectedConference = if (current.selectedConferenceId == expectedConferenceId) {
                        conference
                    } else {
                        current.selectedConference
                    }
                    current.copy(
                        selectedConference = selectedConference,
                        loading = false,
                    ).withReservationActionError(surface, null)
                }
            } catch (exception: IllegalArgumentException) {
                _state.update {
                    it.copy(
                        loading = false,
                    ).withReservationActionError(surface, exception.message ?: "Reservation could not be changed.")
                }
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                    ).withReservationActionError(
                        surface,
                        "Reservation update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}",
                    )
                }
            }
        }
    }

    private fun loadConference(conferenceId: String) {
        scope.launch {
            val conference = repository.getConference(conferenceId)
            _state.update { it.copy(selectedConference = conference) }
        }
    }

    private fun selectedIdOrNull(): String? = _state.value.selectedConferenceId

    private fun validateConferenceName(
        name: String,
        currentConferenceId: String? = null,
        requireChangedFrom: String? = null,
    ): String? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return "Conference name must not be blank."
        }
        if (requireChangedFrom != null && trimmed == requireChangedFrom.trim()) {
            return "Enter a different conference name."
        }
        val duplicate = _state.value.conferences.any { conference ->
            conference.id.value != currentConferenceId && conference.name.trim().equals(trimmed, ignoreCase = true)
        }
        if (duplicate) {
            return "A conference with this name already exists."
        }
        return null
    }

    override fun close() {
        scope.cancel()
    }
}

data class MainUiState(
    val loading: Boolean = true,
    val conferences: List<Conference> = emptyList(),
    val selectedConferenceId: String? = null,
    val selectedConference: Conference? = null,
    val updateIssues: List<UpdateIssue> = emptyList(),
    val updateSummary: UpdateSummary? = null,
    val conferenceActionError: String? = null,
    val boardActionError: String? = null,
    val boardScrollRequest: BoardScrollRequest? = null,
    val selectedDelegateId: String? = null,
    val delegateListOpen: Boolean = false,
    val delegateListControls: DelegateListControlsUiState = DelegateListControlsUiState(),
    val noticeManagerOpen: Boolean = false,
    val noticeManagerDelegateId: String? = null,
    val delegationOverviewOpen: Boolean = false,
    val delegationOverviewTargetId: String? = null,
    val reservationManagerOpen: Boolean = false,
    val reservationManagerDelegationId: String? = null,
    val reservationActionError: String? = null,
    val delegateReservationActionError: String? = null,
    val questionConfigurationOpen: Boolean = false,
    val questionConfigurationError: String? = null,
)

private enum class ReservationActionSurface {
    Manager,
    DelegateDetail,
}

private fun MainUiState.withReservationActionError(
    surface: ReservationActionSurface,
    message: String?,
): MainUiState = when (surface) {
    ReservationActionSurface.Manager -> copy(reservationActionError = message)
    ReservationActionSurface.DelegateDetail -> copy(delegateReservationActionError = message)
}
