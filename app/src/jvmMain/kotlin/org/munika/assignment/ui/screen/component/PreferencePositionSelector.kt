package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.munika.assignment.ui.viewmodel.DelegateStatusTone

data class PreferencePositionOptionUiState(
    val id: String,
    val label: String,
    val currentRank: Int? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencePositionSelector(
    selectedPositionId: String?,
    options: List<PreferencePositionOptionUiState>,
    editingRank: Int,
    label: String,
    enabled: Boolean,
    onSelect: (PreferencePositionOptionUiState?) -> Unit,
    modifier: Modifier = Modifier,
    intentionalEmpty: Boolean = false,
    swapNotice: String? = null,
    onEdit: () -> Unit = {},
    onSelectIntentionalEmpty: () -> Unit = {},
) {
    val intentionalEmptyLabel = "No preference - keep empty"
    val selectedLabel = if (intentionalEmpty) {
        intentionalEmptyLabel
    } else {
        options.firstOrNull { it.id == selectedPositionId }?.label.orEmpty()
    }
    var query by remember(selectedPositionId, selectedLabel, intentionalEmpty) { mutableStateOf(selectedLabel) }
    var expanded by remember { mutableStateOf(false) }
    val filtered = filterPreferencePositionOptions(options, query)

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { value ->
                query = value
                onEdit()
                if ((selectedPositionId != null || intentionalEmpty) && value != selectedLabel) onSelect(null)
                expanded = true
            },
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text("Type committee or position") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = swapNotice?.let { notice ->
                {
                    Text(
                        text = notice,
                        color = DelegateStatusTone.Yellow.contentColor(),
                    )
                }
            },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = enabled)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(intentionalEmptyLabel) },
                onClick = {
                    query = intentionalEmptyLabel
                    expanded = false
                    onSelectIntentionalEmpty()
                },
            )
            if (filtered.isEmpty()) {
                DropdownMenuItem(text = { Text("No matching positions") }, enabled = false, onClick = {})
            } else {
                filtered.forEach { option ->
                    val usageNotice = preferencePositionUsageNotice(option, editingRank)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.label)
                                usageNotice?.let { notice ->
                                    Text(
                                        text = notice,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DelegateStatusTone.Yellow.contentColor(),
                                    )
                                }
                            }
                        },
                        onClick = {
                            query = option.label
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

internal fun filterPreferencePositionOptions(
    options: List<PreferencePositionOptionUiState>,
    query: String,
): List<PreferencePositionOptionUiState> = options.filter { option ->
    query.isBlank() || option.label.contains(query, ignoreCase = true)
}

internal fun preferencePositionUsageNotice(
    option: PreferencePositionOptionUiState,
    editingRank: Int,
): String? = option.currentRank
    ?.takeIf { it != editingRank }
    ?.let { "Currently preference $it - selecting swaps" }
