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
import org.munika.assignment.domain.model.normalizeAnswer
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
    val visibleInVersusView: Boolean = false,
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
    val observedRawValues: List<String> = emptyList(),
    val visibleInVersusView: Boolean = false,
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
            errors += "Select a check type."
        }
        if (roleId != null && StandardQuestionRoles.fromId(roleId) == null) {
            errors += "Select a valid profile field."
        }
        val mappings = selectedMappings()
        errors += mappings.validationErrors(selectedRole?.name ?: "Mapping")
        val hasUnmappedObservedAnswer = observedRawValues.any { observedRawValue ->
            val observedValue = normalizeAnswer(observedRawValue)
            mappings.none { mapping ->
                normalizeAnswer(mapping.rawValue) == observedValue && mapping.mappedValueId != null
            }
        }
        if (selectedRole != null && hasUnmappedObservedAnswer) {
            errors += "Every observed answer must have a selected mapping."
        }
        return errors
    }

    fun toConfiguration(): QuestionConfiguration {
        val errors = validationErrors()
        require(errors.isEmpty()) { errors.joinToString(" ") }
        return QuestionConfiguration(
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
            visibleInVersusView = visibleInVersusView,
        )
    }

    fun withRole(roleId: String?): EditableQuestionConfigurationUiState =
        copy(
            roleId = roleId,
            handlingTypeId = if (roleId == null) {
                StandardQuestionHandlingTypes.Default.id
            } else {
                StandardQuestionHandlingTypes.ExpectedTypedAnswer.id
            },
            visibleInDelegateDetail = roleId == null && visibleInDelegateDetail,
            rules = emptyList(),
            munExperienceMappings = munExperienceMappings.withoutUnmappedObservedRows(),
            highSchoolerMappings = highSchoolerMappings.withoutUnmappedObservedRows(),
            languageLevelMappings = languageLevelMappings.withoutUnmappedObservedRows(),
        ).withObservedAnswers(observedRawValues)

    private fun selectedMappings(): List<EditableAnswerMappingUiState> =
        when (selectedRole) {
            StandardQuestionRoles.ExperienceLevel -> munExperienceMappings
            StandardQuestionRoles.HighSchoolerStatus -> highSchoolerMappings
            StandardQuestionRoles.LanguageLevel -> languageLevelMappings
            null -> emptyList()
            else -> emptyList()
        }
}

private fun List<EditableAnswerMappingUiState>.withoutUnmappedObservedRows(): List<EditableAnswerMappingUiState> =
    filter { it.mappedValueId != null }.map { it.copy(isObserved = false) }

data class EditableAnswerMappingUiState(
    val rawValue: String,
    val mappedValueId: String?,
    val isObserved: Boolean = false,
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
                warningCount = answers.sumOf { answer -> answer.openIssues.count { it.severity.name == "Warning" } },
                noticeCount = answers.sumOf { answer -> answer.openIssues.count { it.severity.name == "Notice" } },
                visibleInVersusView = question.visibleInVersusView,
            )
        },
    )

fun Conference.toEditableQuestionConfigurationUiStates(): List<EditableQuestionConfigurationUiState> =
    questions.sortedWith(questionDisplayComparator()).map { question ->
        question.toConfiguration().toEditableUiState().withObservedAnswers(
            delegates.asSequence()
                .flatMap { it.answers.asSequence() }
                .filter { it.question.id == question.id }
                .mapNotNull { it.rawAnswer?.takeIf(String::isNotBlank) }
                .toList(),
        )
    }

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
        visibleInVersusView = visibleInVersusView,
    )

fun List<EditableQuestionConfigurationUiState>.normalizedForSave(): List<EditableQuestionConfigurationUiState> =
    sortedWith(compareByDescending<EditableQuestionConfigurationUiState> { it.isSpecial }.thenBy { it.displayOrder }.thenBy { it.questionText })
        .mapIndexed { index, question -> question.copy(displayOrder = index) }

fun List<EditableAnswerMappingUiState>.validationErrors(label: String): List<String> {
    val errors = mutableListOf<String>()
    if (any { it.rawValue.isBlank() }) {
        errors += "$label mapping raw values must not be blank."
    }
    if (any { it.mappedValueId == null }) {
        errors += "$label mappings must all have a selected mapping."
    }
    val normalizedValues = map { normalizeAnswer(it.rawValue) }
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
    map { MunExperienceAnswerMapping(it.rawValue.trim(), MunExperienceLevel.valueOf(requireNotNull(it.mappedValueId))) }

private fun List<EditableAnswerMappingUiState>.toHighSchoolerMappings(): List<HighSchoolerAnswerMapping> =
    map { HighSchoolerAnswerMapping(it.rawValue.trim(), HighSchoolerStatus.valueOf(requireNotNull(it.mappedValueId))) }

private fun List<EditableAnswerMappingUiState>.toLanguageLevelMappings(): List<LanguageLevelAnswerMapping> =
    map { LanguageLevelAnswerMapping(it.rawValue.trim(), LanguageLevel.valueOf(requireNotNull(it.mappedValueId))) }

private fun EditableQuestionConfigurationUiState.withObservedAnswers(
    observedRawAnswers: List<String>,
): EditableQuestionConfigurationUiState {
    val distinctObservedRawAnswers = observedRawAnswers.distinctBy(::normalizeAnswer)
    val observedNormalizedValues = distinctObservedRawAnswers.mapTo(linkedSetOf()) { normalizeAnswer(it) }
    val stateWithObservedAnswers = copy(observedRawValues = distinctObservedRawAnswers)

    fun List<EditableAnswerMappingUiState>.mergeObserved(): List<EditableAnswerMappingUiState> {
        val configuredMappings = map { mapping ->
            mapping.copy(isObserved = normalizeAnswer(mapping.rawValue) in observedNormalizedValues)
        }
        val normalizedValues = configuredMappings.mapTo(mutableSetOf()) { normalizeAnswer(it.rawValue) }
        val observedMappings = distinctObservedRawAnswers.mapNotNull { rawAnswer ->
            val normalized = normalizeAnswer(rawAnswer)
            if (normalized.isBlank() || !normalizedValues.add(normalized)) {
                null
            } else {
                EditableAnswerMappingUiState(rawValue = rawAnswer, mappedValueId = null, isObserved = true)
            }
        }
        return configuredMappings + observedMappings
    }

    return when (stateWithObservedAnswers.selectedRole) {
        StandardQuestionRoles.ExperienceLevel -> stateWithObservedAnswers.copy(
            munExperienceMappings = munExperienceMappings.mergeObserved(),
        )
        StandardQuestionRoles.HighSchoolerStatus -> stateWithObservedAnswers.copy(
            highSchoolerMappings = highSchoolerMappings.mergeObserved(),
        )
        StandardQuestionRoles.LanguageLevel -> stateWithObservedAnswers.copy(
            languageLevelMappings = languageLevelMappings.mergeObserved(),
        )
        else -> stateWithObservedAnswers
    }
}
