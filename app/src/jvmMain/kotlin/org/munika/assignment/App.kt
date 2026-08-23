package org.munika.assignment

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.ui.screen.MainScreen
import org.munika.assignment.ui.screen.WorkbookDropFeedback
import org.munika.assignment.ui.viewmodel.MainViewModel

@Composable
fun App(
    viewModel: MainViewModel,
    workbookDropFeedback: WorkbookDropFeedback? = null,
    onOpenWorkbook: () -> Unit,
    onCreateSynchronizedConference: (String) -> Unit,
    onMoveConferenceToSynchronized: (String, String) -> Unit,
    onConnectSynchronizedConference: () -> Unit,
    onLocateSynchronizedConference: (String) -> Unit,
    onExportConference: (String, String) -> Unit,
    onImportConferenceAsNew: () -> Unit,
    onReplaceConference: (String, String) -> Unit,
    onOpenMarkdownExportFolder: () -> Unit,
    onCopyStatusImage: (Conference) -> Result<Unit>,
) {
    val state by viewModel.state.collectAsState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            MainScreen(
                state = state,
                workbookDropFeedback = workbookDropFeedback,
                onSelectConference = viewModel::selectConference,
                onCreateConference = viewModel::createConference,
                onCreateSynchronizedConference = onCreateSynchronizedConference,
                onMoveConferenceToSynchronized = onMoveConferenceToSynchronized,
                onConnectSynchronizedConference = onConnectSynchronizedConference,
                onLocateSynchronizedConference = onLocateSynchronizedConference,
                onReloadSynchronizedConference = viewModel::reloadSynchronizedConference,
                onUnlinkSynchronizedConference = viewModel::unlinkSynchronizedConference,
                onDeleteSynchronizedConferenceFile = viewModel::deleteSynchronizedConferenceFile,
                onRenameConference = viewModel::renameConference,
                onDeleteConference = viewModel::deleteConference,
                onReorderConferences = viewModel::reorderConferences,
                onSetCommitteeSlotsPerPosition = viewModel::setCommitteeSlotsPerPosition,
                onSetCommitteeMode = viewModel::setCommitteeMode,
                onSetCommitteePaymentTier = viewModel::setCommitteePaymentTier,
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
                onSetDelegationHighSchoolStatus = viewModel::setDelegationHighSchoolStatus,
                onOpenDelegationReservationEditor = viewModel::openDelegationReservationEditor,
                onCloseDelegationReservationEditor = viewModel::closeDelegationReservationEditor,
                onOpenReservationManager = viewModel::openReservationManager,
                onCloseReservationManager = viewModel::closeReservationManager,
                onCreateReservation = viewModel::createReservation,
                onCreateDelegationReservation = viewModel::createDelegationReservation,
                onUpdateReservation = viewModel::updateReservation,
                onDeleteReservation = viewModel::deleteReservation,
                onAssignDelegateReservation = viewModel::assignDelegateReservation,
                onRemoveDelegateReservation = viewModel::removeDelegateReservation,
                onResolveAnswerIssue = viewModel::resolveAnswerIssue,
                onSetDelegatePaymentTier = viewModel::setDelegatePaymentTier,
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
                onConfirmPendingWorkbookUpdate = viewModel::confirmPendingWorkbookUpdate,
                onCancelPendingWorkbookUpdate = viewModel::cancelPendingWorkbookUpdate,
                onSavePreferenceCorrections = viewModel::savePreferenceCorrections,
                onCancelPreferenceCompletion = viewModel::cancelPreferenceCompletion,
                onDeleteMymunUpdateFilenameSignature = viewModel::deleteMymunUpdateFilenameSignature,
                onResetPreferenceCorrections = viewModel::resetPreferenceCorrections,
                onExportConference = onExportConference,
                onImportConferenceAsNew = onImportConferenceAsNew,
                onReplaceConference = onReplaceConference,
                onConfirmPendingConferenceReplace = viewModel::confirmPendingConferenceReplace,
                onCancelPendingConferenceReplace = viewModel::cancelPendingConferenceReplace,
                onConfirmSynchronizedImport = viewModel::confirmSynchronizedImportAsNew,
                onConnectPendingSynchronizedImport = viewModel::connectPendingSynchronizedImport,
                onCancelSynchronizedImport = viewModel::cancelSynchronizedImport,
                onOpenMarkdownExportFolder = onOpenMarkdownExportFolder,
                onCopyStatusImage = onCopyStatusImage,
                onClearConferenceActionError = viewModel::clearConferenceActionError,
                onClearBoardActionError = viewModel::clearBoardActionError,
                onClearReservationActionError = viewModel::clearReservationActionError,
            )
        }
    }
}
