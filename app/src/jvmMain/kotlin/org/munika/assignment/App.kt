package org.munika.assignment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.munika.assignment.domain.model.AssignmentStatus
import org.munika.assignment.domain.repository.UpdateIssueSeverity
import org.munika.assignment.ui.viewmodel.MainViewModel

@Composable
fun App(viewModel: MainViewModel, onOpenWorkbook: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var conferenceName by remember { mutableStateOf("") }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Column(modifier = Modifier.width(280.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Conferences", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = conferenceName,
                        onValueChange = { conferenceName = it },
                        label = { Text("New conference") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            viewModel.createConference(conferenceName)
                            conferenceName = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create")
                    }
                    Button(
                        onClick = { viewModel.deleteSelectedConference() },
                        enabled = state.selectedConferenceId != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Delete selected")
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.conferences, key = { it.id.value }) { conference ->
                            Card(onClick = { viewModel.selectConference(conference.id.value) }, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(conference.name, fontWeight = if (conference.id.value == state.selectedConferenceId) FontWeight.Bold else FontWeight.Normal)
                                    Text("Committees: ${conference.committees.size}")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Workbook update", style = MaterialTheme.typography.titleLarge)
                    Text("Drop an Excel workbook onto the window, or open one manually.")
                    Button(onClick = onOpenWorkbook, enabled = state.selectedConferenceId != null) {
                        Text("Open workbook file...")
                    }

                    state.selectedConference?.let { conference ->
                        Text("Selected: ${conference.name}", fontWeight = FontWeight.Bold)
                        Text("Delegates: ${conference.delegates.size} | Committees: ${conference.committees.size} | Positions: ${conference.positions.size}")
                        Text("Open ${conference.openSlots.size} | Pending ${conference.pendingSlots.size} | Assigned ${conference.assignedSlots.size}")

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                            items(conference.committees, key = { it.id.value }) { committee ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("${committee.displayName} (${committee.slotsPerPosition} slots/position)", fontWeight = FontWeight.SemiBold)
                                        committee.positions.forEach { position ->
                                            Text("- ${position.name}")
                                            position.slots.forEach { slot ->
                                                val delegateText = slot.delegate?.displayName ?: "<empty>"
                                                val status = when (slot.status) {
                                                    AssignmentStatus.Empty -> "Empty"
                                                    AssignmentStatus.Pending -> "Pending"
                                                    AssignmentStatus.Assigned -> "Assigned"
                                                }
                                                Text("   slot ${slot.index}: $delegateText ($status)")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } ?: Text("Create or select a conference to begin.")

                    if (state.updateIssues.isNotEmpty()) {
                        Text("Update issues", fontWeight = FontWeight.Bold)
                        state.updateIssues.takeLast(10).forEach { issue ->
                            val marker = if (issue.severity == UpdateIssueSeverity.Error) "ERROR" else "WARN"
                            Text("[$marker] ${issue.message}")
                        }
                    }
                }
            }
        }
    }
}
