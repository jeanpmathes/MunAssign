package org.munika.assignment.data.db.repository

import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.munika.assignment.data.db.DatabaseFactory
import org.munika.assignment.data.db.mapper.ConferenceGraphMapper
import org.munika.assignment.data.db.model.AnswerIssueResolutionRow
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
import org.munika.assignment.data.db.model.ReservationDelegateLinkRow
import org.munika.assignment.data.db.model.ReservationRow
import org.munika.assignment.data.db.model.SlotRow
import org.munika.assignment.data.db.tables.AnswerIssueResolutionTable
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
import org.munika.assignment.data.db.tables.ReservationDelegateLinkTable
import org.munika.assignment.data.db.tables.ReservationTable
import org.munika.assignment.data.db.tables.SlotTable
import org.munika.assignment.data.excel.CellLocation
import org.munika.assignment.data.excel.MymunConfirmedAssignmentCandidate
import org.munika.assignment.data.excel.MymunDelegateRow
import org.munika.assignment.data.excel.MymunDelegationRow
import org.munika.assignment.data.excel.MymunValidationSeverity
import org.munika.assignment.data.excel.MymunWorkbookSnapshot
import org.munika.assignment.domain.model.AssignmentSource
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.HighSchoolerAnswerMapping
import org.munika.assignment.domain.model.isUsableApplicationStatus
import org.munika.assignment.domain.model.LanguageLevelAnswerMapping
import org.munika.assignment.domain.model.MinimumSentenceCount
import org.munika.assignment.domain.model.MinimumTextLength
import org.munika.assignment.domain.model.MunExperienceAnswerMapping
import org.munika.assignment.domain.model.NoticeWhenAffirmative
import org.munika.assignment.domain.model.NoticeWhenNonBlank
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.QuestionConfiguration
import org.munika.assignment.domain.model.ReservationDelegateLink
import org.munika.assignment.domain.model.ReservationDelegateLinkId
import org.munika.assignment.domain.model.ReservationLinkSource
import org.munika.assignment.domain.model.ReservationReconciler
import org.munika.assignment.domain.model.RequiredAnswer
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.TypedAnswerMappings
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
    constructor(databasePath: Path) : this(DatabaseFactory.connect(databasePath))

    private val logger = LoggerFactory.getLogger(ConferenceRepositoryImpl::class.java)

    override suspend fun listConferences(): List<Conference> = db {
        ConferenceTable.selectAll()
            .orderBy(
                ConferenceTable.displayOrder to SortOrder.ASC,
                ConferenceTable.createdAtEpochMillis to SortOrder.ASC,
                ConferenceTable.updatedAtEpochMillis to SortOrder.ASC,
            )
            .map { row ->
                ConferenceGraphMapper.toDomain(
                    ConferenceGraphRows(
                        conference = row.toConferenceRow(),
                        committees = emptyList(),
                        positions = emptyList(),
                        slots = emptyList(),
                        delegations = emptyList(),
                        delegates = emptyList(),
                        reservations = emptyList(),
                        reservationLinks = emptyList(),
                        preferences = emptyList(),
                        comments = emptyList(),
                        questions = emptyList(),
                        questionRules = emptyList(),
                        munExperienceMappings = emptyList(),
                        highSchoolerMappings = emptyList(),
                        languageLevelMappings = emptyList(),
                        answers = emptyList(),
                        issueResolutions = emptyList(),
                    ),
                )
            }
    }

    override suspend fun createConference(name: String): Conference = db {
        val now = Instant.now().toEpochMilli()
        val id = UUID.randomUUID().toString()
        val displayOrder = nextConferenceDisplayOrder()
        ConferenceTable.insert {
            it[ConferenceTable.id] = id
            it[ConferenceTable.name] = name.trim()
            it[ConferenceTable.displayOrder] = displayOrder
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
        db {
            ConferenceTable.deleteWhere { ConferenceTable.id eq conferenceId }
            normalizeConferenceDisplayOrder()
        }
    }

    override suspend fun reorderConferences(conferenceIdsInOrder: List<String>) {
        db {
            val knownIds = ConferenceTable.selectAll()
                .map { it[ConferenceTable.id] }
                .toSet()
            require(conferenceIdsInOrder.toSet() == knownIds && conferenceIdsInOrder.size == knownIds.size) {
                "Conference reorder must include every known conference exactly once."
            }
            conferenceIdsInOrder.forEachIndexed { index, conferenceId ->
                ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
                    it[ConferenceTable.displayOrder] = index
                    it[ConferenceTable.updatedAtEpochMillis] = Instant.now().toEpochMilli()
                }
            }
        }
    }

    override suspend fun setCommitteeSlotsPerPosition(
        conferenceId: String,
        committeeId: String,
        slotsPerPosition: Int,
    ): Conference = db {
        require(slotsPerPosition >= 1) { "Committee slot count must be at least 1." }
        val committee = requireNotNull(
            CommitteeTable.selectAll()
                .where { (CommitteeTable.id eq committeeId) and (CommitteeTable.conferenceId eq conferenceId) }
                .singleOrNull(),
        ) { "Committee $committeeId does not belong to conference $conferenceId." }
        val currentSlotsPerPosition = committee[CommitteeTable.slotsPerPosition]
        if (currentSlotsPerPosition == slotsPerPosition) {
            return@db loadConferenceGraph(conferenceId)
        }

        val positionIds = PositionTable.selectAll()
            .where { PositionTable.committeeId eq committeeId }
            .map { it[PositionTable.id] }
        if (slotsPerPosition < currentSlotsPerPosition && positionIds.isNotEmpty()) {
            val occupiedRemovedSlot = SlotTable.selectAll().where {
                (SlotTable.positionId inList positionIds) and
                    (SlotTable.slotIndex greater slotsPerPosition) and
                    (SlotTable.assignedDelegateId neq null)
            }.firstOrNull()
            require(occupiedRemovedSlot == null) {
                "Cannot reduce slot count because one or more removed slots are occupied."
            }
        }

        val now = Instant.now().toEpochMilli()
        if (slotsPerPosition > currentSlotsPerPosition) {
            positionIds.forEach { positionId ->
                val existingSlotIndexes = SlotTable.selectAll().where { SlotTable.positionId eq positionId }
                    .map { it[SlotTable.slotIndex] }
                    .toSet()
                ((currentSlotsPerPosition + 1)..slotsPerPosition)
                    .filterNot { it in existingSlotIndexes }
                    .forEach { slotIndex -> insertEmptySlot(positionId, slotIndex) }
            }
        } else if (positionIds.isNotEmpty()) {
            SlotTable.deleteWhere {
                (SlotTable.positionId inList positionIds) and (SlotTable.slotIndex greater slotsPerPosition)
            }
        }

        CommitteeTable.update({ CommitteeTable.id eq committeeId }) {
            it[CommitteeTable.slotsPerPosition] = slotsPerPosition
        }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[ConferenceTable.updatedAtEpochMillis] = now
        }
        loadConferenceGraph(conferenceId)
    }

    override suspend fun setCommitteeHighSchoolStatus(
        conferenceId: String,
        committeeId: String,
        isHighSchoolCommittee: Boolean,
    ): Conference = setCommitteeMode(
        conferenceId = conferenceId,
        committeeId = committeeId,
        mode = if (isHighSchoolCommittee) CommitteeMode.HighSchool else CommitteeMode.University,
    )

    override suspend fun setCommitteeMode(
        conferenceId: String,
        committeeId: String,
        mode: CommitteeMode,
    ): Conference = db {
        val committee = requireCommitteeInConference(conferenceId, committeeId)
        val currentMode = CommitteeMode.valueOf(committee[CommitteeTable.mode])
        if (mode == CommitteeMode.Mapped) {
            val inboundMapping = PositionTable.selectAll()
                .where { (PositionTable.conferenceId eq conferenceId) and (PositionTable.mappedCommitteeId eq committeeId) }
                .firstOrNull()
            require(inboundMapping == null) {
                "Committee $committeeId cannot become mapped while positions map to it."
            }
        }
        if (currentMode == CommitteeMode.Mapped && mode != CommitteeMode.Mapped) {
            val mappedPositionNames = PositionTable.selectAll()
                .where { PositionTable.committeeId eq committeeId }
                .filter { it[PositionTable.mappedCommitteeId] != null }
                .map { it[PositionTable.name] }
            require(mappedPositionNames.isEmpty()) {
                "Committee ${committee[CommitteeTable.mymunName]} cannot leave mapped mode while correspondences are set for " +
                    mappedPositionNames.joinToString(limit = 3) + ". Remove all correspondences first."
            }
        }
        val updated = CommitteeTable.update({
            (CommitteeTable.id eq committeeId) and (CommitteeTable.conferenceId eq conferenceId)
        }) {
            it[CommitteeTable.mode] = mode.name
            it[CommitteeTable.isHighSchoolCommittee] = mode == CommitteeMode.HighSchool
        }
        require(updated == 1) { "Committee $committeeId does not belong to conference $conferenceId." }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = Instant.now().toEpochMilli()
        }
        loadConferenceGraph(conferenceId)
    }

    override suspend fun setPositionMappedCommittee(
        conferenceId: String,
        positionId: String,
        mappedCommitteeId: String?,
    ): Conference = db {
        val position = requireNotNull(
            PositionTable.selectAll()
                .where { (PositionTable.id eq positionId) and (PositionTable.conferenceId eq conferenceId) }
                .singleOrNull(),
        ) { "Position $positionId does not belong to conference $conferenceId." }
        val owningCommittee = requireCommitteeInConference(conferenceId, position[PositionTable.committeeId])
        require(CommitteeMode.valueOf(owningCommittee[CommitteeTable.mode]) == CommitteeMode.Mapped) {
            "Position $positionId belongs to a committee that is not mapped."
        }
        val target = mappedCommitteeId?.let { targetId -> requireCommitteeInConference(conferenceId, targetId) }
        require(target == null || CommitteeMode.valueOf(target[CommitteeTable.mode]) != CommitteeMode.Mapped) {
            "Position $positionId cannot map to a mapped committee."
        }
        if (mappedCommitteeId != null) {
            val siblingUsingTarget = PositionTable.selectAll()
                .where {
                    (PositionTable.committeeId eq owningCommittee[CommitteeTable.id]) and
                        (PositionTable.id neq positionId) and
                        (PositionTable.mappedCommitteeId eq mappedCommitteeId)
                }
                .firstOrNull()
            require(siblingUsingTarget == null) {
                "Another position in ${owningCommittee[CommitteeTable.mymunName]} already corresponds to " +
                    "${target?.get(CommitteeTable.mymunName)}."
            }
        }
        PositionTable.update({ PositionTable.id eq positionId }) {
            it[PositionTable.mappedCommitteeId] = mappedCommitteeId
        }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = Instant.now().toEpochMilli()
        }
        loadConferenceGraph(conferenceId)
    }

    override suspend fun getConference(conferenceId: String): Conference? = db {
        if (ConferenceTable.selectAll().where { ConferenceTable.id eq conferenceId }.empty()) {
            return@db null
        }
        loadConferenceGraph(conferenceId)
    }

    override suspend fun assignDelegateToSlot(
        conferenceId: String,
        slotId: String,
        delegateId: String,
        moveExistingPending: Boolean,
    ): Conference = db {
        val slot = requireSlotInConference(conferenceId, slotId)
        require(slot[SlotTable.assignmentSource] != AssignmentSource.Mymun.name) {
            "Confirmed assignments are controlled by MyMUN updates and cannot be changed in MunAssign."
        }
        val replacedDelegateId = slot[SlotTable.assignedDelegateId]

        val delegate = requireNotNull(
            DelegateTable.selectAll()
                .where { (DelegateTable.id eq delegateId) and (DelegateTable.conferenceId eq conferenceId) }
                .singleOrNull(),
        ) { "Delegate $delegateId does not belong to conference $conferenceId." }
        require(delegate[DelegateTable.active]) { "Only active delegates can be manually assigned." }
        require(delegate[DelegateTable.paymentStatus] == PaymentStatus.Paid.name) {
            "Only paid delegates can be manually assigned."
        }
        require(isUsableApplicationStatus(delegate[DelegateTable.applicationStatus])) {
            "Only accepted, assigned, or checked-in delegates can be manually assigned."
        }

        val existingAssignment = SlotTable.selectAll()
            .where { SlotTable.assignedDelegateId eq delegateId }
            .singleOrNull()
        if (existingAssignment != null && existingAssignment[SlotTable.id] != slotId) {
            when (existingAssignment[SlotTable.assignmentSource]) {
                AssignmentSource.Mymun.name -> {
                    throw IllegalArgumentException(
                        "Confirmed assignments are controlled by MyMUN updates and cannot be moved in MunAssign.",
                    )
                }
                AssignmentSource.Application.name -> {
                    require(moveExistingPending) {
                        "Delegate already has a pending assignment. Confirm moving it before assigning a new slot."
                    }
                    clearSlotAssignment(existingAssignment[SlotTable.id], Instant.now().toEpochMilli())
                    logger.info(
                        "Moved pending assignment delegate={} fromSlot={} toSlot={} conference={}",
                        delegateId,
                        existingAssignment[SlotTable.id],
                        slotId,
                        conferenceId,
                    )
                }
                else -> error("Assigned delegate ${delegate[DelegateTable.id]} has no assignment source.")
            }
        }

        val now = Instant.now().toEpochMilli()
        SlotTable.update({ SlotTable.id eq slotId }) {
            it[assignedDelegateId] = delegateId
            it[assignmentSource] = AssignmentSource.Application.name
            it[updatedAtEpochMillis] = now
        }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = now
        }
        logger.info(
            "Assigned delegate={} to slot={} conference={} source={} replacedDelegate={}",
            delegateId,
            slotId,
            conferenceId,
            AssignmentSource.Application.name,
            replacedDelegateId,
        )
        loadConferenceGraph(conferenceId)
    }

    override suspend fun clearPendingSlot(conferenceId: String, slotId: String): Conference = db {
        val slot = requireSlotInConference(conferenceId, slotId)
        require(slot[SlotTable.assignmentSource] != AssignmentSource.Mymun.name) {
            "Confirmed assignments are controlled by MyMUN updates and cannot be cleared in MunAssign."
        }
        require(slot[SlotTable.assignmentSource] == AssignmentSource.Application.name) {
            "Only pending MunAssign assignments can be cleared."
        }

        val now = Instant.now().toEpochMilli()
        clearSlotAssignment(slotId, now)
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = now
        }
        logger.info(
            "Cleared pending assignment slot={} delegate={} conference={}",
            slotId,
            slot[SlotTable.assignedDelegateId],
            conferenceId,
        )
        loadConferenceGraph(conferenceId)
    }

    override suspend fun deactivateDelegate(conferenceId: String, delegateId: String): Conference = db {
        requireDelegateInConference(conferenceId, delegateId)
        val assignedSlot = SlotTable.selectAll()
            .where { SlotTable.assignedDelegateId eq delegateId }
            .singleOrNull()
        require(assignedSlot?.get(SlotTable.assignmentSource) != AssignmentSource.Mymun.name) {
            "Confirmed delegates cannot be deactivated in MunAssign."
        }

        val now = Instant.now().toEpochMilli()
        if (assignedSlot?.get(SlotTable.assignmentSource) == AssignmentSource.Application.name) {
            clearSlotAssignment(assignedSlot[SlotTable.id], now)
        }
        DelegateTable.update({ (DelegateTable.id eq delegateId) and (DelegateTable.conferenceId eq conferenceId) }) {
            it[DelegateTable.active] = false
        }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = now
        }
        reconcileReservations(conferenceId, now)
        logger.info("Deactivated delegate={} conference={} clearedPending={}", delegateId, conferenceId, assignedSlot != null)
        loadConferenceGraph(conferenceId)
    }

    override suspend fun reactivateDelegate(conferenceId: String, delegateId: String): Conference = db {
        requireDelegateInConference(conferenceId, delegateId)
        val now = Instant.now().toEpochMilli()
        DelegateTable.update({ (DelegateTable.id eq delegateId) and (DelegateTable.conferenceId eq conferenceId) }) {
            it[DelegateTable.active] = true
        }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = now
        }
        reconcileReservations(conferenceId, now)
        logger.info("Reactivated delegate={} conference={}", delegateId, conferenceId)
        loadConferenceGraph(conferenceId)
    }

    override suspend fun createReservation(
        conferenceId: String,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: String?,
    ): Conference = db {
        val trimmed = validateReservationInput(conferenceId, name, highSchoolCount, universityCount, delegationId)
        val now = Instant.now().toEpochMilli()
        ReservationTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[ReservationTable.conferenceId] = conferenceId
            it[ReservationTable.name] = trimmed
            it[ReservationTable.highSchoolCount] = highSchoolCount
            it[ReservationTable.universityCount] = universityCount
            it[ReservationTable.delegationId] = delegationId
            it[createdAtEpochMillis] = now
            it[updatedAtEpochMillis] = now
        }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = now
        }
        reconcileReservations(conferenceId, now)
        loadConferenceGraph(conferenceId)
    }

    override suspend fun updateReservation(
        conferenceId: String,
        reservationId: String,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: String?,
    ): Conference = db {
        val trimmed = validateReservationInput(conferenceId, name, highSchoolCount, universityCount, delegationId)
        val conference = loadConferenceGraph(conferenceId)
        val reservation = requireNotNull(conference.reservations.find { it.id.value == reservationId }) {
            "Reservation $reservationId does not belong to conference $conferenceId."
        }
        val manualHighSchoolUsage = reservation.delegateLinks.count {
            it.source == ReservationLinkSource.Manual && it.delegate.active && it.delegate.isHighSchooler
        }
        val manualUniversityUsage = reservation.delegateLinks.count {
            it.source == ReservationLinkSource.Manual && it.delegate.active && !it.delegate.isHighSchooler
        }
        require(highSchoolCount >= manualHighSchoolUsage) {
            "Reservation cannot be reduced below $manualHighSchoolUsage manually linked high-school delegates."
        }
        require(universityCount >= manualUniversityUsage) {
            "Reservation cannot be reduced below $manualUniversityUsage manually linked university delegates."
        }

        val now = Instant.now().toEpochMilli()
        ReservationTable.update({ (ReservationTable.id eq reservationId) and (ReservationTable.conferenceId eq conferenceId) }) {
            it[ReservationTable.name] = trimmed
            it[ReservationTable.highSchoolCount] = highSchoolCount
            it[ReservationTable.universityCount] = universityCount
            it[ReservationTable.delegationId] = delegationId
            it[updatedAtEpochMillis] = now
        }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = now
        }
        reconcileReservations(conferenceId, now)
        loadConferenceGraph(conferenceId)
    }

    override suspend fun deleteReservation(conferenceId: String, reservationId: String): Conference = db {
        requireReservationInConference(conferenceId, reservationId)
        val now = Instant.now().toEpochMilli()
        ReservationTable.deleteWhere { (ReservationTable.id eq reservationId) and (ReservationTable.conferenceId eq conferenceId) }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = now
        }
        reconcileReservations(conferenceId, now)
        loadConferenceGraph(conferenceId)
    }

    override suspend fun assignDelegateReservation(
        conferenceId: String,
        delegateId: String,
        reservationId: String,
    ): Conference = db {
        requireReservationInConference(conferenceId, reservationId)
        val delegateRow = requireDelegateInConference(conferenceId, delegateId)
        require(delegateRow[DelegateTable.active]) { "Inactive delegates cannot consume reservation capacity." }

        val conference = loadConferenceGraph(conferenceId)
        val delegate = requireNotNull(conference.delegates.find { it.id.value == delegateId }) {
            "Delegate $delegateId does not belong to conference $conferenceId."
        }
        val reservation = requireNotNull(conference.reservations.find { it.id.value == reservationId }) {
            "Reservation $reservationId does not belong to conference $conferenceId."
        }
        val usedInBucket = reservation.delegateLinks.count { link ->
            link.delegate.active &&
                link.delegate.id != delegate.id &&
                link.delegate.isHighSchooler == delegate.isHighSchooler
        }
        val capacity = if (delegate.isHighSchooler) reservation.highSchoolCount else reservation.universityCount
        require(usedInBucket < capacity) {
            val bucket = if (delegate.isHighSchooler) "high-school" else "university"
            "Reservation has no remaining $bucket capacity for delegate ${delegate.displayName}."
        }

        val now = Instant.now().toEpochMilli()
        ReservationDelegateLinkTable.deleteWhere { ReservationDelegateLinkTable.delegateId eq delegateId }
        ReservationDelegateLinkTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[ReservationDelegateLinkTable.reservationId] = reservationId
            it[ReservationDelegateLinkTable.delegateId] = delegateId
            it[linkSource] = ReservationLinkSource.Manual.name
            it[createdAtEpochMillis] = now
            it[updatedAtEpochMillis] = now
        }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = now
        }
        reconcileReservations(conferenceId, now)
        loadConferenceGraph(conferenceId)
    }

    override suspend fun removeDelegateReservation(conferenceId: String, delegateId: String): Conference = db {
        requireDelegateInConference(conferenceId, delegateId)
        val existingLink = ReservationDelegateLinkTable.selectAll()
            .where { ReservationDelegateLinkTable.delegateId eq delegateId }
            .singleOrNull()
            ?: return@db loadConferenceGraph(conferenceId)
        require(existingLink[ReservationDelegateLinkTable.linkSource] == ReservationLinkSource.Manual.name) {
            "Automatic reservation links are maintained by delegation reconciliation."
        }

        val now = Instant.now().toEpochMilli()
        ReservationDelegateLinkTable.deleteWhere { ReservationDelegateLinkTable.id eq existingLink[ReservationDelegateLinkTable.id] }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = now
        }
        reconcileReservations(conferenceId, now)
        loadConferenceGraph(conferenceId)
    }

    override suspend fun addDelegateComment(conferenceId: String, delegateId: String, body: String): Conference = db {
        val trimmed = body.trim()
        require(trimmed.isNotBlank()) { "Comment body must not be blank." }
        requireDelegateInConference(conferenceId, delegateId)
        val now = Instant.now().toEpochMilli()
        DelegateCommentTable.insert {
            it[DelegateCommentTable.id] = UUID.randomUUID().toString()
            it[DelegateCommentTable.delegateId] = delegateId
            it[DelegateCommentTable.body] = trimmed
            it[DelegateCommentTable.createdAtEpochMillis] = now
            it[DelegateCommentTable.updatedAtEpochMillis] = now
        }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = now
        }
        loadConferenceGraph(conferenceId)
    }

    override suspend fun updateDelegateComment(conferenceId: String, commentId: String, body: String): Conference = db {
        val trimmed = body.trim()
        require(trimmed.isNotBlank()) { "Comment body must not be blank." }
        requireCommentInConference(conferenceId, commentId)
        val now = Instant.now().toEpochMilli()
        DelegateCommentTable.update({ DelegateCommentTable.id eq commentId }) {
            it[DelegateCommentTable.body] = trimmed
            it[updatedAtEpochMillis] = now
        }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = now
        }
        loadConferenceGraph(conferenceId)
    }

    override suspend fun deleteDelegateComment(conferenceId: String, commentId: String): Conference = db {
        requireCommentInConference(conferenceId, commentId)
        val now = Instant.now().toEpochMilli()
        DelegateCommentTable.deleteWhere { DelegateCommentTable.id eq commentId }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = now
        }
        loadConferenceGraph(conferenceId)
    }

    override suspend fun updateQuestionConfiguration(
        conferenceId: String,
        configuration: QuestionConfiguration,
    ): Conference = db {
        val questionId = configuration.questionId.value
        val question = requireNotNull(
            DelegateQuestionTable.selectAll()
                .where { (DelegateQuestionTable.id eq questionId) and (DelegateQuestionTable.conferenceId eq conferenceId) }
                .singleOrNull(),
        ) { "Question $questionId does not belong to conference $conferenceId." }
        require(question[DelegateQuestionTable.questionText] == configuration.questionText) {
            "Imported question text is controlled by MyMUN updates."
        }

        val now = Instant.now().toEpochMilli()
        DelegateQuestionTable.update({ DelegateQuestionTable.id eq questionId }) {
            it[roleId] = configuration.role?.id
            it[handlingTypeId] = configuration.handling.id
            it[visibleInDelegateDetail] = configuration.role == null && configuration.visibleInDelegateDetail
            it[organizerLabel] = configuration.organizerLabel?.trim()?.takeIf(String::isNotBlank)
            it[displayOrder] = configuration.displayOrder
        }
        replaceQuestionRules(questionId, configuration)
        when (configuration.role) {
            StandardQuestionRoles.ExperienceLevel -> replaceMunExperienceMappings(questionId, configuration.mappings.munExperience)
            StandardQuestionRoles.HighSchoolerStatus -> replaceHighSchoolerMappings(questionId, configuration.mappings.highSchooler)
            StandardQuestionRoles.LanguageLevel -> replaceLanguageLevelMappings(questionId, configuration.mappings.languageLevel)
            null -> Unit
        }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = now
        }
        loadConferenceGraph(conferenceId)
    }

    override suspend fun reorderQuestionConfigurations(
        conferenceId: String,
        questionIdsInDisplayOrder: List<String>,
    ): Conference = db {
        val knownIds = DelegateQuestionTable.selectAll()
            .where { DelegateQuestionTable.conferenceId eq conferenceId }
            .map { it[DelegateQuestionTable.id] }
            .toSet()
        require(questionIdsInDisplayOrder.toSet() == knownIds && questionIdsInDisplayOrder.size == knownIds.size) {
            "Question reorder must include every known question exactly once."
        }
        val now = Instant.now().toEpochMilli()
        questionIdsInDisplayOrder.forEachIndexed { index, questionId ->
            DelegateQuestionTable.update({ DelegateQuestionTable.id eq questionId }) {
                it[displayOrder] = index
            }
        }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = now
        }
        loadConferenceGraph(conferenceId)
    }

    override suspend fun resolveAnswerIssue(
        conferenceId: String,
        delegateId: String,
        questionId: String?,
        issueKey: String,
        justification: String?,
    ): Conference = db {
        val conference = loadConferenceGraph(conferenceId)
        val delegate = requireNotNull(conference.delegates.firstOrNull { it.id.value == delegateId }) {
            "Delegate $delegateId does not belong to conference $conferenceId."
        }
        val currentIssue = if (questionId == null) {
            delegate.profileIssues().firstOrNull { it.key == issueKey }
        } else {
            delegate.answers
                .firstOrNull { it.question.id.value == questionId }
                ?.issues
                ?.firstOrNull { it.key == issueKey }
        }
        requireNotNull(currentIssue) { "Issue is no longer present for this delegate." }

        val trimmedJustification = justification?.trim()?.takeIf { it.isNotBlank() }
        val now = Instant.now().toEpochMilli()
        val existing = AnswerIssueResolutionTable.selectAll().where {
            (AnswerIssueResolutionTable.conferenceId eq conferenceId) and
                (AnswerIssueResolutionTable.delegateId eq delegateId) and
                (AnswerIssueResolutionTable.issueKey eq issueKey)
        }.firstOrNull { row -> row[AnswerIssueResolutionTable.questionId] == questionId }

        if (existing == null) {
            AnswerIssueResolutionTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[AnswerIssueResolutionTable.conferenceId] = conferenceId
                it[AnswerIssueResolutionTable.delegateId] = delegateId
                it[AnswerIssueResolutionTable.questionId] = questionId
                it[AnswerIssueResolutionTable.issueKey] = issueKey
                it[issueMessage] = currentIssue.message
                it[severity] = currentIssue.severity.name
                it[AnswerIssueResolutionTable.justification] = trimmedJustification
                it[resolvedAtEpochMillis] = now
            }
        } else {
            AnswerIssueResolutionTable.update({ AnswerIssueResolutionTable.id eq existing[AnswerIssueResolutionTable.id] }) {
                it[issueMessage] = currentIssue.message
                it[severity] = currentIssue.severity.name
                it[AnswerIssueResolutionTable.justification] = trimmedJustification
                it[resolvedAtEpochMillis] = now
            }
        }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = now
        }
        loadConferenceGraph(conferenceId)
    }

    override suspend fun updateAnswerIssueResolutionJustification(
        conferenceId: String,
        resolutionId: String,
        justification: String?,
    ): Conference = db {
        requireAnswerIssueResolutionInConference(conferenceId, resolutionId)
        val trimmedJustification = justification?.trim()?.takeIf { it.isNotBlank() }
        val now = Instant.now().toEpochMilli()
        AnswerIssueResolutionTable.update({ AnswerIssueResolutionTable.id eq resolutionId }) {
            it[AnswerIssueResolutionTable.justification] = trimmedJustification
            it[resolvedAtEpochMillis] = now
        }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = now
        }
        loadConferenceGraph(conferenceId)
    }

    override suspend fun unresolveAnswerIssue(conferenceId: String, resolutionId: String): Conference = db {
        requireAnswerIssueResolutionInConference(conferenceId, resolutionId)
        AnswerIssueResolutionTable.deleteWhere { id eq resolutionId }
        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[updatedAtEpochMillis] = Instant.now().toEpochMilli()
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

        val resolvedIdByRow: Map<Int, String?> = snapshot.delegates.associate { delegate ->
            delegate.rowNumber to resolveExistingDelegate(delegate, existingDelegates)?.id
        }
        resolvedIdByRow.entries
            .filter { it.value != null }
            .groupBy({ it.value!! }, { it.key })
            .filterValues { it.size > 1 }
            .forEach { (existingDelegateId, rowNumbers) ->
                val existingName = existingDelegateRowsById[existingDelegateId]?.let {
                    "${it[DelegateTable.firstName]} ${it[DelegateTable.lastName]}"
                } ?: "an existing delegate"
                issues += UpdateIssue(
                    severity = UpdateIssueSeverity.Error,
                    code = "ambiguous_delegate_match",
                    message = "Delegates rows ${rowNumbers.sorted().joinToString()} all match $existingName and cannot be " +
                        "told apart. Add a distinguishing email, zip code, or date of birth before updating.",
                    sheetName = "Delegates",
                )
            }

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

        val observedAnswersByQuestionId = existingQuestions.values.associate { question ->
            val questionId = question[DelegateQuestionTable.id]
            questionId to observedAnswers(questionId)
        }
        snapshot.questionAnswers
            .filter { !it.answer.isNullOrBlank() }
            .distinctBy { normalize(it.questionColumn.questionText) to normalizeAnswer(it.answer.orEmpty()) }
            .forEach { answerCell ->
                val rawAnswer = answerCell.answer ?: return@forEach
                val normalizedQuestion = normalize(answerCell.questionColumn.questionText)
                val existingQuestion = existingQuestions[normalizedQuestion]
                val roleId = if (existingQuestion == null) {
                    inferQuestionRoleId(answerCell.questionColumn.questionText)
                } else {
                    existingQuestion[DelegateQuestionTable.roleId]
                }
                val mappedAnswers = mappedAnswers(existingQuestion?.get(DelegateQuestionTable.id), roleId)
                val observedAnswers = existingQuestion
                    ?.get(DelegateQuestionTable.id)
                    ?.let(observedAnswersByQuestionId::get)
                    .orEmpty()
                val normalizedAnswer = normalizeAnswer(rawAnswer)
                if (roleId != null && normalizedAnswer !in mappedAnswers && normalizedAnswer !in observedAnswers) {
                    issues += UpdateIssue(
                        severity = UpdateIssueSeverity.Warning,
                        code = "unmapped_role_answer",
                        message = "Question '${answerCell.questionColumn.questionText}' has unmapped answer '$rawAnswer'.",
                        sheetName = answerCell.location.sheetName,
                        rowNumber = answerCell.location.rowNumber,
                        columnNumber = answerCell.location.columnNumber,
                        columnName = answerCell.location.columnName,
                    )
                }
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
                    it[CommitteeTable.isHighSchoolCommittee] = false
                    it[CommitteeTable.mode] = CommitteeMode.University.name
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
                        it[PositionTable.mappedCommitteeId] = null
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
            val delegateId = resolvedIdByRow[delegate.rowNumber] ?: UUID.randomUUID().toString()
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
            val roleId = inferQuestionRoleId(questionColumn.questionText)
            val handlingId = inferQuestionHandlingId(questionColumn.questionText, roleId)

            if (existingQuestion == null) {
                DelegateQuestionTable.insert {
                    it[DelegateQuestionTable.id] = questionId
                    it[DelegateQuestionTable.conferenceId] = conferenceId
                    it[DelegateQuestionTable.questionText] = questionColumn.questionText
                    it[DelegateQuestionTable.normalizedQuestionText] = normalizedQuestion
                    it[DelegateQuestionTable.roleId] = roleId
                    it[DelegateQuestionTable.handlingTypeId] = handlingId
                    it[DelegateQuestionTable.visibleInDelegateDetail] = roleId == null
                    it[DelegateQuestionTable.organizerLabel] = null
                    it[DelegateQuestionTable.displayOrder] = questionColumn.displayOrder
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
        clearInvalidPositionMappings(conferenceId)

        val pendingAfterAssignmentReconcile = countPendingAssignmentsForConference(conferenceId)
        delta.pendingAssignmentsPreserved = pendingAfterAssignmentReconcile
        delta.pendingAssignmentsCleared = (pendingBeforeUpdate - pendingAfterAssignmentReconcile).coerceAtLeast(0)

        ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
            it[ConferenceTable.updatedAtEpochMillis] = Instant.now().toEpochMilli()
            it[ConferenceTable.lastMymunUpdateAtEpochMillis] = Instant.now().toEpochMilli()
        }
        reconcileReservations(conferenceId, Instant.now().toEpochMilli())

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

    private fun requireSlotInConference(conferenceId: String, slotId: String): ResultRow {
        val slot = requireNotNull(SlotTable.selectAll().where { SlotTable.id eq slotId }.singleOrNull()) {
            "Slot $slotId does not exist."
        }
        val position = requireNotNull(
            PositionTable.selectAll()
                .where { (PositionTable.id eq slot[SlotTable.positionId]) and (PositionTable.conferenceId eq conferenceId) }
                .singleOrNull(),
        ) { "Slot $slotId does not belong to conference $conferenceId." }
        require(position[PositionTable.conferenceId] == conferenceId) {
            "Slot $slotId does not belong to conference $conferenceId."
        }
        return slot
    }

    private fun requireDelegateInConference(conferenceId: String, delegateId: String): ResultRow =
        requireNotNull(
            DelegateTable.selectAll()
                .where { (DelegateTable.id eq delegateId) and (DelegateTable.conferenceId eq conferenceId) }
                .singleOrNull(),
        ) { "Delegate $delegateId does not belong to conference $conferenceId." }

    private fun requireCommitteeInConference(conferenceId: String, committeeId: String): ResultRow =
        requireNotNull(
            CommitteeTable.selectAll()
                .where { (CommitteeTable.id eq committeeId) and (CommitteeTable.conferenceId eq conferenceId) }
                .singleOrNull(),
        ) { "Committee $committeeId does not belong to conference $conferenceId." }

    private fun requireReservationInConference(conferenceId: String, reservationId: String): ResultRow =
        requireNotNull(
            ReservationTable.selectAll()
                .where { (ReservationTable.id eq reservationId) and (ReservationTable.conferenceId eq conferenceId) }
                .singleOrNull(),
        ) { "Reservation $reservationId does not belong to conference $conferenceId." }

    private fun validateReservationInput(
        conferenceId: String,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: String?,
    ): String {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Reservation name must not be blank." }
        require(highSchoolCount >= 0) { "High-school reservation count must not be negative." }
        require(universityCount >= 0) { "University reservation count must not be negative." }
        require(highSchoolCount > 0 || universityCount > 0) {
            "Reservation must reserve at least one high-school or university delegate."
        }
        if (delegationId != null) {
            require(
                DelegationTable.selectAll()
                    .where { (DelegationTable.id eq delegationId) and (DelegationTable.conferenceId eq conferenceId) }
                    .any(),
            ) { "Delegation $delegationId does not belong to conference $conferenceId." }
        }
        return trimmed
    }

    private fun requireCommentInConference(conferenceId: String, commentId: String): ResultRow {
        val comment = requireNotNull(
            DelegateCommentTable.selectAll()
                .where { DelegateCommentTable.id eq commentId }
                .singleOrNull(),
        ) { "Comment $commentId does not exist." }
        requireDelegateInConference(conferenceId, comment[DelegateCommentTable.delegateId])
        return comment
    }

    private fun requireAnswerIssueResolutionInConference(conferenceId: String, resolutionId: String): ResultRow =
        requireNotNull(
            AnswerIssueResolutionTable.selectAll()
                .where { (AnswerIssueResolutionTable.id eq resolutionId) and (AnswerIssueResolutionTable.conferenceId eq conferenceId) }
                .singleOrNull(),
        ) { "Issue resolution $resolutionId does not belong to conference $conferenceId." }

    private fun clearSlotAssignment(slotId: String, updatedAt: Long) {
        SlotTable.update({ SlotTable.id eq slotId }) {
            it[assignedDelegateId] = null
            it[assignmentSource] = null
            it[updatedAtEpochMillis] = updatedAt
        }
    }

    private fun reconcileReservations(conferenceId: String, nowEpochMillis: Long) {
        val conference = loadConferenceGraph(conferenceId)
        val currentLinks = conference.reservations.flatMap { it.delegateLinks }
        val result = ReservationReconciler.reconcile(
            conference = conference,
            existingLinks = currentLinks,
            now = Instant.ofEpochMilli(nowEpochMillis),
            idFactory = { ReservationDelegateLinkId(UUID.randomUUID().toString()) },
        )
        result.removedLinkIds.forEach { linkId ->
            ReservationDelegateLinkTable.deleteWhere { ReservationDelegateLinkTable.id eq linkId.value }
        }
        result.links.forEach { link ->
            upsertReservationDelegateLink(link)
        }
    }

    private fun clearInvalidPositionMappings(conferenceId: String) {
        val validMappedTargetIds = CommitteeTable.selectAll()
            .where {
                (CommitteeTable.conferenceId eq conferenceId) and
                    (CommitteeTable.mode neq CommitteeMode.Mapped.name)
            }
            .map { it[CommitteeTable.id] }
            .toSet()
        PositionTable.selectAll()
            .where { PositionTable.conferenceId eq conferenceId }
            .filter { row ->
                val mappedCommitteeId = row[PositionTable.mappedCommitteeId]
                mappedCommitteeId != null && !validMappedTargetIds.contains(mappedCommitteeId)
            }
            .forEach { row ->
                PositionTable.update({ PositionTable.id eq row[PositionTable.id] }) {
                    it[PositionTable.mappedCommitteeId] = null
                }
            }
    }

    private fun upsertReservationDelegateLink(link: ReservationDelegateLink) {
        val existing = ReservationDelegateLinkTable.selectAll()
            .where { ReservationDelegateLinkTable.id eq link.id.value }
            .singleOrNull()
        if (existing == null) {
            ReservationDelegateLinkTable.insert {
                it[id] = link.id.value
                it[reservationId] = link.reservation.id.value
                it[delegateId] = link.delegate.id.value
                it[linkSource] = link.source.name
                it[createdAtEpochMillis] = link.createdAt.toEpochMilli()
                it[updatedAtEpochMillis] = link.updatedAt.toEpochMilli()
            }
        } else {
            ReservationDelegateLinkTable.update({ ReservationDelegateLinkTable.id eq link.id.value }) {
                it[reservationId] = link.reservation.id.value
                it[delegateId] = link.delegate.id.value
                it[linkSource] = link.source.name
                it[updatedAtEpochMillis] = link.updatedAt.toEpochMilli()
            }
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

    private fun inferQuestionRoleId(questionText: String): String? {
        val normalized = normalize(questionText)
        return when {
            Regex("""\bhigh[- ]?school(er|ers)?\b""").containsMatchIn(normalized) ->
                StandardQuestionRoles.HighSchoolerStatus.id
            Regex("""\b(mun|model united nations)\s+experience\b""").containsMatchIn(normalized) ||
                "experience level" in normalized ||
                Regex("""\bhow\s+(much\s+)?(mun\s+)?experience\b""").containsMatchIn(normalized) ->
                StandardQuestionRoles.ExperienceLevel.id
            "english level" in normalized ||
                "level of english" in normalized ||
                "language level" in normalized ||
                Regex("""\b(english|language)\s+proficiency\b""").containsMatchIn(normalized) ->
                StandardQuestionRoles.LanguageLevel.id
            else -> null
        }
    }

    private fun inferQuestionHandlingId(questionText: String, roleId: String?): String {
        val normalized = normalize(questionText)
        return when {
            roleId != null -> StandardQuestionHandlingTypes.ExpectedTypedAnswer.id
            isCommitteeExplanationQuestion(normalized) -> StandardQuestionHandlingTypes.CommitteeChoiceExplanation.id
            "visa" in normalized -> StandardQuestionHandlingTypes.VisaSupport.id
            "delegation" in normalized -> StandardQuestionHandlingTypes.DelegationMembership.id
            else -> StandardQuestionHandlingTypes.Default.id
        }
    }

    private fun isCommitteeExplanationQuestion(normalizedQuestionText: String): Boolean =
        ("why" in normalizedQuestionText && "committee" in normalizedQuestionText) ||
            ("explain" in normalizedQuestionText &&
                ("committee" in normalizedQuestionText || "comittee" in normalizedQuestionText) &&
                "choice" in normalizedQuestionText)

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
                ReservationTable.update({ ReservationTable.delegationId eq delegationId }) {
                    it[ReservationTable.delegationId] = null
                }
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

    private fun replaceQuestionRules(questionId: String, configuration: QuestionConfiguration) {
        DelegateQuestionRuleTable.deleteWhere { DelegateQuestionRuleTable.questionId eq questionId }
        configuration.rules.forEach { rule ->
            DelegateQuestionRuleTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[DelegateQuestionRuleTable.questionId] = questionId
                when (rule) {
                    is MinimumSentenceCount -> {
                        it[ruleTypeId] = "minimum_sentence_count"
                        it[intParameter] = rule.count
                        it[textParameter] = null
                    }
                    is MinimumTextLength -> {
                        it[ruleTypeId] = "minimum_text_length"
                        it[intParameter] = rule.length
                        it[textParameter] = null
                    }
                    is NoticeWhenNonBlank -> {
                        it[ruleTypeId] = "notice_when_non_blank"
                        it[intParameter] = null
                        it[textParameter] = rule.message
                    }
                    is NoticeWhenAffirmative -> {
                        it[ruleTypeId] = "notice_when_affirmative"
                        it[intParameter] = null
                        it[textParameter] = rule.message
                    }
                    is RequiredAnswer -> {
                        it[ruleTypeId] = "required_answer"
                        it[intParameter] = null
                        it[textParameter] = rule.message
                    }
                }
            }
        }
    }

    private fun replaceMunExperienceMappings(questionId: String, mappings: List<MunExperienceAnswerMapping>) {
        MunExperienceAnswerMappingTable.deleteWhere { MunExperienceAnswerMappingTable.questionId eq questionId }
        mappings.forEach { mapping ->
            MunExperienceAnswerMappingTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[MunExperienceAnswerMappingTable.questionId] = questionId
                it[rawValue] = mapping.rawValue.trim()
                it[normalizedRawValue] = normalizeAnswer(mapping.rawValue)
                it[mappedValue] = mapping.value.name
            }
        }
    }

    private fun replaceHighSchoolerMappings(questionId: String, mappings: List<HighSchoolerAnswerMapping>) {
        HighSchoolerAnswerMappingTable.deleteWhere { HighSchoolerAnswerMappingTable.questionId eq questionId }
        mappings.forEach { mapping ->
            HighSchoolerAnswerMappingTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[HighSchoolerAnswerMappingTable.questionId] = questionId
                it[rawValue] = mapping.rawValue.trim()
                it[normalizedRawValue] = normalizeAnswer(mapping.rawValue)
                it[mappedValue] = mapping.value.name
            }
        }
    }

    private fun replaceLanguageLevelMappings(questionId: String, mappings: List<LanguageLevelAnswerMapping>) {
        LanguageLevelAnswerMappingTable.deleteWhere { LanguageLevelAnswerMappingTable.questionId eq questionId }
        mappings.forEach { mapping ->
            LanguageLevelAnswerMappingTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[LanguageLevelAnswerMappingTable.questionId] = questionId
                it[rawValue] = mapping.rawValue.trim()
                it[normalizedRawValue] = normalizeAnswer(mapping.rawValue)
                it[mappedValue] = mapping.value.name
            }
        }
    }

    private suspend fun <T> db(block: () -> T): T =
        withContext(Dispatchers.IO) {
            transaction(database) { block() }
        }

    private fun loadConferenceGraph(conferenceId: String): Conference {
        val conferenceRow = ConferenceTable.selectAll().where { ConferenceTable.id eq conferenceId }
            .single().toConferenceRow()
        val positionIds = PositionTable.selectAll().where { PositionTable.conferenceId eq conferenceId }.map { it[PositionTable.id] }
        val delegateIds = DelegateTable.selectAll().where { DelegateTable.conferenceId eq conferenceId }.map { it[DelegateTable.id] }
        val reservationRows = ReservationTable.selectAll()
            .where { ReservationTable.conferenceId eq conferenceId }
            .orderBy(ReservationTable.createdAtEpochMillis to SortOrder.ASC, ReservationTable.id to SortOrder.ASC)
            .map { row -> row.toReservationRow() }
        val reservationIds = reservationRows.map { it.id }
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
                    isHighSchoolCommittee = row[CommitteeTable.isHighSchoolCommittee],
                    mode = row[CommitteeTable.mode],
                )
            },
            positions = PositionTable.selectAll().where { PositionTable.conferenceId eq conferenceId }.map { row ->
                PositionRow(
                    id = row[PositionTable.id],
                    conferenceId = row[PositionTable.conferenceId],
                    committeeId = row[PositionTable.committeeId],
                    mappedCommitteeId = row[PositionTable.mappedCommitteeId],
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
            reservations = reservationRows,
            reservationLinks = if (reservationIds.isEmpty()) emptyList() else ReservationDelegateLinkTable.selectAll().where {
                ReservationDelegateLinkTable.reservationId inList reservationIds
            }.orderBy(
                ReservationDelegateLinkTable.createdAtEpochMillis to SortOrder.ASC,
            ).map { row -> row.toReservationDelegateLinkRow() },
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
                    displayOrder = row[DelegateQuestionTable.displayOrder],
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
            issueResolutions = AnswerIssueResolutionTable.selectAll().where {
                AnswerIssueResolutionTable.conferenceId eq conferenceId
            }.map { row ->
                AnswerIssueResolutionRow(
                    id = row[AnswerIssueResolutionTable.id],
                    conferenceId = row[AnswerIssueResolutionTable.conferenceId],
                    delegateId = row[AnswerIssueResolutionTable.delegateId],
                    questionId = row[AnswerIssueResolutionTable.questionId],
                    issueKey = row[AnswerIssueResolutionTable.issueKey],
                    issueMessage = row[AnswerIssueResolutionTable.issueMessage],
                    severity = row[AnswerIssueResolutionTable.severity],
                    justification = row[AnswerIssueResolutionTable.justification],
                    resolvedAtEpochMillis = row[AnswerIssueResolutionTable.resolvedAtEpochMillis],
                )
            },
        )
        return ConferenceGraphMapper.toDomain(rows)
    }

    private fun ResultRow.toConferenceRow(): ConferenceRow =
        ConferenceRow(
            id = this[ConferenceTable.id],
            name = this[ConferenceTable.name],
            displayOrder = this[ConferenceTable.displayOrder],
            createdAtEpochMillis = this[ConferenceTable.createdAtEpochMillis],
            updatedAtEpochMillis = this[ConferenceTable.updatedAtEpochMillis],
            lastMymunUpdateAtEpochMillis = this[ConferenceTable.lastMymunUpdateAtEpochMillis],
        )

    private fun ResultRow.toReservationRow(): ReservationRow =
        ReservationRow(
            id = this[ReservationTable.id],
            conferenceId = this[ReservationTable.conferenceId],
            name = this[ReservationTable.name],
            highSchoolCount = this[ReservationTable.highSchoolCount],
            universityCount = this[ReservationTable.universityCount],
            delegationId = this[ReservationTable.delegationId],
            createdAtEpochMillis = this[ReservationTable.createdAtEpochMillis],
            updatedAtEpochMillis = this[ReservationTable.updatedAtEpochMillis],
        )

    private fun ResultRow.toReservationDelegateLinkRow(): ReservationDelegateLinkRow =
        ReservationDelegateLinkRow(
            id = this[ReservationDelegateLinkTable.id],
            reservationId = this[ReservationDelegateLinkTable.reservationId],
            delegateId = this[ReservationDelegateLinkTable.delegateId],
            source = this[ReservationDelegateLinkTable.linkSource],
            createdAtEpochMillis = this[ReservationDelegateLinkTable.createdAtEpochMillis],
            updatedAtEpochMillis = this[ReservationDelegateLinkTable.updatedAtEpochMillis],
        )

    private fun nextConferenceDisplayOrder(): Int =
        ConferenceTable.selectAll()
            .maxOfOrNull { it[ConferenceTable.displayOrder] + 1 }
            ?: 0

    private fun normalizeConferenceDisplayOrder() {
        ConferenceTable.selectAll()
            .orderBy(
                ConferenceTable.displayOrder to SortOrder.ASC,
                ConferenceTable.createdAtEpochMillis to SortOrder.ASC,
                ConferenceTable.updatedAtEpochMillis to SortOrder.ASC,
            )
            .map { it[ConferenceTable.id] }
            .forEachIndexed { index, conferenceId ->
                ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
                    it[ConferenceTable.displayOrder] = index
                }
            }
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun normalizeAnswer(value: String): String = value.trim().lowercase()

    private fun mappedAnswers(questionId: String?, roleId: String?): Set<String> {
        val persisted = when (roleId) {
            StandardQuestionRoles.ExperienceLevel.id -> questionId?.let { id ->
                MunExperienceAnswerMappingTable.selectAll()
                    .where { MunExperienceAnswerMappingTable.questionId eq id }
                    .map { it[MunExperienceAnswerMappingTable.normalizedRawValue] }
            }.orEmpty()
            StandardQuestionRoles.HighSchoolerStatus.id -> questionId?.let { id ->
                HighSchoolerAnswerMappingTable.selectAll()
                    .where { HighSchoolerAnswerMappingTable.questionId eq id }
                    .map { it[HighSchoolerAnswerMappingTable.normalizedRawValue] }
            }.orEmpty()
            StandardQuestionRoles.LanguageLevel.id -> questionId?.let { id ->
                LanguageLevelAnswerMappingTable.selectAll()
                    .where { LanguageLevelAnswerMappingTable.questionId eq id }
                    .map { it[LanguageLevelAnswerMappingTable.normalizedRawValue] }
            }.orEmpty()
            else -> emptyList()
        }
        if (persisted.isNotEmpty()) return persisted.toSet()

        val defaults = TypedAnswerMappings.defaults()
        return when (roleId) {
            StandardQuestionRoles.ExperienceLevel.id -> defaults.munExperience.mapTo(mutableSetOf()) { normalizeAnswer(it.rawValue) }
            StandardQuestionRoles.HighSchoolerStatus.id -> defaults.highSchooler.mapTo(mutableSetOf()) { normalizeAnswer(it.rawValue) }
            StandardQuestionRoles.LanguageLevel.id -> defaults.languageLevel.mapTo(mutableSetOf()) { normalizeAnswer(it.rawValue) }
            else -> emptySet()
        }
    }

    private fun observedAnswers(questionId: String): Set<String> =
        DelegateQuestionAnswerTable.selectAll()
            .where { DelegateQuestionAnswerTable.questionId eq questionId }
            .mapNotNullTo(mutableSetOf()) { it[DelegateQuestionAnswerTable.answer]?.takeIf(String::isNotBlank)?.let(::normalizeAnswer) }

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
