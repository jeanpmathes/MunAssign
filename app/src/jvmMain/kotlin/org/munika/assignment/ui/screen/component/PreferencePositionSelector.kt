package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

data class PreferencePositionOptionUiState(
    val id: String,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencePositionSelector(
    selectedPositionId: String?,
    options: List<PreferencePositionOptionUiState>,
    excludedPositionIds: Set<String>,
    label: String,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    intentionalEmpty: Boolean = false,
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
    val filtered = filterPreferencePositionOptions(options, excludedPositionIds, query)

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { value ->
                query = value
                if ((selectedPositionId != null || intentionalEmpty) && value != selectedLabel) onSelect(null)
                expanded = true
            },
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text("Type committee or position") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
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
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            query = option.label
                            expanded = false
                            onSelect(option.id)
                        },
                    )
                }
            }
        }
    }
}

internal fun filterPreferencePositionOptions(
    options: List<PreferencePositionOptionUiState>,
    excludedPositionIds: Set<String>,
    query: String,
): List<PreferencePositionOptionUiState> = options.filter { option ->
    option.id !in excludedPositionIds && (query.isBlank() || option.label.contains(query, ignoreCase = true))
}
