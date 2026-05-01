package org.munika.assignment

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.munika.assignment.ui.screen.MainScreen
import org.munika.assignment.ui.viewmodel.MainViewModel

@Composable
fun App(viewModel: MainViewModel, onOpenWorkbook: () -> Unit) {
    val state by viewModel.state.collectAsState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            MainScreen(
                state = state,
                onSelectConference = viewModel::selectConference,
                onCreateConference = viewModel::createConference,
                onRenameConference = viewModel::renameConference,
                onDeleteConference = viewModel::deleteConference,
                onReorderConferences = viewModel::reorderConferences,
                onSetCommitteeSlotsPerPosition = viewModel::setCommitteeSlotsPerPosition,
                onAssignDelegateToSlot = viewModel::assignDelegateToSlot,
                onClearPendingSlot = viewModel::clearPendingSlot,
                onOpenWorkbook = onOpenWorkbook,
                onClearConferenceActionError = viewModel::clearConferenceActionError,
                onClearBoardActionError = viewModel::clearBoardActionError,
            )
        }
    }
}
