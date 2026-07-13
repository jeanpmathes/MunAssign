package org.munika.assignment.ui.viewmodel

import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.munika.assignment.LastSelectedConferenceStore
import org.munika.assignment.data.db.ConferenceTransferService
import org.munika.assignment.data.excel.MymunWorkbookUpdater
import org.munika.assignment.domain.model.AnswerIssueResolutionId
import org.munika.assignment.domain.model.CommitteeId
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.Conference
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
import org.munika.assignment.domain.repository.UpdateIssueSeverity
import org.munika.assignment.domain.repository.UpdateResult
import org.munika.assignment.export.MarkdownSafetyExportRunner
import org.munika.assignment.export.MarkdownSafetyExportService
import org.munika.assignment.export.MarkdownSafetyExportTrigger

internal fun interface WorkbookUpdateRunner {
    suspend fun applyUpdate(conferenceId: String, path: Path): UpdateResult
}

internal interface ConferenceTransferRunner {
    suspend fun exportConference(conferenceId: String, destinationPath: Path)

    suspend fun importConferenceAsNew(sourcePath: Path): String

    suspend fun replaceConference(targetConferenceId: String, sourcePath: Path): String
}

/**
 * Main screen state holder.
 */
class MainViewModel internal constructor(
    private val repository: ConferenceRepository,
    private val workbookUpdater: WorkbookUpdateRunner,
    private val conferenceTransfer: ConferenceTransferRunner,
    private val markdownSafetyExport: MarkdownSafetyExportRunner,
    private val lastSelectedConferenceStore: LastSelectedConferenceStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AutoCloseable {
    constructor(
        repository: ConferenceRepository,
        workbookUpdater: MymunWorkbookUpdater,
        conferenceTransferService: ConferenceTransferService,
        markdownSafetyExportService: MarkdownSafetyExportService,
        lastSelectedConferenceStore: LastSelectedConferenceStore,
    ) : this(
        repository = repository,
        workbookUpdater = WorkbookUpdateRunner { conferenceId, path ->
            workbookUpdater.applyUpdate(conferenceId, path)
        },
        conferenceTransfer = object : ConferenceTransferRunner {
            override suspend fun exportConference(conferenceId: String, destinationPath: Path) {
                conferenceTransferService.exportConference(conferenceId, destinationPath)
            }

            override suspend fun importConferenceAsNew(sourcePath: Path): String =
                conferenceTransferService.importConferenceAsNew(sourcePath)

            override suspend fun replaceConference(targetConferenceId: String, sourcePath: Path): String =
                conferenceTransferService.replaceConference(targetConferenceId, sourcePath)
        },
        markdownSafetyExport = markdownSafetyExportService,
        lastSelectedConferenceStore = lastSelectedConferenceStore,
    )

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    private var workbookUpdateNoticeId: Long = 0
    private var transferNoticeId: Long = 0

    init {
        refreshConferences(exportMarkdownOnComplete = true)
    }

    fun refreshConferences(exportMarkdownOnComplete: Boolean = false) {
        scope.launch {
            val conferences = repository.listConferences()
            val knownConferenceIds = conferences.map { it.id.value }.toSet()
            val selectedId = _state.value.selectedConferenceId?.takeIf { it in knownConferenceIds }
                ?: lastSelectedConferenceStore.load()?.takeIf { it in knownConferenceIds }
                ?: conferences.firstOrNull()?.id?.value
            lastSelectedConferenceStore.save(selectedId)
            _state.update { current ->
                current.copy(
                    conferences = conferences,
                    selectedConferenceId = selectedId,
                    selectedConference = null,
                    loading = !exportMarkdownOnComplete,
                )
            }
            selectedId?.let { loadConferenceNow(it) }
            if (exportMarkdownOnComplete) {
                exportMarkdownSnapshots(MarkdownSafetyExportTrigger.Open, showNotice = true)
            }
            _state.update { it.copy(startupLoading = false, loading = false) }
        }
    }

    fun selectConference(conferenceId: String) {
        lastSelectedConferenceStore.save(conferenceId)
        _state.update {
            it.copy(
                selectedConferenceId = conferenceId,
                selectedConference = null,
                loading = false,
                workbookUpdateNotice = null,
                transferNotice = null,
                boardActionError = null,
                boardScrollRequest = null,
                selectedDelegateId = null,
                delegateListOpen = false,
                noticeManagerOpen = false,
                noticeManagerDelegateId = null,
                delegationOverviewOpen = false,
                delegationOverviewTargetId = null,
                delegationReservationDraft = null,
                delegationReservationActionError = null,
                reservationManagerOpen = false,
                reservationManagerDelegationId = null,
                reservationActionError = null,
                delegateReservationActionError = null,
                questionConfigurationOpen = false,
                questionConfigurationError = null,
            )
        }
        loadConference(conferenceId)
    }

    fun createConference(name: String): Boolean {
        val validationError = validateConferenceName(name)
        if (validationError != null) {
            _state.update { it.copy(conferenceActionError = validationError) }
            return false
        }
        val expectedSelectedConferenceId = selectedIdOrNull()
        launchWithConferenceActionError {
            val created = repository.createConference(name.trim())
            val conferences = repository.listConferences()
            _state.update { current ->
                if (current.selectedConferenceId == expectedSelectedConferenceId) {
                    lastSelectedConferenceStore.save(created.id.value)
                    current.copy(
                        conferences = conferences,
                        selectedConferenceId = created.id.value,
                        selectedConference = created,
                        workbookUpdateNotice = null,
                        conferenceActionError = null,
                    )
                } else {
                    current.copy(conferences = conferences)
                }
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
        launchWithConferenceActionError {
            val renamed = repository.renameConference(ConferenceId(conferenceId), newName.trim())
            val conferences = repository.listConferences()
            _state.update { current ->
                current.copy(
                    conferences = conferences,
                    selectedConference = if (current.selectedConferenceId == conferenceId) renamed else current.selectedConference,
                    conferenceActionError = null,
                )
            }
        }
        return true
    }

    fun deleteConference(conferenceId: String) {
        if (_state.value.loading) return
        launchWithConferenceActionError {
            repository.deleteConference(ConferenceId(conferenceId))
            val conferences = repository.listConferences()
            val previousSelectedId = _state.value.selectedConferenceId
            val nextId = when {
                previousSelectedId != conferenceId -> previousSelectedId?.takeIf { selected -> conferences.any { it.id.value == selected } }
                else -> conferences.firstOrNull()?.id?.value
            }
            val nextConference = nextId?.let { repository.getConference(ConferenceId(it)) }
            lastSelectedConferenceStore.save(nextId)
            _state.update { current ->
                if (current.selectedConferenceId != previousSelectedId) {
                    current.copy(conferences = conferences, conferenceActionError = null)
                } else {
                    current.copy(
                        conferences = conferences,
                        selectedConferenceId = nextId,
                        selectedConference = nextConference,
                        conferenceActionError = null,
                    )
                }
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
        launchWithConferenceActionError {
            repository.reorderConferences(conferenceIdsInOrder.map(::ConferenceId))
            val conferences = repository.listConferences()
            _state.update { it.copy(conferences = conferences, conferenceActionError = null) }
        }
        return true
    }

    fun clearConferenceActionError() {
        _state.update { it.copy(conferenceActionError = null) }
    }

    fun clearTransferNotice() {
        _state.update { it.copy(transferNotice = null) }
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
        val selectedId = selectedIdOrNull()
        val hasUnreviewedDelegations = _state.value.selectedConference?.delegations?.any { it.needsReview } == true
        _state.update { it.copy(delegationOverviewOpen = false, delegationOverviewTargetId = null) }
        if (selectedId == null || !hasUnreviewedDelegations) return
        scope.launch {
            try {
                val conference = repository.markDelegationsReviewed(ConferenceId(selectedId))
                updateConferenceState(selectedId) {
                    it.copy(selectedConference = conference, boardActionError = null)
                }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) {
                    it.copy(boardActionError = exception.message ?: "Delegations could not be marked as reviewed.")
                }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
                    it.copy(
                        boardActionError = "Delegation review update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}",
                    )
                }
            }
        }
    }

    fun setDelegationHighSchoolStatus(delegationId: String, isHighSchoolDelegation: Boolean): Unit {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            updateConferenceState(selectedId) { it.copy(loading = true, boardActionError = null) }
            try {
                val conference = repository.setDelegationHighSchoolStatus(
                    conferenceId = ConferenceId(selectedId),
                    delegationId = DelegationId(delegationId),
                    isHighSchoolDelegation = isHighSchoolDelegation,
                )
                updateConferenceState(selectedId) {
                    it.copy(selectedConference = conference, loading = false, boardActionError = null)
                }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) {
                    it.copy(
                        loading = false,
                        boardActionError = exception.message ?: "Delegation school level could not be changed.",
                    )
                }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
                    it.copy(
                        loading = false,
                        boardActionError = "Delegation school level update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}",
                    )
                }
            }
        }
    }

    fun openDelegationReservationEditor(delegationId: String): Unit {
        val delegation = _state.value.selectedConference?.delegations?.firstOrNull { it.id.value == delegationId } ?: return
        _state.update {
            it.copy(
                delegationReservationDraft = delegation.toQuickReservationDraftUiState(),
                delegationReservationActionError = null,
            )
        }
    }

    fun closeDelegationReservationEditor(): Unit {
        _state.update {
            it.copy(
                delegationReservationDraft = null,
                delegationReservationActionError = null,
            )
        }
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
            updateConferenceState(selectedId) { it.copy(loading = true, boardActionError = null) }
            try {
                val conference = repository.setCommitteeSlotsPerPosition(ConferenceId(selectedId), CommitteeId(committeeId), slotsPerPosition)
                updateConferenceState(selectedId) {
                    it.copy(
                        selectedConference = conference,
                        loading = false,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) {
                    it.copy(
                        loading = false,
                        boardActionError = exception.message ?: "Slot count could not be changed.",
                    )
                }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
                    it.copy(
                        loading = false,
                        boardActionError = "Slot count update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}",
                    )
                }
            }
        }
    }

    fun setCommitteeHighSchoolStatus(committeeId: String, isHighSchoolCommittee: Boolean) {
        setCommitteeMode(
            committeeId = committeeId,
            mode = if (isHighSchoolCommittee) CommitteeMode.HighSchool else CommitteeMode.University,
        )
    }

    fun setCommitteeMode(committeeId: String, mode: CommitteeMode) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            updateConferenceState(selectedId) { it.copy(loading = true, boardActionError = null) }
            try {
                val conference = repository.setCommitteeMode(ConferenceId(selectedId), CommitteeId(committeeId), mode)
                updateConferenceState(selectedId) {
                    it.copy(
                        selectedConference = conference,
                        loading = false,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) {
                    it.copy(
                        loading = false,
                        boardActionError = exception.message ?: "Committee mode could not be changed.",
                    )
                }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
                    it.copy(
                        loading = false,
                        boardActionError = "Committee mode update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}",
                    )
                }
            }
        }
    }

    fun setPositionMappedCommittee(positionId: String, mappedCommitteeId: String?) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            updateConferenceState(selectedId) { it.copy(loading = true, boardActionError = null) }
            try {
                val conference = repository.setPositionMappedCommittee(
                    ConferenceId(selectedId),
                    PositionId(positionId),
                    mappedCommitteeId?.let(::CommitteeId),
                )
                updateConferenceState(selectedId) {
                    it.copy(
                        selectedConference = conference,
                        loading = false,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) {
                    it.copy(
                        loading = false,
                        boardActionError = exception.message ?: "Linked committee could not be changed.",
                    )
                }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
                    it.copy(
                        loading = false,
                        boardActionError = "Linked committee update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}",
                    )
                }
            }
        }
    }

    fun assignDelegateToSlot(slotId: String, delegateId: String, moveExistingPending: Boolean) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            updateConferenceState(selectedId) { it.copy(loading = true, boardActionError = null) }
            try {
                val conference = repository.assignDelegateToSlot(
                    conferenceId = ConferenceId(selectedId),
                    slotId = SlotId(slotId),
                    delegateId = DelegateId(delegateId),
                    moveExistingPending = moveExistingPending,
                )
                updateConferenceState(selectedId) {
                    it.copy(
                        selectedConference = conference,
                        loading = false,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) {
                    it.copy(
                        loading = false,
                        boardActionError = exception.message ?: "Assignment could not be changed.",
                    )
                }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
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
            updateConferenceState(selectedId) { it.copy(loading = true, boardActionError = null) }
            try {
                val conference = repository.clearPendingSlot(ConferenceId(selectedId), SlotId(slotId))
                updateConferenceState(selectedId) {
                    it.copy(
                        selectedConference = conference,
                        loading = false,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) {
                    it.copy(
                        loading = false,
                        boardActionError = exception.message ?: "Pending assignment could not be cleared.",
                    )
                }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
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
            updateConferenceState(selectedId) { it.copy(loading = true, boardActionError = null) }
            try {
                val conference = repository.deactivateDelegate(ConferenceId(selectedId), DelegateId(delegateId))
                updateConferenceState(selectedId) {
                    it.copy(
                        selectedConference = conference,
                        loading = false,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) {
                    it.copy(
                        loading = false,
                        boardActionError = exception.message ?: "Delegate could not be deactivated.",
                    )
                }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
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
            updateConferenceState(selectedId) { it.copy(loading = true, boardActionError = null) }
            try {
                val conference = repository.reactivateDelegate(ConferenceId(selectedId), DelegateId(delegateId))
                updateConferenceState(selectedId) {
                    it.copy(
                        selectedConference = conference,
                        loading = false,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) {
                    it.copy(
                        loading = false,
                        boardActionError = exception.message ?: "Delegate could not be reactivated.",
                    )
                }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
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
                conferenceId = ConferenceId(conferenceId),
                name = name,
                highSchoolCount = highSchoolCount,
                universityCount = universityCount,
                delegationId = delegationId?.let(::DelegationId),
            )
        }
    }

    fun createDelegationReservation(
        conferenceId: String,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: String?,
    ): Unit {
        updateConferenceState(conferenceId) {
            it.copy(loading = true, delegationReservationActionError = null)
        }
        scope.launch {
            try {
                val conference = repository.createReservation(
                    conferenceId = ConferenceId(conferenceId),
                    name = name,
                    highSchoolCount = highSchoolCount,
                    universityCount = universityCount,
                    delegationId = delegationId?.let(::DelegationId),
                )
                updateConferenceState(conferenceId) {
                    it.copy(
                        selectedConference = conference,
                        loading = false,
                        delegationReservationDraft = null,
                        delegationReservationActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(conferenceId) {
                    it.copy(
                        loading = false,
                        delegationReservationActionError = exception.message ?: "Reservation could not be created.",
                    )
                }
            } catch (exception: Exception) {
                updateConferenceState(conferenceId) {
                    it.copy(
                        loading = false,
                        delegationReservationActionError =
                            "Reservation creation failed: ${exception.message ?: exception::class.simpleName.orEmpty()}",
                    )
                }
            }
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
                conferenceId = ConferenceId(conferenceId),
                reservationId = ReservationId(reservationId),
                name = name,
                highSchoolCount = highSchoolCount,
                universityCount = universityCount,
                delegationId = delegationId?.let(::DelegationId),
            )
        }
    }

    fun deleteReservation(conferenceId: String, reservationId: String): Unit {
        updateReservationState(conferenceId) {
            repository.deleteReservation(
                conferenceId = ConferenceId(conferenceId),
                reservationId = ReservationId(reservationId),
            )
        }
    }

    fun assignDelegateReservation(conferenceId: String, delegateId: String, reservationId: String): Unit {
        updateReservationState(conferenceId, ReservationActionSurface.DelegateDetail) {
            repository.assignDelegateReservation(
                conferenceId = ConferenceId(conferenceId),
                delegateId = DelegateId(delegateId),
                reservationId = ReservationId(reservationId),
            )
        }
    }

    fun removeDelegateReservation(conferenceId: String, delegateId: String): Unit {
        updateReservationState(conferenceId, ReservationActionSurface.DelegateDetail) {
            repository.removeDelegateReservation(
                conferenceId = ConferenceId(conferenceId),
                delegateId = DelegateId(delegateId),
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
                val conference = repository.addDelegateComment(ConferenceId(selectedId), DelegateId(delegateId), body)
                updateConferenceState(selectedId) { it.copy(selectedConference = conference, boardActionError = null) }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) { it.copy(boardActionError = exception.message ?: "Comment could not be added.") }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
                    it.copy(boardActionError = "Comment could not be added: ${exception.message ?: exception::class.simpleName.orEmpty()}")
                }
            }
        }
    }

    fun updateDelegateComment(commentId: String, body: String) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                val conference = repository.updateDelegateComment(ConferenceId(selectedId), DelegateCommentId(commentId), body)
                updateConferenceState(selectedId) { it.copy(selectedConference = conference, boardActionError = null) }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) { it.copy(boardActionError = exception.message ?: "Comment could not be updated.") }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
                    it.copy(boardActionError = "Comment could not be updated: ${exception.message ?: exception::class.simpleName.orEmpty()}")
                }
            }
        }
    }

    fun deleteDelegateComment(commentId: String) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                val conference = repository.deleteDelegateComment(ConferenceId(selectedId), DelegateCommentId(commentId))
                updateConferenceState(selectedId) { it.copy(selectedConference = conference, boardActionError = null) }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) { it.copy(boardActionError = exception.message ?: "Comment could not be removed.") }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
                    it.copy(boardActionError = "Comment could not be removed: ${exception.message ?: exception::class.simpleName.orEmpty()}")
                }
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
                    conferenceId = ConferenceId(selectedId),
                    delegateId = DelegateId(delegateId),
                    questionId = questionId?.let(::QuestionId),
                    issueKey = issueKey,
                    justification = justification,
                )
                updateConferenceState(selectedId) { it.copy(selectedConference = conference, boardActionError = null) }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) { it.copy(boardActionError = exception.message ?: "Issue could not be resolved.") }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
                    it.copy(boardActionError = "Issue could not be resolved: ${exception.message ?: exception::class.simpleName.orEmpty()}")
                }
            }
        }
    }

    fun updateAnswerIssueResolutionJustification(resolutionId: String, justification: String?) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                val conference = repository.updateAnswerIssueResolutionJustification(
                    conferenceId = ConferenceId(selectedId),
                    resolutionId = AnswerIssueResolutionId(resolutionId),
                    justification = justification,
                )
                updateConferenceState(selectedId) { it.copy(selectedConference = conference, boardActionError = null) }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) { it.copy(boardActionError = exception.message ?: "Issue justification could not be updated.") }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
                    it.copy(boardActionError = "Issue justification could not be updated: ${exception.message ?: exception::class.simpleName.orEmpty()}")
                }
            }
        }
    }

    fun unresolveAnswerIssue(resolutionId: String) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                val conference = repository.unresolveAnswerIssue(ConferenceId(selectedId), AnswerIssueResolutionId(resolutionId))
                updateConferenceState(selectedId) { it.copy(selectedConference = conference, boardActionError = null) }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) { it.copy(boardActionError = exception.message ?: "Issue could not be reopened.") }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
                    it.copy(boardActionError = "Issue could not be reopened: ${exception.message ?: exception::class.simpleName.orEmpty()}")
                }
            }
        }
    }

    fun updateQuestionConfiguration(configuration: QuestionConfiguration) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                val conference = repository.updateQuestionConfiguration(ConferenceId(selectedId), configuration)
                updateConferenceState(selectedId) {
                    it.copy(
                        selectedConference = conference,
                        questionConfigurationError = null,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) {
                    it.copy(questionConfigurationError = exception.message ?: "Question configuration could not be saved.")
                }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
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
                val conference = repository.updateQuestionConfigurations(ConferenceId(selectedId), configurations)
                updateConferenceState(selectedId) {
                    it.copy(
                        selectedConference = conference,
                        questionConfigurationError = null,
                        boardActionError = null,
                    )
                }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) {
                    it.copy(questionConfigurationError = exception.message ?: "Question configuration could not be saved.")
                }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
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
            updateConferenceState(selectedId) { it.copy(loading = true, workbookUpdateNotice = null) }
            try {
                val result = workbookUpdater.applyUpdate(selectedId, path)
                val conference = repository.getConference(ConferenceId(selectedId))
                updateConferenceState(selectedId) {
                    it.copy(
                        selectedConference = conference,
                        workbookUpdateNotice = result.toWorkbookUpdateNotice(),
                        loading = false,
                    )
                }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
                    it.copy(
                        workbookUpdateNotice = WorkbookUpdateNotice(
                            id = nextWorkbookUpdateNoticeId(),
                            message = "Update failed: ${exception.readableMessage()}",
                        ),
                        loading = false,
                    )
                }
            }
        }
    }

    fun rejectWorkbookUpdate(message: String) {
        _state.update {
            it.copy(
                workbookUpdateNotice = WorkbookUpdateNotice(
                    id = nextWorkbookUpdateNoticeId(),
                    message = message,
                ),
            )
        }
    }

    fun exportConference(conferenceId: String, destinationPath: Path) {
        scope.launch {
            _state.update { it.copy(loading = true, transferNotice = null, conferenceActionError = null) }
            try {
                conferenceTransfer.exportConference(conferenceId, destinationPath)
                _state.update {
                    it.copy(
                        loading = false,
                        transferNotice = TransferNotice(nextTransferNoticeId(), "Conference exported."),
                    )
                }
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        transferNotice = TransferNotice(
                            nextTransferNoticeId(),
                            "Export failed: ${exception.readableMessage()}",
                        ),
                    )
                }
            }
        }
    }

    fun importConferenceAsNew(sourcePath: Path) {
        scope.launch {
            _state.update { it.copy(loading = true, transferNotice = null, conferenceActionError = null) }
            try {
                val importedId = conferenceTransfer.importConferenceAsNew(sourcePath)
                val conferences = repository.listConferences()
                val imported = repository.getConference(ConferenceId(importedId))
                lastSelectedConferenceStore.save(importedId)
                _state.update {
                    it.copy(
                        conferences = conferences,
                        selectedConferenceId = importedId,
                        selectedConference = imported,
                        loading = false,
                        transferNotice = TransferNotice(nextTransferNoticeId(), "Conference imported."),
                    )
                }
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        transferNotice = TransferNotice(
                            nextTransferNoticeId(),
                            "Import failed: ${exception.readableMessage()}",
                        ),
                    )
                }
            }
        }
    }

    fun replaceConference(conferenceId: String, sourcePath: Path) {
        scope.launch {
            _state.update { it.copy(loading = true, transferNotice = null, conferenceActionError = null) }
            try {
                val replacedId = conferenceTransfer.replaceConference(conferenceId, sourcePath)
                val conferences = repository.listConferences()
                val replaced = repository.getConference(ConferenceId(replacedId))
                lastSelectedConferenceStore.save(replacedId)
                _state.update {
                    it.copy(
                        conferences = conferences,
                        selectedConferenceId = replacedId,
                        selectedConference = replaced,
                        loading = false,
                        transferNotice = TransferNotice(nextTransferNoticeId(), "Conference replaced."),
                    )
                }
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        transferNotice = TransferNotice(
                            nextTransferNoticeId(),
                            "Replace failed: ${exception.readableMessage()}",
                        ),
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
            updateConferenceState(expectedConferenceId) {
                it.withReservationActionError(surface, null).copy(loading = true)
            }
            try {
                val conference = action()
                updateConferenceState(expectedConferenceId) { current ->
                    current.copy(
                        selectedConference = conference,
                        loading = false,
                    ).withReservationActionError(surface, null)
                }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(expectedConferenceId) {
                    it.copy(
                        loading = false,
                    ).withReservationActionError(surface, exception.message ?: "Reservation could not be changed.")
                }
            } catch (exception: Exception) {
                updateConferenceState(expectedConferenceId) {
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
            loadConferenceNow(conferenceId)
        }
    }

    private fun launchWithConferenceActionError(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (exception: Exception) {
                _state.update { it.copy(conferenceActionError = exception.readableMessage()) }
            }
        }
    }

    private suspend fun loadConferenceNow(conferenceId: String) {
        val conference = repository.getConference(ConferenceId(conferenceId))
        updateConferenceState(conferenceId) { it.copy(selectedConference = conference) }
    }

    private fun selectedIdOrNull(): String? = _state.value.selectedConferenceId

    private suspend fun exportMarkdownSnapshots(
        trigger: MarkdownSafetyExportTrigger,
        showNotice: Boolean,
    ) {
        try {
            val fullConferences = repository.listConferences()
                .mapNotNull { conference -> repository.getConference(conference.id) }
            val result = markdownSafetyExport.exportAll(fullConferences, trigger)
            if (showNotice && result.hasFailures) {
                val count = result.failures.size
                val message = if (count == 1) {
                    "Textual safety export failed for 1 conference."
                } else {
                    "Textual safety export failed for $count conferences."
                }
                _state.update { it.copy(transferNotice = TransferNotice(nextTransferNoticeId(), message)) }
            }
        } catch (exception: Exception) {
            if (showNotice) {
                _state.update {
                    it.copy(
                        transferNotice = TransferNotice(
                            nextTransferNoticeId(),
                            "Textual safety export failed: ${exception.readableMessage()}",
                        ),
                    )
                }
            }
        }
    }

    private fun updateConferenceState(
        expectedConferenceId: String,
        transform: (MainUiState) -> MainUiState,
    ): Unit {
        _state.update { current ->
            if (current.selectedConferenceId == expectedConferenceId) transform(current) else current
        }
    }

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

    private fun UpdateResult.toWorkbookUpdateNotice(): WorkbookUpdateNotice {
        if (!success || summary.blockingErrorCount > 0) {
            val message = issues.firstOrNull { it.severity == UpdateIssueSeverity.Error }?.message
                ?: "Please check the file and try again."
            return WorkbookUpdateNotice(
                id = nextWorkbookUpdateNoticeId(),
                message = "Update failed: $message",
            )
        }
        val warningCount = maxOf(summary.warningCount, issues.count { it.severity == UpdateIssueSeverity.Warning })
        val message = when (warningCount) {
            0 -> "Update complete."
            1 -> "Update complete with 1 warning."
            else -> "Update complete with $warningCount warnings."
        }
        return WorkbookUpdateNotice(id = nextWorkbookUpdateNoticeId(), message = message)
    }

    private fun nextWorkbookUpdateNoticeId(): Long {
        workbookUpdateNoticeId += 1
        return workbookUpdateNoticeId
    }

    private fun nextTransferNoticeId(): Long {
        transferNoticeId += 1
        return transferNoticeId
    }

    private fun Exception.readableMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "Please check the file and try again."

    override fun close() {
        runBlocking {
            val completed = withTimeoutOrNull(ShutdownExportTimeout) {
                exportMarkdownSnapshots(MarkdownSafetyExportTrigger.Close, showNotice = false)
            }
            if (completed == null) {
                System.err.println("Textual safety export on close timed out after $ShutdownExportTimeout; shutting down anyway.")
            }
        }
        scope.cancel()
    }

    private companion object {
        val ShutdownExportTimeout = 5.seconds
    }
}

data class MainUiState(
    val startupLoading: Boolean = true,
    val loading: Boolean = true,
    val conferences: List<Conference> = emptyList(),
    val selectedConferenceId: String? = null,
    val selectedConference: Conference? = null,
    val workbookUpdateNotice: WorkbookUpdateNotice? = null,
    val transferNotice: TransferNotice? = null,
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
    val delegationReservationDraft: DelegationReservationDraftUiState? = null,
    val delegationReservationActionError: String? = null,
    val reservationManagerOpen: Boolean = false,
    val reservationManagerDelegationId: String? = null,
    val reservationActionError: String? = null,
    val delegateReservationActionError: String? = null,
    val questionConfigurationOpen: Boolean = false,
    val questionConfigurationError: String? = null,
)

data class WorkbookUpdateNotice(
    val id: Long,
    val message: String,
)

data class TransferNotice(
    val id: Long,
    val message: String,
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
