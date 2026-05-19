package org.munika.assignment.data.db.repository

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
import org.munika.assignment.data.db.tables.ConferenceTable
import org.munika.assignment.data.db.tables.DelegateCommentTable
import org.munika.assignment.data.db.tables.DelegateQuestionRuleTable
import org.munika.assignment.data.db.tables.DelegateQuestionTable
import org.munika.assignment.data.db.tables.DelegateTable
import org.munika.assignment.data.db.tables.HighSchoolerAnswerMappingTable
import org.munika.assignment.data.db.tables.LanguageLevelAnswerMappingTable
import org.munika.assignment.data.db.tables.MunExperienceAnswerMappingTable
import org.munika.assignment.data.db.tables.MymunUpdateIssueTable
import org.munika.assignment.data.db.tables.PositionTable
import org.munika.assignment.data.db.tables.QuestionHandlingTypeTable
import org.munika.assignment.data.db.tables.QuestionRoleTable
import org.munika.assignment.data.db.tables.SlotTable
import org.munika.assignment.data.excel.CellLocation
import org.munika.assignment.data.excel.MymunConfirmedAssignmentCandidate
import org.munika.assignment.data.excel.MymunCountryPositionRow
import org.munika.assignment.data.excel.MymunDelegateRow
import org.munika.assignment.data.excel.MymunQuestionAnswerCell
import org.munika.assignment.data.excel.MymunQuestionColumn
import org.munika.assignment.data.excel.MymunWorkbookSnapshot
import org.munika.assignment.data.excel.MymunWorkbookUpdater
import org.munika.assignment.domain.model.AssignmentSource
import org.munika.assignment.domain.model.HighSchoolerAnswerMapping
import org.munika.assignment.domain.model.HighSchoolerStatus
import org.munika.assignment.domain.model.LanguageLevel
import org.munika.assignment.domain.model.LanguageLevelAnswerMapping
import org.munika.assignment.domain.model.MinimumTextLength
import org.munika.assignment.domain.model.MunExperienceAnswerMapping
import org.munika.assignment.domain.model.MunExperienceLevel
import org.munika.assignment.domain.model.NoticeWhenNonBlank
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.QuestionConfiguration
import org.munika.assignment.domain.model.RequiredAnswer
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.TypedAnswerMappings
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
        assertTrue(loaded.delegates.any { it.preferences.isNotEmpty() })

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
    fun `conference order is persisted and create appends`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-order").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)

        val first = repository.createConference("First")
        val second = repository.createConference("Second")
        val third = repository.createConference("Third")

        assertEquals(listOf("First", "Second", "Third"), repository.listConferences().map { it.name })

        repository.reorderConferences(listOf(third.id.value, first.id.value, second.id.value))

        assertEquals(listOf("Third", "First", "Second"), repository.listConferences().map { it.name })
        assertEquals(listOf(0, 1, 2), repository.listConferences().map { it.displayOrder })

        val fourth = repository.createConference("Fourth")

        assertEquals(listOf("Third", "First", "Second", "Fourth"), repository.listConferences().map { it.name })
        assertEquals(3, fourth.displayOrder)
    }

    @Test
    fun `conference reorder rejects missing duplicate or unknown ids`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-order-invalid").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)

        val first = repository.createConference("First")
        val second = repository.createConference("Second")

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            repository.reorderConferences(listOf(first.id.value))
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            repository.reorderConferences(listOf(first.id.value, first.id.value))
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            repository.reorderConferences(listOf(first.id.value, "missing"))
        }

        assertEquals(listOf(first.id, second.id), repository.listConferences().map { it.id })
    }

    @Test
    fun `increasing committee slot count creates empty slots for every position`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-slot-increase").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(confirmedAssignments = emptyList())).success)
        val loaded = requireNotNull(repository.getConference(conference.id.value))
        val committee = loaded.committees.single()

        val updated = repository.setCommitteeSlotsPerPosition(conference.id.value, committee.id.value, 3)

        val updatedCommittee = updated.committees.single()
        assertEquals(3, updatedCommittee.slotsPerPosition)
        assertTrue(updatedCommittee.positions.all { it.slots.map { slot -> slot.index } == listOf(1, 2, 3) })
        assertTrue(updatedCommittee.positions.all { position -> position.slots.filter { it.index > 1 }.all { it.isEmpty } })
    }

    @Test
    fun `committee high school status is persisted`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-committee-hs").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(confirmedAssignments = emptyList())).success)
        val committee = requireNotNull(repository.getConference(conference.id.value)).committees.single()

        val updated = repository.setCommitteeHighSchoolStatus(conference.id.value, committee.id.value, true)

        assertTrue(updated.committees.single().isHighSchoolCommittee)
        transaction(database) {
            assertTrue(CommitteeTable.selectAll().single()[CommitteeTable.isHighSchoolCommittee])
        }
    }

    @Test
    fun `decreasing committee slot count removes only empty trailing slots`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-slot-decrease").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(confirmedAssignments = emptyList())).success)
        val committee = requireNotNull(repository.getConference(conference.id.value)).committees.single()
        repository.setCommitteeSlotsPerPosition(conference.id.value, committee.id.value, 3)

        val updated = repository.setCommitteeSlotsPerPosition(conference.id.value, committee.id.value, 1)

        val updatedCommittee = updated.committees.single()
        assertEquals(1, updatedCommittee.slotsPerPosition)
        assertTrue(updatedCommittee.positions.all { it.slots.map { slot -> slot.index } == listOf(1) })
        transaction(database) {
            assertEquals(2, SlotTable.selectAll().count())
        }
    }

    @Test
    fun `decreasing committee slot count is blocked when removed slot is pending`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-slot-pending-block").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(confirmedAssignments = emptyList())).success)
        val loaded = requireNotNull(repository.getConference(conference.id.value))
        val committee = loaded.committees.single()
        val expanded = repository.setCommitteeSlotsPerPosition(conference.id.value, committee.id.value, 2)
        val delegate = expanded.delegates.single()
        val trailingSlot = expanded.positions.single { it.name == "Germany" }.slots.single { it.index == 2 }
        transaction(database) {
            SlotTable.update({ SlotTable.id eq trailingSlot.id.value }) {
                it[assignedDelegateId] = delegate.id.value
                it[assignmentSource] = AssignmentSource.Application.name
                it[updatedAtEpochMillis] = 2L
            }
        }

        assertFailsWith<IllegalArgumentException> {
            repository.setCommitteeSlotsPerPosition(conference.id.value, committee.id.value, 1)
        }

        val reloaded = requireNotNull(repository.getConference(conference.id.value)).committees.single()
        assertEquals(2, reloaded.slotsPerPosition)
        assertEquals(1, requireNotNull(repository.getConference(conference.id.value)).pendingSlots.size)
    }

    @Test
    fun `decreasing committee slot count is blocked when removed slot is confirmed`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-slot-confirmed-block").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(confirmedAssignments = emptyList())).success)
        val loaded = requireNotNull(repository.getConference(conference.id.value))
        val committee = loaded.committees.single()
        val expanded = repository.setCommitteeSlotsPerPosition(conference.id.value, committee.id.value, 2)
        val delegate = expanded.delegates.single()
        val trailingSlot = expanded.positions.single { it.name == "Germany" }.slots.single { it.index == 2 }
        transaction(database) {
            SlotTable.update({ SlotTable.id eq trailingSlot.id.value }) {
                it[assignedDelegateId] = delegate.id.value
                it[assignmentSource] = AssignmentSource.Mymun.name
                it[updatedAtEpochMillis] = 2L
            }
        }

        assertFailsWith<IllegalArgumentException> {
            repository.setCommitteeSlotsPerPosition(conference.id.value, committee.id.value, 1)
        }

        val reloaded = requireNotNull(repository.getConference(conference.id.value)).committees.single()
        assertEquals(2, reloaded.slotsPerPosition)
        assertEquals(1, requireNotNull(repository.getConference(conference.id.value)).assignedSlots.size)
    }

    @Test
    fun `manual assignment persists as pending`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-manual-assign").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(confirmedAssignments = emptyList())).success)
        val loaded = requireNotNull(repository.getConference(conference.id.value))
        val delegate = loaded.delegates.single()
        val slot = loaded.openSlots.first()

        val updated = repository.assignDelegateToSlot(conference.id.value, slot.id.value, delegate.id.value, moveExistingPending = false)

        val pendingSlot = updated.pendingSlots.single()
        assertEquals(slot.id, pendingSlot.id)
        assertEquals(delegate.id, pendingSlot.delegate?.id)
        assertEquals(AssignmentSource.Application, pendingSlot.source)
    }

    @Test
    fun `clearing pending slot persists and frees delegate`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-clear-pending").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(confirmedAssignments = emptyList())).success)
        val loaded = requireNotNull(repository.getConference(conference.id.value))
        val delegate = loaded.delegates.single()
        val slot = loaded.openSlots.first()
        repository.assignDelegateToSlot(conference.id.value, slot.id.value, delegate.id.value, moveExistingPending = false)

        val updated = repository.clearPendingSlot(conference.id.value, slot.id.value)

        assertTrue(updated.pendingSlots.isEmpty())
        assertNull(updated.delegates.single().currentSlot)
    }

    @Test
    fun `manual assignment replaces pending slot with unassigned delegate`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-replace-pending").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val delegates = listOf(
            delegate(rowNumber = 2, firstName = "Alex", lastName = "Kim", email = "alex@example.test"),
            delegate(rowNumber = 3, firstName = "Blake", lastName = "Roy", email = "blake@example.test"),
        )

        assertTrue(
            repository.applyMymunUpdateSnapshot(
                conference.id.value,
                snapshotWithAssignments(delegates = delegates, confirmedAssignments = emptyList()),
            ).success,
        )
        val loaded = requireNotNull(repository.getConference(conference.id.value))
        val firstDelegate = loaded.delegates.single { it.name.firstName == "Alex" }
        val secondDelegate = loaded.delegates.single { it.name.firstName == "Blake" }
        val slot = loaded.openSlots.first()
        repository.assignDelegateToSlot(conference.id.value, slot.id.value, firstDelegate.id.value, moveExistingPending = false)

        val updated = repository.assignDelegateToSlot(conference.id.value, slot.id.value, secondDelegate.id.value, moveExistingPending = false)

        assertEquals(secondDelegate.id, updated.pendingSlots.single().delegate?.id)
        assertNull(updated.delegates.single { it.id == firstDelegate.id }.currentSlot)
    }

    @Test
    fun `manual assignment requires confirmation before moving existing pending assignment`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-move-pending").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(confirmedAssignments = emptyList())).success)
        val loaded = requireNotNull(repository.getConference(conference.id.value))
        val delegate = loaded.delegates.single()
        val sourceSlot = loaded.positions.single { it.name == "France" }.slots.single()
        val targetSlot = loaded.positions.single { it.name == "Germany" }.slots.single()
        repository.assignDelegateToSlot(conference.id.value, sourceSlot.id.value, delegate.id.value, moveExistingPending = false)

        assertFailsWith<IllegalArgumentException> {
            repository.assignDelegateToSlot(conference.id.value, targetSlot.id.value, delegate.id.value, moveExistingPending = false)
        }

        val moved = repository.assignDelegateToSlot(conference.id.value, targetSlot.id.value, delegate.id.value, moveExistingPending = true)
        assertEquals(targetSlot.id, moved.pendingSlots.single().id)
        assertTrue(moved.positions.single { it.name == "France" }.slots.single().isEmpty)
    }

    @Test
    fun `manual assignment cannot change confirmed assignments or confirmed delegates`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-confirmed-read-only").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val delegates = listOf(
            delegate(rowNumber = 2, firstName = "Alex", lastName = "Kim", email = "alex@example.test"),
            delegate(rowNumber = 3, firstName = "Blake", lastName = "Roy", email = "blake@example.test"),
        )

        assertTrue(
            repository.applyMymunUpdateSnapshot(
                conference.id.value,
                snapshotWithAssignments(
                    delegates = delegates,
                    confirmedAssignments = listOf(assignment(rowNumber = 2, positionName = "France", slotIndex = 1, delegateName = "Alex Kim")),
                ),
            ).success,
        )
        val loaded = requireNotNull(repository.getConference(conference.id.value))
        val confirmedDelegate = loaded.delegates.single { it.name.firstName == "Alex" }
        val unassignedDelegate = loaded.delegates.single { it.name.firstName == "Blake" }
        val confirmedSlot = loaded.assignedSlots.single()
        val emptySlot = loaded.openSlots.single()

        assertFailsWith<IllegalArgumentException> {
            repository.clearPendingSlot(conference.id.value, confirmedSlot.id.value)
        }
        assertFailsWith<IllegalArgumentException> {
            repository.assignDelegateToSlot(conference.id.value, confirmedSlot.id.value, unassignedDelegate.id.value, moveExistingPending = false)
        }
        assertFailsWith<IllegalArgumentException> {
            repository.assignDelegateToSlot(conference.id.value, emptySlot.id.value, confirmedDelegate.id.value, moveExistingPending = true)
        }
        Unit
    }

    @Test
    fun `manual assignment rejects unpaid and inactive delegates`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-unavailable-delegates").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val delegates = listOf(
            delegate(rowNumber = 2, firstName = "Alex", lastName = "Kim", email = "alex@example.test"),
            delegate(rowNumber = 3, firstName = "Blake", lastName = "Roy", email = "blake@example.test"),
        )

        assertTrue(
            repository.applyMymunUpdateSnapshot(
                conference.id.value,
                snapshotWithAssignments(delegates = delegates, confirmedAssignments = emptyList()),
            ).success,
        )
        val loaded = requireNotNull(repository.getConference(conference.id.value))
        val unpaidDelegate = loaded.delegates.single { it.name.firstName == "Alex" }
        val inactiveDelegate = loaded.delegates.single { it.name.firstName == "Blake" }
        val slots = loaded.openSlots
        transaction(database) {
            DelegateTable.update({ DelegateTable.id eq unpaidDelegate.id.value }) {
                it[paymentStatus] = PaymentStatus.Unpaid.name
            }
            DelegateTable.update({ DelegateTable.id eq inactiveDelegate.id.value }) {
                it[active] = false
            }
        }

        assertFailsWith<IllegalArgumentException> {
            repository.assignDelegateToSlot(conference.id.value, slots[0].id.value, unpaidDelegate.id.value, moveExistingPending = false)
        }
        assertFailsWith<IllegalArgumentException> {
            repository.assignDelegateToSlot(conference.id.value, slots[1].id.value, inactiveDelegate.id.value, moveExistingPending = false)
        }
        Unit
    }

    @Test
    fun `deactivating delegate clears pending assignment and reactivation does not restore it`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-deactivate-pending").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(confirmedAssignments = emptyList())).success)
        val loaded = requireNotNull(repository.getConference(conference.id.value))
        val delegate = loaded.delegates.single()
        val slot = loaded.openSlots.first()
        repository.assignDelegateToSlot(conference.id.value, slot.id.value, delegate.id.value, moveExistingPending = false)

        val deactivated = repository.deactivateDelegate(conference.id.value, delegate.id.value)

        val inactiveDelegate = deactivated.delegates.single()
        assertFalse(inactiveDelegate.active)
        assertNull(inactiveDelegate.currentSlot)
        assertTrue(deactivated.pendingSlots.isEmpty())

        val reactivated = repository.reactivateDelegate(conference.id.value, delegate.id.value)

        val activeDelegate = reactivated.delegates.single()
        assertTrue(activeDelegate.active)
        assertNull(activeDelegate.currentSlot)
        assertTrue(reactivated.pendingSlots.isEmpty())
        Unit
    }

    @Test
    fun `deactivating confirmed delegate is rejected`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-deactivate-confirmed").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments()).success)
        val delegate = requireNotNull(repository.getConference(conference.id.value)).delegates.single()

        assertFailsWith<IllegalArgumentException> {
            repository.deactivateDelegate(conference.id.value, delegate.id.value)
        }

        val reloaded = requireNotNull(repository.getConference(conference.id.value)).delegates.single()
        assertTrue(reloaded.active)
        assertNotNull(reloaded.currentSlot)
        Unit
    }

    @Test
    fun `delegate comments can be added updated and deleted`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-comments").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(confirmedAssignments = emptyList())).success)
        val delegate = requireNotNull(repository.getConference(conference.id.value)).delegates.single()

        val added = repository.addDelegateComment(conference.id.value, delegate.id.value, " First note ")
        val comment = added.delegates.single().comments.single()
        assertEquals("First note", comment.body)

        val updated = repository.updateDelegateComment(conference.id.value, comment.id.value, "Edited note")
        assertEquals("Edited note", updated.delegates.single().comments.single().body)

        val deleted = repository.deleteDelegateComment(conference.id.value, comment.id.value)
        assertTrue(deleted.delegates.single().comments.isEmpty())
        Unit
    }

    @Test
    fun `conference display order column exists after migration`() {
        val dbPath = Files.createTempDirectory("munassign-order-column").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)

        transaction(database) {
            ConferenceTable.insert {
                it[id] = "conference"
                it[name] = "Conference"
                it[displayOrder] = 7
                it[createdAtEpochMillis] = 1L
                it[updatedAtEpochMillis] = 1L
                it[lastMymunUpdateAtEpochMillis] = null
            }
            assertEquals(7, ConferenceTable.selectAll().single()[ConferenceTable.displayOrder])
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
                it[CommitteeTable.isHighSchoolCommittee] = true
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
        assertTrue(reloadedCommittee.isHighSchoolCommittee)
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

    @Test
    fun `MyMUN update aborts when confirmed assignments exceed configured slot count`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-slot-mismatch").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments()).success)
        transaction(database) {
            CommitteeTable.update({ CommitteeTable.conferenceId eq conference.id.value }) {
                it[slotsPerPosition] = 1
            }
        }

        val result = repository.applyMymunUpdateSnapshot(
            conferenceId = conference.id.value,
            snapshot = snapshotWithAssignments(
                delegates = listOf(
                    delegate(rowNumber = 2, firstName = "Alex", lastName = "Kim", email = "alex@example.test"),
                    delegate(rowNumber = 3, firstName = "Blake", lastName = "Roy", email = "blake@example.test"),
                ),
                confirmedAssignments = listOf(
                    assignment(rowNumber = 2, positionName = "France", slotIndex = 1, delegateName = "Alex Kim"),
                    assignment(rowNumber = 3, positionName = "Germany", slotIndex = 2, delegateName = "Blake Roy"),
                ),
            ),
            sourceFilename = "slot-mismatch.xlsx",
        )

        assertFalse(result.success)
        assertEquals(1, result.summary.blockingErrorCount)
        assertTrue(result.issues.any { it.code == "slot_count_mismatch" && it.rowNumber == 3 })
    }

    @Test
    fun `MyMUN update aborts unmatched confirmed delegates with structured issue metadata`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-unmatched").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        val result = repository.applyMymunUpdateSnapshot(
            conferenceId = conference.id.value,
            snapshot = snapshotWithAssignments(
                confirmedAssignments = listOf(
                    assignment(rowNumber = 2, positionName = "France", slotIndex = 1, delegateName = "Missing Person"),
                ),
            ),
            sourceFilename = "unmatched.xlsx",
        )

        assertFalse(result.success)
        val issue = result.issues.single { it.code == "unmatched_confirmed_delegate" }
        assertEquals(UpdateIssueSeverity.Error, issue.severity)
        assertEquals("Countries", issue.sheetName)
        assertEquals(2, issue.rowNumber)
        assertEquals("Delegates", issue.columnName)

        transaction(database) {
            val persistedIssue = MymunUpdateIssueTable.selectAll()
                .where { MymunUpdateIssueTable.code eq "unmatched_confirmed_delegate" }
                .single()
            assertEquals("Error", persistedIssue[MymunUpdateIssueTable.severity])
            assertEquals("Countries", persistedIssue[MymunUpdateIssueTable.sheetName])
            assertEquals(2, persistedIssue[MymunUpdateIssueTable.rowNumber])
            assertEquals(4, persistedIssue[MymunUpdateIssueTable.columnNumber])
            assertEquals("Delegates", persistedIssue[MymunUpdateIssueTable.columnName])
        }
    }

    @Test
    fun `MyMUN update warns when assignment is matched by first name only`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-first-name").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        val result = repository.applyMymunUpdateSnapshot(
            conferenceId = conference.id.value,
            snapshot = snapshotWithAssignments(
                confirmedAssignments = listOf(
                    assignment(rowNumber = 2, positionName = "France", slotIndex = 1, delegateName = "Alex"),
                ),
            ),
            sourceFilename = "first-name.xlsx",
        )

        assertTrue(result.success, result.issues.joinToString("; ") { it.message })
        assertEquals(1, result.summary.warningCount)
        assertTrue(result.issues.any { it.code == "first_name_only_assignment" })
    }

    @Test
    fun `MyMUN update preserves pending app assignments when not contradicted`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-pending").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val snapshot = snapshotWithAssignments(confirmedAssignments = emptyList())

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshot).success)
        val loaded = requireNotNull(repository.getConference(conference.id.value))
        val delegate = loaded.delegates.single { it.name.firstName == "Alex" }
        val slot = loaded.openSlots.first()
        transaction(database) {
            SlotTable.update({ SlotTable.id eq slot.id.value }) {
                it[assignedDelegateId] = delegate.id.value
                it[assignmentSource] = AssignmentSource.Application.name
                it[updatedAtEpochMillis] = 2L
            }
        }

        val result = repository.applyMymunUpdateSnapshot(conference.id.value, snapshot, "pending.xlsx")
        val reloaded = requireNotNull(repository.getConference(conference.id.value))

        assertTrue(result.success, result.issues.joinToString("; ") { it.message })
        assertEquals(1, reloaded.pendingSlots.size)
        assertEquals(1, result.summary.pendingAssignmentsPreserved)
        assertEquals(0, result.summary.pendingAssignmentsCleared)
    }

    @Test
    fun `MyMUN update counts only actual changed rows in summary`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-no-op-summary").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val snapshot = snapshotWithAssignments()

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshot, "first.xlsx").success)
        val result = repository.applyMymunUpdateSnapshot(conference.id.value, snapshot, "second.xlsx")

        assertTrue(result.success, result.issues.joinToString("; ") { it.message })
        assertEquals(0, result.summary.delegationsUpdated)
        assertEquals(0, result.summary.committeesUpdated)
        assertEquals(0, result.summary.positionsUpdated)
        assertEquals(0, result.summary.delegatesUpdated)
        assertEquals(0, result.summary.questionsUpdated)
        assertEquals(0, result.summary.assignmentsConfirmed)
    }

    @Test
    fun `MyMUN update counts pending assignments cleared by pruned positions`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-pruned-pending").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val initialSnapshot = snapshotWithAssignments(confirmedAssignments = emptyList())

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, initialSnapshot).success)
        val loaded = requireNotNull(repository.getConference(conference.id.value))
        val delegate = loaded.delegates.single { it.name.firstName == "Alex" }
        val germanySlot = loaded.positions.single { it.name == "Germany" }.slots.single()
        transaction(database) {
            SlotTable.update({ SlotTable.id eq germanySlot.id.value }) {
                it[assignedDelegateId] = delegate.id.value
                it[assignmentSource] = AssignmentSource.Application.name
                it[updatedAtEpochMillis] = 2L
            }
        }

        val result = repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithOnlyFrance(), "remove-germany.xlsx")

        assertTrue(result.success, result.issues.joinToString("; ") { it.message })
        assertEquals(0, result.summary.pendingAssignmentsPreserved)
        assertEquals(1, result.summary.pendingAssignmentsCleared)
        assertEquals(1, result.summary.positionsRemoved)
    }

    @Test
    fun `organizer managed question rules and typed mappings survive repeated updates`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-question-config").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val snapshot = snapshotWithQuestion()

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshot).success)
        val questionId = transaction(database) {
            DelegateQuestionTable.selectAll().single()[DelegateQuestionTable.id]
        }
        transaction(database) {
            DelegateQuestionRuleTable.insert {
                it[id] = "rule-1"
                it[DelegateQuestionRuleTable.questionId] = questionId
                it[ruleTypeId] = "minimum_text_length"
                it[intParameter] = 20
                it[textParameter] = null
            }
            MunExperienceAnswerMappingTable.insert {
                it[id] = "mapping-1"
                it[MunExperienceAnswerMappingTable.questionId] = questionId
                it[rawValue] = "Lots"
                it[normalizedRawValue] = "lots"
                it[mappedValue] = MunExperienceLevel.Advanced.name
            }
        }

        val result = repository.applyMymunUpdateSnapshot(conference.id.value, snapshot, "question-config.xlsx")

        assertTrue(result.success, result.issues.joinToString("; ") { it.message })
        transaction(database) {
            assertEquals(1, DelegateQuestionRuleTable.selectAll().where { DelegateQuestionRuleTable.questionId eq questionId }.count())
            assertEquals(1, MunExperienceAnswerMappingTable.selectAll().where { MunExperienceAnswerMappingTable.questionId eq questionId }.count())
        }
    }

    @Test
    fun `question configuration update saves local handling label rules and mappings`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-question-save").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val snapshot = snapshotWithQuestion()

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshot).success)
        val loaded = requireNotNull(repository.getConference(conference.id.value))
        val question = loaded.questions.single()

        val updated = repository.updateQuestionConfiguration(
            conferenceId = conference.id.value,
            configuration = QuestionConfiguration(
                questionId = question.id,
                questionText = question.questionText,
                role = StandardQuestionRoles.ExperienceLevel,
                handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
                visibleInDelegateDetail = false,
                organizerLabel = " Experience ",
                displayOrder = 2,
                rules = listOf(RequiredAnswer("Missing"), MinimumTextLength(12), NoticeWhenNonBlank("Review")),
                mappings = TypedAnswerMappings(
                    munExperience = listOf(MunExperienceAnswerMapping("Lots", MunExperienceLevel.Advanced)),
                ),
            ),
        )

        val reloadedQuestion = updated.questions.single()
        assertFalse(reloadedQuestion.visibleInDelegateDetail)
        assertEquals("Experience", reloadedQuestion.organizerLabel)
        assertEquals(StandardQuestionRoles.ExperienceLevel, reloadedQuestion.role)
        assertEquals(StandardQuestionHandlingTypes.ExpectedTypedAnswer, reloadedQuestion.handling)
        assertEquals(3, reloadedQuestion.rules.size)
        assertEquals(MunExperienceLevel.Advanced, reloadedQuestion.mapMunExperience(" lots "))
        transaction(database) {
            assertEquals(3, DelegateQuestionRuleTable.selectAll().where { DelegateQuestionRuleTable.questionId eq question.id.value }.count())
            assertEquals(1, MunExperienceAnswerMappingTable.selectAll().where { MunExperienceAnswerMappingTable.questionId eq question.id.value }.count())
        }
    }

    @Test
    fun `question configuration update normalizes blank labels and rejects duplicate mappings`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-question-validation").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithQuestion()).success)
        val question = requireNotNull(repository.getConference(conference.id.value)).questions.single()

        val saved = repository.updateQuestionConfiguration(
            conferenceId = conference.id.value,
            configuration = QuestionConfiguration(
                questionId = question.id,
                questionText = question.questionText,
                role = StandardQuestionRoles.HighSchoolerStatus,
                handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
                visibleInDelegateDetail = true,
                organizerLabel = "   ",
                displayOrder = question.displayOrder,
                rules = emptyList(),
                mappings = TypedAnswerMappings(
                    highSchooler = listOf(HighSchoolerAnswerMapping("Yes", HighSchoolerStatus.Yes)),
                ),
            ),
        )
        assertNull(saved.questions.single().organizerLabel)

        assertFailsWith<IllegalArgumentException> {
            repository.updateQuestionConfiguration(
                conferenceId = conference.id.value,
                configuration = QuestionConfiguration(
                    questionId = question.id,
                    questionText = question.questionText,
                    role = StandardQuestionRoles.LanguageLevel,
                    handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
                    visibleInDelegateDetail = true,
                    organizerLabel = null,
                    displayOrder = question.displayOrder,
                    rules = emptyList(),
                    mappings = TypedAnswerMappings(
                        languageLevel = listOf(
                            LanguageLevelAnswerMapping("B1", LanguageLevel.IndependentUserB1B2),
                            LanguageLevelAnswerMapping(" b1 ", LanguageLevel.IndependentUserB1B2),
                        ),
                    ),
                ),
            )
        }
        Unit
    }

    @Test
    fun `question configuration update touches only selected role mapping table`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-question-role-mappings").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithQuestion()).success)
        val question = requireNotNull(repository.getConference(conference.id.value)).questions.single()
        transaction(database) {
            MunExperienceAnswerMappingTable.insert {
                it[id] = "experience-mapping"
                it[MunExperienceAnswerMappingTable.questionId] = question.id.value
                it[rawValue] = "Lots"
                it[normalizedRawValue] = "lots"
                it[mappedValue] = MunExperienceLevel.Advanced.name
            }
            HighSchoolerAnswerMappingTable.insert {
                it[id] = "school-mapping"
                it[HighSchoolerAnswerMappingTable.questionId] = question.id.value
                it[rawValue] = "Yes"
                it[normalizedRawValue] = "yes"
                it[mappedValue] = HighSchoolerStatus.Yes.name
            }
        }

        repository.updateQuestionConfiguration(
            conferenceId = conference.id.value,
            configuration = QuestionConfiguration(
                questionId = question.id,
                questionText = question.questionText,
                role = StandardQuestionRoles.LanguageLevel,
                handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
                visibleInDelegateDetail = true,
                organizerLabel = null,
                displayOrder = question.displayOrder,
                rules = emptyList(),
                mappings = TypedAnswerMappings(
                    languageLevel = listOf(LanguageLevelAnswerMapping("B1", LanguageLevel.IndependentUserB1B2)),
                ),
            ),
        )

        transaction(database) {
            assertEquals(1, MunExperienceAnswerMappingTable.selectAll().where { MunExperienceAnswerMappingTable.questionId eq question.id.value }.count())
            assertEquals(1, HighSchoolerAnswerMappingTable.selectAll().where { HighSchoolerAnswerMappingTable.questionId eq question.id.value }.count())
            assertEquals(1, LanguageLevelAnswerMappingTable.selectAll().where { LanguageLevelAnswerMappingTable.questionId eq question.id.value }.count())
        }
    }

    @Test
    fun `question role heuristics detect specific role questions without broad experience matches`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-question-heuristics").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(
            repository.applyMymunUpdateSnapshot(
                conference.id.value,
                snapshotWithQuestions(
                    listOf(
                        "Question: Please describe any of your accessibility needs.",
                        "Question: Are you a high-schooler?",
                        "Question: What is your MUN experience?",
                        "Question: Please explain your choice of comittees.",
                    ),
                ),
            ).success,
        )
        val questions = requireNotNull(repository.getConference(conference.id.value)).questions.associateBy { it.questionText }

        assertNull(questions.getValue("Question: Please describe any of your accessibility needs.").role)
        assertEquals(StandardQuestionRoles.HighSchoolerStatus, questions.getValue("Question: Are you a high-schooler?").role)
        assertEquals(StandardQuestionRoles.ExperienceLevel, questions.getValue("Question: What is your MUN experience?").role)
        assertEquals(
            StandardQuestionHandlingTypes.CommitteeChoiceExplanation,
            questions.getValue("Question: Please explain your choice of comittees.").handling,
        )
    }

    @Test
    fun `question reorder persists display order`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-question-order").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithQuestions(listOf("Question: A", "Question: B"))).success)
        val loaded = requireNotNull(repository.getConference(conference.id.value))
        val reorderedIds = loaded.questions.sortedByDescending { it.questionText }.map { it.id.value }
        val reordered = repository.reorderQuestionConfigurations(conference.id.value, reorderedIds)

        assertEquals(listOf("Question: B", "Question: A"), reordered.questions.sortedBy { it.displayOrder }.map { it.questionText })
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

    private fun snapshotWithAssignments(
        delegates: List<MymunDelegateRow> = listOf(delegate()),
        confirmedAssignments: List<MymunConfirmedAssignmentCandidate> = listOf(
            assignment(rowNumber = 2, positionName = "France", slotIndex = 1),
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
                    delegatesRaw = null,
                ),
                MymunCountryPositionRow(
                    rowNumber = 3,
                    committeeName = "GA",
                    memberName = "Germany",
                    delegationName = null,
                    delegatesRaw = null,
                ),
            ),
            delegations = emptyList(),
            questionColumns = emptyList(),
            questionAnswers = emptyList(),
            preferenceCandidates = emptyList(),
            confirmedAssignmentCandidates = confirmedAssignments,
            issues = emptyList(),
        )

    private fun snapshotWithQuestion(): MymunWorkbookSnapshot =
        snapshotWithQuestions(listOf("Question: MUN experience"))

    private fun snapshotWithQuestions(questionTexts: List<String>): MymunWorkbookSnapshot {
        val questionColumns = questionTexts.mapIndexed { index, questionText ->
            MymunQuestionColumn(
            sheetName = "Delegates",
                columnNumber = 5 + index,
                displayOrder = 4 + index,
                questionText = questionText,
            )
        }
        return MymunWorkbookSnapshot(
            workbookName = "question.xlsx",
            delegates = listOf(delegate()),
            countryPositions = listOf(
                MymunCountryPositionRow(
                    rowNumber = 2,
                    committeeName = "GA",
                    memberName = "France",
                    delegationName = null,
                    delegatesRaw = null,
                ),
            ),
            delegations = emptyList(),
            questionColumns = questionColumns,
            questionAnswers = questionColumns.map { questionColumn ->
                MymunQuestionAnswerCell(
                    delegateRowNumber = 2,
                    questionColumn = questionColumn,
                    answer = "Lots",
                    location = CellLocation("Delegates", 2, 5, questionColumn.questionText),
                )
            },
            preferenceCandidates = emptyList(),
            confirmedAssignmentCandidates = emptyList(),
            issues = emptyList(),
        )
    }

    private fun snapshotWithOnlyFrance(): MymunWorkbookSnapshot =
        MymunWorkbookSnapshot(
            workbookName = "test.xlsx",
            delegates = listOf(delegate()),
            countryPositions = listOf(
                MymunCountryPositionRow(
                    rowNumber = 2,
                    committeeName = "GA",
                    memberName = "France",
                    delegationName = null,
                    delegatesRaw = null,
                ),
            ),
            delegations = emptyList(),
            questionColumns = emptyList(),
            questionAnswers = emptyList(),
            preferenceCandidates = emptyList(),
            confirmedAssignmentCandidates = emptyList(),
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
