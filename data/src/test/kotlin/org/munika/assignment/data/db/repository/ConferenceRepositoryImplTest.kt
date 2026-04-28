package org.munika.assignment.data.db.repository

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.munika.assignment.data.db.DatabaseFactory
import org.munika.assignment.data.db.tables.AnswerRuleTypeTable
import org.munika.assignment.data.db.tables.CommitteeTable
import org.munika.assignment.data.db.tables.DelegateCommentTable
import org.munika.assignment.data.db.tables.DelegateQuestionTable
import org.munika.assignment.data.db.tables.DelegateTable
import org.munika.assignment.data.db.tables.QuestionHandlingTypeTable
import org.munika.assignment.data.db.tables.QuestionRoleTable
import org.munika.assignment.data.excel.MymunConfirmedAssignmentCandidate
import org.munika.assignment.data.excel.MymunCountryPositionRow
import org.munika.assignment.data.excel.MymunDelegateRow
import org.munika.assignment.data.excel.MymunWorkbookSnapshot
import org.munika.assignment.data.excel.MymunWorkbookUpdater
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.repository.UpdateIssueSeverity

class ConferenceRepositoryImplTest {
    @Test
    fun `conference CRUD and cascade delete works`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-test").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val updater = MymunWorkbookUpdater(repository)

        val conference = repository.createConference("TestConf")
        val workbook = requireNotNull(javaClass.classLoader.getResource("export.xlsx"))
        val update = updater.applyUpdate(conference.id.value, java.nio.file.Paths.get(workbook.toURI()))
        assertTrue(update.success, update.issues.joinToString("; ") { it.message })

        val loaded = repository.getConference(conference.id.value)
        assertNotNull(loaded)
        assertTrue(loaded.committees.isNotEmpty())
        assertTrue(loaded.delegates.isNotEmpty())

        repository.deleteConference(conference.id.value)

        assertEquals(0, transaction(database) { CommitteeTable.selectAll().count() })
        assertEquals(0, transaction(database) { DelegateTable.selectAll().count() })
    }

    @Test
    fun `default lookup seed data exists`() {
        val dbPath = Files.createTempDirectory("munassign-seed").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)

        transaction(database) {
            val roleIds = QuestionRoleTable.selectAll().map { it[QuestionRoleTable.id] }.toSet()
            assertTrue(StandardQuestionRoles.all.all { it.id in roleIds })

            val handlingIds = QuestionHandlingTypeTable.selectAll().map { it[QuestionHandlingTypeTable.id] }.toSet()
            assertTrue(StandardQuestionHandlingTypes.all.all { it.id in handlingIds })

            val answerRuleIds = AnswerRuleTypeTable.selectAll().map { it[AnswerRuleTypeTable.id] }.toSet()
            assertTrue(
                setOf("minimum_sentence_count", "minimum_text_length", "notice_when_non_blank", "required_answer")
                    .all { it in answerRuleIds },
            )
        }
    }

    @Test
    fun `MyMUN update preserves organizer managed state for retained rows`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-preserve").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val updater = MymunWorkbookUpdater(repository)

        val conference = repository.createConference("TestConf")
        val workbook = requireNotNull(javaClass.classLoader.getResource("export.xlsx"))
        val workbookPath = java.nio.file.Paths.get(workbook.toURI())
        assertTrue(updater.applyUpdate(conference.id.value, workbookPath).success)

        val firstLoaded = requireNotNull(repository.getConference(conference.id.value))
        val delegate = firstLoaded.delegates.first()
        val committee = firstLoaded.committees.first()
        val question = firstLoaded.questions.first()

        transaction(database) {
            DelegateTable.update({ DelegateTable.id eq delegate.id.value }) {
                it[active] = false
            }
            DelegateCommentTable.insert {
                it[id] = "comment-1"
                it[delegateId] = delegate.id.value
                it[body] = "Preserve this"
                it[createdAtEpochMillis] = 1L
                it[updatedAtEpochMillis] = 1L
            }
            CommitteeTable.update({ CommitteeTable.id eq committee.id.value }) {
                it[alias] = "Local alias"
            }
            DelegateQuestionTable.update({ DelegateQuestionTable.id eq question.id.value }) {
                it[visibleInDelegateDetail] = false
                it[organizerLabel] = "Local label"
            }
        }

        assertTrue(updater.applyUpdate(conference.id.value, workbookPath).success)

        val reloaded = requireNotNull(repository.getConference(conference.id.value))
        val reloadedDelegate = requireNotNull(reloaded.delegates.find { it.id == delegate.id })
        val reloadedCommittee = requireNotNull(reloaded.committees.find { it.id == committee.id })
        val reloadedQuestion = requireNotNull(reloaded.questions.find { it.id == question.id })
        assertFalse(reloadedDelegate.active)
        assertEquals(listOf("Preserve this"), reloadedDelegate.comments.map { it.body })
        assertEquals("Local alias", reloadedCommittee.alias)
        assertFalse(reloadedQuestion.visibleInDelegateDetail)
        assertEquals("Local label", reloadedQuestion.organizerLabel)
    }

    @Test
    fun `MyMUN update rejects duplicate confirmed assignments for one delegate`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-duplicate-assignment").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        val result = repository.applyMymunUpdateSnapshot(
            conferenceId = conference.id.value,
            snapshot = duplicateAssignmentSnapshot(),
            sourceFilename = "duplicate.xlsx",
        )

        assertFalse(result.success)
        assertTrue(result.issues.any { it.severity == UpdateIssueSeverity.Error && "more than one slot" in it.message })
    }

    @Test
    fun `MyMUN update rejects ambiguous confirmed assignment display names`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-ambiguous-assignment").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        val result = repository.applyMymunUpdateSnapshot(
            conferenceId = conference.id.value,
            snapshot = duplicateAssignmentSnapshot(
                delegates = listOf(
                    delegate(rowNumber = 2, firstName = "Alex", lastName = "Kim", email = "alex1@example.test"),
                    delegate(rowNumber = 3, firstName = "Alex", lastName = "Kim", email = "alex2@example.test"),
                ),
                confirmedAssignments = listOf(
                    assignment(rowNumber = 2, positionName = "France", slotIndex = 1, delegateName = "Alex Kim"),
                ),
            ),
            sourceFilename = "ambiguous.xlsx",
        )

        assertFalse(result.success)
        assertTrue(result.issues.any { it.severity == UpdateIssueSeverity.Error && "ambiguous" in it.message })
    }

    private fun duplicateAssignmentSnapshot(
        delegates: List<MymunDelegateRow> = listOf(delegate()),
        confirmedAssignments: List<MymunConfirmedAssignmentCandidate> = listOf(
            assignment(rowNumber = 2, positionName = "France", slotIndex = 1),
            assignment(rowNumber = 3, positionName = "Germany", slotIndex = 2),
        ),
    ): MymunWorkbookSnapshot =
        MymunWorkbookSnapshot(
            workbookName = "test.xlsx",
            delegates = delegates,
            countryPositions = listOf(
                MymunCountryPositionRow(
                    rowNumber = 2,
                    committeeName = "GA",
                    memberName = "France",
                    delegationName = null,
                    delegatesRaw = "Alex Kim",
                ),
                MymunCountryPositionRow(
                    rowNumber = 3,
                    committeeName = "GA",
                    memberName = "Germany",
                    delegationName = null,
                    delegatesRaw = "Alex Kim",
                ),
            ),
            delegations = emptyList(),
            questionColumns = emptyList(),
            questionAnswers = emptyList(),
            preferenceCandidates = emptyList(),
            confirmedAssignmentCandidates = confirmedAssignments,
            issues = emptyList(),
        )

    private fun delegate(
        rowNumber: Int = 2,
        firstName: String = "Alex",
        lastName: String = "Kim",
        email: String = "alex@example.test",
    ): MymunDelegateRow =
        MymunDelegateRow(
            rowNumber = rowNumber,
            firstName = firstName,
            lastName = lastName,
            email = email,
            normalizedEmail = email,
            applicationStatus = null,
            submittedAt = null,
            createdAt = null,
            feePaid = true,
            country = null,
            city = null,
            streetAddress = null,
            zipCode = null,
            diet = null,
            phone = null,
            nationality = null,
            dateOfBirth = null,
            delegationName = null,
            isHeadDelegate = false,
            mymunAssignedPositionName = null,
            mymunAssignedCommitteeName = null,
        )

    private fun assignment(
        rowNumber: Int,
        positionName: String,
        slotIndex: Int,
        delegateName: String = "Alex Kim",
    ): MymunConfirmedAssignmentCandidate =
        MymunConfirmedAssignmentCandidate(
            countryRowNumber = rowNumber,
            committeeName = "GA",
            positionName = positionName,
            slotIndex = slotIndex,
            delegateDisplayName = delegateName,
            rawDelegatesText = delegateName,
        )
}
