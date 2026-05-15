package org.munika.assignment.ui.viewmodel

import org.munika.assignment.domain.model.AnswerRule
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.HighSchoolerAnswerMapping
import org.munika.assignment.domain.model.HighSchoolerStatus
import org.munika.assignment.domain.model.LanguageLevel
import org.munika.assignment.domain.model.LanguageLevelAnswerMapping
import org.munika.assignment.domain.model.MunExperienceAnswerMapping
import org.munika.assignment.domain.model.MunExperienceLevel
import org.munika.assignment.domain.model.QuestionConfiguration
import org.munika.assignment.domain.model.QuestionHandlingType
import org.munika.assignment.domain.model.QuestionId
import org.munika.assignment.domain.model.QuestionRole
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.TypedAnswerMappings
import org.munika.assignment.domain.model.toConfiguration

data class QuestionConfigurationListUiState(
    val questions: List<QuestionConfigurationSummaryUiState>,
)

data class QuestionConfigurationSummaryUiState(
    val id: String,
    val label: String,
    val questionText: String,
    val roleLabel: String,
    val visibleInDelegateDetail: Boolean,
    val displayOrder: Int,
    val warningCount: Int,
    val noticeCount: Int,
)

data class EditableQuestionConfigurationUiState(
    val questionId: String,
    val questionText: String,
    val roleId: String?,
    val handlingTypeId: String,
    val visibleInDelegateDetail: Boolean,
    val organizerLabel: String,
    val displayOrder: Int,
    val rules: List<AnswerRule>,
    val munExperienceMappings: List<EditableAnswerMappingUiState>,
    val highSchoolerMappings: List<EditableAnswerMappingUiState>,
    val languageLevelMappings: List<EditableAnswerMappingUiState>,
) {
    val selectedRole: QuestionRole?
        get() = StandardQuestionRoles.fromId(roleId)

    val selectedHandling: QuestionHandlingType
        get() = StandardQuestionHandlingTypes.fromId(handlingTypeId)

    val isSpecial: Boolean
        get() = selectedRole != null

    fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (StandardQuestionHandlingTypes.all.none { it.id == handlingTypeId }) {
            errors += "Select a handling type."
        }
        if (roleId != null && StandardQuestionRoles.fromId(roleId) == null) {
            errors += "Select a valid role."
        }
        errors += selectedMappings().validationErrors(selectedRole?.name ?: "Mapping")
        return errors
    }

    fun toConfiguration(): QuestionConfiguration =
        QuestionConfiguration(
            questionId = QuestionId(questionId),
            questionText = questionText,
            role = selectedRole,
            handling = selectedHandling,
            visibleInDelegateDetail = selectedRole == null && visibleInDelegateDetail,
            organizerLabel = organizerLabel.trim().takeIf { it.isNotBlank() },
            displayOrder = displayOrder,
            rules = rules,
            mappings = TypedAnswerMappings(
                munExperience = munExperienceMappings.toMunExperienceMappings(),
                highSchooler = highSchoolerMappings.toHighSchoolerMappings(),
                languageLevel = languageLevelMappings.toLanguageLevelMappings(),
            ),
        )

    private fun selectedMappings(): List<EditableAnswerMappingUiState> =
        when (selectedRole) {
            StandardQuestionRoles.ExperienceLevel -> munExperienceMappings
            StandardQuestionRoles.HighSchoolerStatus -> highSchoolerMappings
            StandardQuestionRoles.LanguageLevel -> languageLevelMappings
            null -> emptyList()
            else -> emptyList()
        }
}

data class EditableAnswerMappingUiState(
    val rawValue: String,
    val mappedValueId: String,
)

fun Conference.toQuestionConfigurationListUiState(): QuestionConfigurationListUiState =
    QuestionConfigurationListUiState(
        questions = questions.sortedWith(questionDisplayComparator()).map { question ->
            val answers = delegates.flatMap { it.answers }.filter { it.question === question }
            QuestionConfigurationSummaryUiState(
                id = question.id.value,
                label = question.displayLabel,
                questionText = question.questionText,
                roleLabel = question.role?.name ?: "General",
                visibleInDelegateDetail = question.role == null && question.visibleInDelegateDetail,
                displayOrder = question.displayOrder,
                warningCount = answers.sumOf { answer -> answer.issues.count { it.severity.name == "Warning" } },
                noticeCount = answers.sumOf { answer -> answer.issues.count { it.severity.name == "Notice" } },
            )
        },
    )

fun Conference.toEditableQuestionConfigurationUiStates(): List<EditableQuestionConfigurationUiState> =
    questions.sortedWith(questionDisplayComparator()).map { it.toConfiguration().toEditableUiState() }

fun QuestionConfiguration.toEditableUiState(): EditableQuestionConfigurationUiState =
    EditableQuestionConfigurationUiState(
        questionId = questionId.value,
        questionText = questionText,
        roleId = role?.id,
        handlingTypeId = handling.id,
        visibleInDelegateDetail = role == null && visibleInDelegateDetail,
        organizerLabel = organizerLabel.orEmpty(),
        displayOrder = displayOrder,
        rules = rules,
        munExperienceMappings = mappings.munExperience.map { EditableAnswerMappingUiState(it.rawValue, it.value.name) },
        highSchoolerMappings = mappings.highSchooler.map { EditableAnswerMappingUiState(it.rawValue, it.value.name) },
        languageLevelMappings = mappings.languageLevel.map { EditableAnswerMappingUiState(it.rawValue, it.value.name) },
    )

fun List<EditableQuestionConfigurationUiState>.normalizedForSave(): List<EditableQuestionConfigurationUiState> =
    sortedWith(compareByDescending<EditableQuestionConfigurationUiState> { it.isSpecial }.thenBy { it.displayOrder }.thenBy { it.questionText })
        .mapIndexed { index, question -> question.copy(displayOrder = index) }

fun List<EditableAnswerMappingUiState>.validationErrors(label: String): List<String> {
    val errors = mutableListOf<String>()
    if (any { it.rawValue.isBlank() }) {
        errors += "$label mapping raw values must not be blank."
    }
    val normalizedValues = map { it.rawValue.trim().lowercase() }
    if (normalizedValues.size != normalizedValues.toSet().size) {
        errors += "$label mapping raw values must be unique."
    }
    return errors
}

private fun questionDisplayComparator(): Comparator<org.munika.assignment.domain.model.Question> =
    compareByDescending<org.munika.assignment.domain.model.Question> { it.role != null }
        .thenBy { it.displayOrder }
        .thenBy { it.questionText }

private fun List<EditableAnswerMappingUiState>.toMunExperienceMappings(): List<MunExperienceAnswerMapping> =
    map { MunExperienceAnswerMapping(it.rawValue.trim(), MunExperienceLevel.valueOf(it.mappedValueId)) }

private fun List<EditableAnswerMappingUiState>.toHighSchoolerMappings(): List<HighSchoolerAnswerMapping> =
    map { HighSchoolerAnswerMapping(it.rawValue.trim(), HighSchoolerStatus.valueOf(it.mappedValueId)) }

private fun List<EditableAnswerMappingUiState>.toLanguageLevelMappings(): List<LanguageLevelAnswerMapping> =
    map { LanguageLevelAnswerMapping(it.rawValue.trim(), LanguageLevel.valueOf(it.mappedValueId)) }
