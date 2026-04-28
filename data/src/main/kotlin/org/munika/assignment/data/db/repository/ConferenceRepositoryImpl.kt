package org.munika.assignment.data.db.repository

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.munika.assignment.data.db.DatabaseFactory
import org.munika.assignment.data.db.mapper.ConferenceGraphMapper
import org.munika.assignment.data.db.model.CommitteeRow
import org.munika.assignment.data.db.model.ConferenceGraphRows
import org.munika.assignment.data.db.model.ConferenceRow
import org.munika.assignment.data.db.model.DelegateCommentRow
import org.munika.assignment.data.db.model.DelegateQuestionAnswerRow
import org.munika.assignment.data.db.model.DelegateQuestionRow
import org.munika.assignment.data.db.model.DelegateQuestionRuleRow
import org.munika.assignment.data.db.model.DelegateRow
import org.munika.assignment.data.db.model.DelegationRow
import org.munika.assignment.data.db.model.HighSchoolerAnswerMappingRow
import org.munika.assignment.data.db.model.LanguageLevelAnswerMappingRow
import org.munika.assignment.data.db.model.MunExperienceAnswerMappingRow
import org.munika.assignment.data.db.model.PositionRow
import org.munika.assignment.data.db.model.PreferenceRow
import org.munika.assignment.data.db.model.SlotRow
import org.munika.assignment.data.db.tables.CommitteeTable
import org.munika.assignment.data.db.tables.ConferenceTable
import org.munika.assignment.data.db.tables.DelegateCommentTable
import org.munika.assignment.data.db.tables.DelegateQuestionAnswerTable
import org.munika.assignment.data.db.tables.DelegateQuestionRuleTable
import org.munika.assignment.data.db.tables.DelegateQuestionTable
import org.munika.assignment.data.db.tables.DelegateTable
import org.munika.assignment.data.db.tables.DelegationTable
import org.munika.assignment.data.db.tables.HighSchoolerAnswerMappingTable
import org.munika.assignment.data.db.tables.LanguageLevelAnswerMappingTable
import org.munika.assignment.data.db.tables.MunExperienceAnswerMappingTable
import org.munika.assignment.data.db.tables.MymunUpdateIssueTable
import org.munika.assignment.data.db.tables.MymunUpdateRunTable
import org.munika.assignment.data.db.tables.PositionTable
import org.munika.assignment.data.db.tables.PreferenceTable
import org.munika.assignment.data.db.tables.SlotTable
import org.munika.assignment.data.excel.CellLocation
import org.munika.assignment.data.excel.MymunConfirmedAssignmentCandidate
import org.munika.assignment.data.excel.MymunDelegateRow
import org.munika.assignment.data.excel.MymunDelegationRow
import org.munika.assignment.data.excel.MymunValidationSeverity
import org.munika.assignment.data.excel.MymunWorkbookSnapshot
import org.munika.assignment.domain.model.AssignmentSource
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.repository.ConferenceRepository
import org.munika.assignment.domain.repository.UpdateIssue
import org.munika.assignment.domain.repository.UpdateIssueSeverity
import org.munika.assignment.domain.repository.UpdateResult
import org.munika.assignment.domain.repository.UpdateSummary
import org.slf4j.LoggerFactory

/**
 * SQLite-backed conference repository.
 */
class ConferenceRepositoryImpl(
    private val database: Database = DatabaseFactory.connect(),
) : ConferenceRepository {
    private val logger = LoggerFactory.getLogger(ConferenceRepositoryImpl::class.java)

    override suspend fun listConferences(): List<Conference> = db {
        ConferenceTable.selectAll()
            .orderBy(ConferenceTable.updatedAtEpochMillis)
            .map { row ->
                ConferenceGraphMapper.toDomain(
                    ConferenceGraphRows(
                        conference = row.toConferenceRow(),
                        committees = emptyList(),
                        positions = emptyList(),
                        slots = emptyList(),
                        delegations = emptyList(),
                        delegates = emptyList(),
                        preferences = emptyList(),
                        comments = emptyList(),
                        questions = emptyList(),
                        questionRules = emptyList(),
                        munExperienceMappings = emptyList(),
                        highSchoolerMappings = emptyList(),
                        languageLevelMappings = emptyList(),
                        answers = emptyList(),
                    ),
                )
            }
    }

    override suspend fun createConference(name: String): Conference = db {
        val now = Instant.now().toEpochMilli()
        val id = UUID.randomUUID().toString()
        ConferenceTable.insert {
            it[ConferenceTable.id] = id
            it[ConferenceTable.name] = name.trim()
            it[ConferenceTable.createdAtEpochMillis] = now
            it[ConferenceTable.updatedAtEpochMillis] = now
            it[ConferenceTable.lastMymunUpdateAtEpochMillis] = null
        }
        loadConferenceGraph(id)
    }

    override suspend fun renameConference(conferenceId: String, newName: String): Conference = db {
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[ConferenceTable.name] = newName.trim()
            it[ConferenceTable.updatedAtEpochMillis] = Instant.now().toEpochMilli()
        }
        loadConferenceGraph(conferenceId)
    }

    override suspend fun deleteConference(conferenceId: String) {
        db { ConferenceTable.deleteWhere { ConferenceTable.id eq conferenceId } }
    }

    override suspend fun getConference(conferenceId: String): Conference? = db {
        if (ConferenceTable.selectAll().where { ConferenceTable.id eq conferenceId }.empty()) {
            return@db null
        }
        loadConferenceGraph(conferenceId)
    }

    /**
     * Applies an already parsed MyMUN update snapshot to one conference.
     *
     * MyMUN-managed rows are reconciled by workbook identity while organizer-managed state remains
     * attached to stable local IDs whenever the referenced workbook object still exists.
     */
    suspend fun applyMymunUpdateSnapshot(
        conferenceId: String,
        snapshot: MymunWorkbookSnapshot,
        sourceFilename: String? = snapshot.workbookName,
    ): UpdateResult = db {
        logger.info(
            "Starting MyMUN update for conference={} source={} delegates={} positions={} delegations={} confirmedAssignments={} parserIssues={}",
            conferenceId,
            sourceFilename,
            snapshot.delegates.size,
            snapshot.countryPositions.size,
            snapshot.delegations.size,
            snapshot.confirmedAssignmentCandidates.size,
            snapshot.issues.size,
        )
        val updateRunId = UUID.randomUUID().toString()
        val now = Instant.now().toEpochMilli()
        val delta = MutableUpdateSummary()
        MymunUpdateRunTable.insert {
            it[MymunUpdateRunTable.id] = updateRunId
            it[MymunUpdateRunTable.conferenceId] = conferenceId
            it[MymunUpdateRunTable.startedAtEpochMillis] = now
            it[MymunUpdateRunTable.completedAtEpochMillis] = null
            it[MymunUpdateRunTable.sourceFilename] = sourceFilename
        }

        val issues = snapshot.issues.map { it.toUpdateIssue() }.toMutableList()

        val existingCommittees = CommitteeTable
            .selectAll()
            .where { CommitteeTable.conferenceId eq conferenceId }
            .associateBy { normalize(it[CommitteeTable.normalizedMymunName]) }
        val existingPositions = PositionTable
            .selectAll()
            .where { PositionTable.conferenceId eq conferenceId }
            .associateBy { it[PositionTable.committeeId] to normalize(it[PositionTable.normalizedName]) }
        val existingDelegations = DelegationTable
            .selectAll()
            .where { DelegationTable.conferenceId eq conferenceId }
            .associateBy { normalize(it[DelegationTable.normalizedName]) }
        val existingDelegateRows = DelegateTable
            .selectAll()
            .where { DelegateTable.conferenceId eq conferenceId }
            .toList()
        val existingDelegates = existingDelegateRows.map { ExistingDelegate.from(it) }
        val existingDelegateRowsById = existingDelegateRows.associateBy { it[DelegateTable.id] }
        val existingQuestions = DelegateQuestionTable
            .selectAll()
            .where { DelegateQuestionTable.conferenceId eq conferenceId }
            .associateBy { normalize(it[DelegateQuestionTable.normalizedQuestionText]) }

        val requiredSlotsByCommittee = snapshot.confirmedAssignmentCandidates
            .groupBy { normalize(it.committeeName) }
            .mapValues { (_, candidates) -> candidates.maxOfOrNull { it.slotIndex } ?: 1 }

        requiredSlotsByCommittee.forEach { (committee, requiredSlots) ->
            val configured = existingCommittees[committee]?.get(CommitteeTable.slotsPerPosition)
            if (configured != null && requiredSlots > configured) {
                val location = snapshot.confirmedAssignmentCandidates
                    .filter { normalize(it.committeeName) == committee }
                    .maxByOrNull { it.slotIndex }
                    ?.countriesDelegatesLocation()
                issues += UpdateIssue(
                    severity = UpdateIssueSeverity.Error,
                    code = "slot_count_mismatch",
                    message = "Committee $committee has $configured slots configured but workbook requires $requiredSlots.",
                    sheetName = location?.sheetName,
                    rowNumber = location?.rowNumber,
                    columnNumber = location?.columnNumber,
                    columnName = location?.columnName,
                )
            }
        }

        val assignmentResolutions = snapshot.confirmedAssignmentCandidates.mapNotNull { candidate ->
            when (val resolution = resolveConfirmedAssignment(candidate, snapshot.delegates)) {
                is AssignmentResolution.Resolved -> candidate to resolution.delegate
                is AssignmentResolution.WarningMatch -> {
                    issues += UpdateIssue(
                        severity = UpdateIssueSeverity.Warning,
                        code = "first_name_only_assignment",
                        message = resolution.message,
                        sheetName = candidate.countriesDelegatesLocation().sheetName,
                        rowNumber = candidate.countriesDelegatesLocation().rowNumber,
                        columnNumber = candidate.countriesDelegatesLocation().columnNumber,
                        columnName = candidate.countriesDelegatesLocation().columnName,
                    )
                    candidate to resolution.delegate
                }
                is AssignmentResolution.Unresolved -> {
                    issues += UpdateIssue(
                        severity = UpdateIssueSeverity.Error,
                        code = "unmatched_confirmed_delegate",
                        message = resolution.message,
                        sheetName = candidate.countriesDelegatesLocation().sheetName,
                        rowNumber = candidate.countriesDelegatesLocation().rowNumber,
                        columnNumber = candidate.countriesDelegatesLocation().columnNumber,
                        columnName = candidate.countriesDelegatesLocation().columnName,
                    )
                    null
                }
            }
        }
        assignmentResolutions
            .groupBy { (_, delegate) -> delegate.matchingIdentity }
            .filterValues { it.size > 1 }
            .forEach { (_, assignments) ->
                val delegate = assignments.first().second
                val positions = assignments.joinToString { (candidate, _) -> "${candidate.committeeName} (${candidate.positionName})" }
                issues += UpdateIssue(
                    severity = UpdateIssueSeverity.Error,
                    code = "duplicate_confirmed_assignment",
                    message = "Confirmed assignment delegate '${delegate.displayName}' appears in more than one slot: $positions.",
                )
            }

        issues.forEach { issue ->
            insertUpdateIssue(updateRunId, issue)
        }

        val allBlocking = issues.filter { it.severity == UpdateIssueSeverity.Error }
        if (allBlocking.isNotEmpty()) {
            MymunUpdateRunTable.update({ MymunUpdateRunTable.id eq updateRunId }) {
                it[MymunUpdateRunTable.completedAtEpochMillis] = Instant.now().toEpochMilli()
            }
            val summary = delta.toSummary(issues)
            logger.warn(
                "MyMUN update aborted for conference={} source={} errors={} warnings={}",
                conferenceId,
                sourceFilename,
                summary.blockingErrorCount,
                summary.warningCount,
            )
            return@db UpdateResult(success = false, issues = issues, summary = summary)
        }

        val delegationIdsByName = mutableMapOf<String, String>()
        val retainedCommitteeIds = mutableSetOf<String>()
        val retainedPositionIds = mutableSetOf<String>()
        val retainedDelegationIds = mutableSetOf<String>()
        val retainedDelegateIds = mutableSetOf<String>()
        val retainedQuestionIds = mutableSetOf<String>()
        val pendingBeforeUpdate = countPendingAssignmentsForConference(conferenceId)

        snapshot.delegations.forEach { delegation ->
            val normalizedDelegationName = normalize(delegation.name)
            val existingDelegation = existingDelegations[normalizedDelegationName]
            val delegationId = existingDelegation?.get(DelegationTable.id) ?: UUID.randomUUID().toString()
            delegationIdsByName[normalize(delegation.name)] = delegationId
            retainedDelegationIds += delegationId
            if (existingDelegation != null) {
                if (delegationChanged(existingDelegation, delegation)) {
                    DelegationTable.update({ DelegationTable.id eq delegationId }) {
                        it[DelegationTable.name] = delegation.name
                        it[DelegationTable.normalizedName] = normalizedDelegationName
                        it[DelegationTable.country] = delegation.country
                        it[DelegationTable.expectedDelegateCount] = delegation.expectedDelegates
                        it[DelegationTable.currentDelegateCountFromMymun] = delegation.currentDelegates
                    }
                    delta.delegationsUpdated++
                }
            } else {
                DelegationTable.insert {
                    it[DelegationTable.id] = delegationId
                    it[DelegationTable.conferenceId] = conferenceId
                    it[DelegationTable.name] = delegation.name
                    it[DelegationTable.normalizedName] = normalizedDelegationName
                    it[DelegationTable.country] = delegation.country
                    it[DelegationTable.expectedDelegateCount] = delegation.expectedDelegates
                    it[DelegationTable.currentDelegateCountFromMymun] = delegation.currentDelegates
                    it[DelegationTable.headDelegateId] = null
                }
                delta.delegationsCreated++
            }
        }

        val committeeIds = mutableMapOf<String, String>()
        val positionIds = mutableMapOf<Pair<String, String>, String>()
        val delegateIdsByRow = mutableMapOf<Int, String>()
        val delegateIdsByIdentity = mutableMapOf<DelegateMatchingIdentity, String>()

        snapshot.countryPositions.groupBy { normalize(it.committeeName) }.forEach { (committeeNormalized, rows) ->
            val first = rows.first()
            val existingCommittee = existingCommittees[committeeNormalized]
            val committeeId = existingCommittee?.get(CommitteeTable.id) ?: UUID.randomUUID().toString()
            val requiredSlots = requiredSlotsByCommittee[committeeNormalized] ?: 1
            val slotsPerPosition = existingCommittee?.get(CommitteeTable.slotsPerPosition) ?: requiredSlots
            retainedCommitteeIds += committeeId
            if (existingCommittee == null) {
                CommitteeTable.insert {
                    it[CommitteeTable.id] = committeeId
                    it[CommitteeTable.conferenceId] = conferenceId
                    it[mymunName] = first.committeeName
                    it[normalizedMymunName] = committeeNormalized
                    it[alias] = null
                    it[CommitteeTable.slotsPerPosition] = slotsPerPosition
                }
                delta.committeesCreated++
            } else {
                if (committeeChanged(existingCommittee, first.committeeName, committeeNormalized)) {
                    CommitteeTable.update({ CommitteeTable.id eq committeeId }) {
                        it[mymunName] = first.committeeName
                        it[normalizedMymunName] = committeeNormalized
                    }
                    delta.committeesUpdated++
                }
            }
            committeeIds[committeeNormalized] = committeeId

            rows.forEach { country ->
                val positionName = normalize(country.memberName)
                val existingPosition = existingPositions[committeeId to positionName]
                val positionId = existingPosition?.get(PositionTable.id) ?: UUID.randomUUID().toString()
                retainedPositionIds += positionId
                if (existingPosition != null) {
                    if (positionChanged(existingPosition, country.memberName, positionName)) {
                        PositionTable.update({ PositionTable.id eq positionId }) {
                            it[PositionTable.name] = country.memberName
                            it[PositionTable.normalizedName] = positionName
                        }
                        delta.positionsUpdated++
                    }
                } else {
                    PositionTable.insert {
                        it[PositionTable.id] = positionId
                        it[PositionTable.conferenceId] = conferenceId
                        it[PositionTable.committeeId] = committeeId
                        it[PositionTable.name] = country.memberName
                        it[PositionTable.normalizedName] = positionName
                    }
                    delta.positionsCreated++
                }
                positionIds[committeeNormalized to positionName] = positionId

                val existingSlotIndexes = SlotTable.selectAll().where { SlotTable.positionId eq positionId }
                    .map { it[SlotTable.slotIndex] }
                    .toSet()
                (1..slotsPerPosition).filterNot { it in existingSlotIndexes }.forEach { slotIndex ->
                    insertEmptySlot(positionId, slotIndex)
                    delta.slotsCreated++
                }
            }
        }

        snapshot.delegates.forEach { delegate ->
            val matchingIdentity = delegate.matchingIdentity
            val delegateId = resolveExistingDelegate(delegate, existingDelegates)?.id ?: UUID.randomUUID().toString()
            delegateIdsByRow[delegate.rowNumber] = delegateId
            delegateIdsByIdentity[matchingIdentity] = delegateId
            retainedDelegateIds += delegateId
            val delegationId = delegate.delegationName?.let { delegationIdsByName[normalize(it)] }
            val existingDelegate = existingDelegateRowsById[delegateId]
            if (existingDelegate != null) {
                if (delegateChanged(existingDelegate, delegate, delegationId)) {
                    updateDelegate(delegateId, delegate, delegationId)
                    delta.delegatesUpdated++
                }
            } else {
                insertDelegate(conferenceId, delegateId, delegate, delegationId)
                delta.delegatesCreated++
            }
        }

        replaceWorkbookManagedChildren(retainedDelegateIds, retainedQuestionIds)

        snapshot.preferenceCandidates.forEach { preference ->
            val delegateId = delegateIdsByRow[preference.delegateRowNumber] ?: return@forEach
            val committeeName = preference.parsedCommitteeName?.let(::normalize) ?: return@forEach
            val positionName = preference.parsedPositionName?.let(::normalize) ?: return@forEach
            val positionId = positionIds[committeeName to positionName] ?: return@forEach
            PreferenceTable.insert {
                it[PreferenceTable.id] = UUID.randomUUID().toString()
                it[PreferenceTable.delegateId] = delegateId
                it[PreferenceTable.positionId] = positionId
                it[PreferenceTable.rank] = preference.rank
                it[PreferenceTable.rawPreferenceText] = preference.rawPreferenceText
            }
            delta.preferencesWritten++
        }

        val questionIdsByText = mutableMapOf<String, String>()
        snapshot.questionColumns.forEach { questionColumn ->
            val normalizedQuestion = normalize(questionColumn.questionText)
            val existingQuestion = existingQuestions[normalizedQuestion]
            val questionId = existingQuestion?.get(DelegateQuestionTable.id) ?: UUID.randomUUID().toString()
            questionIdsByText[questionColumn.questionText] = questionId
            retainedQuestionIds += questionId
            val questionTextLower = questionColumn.questionText.lowercase()
            val roleId = when {
                "experience" in questionTextLower -> StandardQuestionRoles.ExperienceLevel.id
                "high school" in questionTextLower -> StandardQuestionRoles.HighSchoolerStatus.id
                "english" in questionTextLower || "language" in questionTextLower -> StandardQuestionRoles.LanguageLevel.id
                else -> null
            }
            val handlingId = when {
                "why" in questionTextLower && "committee" in questionTextLower -> StandardQuestionHandlingTypes.CommitteeChoiceExplanation.id
                "visa" in questionTextLower -> StandardQuestionHandlingTypes.VisaSupport.id
                "delegation" in questionTextLower -> StandardQuestionHandlingTypes.DelegationMembership.id
                roleId != null -> StandardQuestionHandlingTypes.ExpectedTypedAnswer.id
                else -> StandardQuestionHandlingTypes.Default.id
            }

            if (existingQuestion == null) {
                DelegateQuestionTable.insert {
                    it[DelegateQuestionTable.id] = questionId
                    it[DelegateQuestionTable.conferenceId] = conferenceId
                    it[DelegateQuestionTable.questionText] = questionColumn.questionText
                    it[DelegateQuestionTable.normalizedQuestionText] = normalizedQuestion
                    it[DelegateQuestionTable.roleId] = roleId
                    it[DelegateQuestionTable.handlingTypeId] = handlingId
                    it[DelegateQuestionTable.visibleInDelegateDetail] = true
                    it[DelegateQuestionTable.organizerLabel] = null
                }
                delta.questionsCreated++
            } else {
                if (questionChanged(existingQuestion, questionColumn.questionText, normalizedQuestion)) {
                    DelegateQuestionTable.update({ DelegateQuestionTable.id eq questionId }) {
                        it[DelegateQuestionTable.questionText] = questionColumn.questionText
                        it[DelegateQuestionTable.normalizedQuestionText] = normalizedQuestion
                    }
                    delta.questionsUpdated++
                }
            }
        }

        val existingSlotAssignments = loadSlotAssignments(retainedPositionIds)
        clearMymunAssignments(retainedPositionIds)

        snapshot.questionAnswers.forEach { answerCell ->
            val delegateId = delegateIdsByRow[answerCell.delegateRowNumber] ?: return@forEach
            val questionId = questionIdsByText[answerCell.questionColumn.questionText] ?: return@forEach
            DelegateQuestionAnswerTable.insert {
                it[DelegateQuestionAnswerTable.id] = UUID.randomUUID().toString()
                it[DelegateQuestionAnswerTable.delegateId] = delegateId
                it[DelegateQuestionAnswerTable.questionId] = questionId
                it[DelegateQuestionAnswerTable.answer] = answerCell.answer
            }
            delta.answersWritten++
        }

        assignmentResolutions.forEach { (confirmed, delegate) ->
            val delegateId = delegateIdsByIdentity[delegate.matchingIdentity] ?: return@forEach
            val positionId = positionIds[normalize(confirmed.committeeName) to normalize(confirmed.positionName)] ?: return@forEach
            clearOtherAssignments(delegateId, positionId, confirmed.slotIndex)
            SlotTable.update({ (SlotTable.positionId eq positionId) and (SlotTable.slotIndex eq confirmed.slotIndex) }) {
                it[SlotTable.assignedDelegateId] = delegateId
                it[SlotTable.assignmentSource] = AssignmentSource.Mymun.name
                it[SlotTable.updatedAtEpochMillis] = Instant.now().toEpochMilli()
            }
            if (existingSlotAssignments[positionId to confirmed.slotIndex] != SlotAssignment(delegateId, AssignmentSource.Mymun.name)) {
                delta.assignmentsConfirmed++
            }
        }

        val pruned = pruneRemovedRows(
            conferenceId = conferenceId,
            retainedCommitteeIds = retainedCommitteeIds,
            retainedPositionIds = retainedPositionIds,
            retainedDelegationIds = retainedDelegationIds,
            retainedDelegateIds = retainedDelegateIds,
            retainedQuestionIds = retainedQuestionIds,
        )
        delta.committeesRemoved += pruned.committees
        delta.positionsRemoved += pruned.positions
        delta.delegationsRemoved += pruned.delegations
        delta.delegatesRemoved += pruned.delegates
        delta.questionsRemoved += pruned.questions

        val pendingAfterAssignmentReconcile = countPendingAssignmentsForConference(conferenceId)
        delta.pendingAssignmentsPreserved = pendingAfterAssignmentReconcile
        delta.pendingAssignmentsCleared = (pendingBeforeUpdate - pendingAfterAssignmentReconcile).coerceAtLeast(0)

        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[ConferenceTable.updatedAtEpochMillis] = Instant.now().toEpochMilli()
            it[ConferenceTable.lastMymunUpdateAtEpochMillis] = Instant.now().toEpochMilli()
        }

        MymunUpdateRunTable.update({ MymunUpdateRunTable.id eq updateRunId }) {
            it[MymunUpdateRunTable.completedAtEpochMillis] = Instant.now().toEpochMilli()
        }

        val summary = delta.toSummary(issues)
        logger.info(
            "MyMUN update completed for conference={} source={} errors={} warnings={} delegates(+{}/~{}/-{}) committees(+{}/~{}/-{}) positions(+{}/~{}/-{}) slotsCreated={} confirmedAssignments={} pendingPreserved={} pendingCleared={} questions(+{}/~{}/-{}) delegations(+{}/~{}/-{}) preferences={} answers={}",
            conferenceId,
            sourceFilename,
            summary.blockingErrorCount,
            summary.warningCount,
            summary.delegatesCreated,
            summary.delegatesUpdated,
            summary.delegatesRemoved,
            summary.committeesCreated,
            summary.committeesUpdated,
            summary.committeesRemoved,
            summary.positionsCreated,
            summary.positionsUpdated,
            summary.positionsRemoved,
            summary.slotsCreated,
            summary.assignmentsConfirmed,
            summary.pendingAssignmentsPreserved,
            summary.pendingAssignmentsCleared,
            summary.questionsCreated,
            summary.questionsUpdated,
            summary.questionsRemoved,
            summary.delegationsCreated,
            summary.delegationsUpdated,
            summary.delegationsRemoved,
            summary.preferencesWritten,
            summary.answersWritten,
        )

        UpdateResult(success = true, issues = issues, summary = summary)
    }

    private fun resolveConfirmedAssignment(
        candidate: MymunConfirmedAssignmentCandidate,
        delegates: List<MymunDelegateRow>,
    ): AssignmentResolution {
        val normalizedDisplay = normalize(candidate.delegateDisplayName)
        val exactMatches = delegates.filter { normalize(it.displayName) == normalizedDisplay }
        if (exactMatches.size == 1) return AssignmentResolution.Resolved(exactMatches.single())
        if (exactMatches.size > 1) {
            return AssignmentResolution.Unresolved(
                "Confirmed assignment delegate '${candidate.delegateDisplayName}' for ${candidate.committeeName} (${candidate.positionName}) is ambiguous.",
            )
        }

        val firstNameMatches = delegates.filter { normalize(it.firstName) == normalizedDisplay }
        if (normalizedDisplay.split(" ").size == 1 && firstNameMatches.size == 1) {
            return AssignmentResolution.WarningMatch(
                delegate = firstNameMatches.single(),
                message = "Assignment '${candidate.delegateDisplayName}' for ${candidate.committeeName} (${candidate.positionName}) was matched by first name only.",
            )
        }

        return AssignmentResolution.Unresolved(
            "Could not match confirmed assignment delegate '${candidate.delegateDisplayName}' for ${candidate.committeeName} (${candidate.positionName}).",
        )
    }

    private fun resolveExistingDelegate(
        incoming: MymunDelegateRow,
        existingDelegates: List<ExistingDelegate>,
    ): ExistingDelegate? {
        val sameName = existingDelegates.filter {
            it.normalizedFirstName == normalize(incoming.firstName) && it.normalizedLastName == normalize(incoming.lastName)
        }
        if (sameName.size == 1) return sameName.single()

        val sameEmail = sameName.filter {
            it.normalizedEmail != null && it.normalizedEmail == incoming.normalizedEmail.ifBlank { null }
        }
        if (sameEmail.size == 1) return sameEmail.single()

        val sameIdentity = sameEmail.filter {
            it.normalizedZipCode == normalize(incoming.zipCode.orEmpty()) && it.dateOfBirth == incoming.dateOfBirth?.toString()
        }
        return sameIdentity.singleOrNull()
    }

    private fun insertEmptySlot(positionId: String, slotIndex: Int) {
        SlotTable.insert {
            it[SlotTable.id] = UUID.randomUUID().toString()
            it[SlotTable.positionId] = positionId
            it[SlotTable.slotIndex] = slotIndex
            it[SlotTable.assignedDelegateId] = null
            it[SlotTable.assignmentSource] = null
            it[SlotTable.updatedAtEpochMillis] = Instant.now().toEpochMilli()
        }
    }

    private fun countPendingAssignmentsForConference(conferenceId: String): Int {
        val positionIds = PositionTable.selectAll()
            .where { PositionTable.conferenceId eq conferenceId }
            .map { it[PositionTable.id] }
            .toSet()
        return countPendingAssignments(positionIds)
    }

    private fun countPendingAssignments(positionIds: Set<String>): Int {
        if (positionIds.isEmpty()) return 0
        return SlotTable.selectAll().where {
            (SlotTable.positionId inList positionIds.toList()) and (SlotTable.assignmentSource eq AssignmentSource.Application.name)
        }.count().toInt()
    }

    private fun loadSlotAssignments(positionIds: Set<String>): Map<Pair<String, Int>, SlotAssignment> {
        if (positionIds.isEmpty()) return emptyMap()
        return SlotTable.selectAll().where { SlotTable.positionId inList positionIds.toList() }
            .mapNotNull { row ->
                val delegateId = row[SlotTable.assignedDelegateId] ?: return@mapNotNull null
                val source = row[SlotTable.assignmentSource] ?: return@mapNotNull null
                (row[SlotTable.positionId] to row[SlotTable.slotIndex]) to SlotAssignment(delegateId, source)
            }
            .toMap()
    }

    private fun delegationChanged(row: ResultRow, incoming: MymunDelegationRow): Boolean =
        row[DelegationTable.name] != incoming.name ||
            row[DelegationTable.normalizedName] != normalize(incoming.name) ||
            row[DelegationTable.country] != incoming.country ||
            row[DelegationTable.expectedDelegateCount] != incoming.expectedDelegates ||
            row[DelegationTable.currentDelegateCountFromMymun] != incoming.currentDelegates

    private fun committeeChanged(row: ResultRow, mymunName: String, normalizedMymunName: String): Boolean =
        row[CommitteeTable.mymunName] != mymunName ||
            row[CommitteeTable.normalizedMymunName] != normalizedMymunName

    private fun positionChanged(row: ResultRow, name: String, normalizedName: String): Boolean =
        row[PositionTable.name] != name ||
            row[PositionTable.normalizedName] != normalizedName

    private fun questionChanged(row: ResultRow, questionText: String, normalizedQuestionText: String): Boolean =
        row[DelegateQuestionTable.questionText] != questionText ||
            row[DelegateQuestionTable.normalizedQuestionText] != normalizedQuestionText

    private fun delegateChanged(row: ResultRow, incoming: MymunDelegateRow, delegationId: String?): Boolean =
        row[DelegateTable.delegationId] != delegationId ||
            row[DelegateTable.firstName] != incoming.firstName ||
            row[DelegateTable.lastName] != incoming.lastName ||
            row[DelegateTable.normalizedFirstName] != normalize(incoming.firstName) ||
            row[DelegateTable.normalizedLastName] != normalize(incoming.lastName) ||
            row[DelegateTable.email] != incoming.email.ifBlank { null } ||
            row[DelegateTable.normalizedEmail] != incoming.normalizedEmail.ifBlank { null } ||
            row[DelegateTable.country] != incoming.country ||
            row[DelegateTable.city] != incoming.city ||
            row[DelegateTable.streetAddress] != incoming.streetAddress ||
            row[DelegateTable.zipCode] != incoming.zipCode ||
            row[DelegateTable.diet] != incoming.diet ||
            row[DelegateTable.phone] != incoming.phone ||
            row[DelegateTable.nationality] != incoming.nationality ||
            row[DelegateTable.dateOfBirth] != incoming.dateOfBirth?.toString() ||
            row[DelegateTable.delegationNameFromMymun] != incoming.delegationName ||
            row[DelegateTable.applicationStatus] != incoming.applicationStatus ||
            row[DelegateTable.paymentStatus] != (if (incoming.feePaid) PaymentStatus.Paid.name else PaymentStatus.Unpaid.name) ||
            row[DelegateTable.isHeadDelegate] != incoming.isHeadDelegate ||
            row[DelegateTable.createdAtFromMymunEpochMillis] != incoming.createdAt?.toEpochMilliUtc() ||
            row[DelegateTable.submittedAtEpochMillis] != incoming.submittedAt?.toEpochMilliUtc()

    private fun insertDelegate(
        conferenceId: String,
        delegateId: String,
        delegate: MymunDelegateRow,
        delegationId: String?,
    ) {
        DelegateTable.insert {
            it[DelegateTable.id] = delegateId
            it[DelegateTable.conferenceId] = conferenceId
            it[DelegateTable.delegationId] = delegationId
            it[DelegateTable.firstName] = delegate.firstName
            it[DelegateTable.lastName] = delegate.lastName
            it[normalizedFirstName] = normalize(delegate.firstName)
            it[normalizedLastName] = normalize(delegate.lastName)
            it[DelegateTable.email] = delegate.email.ifBlank { null }
            it[normalizedEmail] = delegate.normalizedEmail.ifBlank { null }
            it[DelegateTable.country] = delegate.country
            it[DelegateTable.city] = delegate.city
            it[DelegateTable.streetAddress] = delegate.streetAddress
            it[DelegateTable.zipCode] = delegate.zipCode
            it[DelegateTable.diet] = delegate.diet
            it[DelegateTable.phone] = delegate.phone
            it[DelegateTable.nationality] = delegate.nationality
            it[DelegateTable.dateOfBirth] = delegate.dateOfBirth?.toString()
            it[DelegateTable.delegationNameFromMymun] = delegate.delegationName
            it[DelegateTable.applicationStatus] = delegate.applicationStatus
            it[DelegateTable.paymentStatus] = if (delegate.feePaid) PaymentStatus.Paid.name else PaymentStatus.Unpaid.name
            it[DelegateTable.active] = true
            it[DelegateTable.isHeadDelegate] = delegate.isHeadDelegate
            it[DelegateTable.createdAtFromMymunEpochMillis] = delegate.createdAt?.toEpochMilliUtc()
            it[DelegateTable.submittedAtEpochMillis] = delegate.submittedAt?.toEpochMilliUtc()
        }
    }

    private fun updateDelegate(delegateId: String, delegate: MymunDelegateRow, delegationId: String?) {
        DelegateTable.update({ DelegateTable.id eq delegateId }) {
            it[DelegateTable.delegationId] = delegationId
            it[DelegateTable.firstName] = delegate.firstName
            it[DelegateTable.lastName] = delegate.lastName
            it[normalizedFirstName] = normalize(delegate.firstName)
            it[normalizedLastName] = normalize(delegate.lastName)
            it[DelegateTable.email] = delegate.email.ifBlank { null }
            it[normalizedEmail] = delegate.normalizedEmail.ifBlank { null }
            it[DelegateTable.country] = delegate.country
            it[DelegateTable.city] = delegate.city
            it[DelegateTable.streetAddress] = delegate.streetAddress
            it[DelegateTable.zipCode] = delegate.zipCode
            it[DelegateTable.diet] = delegate.diet
            it[DelegateTable.phone] = delegate.phone
            it[DelegateTable.nationality] = delegate.nationality
            it[DelegateTable.dateOfBirth] = delegate.dateOfBirth?.toString()
            it[DelegateTable.delegationNameFromMymun] = delegate.delegationName
            it[DelegateTable.applicationStatus] = delegate.applicationStatus
            it[DelegateTable.paymentStatus] = if (delegate.feePaid) PaymentStatus.Paid.name else PaymentStatus.Unpaid.name
            it[DelegateTable.isHeadDelegate] = delegate.isHeadDelegate
            it[DelegateTable.createdAtFromMymunEpochMillis] = delegate.createdAt?.toEpochMilliUtc()
            it[DelegateTable.submittedAtEpochMillis] = delegate.submittedAt?.toEpochMilliUtc()
        }
    }

    private fun clearMymunAssignments(retainedPositionIds: Set<String>) {
        if (retainedPositionIds.isEmpty()) return
        SlotTable.update({
            (SlotTable.positionId inList retainedPositionIds.toList()) and (SlotTable.assignmentSource eq AssignmentSource.Mymun.name)
        }) {
            it[SlotTable.assignedDelegateId] = null
            it[SlotTable.assignmentSource] = null
            it[SlotTable.updatedAtEpochMillis] = Instant.now().toEpochMilli()
        }
    }

    private fun clearOtherAssignments(delegateId: String, positionId: String, slotIndex: Int) {
        SlotTable.update({
            (SlotTable.assignedDelegateId eq delegateId) and
                ((SlotTable.positionId neq positionId) or (SlotTable.slotIndex neq slotIndex))
        }) {
            it[SlotTable.assignedDelegateId] = null
            it[SlotTable.assignmentSource] = null
            it[SlotTable.updatedAtEpochMillis] = Instant.now().toEpochMilli()
        }
    }

    private fun replaceWorkbookManagedChildren(retainedDelegateIds: Set<String>, retainedQuestionIds: Set<String>) {
        retainedDelegateIds.forEach { delegateId ->
            PreferenceTable.deleteWhere { PreferenceTable.delegateId eq delegateId }
            DelegateQuestionAnswerTable.deleteWhere { DelegateQuestionAnswerTable.delegateId eq delegateId }
        }
        retainedQuestionIds.forEach { questionId ->
            DelegateQuestionAnswerTable.deleteWhere { DelegateQuestionAnswerTable.questionId eq questionId }
        }
    }

    private fun pruneRemovedRows(
        conferenceId: String,
        retainedCommitteeIds: Set<String>,
        retainedPositionIds: Set<String>,
        retainedDelegationIds: Set<String>,
        retainedDelegateIds: Set<String>,
        retainedQuestionIds: Set<String>,
    ): PrunedRowCounts {
        var positions = 0
        var committees = 0
        var delegates = 0
        var delegations = 0
        var questions = 0

        PositionTable.selectAll().where { PositionTable.conferenceId eq conferenceId }
            .map { it[PositionTable.id] }
            .filterNot { it in retainedPositionIds }
            .forEach { positionId ->
                PositionTable.deleteWhere { PositionTable.id eq positionId }
                positions++
            }

        CommitteeTable.selectAll().where { CommitteeTable.conferenceId eq conferenceId }
            .map { it[CommitteeTable.id] }
            .filterNot { it in retainedCommitteeIds }
            .forEach { committeeId ->
                CommitteeTable.deleteWhere { CommitteeTable.id eq committeeId }
                committees++
            }

        DelegateTable.selectAll().where { DelegateTable.conferenceId eq conferenceId }
            .map { it[DelegateTable.id] }
            .filterNot { it in retainedDelegateIds }
            .forEach { delegateId ->
                DelegateTable.deleteWhere { DelegateTable.id eq delegateId }
                delegates++
            }

        DelegationTable.selectAll().where { DelegationTable.conferenceId eq conferenceId }
            .map { it[DelegationTable.id] }
            .filterNot { it in retainedDelegationIds }
            .forEach { delegationId ->
                DelegationTable.deleteWhere { DelegationTable.id eq delegationId }
                delegations++
            }

        DelegateQuestionTable.selectAll().where { DelegateQuestionTable.conferenceId eq conferenceId }
            .map { it[DelegateQuestionTable.id] }
            .filterNot { it in retainedQuestionIds }
            .forEach { questionId ->
                DelegateQuestionTable.deleteWhere { DelegateQuestionTable.id eq questionId }
                questions++
            }

        return PrunedRowCounts(
            positions = positions,
            committees = committees,
            delegates = delegates,
            delegations = delegations,
            questions = questions,
        )
    }

    private fun insertUpdateIssue(updateRunId: String, issue: UpdateIssue) {
        MymunUpdateIssueTable.insert {
            it[MymunUpdateIssueTable.id] = UUID.randomUUID().toString()
            it[MymunUpdateIssueTable.updateRunId] = updateRunId
            it[MymunUpdateIssueTable.severity] = issue.severity.name
            it[MymunUpdateIssueTable.code] = issue.code
            it[MymunUpdateIssueTable.message] = issue.message
            it[MymunUpdateIssueTable.sheetName] = issue.sheetName
            it[MymunUpdateIssueTable.rowNumber] = issue.rowNumber
            it[MymunUpdateIssueTable.columnNumber] = issue.columnNumber
            it[MymunUpdateIssueTable.columnName] = issue.columnName
        }
    }

    private suspend fun <T> db(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, db = database) { block() }

    private fun loadConferenceGraph(conferenceId: String): Conference {
        val conferenceRow = ConferenceTable.selectAll().where { ConferenceTable.id eq conferenceId }
            .single().toConferenceRow()
        val positionIds = PositionTable.selectAll().where { PositionTable.conferenceId eq conferenceId }.map { it[PositionTable.id] }
        val delegateIds = DelegateTable.selectAll().where { DelegateTable.conferenceId eq conferenceId }.map { it[DelegateTable.id] }
        val questionIds = DelegateQuestionTable.selectAll().where { DelegateQuestionTable.conferenceId eq conferenceId }
            .map { it[DelegateQuestionTable.id] }
        val rows = ConferenceGraphRows(
            conference = conferenceRow,
            committees = CommitteeTable.selectAll().where { CommitteeTable.conferenceId eq conferenceId }.map { row ->
                CommitteeRow(
                    id = row[CommitteeTable.id],
                    conferenceId = row[CommitteeTable.conferenceId],
                    mymunName = row[CommitteeTable.mymunName],
                    alias = row[CommitteeTable.alias],
                    slotsPerPosition = row[CommitteeTable.slotsPerPosition],
                )
            },
            positions = PositionTable.selectAll().where { PositionTable.conferenceId eq conferenceId }.map { row ->
                PositionRow(
                    id = row[PositionTable.id],
                    conferenceId = row[PositionTable.conferenceId],
                    committeeId = row[PositionTable.committeeId],
                    name = row[PositionTable.name],
                )
            },
            slots = if (positionIds.isEmpty()) emptyList() else SlotTable.selectAll().where {
                SlotTable.positionId inList positionIds
            }.map { row ->
                SlotRow(
                    id = row[SlotTable.id],
                    positionId = row[SlotTable.positionId],
                    slotIndex = row[SlotTable.slotIndex],
                    assignedDelegateId = row[SlotTable.assignedDelegateId],
                    assignmentSource = row[SlotTable.assignmentSource],
                    updatedAtEpochMillis = row[SlotTable.updatedAtEpochMillis],
                )
            },
            delegations = DelegationTable.selectAll().where { DelegationTable.conferenceId eq conferenceId }.map { row ->
                DelegationRow(
                    id = row[DelegationTable.id],
                    conferenceId = row[DelegationTable.conferenceId],
                    name = row[DelegationTable.name],
                    country = row[DelegationTable.country],
                    expectedDelegateCount = row[DelegationTable.expectedDelegateCount],
                    currentDelegateCountFromMymun = row[DelegationTable.currentDelegateCountFromMymun],
                    headDelegateId = row[DelegationTable.headDelegateId],
                )
            },
            delegates = DelegateTable.selectAll().where { DelegateTable.conferenceId eq conferenceId }.map { row ->
                DelegateRow(
                    id = row[DelegateTable.id],
                    conferenceId = row[DelegateTable.conferenceId],
                    delegationId = row[DelegateTable.delegationId],
                    firstName = row[DelegateTable.firstName],
                    lastName = row[DelegateTable.lastName],
                    email = row[DelegateTable.email],
                    country = row[DelegateTable.country],
                    city = row[DelegateTable.city],
                    streetAddress = row[DelegateTable.streetAddress],
                    zipCode = row[DelegateTable.zipCode],
                    diet = row[DelegateTable.diet],
                    phone = row[DelegateTable.phone],
                    nationality = row[DelegateTable.nationality],
                    dateOfBirth = row[DelegateTable.dateOfBirth],
                    delegationNameFromMymun = row[DelegateTable.delegationNameFromMymun],
                    applicationStatus = row[DelegateTable.applicationStatus],
                    paymentStatus = row[DelegateTable.paymentStatus],
                    active = row[DelegateTable.active],
                    isHeadDelegate = row[DelegateTable.isHeadDelegate],
                    createdAtFromMymunEpochMillis = row[DelegateTable.createdAtFromMymunEpochMillis],
                    submittedAtEpochMillis = row[DelegateTable.submittedAtEpochMillis],
                )
            },
            preferences = if (delegateIds.isEmpty()) emptyList() else PreferenceTable.selectAll().where {
                PreferenceTable.delegateId inList delegateIds
            }.map { row ->
                PreferenceRow(
                    id = row[PreferenceTable.id],
                    delegateId = row[PreferenceTable.delegateId],
                    positionId = row[PreferenceTable.positionId],
                    rank = row[PreferenceTable.rank],
                    rawPreferenceText = row[PreferenceTable.rawPreferenceText],
                )
            },
            comments = if (delegateIds.isEmpty()) emptyList() else DelegateCommentTable.selectAll().where {
                DelegateCommentTable.delegateId inList delegateIds
            }.map { row ->
                DelegateCommentRow(
                    id = row[DelegateCommentTable.id],
                    delegateId = row[DelegateCommentTable.delegateId],
                    body = row[DelegateCommentTable.body],
                    createdAtEpochMillis = row[DelegateCommentTable.createdAtEpochMillis],
                    updatedAtEpochMillis = row[DelegateCommentTable.updatedAtEpochMillis],
                )
            },
            questions = DelegateQuestionTable.selectAll().where { DelegateQuestionTable.conferenceId eq conferenceId }.map { row ->
                DelegateQuestionRow(
                    id = row[DelegateQuestionTable.id],
                    conferenceId = row[DelegateQuestionTable.conferenceId],
                    questionText = row[DelegateQuestionTable.questionText],
                    roleId = row[DelegateQuestionTable.roleId],
                    handlingTypeId = row[DelegateQuestionTable.handlingTypeId],
                    visibleInDelegateDetail = row[DelegateQuestionTable.visibleInDelegateDetail],
                    organizerLabel = row[DelegateQuestionTable.organizerLabel],
                )
            },
            questionRules = if (questionIds.isEmpty()) emptyList() else DelegateQuestionRuleTable.selectAll().where {
                DelegateQuestionRuleTable.questionId inList questionIds
            }.map { row ->
                DelegateQuestionRuleRow(
                    id = row[DelegateQuestionRuleTable.id],
                    questionId = row[DelegateQuestionRuleTable.questionId],
                    ruleTypeId = row[DelegateQuestionRuleTable.ruleTypeId],
                    intParameter = row[DelegateQuestionRuleTable.intParameter],
                    textParameter = row[DelegateQuestionRuleTable.textParameter],
                )
            },
            munExperienceMappings = if (questionIds.isEmpty()) emptyList() else MunExperienceAnswerMappingTable.selectAll().where {
                MunExperienceAnswerMappingTable.questionId inList questionIds
            }.map { row ->
                MunExperienceAnswerMappingRow(
                    id = row[MunExperienceAnswerMappingTable.id],
                    questionId = row[MunExperienceAnswerMappingTable.questionId],
                    rawValue = row[MunExperienceAnswerMappingTable.rawValue],
                    mappedValue = row[MunExperienceAnswerMappingTable.mappedValue],
                )
            },
            highSchoolerMappings = if (questionIds.isEmpty()) emptyList() else HighSchoolerAnswerMappingTable.selectAll().where {
                HighSchoolerAnswerMappingTable.questionId inList questionIds
            }.map { row ->
                HighSchoolerAnswerMappingRow(
                    id = row[HighSchoolerAnswerMappingTable.id],
                    questionId = row[HighSchoolerAnswerMappingTable.questionId],
                    rawValue = row[HighSchoolerAnswerMappingTable.rawValue],
                    mappedValue = row[HighSchoolerAnswerMappingTable.mappedValue],
                )
            },
            languageLevelMappings = if (questionIds.isEmpty()) emptyList() else LanguageLevelAnswerMappingTable.selectAll().where {
                LanguageLevelAnswerMappingTable.questionId inList questionIds
            }.map { row ->
                LanguageLevelAnswerMappingRow(
                    id = row[LanguageLevelAnswerMappingTable.id],
                    questionId = row[LanguageLevelAnswerMappingTable.questionId],
                    rawValue = row[LanguageLevelAnswerMappingTable.rawValue],
                    mappedValue = row[LanguageLevelAnswerMappingTable.mappedValue],
                )
            },
            answers = if (delegateIds.isEmpty()) emptyList() else DelegateQuestionAnswerTable.selectAll().where {
                DelegateQuestionAnswerTable.delegateId inList delegateIds
            }.map { row ->
                DelegateQuestionAnswerRow(
                    id = row[DelegateQuestionAnswerTable.id],
                    delegateId = row[DelegateQuestionAnswerTable.delegateId],
                    questionId = row[DelegateQuestionAnswerTable.questionId],
                    answer = row[DelegateQuestionAnswerTable.answer],
                )
            },
        )
        return ConferenceGraphMapper.toDomain(rows)
    }

    private fun ResultRow.toConferenceRow(): ConferenceRow =
        ConferenceRow(
            id = this[ConferenceTable.id],
            name = this[ConferenceTable.name],
            createdAtEpochMillis = this[ConferenceTable.createdAtEpochMillis],
            updatedAtEpochMillis = this[ConferenceTable.updatedAtEpochMillis],
            lastMymunUpdateAtEpochMillis = this[ConferenceTable.lastMymunUpdateAtEpochMillis],
        )

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun LocalDate.toEpochMilliUtc(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun org.munika.assignment.data.excel.MymunValidationIssue.toUpdateIssue(): UpdateIssue =
        UpdateIssue(
            severity = if (severity == MymunValidationSeverity.Error) UpdateIssueSeverity.Error else UpdateIssueSeverity.Warning,
            code = code,
            message = message,
            sheetName = location?.sheetName,
            rowNumber = location?.rowNumber,
            columnNumber = location?.columnNumber,
            columnName = location?.columnName,
        )

    private fun MymunConfirmedAssignmentCandidate.countriesDelegatesLocation(): CellLocation =
        CellLocation(
            sheetName = "Countries",
            rowNumber = countryRowNumber,
            columnNumber = 4,
            columnName = "Delegates",
        )

    private val MymunDelegateRow.matchingIdentity: DelegateMatchingIdentity
        get() = DelegateMatchingIdentity(
            firstName = normalize(firstName),
            lastName = normalize(lastName),
            email = normalizedEmail.ifBlank { null },
            zipCode = normalize(zipCode.orEmpty()),
            dateOfBirth = dateOfBirth?.toString(),
        )

    private sealed interface AssignmentResolution {
        data class Resolved(val delegate: MymunDelegateRow) : AssignmentResolution

        data class WarningMatch(
            val delegate: MymunDelegateRow,
            val message: String,
        ) : AssignmentResolution

        data class Unresolved(val message: String) : AssignmentResolution
    }

    private data class DelegateMatchingIdentity(
        val firstName: String,
        val lastName: String,
        val email: String?,
        val zipCode: String,
        val dateOfBirth: String?,
    )

    private data class PrunedRowCounts(
        val positions: Int,
        val committees: Int,
        val delegates: Int,
        val delegations: Int,
        val questions: Int,
    )

    private data class SlotAssignment(
        val delegateId: String,
        val assignmentSource: String,
    )

    private data class MutableUpdateSummary(
        var delegatesCreated: Int = 0,
        var delegatesUpdated: Int = 0,
        var delegatesRemoved: Int = 0,
        var committeesCreated: Int = 0,
        var committeesUpdated: Int = 0,
        var committeesRemoved: Int = 0,
        var positionsCreated: Int = 0,
        var positionsUpdated: Int = 0,
        var positionsRemoved: Int = 0,
        var slotsCreated: Int = 0,
        var assignmentsConfirmed: Int = 0,
        var pendingAssignmentsPreserved: Int = 0,
        var pendingAssignmentsCleared: Int = 0,
        var questionsCreated: Int = 0,
        var questionsUpdated: Int = 0,
        var questionsRemoved: Int = 0,
        var delegationsCreated: Int = 0,
        var delegationsUpdated: Int = 0,
        var delegationsRemoved: Int = 0,
        var preferencesWritten: Int = 0,
        var answersWritten: Int = 0,
    ) {
        fun toSummary(issues: List<UpdateIssue>): UpdateSummary =
            UpdateSummary(
                blockingErrorCount = issues.count { it.severity == UpdateIssueSeverity.Error },
                warningCount = issues.count { it.severity == UpdateIssueSeverity.Warning },
                delegatesCreated = delegatesCreated,
                delegatesUpdated = delegatesUpdated,
                delegatesRemoved = delegatesRemoved,
                committeesCreated = committeesCreated,
                committeesUpdated = committeesUpdated,
                committeesRemoved = committeesRemoved,
                positionsCreated = positionsCreated,
                positionsUpdated = positionsUpdated,
                positionsRemoved = positionsRemoved,
                slotsCreated = slotsCreated,
                assignmentsConfirmed = assignmentsConfirmed,
                pendingAssignmentsPreserved = pendingAssignmentsPreserved,
                pendingAssignmentsCleared = pendingAssignmentsCleared,
                questionsCreated = questionsCreated,
                questionsUpdated = questionsUpdated,
                questionsRemoved = questionsRemoved,
                delegationsCreated = delegationsCreated,
                delegationsUpdated = delegationsUpdated,
                delegationsRemoved = delegationsRemoved,
                preferencesWritten = preferencesWritten,
                answersWritten = answersWritten,
            )
    }

    private data class ExistingDelegate(
        val id: String,
        val normalizedFirstName: String,
        val normalizedLastName: String,
        val normalizedEmail: String?,
        val normalizedZipCode: String,
        val dateOfBirth: String?,
    ) {
        companion object {
            fun from(row: ResultRow): ExistingDelegate =
                ExistingDelegate(
                    id = row[DelegateTable.id],
                    normalizedFirstName = row[DelegateTable.normalizedFirstName],
                    normalizedLastName = row[DelegateTable.normalizedLastName],
                    normalizedEmail = row[DelegateTable.normalizedEmail],
                    normalizedZipCode = row[DelegateTable.zipCode]?.trim()?.lowercase()?.replace(Regex("\\s+"), " ").orEmpty(),
                    dateOfBirth = row[DelegateTable.dateOfBirth],
                )
        }
    }
}
