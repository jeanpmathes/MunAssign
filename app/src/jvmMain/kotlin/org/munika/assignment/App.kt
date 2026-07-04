package org.munika.assignment

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.munika.assignment.ui.screen.MainScreen
import org.munika.assignment.ui.screen.WorkbookDropFeedback
import org.munika.assignment.ui.viewmodel.MainViewModel

@Composable
fun App(
    viewModel: MainViewModel,
    workbookDropFeedback: WorkbookDropFeedback? = null,
    onOpenWorkbook: () -> Unit,
    onExportConference: (String, String) -> Unit,
    onImportConferenceAsNew: () -> Unit,
    onReplaceConference: (String, String) -> Unit,
    onOpenMarkdownExportFolder: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            MainScreen(
                state = state,
                workbookDropFeedback = workbookDropFeedback,
                onSelectConference = viewModel::selectConference,
                onCreateConference = viewModel::createConference,
                onRenameConference = viewModel::renameConference,
                onDeleteConference = viewModel::deleteConference,
                onReorderConferences = viewModel::reorderConferences,
                onSetCommitteeSlotsPerPosition = viewModel::setCommitteeSlotsPerPosition,
                onSetCommitteeMode = viewModel::setCommitteeMode,
                onSetPositionMappedCommittee = viewModel::setPositionMappedCommittee,
                onAssignDelegateToSlot = viewModel::assignDelegateToSlot,
                onClearPendingSlot = viewModel::clearPendingSlot,
                onOpenDelegateList = viewModel::openDelegateList,
                onCloseDelegateList = viewModel::closeDelegateList,
                onOpenNoticeManager = viewModel::openNoticeManager,
                onOpenDelegateNoticeManager = viewModel::openDelegateNoticeManager,
                onCloseNoticeManager = viewModel::closeNoticeManager,
                onOpenDelegationOverview = viewModel::openDelegationOverview,
                onOpenDelegationOverviewFor = viewModel::openDelegationOverviewFor,
                onCloseDelegationOverview = viewModel::closeDelegationOverview,
                onOpenReservationManager = viewModel::openReservationManager,
                onCloseReservationManager = viewModel::closeReservationManager,
                onCreateReservation = viewModel::createReservation,
                onUpdateReservation = viewModel::updateReservation,
                onDeleteReservation = viewModel::deleteReservation,
                onAssignDelegateReservation = viewModel::assignDelegateReservation,
                onRemoveDelegateReservation = viewModel::removeDelegateReservation,
                onResolveAnswerIssue = viewModel::resolveAnswerIssue,
                onUpdateAnswerIssueResolutionJustification = viewModel::updateAnswerIssueResolutionJustification,
                onUnresolveAnswerIssue = viewModel::unresolveAnswerIssue,
                onOpenQuestionConfiguration = viewModel::openQuestionConfiguration,
                onCloseQuestionConfiguration = viewModel::closeQuestionConfiguration,
                onUpdateQuestionConfigurations = viewModel::updateQuestionConfigurations,
                onUpdateDelegateListControls = viewModel::updateDelegateListControls,
                onApplyDelegateListPreset = viewModel::applyDelegateListPreset,
                onOpenDelegateDetail = viewModel::openDelegateDetail,
                onCloseDelegateDetail = viewModel::closeDelegateDetail,
                onNavigateToBoardPosition = viewModel::navigateToBoardPosition,
                onDeactivateDelegate = viewModel::deactivateDelegate,
                onReactivateDelegate = viewModel::reactivateDelegate,
                onAddDelegateComment = viewModel::addDelegateComment,
                onUpdateDelegateComment = viewModel::updateDelegateComment,
                onDeleteDelegateComment = viewModel::deleteDelegateComment,
                onOpenWorkbook = onOpenWorkbook,
                onExportConference = onExportConference,
                onImportConferenceAsNew = onImportConferenceAsNew,
                onReplaceConference = onReplaceConference,
                onOpenMarkdownExportFolder = onOpenMarkdownExportFolder,
                onClearConferenceActionError = viewModel::clearConferenceActionError,
                onClearBoardActionError = viewModel::clearBoardActionError,
                onClearReservationActionError = viewModel::clearReservationActionError,
            )
        }
    }
}
