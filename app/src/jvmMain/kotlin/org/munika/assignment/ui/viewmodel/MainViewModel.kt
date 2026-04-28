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
import org.munika.assignment.domain.repository.ConferenceRepository
import org.munika.assignment.domain.repository.UpdateIssue
import org.munika.assignment.domain.repository.UpdateIssueSeverity

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

    fun createConference(name: String) {
        if (name.isBlank()) return
        scope.launch {
            val created = repository.createConference(name)
            val conferences = repository.listConferences()
            _state.update {
                it.copy(
                    conferences = conferences,
                    selectedConferenceId = created.id.value,
                    selectedConference = created,
                    updateIssues = emptyList(),
                )
            }
        }
    }

    fun deleteSelectedConference() {
        val selectedId = selectedIdOrNull() ?: return
        scope.launch {
            repository.deleteConference(selectedId)
            val conferences = repository.listConferences()
            val nextId = conferences.firstOrNull()?.id?.value
            val nextConference = nextId?.let { repository.getConference(it) }
            _state.update {
                it.copy(
                    conferences = conferences,
                    selectedConferenceId = nextId,
                    selectedConference = nextConference,
                )
            }
        }
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
                            ),
                        ),
                        loading = false,
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
)
