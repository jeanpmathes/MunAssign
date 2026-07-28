@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package org.munika.assignment.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.PaymentTier
import org.munika.assignment.domain.model.QuestionConfiguration
import org.munika.assignment.ui.screen.dialog.IssueManagerPanel
import org.munika.assignment.ui.screen.dialog.QuestionConfigurationDialog
import org.munika.assignment.ui.screen.dialog.ReservationEditorDialog
import org.munika.assignment.ui.screen.dialog.ReservationManagerDialog
import org.munika.assignment.ui.screen.component.AssignmentBoard
import org.munika.assignment.ui.screen.component.DelegateListPanel
import org.munika.assignment.ui.screen.component.DelegateSidePanel
import org.munika.assignment.ui.screen.component.DelegationOverviewPanel
import org.munika.assignment.ui.screen.component.containerColor
import org.munika.assignment.ui.screen.component.contentColor
import org.munika.assignment.ui.viewmodel.DelegateListControlsUiState
import org.munika.assignment.ui.viewmodel.DelegateListPreset
import org.munika.assignment.ui.viewmodel.DelegateStatusTone
import org.munika.assignment.ui.viewmodel.ConferenceListAvailability
import org.munika.assignment.ui.viewmodel.ConferenceListItem
import org.munika.assignment.ui.viewmodel.ConferenceListStorageKind
import org.munika.assignment.ui.viewmodel.MainUiState
import org.munika.assignment.ui.viewmodel.PendingWorkbookUpdate
import org.munika.assignment.ui.viewmodel.ReservationSummaryUiState
import org.munika.assignment.ui.viewmodel.toIssueManagerUiState
import org.munika.assignment.ui.viewmodel.toQuestionConfigurationListUiState
import org.munika.assignment.ui.viewmodel.toDelegateListUiState
import org.munika.assignment.ui.viewmodel.toDelegateDetailUiState
import org.munika.assignment.ui.viewmodel.toDelegationOverviewUiState
import org.munika.assignment.ui.viewmodel.toAssignmentBoardUiState
import org.munika.assignment.ui.viewmodel.toReservationManagerUiState

enum class WorkbookDropFeedback {
    Accepted,
    Rejected,
}

@Composable
fun MainScreen(
    state: MainUiState,
    workbookDropFeedback: WorkbookDropFeedback? = null,
    onSelectConference: (String) -> Unit,
    onCreateConference: (String) -> Boolean,
    onCreateSynchronizedConference: (String) -> Unit,
    onMoveConferenceToSynchronized: (String, String) -> Unit,
    onConnectSynchronizedConference: () -> Unit,
    onLocateSynchronizedConference: (String) -> Unit,
    onReloadSynchronizedConference: (String) -> Unit,
    onUnlinkSynchronizedConference: (String) -> Unit,
    onDeleteSynchronizedConferenceFile: (String) -> Unit,
    onRenameConference: (String, String) -> Boolean,
    onDeleteConference: (String) -> Unit,
    onReorderConferences: (List<String>) -> Boolean,
    onSetCommitteeSlotsPerPosition: (String, Int) -> Unit,
    onSetCommitteeMode: (String, CommitteeMode) -> Unit,
    onSetCommitteePaymentTier: (String, PaymentTier) -> Unit,
    onSetPositionMappedCommittee: (String, String?) -> Unit,
    onAssignDelegateToSlot: (slotId: String, delegateId: String, moveExistingPending: Boolean) -> Unit,
    onClearPendingSlot: (slotId: String) -> Unit,
    onOpenDelegateList: () -> Unit,
    onCloseDelegateList: () -> Unit,
    onOpenNoticeManager: () -> Unit,
    onOpenDelegateNoticeManager: (delegateId: String) -> Unit,
    onCloseNoticeManager: () -> Unit,
    onOpenDelegationOverview: () -> Unit = {},
    onOpenDelegationOverviewFor: (delegationId: String) -> Unit = {},
    onCloseDelegationOverview: () -> Unit = {},
    onSetDelegationHighSchoolStatus: (delegationId: String, isHighSchoolDelegation: Boolean) -> Unit = { _, _ -> },
    onOpenDelegationReservationEditor: (delegationId: String) -> Unit = {},
    onCloseDelegationReservationEditor: () -> Unit = {},
    onOpenReservationManager: (delegationId: String?) -> Unit = {},
    onCloseReservationManager: () -> Unit = {},
    onCreateReservation: (
        conferenceId: String,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: String?,
    ) -> Unit = { _, _, _, _, _ -> },
    onCreateDelegationReservation: (
        conferenceId: String,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: String?,
    ) -> Unit = { _, _, _, _, _ -> },
    onUpdateReservation: (
        conferenceId: String,
        reservationId: String,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: String?,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteReservation: (conferenceId: String, reservationId: String) -> Unit = { _, _ -> },
    onAssignDelegateReservation: (
        conferenceId: String,
        delegateId: String,
        reservationId: String,
    ) -> Unit = { _, _, _ -> },
    onRemoveDelegateReservation: (conferenceId: String, delegateId: String) -> Unit = { _, _ -> },
    onResolveAnswerIssue: (delegateId: String, questionId: String?, issueKey: String, justification: String?) -> Unit,
    onSetDelegatePaymentTier: (delegateId: String, paymentTier: PaymentTier) -> Unit,
    onUpdateAnswerIssueResolutionJustification: (resolutionId: String, justification: String?) -> Unit,
    onUnresolveAnswerIssue: (resolutionId: String) -> Unit,
    onOpenQuestionConfiguration: () -> Unit,
    onCloseQuestionConfiguration: () -> Unit,
    onUpdateQuestionConfigurations: (List<QuestionConfiguration>) -> Unit,
    onUpdateDelegateListControls: (DelegateListControlsUiState) -> Unit,
    onApplyDelegateListPreset: (DelegateListPreset) -> Unit,
    onOpenDelegateDetail: (String) -> Unit,
    onCloseDelegateDetail: () -> Unit,
    onNavigateToBoardPosition: (committeeId: String, positionId: String) -> Unit,
    onDeactivateDelegate: (String) -> Unit,
    onReactivateDelegate: (String) -> Unit,
    onAddDelegateComment: (delegateId: String, body: String) -> Unit,
    onUpdateDelegateComment: (commentId: String, body: String) -> Unit,
    onDeleteDelegateComment: (commentId: String) -> Unit,
    onOpenWorkbook: () -> Unit,
    onConfirmPendingWorkbookUpdate: () -> Unit,
    onCancelPendingWorkbookUpdate: () -> Unit,
    onDeleteMymunUpdateFilenameSignature: (conferenceId: String, signatureId: String) -> Unit,
    onExportConference: (String, String) -> Unit,
    onImportConferenceAsNew: () -> Unit,
    onReplaceConference: (String, String) -> Unit,
    onConfirmPendingConferenceReplace: () -> Unit,
    onCancelPendingConferenceReplace: () -> Unit,
    onConfirmSynchronizedImport: () -> Unit,
    onConnectPendingSynchronizedImport: () -> Unit,
    onCancelSynchronizedImport: () -> Unit,
    onOpenMarkdownExportFolder: () -> Unit,
    onCopyStatusImage: (Conference) -> Result<Unit>,
    onClearConferenceActionError: () -> Unit,
    onClearBoardActionError: () -> Unit,
    onClearReservationActionError: () -> Unit = {},
) {
    var createDialogOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ConferenceListItem?>(null) }
    var managementOpen by remember { mutableStateOf(false) }
    val selectedConference = state.selectedConference
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    LaunchedEffect(state.workbookUpdateNotice?.id) {
        val notice = state.workbookUpdateNotice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice.message)
    }

    LaunchedEffect(state.transferNotice?.id) {
        val notice = state.transferNotice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice.message)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    state = state,
                    onSelectConference = onSelectConference,
                    onOpenCreate = {
                        onClearConferenceActionError()
                        createDialogOpen = true
                    },
                    onOpenRename = { conference ->
                        onClearConferenceActionError()
                        renameTarget = conference
                    },
                    onOpenManagement = {
                        onClearConferenceActionError()
                        managementOpen = true
                    },
                    onOpenDelegateList = onOpenDelegateList,
                    onOpenNoticeManager = onOpenNoticeManager,
                    onOpenDelegationOverview = onOpenDelegationOverview,
                    onOpenReservationManager = { onOpenReservationManager(null) },
                    onOpenQuestionConfiguration = onOpenQuestionConfiguration,
                    onOpenWorkbook = onOpenWorkbook,
                    onOpenMarkdownExportFolder = onOpenMarkdownExportFolder,
                    onCopyStatusImage = { conference ->
                        val message = onCopyStatusImage(conference).fold(
                            onSuccess = { "Status image copied to clipboard." },
                            onFailure = { error ->
                                "Could not copy status image: ${error.message ?: error::class.simpleName.orEmpty()}"
                            },
                        )
                        snackbarScope.launch { snackbarHostState.showSnackbar(message) }
                    },
                )
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    when {
                        state.loading && state.conferenceEntries.isEmpty() -> LoadingState()
                        state.conferenceEntries.isEmpty() -> NoConferenceState(
                            onCreate = {
                                onClearConferenceActionError()
                                createDialogOpen = true
                            },
                        )
                        selectedConference == null -> UnavailableConferenceState(
                            entry = state.conferenceEntries.firstOrNull { it.id == state.selectedConferenceId },
                            onReload = { state.selectedConferenceId?.let(onReloadSynchronizedConference) },
                            onLocate = { state.selectedConferenceId?.let(onLocateSynchronizedConference) },
                            onUnlink = { state.selectedConferenceId?.let(onUnlinkSynchronizedConference) },
                        )
                        selectedConference.committees.isEmpty() -> EmptyConferenceWorkspace()
                        else -> ConferenceWorkspace(
                            conference = selectedConference,
                            loading = state.loading,
                            boardActionError = state.boardActionError,
                            boardScrollRequest = state.boardScrollRequest,
                            selectedDelegateId = state.selectedDelegateId,
                            delegateListOpen = state.delegateListOpen,
                            delegationOverviewOpen = state.delegationOverviewOpen,
                            delegationOverviewTargetId = state.delegationOverviewTargetId,
                            noticeManagerOpen = state.noticeManagerOpen,
                            issueManager = selectedConference.toIssueManagerUiState(
                                delegateId = state.noticeManagerDelegateId,
                            ),
                            reservationOptions = selectedConference.toReservationManagerUiState().reservations,
                            delegateReservationActionError = state.delegateReservationActionError,
                            delegateListControls = state.delegateListControls,
                            onSetCommitteeSlotsPerPosition = onSetCommitteeSlotsPerPosition,
                            onSetCommitteeMode = onSetCommitteeMode,
                            onSetCommitteePaymentTier = onSetCommitteePaymentTier,
                            onSetPositionMappedCommittee = onSetPositionMappedCommittee,
                            onAssignDelegateToSlot = onAssignDelegateToSlot,
                            onClearPendingSlot = onClearPendingSlot,
                            onCloseDelegateList = onCloseDelegateList,
                            onCloseDelegationOverview = onCloseDelegationOverview,
                            onSetDelegationHighSchoolStatus = onSetDelegationHighSchoolStatus,
                            onOpenDelegationReservationEditor = onOpenDelegationReservationEditor,
                            onOpenReservationManager = onOpenReservationManager,
                            onUpdateDelegateListControls = onUpdateDelegateListControls,
                            onApplyDelegateListPreset = onApplyDelegateListPreset,
                            onOpenDelegateDetail = onOpenDelegateDetail,
                            onCloseDelegateDetail = onCloseDelegateDetail,
                            onNavigateToBoardPosition = onNavigateToBoardPosition,
                            onDeactivateDelegate = onDeactivateDelegate,
                            onReactivateDelegate = onReactivateDelegate,
                            onAddDelegateComment = onAddDelegateComment,
                            onUpdateDelegateComment = onUpdateDelegateComment,
                            onDeleteDelegateComment = onDeleteDelegateComment,
                            onAssignDelegateReservation = onAssignDelegateReservation,
                            onRemoveDelegateReservation = onRemoveDelegateReservation,
                            onOpenDelegateNoticeManager = onOpenDelegateNoticeManager,
                            onOpenNoticeManager = onOpenNoticeManager,
                            onCloseNoticeManager = onCloseNoticeManager,
                            onResolveAnswerIssue = onResolveAnswerIssue,
                            onSetDelegatePaymentTier = onSetDelegatePaymentTier,
                            onUpdateAnswerIssueResolutionJustification = onUpdateAnswerIssueResolutionJustification,
                            onUnresolveAnswerIssue = onUnresolveAnswerIssue,
                            onClearBoardActionError = onClearBoardActionError,
                        )
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            )
            workbookDropFeedback?.let { feedback ->
                WorkbookDropOverlay(
                    feedback = feedback,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }

    if (createDialogOpen) {
        ConferenceNameDialog(
            title = "Create conference",
            confirmLabel = "Create",
            initialName = "",
            error = state.conferenceActionError,
            onDismiss = {
                createDialogOpen = false
                onClearConferenceActionError()
            },
            onConfirm = { name ->
                if (onCreateConference(name)) {
                    createDialogOpen = false
                }
            },
        )
    }

    renameTarget?.let { conference ->
        ConferenceNameDialog(
            title = "Rename conference",
            confirmLabel = "Rename",
            initialName = conference.name,
            error = state.conferenceActionError,
            onDismiss = {
                renameTarget = null
                onClearConferenceActionError()
            },
            onConfirm = { name ->
                if (onRenameConference(conference.id, name)) {
                    renameTarget = null
                }
            },
        )
    }

    if (managementOpen) {
        ConferenceManagementDialog(
            state = state,
            onDismiss = {
                managementOpen = false
                onClearConferenceActionError()
            },
            onCreateConference = onCreateConference,
            onCreateSynchronizedConference = onCreateSynchronizedConference,
            onMoveConferenceToSynchronized = onMoveConferenceToSynchronized,
            onConnectSynchronizedConference = onConnectSynchronizedConference,
            onLocateSynchronizedConference = onLocateSynchronizedConference,
            onReloadSynchronizedConference = onReloadSynchronizedConference,
            onUnlinkSynchronizedConference = onUnlinkSynchronizedConference,
            onDeleteSynchronizedConferenceFile = onDeleteSynchronizedConferenceFile,
            onRenameConference = onRenameConference,
            onDeleteConference = onDeleteConference,
            onReorderConferences = onReorderConferences,
            onExportConference = onExportConference,
            onImportConferenceAsNew = onImportConferenceAsNew,
            onReplaceConference = onReplaceConference,
            onDeleteMymunUpdateFilenameSignature = onDeleteMymunUpdateFilenameSignature,
            onClearConferenceActionError = onClearConferenceActionError,
        )
    }

    state.pendingWorkbookUpdate?.let { pending ->
        WorkbookFilenameMismatchDialog(
            pending = pending,
            onDismiss = onCancelPendingWorkbookUpdate,
            onConfirm = onConfirmPendingWorkbookUpdate,
        )
    }

    state.pendingConferenceReplace?.let { pending ->
        ReplaceConferenceDialog(
            conferenceName = pending.conferenceName,
            synchronizedTarget = pending.synchronizedTarget,
            synchronizedSource = pending.synchronizedSource,
            sourceName = pending.sourcePath.fileName.toString(),
            onDismiss = onCancelPendingConferenceReplace,
            onConfirm = onConfirmPendingConferenceReplace,
        )
    }

    state.pendingSynchronizedImportPath?.let { path ->
        SynchronizedImportDialog(
            sourceName = path.fileName.toString(),
            onDismiss = onCancelSynchronizedImport,
            onConnect = onConnectPendingSynchronizedImport,
            onImport = onConfirmSynchronizedImport,
        )
    }

    if (state.questionConfigurationOpen && selectedConference != null) {
        QuestionConfigurationDialog(
            conference = selectedConference,
            list = selectedConference.toQuestionConfigurationListUiState(),
            error = state.questionConfigurationError,
            onDismiss = onCloseQuestionConfiguration,
            onSave = onUpdateQuestionConfigurations,
        )
    }

    state.delegationReservationDraft?.let { draft ->
        if (selectedConference != null) {
            ReservationEditorDialog(
                title = "Create reservation",
                confirmLabel = "Create",
                delegations = selectedConference.toReservationManagerUiState().delegations,
                initialName = draft.name,
                initialHighSchoolCount = draft.highSchoolCount,
                initialUniversityCount = draft.universityCount,
                initialDelegationId = draft.delegationId,
                error = state.delegationReservationActionError,
                submitting = state.loading,
                onDismiss = onCloseDelegationReservationEditor,
                onSubmit = { name, highSchoolCount, universityCount, delegationId ->
                    onCreateDelegationReservation(
                        selectedConference.id.value,
                        name,
                        highSchoolCount,
                        universityCount,
                        delegationId,
                    )
                },
            )
        }
    }

    if (state.reservationManagerOpen && selectedConference != null) {
        ReservationManagerDialog(
            manager = selectedConference.toReservationManagerUiState(),
            initialDelegationId = state.reservationManagerDelegationId,
            reservationActionError = state.reservationActionError,
            onCreateReservation = { name, highSchoolCount, universityCount, delegationId ->
                onCreateReservation(selectedConference.id.value, name, highSchoolCount, universityCount, delegationId)
            },
            onUpdateReservation = { reservationId, name, highSchoolCount, universityCount, delegationId ->
                onUpdateReservation(selectedConference.id.value, reservationId, name, highSchoolCount, universityCount, delegationId)
            },
            onDeleteReservation = { reservationId ->
                onDeleteReservation(selectedConference.id.value, reservationId)
            },
            onOpenDelegation = onOpenDelegationOverviewFor,
            onDismiss = {
                onCloseReservationManager()
                onClearReservationActionError()
            },
        )
    }
}

@Composable
private fun WorkbookDropOverlay(
    feedback: WorkbookDropFeedback,
    modifier: Modifier = Modifier,
) {
    val accepted = feedback == WorkbookDropFeedback.Accepted
    val background = if (accepted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
    }
    val borderColor = if (accepted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val containerColor = if (accepted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (accepted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    val message = if (accepted) {
        "Drop to update this conference"
    } else {
        "Drop one Excel file"
    }
    Box(
        modifier = modifier
            .background(background)
            .border(2.dp, borderColor),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(message, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TopBar(
    state: MainUiState,
    onSelectConference: (String) -> Unit,
    onOpenCreate: () -> Unit,
    onOpenRename: (ConferenceListItem) -> Unit,
    onOpenManagement: () -> Unit,
    onOpenDelegateList: () -> Unit,
    onOpenNoticeManager: () -> Unit,
    onOpenDelegationOverview: () -> Unit,
    onOpenReservationManager: () -> Unit,
    onOpenQuestionConfiguration: () -> Unit,
    onOpenWorkbook: (() -> Unit)? = null,
    onOpenMarkdownExportFolder: () -> Unit,
    onCopyStatusImage: (Conference) -> Unit,
) {
    Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 1600.dp
            Column {
                TopBarActionRow(
                    state = state,
                    compact = compact,
                    onSelectConference = onSelectConference,
                    onOpenCreate = onOpenCreate,
                    onOpenRename = onOpenRename,
                    onOpenManagement = onOpenManagement,
                    onOpenDelegateList = onOpenDelegateList,
                    onOpenNoticeManager = onOpenNoticeManager,
                    onOpenDelegationOverview = onOpenDelegationOverview,
                    onOpenReservationManager = onOpenReservationManager,
                    onOpenQuestionConfiguration = onOpenQuestionConfiguration,
                    onOpenWorkbook = onOpenWorkbook,
                    onOpenMarkdownExportFolder = onOpenMarkdownExportFolder,
                    onCopyStatusImage = onCopyStatusImage,
                )
                if (compact) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TopBarStatus(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBarActionRow(
    state: MainUiState,
    compact: Boolean,
    onSelectConference: (String) -> Unit,
    onOpenCreate: () -> Unit,
    onOpenRename: (ConferenceListItem) -> Unit,
    onOpenManagement: () -> Unit,
    onOpenDelegateList: () -> Unit,
    onOpenNoticeManager: () -> Unit,
    onOpenDelegationOverview: () -> Unit,
    onOpenReservationManager: () -> Unit,
    onOpenQuestionConfiguration: () -> Unit,
    onOpenWorkbook: (() -> Unit)?,
    onOpenMarkdownExportFolder: () -> Unit,
    onCopyStatusImage: (Conference) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(if (compact) 64.dp else 72.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConferenceDropdown(
                conferences = state.conferenceEntries,
                selectedConferenceId = state.selectedConferenceId,
                enabled = !state.loading,
                onSelectConference = onSelectConference,
                onOpenCreate = onOpenCreate,
                onOpenRename = onOpenRename,
            )
            TooltipIconButton(
                tooltip = "Manage conferences",
                icon = Icons.AutoMirrored.Filled.EventNote,
                onClick = onOpenManagement,
                enabled = !state.loading,
            )
            TooltipIconButton(
                tooltip = "Open textual exports folder",
                icon = Icons.Filled.FolderOpen,
                onClick = onOpenMarkdownExportFolder,
                enabled = !state.loading,
            )
            TooltipIconButton(
                tooltip = "Copy conference status image",
                icon = Icons.Filled.Image,
                onClick = { state.selectedConference?.let(onCopyStatusImage) },
                enabled = !state.loading && state.selectedConference != null,
            )
        }

        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (compact) {
                TooltipIconButton(
                    tooltip = "Update conference from a MyMUN workbook",
                    icon = Icons.Filled.UploadFile,
                    onClick = { onOpenWorkbook?.invoke() },
                    enabled = !state.loading && state.selectedConferenceId != null && onOpenWorkbook != null,
                )
                TooltipIconButton(
                    tooltip = "Configure application questions",
                    icon = Icons.Filled.Tune,
                    onClick = onOpenQuestionConfiguration,
                    enabled = !state.loading && state.selectedConference?.questions?.isNotEmpty() == true,
                )
            } else {
                WithTooltip("Choose the online Excel file for this conference update.") {
                    Button(
                        onClick = { onOpenWorkbook?.invoke() },
                        enabled = !state.loading && state.selectedConferenceId != null && onOpenWorkbook != null,
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Update")
                    }
                }
                WithTooltip("Choose how application questions are shown and checked.") {
                    OutlinedButton(
                        onClick = onOpenQuestionConfiguration,
                        enabled = !state.loading && state.selectedConference?.questions?.isNotEmpty() == true,
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Questions")
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val issueManager = state.selectedConference?.toIssueManagerUiState()
            val openIssueCount = (issueManager?.openWarningCount ?: 0) + (issueManager?.openNoticeCount ?: 0)
            NoticeIconButton(
                tooltip = "Open issues ($openIssueCount unresolved)",
                warningCount = issueManager?.openWarningCount ?: 0,
                noticeCount = issueManager?.openNoticeCount ?: 0,
                onClick = onOpenNoticeManager,
                enabled = !state.loading && state.selectedConference != null,
            )
            TooltipIconButton(
                tooltip = "Open delegate list",
                icon = Icons.Filled.People,
                onClick = onOpenDelegateList,
                enabled = !state.loading && state.selectedConference != null,
            )
            val unreviewedDelegationCount = state.selectedConference?.delegations?.count { it.needsReview } ?: 0
            DelegationIconButton(
                unreviewedCount = unreviewedDelegationCount,
                onClick = onOpenDelegationOverview,
                enabled = !state.loading && state.selectedConference != null,
            )
            TooltipIconButton(
                tooltip = "Open reservation manager",
                icon = Icons.Filled.ConfirmationNumber,
                onClick = onOpenReservationManager,
                enabled = !state.loading && state.selectedConference != null,
            )
            if (!compact) {
                Spacer(Modifier.width(8.dp))
                TopBarStatus(state)
            }
        }
    }
}

@Composable
private fun TopBarStatus(state: MainUiState) {
    if (state.loading) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("Updating", style = MaterialTheme.typography.bodyMedium)
    } else {
        state.selectedConference?.let { ConferenceStats(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConferenceDropdown(
    conferences: List<ConferenceListItem>,
    selectedConferenceId: String?,
    enabled: Boolean,
    onSelectConference: (String) -> Unit,
    onOpenCreate: () -> Unit,
    onOpenRename: (ConferenceListItem) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = conferences.firstOrNull { it.id == selectedConferenceId }?.name ?: "Select conference"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text("Conference") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).width(200.dp),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
        ) {
            conferences.forEach { conference ->
                DropdownMenuItem(
                    text = {
                        Text(conference.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    onClick = {
                        expanded = false
                        onSelectConference(conference.id)
                    },
                    leadingIcon = if (conference.storageKind == ConferenceListStorageKind.Synchronized) {
                        {
                            Icon(
                                if (conference.availability == ConferenceListAvailability.Available) {
                                    Icons.Filled.Cloud
                                } else {
                                    Icons.Filled.CloudOff
                                },
                                contentDescription = null,
                            )
                        }
                    } else {
                        null
                    },
                    trailingIcon = {
                        TooltipIconButton(
                            tooltip = "Rename ${conference.name}",
                            icon = Icons.Filled.Edit,
                            onClick = {
                                expanded = false
                                onOpenRename(conference)
                            },
                            enabled = conference.availability == ConferenceListAvailability.Available,
                            modifier = Modifier.size(32.dp),
                        )
                    },
                )
            }
            if (conferences.isNotEmpty()) {
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text("Add conference") },
                onClick = {
                    expanded = false
                    onOpenCreate()
                },
                trailingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun ConferenceStats(conference: Conference) {
    val acceptedApplications = conference.delegates.filter { it.hasActionableApplicationStatus() }
    val submittedApplications = conference.delegates.count { it.submittedAt != null && !it.hasActionableApplicationStatus() }
    val paidApplications = acceptedApplications.count { it.paymentStatus == PaymentStatus.Paid }
    val unpaidApplications = acceptedApplications.count { it.paymentStatus == PaymentStatus.Unpaid }
    val acceptanceCapacity = conference.toAcceptanceCapacityUiState()
    val activePaidDelegates = conference.delegates.count { it.active && it.paymentStatus == PaymentStatus.Paid }
    val assignedActivePaidDelegates = conference.delegates.count {
        it.active && it.paymentStatus == PaymentStatus.Paid && it.currentSlot != null
    }
    val pendingActivePaidDelegates = conference.delegates.count {
        it.active && it.paymentStatus == PaymentStatus.Paid && it.currentSlot?.isPending == true
    }
    val totalSlots = conference.slots.size
    val filledSlots = conference.pendingSlots.size + conference.assignedSlots.size

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StatGroup(
            label = "Applications",
            value = "${acceptedApplications.size} accepted · $paidApplications/$unpaidApplications paid/unpaid · $submittedApplications submitted",
            tooltip = "Accepted paid delegates can be assigned. Unpaid accepted delegates are counted separately because they are not confirmed for assignment yet. Submitted delegates still need an online acceptance decision.",
        )
        StatGroup(
            label = "Delegates",
            value = "$assignedActivePaidDelegates / $activePaidDelegates assigned · $pendingActivePaidDelegates pending",
            tooltip = "Shows how many usable delegates already have a pending or confirmed assignment. Pending assignments still need confirmation in a later update.",
        )
        StatGroup(
            label = "Slots",
            value = "$filledSlots / $totalSlots filled",
            tooltip = "Shows filled assignment places out of all available places. Filled includes pending and confirmed assignments.",
        )
        CapacityStatGroup(acceptanceCapacity)
    }
}

internal data class AcceptanceCapacityUiState(
    val highSchoolCount: Int,
    val universityCount: Int,
)

internal fun Conference.toAcceptanceCapacityUiState(): AcceptanceCapacityUiState {
    val highSchoolSlots = positions
        .filter { it.effectiveCommitteeMode == CommitteeMode.HighSchool }
        .sumOf { it.slots.size }
    val universitySlots = positions
        .filter { it.effectiveCommitteeMode == CommitteeMode.University }
        .sumOf { it.slots.size }
    val acceptedActiveDelegates = delegates.filter {
        it.active && it.hasActionableApplicationStatus()
    }
    val acceptedHighSchoolDelegates = acceptedActiveDelegates.count { it.isHighSchooler }
    val acceptedUniversityDelegates = acceptedActiveDelegates.count { !it.isHighSchooler }

    return AcceptanceCapacityUiState(
        highSchoolCount = highSchoolSlots - acceptedHighSchoolDelegates - remainingHighSchoolReservationCount,
        universityCount = universitySlots - acceptedUniversityDelegates - remainingUniversityReservationCount,
    )
}

@Composable
private fun StatGroup(label: String, value: String, tooltip: String) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CapacityStatGroup(capacity: AcceptanceCapacityUiState) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text("Remaining high-school and university places after accepted delegates and reservation holds are counted.")
            }
        },
        state = rememberTooltipState(),
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 108.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "Capacity",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                CapacityValue("${capacity.highSchoolCount} HS", capacity.highSchoolCount)
                Text(
                    "·",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                CapacityValue("${capacity.universityCount} Uni", capacity.universityCount)
            }
        }
    }
}

@Composable
private fun CapacityValue(text: String, value: Int) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (value < 0) CapacityNegativeColor else CapacityPositiveColor,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun TooltipIconButton(
    tooltip: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(icon, contentDescription = tooltip, tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
        }
    }
}

@Composable
private fun NoticeIconButton(
    tooltip: String,
    warningCount: Int,
    noticeCount: Int,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            IconButton(onClick = onClick, enabled = enabled) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = tooltip,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                )
            }
            NoticeBadgeRow(
                warningCount = warningCount,
                noticeCount = noticeCount,
                modifier = Modifier.align(Alignment.BottomCenter).padding(top = 27.dp),
            )
        }
    }
}

@Composable
private fun DelegationIconButton(
    unreviewedCount: Int,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val tooltip = if (unreviewedCount > 0) {
        "Open delegation overview ($unreviewedCount new)"
    } else {
        "Open delegation overview"
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            IconButton(onClick = onClick, enabled = enabled) {
                Icon(
                    Icons.Filled.AccountTree,
                    contentDescription = tooltip,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
                )
            }
            if (unreviewedCount > 0) {
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(top = 27.dp)) {
                    NoticeCountBadge(
                        count = unreviewedCount,
                        tone = DelegateStatusTone.Yellow,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeBadgeRow(warningCount: Int, noticeCount: Int, modifier: Modifier = Modifier) {
    if (warningCount == 0 && noticeCount == 0) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (warningCount > 0) {
            NoticeCountBadge(warningCount, DelegateStatusTone.Yellow)
        }
        if (noticeCount > 0) {
            NoticeCountBadge(noticeCount, DelegateStatusTone.Notice)
        }
    }
}

@Composable
private fun NoticeCountBadge(count: Int, tone: DelegateStatusTone) {
    Surface(
        color = tone.containerColor(),
        contentColor = tone.contentColor(),
        shape = RoundedCornerShape(5.dp),
        shadowElevation = 1.dp,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = 14.dp).padding(horizontal = 3.dp, vertical = 1.dp),
        )
    }
}

private val CapacityPositiveColor = Color(0xFF2E7D32)
private val CapacityNegativeColor = Color(0xFFC62828)

@Composable
private fun WithTooltip(tooltip: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        content()
    }
}

@Composable
private fun NoConferenceState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No conferences yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        WithTooltip("Create the first conference.") {
            Button(onClick = onCreate) {
                Text("Create conference")
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyConferenceWorkspace() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No update has been loaded yet.", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ConferenceWorkspace(
    conference: Conference,
    loading: Boolean,
    boardActionError: String?,
    boardScrollRequest: org.munika.assignment.ui.viewmodel.BoardScrollRequest?,
    selectedDelegateId: String?,
    delegateListOpen: Boolean,
    delegationOverviewOpen: Boolean,
    delegationOverviewTargetId: String?,
    noticeManagerOpen: Boolean,
    issueManager: org.munika.assignment.ui.viewmodel.IssueManagerUiState,
    reservationOptions: List<ReservationSummaryUiState>,
    delegateReservationActionError: String?,
    delegateListControls: DelegateListControlsUiState,
    onSetCommitteeSlotsPerPosition: (String, Int) -> Unit,
    onSetCommitteeMode: (String, CommitteeMode) -> Unit,
    onSetCommitteePaymentTier: (String, PaymentTier) -> Unit,
    onSetPositionMappedCommittee: (String, String?) -> Unit,
    onAssignDelegateToSlot: (slotId: String, delegateId: String, moveExistingPending: Boolean) -> Unit,
    onClearPendingSlot: (slotId: String) -> Unit,
    onCloseDelegateList: () -> Unit,
    onCloseDelegationOverview: () -> Unit,
    onSetDelegationHighSchoolStatus: (String, Boolean) -> Unit,
    onOpenDelegationReservationEditor: (String) -> Unit,
    onOpenReservationManager: (delegationId: String?) -> Unit,
    onUpdateDelegateListControls: (DelegateListControlsUiState) -> Unit,
    onApplyDelegateListPreset: (DelegateListPreset) -> Unit,
    onOpenDelegateDetail: (String) -> Unit,
    onCloseDelegateDetail: () -> Unit,
    onNavigateToBoardPosition: (committeeId: String, positionId: String) -> Unit,
    onDeactivateDelegate: (String) -> Unit,
    onReactivateDelegate: (String) -> Unit,
    onAddDelegateComment: (delegateId: String, body: String) -> Unit,
    onUpdateDelegateComment: (commentId: String, body: String) -> Unit,
    onDeleteDelegateComment: (commentId: String) -> Unit,
    onAssignDelegateReservation: (conferenceId: String, delegateId: String, reservationId: String) -> Unit,
    onRemoveDelegateReservation: (conferenceId: String, delegateId: String) -> Unit,
    onOpenDelegateNoticeManager: (delegateId: String) -> Unit,
    onOpenNoticeManager: () -> Unit,
    onCloseNoticeManager: () -> Unit,
    onResolveAnswerIssue: (delegateId: String, questionId: String?, issueKey: String, justification: String?) -> Unit,
    onSetDelegatePaymentTier: (delegateId: String, paymentTier: PaymentTier) -> Unit,
    onUpdateAnswerIssueResolutionJustification: (resolutionId: String, justification: String?) -> Unit,
    onUnresolveAnswerIssue: (resolutionId: String) -> Unit,
    onClearBoardActionError: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AssignmentBoard(
                board = remember(conference) { conference.toAssignmentBoardUiState() },
                loading = loading,
                actionError = boardActionError,
                scrollRequest = boardScrollRequest,
                onSetCommitteeSlotsPerPosition = onSetCommitteeSlotsPerPosition,
                onSetCommitteeMode = onSetCommitteeMode,
                onSetCommitteePaymentTier = onSetCommitteePaymentTier,
                onSetPositionMappedCommittee = onSetPositionMappedCommittee,
                onAssignDelegateToSlot = onAssignDelegateToSlot,
                onClearPendingSlot = onClearPendingSlot,
                onOpenDelegateDetail = onOpenDelegateDetail,
                onOpenReservationManager = { onOpenReservationManager(null) },
                onClearActionError = onClearBoardActionError,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            if (delegateListOpen) {
                DelegateListPanel(
                    list = remember(conference, delegateListControls) { conference.toDelegateListUiState(delegateListControls) },
                    controls = delegateListControls,
                    onControlsChange = onUpdateDelegateListControls,
                    onApplyPreset = onApplyDelegateListPreset,
                    onOpenDelegateDetail = onOpenDelegateDetail,
                    onNavigateToPosition = onNavigateToBoardPosition,
                    onClose = onCloseDelegateList,
                )
            }
            if (delegationOverviewOpen) {
                DelegationOverviewPanel(
                    overview = conference.toDelegationOverviewUiState(),
                    targetDelegationId = delegationOverviewTargetId,
                    loading = loading,
                    onOpenReservationManager = onOpenReservationManager,
                    onCreateReservation = onOpenDelegationReservationEditor,
                    onSetHighSchoolStatus = onSetDelegationHighSchoolStatus,
                    onOpenDelegateDetail = onOpenDelegateDetail,
                    onClose = onCloseDelegationOverview,
                )
            }
            if (noticeManagerOpen) {
                IssueManagerPanel(
                    manager = issueManager,
                    onClose = onCloseNoticeManager,
                    onResolve = onResolveAnswerIssue,
                    onSetDelegatePaymentTier = onSetDelegatePaymentTier,
                    onUpdateJustification = onUpdateAnswerIssueResolutionJustification,
                    onUnresolve = onUnresolveAnswerIssue,
                    onOpenDelegate = onOpenDelegateDetail,
                    onClearFilter = onOpenNoticeManager,
                )
            }
            conference.toDelegateDetailUiState(selectedDelegateId)?.let { detail ->
                DelegateSidePanel(
                    detail = detail,
                    loading = loading,
                    onClose = onCloseDelegateDetail,
                    onNavigateToPosition = onNavigateToBoardPosition,
                    onDeactivate = onDeactivateDelegate,
                    onReactivate = onReactivateDelegate,
                    onAddComment = onAddDelegateComment,
                    onUpdateComment = onUpdateDelegateComment,
                    onDeleteComment = onDeleteDelegateComment,
                    reservationOptions = reservationOptions,
                    reservationActionError = delegateReservationActionError,
                    onAssignDelegateReservation = { delegateId, reservationId ->
                        onAssignDelegateReservation(conference.id.value, delegateId, reservationId)
                    },
                    onRemoveDelegateReservation = { delegateId ->
                        onRemoveDelegateReservation(conference.id.value, delegateId)
                    },
                    onOpenNotices = onOpenDelegateNoticeManager,
                    onResolveIssue = onResolveAnswerIssue,
                    onUnresolveIssue = onUnresolveAnswerIssue,
                    onSetPaymentTier = onSetDelegatePaymentTier,
                )
            }
        }
    }
}

@Composable
private fun WorkbookFilenameMismatchDialog(
    pending: PendingWorkbookUpdate,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unexpected update filename") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Selected file: ${pending.path.fileName}")
                if (pending.emptyCandidate) {
                    Text(
                        "MunAssign could not identify conference-specific parts in this filename. " +
                            "This filename will require confirmation every time.",
                    )
                } else {
                    Text("This filename does not match a trusted filename for the selected conference.")
                    Text("Expected: ${pending.expectedParts.joinToString(" + ")}")
                    Text(
                        "Missing expected parts: ${pending.missingParts.ifEmpty { listOf("None") }.joinToString()}",
                    )
                    Text(
                        "Unexpected parts: ${pending.unexpectedParts.ifEmpty { listOf("None") }.joinToString()}",
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        dismissButton = {
            ArmedConfirmationAction(
                revealLabel = "Update anyway",
                confirmLabel = "Update",
                armTooltip = "Arm this update",
                onConfirm = onConfirm,
            )
        },
    )
}

@Composable
private fun ConferenceNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Conference name") },
                    singleLine = true,
                    isError = error != null,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

internal enum class ConferenceManagementAction {
    Reload,
    Export,
    Replace,
    TrustedFilenames,
    Rename,
    MoveToSynchronized,
    Locate,
    Remove,
}

internal data class ConferenceManagementMenuAction(
    val action: ConferenceManagementAction,
    val enabled: Boolean,
)

internal enum class ConferenceAddTab(val label: String) {
    CreateLocal("Create new local"),
    ImportLocal("Import as new"),
    CreateSynchronized("Create new synchronized"),
    ConnectSynchronized("Connect synchronized"),
}

internal fun ConferenceListItem.inlineManagementActions(): List<ConferenceManagementAction> =
    if (
        storageKind == ConferenceListStorageKind.Synchronized &&
        availability == ConferenceListAvailability.Available
    ) {
        listOf(ConferenceManagementAction.Reload)
    } else {
        emptyList()
    }

internal fun ConferenceListItem.overflowManagementActions(): List<ConferenceManagementMenuAction> {
    val available = availability == ConferenceListAvailability.Available
    return buildList {
        add(ConferenceManagementMenuAction(ConferenceManagementAction.Export, available))
        add(ConferenceManagementMenuAction(ConferenceManagementAction.Replace, available))
        add(ConferenceManagementMenuAction(ConferenceManagementAction.TrustedFilenames, available))
        add(ConferenceManagementMenuAction(ConferenceManagementAction.Rename, available))
        add(
            if (storageKind == ConferenceListStorageKind.Local) {
                ConferenceManagementMenuAction(ConferenceManagementAction.MoveToSynchronized, available)
            } else {
                ConferenceManagementMenuAction(ConferenceManagementAction.Locate, enabled = true)
            },
        )
        add(ConferenceManagementMenuAction(ConferenceManagementAction.Remove, enabled = true))
    }
}

@Composable
private fun ConferenceManagementDialog(
    state: MainUiState,
    onDismiss: () -> Unit,
    onCreateConference: (String) -> Boolean,
    onCreateSynchronizedConference: (String) -> Unit,
    onMoveConferenceToSynchronized: (String, String) -> Unit,
    onConnectSynchronizedConference: () -> Unit,
    onLocateSynchronizedConference: (String) -> Unit,
    onReloadSynchronizedConference: (String) -> Unit,
    onUnlinkSynchronizedConference: (String) -> Unit,
    onDeleteSynchronizedConferenceFile: (String) -> Unit,
    onRenameConference: (String, String) -> Boolean,
    onDeleteConference: (String) -> Unit,
    onReorderConferences: (List<String>) -> Boolean,
    onExportConference: (String, String) -> Unit,
    onImportConferenceAsNew: () -> Unit,
    onReplaceConference: (String, String) -> Unit,
    onDeleteMymunUpdateFilenameSignature: (conferenceId: String, signatureId: String) -> Unit,
    onClearConferenceActionError: () -> Unit,
) {
    val orderedConferences = remember { state.conferenceEntries.toMutableStateList() }
    var addDialogOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ConferenceListItem?>(null) }
    var deleteTarget by remember { mutableStateOf<ConferenceListItem?>(null) }
    var filenameSignatureTargetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.conferenceEntries) {
        orderedConferences.replaceAll(state.conferenceEntries)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage conferences") },
        text = {
            Column(modifier = Modifier.width(560.dp).fillMaxHeight(0.72f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.conferenceActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(orderedConferences, key = { _, conference -> conference.id }) { index, conference ->
                        ReorderableConferenceRow(
                            conference = conference,
                            index = index,
                            conferences = orderedConferences,
                            reorderEnabled = !state.loading,
                            onRename = {
                                onClearConferenceActionError()
                                renameTarget = conference
                            },
                            onDelete = {
                                onClearConferenceActionError()
                                deleteTarget = conference
                            },
                            onExport = {
                                onClearConferenceActionError()
                                onExportConference(conference.id, conference.name)
                            },
                            onReplace = {
                                onClearConferenceActionError()
                                onReplaceConference(conference.id, conference.name)
                            },
                            onMoveToSynchronized = {
                                onMoveConferenceToSynchronized(conference.id, conference.name)
                            },
                            onLocate = { onLocateSynchronizedConference(conference.id) },
                            onReload = { onReloadSynchronizedConference(conference.id) },
                            onManageFilenameSignatures = {
                                onClearConferenceActionError()
                                filenameSignatureTargetId = conference.id
                            },
                            onMoved = {
                                onReorderConferences(orderedConferences.map { it.id })
                            },
                        )
                    }
                }
                WithTooltip("Create, import, or connect a conference.") {
                    OutlinedButton(
                        onClick = {
                            onClearConferenceActionError()
                            addDialogOpen = true
                        },
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add conference")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        },
    )

    if (addDialogOpen) {
        AddConferenceDialog(
            error = state.conferenceActionError,
            onDismiss = {
                addDialogOpen = false
                onClearConferenceActionError()
            },
            onCreateLocal = { name ->
                if (onCreateConference(name)) addDialogOpen = false
            },
            onImportLocal = {
                addDialogOpen = false
                onImportConferenceAsNew()
            },
            onCreateSynchronized = { name ->
                addDialogOpen = false
                onCreateSynchronizedConference(name)
            },
            onConnectSynchronized = {
                addDialogOpen = false
                onConnectSynchronizedConference()
            },
            onClearError = onClearConferenceActionError,
        )
    }

    renameTarget?.let { conference ->
        ConferenceNameDialog(
            title = "Rename conference",
            confirmLabel = "Rename",
            initialName = conference.name,
            error = state.conferenceActionError,
            onDismiss = {
                renameTarget = null
                onClearConferenceActionError()
            },
            onConfirm = { name ->
                if (onRenameConference(conference.id, name)) {
                    renameTarget = null
                }
            },
        )
    }

    deleteTarget?.let { conference ->
        if (conference.storageKind == ConferenceListStorageKind.Synchronized) {
            RemoveSynchronizedConferenceDialog(
                conferenceName = conference.name,
                onDismiss = { deleteTarget = null },
                onUnlink = {
                    onUnlinkSynchronizedConference(conference.id)
                    deleteTarget = null
                },
                onDeleteFile = {
                    onDeleteSynchronizedConferenceFile(conference.id)
                    deleteTarget = null
                },
            )
        } else {
            DeleteConferenceDialog(
                conferenceName = conference.name,
                onDismiss = { deleteTarget = null },
                onConfirm = {
                    onDeleteConference(conference.id)
                    deleteTarget = null
                },
            )
        }
    }

    filenameSignatureTargetId?.let { conferenceId ->
        state.conferences.firstOrNull { it.id.value == conferenceId }?.let { conference ->
            TrustedFilenameSignaturesDialog(
                conference = conference,
                error = state.conferenceActionError,
                onDismiss = {
                    filenameSignatureTargetId = null
                    onClearConferenceActionError()
                },
                onDelete = { signatureId ->
                    onDeleteMymunUpdateFilenameSignature(conferenceId, signatureId)
                },
            )
        }
    }
}

@Composable
private fun AddConferenceDialog(
    error: String?,
    onDismiss: () -> Unit,
    onCreateLocal: (String) -> Unit,
    onImportLocal: () -> Unit,
    onCreateSynchronized: (String) -> Unit,
    onConnectSynchronized: () -> Unit,
    onClearError: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(ConferenceAddTab.CreateLocal) }
    var localName by remember { mutableStateOf("") }
    var synchronizedName by remember { mutableStateOf("") }
    val name = if (selectedTab == ConferenceAddTab.CreateLocal) localName else synchronizedName
    val actionEnabled = when (selectedTab) {
        ConferenceAddTab.CreateLocal,
        ConferenceAddTab.CreateSynchronized,
        -> name.isNotBlank()
        ConferenceAddTab.ImportLocal,
        ConferenceAddTab.ConnectSynchronized,
        -> true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add conference") },
        text = {
            Column(modifier = Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    ConferenceAddTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = {
                                if (selectedTab != tab) onClearError()
                                selectedTab = tab
                            },
                            text = {
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                )
                            },
                        )
                    }
                }
                when (selectedTab) {
                    ConferenceAddTab.CreateLocal -> {
                        Text("Create a new local conference in this MunAssign installation.")
                        OutlinedTextField(
                            value = localName,
                            onValueChange = { localName = it },
                            label = { Text("Conference name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ConferenceAddTab.ImportLocal -> Text(
                        "Choose a .munassign transfer to import as a detached local conference.",
                    )
                    ConferenceAddTab.CreateSynchronized -> {
                        Text("Create a new synchronized conference, then choose its shared file location.")
                        OutlinedTextField(
                            value = synchronizedName,
                            onValueChange = { synchronizedName = it },
                            label = { Text("Conference name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ConferenceAddTab.ConnectSynchronized -> Text(
                        "Choose an existing synchronized conference file and keep it linked at that location.",
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = actionEnabled,
                onClick = {
                    when (selectedTab) {
                        ConferenceAddTab.CreateLocal -> onCreateLocal(localName)
                        ConferenceAddTab.ImportLocal -> onImportLocal()
                        ConferenceAddTab.CreateSynchronized -> onCreateSynchronized(synchronizedName)
                        ConferenceAddTab.ConnectSynchronized -> onConnectSynchronized()
                    }
                },
            ) {
                Text(
                    when (selectedTab) {
                        ConferenceAddTab.CreateLocal -> "Create"
                        ConferenceAddTab.ImportLocal -> "Choose file"
                        ConferenceAddTab.CreateSynchronized -> "Choose location"
                        ConferenceAddTab.ConnectSynchronized -> "Choose file"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TrustedFilenameSignaturesDialog(
    conference: Conference,
    error: String?,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit,
) {
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    val signatures = conference.mymunUpdateFilenameSignatures.sortedByDescending { it.trustedAt }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trusted update filenames - ${conference.name}") },
        text = {
            Column(
                modifier = Modifier.width(520.dp).fillMaxHeight(0.65f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "A selected Excel file must match one complete set of parts. " +
                        "Deleting the final set makes the next successful update the new baseline.",
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (signatures.isEmpty()) {
                    Text("No trusted filename parts are stored for this conference.")
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(signatures, key = { _, signature -> signature.id.value }) { _, signature ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    signature.parts.joinToString(" + ") { it.displayValue },
                                    modifier = Modifier.weight(1f),
                                )
                                TooltipIconButton(
                                    tooltip = "Delete this trusted filename",
                                    icon = Icons.Filled.Delete,
                                    onClick = { deleteTargetId = signature.id.value },
                                    enabled = true,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        },
    )

    deleteTargetId?.let { signatureId ->
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("Delete trusted filename") },
            text = { Text("Future update files matching only this set of parts will require confirmation again.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(signatureId)
                        deleteTargetId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ReorderableConferenceRow(
    conference: ConferenceListItem,
    index: Int,
    conferences: SnapshotStateList<ConferenceListItem>,
    reorderEnabled: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onReplace: () -> Unit,
    onMoveToSynchronized: () -> Unit,
    onLocate: () -> Unit,
    onReload: () -> Unit,
    onManageFilenameSignatures: () -> Unit,
    onMoved: () -> Unit,
) {
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    var menuOpen by remember(conference.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .pointerInput(conference.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Release && event.button == PointerButton.Secondary) {
                            event.changes.forEach { it.consume() }
                            menuOpen = true
                        }
                    }
                }
            }
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.DragIndicator,
            contentDescription = "Drag to reorder ${conference.name}",
            modifier = Modifier.pointerInput(reorderEnabled, index, conferences.size) {
                if (!reorderEnabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = { accumulatedDrag = 0f },
                    onDragCancel = { accumulatedDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDrag += dragAmount
                        if (abs(accumulatedDrag) >= 36f) {
                            val direction = if (accumulatedDrag > 0) 1 else -1
                            val target = (index + direction).coerceIn(0, conferences.lastIndex)
                            if (target != index) {
                                conferences.move(index, target)
                                onMoved()
                            }
                            accumulatedDrag = 0f
                        }
                    },
                )
            },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(conference.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            if (conference.storageKind == ConferenceListStorageKind.Synchronized) {
                Text(
                    when (conference.availability) {
                        ConferenceListAvailability.Available -> "Synchronized"
                        ConferenceListAvailability.Missing -> "Synchronized file missing"
                        ConferenceListAvailability.Invalid -> "Synchronized file invalid"
                        ConferenceListAvailability.NewerVersion -> "Requires newer MunAssign"
                        ConferenceListAvailability.Inaccessible -> "Synchronized file inaccessible"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (conference.availability == ConferenceListAvailability.Available) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                conference.lastLoadedAtEpochMillis?.let { loadedAt ->
                    Text(
                        "Last loaded ${formatLastLoaded(loadedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        if (ConferenceManagementAction.Reload in conference.inlineManagementActions()) {
            TooltipIconButton(
                tooltip = "Reload ${conference.name}",
                icon = Icons.Filled.Refresh,
                onClick = onReload,
                enabled = reorderEnabled,
            )
        }
        Box {
            TooltipIconButton(
                tooltip = "More actions for ${conference.name}",
                icon = Icons.Filled.ArrowDropDown,
                onClick = { menuOpen = true },
                enabled = reorderEnabled,
            )
            ConferenceManagementMenu(
                conference = conference,
                expanded = menuOpen,
                actionsEnabled = reorderEnabled,
                onDismiss = { menuOpen = false },
                onExport = onExport,
                onReplace = onReplace,
                onManageFilenameSignatures = onManageFilenameSignatures,
                onRename = onRename,
                onMoveToSynchronized = onMoveToSynchronized,
                onLocate = onLocate,
                onRemove = onDelete,
            )
        }
    }
}

@Composable
private fun ConferenceManagementMenu(
    conference: ConferenceListItem,
    expanded: Boolean,
    actionsEnabled: Boolean,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onReplace: () -> Unit,
    onManageFilenameSignatures: () -> Unit,
    onRename: () -> Unit,
    onMoveToSynchronized: () -> Unit,
    onLocate: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        conference.overflowManagementActions().forEach { menuAction ->
            if (menuAction.action == ConferenceManagementAction.Remove) {
                HorizontalDivider()
            }
            val label = when (menuAction.action) {
                ConferenceManagementAction.Export -> "Export conference"
                ConferenceManagementAction.Replace -> "Replace from file"
                ConferenceManagementAction.TrustedFilenames -> "Trusted filename signatures"
                ConferenceManagementAction.Rename -> "Rename conference"
                ConferenceManagementAction.MoveToSynchronized -> "Move to synchronized file"
                ConferenceManagementAction.Locate -> "Locate synchronized file"
                ConferenceManagementAction.Remove -> if (conference.storageKind == ConferenceListStorageKind.Local) {
                    "Delete conference"
                } else {
                    "Remove synchronized conference"
                }
                ConferenceManagementAction.Reload -> error("Reload is an inline conference action.")
            }
            val icon = when (menuAction.action) {
                ConferenceManagementAction.Export -> Icons.Filled.FileUpload
                ConferenceManagementAction.Replace -> Icons.Filled.RestorePage
                ConferenceManagementAction.TrustedFilenames -> Icons.Filled.Verified
                ConferenceManagementAction.Rename -> Icons.Filled.Edit
                ConferenceManagementAction.MoveToSynchronized -> Icons.Filled.CloudUpload
                ConferenceManagementAction.Locate -> Icons.Filled.FolderOpen
                ConferenceManagementAction.Remove -> Icons.Filled.Delete
                ConferenceManagementAction.Reload -> Icons.Filled.Refresh
            }
            val action = when (menuAction.action) {
                ConferenceManagementAction.Export -> onExport
                ConferenceManagementAction.Replace -> onReplace
                ConferenceManagementAction.TrustedFilenames -> onManageFilenameSignatures
                ConferenceManagementAction.Rename -> onRename
                ConferenceManagementAction.MoveToSynchronized -> onMoveToSynchronized
                ConferenceManagementAction.Locate -> onLocate
                ConferenceManagementAction.Remove -> onRemove
                ConferenceManagementAction.Reload -> error("Reload is an inline conference action.")
            }
            val destructive = menuAction.action == ConferenceManagementAction.Remove
            DropdownMenuItem(
                text = {
                    Text(
                        label,
                        color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                },
                leadingIcon = {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                enabled = actionsEnabled && menuAction.enabled,
                onClick = {
                    onDismiss()
                    action()
                },
            )
        }
    }
}

@Composable
private fun ReplaceConferenceDialog(
    conferenceName: String,
    synchronizedTarget: Boolean,
    synchronizedSource: Boolean,
    sourceName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace $conferenceName?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$sourceName will replace all current conference contents.")
                if (synchronizedTarget) {
                    Text("This is a synchronized conference, so the replacement affects every collaborator.")
                }
                if (synchronizedSource) {
                    Text("The source is synchronized. MunAssign will use a detached snapshot and leave that source unchanged.")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        dismissButton = {
            ArmedConfirmationAction(
                revealLabel = "Replace conference",
                confirmLabel = "Replace",
                armTooltip = "Arm conference replacement",
                destructive = true,
                onConfirm = onConfirm,
            )
        },
    )
}

@Composable
private fun DeleteConferenceDialog(
    conferenceName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $conferenceName?") },
        text = {
            Text(
                "This removes the conference and all of its committees, positions, slots, delegates, preferences, answers, comments, delegations, and update history.",
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        dismissButton = {
            ArmedConfirmationAction(
                revealLabel = "Delete conference",
                confirmLabel = "Delete",
                armTooltip = "Arm conference deletion",
                destructive = true,
                onConfirm = onConfirm,
            )
        },
    )
}

private val lastLoadedFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatLastLoaded(epochMillis: Long): String =
    lastLoadedFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

@Composable
private fun RemoveSynchronizedConferenceDialog(
    conferenceName: String,
    onDismiss: () -> Unit,
    onUnlink: () -> Unit,
    onDeleteFile: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove $conferenceName?") },
        text = {
            Text(
                "Unlink removes only this installation's reference. Deleting the shared file makes it unavailable to every collaborator.",
            )
        },
        confirmButton = {
            Button(onClick = onUnlink) {
                Text("Unlink from this device")
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ArmedConfirmationAction(
                    revealLabel = "Delete shared file",
                    confirmLabel = "Delete file",
                    armTooltip = "Arm shared file deletion",
                    destructive = true,
                    onConfirm = onDeleteFile,
                )
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun SynchronizedImportDialog(
    sourceName: String,
    onDismiss: () -> Unit,
    onConnect: () -> Unit,
    onImport: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Synchronized conference selected") },
        text = {
            Text(
                "$sourceName is intended to remain linked at its current location. Connect it to collaborate, or arm importing a detached local copy.",
            )
        },
        confirmButton = {
            Button(onClick = {
                onDismiss()
                onConnect()
            }) {
                Text("Connect instead")
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ArmedConfirmationAction(
                    revealLabel = "Import detached copy",
                    confirmLabel = "Import copy",
                    armTooltip = "Arm detached import",
                    onConfirm = onImport,
                )
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun UnavailableConferenceState(
    entry: ConferenceListItem?,
    onReload: () -> Unit,
    onLocate: () -> Unit,
    onUnlink: () -> Unit,
) {
    if (entry == null) {
        LoadingState()
        return
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(entry.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            when (entry.availability) {
                ConferenceListAvailability.Missing -> "The synchronized file is missing from its saved location."
                ConferenceListAvailability.Invalid -> "The file at the saved location is not the expected synchronized conference."
                ConferenceListAvailability.NewerVersion -> "This synchronized conference requires a newer MunAssign version."
                ConferenceListAvailability.Inaccessible -> "The synchronized file cannot currently be accessed."
                ConferenceListAvailability.Available -> "The synchronized conference could not be loaded."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        entry.path?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onReload) { Text("Reload") }
            Button(onClick = onLocate) { Text("Locate") }
            TextButton(onClick = onUnlink) { Text("Unlink") }
        }
    }
}

@Composable
private fun ArmedConfirmationAction(
    revealLabel: String,
    confirmLabel: String,
    armTooltip: String,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
) {
    var state by remember { mutableStateOf(ArmedConfirmationState()) }
    if (!state.revealed) {
        OutlinedButton(
            onClick = { state = state.reveal() },
            modifier = Modifier.width(176.dp),
        ) {
            Text(revealLabel)
        }
    } else {
        Surface(
            modifier = Modifier.width(176.dp).height(40.dp).offset(y = 3.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WithTooltip(armTooltip) {
                    Switch(
                        checked = state.armed,
                        onCheckedChange = { armed -> state = state.setArmed(armed) },
                    )
                }
                Button(
                    onClick = onConfirm,
                    enabled = state.confirmationEnabled,
                    colors = if (destructive) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    Text(confirmLabel)
                }
            }
        }
    }
}

private fun SnapshotStateList<ConferenceListItem>.replaceAll(conferences: List<ConferenceListItem>) {
    clear()
    addAll(conferences)
}

private fun SnapshotStateList<ConferenceListItem>.move(from: Int, to: Int) {
    if (from == to) return
    val item = removeAt(from)
    add(to, item)
}

private fun Delegate.hasActionableApplicationStatus(): Boolean = profile.isUsableForAssignment
