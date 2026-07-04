@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.munika.assignment.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.QuestionConfiguration
import org.munika.assignment.ui.screen.dialog.IssueManagerPanel
import org.munika.assignment.ui.screen.dialog.QuestionConfigurationDialog
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
import org.munika.assignment.ui.viewmodel.MainUiState
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
    onRenameConference: (String, String) -> Boolean,
    onDeleteConference: (String) -> Unit,
    onReorderConferences: (List<String>) -> Boolean,
    onSetCommitteeSlotsPerPosition: (String, Int) -> Unit,
    onSetCommitteeMode: (String, CommitteeMode) -> Unit,
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
    onOpenReservationManager: (delegationId: String?) -> Unit = {},
    onCloseReservationManager: () -> Unit = {},
    onCreateReservation: (
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
    onExportConference: (String, String) -> Unit,
    onImportConferenceAsNew: () -> Unit,
    onReplaceConference: (String, String) -> Unit,
    onOpenMarkdownExportFolder: () -> Unit,
    onClearConferenceActionError: () -> Unit,
    onClearBoardActionError: () -> Unit,
    onClearReservationActionError: () -> Unit = {},
) {
    var createDialogOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Conference?>(null) }
    var managementOpen by remember { mutableStateOf(false) }
    val selectedConference = state.selectedConference
    val snackbarHostState = remember { SnackbarHostState() }

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
                )
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    when {
                        state.loading && state.conferences.isEmpty() -> LoadingState()
                        state.conferences.isEmpty() -> NoConferenceState(
                            onCreate = {
                                onClearConferenceActionError()
                                createDialogOpen = true
                            },
                        )
                        selectedConference == null -> LoadingState()
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
                            onSetPositionMappedCommittee = onSetPositionMappedCommittee,
                            onAssignDelegateToSlot = onAssignDelegateToSlot,
                            onClearPendingSlot = onClearPendingSlot,
                            onCloseDelegateList = onCloseDelegateList,
                            onCloseDelegationOverview = onCloseDelegationOverview,
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
                if (onRenameConference(conference.id.value, name)) {
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
            onRenameConference = onRenameConference,
            onDeleteConference = onDeleteConference,
            onReorderConferences = onReorderConferences,
            onExportConference = onExportConference,
            onImportConferenceAsNew = onImportConferenceAsNew,
            onReplaceConference = onReplaceConference,
            onClearConferenceActionError = onClearConferenceActionError,
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
    onOpenRename: (Conference) -> Unit,
    onOpenManagement: () -> Unit,
    onOpenDelegateList: () -> Unit,
    onOpenNoticeManager: () -> Unit,
    onOpenDelegationOverview: () -> Unit,
    onOpenReservationManager: () -> Unit,
    onOpenQuestionConfiguration: () -> Unit,
    onOpenWorkbook: (() -> Unit)? = null,
    onOpenMarkdownExportFolder: () -> Unit,
) {
    Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(0.82f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConferenceDropdown(
                    conferences = state.conferences,
                    selectedConferenceId = state.selectedConferenceId,
                    enabled = !state.loading,
                    onSelectConference = onSelectConference,
                    onOpenCreate = onOpenCreate,
                    onOpenRename = onOpenRename,
                )
                TooltipIconButton(
                    tooltip = "Manage conferences",
                    icon = Icons.Filled.ManageAccounts,
                    onClick = onOpenManagement,
                    enabled = !state.loading,
                )
                TooltipIconButton(
                    tooltip = "Open textual exports folder",
                    icon = Icons.Filled.FolderOpen,
                    onClick = onOpenMarkdownExportFolder,
                    enabled = !state.loading,
                )
            }

            Row(
                modifier = Modifier.weight(0.72f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Questions")
                    }
                }
            }

            Row(
                modifier = Modifier.weight(1.56f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
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
                TooltipIconButton(
                    tooltip = "Open delegation overview",
                    icon = Icons.Filled.Groups,
                    onClick = onOpenDelegationOverview,
                    enabled = !state.loading && state.selectedConference != null,
                )
                TooltipIconButton(
                    tooltip = "Open reservation manager",
                    icon = Icons.Filled.Bookmark,
                    onClick = onOpenReservationManager,
                    enabled = !state.loading && state.selectedConference != null,
                )
                Spacer(Modifier.width(8.dp))
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Updating", style = MaterialTheme.typography.bodyMedium)
                } else {
                    state.selectedConference?.let { ConferenceStats(it) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConferenceDropdown(
    conferences: List<Conference>,
    selectedConferenceId: String?,
    enabled: Boolean,
    onSelectConference: (String) -> Unit,
    onOpenCreate: () -> Unit,
    onOpenRename: (Conference) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = conferences.firstOrNull { it.id.value == selectedConferenceId }?.name ?: "Select conference"
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
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).width(300.dp),
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
                        onSelectConference(conference.id.value)
                    },
                    trailingIcon = {
                        TooltipIconButton(
                            tooltip = "Rename ${conference.name}",
                            icon = Icons.Filled.Edit,
                            onClick = {
                                expanded = false
                                onOpenRename(conference)
                            },
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
    val acceptedPaidActiveDelegates = delegates.filter {
        it.active && it.paymentStatus == PaymentStatus.Paid && it.hasActionableApplicationStatus()
    }
    val acceptedHighSchoolDelegates = acceptedPaidActiveDelegates.count { it.isHighSchooler }
    val acceptedUniversityDelegates = acceptedPaidActiveDelegates.count { !it.isHighSchooler }

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
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CapacityStatGroup(capacity: AcceptanceCapacityUiState) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text("Remaining high-school and university places after accepted paid delegates and reservation holds are counted.")
            }
        },
        state = rememberTooltipState(),
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text("Capacity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                CapacityValue("${capacity.highSchoolCount} HS", capacity.highSchoolCount)
                Text("·", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onSetPositionMappedCommittee: (String, String?) -> Unit,
    onAssignDelegateToSlot: (slotId: String, delegateId: String, moveExistingPending: Boolean) -> Unit,
    onClearPendingSlot: (slotId: String) -> Unit,
    onCloseDelegateList: () -> Unit,
    onCloseDelegationOverview: () -> Unit,
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
    onUpdateAnswerIssueResolutionJustification: (resolutionId: String, justification: String?) -> Unit,
    onUnresolveAnswerIssue: (resolutionId: String) -> Unit,
    onClearBoardActionError: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AssignmentBoard(
                board = conference.toAssignmentBoardUiState(),
                loading = loading,
                actionError = boardActionError,
                scrollRequest = boardScrollRequest,
                onSetCommitteeSlotsPerPosition = onSetCommitteeSlotsPerPosition,
                onSetCommitteeMode = onSetCommitteeMode,
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
                    list = conference.toDelegateListUiState(delegateListControls),
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
                    onOpenReservationManager = onOpenReservationManager,
                    onOpenDelegateDetail = onOpenDelegateDetail,
                    onClose = onCloseDelegationOverview,
                )
            }
            if (noticeManagerOpen) {
                IssueManagerPanel(
                    manager = issueManager,
                    onClose = onCloseNoticeManager,
                    onResolve = onResolveAnswerIssue,
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
                )
            }
        }
    }
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

@Composable
private fun ConferenceManagementDialog(
    state: MainUiState,
    onDismiss: () -> Unit,
    onCreateConference: (String) -> Boolean,
    onRenameConference: (String, String) -> Boolean,
    onDeleteConference: (String) -> Unit,
    onReorderConferences: (List<String>) -> Boolean,
    onExportConference: (String, String) -> Unit,
    onImportConferenceAsNew: () -> Unit,
    onReplaceConference: (String, String) -> Unit,
    onClearConferenceActionError: () -> Unit,
) {
    val orderedConferences = remember { state.conferences.toMutableStateList() }
    var createDialogOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Conference?>(null) }
    var deleteTarget by remember { mutableStateOf<Conference?>(null) }
    var replaceTarget by remember { mutableStateOf<Conference?>(null) }

    LaunchedEffect(state.conferences) {
        orderedConferences.replaceAll(state.conferences)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage conferences") },
        text = {
            Column(modifier = Modifier.width(560.dp).fillMaxHeight(0.72f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.conferenceActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(orderedConferences, key = { _, conference -> conference.id.value }) { index, conference ->
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
                                onExportConference(conference.id.value, conference.name)
                            },
                            onReplace = {
                                onClearConferenceActionError()
                                replaceTarget = conference
                            },
                            onMoved = {
                                onReorderConferences(orderedConferences.map { it.id.value })
                            },
                        )
                    }
                }
                WithTooltip("Create a new conference.") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onClearConferenceActionError()
                                createDialogOpen = true
                            },
                            enabled = !state.loading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Create conference")
                        }
                        OutlinedButton(
                            onClick = {
                                onClearConferenceActionError()
                                onImportConferenceAsNew()
                            },
                            enabled = !state.loading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Import as new")
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
                if (onRenameConference(conference.id.value, name)) {
                    renameTarget = null
                }
            },
        )
    }

    deleteTarget?.let { conference ->
        DeleteConferenceDialog(
            conference = conference,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onDeleteConference(conference.id.value)
                deleteTarget = null
            },
        )
    }

    replaceTarget?.let { conference ->
        ReplaceConferenceDialog(
            conference = conference,
            onDismiss = { replaceTarget = null },
            onConfirm = {
                onReplaceConference(conference.id.value, conference.name)
                replaceTarget = null
            },
        )
    }
}

@Composable
private fun ReorderableConferenceRow(
    conference: Conference,
    index: Int,
    conferences: SnapshotStateList<Conference>,
    reorderEnabled: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onReplace: () -> Unit,
    onMoved: () -> Unit,
) {
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    Row(
        modifier = Modifier.fillMaxWidth()
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
        }
        TooltipIconButton(
            tooltip = "Export ${conference.name}",
            icon = Icons.Filled.FileDownload,
            onClick = onExport,
            enabled = reorderEnabled,
        )
        TooltipIconButton(
            tooltip = "Replace ${conference.name} from .munassign",
            icon = Icons.Filled.UploadFile,
            onClick = onReplace,
            enabled = reorderEnabled,
        )
        TooltipIconButton(
            tooltip = "Rename ${conference.name}",
            icon = Icons.Filled.Edit,
            onClick = onRename,
            enabled = reorderEnabled,
        )
        TooltipIconButton(
            tooltip = "Delete ${conference.name}",
            icon = Icons.Filled.Delete,
            onClick = onDelete,
            enabled = reorderEnabled,
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ReplaceConferenceDialog(
    conference: Conference,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace ${conference.name}?") },
        text = {
            Text(
                "This imports a .munassign file over this conference. The current conference data will be replaced.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Replace")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun DeleteConferenceDialog(
    conference: Conference,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${conference.name}?") },
        text = {
            Text(
                "This removes the conference and all of its committees, positions, slots, delegates, preferences, answers, comments, delegations, and update history.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun SnapshotStateList<Conference>.replaceAll(conferences: List<Conference>) {
    clear()
    addAll(conferences)
}

private fun SnapshotStateList<Conference>.move(from: Int, to: Int) {
    if (from == to) return
    val item = removeAt(from)
    add(to, item)
}

private fun Delegate.hasActionableApplicationStatus(): Boolean {
    val normalizedStatus = profile.applicationStatus?.trim()?.lowercase().orEmpty()
    return normalizedStatus == "accepted" || normalizedStatus == "assigned"
}
