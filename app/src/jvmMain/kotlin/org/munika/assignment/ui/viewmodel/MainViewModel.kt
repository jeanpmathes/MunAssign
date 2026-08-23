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
import org.munika.assignment.domain.model.AnswerIssueResolutionId
import org.munika.assignment.domain.model.CommitteeId
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.DelegateCommentId
import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.DelegationId
import org.munika.assignment.domain.model.MymunUpdateFilenameAssessment
import org.munika.assignment.domain.model.MymunUpdateFilenameCandidate
import org.munika.assignment.domain.model.MymunUpdateFilenameSignatureId
import org.munika.assignment.domain.model.PaymentTier
import org.munika.assignment.domain.model.PositionId
import org.munika.assignment.domain.model.QuestionConfiguration
import org.munika.assignment.domain.model.QuestionId
import org.munika.assignment.domain.model.ReservationId
import org.munika.assignment.domain.model.SlotId
import org.munika.assignment.domain.repository.ConferenceRepository
import org.munika.assignment.domain.repository.PreferenceCompletionSelection
import org.munika.assignment.domain.repository.PreferenceCorrectionSelection
import org.munika.assignment.domain.repository.UpdateIssueSeverity
import org.munika.assignment.domain.repository.UpdateResult
import org.munika.assignment.export.MarkdownSafetyExportRunner
import org.munika.assignment.export.MarkdownSafetyExportTrigger

internal fun interface WorkbookUpdateRunner {
    suspend fun applyUpdate(
        conferenceId: String,
        path: Path,
        signatureToTrust: MymunUpdateFilenameCandidate?,
    ): UpdateResult
}

internal interface ConferenceTransferRunner {
    suspend fun exportConference(conferenceId: String, destinationPath: Path)

    suspend fun importConferenceAsNew(sourcePath: Path): String

    suspend fun replaceConference(targetConferenceId: String, sourcePath: Path): String
}

internal interface ConferenceWorkspaceRunner {
    suspend fun listEntries(): List<ConferenceListItem>

    suspend fun createSynchronizedConference(name: String, destinationPath: Path): Conference

    suspend fun moveConferenceToSynchronized(conferenceId: String, destinationPath: Path): Conference

    suspend fun connectSynchronizedConference(path: Path): Conference

    suspend fun locateSynchronizedConference(conferenceId: String, path: Path): Conference

    suspend fun unlinkSynchronizedConference(conferenceId: String)

    suspend fun deleteSynchronizedConferenceFile(conferenceId: String)

    suspend fun reloadConference(conferenceId: String): Conference?

    fun inspectFile(path: Path): ConferenceFileInspection

    suspend fun importSynchronizedAsNew(path: Path): Conference

    suspend fun replaceFromSynchronized(conferenceId: String, path: Path): Conference
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
    private val workspace: ConferenceWorkspaceRunner? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AutoCloseable {
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    private var workbookUpdateNoticeId: Long = 0
    private var transferNoticeId: Long = 0

    init {
        refreshConferences(exportMarkdownOnComplete = true)
    }

    fun refreshConferences(exportMarkdownOnComplete: Boolean = false) {
        scope.launch {
            val index = loadConferenceIndex()
            val conferences = index.conferences
            val knownConferenceIds = index.entries.map { it.id }.toSet()
            val selectedId = _state.value.selectedConferenceId?.takeIf { it in knownConferenceIds }
                ?: lastSelectedConferenceStore.load()?.takeIf { it in knownConferenceIds }
                ?: index.entries.firstOrNull()?.id
            lastSelectedConferenceStore.save(selectedId)
            _state.update { current ->
                current.copy(
                    conferences = conferences,
                    conferenceEntries = index.entries,
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
                pendingWorkbookUpdate = null,
                pendingPreferenceCompletion = null,
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
            val index = loadConferenceIndex()
            _state.update { current ->
                if (current.selectedConferenceId == expectedSelectedConferenceId) {
                    lastSelectedConferenceStore.save(created.id.value)
                    current.copy(
                        conferences = index.conferences,
                        conferenceEntries = index.entries,
                        selectedConferenceId = created.id.value,
                        selectedConference = created,
                        workbookUpdateNotice = null,
                        conferenceActionError = null,
                    )
                } else {
                    current.copy(conferences = index.conferences, conferenceEntries = index.entries)
                }
            }
        }
        return true
    }

    fun renameConference(conferenceId: String, newName: String): Boolean {
        val existing = _state.value.conferenceEntries.firstOrNull { it.id == conferenceId }
        val validationError = validateConferenceName(newName, currentConferenceId = conferenceId, requireChangedFrom = existing?.name)
        if (validationError != null) {
            _state.update { it.copy(conferenceActionError = validationError) }
            return false
        }
        launchWithConferenceActionError {
            val renamed = repository.renameConference(ConferenceId(conferenceId), newName.trim())
            val index = loadConferenceIndex()
            _state.update { current ->
                current.copy(
                    conferences = index.conferences,
                    conferenceEntries = index.entries,
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
            val index = loadConferenceIndex()
            val previousSelectedId = _state.value.selectedConferenceId
            val nextId = when {
                previousSelectedId != conferenceId -> previousSelectedId?.takeIf { selected -> index.entries.any { it.id == selected } }
                else -> index.entries.firstOrNull()?.id
            }
            val nextConference = nextId?.let { repository.getConference(ConferenceId(it)) }
            lastSelectedConferenceStore.save(nextId)
            _state.update { current ->
                if (current.selectedConferenceId != previousSelectedId) {
                    current.copy(
                        conferences = index.conferences,
                        conferenceEntries = index.entries,
                        conferenceActionError = null,
                    )
                } else {
                    current.copy(
                        conferences = index.conferences,
                        conferenceEntries = index.entries,
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
        val knownIds = _state.value.conferenceEntries.map { it.id }
        if (conferenceIdsInOrder.toSet() != knownIds.toSet() || conferenceIdsInOrder.size != knownIds.size) {
            _state.update { it.copy(conferenceActionError = "Conference order is out of date. Reopen management and try again.") }
            return false
        }
        launchWithConferenceActionError {
            repository.reorderConferences(conferenceIdsInOrder.map(::ConferenceId))
            val index = loadConferenceIndex()
            _state.update {
                it.copy(
                    conferences = index.conferences,
                    conferenceEntries = index.entries,
                    conferenceActionError = null,
                )
            }
        }
        return true
    }

    fun createSynchronizedConference(name: String, destinationPath: Path): Boolean {
        val validationError = validateConferenceName(name)
        if (validationError != null) {
            _state.update { it.copy(conferenceActionError = validationError) }
            return false
        }
        launchWorkspaceAction(selectResult = true) {
            requireNotNull(workspace) { "Synchronized conferences are unavailable." }
                .createSynchronizedConference(name.trim(), destinationPath)
        }
        return true
    }

    fun moveConferenceToSynchronized(conferenceId: String, destinationPath: Path) {
        launchWorkspaceAction(selectResult = true) {
            requireNotNull(workspace) { "Synchronized conferences are unavailable." }
                .moveConferenceToSynchronized(conferenceId, destinationPath)
        }
    }

    fun connectSynchronizedConference(path: Path) {
        launchWorkspaceAction(selectResult = true) {
            requireNotNull(workspace) { "Synchronized conferences are unavailable." }
                .connectSynchronizedConference(path)
        }
    }

    fun locateSynchronizedConference(conferenceId: String, path: Path) {
        launchWorkspaceAction(selectResult = true) {
            requireNotNull(workspace) { "Synchronized conferences are unavailable." }
                .locateSynchronizedConference(conferenceId, path)
        }
    }

    fun reloadSelectedSynchronizedConference() {
        val selectedId = selectedIdOrNull() ?: return
        reloadSynchronizedConference(selectedId)
    }

    fun reloadSynchronizedConference(conferenceId: String) {
        val entry = _state.value.conferenceEntries.firstOrNull { it.id == conferenceId } ?: return
        if (entry.storageKind != ConferenceListStorageKind.Synchronized || _state.value.loading) return
        launchWorkspaceAction(selectResult = true) {
            requireNotNull(
                requireNotNull(workspace) { "Synchronized conferences are unavailable." }
                    .reloadConference(conferenceId),
            ) { "The synchronized conference is unavailable." }
        }
    }

    fun unlinkSynchronizedConference(conferenceId: String) {
        launchWorkspaceRemoval(conferenceId) {
            requireNotNull(workspace).unlinkSynchronizedConference(conferenceId)
        }
    }

    fun deleteSynchronizedConferenceFile(conferenceId: String) {
        launchWorkspaceRemoval(conferenceId) {
            requireNotNull(workspace).deleteSynchronizedConferenceFile(conferenceId)
        }
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

    fun setCommitteePaymentTier(committeeId: String, paymentTier: PaymentTier) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                val conference = repository.setCommitteePaymentTier(
                    ConferenceId(selectedId),
                    CommitteeId(committeeId),
                    paymentTier,
                )
                updateConferenceState(selectedId) {
                    it.copy(selectedConference = conference, boardActionError = null)
                }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) {
                    it.copy(boardActionError = exception.message ?: "Committee payment tier could not be changed.")
                }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
                    it.copy(boardActionError = "Committee payment tier update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}")
                }
            }
        }
    }

    fun setDelegatePaymentTier(delegateId: String, paymentTier: PaymentTier) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                val conference = repository.setDelegatePaymentTier(
                    ConferenceId(selectedId),
                    DelegateId(delegateId),
                    paymentTier,
                )
                updateConferenceState(selectedId) {
                    it.copy(selectedConference = conference, boardActionError = null)
                }
            } catch (exception: IllegalArgumentException) {
                updateConferenceState(selectedId) {
                    it.copy(boardActionError = exception.message ?: "Delegate payment tier could not be changed.")
                }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
                    it.copy(boardActionError = "Delegate payment tier update failed: ${exception.message ?: exception::class.simpleName.orEmpty()}")
                }
            }
        }
    }

    fun savePreferenceCorrections(selections: List<PreferenceCorrectionSelection>) {
        val pending = _state.value.pendingPreferenceCompletion ?: return
        if (selections.isEmpty()) {
            _state.update { it.copy(pendingPreferenceCompletion = null) }
            return
        }
        scope.launch {
            updateConferenceState(pending.conferenceId) {
                it.copy(loading = true, pendingPreferenceCompletion = pending.copy(error = null))
            }
            try {
                val conference = repository.savePreferenceCorrections(ConferenceId(pending.conferenceId), selections)
                updateConferenceState(pending.conferenceId) {
                    it.copy(
                        selectedConference = conference,
                        loading = false,
                        pendingPreferenceCompletion = null,
                        boardActionError = null,
                    )
                }
            } catch (exception: Exception) {
                updateConferenceState(pending.conferenceId) {
                    it.copy(
                        loading = false,
                        pendingPreferenceCompletion = pending.copy(error = exception.readableMessage()),
                    )
                }
            }
        }
    }

    fun cancelPreferenceCompletion() {
        _state.update { it.copy(pendingPreferenceCompletion = null) }
    }

    fun resetPreferenceCorrections(conferenceId: String) {
        scope.launch {
            updateConferenceState(conferenceId) { it.copy(loading = true, conferenceActionError = null) }
            try {
                val conference = repository.resetPreferenceCorrections(ConferenceId(conferenceId))
                updateConferenceState(conferenceId) {
                    it.copy(selectedConference = conference, loading = false, conferenceActionError = null)
                }
            } catch (exception: Exception) {
                updateConferenceState(conferenceId) {
                    it.copy(
                        loading = false,
                        conferenceActionError = "Preference corrections could not be reset: ${exception.readableMessage()}",
                    )
                }
            }
        }
    }

    fun setPreferenceCompletion(delegateId: String, rank: Int, positionId: String) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                val conference = repository.savePreferenceCompletions(
                    ConferenceId(selectedId),
                    listOf(PreferenceCompletionSelection(DelegateId(delegateId), rank, PositionId(positionId))),
                )
                updateConferenceState(selectedId) { it.copy(selectedConference = conference, boardActionError = null) }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
                    it.copy(boardActionError = "Preference completion failed: ${exception.readableMessage()}")
                }
            }
        }
    }

    fun clearPreferenceCompletion(delegateId: String, rank: Int) {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            try {
                val conference = repository.clearPreferenceCompletion(
                    ConferenceId(selectedId),
                    DelegateId(delegateId),
                    rank,
                )
                updateConferenceState(selectedId) { it.copy(selectedConference = conference, boardActionError = null) }
            } catch (exception: Exception) {
                updateConferenceState(selectedId) {
                    it.copy(boardActionError = "Preference completion could not be cleared: ${exception.readableMessage()}")
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
        val conference = _state.value.selectedConference ?: return
        val candidate = MymunUpdateFilenameCandidate.fromFilename(path.fileName.toString())
        when (val assessment = candidate.assess(conference.mymunUpdateFilenameSignatures)) {
            is MymunUpdateFilenameAssessment.FirstUse -> applyWorkbookUpdate(selectedId, path, candidate)
            is MymunUpdateFilenameAssessment.Match -> applyWorkbookUpdate(selectedId, path, signatureToTrust = null)
            is MymunUpdateFilenameAssessment.Empty -> {
                _state.update {
                    it.copy(
                        pendingWorkbookUpdate = PendingWorkbookUpdate(
                            conferenceId = selectedId,
                            path = path,
                            candidate = candidate,
                            expectedParts = emptyList(),
                            missingParts = emptyList(),
                            unexpectedParts = emptyList(),
                            emptyCandidate = true,
                        ),
                    )
                }
            }
            is MymunUpdateFilenameAssessment.Mismatch -> {
                _state.update {
                    it.copy(
                        pendingWorkbookUpdate = PendingWorkbookUpdate(
                            conferenceId = selectedId,
                            path = path,
                            candidate = candidate,
                            expectedParts = assessment.closestSignature.parts.map { part -> part.displayValue },
                            missingParts = assessment.missingParts,
                            unexpectedParts = assessment.unexpectedParts,
                            emptyCandidate = false,
                        ),
                    )
                }
            }
        }
    }

    fun confirmPendingWorkbookUpdate() {
        val pending = _state.value.pendingWorkbookUpdate ?: return
        if (_state.value.selectedConferenceId != pending.conferenceId) {
            cancelPendingWorkbookUpdate()
            return
        }
        _state.update { it.copy(pendingWorkbookUpdate = null) }
        applyWorkbookUpdate(
            conferenceId = pending.conferenceId,
            path = pending.path,
            signatureToTrust = pending.candidate.takeUnless { pending.emptyCandidate },
        )
    }

    fun cancelPendingWorkbookUpdate() {
        _state.update { it.copy(pendingWorkbookUpdate = null) }
    }

    fun deleteMymunUpdateFilenameSignature(conferenceId: String, signatureId: String) {
        scope.launch {
            try {
                val updated = repository.deleteMymunUpdateFilenameSignature(
                    ConferenceId(conferenceId),
                    MymunUpdateFilenameSignatureId(signatureId),
                )
                val index = loadConferenceIndex()
                _state.update { current ->
                    current.copy(
                        conferences = index.conferences,
                        conferenceEntries = index.entries,
                        selectedConference = if (current.selectedConferenceId == conferenceId) updated else current.selectedConference,
                        conferenceActionError = null,
                    )
                }
            } catch (exception: Exception) {
                _state.update { it.copy(conferenceActionError = exception.readableMessage()) }
            }
        }
    }

    private fun applyWorkbookUpdate(
        conferenceId: String,
        path: Path,
        signatureToTrust: MymunUpdateFilenameCandidate?,
    ) {
        scope.launch {
            updateConferenceState(conferenceId) { it.copy(loading = true, workbookUpdateNotice = null) }
            try {
                val result = workbookUpdater.applyUpdate(conferenceId, path, signatureToTrust)
                val conference = repository.getConference(ConferenceId(conferenceId))
                updateConferenceState(conferenceId) { current ->
                    current.copy(
                        selectedConference = conference,
                        conferences = if (conference == null) current.conferences else current.conferences.map { listed ->
                            if (listed.id == conference.id) conference else listed
                        },
                        workbookUpdateNotice = result.toWorkbookUpdateNotice(),
                        pendingPreferenceCompletion = if (
                            result.success && conference != null && result.missingPreferences.isNotEmpty()
                        ) {
                            PendingPreferenceCompletion(
                                conferenceId = conferenceId,
                                preferenceRanks = result.preferenceRanks,
                                missingRanksByDelegateId = result.missingPreferences
                                    .groupBy({ it.delegateId.value }, { it.rank })
                                    .mapValues { (_, ranks) -> ranks.toSet() },
                            )
                        } else {
                            null
                        },
                        loading = false,
                    )
                }
            } catch (exception: Exception) {
                updateConferenceState(conferenceId) {
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
        val inspection = workspace?.inspectFile(sourcePath)
        if (inspection?.kind == ConferenceFileKind.SynchronizedConference) {
            _state.update { it.copy(pendingSynchronizedImportPath = sourcePath, transferNotice = null) }
            return
        }
        importConferenceAsNewNow(sourcePath, synchronizedSource = false)
    }

    fun confirmSynchronizedImportAsNew() {
        val sourcePath = _state.value.pendingSynchronizedImportPath ?: return
        _state.update { it.copy(pendingSynchronizedImportPath = null) }
        importConferenceAsNewNow(sourcePath, synchronizedSource = true)
    }

    fun connectPendingSynchronizedImport() {
        val sourcePath = _state.value.pendingSynchronizedImportPath ?: return
        _state.update { it.copy(pendingSynchronizedImportPath = null) }
        connectSynchronizedConference(sourcePath)
    }

    fun cancelSynchronizedImport() {
        _state.update { it.copy(pendingSynchronizedImportPath = null) }
    }

    private fun importConferenceAsNewNow(sourcePath: Path, synchronizedSource: Boolean) {
        scope.launch {
            _state.update { it.copy(loading = true, transferNotice = null, conferenceActionError = null) }
            try {
                val imported = if (synchronizedSource) {
                    requireNotNull(workspace).importSynchronizedAsNew(sourcePath)
                } else {
                    val importedId = conferenceTransfer.importConferenceAsNew(sourcePath)
                    requireNotNull(repository.getConference(ConferenceId(importedId)))
                }
                val importedId = imported.id.value
                val index = loadConferenceIndex()
                lastSelectedConferenceStore.save(importedId)
                _state.update {
                    it.copy(
                        conferences = index.conferences,
                        conferenceEntries = index.entries,
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

    fun prepareReplaceConference(conferenceId: String, sourcePath: Path) {
        val target = _state.value.conferenceEntries.firstOrNull { it.id == conferenceId } ?: return
        val sourceInspection = workspace?.inspectFile(sourcePath)
        _state.update {
            it.copy(
                pendingConferenceReplace = PendingConferenceReplace(
                    conferenceId = conferenceId,
                    conferenceName = target.name,
                    sourcePath = sourcePath,
                    synchronizedTarget = target.storageKind == ConferenceListStorageKind.Synchronized,
                    synchronizedSource = sourceInspection?.kind == ConferenceFileKind.SynchronizedConference,
                ),
            )
        }
    }

    fun cancelPendingConferenceReplace() {
        _state.update { it.copy(pendingConferenceReplace = null) }
    }

    fun confirmPendingConferenceReplace() {
        val pending = _state.value.pendingConferenceReplace ?: return
        _state.update { it.copy(pendingConferenceReplace = null) }
        replaceConferenceNow(pending)
    }

    private fun replaceConferenceNow(pending: PendingConferenceReplace) {
        scope.launch {
            _state.update { it.copy(loading = true, transferNotice = null, conferenceActionError = null) }
            try {
                val replaced = if (pending.synchronizedSource) {
                    requireNotNull(workspace).replaceFromSynchronized(pending.conferenceId, pending.sourcePath)
                } else {
                    val replacedId = conferenceTransfer.replaceConference(pending.conferenceId, pending.sourcePath)
                    requireNotNull(repository.getConference(ConferenceId(replacedId)))
                }
                val replacedId = replaced.id.value
                val index = loadConferenceIndex()
                lastSelectedConferenceStore.save(replacedId)
                _state.update {
                    it.copy(
                        conferences = index.conferences,
                        conferenceEntries = index.entries,
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

    private fun launchWorkspaceAction(selectResult: Boolean, action: suspend () -> Conference) {
        val expectedSelectedConferenceId = selectedIdOrNull()
        scope.launch {
            _state.update { it.copy(loading = true, conferenceActionError = null, transferNotice = null) }
            try {
                val conference = action()
                val index = loadConferenceIndex()
                var selectedResult = false
                _state.update { current ->
                    selectedResult = selectResult && current.selectedConferenceId == expectedSelectedConferenceId
                    current.copy(
                        conferences = index.conferences,
                        conferenceEntries = index.entries,
                        selectedConferenceId = if (selectedResult) conference.id.value else current.selectedConferenceId,
                        selectedConference = if (selectedResult) conference else current.selectedConference,
                        loading = false,
                        conferenceActionError = null,
                    )
                }
                if (selectedResult) lastSelectedConferenceStore.save(conference.id.value)
            } catch (exception: Exception) {
                val index = runCatching { loadConferenceIndex() }.getOrNull()
                _state.update {
                    it.copy(
                        conferences = index?.conferences ?: it.conferences,
                        conferenceEntries = index?.entries ?: it.conferenceEntries,
                        loading = false,
                        conferenceActionError = exception.readableMessage(),
                    )
                }
            }
        }
    }

    private fun launchWorkspaceRemoval(conferenceId: String, action: suspend () -> Unit) {
        scope.launch {
            _state.update { it.copy(loading = true, conferenceActionError = null) }
            try {
                action()
                val index = loadConferenceIndex()
                val previousSelected = _state.value.selectedConferenceId
                val nextId = if (previousSelected == conferenceId) index.entries.firstOrNull()?.id else previousSelected
                val nextConference = nextId?.let { repository.getConference(ConferenceId(it)) }
                lastSelectedConferenceStore.save(nextId)
                _state.update {
                    it.copy(
                        conferences = index.conferences,
                        conferenceEntries = index.entries,
                        selectedConferenceId = nextId,
                        selectedConference = nextConference,
                        loading = false,
                    )
                }
            } catch (exception: Exception) {
                _state.update { it.copy(loading = false, conferenceActionError = exception.readableMessage()) }
            }
        }
    }

    private suspend fun loadConferenceNow(conferenceId: String) {
        val conference = repository.getConference(ConferenceId(conferenceId))
        val entries = workspace?.listEntries()
        updateConferenceState(conferenceId) {
            it.copy(
                selectedConference = conference,
                conferenceEntries = entries ?: it.conferenceEntries,
            )
        }
    }

    private suspend fun loadConferenceIndex(): ConferenceIndex {
        val conferences = repository.listConferences()
        val entries = workspace?.listEntries() ?: conferences.mapIndexed { index, conference ->
            ConferenceListItem(
                id = conference.id.value,
                name = conference.name,
                displayOrder = index,
                storageKind = ConferenceListStorageKind.Local,
                availability = ConferenceListAvailability.Available,
            )
        }
        return ConferenceIndex(conferences, entries.sortedBy { it.displayOrder })
    }

    private fun selectedIdOrNull(): String? = _state.value.selectedConferenceId

    private suspend fun exportMarkdownSnapshots(
        trigger: MarkdownSafetyExportTrigger,
        showNotice: Boolean,
    ) {
        try {
            val unavailableCount = _state.value.conferenceEntries.count { entry ->
                entry.storageKind == ConferenceListStorageKind.Synchronized &&
                    entry.availability != ConferenceListAvailability.Available
            }
            val fullConferences = repository.listConferences()
                .mapNotNull { conference -> repository.getConference(conference.id) }
            val result = markdownSafetyExport.exportAll(fullConferences, trigger)
            if (showNotice && (result.hasFailures || unavailableCount > 0)) {
                val message = buildList {
                    if (result.hasFailures) {
                        val count = result.failures.size
                        add(
                            if (count == 1) {
                                "Textual safety export failed for 1 conference."
                            } else {
                                "Textual safety export failed for $count conferences."
                            },
                        )
                    }
                    if (unavailableCount > 0) {
                        add(
                            if (unavailableCount == 1) {
                                "Skipped 1 unavailable synchronized conference."
                            } else {
                                "Skipped $unavailableCount unavailable synchronized conferences."
                            },
                        )
                    }
                }.joinToString(" ")
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
        val duplicate = _state.value.conferenceEntries.any { conference ->
            conference.id != currentConferenceId && conference.name.trim().equals(trimmed, ignoreCase = true)
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
    val conferenceEntries: List<ConferenceListItem> = emptyList(),
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
    val pendingWorkbookUpdate: PendingWorkbookUpdate? = null,
    val pendingPreferenceCompletion: PendingPreferenceCompletion? = null,
    val pendingSynchronizedImportPath: Path? = null,
    val pendingConferenceReplace: PendingConferenceReplace? = null,
)

data class PendingPreferenceCompletion(
    val conferenceId: String,
    val preferenceRanks: List<Int>,
    val missingRanksByDelegateId: Map<String, Set<Int>>,
    val error: String? = null,
)

data class ConferenceListItem(
    val id: String,
    val name: String,
    val displayOrder: Int,
    val storageKind: ConferenceListStorageKind,
    val availability: ConferenceListAvailability,
    val path: String? = null,
    val lastLoadedAtEpochMillis: Long? = null,
)

enum class ConferenceListStorageKind {
    Local,
    Synchronized,
}

enum class ConferenceListAvailability {
    Available,
    Missing,
    Invalid,
    NewerVersion,
    Inaccessible,
}

data class PendingConferenceReplace(
    val conferenceId: String,
    val conferenceName: String,
    val sourcePath: Path,
    val synchronizedTarget: Boolean,
    val synchronizedSource: Boolean,
)

data class ConferenceFileInspection(
    val kind: ConferenceFileKind,
    val message: String? = null,
)

enum class ConferenceFileKind {
    ApplicationDatabase,
    ConferenceTransfer,
    SynchronizedConference,
    LegacyTransfer,
    Invalid,
}

private data class ConferenceIndex(
    val conferences: List<Conference>,
    val entries: List<ConferenceListItem>,
)

data class PendingWorkbookUpdate(
    val conferenceId: String,
    val path: Path,
    val candidate: MymunUpdateFilenameCandidate,
    val expectedParts: List<String>,
    val missingParts: List<String>,
    val unexpectedParts: List<String>,
    val emptyCandidate: Boolean,
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
