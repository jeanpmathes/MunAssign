@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.munika.assignment.ui.screen.dialog

import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.munika.assignment.domain.model.AnswerRule
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.HighSchoolerStatus
import org.munika.assignment.domain.model.LanguageLevel
import org.munika.assignment.domain.model.MinimumSentenceCount
import org.munika.assignment.domain.model.MunExperienceLevel
import org.munika.assignment.domain.model.NoticeWhenAffirmative
import org.munika.assignment.domain.model.QuestionConfiguration
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.ui.viewmodel.EditableAnswerMappingUiState
import org.munika.assignment.ui.viewmodel.EditableQuestionConfigurationUiState
import org.munika.assignment.ui.viewmodel.QuestionConfigurationListUiState
import org.munika.assignment.ui.viewmodel.normalizedForSave
import org.munika.assignment.ui.viewmodel.toEditableQuestionConfigurationUiStates
import org.munika.assignment.ui.viewmodel.validationErrors

@Composable
fun QuestionConfigurationDialog(
    conference: Conference,
    list: QuestionConfigurationListUiState,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (List<QuestionConfiguration>) -> Unit,
) {
    var editStates by remember(conference.id.value, list.questions) {
        mutableStateOf(conference.toEditableQuestionConfigurationUiStates())
    }
    var mappingTargetId by remember(conference.id.value) { mutableStateOf<String?>(null) }
    var labelTargetId by remember(conference.id.value) { mutableStateOf<String?>(null) }
    var handlingTargetId by remember(conference.id.value) { mutableStateOf<String?>(null) }
    val normalizedStates = editStates.normalizedForSave()
    val specialStates = normalizedStates.filter { it.isSpecial }
    val generalStates = normalizedStates.filterNot { it.isSpecial }
    val validationErrors = normalizedStates.flatMap { it.validationErrors() }.distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Questions") },
        text = {
            Column(modifier = Modifier.width(980.dp).fillMaxHeight(0.78f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                validationErrors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(specialStates, key = { it.questionId }) { question ->
                        QuestionRow(
                            state = question,
                            canMoveUp = false,
                            canMoveDown = false,
                            onChange = { updated ->
                                editStates = editStates.replaceQuestion(updated)
                            },
                            onMove = {},
                            onOpenMappings = { mappingTargetId = question.questionId },
                            onOpenLabel = { labelTargetId = question.questionId },
                            onOpenHandling = { handlingTargetId = question.questionId },
                        )
                    }
                    if (specialStates.isNotEmpty() && generalStates.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f))
                                Text("General questions", style = MaterialTheme.typography.labelMedium)
                                HorizontalDivider(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    items(generalStates, key = { it.questionId }) { question ->
                        QuestionRow(
                            state = question,
                            canMoveUp = normalizedStates.canMove(question.questionId, -1),
                            canMoveDown = normalizedStates.canMove(question.questionId, 1),
                            onChange = { updated ->
                                editStates = editStates.replaceQuestion(updated)
                            },
                            onMove = { direction ->
                                editStates = normalizedStates.moveQuestion(question.questionId, direction)
                            },
                            onOpenMappings = { mappingTargetId = question.questionId },
                            onOpenLabel = { labelTargetId = question.questionId },
                            onOpenHandling = { handlingTargetId = question.questionId },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = validationErrors.isEmpty(),
                onClick = {
                    onSave(normalizedStates.map { it.toConfiguration() })
                    onDismiss()
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )

    mappingTargetId?.let { questionId ->
        normalizedStates.firstOrNull { it.questionId == questionId }?.let { question ->
            MappingDialog(
                state = question,
                onChange = { updated ->
                    editStates = editStates.replaceQuestion(updated)
                },
                onDismiss = { mappingTargetId = null },
            )
        }
    }

    labelTargetId?.let { questionId ->
        normalizedStates.firstOrNull { it.questionId == questionId }?.let { question ->
            LabelDialog(
                label = question.organizerLabel,
                onChange = { label ->
                    editStates = editStates.replaceQuestion(question.copy(organizerLabel = label))
                },
                onDismiss = { labelTargetId = null },
            )
        }
    }

    handlingTargetId?.let { questionId ->
        normalizedStates.firstOrNull { it.questionId == questionId }?.let { question ->
            HandlingDialog(
                selectedHandlingId = question.handlingTypeId,
                onSelect = { handlingId ->
                    editStates = editStates.replaceQuestion(
                        question.copy(
                            handlingTypeId = handlingId,
                            rules = defaultRulesForHandling(handlingId),
                        ),
                    )
                    handlingTargetId = null
                },
                onDismiss = { handlingTargetId = null },
            )
        }
    }
}

@Composable
private fun QuestionRow(
    state: EditableQuestionConfigurationUiState,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (EditableQuestionConfigurationUiState) -> Unit,
    onMove: (Int) -> Unit,
    onOpenMappings: () -> Unit,
    onOpenLabel: () -> Unit,
    onOpenHandling: () -> Unit,
) {
    val rowColor = if (state.isSpecial) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = rowColor,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val actionIconTint = MaterialTheme.colorScheme.onSurfaceVariant
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                QuestionText(state.questionText)
                if (state.organizerLabel.isNotBlank()) {
                    Text(state.organizerLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoleCapsule(
                    selectedRoleId = state.roleId,
                    onSelect = { roleId ->
                        onChange(state.withRole(roleId))
                    },
                )
                Spacer(modifier = Modifier.weight(1f))
                TooltipIconButton("Edit label", Icons.AutoMirrored.Filled.Label, enabled = true, tint = actionIconTint, onClick = onOpenLabel)
                VisibilityButton(state = state, tint = actionIconTint, onToggle = { onChange(state.copy(visibleInDelegateDetail = !state.visibleInDelegateDetail)) })
                TooltipIconButton("Edit mappings", Icons.Filled.Settings, enabled = state.selectedRole != null, tint = actionIconTint, onClick = onOpenMappings)
                ReorderButton("Move up", Icons.Filled.ArrowUpward, enabled = canMoveUp, tint = actionIconTint) { onMove(-1) }
                ReorderButton("Move down", Icons.Filled.ArrowDownward, enabled = canMoveDown, tint = actionIconTint) { onMove(1) }
            }
            if (!state.isSpecial) {
                HorizontalDivider()
                val validationSet = state.handlingTypeId.isValidationHandling()
                val validationColor = if (validationSet) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(handlingLabel(state.handlingTypeId), style = MaterialTheme.typography.labelSmall, color = validationColor)
                    TooltipIconButton(
                        tooltip = "Set handling and validation",
                        icon = Icons.Filled.VerifiedUser,
                        enabled = true,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onOpenHandling,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionText(questionText: String) {
    val prefix = "Question:"
    val text = if (questionText.startsWith(prefix)) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                append(prefix)
            }
            append(questionText.removePrefix(prefix))
        }
    } else {
        buildAnnotatedString { append(questionText) }
    }
    Text(text, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun RoleCapsule(selectedRoleId: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val role = StandardQuestionRoles.fromId(selectedRoleId)
    val color = when (role) {
        StandardQuestionRoles.ExperienceLevel -> Color(0xFFE7D7FF)
        StandardQuestionRoles.HighSchoolerStatus -> Color(0xFFF0C8F5)
        StandardQuestionRoles.LanguageLevel -> Color(0xFFD6EEDC)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (role) {
        StandardQuestionRoles.ExperienceLevel -> Color(0xFF3F146F)
        StandardQuestionRoles.HighSchoolerStatus -> Color(0xFF7B1FA2)
        StandardQuestionRoles.LanguageLevel -> Color(0xFF173F29)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(999.dp), color = color, contentColor = contentColor) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 12.dp, end = 2.dp, top = 0.dp, bottom = 0.dp),
        ) {
            Text(role?.name ?: "No Role", style = MaterialTheme.typography.labelMedium)
            TooltipIconButton("Set role", Icons.Filled.KeyboardArrowDown, enabled = true) { expanded = true }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("No Role") }, onClick = {
                    expanded = false
                    onSelect(null)
                })
                StandardQuestionRoles.all.forEach { option ->
                    DropdownMenuItem(text = { Text(option.name) }, onClick = {
                        expanded = false
                        onSelect(option.id)
                    })
                }
            }
        }
    }
}

@Composable
private fun VisibilityButton(state: EditableQuestionConfigurationUiState, tint: Color, onToggle: () -> Unit) {
    val icon = when {
        state.isSpecial -> Icons.Filled.Block
        state.visibleInDelegateDetail -> Icons.Filled.Visibility
        else -> Icons.Filled.VisibilityOff
    }
    val tooltip = when {
        state.isSpecial -> "Special questions are not shown in the answer overview"
        state.visibleInDelegateDetail -> "Shown in delegate detail"
        else -> "Hidden from delegate detail"
    }
    TooltipIconButton(tooltip, icon, enabled = !state.isSpecial, tint = tint, onClick = onToggle)
}

@Composable
private fun LabelDialog(label: String, onChange: (String) -> Unit, onDismiss: () -> Unit) {
    var draft by remember(label) { mutableStateOf(label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Question label") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Organizer label") },
                singleLine = true,
                modifier = Modifier.width(520.dp),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onChange(draft)
                    onDismiss()
                },
            ) {
                Text("OK")
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
private fun HandlingDialog(selectedHandlingId: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Handling") },
        text = {
            Column(modifier = Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HandlingOption(
                    selected = selectedHandlingId == StandardQuestionHandlingTypes.Default.id,
                    title = "No special validation",
                    body = "Show the answer without adding warnings or notices.",
                    onClick = { onSelect(StandardQuestionHandlingTypes.Default.id) },
                )
                HandlingOption(
                    selected = selectedHandlingId == StandardQuestionHandlingTypes.VisaSupport.id,
                    title = "Visa",
                    body = "Notify when delegates require visa assistance.",
                    onClick = { onSelect(StandardQuestionHandlingTypes.VisaSupport.id) },
                )
                HandlingOption(
                    selected = selectedHandlingId == StandardQuestionHandlingTypes.CommitteeChoiceExplanation.id,
                    title = "Explanation",
                    body = "Warn when delegates have not provided a thorough enough explaination.",
                    onClick = { onSelect(StandardQuestionHandlingTypes.CommitteeChoiceExplanation.id) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun HandlingOption(selected: Boolean, title: String, body: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MappingDialog(
    state: EditableQuestionConfigurationUiState,
    onChange: (EditableQuestionConfigurationUiState) -> Unit,
    onDismiss: () -> Unit,
) {
    val role = state.selectedRole ?: return
    val mappings = state.mappingsForSelectedRole()
    val errors = mappings.validationErrors(role.name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${role.name} mappings") },
        text = {
            Column(modifier = Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    mappings.forEachIndexed { index, mapping ->
                        MappingRow(
                            roleId = role.id,
                            mapping = mapping,
                            onChange = { updated ->
                                onChange(state.withSelectedRoleMappings(mappings.toMutableList().also { it[index] = updated }))
                            },
                            onDelete = {
                                onChange(state.withSelectedRoleMappings(mappings.toMutableList().also { it.removeAt(index) }))
                            },
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        onChange(state.withSelectedRoleMappings(mappings + EditableAnswerMappingUiState("", firstMappedValueId(role.id))))
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Add mapping")
                }
            }
        },
        confirmButton = {
            Button(enabled = errors.isEmpty(), onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}

@Composable
private fun MappingRow(
    roleId: String,
    mapping: EditableAnswerMappingUiState,
    onChange: (EditableAnswerMappingUiState) -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = mapping.rawValue,
            onValueChange = { onChange(mapping.copy(rawValue = it)) },
            readOnly = mapping.isObserved,
            label = { Text("Raw answer") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        DropdownField(
            label = "Maps to",
            selectedLabel = mappedValueLabel(roleId, mapping.mappedValueId),
            options = mappedValueOptions(roleId),
            onSelect = { onChange(mapping.copy(mappedValueId = it)) },
            modifier = Modifier.weight(1f),
        )
        ReorderButton(
            tooltip = if (mapping.isObserved) "Observed answers cannot be removed." else "Remove mapping",
            icon = Icons.Filled.Delete,
            enabled = !mapping.isObserved,
            onClick = onDelete,
        )
    }
}

@Composable
private fun <T> DropdownField(
    label: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun ReorderButton(
    tooltip: String,
    icon: ImageVector,
    enabled: Boolean,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) = TooltipIconButton(tooltip, icon, enabled, tint, onClick)

@Composable
private fun TooltipIconButton(
    tooltip: String,
    icon: ImageVector,
    enabled: Boolean,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(38.dp)) {
            val iconTint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
            if (iconTint == Color.Unspecified) {
                Icon(icon, contentDescription = tooltip, modifier = Modifier.size(22.dp))
            } else {
                Icon(icon, contentDescription = tooltip, modifier = Modifier.size(22.dp), tint = iconTint)
            }
        }
    }
}

private fun defaultRulesForHandling(handlingId: String): List<AnswerRule> =
    when (handlingId) {
        StandardQuestionHandlingTypes.CommitteeChoiceExplanation.id -> listOf(MinimumSentenceCount(3))
        StandardQuestionHandlingTypes.VisaSupport.id -> listOf(NoticeWhenAffirmative("Visa support requested."))
        else -> emptyList()
    }

private fun handlingLabel(handlingId: String): String =
    when (handlingId) {
        StandardQuestionHandlingTypes.CommitteeChoiceExplanation.id -> "Explanation validation"
        StandardQuestionHandlingTypes.VisaSupport.id -> "Visa validation"
        else -> "No special validation"
    }

private fun String.isValidationHandling(): Boolean =
    this == StandardQuestionHandlingTypes.CommitteeChoiceExplanation.id ||
        this == StandardQuestionHandlingTypes.VisaSupport.id

private fun List<EditableQuestionConfigurationUiState>.replaceQuestion(
    updated: EditableQuestionConfigurationUiState,
): List<EditableQuestionConfigurationUiState> =
    map { if (it.questionId == updated.questionId) updated else it }

private fun List<EditableQuestionConfigurationUiState>.canMove(questionId: String, direction: Int): Boolean {
    val general = filterNot { it.isSpecial }
    val index = general.indexOfFirst { it.questionId == questionId }
    if (index == -1) return false
    val target = index + direction
    return target in general.indices
}

private fun List<EditableQuestionConfigurationUiState>.moveQuestion(
    questionId: String,
    direction: Int,
): List<EditableQuestionConfigurationUiState> {
    val special = filter { it.isSpecial }
    val general = filterNot { it.isSpecial }.toMutableList()
    val index = general.indexOfFirst { it.questionId == questionId }
    val target = index + direction
    if (index == -1 || target !in general.indices) return this
    val item = general.removeAt(index)
    general.add(target, item)
    return (special + general).mapIndexed { displayOrder, question -> question.copy(displayOrder = displayOrder) }
}

private fun EditableQuestionConfigurationUiState.mappingsForSelectedRole(): List<EditableAnswerMappingUiState> =
    when (selectedRole) {
        StandardQuestionRoles.ExperienceLevel -> munExperienceMappings
        StandardQuestionRoles.HighSchoolerStatus -> highSchoolerMappings
        StandardQuestionRoles.LanguageLevel -> languageLevelMappings
        else -> emptyList()
    }

private fun EditableQuestionConfigurationUiState.withSelectedRoleMappings(
    mappings: List<EditableAnswerMappingUiState>,
): EditableQuestionConfigurationUiState =
    when (selectedRole) {
        StandardQuestionRoles.ExperienceLevel -> copy(munExperienceMappings = mappings)
        StandardQuestionRoles.HighSchoolerStatus -> copy(highSchoolerMappings = mappings)
        StandardQuestionRoles.LanguageLevel -> copy(languageLevelMappings = mappings)
        else -> this
    }

private fun mappedValueOptions(roleId: String): List<Pair<String, String>> =
    when (roleId) {
        StandardQuestionRoles.ExperienceLevel.id -> MunExperienceLevel.entries.map { it.name to it.label }
        StandardQuestionRoles.HighSchoolerStatus.id -> HighSchoolerStatus.entries.map { it.name to it.label }
        StandardQuestionRoles.LanguageLevel.id -> LanguageLevel.entries.map { it.name to it.label }
        else -> emptyList()
    }

private fun firstMappedValueId(roleId: String): String =
    mappedValueOptions(roleId).first().first

private fun mappedValueLabel(roleId: String, valueId: String?): String =
    valueId?.let { selected -> mappedValueOptions(roleId).firstOrNull { it.first == selected }?.second }
        ?: "Select mapping"

private val MunExperienceLevel.label: String
    get() = when (this) {
        MunExperienceLevel.FirstTime -> "First time"
        MunExperienceLevel.Beginner -> "Beginner"
        MunExperienceLevel.Intermediate -> "Intermediate"
        MunExperienceLevel.Advanced -> "Advanced"
    }

private val HighSchoolerStatus.label: String
    get() = when (this) {
        HighSchoolerStatus.Yes -> "Yes"
        HighSchoolerStatus.No -> "No"
    }

private val LanguageLevel.label: String
    get() = when (this) {
        LanguageLevel.BeginnerA1A2 -> "A1/A2"
        LanguageLevel.IndependentUserB1B2 -> "B1/B2"
        LanguageLevel.ProficientUserC1C2 -> "C1/C2"
    }
