package org.munika.assignment.data.db.repository

import java.nio.file.Files
import java.sql.DriverManager
import java.time.LocalDate
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
import org.munika.assignment.data.db.tables.AnswerIssueResolutionTable
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
import org.munika.assignment.data.db.tables.MymunUpdateRunTable
import org.munika.assignment.data.db.tables.PositionTable
import org.munika.assignment.data.db.tables.QuestionHandlingTypeTable
import org.munika.assignment.data.db.tables.QuestionRoleTable
import org.munika.assignment.data.db.tables.SlotTable
import org.munika.assignment.data.excel.CellLocation
import org.munika.assignment.data.excel.MymunConfirmedAssignmentCandidate
import org.munika.assignment.data.excel.MymunCountryPositionRow
import org.munika.assignment.data.excel.MymunDelegateRow
import org.munika.assignment.data.excel.MymunDelegationRow
import org.munika.assignment.data.excel.MymunMissingPreference
import org.munika.assignment.data.excel.MymunPreferenceCandidate
import org.munika.assignment.data.excel.MymunQuestionAnswerCell
import org.munika.assignment.data.excel.MymunQuestionColumn
import org.munika.assignment.data.excel.MymunWorkbookSnapshot
import org.munika.assignment.data.excel.MymunWorkbookUpdater
import org.munika.assignment.domain.model.AssignmentSource
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.HighSchoolerAnswerMapping
import org.munika.assignment.domain.model.HighSchoolerStatus
import org.munika.assignment.domain.model.LanguageLevel
import org.munika.assignment.domain.model.LanguageLevelAnswerMapping
import org.munika.assignment.domain.model.MinimumTextLength
import org.munika.assignment.domain.model.MunExperienceAnswerMapping
import org.munika.assignment.domain.model.MunExperienceLevel
import org.munika.assignment.domain.model.NoticeWhenNonBlank
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.PaymentTier
import org.munika.assignment.domain.model.PreferenceSource
import org.munika.assignment.domain.model.QuestionConfiguration
import org.munika.assignment.domain.model.QuestionId
import org.munika.assignment.domain.model.ReservationLinkSource
import org.munika.assignment.domain.model.RequiredAnswer
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.TypedAnswerMappings
import org.munika.assignment.domain.repository.UpdateIssueSeverity
import org.munika.assignment.domain.repository.PreferenceCompletionSelection
import org.munika.assignment.domain.repository.PreferenceCorrectionSelection
import org.munika.assignment.domain.repository.PreferenceValueOrigin

class ConferenceRepositoryImplTest {
    @Test
    fun `preference correction reorders imported values and acknowledges an intentional empty rank`() = runBlocking {
        val database = DatabaseFactory.connect(Files.createTempDirectory("munassign-preference-order").resolve("data.db"))
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        val firstUpdate = repository.applyMymunUpdateSnapshot(conference.id.value, gappedPreferenceSnapshot())
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single()
        val france = loaded.positions.single { it.name == "France" }
        val germany = loaded.positions.single { it.name == "Germany" }
        assertEquals(listOf(2), firstUpdate.missingPreferences.map { it.rank })

        assertFailsWith<IllegalArgumentException> {
            repository.savePreferenceCorrections(
                conference.id,
                listOf(
                    PreferenceCorrectionSelection(delegate.id, 1, france.id, PreferenceValueOrigin.Mymun),
                    PreferenceCorrectionSelection(delegate.id, 2, positionId = null, origin = null),
                    PreferenceCorrectionSelection(delegate.id, 3, positionId = null, origin = null, emptyAcknowledged = true),
                ),
            )
        }
        assertEquals(
            listOf(1 to "France", 3 to "Germany"),
            requireNotNull(repository.getConference(conference.id)).delegates.single().preferences
                .sortedBy { it.rank }.map { it.rank to it.position.name },
        )

        val corrected = repository.savePreferenceCorrections(
            conference.id,
            listOf(
                PreferenceCorrectionSelection(delegate.id, 1, france.id, PreferenceValueOrigin.Mymun),
                PreferenceCorrectionSelection(delegate.id, 2, germany.id, PreferenceValueOrigin.Mymun),
                PreferenceCorrectionSelection(delegate.id, 3, positionId = null, origin = null, emptyAcknowledged = true),
            ),
        )
        assertEquals(
            listOf(1 to "France", 2 to "Germany"),
            corrected.delegates.single().preferences.sortedBy { it.rank }.map { it.rank to it.position.name },
        )
        assertEquals(
            PreferenceSource.OrganizerCorrection,
            corrected.delegates.single().preferences.single { it.rank == 2 }.source,
        )

        val repeated = repository.applyMymunUpdateSnapshot(conference.id.value, gappedPreferenceSnapshot())
        assertTrue(repeated.missingPreferences.isEmpty())
        assertEquals(
            listOf(1 to "France", 2 to "Germany"),
            requireNotNull(repository.getConference(conference.id)).delegates.single().preferences
                .sortedBy { it.rank }.map { it.rank to it.position.name },
        )

        val changed = repository.applyMymunUpdateSnapshot(
            conference.id.value,
            gappedPreferenceSnapshot(germanyRank = 2, missingRank = 3),
        )
        assertEquals(listOf(3), changed.missingPreferences.map { it.rank })
        val changedPreferences = requireNotNull(repository.getConference(conference.id)).delegates.single().preferences
        assertEquals(PreferenceSource.Mymun, changedPreferences.single { it.rank == 2 }.source)
    }

    @Test
    fun `reset preference corrections restores raw MyMUN ranks and makes blanks unresolved again`() = runBlocking {
        val database = DatabaseFactory.connect(Files.createTempDirectory("munassign-preference-reset").resolve("data.db"))
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        repository.applyMymunUpdateSnapshot(conference.id.value, gappedPreferenceSnapshot())
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single()
        val france = loaded.positions.single { it.name == "France" }
        val germany = loaded.positions.single { it.name == "Germany" }

        repository.savePreferenceCorrections(
            conference.id,
            listOf(
                PreferenceCorrectionSelection(delegate.id, 1, france.id, PreferenceValueOrigin.Mymun),
                PreferenceCorrectionSelection(delegate.id, 2, germany.id, PreferenceValueOrigin.Mymun),
                PreferenceCorrectionSelection(delegate.id, 3, positionId = null, origin = null, emptyAcknowledged = true),
            ),
        )

        val reset = repository.resetPreferenceCorrections(conference.id)
        assertEquals(
            listOf(1 to "France", 3 to "Germany"),
            reset.delegates.single().preferences.sortedBy { it.rank }.map { it.rank to it.position.name },
        )
        val nextUpdate = repository.applyMymunUpdateSnapshot(conference.id.value, gappedPreferenceSnapshot())
        assertEquals(listOf(2), nextUpdate.missingPreferences.map { it.rank })
    }

    @Test
    fun `organizer completion supplies repeated empty preference and MyMUN later wins`() = runBlocking {
        val database = DatabaseFactory.connect(Files.createTempDirectory("munassign-preference-completion").resolve("data.db"))
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        val firstUpdate = repository.applyMymunUpdateSnapshot(conference.id.value, missingPreferenceSnapshot())
        val firstLoaded = requireNotNull(repository.getConference(conference.id))
        val delegate = firstLoaded.delegates.single()
        val germany = firstLoaded.positions.single { it.name == "Germany" }
        assertEquals(listOf(delegate.id.value to 1), firstUpdate.missingPreferences.map { it.delegateId.value to it.rank })

        val completed = repository.savePreferenceCompletions(
            conference.id,
            listOf(PreferenceCompletionSelection(delegate.id, rank = 1, germany.id)),
        )
        assertEquals(PreferenceSource.OrganizerCompletion, completed.delegates.single().preferences.single().source)

        val repeated = repository.applyMymunUpdateSnapshot(conference.id.value, missingPreferenceSnapshot())
        assertTrue(repeated.missingPreferences.isEmpty())
        assertEquals("Germany", requireNotNull(repository.getConference(conference.id)).delegates.single().preferences.single().position.name)

        val mymunUpdate = repository.applyMymunUpdateSnapshot(
            conference.id.value,
            missingPreferenceSnapshot(
                preference = MymunPreferenceCandidate(
                    delegateRowNumber = 2,
                    rank = 1,
                    rawPreferenceText = "GA (France)",
                    parsedCommitteeName = "GA",
                    parsedPositionName = "France",
                    resolvedCountryRowNumber = 2,
                    location = CellLocation("Delegates", 2, 5, "Preference 1"),
                ),
            ),
        )

        assertTrue(mymunUpdate.missingPreferences.isEmpty())
        val finalPreference = requireNotNull(repository.getConference(conference.id)).delegates.single().preferences.single()
        assertEquals("France", finalPreference.position.name)
        assertEquals(PreferenceSource.Mymun, finalPreference.source)
    }

    @Test
    fun `preference completion rejects duplicate positions and can be cleared`() = runBlocking {
        val database = DatabaseFactory.connect(Files.createTempDirectory("munassign-preference-validation").resolve("data.db"))
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        repository.applyMymunUpdateSnapshot(conference.id.value, missingPreferenceSnapshot(ranks = listOf(1, 2)))
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single()
        val germany = loaded.positions.single { it.name == "Germany" }

        assertFailsWith<IllegalArgumentException> {
            repository.savePreferenceCompletions(
                conference.id,
                listOf(
                    PreferenceCompletionSelection(delegate.id, 1, germany.id),
                    PreferenceCompletionSelection(delegate.id, 2, germany.id),
                ),
            )
        }
        assertTrue(requireNotNull(repository.getConference(conference.id)).delegates.single().preferences.isEmpty())

        val otherConference = repository.createConference("Other")
        repository.applyMymunUpdateSnapshot(otherConference.id.value, missingPreferenceSnapshot())
        val otherPosition = requireNotNull(repository.getConference(otherConference.id)).positions.first()
        assertFailsWith<IllegalArgumentException> {
            repository.savePreferenceCompletions(
                conference.id,
                listOf(PreferenceCompletionSelection(delegate.id, 1, otherPosition.id)),
            )
        }
        assertTrue(requireNotNull(repository.getConference(conference.id)).delegates.single().preferences.isEmpty())

        repository.savePreferenceCompletions(
            conference.id,
            listOf(PreferenceCompletionSelection(delegate.id, 1, germany.id)),
        )
        val cleared = repository.clearPreferenceCompletion(conference.id, delegate.id, rank = 1)
        assertTrue(cleared.delegates.single().preferences.isEmpty())
    }

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

        val loaded = repository.getConference(conference.id)
        assertNotNull(loaded)
        assertTrue(loaded.committees.isNotEmpty())
        assertTrue(loaded.delegates.isNotEmpty())
        assertTrue(loaded.delegates.any { it.preferences.isNotEmpty() })

        repository.deleteConference(conference.id)

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

        repository.reorderConferences(listOf(third.id, first.id, second.id))

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
            repository.reorderConferences(listOf(first.id))
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            repository.reorderConferences(listOf(first.id, first.id))
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            repository.reorderConferences(listOf(first.id, ConferenceId("missing")))
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
        val loaded = requireNotNull(repository.getConference(conference.id))
        val committee = loaded.committees.single()

        val updated = repository.setCommitteeSlotsPerPosition(conference.id, committee.id, 3)

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
        val committee = requireNotNull(repository.getConference(conference.id)).committees.single()

        val updated = repository.setCommitteeHighSchoolStatus(conference.id, committee.id, true)

        assertTrue(updated.committees.single().isHighSchoolCommittee)
        transaction(database) {
            assertTrue(CommitteeTable.selectAll().single()[CommitteeTable.isHighSchoolCommittee])
        }
    }

    @Test
    fun `committee mode and position mapped committee are persisted`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-ipc-persist").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithTwoCommittees()).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val ipc = loaded.committees.single { it.mymunName == "IPC" }
        val target = loaded.committees.single { it.mymunName == "UNSC" }

        repository.setCommitteeMode(conference.id, target.id, CommitteeMode.HighSchool)
        repository.setCommitteeMode(conference.id, ipc.id, CommitteeMode.Mapped)
        val mapped = repository.setPositionMappedCommittee(
            conferenceId = conference.id,
            positionId = ipc.positions.single().id,
            mappedCommitteeId = target.id,
        )

        val mappedPosition = mapped.committees.single { it.mymunName == "IPC" }.positions.single()
        assertEquals(CommitteeMode.Mapped, mappedPosition.committee.mode)
        assertEquals(target.id, mappedPosition.mappedCommittee?.id)
        assertEquals(CommitteeMode.HighSchool, mappedPosition.effectiveCommitteeMode)

        val reloaded = requireNotNull(repository.getConference(conference.id))
        assertEquals(CommitteeMode.Mapped, reloaded.committees.single { it.mymunName == "IPC" }.mode)
        assertEquals(
            reloaded.committees.single { it.mymunName == "UNSC" }.id,
            reloaded.committees.single { it.mymunName == "IPC" }.positions.single().mappedCommittee?.id,
        )
    }

    @Test
    fun `committee and delegate payment tiers default and persist`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-payment-tiers").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments()).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val committee = loaded.committees.single()
        val delegate = loaded.delegates.single()

        assertEquals(PaymentTier.Full, committee.paymentTier)
        assertNull(delegate.paymentTier)

        repository.setCommitteePaymentTier(conference.id, committee.id, PaymentTier.Reduced)
        val updated = repository.setDelegatePaymentTier(conference.id, delegate.id, PaymentTier.Full)

        assertEquals(PaymentTier.Reduced, updated.committees.single().paymentTier)
        assertEquals(PaymentTier.Full, updated.delegates.single().paymentTier)
        val reloaded = requireNotNull(repository.getConference(conference.id))
        assertEquals(PaymentTier.Reduced, reloaded.committees.single().paymentTier)
        assertEquals(PaymentTier.Full, reloaded.delegates.single().paymentTier)
    }

    @Test
    fun `payment tier migration defaults existing committees and leaves delegates unclassified`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-payment-tier-migration").resolve("data.db")
        val repository = ConferenceRepositoryImpl(DatabaseFactory.connect(dbPath))
        val conference = repository.createConference("TestConf")
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments()).success)

        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("ALTER TABLE committees DROP COLUMN payment_tier")
                statement.executeUpdate("ALTER TABLE delegates DROP COLUMN payment_tier")
                statement.executeUpdate("DELETE FROM schema_migrations WHERE version = 13")
            }
        }

        val migrated = requireNotNull(ConferenceRepositoryImpl(DatabaseFactory.connect(dbPath)).getConference(conference.id))
        assertEquals(PaymentTier.Full, migrated.committees.single().paymentTier)
        assertNull(migrated.delegates.single().paymentTier)
    }

    @Test
    fun `MyMUN update preserves a paid delegate tier and clears it when unpaid`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-payment-tier-update").resolve("data.db")
        val repository = ConferenceRepositoryImpl(DatabaseFactory.connect(dbPath))
        val conference = repository.createConference("TestConf")
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithDelegates(listOf(delegate()))).success)
        val paidDelegate = requireNotNull(repository.getConference(conference.id)).delegates.single()
        repository.setDelegatePaymentTier(conference.id, paidDelegate.id, PaymentTier.Reduced)

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithDelegates(listOf(delegate()))).success)
        assertEquals(PaymentTier.Reduced, requireNotNull(repository.getConference(conference.id)).delegates.single().paymentTier)

        assertTrue(
            repository.applyMymunUpdateSnapshot(
                conference.id.value,
                snapshotWithDelegates(listOf(delegate(feePaid = false))),
            ).success,
        )
        assertNull(requireNotNull(repository.getConference(conference.id)).delegates.single().paymentTier)
    }

    @Test
    fun `new committees use name heuristics for initial mode`() = runBlocking {
        listOf(
            "High School Committee" to CommitteeMode.HighSchool,
            "highschool debate" to CommitteeMode.HighSchool,
            "IPC" to CommitteeMode.Mapped,
            "ipc" to CommitteeMode.University,
            "High School IPC" to CommitteeMode.HighSchool,
        ).forEach { (committeeName, expectedMode) ->
            val dbPath = Files.createTempDirectory("munassign-committee-heuristics").resolve("data.db")
            val database = DatabaseFactory.connect(dbPath)
            val repository = ConferenceRepositoryImpl(database)
            val conference = repository.createConference("TestConf")

            val result = repository.applyMymunUpdateSnapshot(
                conference.id.value,
                snapshotWithCommitteeNames(committeeName),
            )

            assertTrue(result.success, result.issues.joinToString("; ") { it.message })
            val loaded = requireNotNull(repository.getConference(conference.id))
            val committee = loaded.committees.single()
            assertEquals(expectedMode, committee.mode)
            assertEquals(expectedMode == CommitteeMode.HighSchool, committee.isHighSchoolCommittee)

            transaction(database) {
                val legacyFlag = CommitteeTable.selectAll().single()[CommitteeTable.isHighSchoolCommittee]
                assertEquals(expectedMode == CommitteeMode.HighSchool, legacyFlag)
            }
        }
    }

    @Test
    fun `committee name heuristics do not reclassify retained committees`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-committee-heuristics-preserve").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        val firstResult = repository.applyMymunUpdateSnapshot(
            conference.id.value,
            snapshotWithCommitteeNames("High School Committee"),
        )
        assertTrue(firstResult.success, firstResult.issues.joinToString("; ") { it.message })
        val committee = requireNotNull(repository.getConference(conference.id)).committees.single()

        repository.setCommitteeMode(conference.id, committee.id, CommitteeMode.University)

        val repeatedResult = repository.applyMymunUpdateSnapshot(
            conference.id.value,
            snapshotWithCommitteeNames("High School Committee"),
        )

        assertTrue(repeatedResult.success, repeatedResult.issues.joinToString("; ") { it.message })
        val reloaded = requireNotNull(repository.getConference(conference.id)).committees.single()
        assertEquals(CommitteeMode.University, reloaded.mode)
        assertFalse(reloaded.isHighSchoolCommittee)
    }

    @Test
    fun `position mapping rejects mapped target and normal owning committee`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-ipc-reject").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithTwoCommittees()).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val ipc = loaded.committees.single { it.mymunName == "IPC" }
        val target = loaded.committees.single { it.mymunName == "UNSC" }

        assertFailsWith<IllegalArgumentException> {
            repository.setPositionMappedCommittee(conference.id, target.positions.single().id, ipc.id)
        }

        repository.setCommitteeMode(conference.id, ipc.id, CommitteeMode.Mapped)
        repository.setCommitteeMode(conference.id, target.id, CommitteeMode.Mapped)

        assertFailsWith<IllegalArgumentException> {
            repository.setPositionMappedCommittee(conference.id, ipc.positions.single().id, target.id)
        }
        Unit
    }

    @Test
    fun `switching mapped committee back to normal rejects existing position mappings`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-ipc-clear").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithTwoCommittees()).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val ipc = loaded.committees.single { it.mymunName == "IPC" }
        val target = loaded.committees.single { it.mymunName == "UNSC" }
        repository.setCommitteeMode(conference.id, target.id, CommitteeMode.HighSchool)
        repository.setCommitteeMode(conference.id, ipc.id, CommitteeMode.Mapped)
        repository.setPositionMappedCommittee(conference.id, ipc.positions.single().id, target.id)

        val error = assertFailsWith<IllegalArgumentException> {
            repository.setCommitteeMode(conference.id, ipc.id, CommitteeMode.University)
        }

        assertTrue(error.message.orEmpty().contains("correspondence"))
        val reloaded = requireNotNull(repository.getConference(conference.id))
        assertEquals(CommitteeMode.Mapped, reloaded.committees.single { it.mymunName == "IPC" }.mode)
        assertEquals(target.id, reloaded.committees.single { it.mymunName == "IPC" }.positions.single().mappedCommittee?.id)
    }

    @Test
    fun `position mapping allows a target already used by a sibling position`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-ipc-duplicate-target").resolve("data.db")
        val repository = ConferenceRepositoryImpl(DatabaseFactory.connect(dbPath))
        val conference = repository.createConference("TestConf")
        assertTrue(
            repository.applyMymunUpdateSnapshot(
                conference.id.value,
                snapshotWithTwoCommittees(ipcPositionNames = listOf("Reporter One", "Reporter Two")),
            ).success,
        )
        val loaded = requireNotNull(repository.getConference(conference.id))
        val ipc = loaded.committees.single { it.mymunName == "IPC" }
        val target = loaded.committees.single { it.mymunName == "UNSC" }
        repository.setCommitteeMode(conference.id, ipc.id, CommitteeMode.Mapped)
        repository.setPositionMappedCommittee(conference.id, ipc.positions.first().id, target.id)
        repository.setPositionMappedCommittee(conference.id, ipc.positions.last().id, target.id)

        val reloaded = requireNotNull(repository.getConference(conference.id))
        val reloadedIpc = reloaded.committees.single { it.mymunName == "IPC" }
        val reloadedTarget = reloaded.committees.single { it.mymunName == "UNSC" }

        assertEquals(
            listOf(reloadedTarget.id, reloadedTarget.id),
            reloadedIpc.positions.sortedBy { it.name }.map { it.mappedCommittee?.id },
        )
    }

    @Test
    fun `reservation can be created and loaded`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-reservation-create").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        val updated = repository.createReservation(
            conferenceId = conference.id,
            name = "Delegation hold",
            highSchoolCount = 2,
            universityCount = 1,
            delegationId = null,
        )

        val reservation = updated.reservations.single()
        assertEquals("Delegation hold", reservation.name)
        assertEquals(2, reservation.highSchoolCount)
        assertEquals(1, reservation.universityCount)
        assertNull(reservation.delegation)

        val reloaded = requireNotNull(repository.getConference(conference.id))
        assertEquals(reservation.id, reloaded.reservations.single().id)
        assertEquals("Delegation hold", reloaded.reservations.single().name)
    }

    @Test
    fun `manual reservation link survives reload`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-reservation-manual-reload").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, reservationSnapshot()).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single { it.name.firstName == "Alex" }
        val reservation = repository.createReservation(
            conferenceId = conference.id,
            name = "Manual hold",
            highSchoolCount = 1,
            universityCount = 1,
            delegationId = null,
        ).reservations.single()

        repository.assignDelegateReservation(conference.id, delegate.id, reservation.id)

        val reloaded = requireNotNull(repository.getConference(conference.id))
        val link = requireNotNull(reloaded.delegates.single { it.id == delegate.id }.reservationLink)
        assertEquals(reservation.id, link.reservation.id)
        assertEquals(ReservationLinkSource.Manual, link.source)
        assertEquals(link.id, reloaded.reservations.single().delegateLinks.single().id)
    }

    @Test
    fun `removing manual reservation link from standalone reservation leaves delegate unlinked`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-reservation-remove-manual").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, reservationSnapshot()).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single { it.name.firstName == "Alex" }
        val reservation = repository.createReservation(
            conferenceId = conference.id,
            name = "Manual hold",
            highSchoolCount = 1,
            universityCount = 0,
            delegationId = null,
        ).reservations.single()
        repository.assignDelegateReservation(conference.id, delegate.id, reservation.id)

        val updated = repository.removeDelegateReservation(conference.id, delegate.id)

        assertNull(updated.delegates.single { it.id == delegate.id }.reservationLink)
        assertTrue(updated.reservations.single().delegateLinks.isEmpty())
    }

    @Test
    fun `removing automatic reservation link is rejected and leaves link intact`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-reservation-remove-auto-reject").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, reservationSnapshot()).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single { it.name.firstName == "Alex" }
        val delegation = loaded.delegations.single()
        val reservation = repository.createReservation(
            conferenceId = conference.id,
            name = "Delegation hold",
            highSchoolCount = 1,
            universityCount = 0,
            delegationId = delegation.id,
        ).reservations.single()

        val error = assertFailsWith<IllegalArgumentException> {
            repository.removeDelegateReservation(conference.id, delegate.id)
        }

        assertTrue(error.message.orEmpty().contains("Automatic reservation links", ignoreCase = true))
        val reloaded = requireNotNull(repository.getConference(conference.id))
        val link = requireNotNull(reloaded.delegates.single { it.id == delegate.id }.reservationLink)
        assertEquals(reservation.id, link.reservation.id)
        assertEquals(ReservationLinkSource.DelegationAuto, link.source)
    }

    @Test
    fun `manual assignment replaces automatic reservation link`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-reservation-auto-to-manual").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, reservationSnapshot()).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single { it.name.firstName == "Alex" }
        val delegation = loaded.delegations.single()
        repository.createReservation(
            conferenceId = conference.id,
            name = "Delegation hold",
            highSchoolCount = 1,
            universityCount = 0,
            delegationId = delegation.id,
        )
        val manualReservation = repository.createReservation(
            conferenceId = conference.id,
            name = "Manual hold",
            highSchoolCount = 1,
            universityCount = 0,
            delegationId = null,
        ).reservations.single { it.name == "Manual hold" }

        val updated = repository.assignDelegateReservation(conference.id, delegate.id, manualReservation.id)

        val link = requireNotNull(updated.delegates.single { it.id == delegate.id }.reservationLink)
        assertEquals(manualReservation.id, link.reservation.id)
        assertEquals(ReservationLinkSource.Manual, link.source)
        assertTrue(updated.reservations.single { it.name == "Delegation hold" }.delegateLinks.isEmpty())
    }

    @Test
    fun `delegation linked reservation auto-links active accepted delegates regardless of payment by submission date`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-reservation-auto").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val delegates = listOf(
            reservationDelegate(rowNumber = 2, firstName = "Late", email = "late@example.test", submittedAt = LocalDate.parse("2026-01-05")),
            reservationDelegate(rowNumber = 3, firstName = "Early", email = "early@example.test", submittedAt = LocalDate.parse("2026-01-02")),
            reservationDelegate(rowNumber = 4, firstName = "Unpaid", email = "unpaid@example.test", feePaid = false, submittedAt = LocalDate.parse("2026-01-01")),
            reservationDelegate(rowNumber = 5, firstName = "Pending", email = "pending@example.test", applicationStatus = "Pending", submittedAt = LocalDate.parse("2026-01-01")),
        )
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, reservationSnapshot(delegates = delegates)).success)
        val delegation = requireNotNull(repository.getConference(conference.id)).delegations.single()

        val updated = repository.createReservation(
            conferenceId = conference.id,
            name = "School allocation",
            highSchoolCount = 2,
            universityCount = 0,
            delegationId = delegation.id,
        )

        val linkedNames = updated.reservations.single().delegateLinks.map { it.delegate.name.firstName }
        assertEquals(listOf("Unpaid", "Early"), linkedNames)
        assertTrue(updated.reservations.single().delegateLinks.all { it.source == ReservationLinkSource.DelegationAuto })
    }

    @Test
    fun `manual linked delegate is skipped by auto fill`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-reservation-manual-skips-auto").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val delegates = listOf(
            reservationDelegate(rowNumber = 2, firstName = "Early", email = "early@example.test", submittedAt = LocalDate.parse("2026-01-01")),
            reservationDelegate(rowNumber = 3, firstName = "Late", email = "late@example.test", submittedAt = LocalDate.parse("2026-01-02")),
        )
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, reservationSnapshot(delegates = delegates)).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegation = loaded.delegations.single()
        val manualDelegate = loaded.delegates.single { it.name.firstName == "Early" }
        val manualReservation = repository.createReservation(
            conferenceId = conference.id,
            name = "Manual hold",
            highSchoolCount = 1,
            universityCount = 0,
            delegationId = null,
        ).reservations.single()
        repository.assignDelegateReservation(conference.id, manualDelegate.id, manualReservation.id)

        val updated = repository.createReservation(
            conferenceId = conference.id,
            name = "Delegation hold",
            highSchoolCount = 1,
            universityCount = 0,
            delegationId = delegation.id,
        )

        val manualLink = requireNotNull(updated.delegates.single { it.id == manualDelegate.id }.reservationLink)
        assertEquals(manualReservation.id, manualLink.reservation.id)
        val autoReservation = updated.reservations.single { it.name == "Delegation hold" }
        assertEquals(listOf("Late"), autoReservation.delegateLinks.map { it.delegate.name.firstName })
    }

    @Test
    fun `deactivating delegate removes reservation link`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-reservation-deactivate").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, reservationSnapshot()).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single { it.name.firstName == "Alex" }
        val reservation = repository.createReservation(
            conferenceId = conference.id,
            name = "Manual hold",
            highSchoolCount = 1,
            universityCount = 0,
            delegationId = null,
        ).reservations.single()
        repository.assignDelegateReservation(conference.id, delegate.id, reservation.id)

        val deactivated = repository.deactivateDelegate(conference.id, delegate.id)

        assertNull(deactivated.delegates.single { it.id == delegate.id }.reservationLink)
        assertTrue(deactivated.reservations.single().delegateLinks.isEmpty())
    }

    @Test
    fun `reducing reservation below manual usage is rejected`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-reservation-reduce-manual-block").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, reservationSnapshot()).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single { it.name.firstName == "Alex" }
        val reservation = repository.createReservation(
            conferenceId = conference.id,
            name = "Manual hold",
            highSchoolCount = 1,
            universityCount = 0,
            delegationId = null,
        ).reservations.single()
        repository.assignDelegateReservation(conference.id, delegate.id, reservation.id)

        val error = assertFailsWith<IllegalArgumentException> {
            repository.updateReservation(
                conferenceId = conference.id,
                reservationId = reservation.id,
                name = "Manual hold",
                highSchoolCount = 0,
                universityCount = 1,
                delegationId = null,
            )
        }

        assertTrue(error.message.orEmpty().contains("manual", ignoreCase = true))
        assertTrue(error.message.orEmpty().contains("high-school", ignoreCase = true))
    }

    @Test
    fun `valid reduction trims auto links`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-reservation-reduce-auto").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val delegates = listOf(
            reservationDelegate(rowNumber = 2, firstName = "First", email = "first@example.test", submittedAt = LocalDate.parse("2026-01-01")),
            reservationDelegate(rowNumber = 3, firstName = "Second", email = "second@example.test", submittedAt = LocalDate.parse("2026-01-02")),
        )
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, reservationSnapshot(delegates = delegates)).success)
        val delegation = requireNotNull(repository.getConference(conference.id)).delegations.single()
        val reservation = repository.createReservation(
            conferenceId = conference.id,
            name = "Delegation hold",
            highSchoolCount = 2,
            universityCount = 0,
            delegationId = delegation.id,
        ).reservations.single()

        val reduced = repository.updateReservation(
            conferenceId = conference.id,
            reservationId = reservation.id,
            name = "Delegation hold",
            highSchoolCount = 1,
            universityCount = 0,
            delegationId = delegation.id,
        )

        assertEquals(listOf("First"), reduced.reservations.single().delegateLinks.map { it.delegate.name.firstName })
    }

    @Test
    fun `MyMUN update preserves reservation and rebuilds auto links`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-reservation-update-reconcile").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, reservationSnapshot()).success)
        val delegation = requireNotNull(repository.getConference(conference.id)).delegations.single()
        val reservation = repository.createReservation(
            conferenceId = conference.id,
            name = "Delegation hold",
            highSchoolCount = 1,
            universityCount = 0,
            delegationId = delegation.id,
        ).reservations.single()

        val replacementDelegate = reservationDelegate(
            rowNumber = 2,
            firstName = "Blake",
            email = "blake@example.test",
            submittedAt = LocalDate.parse("2026-01-01"),
        )
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, reservationSnapshot(delegates = listOf(replacementDelegate))).success)

        val updated = requireNotNull(repository.getConference(conference.id))
        val updatedReservation = updated.reservations.single { it.id == reservation.id }
        assertEquals("Delegation hold", updatedReservation.name)
        assertEquals(listOf("Blake"), updatedReservation.delegateLinks.map { it.delegate.name.firstName })
    }

    @Test
    fun `disappeared linked delegation clears reservation delegation link but keeps reservation`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-reservation-missing-delegation").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, reservationSnapshot()).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegation = loaded.delegations.single()
        val reservation = repository.createReservation(
            conferenceId = conference.id,
            name = "Delegation hold",
            highSchoolCount = 1,
            universityCount = 0,
            delegationId = delegation.id,
        ).reservations.single()

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, reservationSnapshot(delegations = emptyList())).success)

        val updated = requireNotNull(repository.getConference(conference.id)).reservations.single { it.id == reservation.id }
        assertNull(updated.delegation)
        assertTrue(updated.delegateLinks.isEmpty())
    }

    @Test
    fun `decreasing committee slot count removes only empty trailing slots`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-slot-decrease").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(confirmedAssignments = emptyList())).success)
        val committee = requireNotNull(repository.getConference(conference.id)).committees.single()
        repository.setCommitteeSlotsPerPosition(conference.id, committee.id, 3)

        val updated = repository.setCommitteeSlotsPerPosition(conference.id, committee.id, 1)

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
        val loaded = requireNotNull(repository.getConference(conference.id))
        val committee = loaded.committees.single()
        val expanded = repository.setCommitteeSlotsPerPosition(conference.id, committee.id, 2)
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
            repository.setCommitteeSlotsPerPosition(conference.id, committee.id, 1)
        }

        val reloaded = requireNotNull(repository.getConference(conference.id)).committees.single()
        assertEquals(2, reloaded.slotsPerPosition)
        assertEquals(1, requireNotNull(repository.getConference(conference.id)).pendingSlots.size)
    }

    @Test
    fun `decreasing committee slot count is blocked when removed slot is confirmed`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-slot-confirmed-block").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(confirmedAssignments = emptyList())).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val committee = loaded.committees.single()
        val expanded = repository.setCommitteeSlotsPerPosition(conference.id, committee.id, 2)
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
            repository.setCommitteeSlotsPerPosition(conference.id, committee.id, 1)
        }

        val reloaded = requireNotNull(repository.getConference(conference.id)).committees.single()
        assertEquals(2, reloaded.slotsPerPosition)
        assertEquals(1, requireNotNull(repository.getConference(conference.id)).assignedSlots.size)
    }

    @Test
    fun `manual assignment persists as pending`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-manual-assign").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(confirmedAssignments = emptyList())).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single()
        val slot = loaded.openSlots.first()

        val updated = repository.assignDelegateToSlot(conference.id, slot.id, delegate.id, moveExistingPending = false)

        val pendingSlot = updated.pendingSlots.single()
        assertEquals(slot.id, pendingSlot.id)
        assertEquals(delegate.id, pendingSlot.delegate?.id)
        assertEquals(AssignmentSource.Application, pendingSlot.source)
    }

    @Test
    fun `manual assignment succeeds for a checked-in delegate`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-manual-assign-checked-in").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(
            repository.applyMymunUpdateSnapshot(
                conference.id.value,
                snapshotWithAssignments(
                    delegates = listOf(delegate(applicationStatus = "Checked In")),
                    confirmedAssignments = emptyList(),
                ),
            ).success,
        )
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single()
        val slot = loaded.openSlots.first()

        val updated = repository.assignDelegateToSlot(conference.id, slot.id, delegate.id, moveExistingPending = false)

        assertEquals(delegate.id, updated.pendingSlots.single().delegate?.id)
    }

    @Test
    fun `clearing pending slot persists and frees delegate`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-clear-pending").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(confirmedAssignments = emptyList())).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single()
        val slot = loaded.openSlots.first()
        repository.assignDelegateToSlot(conference.id, slot.id, delegate.id, moveExistingPending = false)

        val updated = repository.clearPendingSlot(conference.id, slot.id)

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
        val loaded = requireNotNull(repository.getConference(conference.id))
        val firstDelegate = loaded.delegates.single { it.name.firstName == "Alex" }
        val secondDelegate = loaded.delegates.single { it.name.firstName == "Blake" }
        val slot = loaded.openSlots.first()
        repository.assignDelegateToSlot(conference.id, slot.id, firstDelegate.id, moveExistingPending = false)

        val updated = repository.assignDelegateToSlot(conference.id, slot.id, secondDelegate.id, moveExistingPending = false)

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
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single()
        val sourceSlot = loaded.positions.single { it.name == "France" }.slots.single()
        val targetSlot = loaded.positions.single { it.name == "Germany" }.slots.single()
        repository.assignDelegateToSlot(conference.id, sourceSlot.id, delegate.id, moveExistingPending = false)

        assertFailsWith<IllegalArgumentException> {
            repository.assignDelegateToSlot(conference.id, targetSlot.id, delegate.id, moveExistingPending = false)
        }

        val moved = repository.assignDelegateToSlot(conference.id, targetSlot.id, delegate.id, moveExistingPending = true)
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
        val loaded = requireNotNull(repository.getConference(conference.id))
        val confirmedDelegate = loaded.delegates.single { it.name.firstName == "Alex" }
        val unassignedDelegate = loaded.delegates.single { it.name.firstName == "Blake" }
        val confirmedSlot = loaded.assignedSlots.single()
        val emptySlot = loaded.openSlots.single()

        assertFailsWith<IllegalArgumentException> {
            repository.clearPendingSlot(conference.id, confirmedSlot.id)
        }
        assertFailsWith<IllegalArgumentException> {
            repository.assignDelegateToSlot(conference.id, confirmedSlot.id, unassignedDelegate.id, moveExistingPending = false)
        }
        assertFailsWith<IllegalArgumentException> {
            repository.assignDelegateToSlot(conference.id, emptySlot.id, confirmedDelegate.id, moveExistingPending = true)
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
        val loaded = requireNotNull(repository.getConference(conference.id))
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
            repository.assignDelegateToSlot(conference.id, slots[0].id, unpaidDelegate.id, moveExistingPending = false)
        }
        assertFailsWith<IllegalArgumentException> {
            repository.assignDelegateToSlot(conference.id, slots[1].id, inactiveDelegate.id, moveExistingPending = false)
        }
        Unit
    }

    @Test
    fun `manual assignment rejects submitted delegates`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-submitted-delegates").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val delegates = listOf(
            delegate(
                rowNumber = 2,
                firstName = "Delegate",
                lastName = "H",
                email = "delegate.h@example.test",
                applicationStatus = "Submitted",
            ),
        )

        assertTrue(
            repository.applyMymunUpdateSnapshot(
                conference.id.value,
                snapshotWithAssignments(delegates = delegates, confirmedAssignments = emptyList()),
            ).success,
        )
        val loaded = requireNotNull(repository.getConference(conference.id))
        val submittedDelegate = loaded.delegates.single { it.name.firstName == "Delegate" }
        val slot = loaded.openSlots.first()

        assertFailsWith<IllegalArgumentException> {
            repository.assignDelegateToSlot(conference.id, slot.id, submittedDelegate.id, moveExistingPending = false)
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
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single()
        val slot = loaded.openSlots.first()
        repository.assignDelegateToSlot(conference.id, slot.id, delegate.id, moveExistingPending = false)

        val deactivated = repository.deactivateDelegate(conference.id, delegate.id)

        val inactiveDelegate = deactivated.delegates.single()
        assertFalse(inactiveDelegate.active)
        assertNull(inactiveDelegate.currentSlot)
        assertTrue(deactivated.pendingSlots.isEmpty())

        val reactivated = repository.reactivateDelegate(conference.id, delegate.id)

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
        val delegate = requireNotNull(repository.getConference(conference.id)).delegates.single()

        assertFailsWith<IllegalArgumentException> {
            repository.deactivateDelegate(conference.id, delegate.id)
        }

        val reloaded = requireNotNull(repository.getConference(conference.id)).delegates.single()
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
        val delegate = requireNotNull(repository.getConference(conference.id)).delegates.single()

        val added = repository.addDelegateComment(conference.id, delegate.id, " First note ")
        val comment = added.delegates.single().comments.single()
        assertEquals("First note", comment.body)

        val updated = repository.updateDelegateComment(conference.id, comment.id, "Edited note")
        assertEquals("Edited note", updated.delegates.single().comments.single().body)

        val deleted = repository.deleteDelegateComment(conference.id, comment.id)
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

        val firstLoaded = requireNotNull(repository.getConference(conference.id))
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
                it[CommitteeTable.mode] = CommitteeMode.HighSchool.name
            }
            DelegateQuestionTable.update({ DelegateQuestionTable.id eq question.id.value }) {
                it[visibleInDelegateDetail] = false
                it[organizerLabel] = "Local label"
            }
        }

        assertTrue(updater.applyUpdate(conference.id.value, workbookPath).success)

        val reloaded = requireNotNull(repository.getConference(conference.id))
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
    fun `MyMUN update rejects two incoming rows that match the same existing delegate by name only`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-ambiguous-delegate-match").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(
            repository.applyMymunUpdateSnapshot(
                conference.id.value,
                snapshotWithDelegates(listOf(delegate(rowNumber = 2, firstName = "Alex", lastName = "Kim", email = "seed@example.test"))),
            ).success,
        )

        val result = repository.applyMymunUpdateSnapshot(
            conferenceId = conference.id.value,
            snapshot = snapshotWithDelegates(
                listOf(
                    delegate(rowNumber = 2, firstName = "Alex", lastName = "Kim", email = "new1@example.test"),
                    delegate(rowNumber = 3, firstName = "Alex", lastName = "Kim", email = "new2@example.test"),
                ),
            ),
            sourceFilename = "ambiguous-delegate.xlsx",
        )

        assertFalse(result.success)
        val issue = result.issues.single { it.code == "ambiguous_delegate_match" }
        assertTrue("2" in issue.message && "3" in issue.message)

        val unchanged = repository.getConference(conference.id)!!
        assertEquals(1, unchanged.delegates.size)
        assertEquals("seed@example.test", unchanged.delegates.single().email)
    }

    @Test
    fun `MyMUN update creates two distinct new delegates that share a name`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-same-name-new").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        val result = repository.applyMymunUpdateSnapshot(
            conferenceId = conference.id.value,
            snapshot = snapshotWithDelegates(
                listOf(
                    delegate(rowNumber = 2, firstName = "Alex", lastName = "Kim", email = "alex1@example.test"),
                    delegate(rowNumber = 3, firstName = "Alex", lastName = "Kim", email = "alex2@example.test"),
                ),
            ),
        )

        assertTrue(result.success)
        assertEquals(2, result.summary.delegatesCreated)
        val conferenceAfter = repository.getConference(conference.id)!!
        assertEquals(setOf("alex1@example.test", "alex2@example.test"), conferenceAfter.delegates.map { it.email }.toSet())
    }

    @Test
    fun `MyMUN update resolves two same-named incoming rows against two existing delegates by email`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-same-name-resolved").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(
            repository.applyMymunUpdateSnapshot(
                conference.id.value,
                snapshotWithDelegates(
                    listOf(
                        delegate(rowNumber = 2, firstName = "Alex", lastName = "Kim", email = "alex1@example.test"),
                        delegate(rowNumber = 3, firstName = "Alex", lastName = "Kim", email = "alex2@example.test"),
                    ),
                ),
            ).success,
        )

        val result = repository.applyMymunUpdateSnapshot(
            conferenceId = conference.id.value,
            snapshot = snapshotWithDelegates(
                listOf(
                    delegate(rowNumber = 2, firstName = "Alex", lastName = "Kim", email = "alex1@example.test", feePaid = false),
                    delegate(rowNumber = 3, firstName = "Alex", lastName = "Kim", email = "alex2@example.test", feePaid = false),
                ),
            ),
        )

        assertTrue(result.success)
        assertEquals(2, result.summary.delegatesUpdated)
        val conferenceAfter = repository.getConference(conference.id)!!
        assertEquals(2, conferenceAfter.delegates.size)
        assertEquals(setOf("alex1@example.test", "alex2@example.test"), conferenceAfter.delegates.map { it.email }.toSet())
    }

    @Test
    fun `MyMUN update cites only the truly ambiguous rows when a third row disambiguates by email`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-partial-ambiguous").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(
            repository.applyMymunUpdateSnapshot(
                conference.id.value,
                snapshotWithDelegates(
                    listOf(
                        delegate(rowNumber = 2, firstName = "Alex", lastName = "Kim", email = "shared@example.test"),
                        delegate(rowNumber = 3, firstName = "Alex", lastName = "Kim", email = "other@example.test"),
                    ),
                ),
            ).success,
        )

        val result = repository.applyMymunUpdateSnapshot(
            conferenceId = conference.id.value,
            snapshot = snapshotWithDelegates(
                listOf(
                    delegate(rowNumber = 2, firstName = "Alex", lastName = "Kim", email = "shared@example.test"),
                    delegate(rowNumber = 3, firstName = "Alex", lastName = "Kim", email = "shared@example.test"),
                    delegate(rowNumber = 4, firstName = "Alex", lastName = "Kim", email = "other@example.test"),
                ),
            ),
            sourceFilename = "partial-ambiguous.xlsx",
        )

        assertFalse(result.success)
        val issue = result.issues.single { it.code == "ambiguous_delegate_match" }
        assertTrue("2" in issue.message && "3" in issue.message)
        assertFalse("4" in issue.message)
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
        val loaded = requireNotNull(repository.getConference(conference.id))
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
        val reloaded = requireNotNull(repository.getConference(conference.id))

        assertTrue(result.success, result.issues.joinToString("; ") { it.message })
        assertEquals(1, reloaded.pendingSlots.size)
        assertEquals(1, result.summary.pendingAssignmentsPreserved)
        assertEquals(0, result.summary.pendingAssignmentsCleared)
    }

    @Test
    fun `MyMUN update places a confirmed delegate in a free slot instead of overwriting a pending assignment at the same index`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-pending-not-displaced").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val delegates = listOf(
            delegate(rowNumber = 2, firstName = "Alex", lastName = "Kim", email = "alex@example.test"),
            delegate(rowNumber = 3, firstName = "Blake", lastName = "Roy", email = "blake@example.test"),
        )

        assertTrue(
            repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(delegates = delegates, confirmedAssignments = emptyList())).success,
        )
        val loaded = requireNotNull(repository.getConference(conference.id))
        val committee = loaded.committees.single()
        repository.setCommitteeSlotsPerPosition(conference.id, committee.id, 2)
        val alex = loaded.delegates.single { it.name.firstName == "Alex" }
        val franceSlot1 = requireNotNull(repository.getConference(conference.id))
            .positions.single { it.name == "France" }.slots.single { it.index == 1 }
        repository.assignDelegateToSlot(conference.id, franceSlot1.id, alex.id, moveExistingPending = false)

        val result = repository.applyMymunUpdateSnapshot(
            conferenceId = conference.id.value,
            snapshot = snapshotWithAssignments(
                delegates = delegates,
                confirmedAssignments = listOf(assignment(rowNumber = 2, positionName = "France", slotIndex = 1, delegateName = "Blake Roy")),
            ),
            sourceFilename = "confirm-blake.xlsx",
        )

        assertTrue(result.success, result.issues.joinToString("; ") { it.message })
        assertTrue(result.issues.none { it.code == "pending_assignment_displaced" })
        assertEquals(0, result.summary.pendingAssignmentsDisplaced)

        val reloaded = requireNotNull(repository.getConference(conference.id))
        val france = reloaded.positions.single { it.name == "France" }
        val pendingSlot = france.slots.single { it.isPending }
        val assignedSlot = france.slots.single { it.isAssigned }
        assertEquals("Alex", pendingSlot.delegate?.name?.firstName)
        assertEquals("Blake", assignedSlot.delegate?.name?.firstName)
    }

    @Test
    fun `MyMUN update warns and displaces a pending delegate when confirmed delegates fill every slot`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-forced-displacement").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val delegates = listOf(
            delegate(rowNumber = 2, firstName = "Alex", lastName = "Kim", email = "alex@example.test"),
            delegate(rowNumber = 3, firstName = "Blake", lastName = "Roy", email = "blake@example.test"),
            delegate(rowNumber = 4, firstName = "Casey", lastName = "Lee", email = "casey@example.test"),
        )

        assertTrue(
            repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(delegates = delegates, confirmedAssignments = emptyList())).success,
        )
        val loaded = requireNotNull(repository.getConference(conference.id))
        val committee = loaded.committees.single()
        repository.setCommitteeSlotsPerPosition(conference.id, committee.id, 2)
        val casey = loaded.delegates.single { it.name.firstName == "Casey" }
        val franceSlot1 = requireNotNull(repository.getConference(conference.id))
            .positions.single { it.name == "France" }.slots.single { it.index == 1 }
        repository.assignDelegateToSlot(conference.id, franceSlot1.id, casey.id, moveExistingPending = false)

        val result = repository.applyMymunUpdateSnapshot(
            conferenceId = conference.id.value,
            snapshot = snapshotWithAssignments(
                delegates = delegates,
                confirmedAssignments = listOf(
                    assignment(rowNumber = 2, positionName = "France", slotIndex = 1, delegateName = "Alex Kim"),
                    assignment(rowNumber = 3, positionName = "France", slotIndex = 2, delegateName = "Blake Roy"),
                ),
            ),
            sourceFilename = "force-displace.xlsx",
        )

        assertTrue(result.success, result.issues.joinToString("; ") { it.message })
        val warning = result.issues.single { it.code == "pending_assignment_displaced" }
        assertTrue("Casey" in warning.message)
        assertEquals(1, result.summary.pendingAssignmentsDisplaced)

        val reloaded = requireNotNull(repository.getConference(conference.id))
        val france = reloaded.positions.single { it.name == "France" }
        assertEquals(setOf("Alex", "Blake"), france.slots.mapNotNull { it.delegate?.name?.firstName }.toSet())
        assertNull(reloaded.delegates.single { it.name.firstName == "Casey" }.currentSlot)
    }

    @Test
    fun `MyMUN update flips a pending delegate's own slot to confirmed without a warning`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-self-confirm").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val delegates = listOf(delegate(rowNumber = 2, firstName = "Alex", lastName = "Kim", email = "alex@example.test"))

        assertTrue(
            repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(delegates = delegates, confirmedAssignments = emptyList())).success,
        )
        val loaded = requireNotNull(repository.getConference(conference.id))
        val alex = loaded.delegates.single()
        val franceSlot = loaded.positions.single { it.name == "France" }.slots.single()
        repository.assignDelegateToSlot(conference.id, franceSlot.id, alex.id, moveExistingPending = false)

        val result = repository.applyMymunUpdateSnapshot(
            conferenceId = conference.id.value,
            snapshot = snapshotWithAssignments(
                delegates = delegates,
                confirmedAssignments = listOf(assignment(rowNumber = 2, positionName = "France", slotIndex = 1, delegateName = "Alex Kim")),
            ),
            sourceFilename = "self-confirm.xlsx",
        )

        assertTrue(result.success, result.issues.joinToString("; ") { it.message })
        assertTrue(result.issues.none { it.code == "pending_assignment_displaced" })
        assertEquals(0, result.summary.pendingAssignmentsDisplaced)
        val reloaded = requireNotNull(repository.getConference(conference.id))
        val slot = reloaded.positions.single { it.name == "France" }.slots.single()
        assertTrue(slot.isAssigned)
        assertEquals("Alex", slot.delegate?.name?.firstName)
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
    fun `delegation school level and review state are organizer managed across updates`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-delegation-organizer-state").resolve("data.db")
        val repository = ConferenceRepositoryImpl(DatabaseFactory.connect(dbPath))
        val conference = repository.createConference("TestConf")
        val originalSnapshot = reservationSnapshot(
            delegations = listOf(delegation("School Delegation")),
        )

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, originalSnapshot).success)
        val created = requireNotNull(repository.getConference(conference.id)).delegations.single()
        assertFalse(created.isHighSchoolDelegation)
        assertTrue(created.needsReview)

        repository.setDelegationHighSchoolStatus(conference.id, created.id, isHighSchoolDelegation = true)
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, originalSnapshot).success)
        val retained = requireNotNull(repository.getConference(conference.id)).delegations.single()
        assertTrue(retained.isHighSchoolDelegation)
        assertTrue(retained.needsReview)

        val reviewed = repository.markDelegationsReviewed(conference.id)
        assertFalse(reviewed.delegations.single().needsReview)

        val expandedSnapshot = reservationSnapshot(
            delegations = listOf(
                delegation("School Delegation"),
                delegation("New Delegation"),
            ),
        )
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, expandedSnapshot).success)
        val updated = requireNotNull(repository.getConference(conference.id))
        val original = updated.delegations.single { it.name == "School Delegation" }
        val added = updated.delegations.single { it.name == "New Delegation" }
        assertTrue(original.isHighSchoolDelegation)
        assertFalse(original.needsReview)
        assertFalse(added.isHighSchoolDelegation)
        assertTrue(added.needsReview)
    }

    @Test
    fun `delegation school level rejects a delegation from another conference`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-delegation-school-level-scope").resolve("data.db")
        val repository = ConferenceRepositoryImpl(DatabaseFactory.connect(dbPath))
        val first = repository.createConference("First")
        val second = repository.createConference("Second")
        assertTrue(repository.applyMymunUpdateSnapshot(first.id.value, reservationSnapshot()).success)
        val delegation = requireNotNull(repository.getConference(first.id)).delegations.single()

        assertFailsWith<IllegalArgumentException> {
            repository.setDelegationHighSchoolStatus(second.id, delegation.id, isHighSchoolDelegation = true)
        }
        Unit
    }

    @Test
    fun `delegation organizer state migration defaults existing rows to university and reviewed`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-delegation-organizer-migration").resolve("data.db")
        val repository = ConferenceRepositoryImpl(DatabaseFactory.connect(dbPath))
        val conference = repository.createConference("TestConf")
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, reservationSnapshot()).success)

        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("ALTER TABLE delegations DROP COLUMN is_high_school_delegation")
                statement.executeUpdate("ALTER TABLE delegations DROP COLUMN needs_review")
                statement.executeUpdate("DELETE FROM schema_migrations WHERE version = 10")
            }
        }

        val migratedRepository = ConferenceRepositoryImpl(DatabaseFactory.connect(dbPath))
        val delegation = requireNotNull(migratedRepository.getConference(conference.id)).delegations.single()
        assertFalse(delegation.isHighSchoolDelegation)
        assertFalse(delegation.needsReview)
    }

    @Test
    fun `old update runs are pruned beyond the retention limit`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-update-run-retention").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val snapshot = snapshotWithDelegates(emptyList())

        repeat(25) { index ->
            assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshot, "update-$index.xlsx").success)
        }

        val remainingRunCount = transaction(database) {
            MymunUpdateRunTable.selectAll().where { MymunUpdateRunTable.conferenceId eq conference.id.value }.count()
        }
        assertEquals(20, remainingRunCount)
    }

    @Test
    fun `MyMUN update counts pending assignments cleared by pruned positions`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-pruned-pending").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val initialSnapshot = snapshotWithAssignments(confirmedAssignments = emptyList())

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, initialSnapshot).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
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
    fun `MyMUN update fully clears a slot when its pending delegate is removed from the workbook`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-removed-delegate-slot").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val delegates = listOf(
            delegate(rowNumber = 2, firstName = "Alex", lastName = "Kim", email = "alex@example.test"),
            delegate(rowNumber = 3, firstName = "Blake", lastName = "Roy", email = "blake@example.test"),
        )

        assertTrue(
            repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments(delegates = delegates, confirmedAssignments = emptyList())).success,
        )
        val loaded = requireNotNull(repository.getConference(conference.id))
        val blake = loaded.delegates.single { it.name.firstName == "Blake" }
        val germanySlot = loaded.positions.single { it.name == "Germany" }.slots.single()
        repository.assignDelegateToSlot(conference.id, germanySlot.id, blake.id, moveExistingPending = false)

        val result = repository.applyMymunUpdateSnapshot(
            conferenceId = conference.id.value,
            snapshot = snapshotWithAssignments(
                delegates = listOf(delegates[0]),
                confirmedAssignments = emptyList(),
            ),
            sourceFilename = "remove-blake.xlsx",
        )

        assertTrue(result.success, result.issues.joinToString("; ") { it.message })
        assertEquals(1, result.summary.delegatesRemoved)
        transaction(database) {
            val slotRow = SlotTable.selectAll().where { SlotTable.id eq germanySlot.id.value }.single()
            assertNull(slotRow[SlotTable.assignedDelegateId])
            assertNull(slotRow[SlotTable.assignmentSource])
        }
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
    fun `answer issue resolution persists and survives repeated update for retained delegate and question`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-issue-resolution").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithQuestion()).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single()
        val question = loaded.questions.single()
        val issue = delegate.answers.single().issues.single()

        val resolved = repository.resolveAnswerIssue(
            conferenceId = conference.id,
            delegateId = delegate.id,
            questionId = question.id,
            issueKey = issue.key,
            justification = "Reviewed and acceptable.",
        )

        assertTrue(resolved.delegates.single().answers.single().openIssues.isEmpty())
        assertEquals(
            "Reviewed and acceptable.",
            resolved.delegates.single().answers.single().resolvedIssues.single().resolution.justification,
        )

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithQuestion()).success)
        val afterUpdate = requireNotNull(repository.getConference(conference.id))
        assertTrue(afterUpdate.delegates.single().answers.single().openIssues.isEmpty())
        assertEquals(
            "Reviewed and acceptable.",
            afterUpdate.delegates.single().answers.single().resolvedIssues.single().resolution.justification,
        )
    }

    @Test
    fun `payment tier mismatches can be acknowledged but missing tiers cannot be dismissed`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-payment-tier-resolution").resolve("data.db")
        val repository = ConferenceRepositoryImpl(DatabaseFactory.connect(dbPath))
        val conference = repository.createConference("TestConf")
        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithAssignments()).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single()
        val missingIssue = delegate.paymentTierIssues().single()

        assertFailsWith<IllegalArgumentException> {
            repository.resolveAnswerIssue(conference.id, delegate.id, null, missingIssue.key, null)
        }

        val classified = repository.setDelegatePaymentTier(conference.id, delegate.id, PaymentTier.Reduced)
        val mismatch = classified.delegates.single().paymentTierIssues().single()
        val resolved = repository.resolveAnswerIssue(
            conference.id,
            delegate.id,
            null,
            mismatch.key,
            "Approved exception.",
        )

        assertTrue(resolved.delegates.single().openPaymentTierIssues().isEmpty())
        assertEquals(
            "Approved exception.",
            resolved.delegates.single().resolvedPaymentTierIssues().single().resolution.justification,
        )
    }

    @Test
    fun `answer issue resolution justification can be edited and undone`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-issue-resolution-edit").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithQuestion()).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.single()
        val question = loaded.questions.single()
        val issue = delegate.answers.single().issues.single()
        val resolved = repository.resolveAnswerIssue(
            conferenceId = conference.id,
            delegateId = delegate.id,
            questionId = question.id,
            issueKey = issue.key,
            justification = "Initial.",
        )
        val resolutionId = resolved.delegates.single().answers.single().resolvedIssues.single().resolution.id

        val edited = repository.updateAnswerIssueResolutionJustification(
            conferenceId = conference.id,
            resolutionId = resolutionId,
            justification = "Edited.",
        )

        assertEquals("Edited.", edited.delegates.single().answers.single().resolvedIssues.single().resolution.justification)

        val undone = repository.unresolveAnswerIssue(conferenceId = conference.id, resolutionId = resolutionId)

        assertEquals(1, undone.delegates.single().answers.single().openIssues.size)
        assertTrue(undone.delegates.single().answers.single().resolvedIssues.isEmpty())
        transaction(database) {
            assertEquals(0, AnswerIssueResolutionTable.selectAll().count())
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
        val loaded = requireNotNull(repository.getConference(conference.id))
        val question = loaded.questions.single()

        val updated = repository.updateQuestionConfiguration(
            conferenceId = conference.id,
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
        val question = requireNotNull(repository.getConference(conference.id)).questions.single()

        val saved = repository.updateQuestionConfiguration(
            conferenceId = conference.id,
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
                conferenceId = conference.id,
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
        val question = requireNotNull(repository.getConference(conference.id)).questions.single()
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
            conferenceId = conference.id,
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
        val questions = requireNotNull(repository.getConference(conference.id)).questions.associateBy { it.questionText }

        assertNull(questions.getValue("Question: Please describe any of your accessibility needs.").role)
        assertEquals(StandardQuestionRoles.HighSchoolerStatus, questions.getValue("Question: Are you a high-schooler?").role)
        assertEquals(StandardQuestionRoles.ExperienceLevel, questions.getValue("Question: What is your MUN experience?").role)
        assertEquals(
            StandardQuestionHandlingTypes.CommitteeChoiceExplanation,
            questions.getValue("Question: Please explain your choice of comittees.").handling,
        )
    }

    @Test
    fun `MyMUN update warns for a newly observed unmapped role answer`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-unmapped-role-answer").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        val result = repository.applyMymunUpdateSnapshot(
            conference.id.value,
            snapshotWithRoleAnswer("Maybe"),
        )

        assertTrue(result.success, result.issues.joinToString("; ") { it.message })
        val warning = result.issues.single { it.code == "unmapped_role_answer" }
        assertEquals(UpdateIssueSeverity.Warning, warning.severity)
        assertTrue("Question: Are you a high-schooler?" in warning.message)
        assertTrue("Maybe" in warning.message)
    }

    @Test
    fun `MyMUN update does not warn for a default-mapped role answer`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-default-role-answer").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        val result = repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithRoleAnswer("Yes"))

        assertTrue(result.success, result.issues.joinToString("; ") { it.message })
        assertTrue(result.issues.none { it.code == "unmapped_role_answer" })
    }

    @Test
    fun `MyMUN update does not warn for a mapped role answer`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-mapped-role-answer").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        assertTrue(
            repository.applyMymunUpdateSnapshot(
                conference.id.value,
                snapshotWithQuestions(listOf("Question: Are you a high-schooler?")),
            ).success,
        )
        val question = requireNotNull(repository.getConference(conference.id)).questions.single()
        repository.updateQuestionConfiguration(
            conferenceId = conference.id,
            configuration = QuestionConfiguration(
                questionId = question.id,
                questionText = question.questionText,
                visibleInDelegateDetail = false,
                organizerLabel = null,
                role = StandardQuestionRoles.HighSchoolerStatus,
                handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
                displayOrder = question.displayOrder,
                rules = emptyList(),
                mappings = TypedAnswerMappings(
                    highSchooler = listOf(HighSchoolerAnswerMapping("Maybe", HighSchoolerStatus.Yes)),
                ),
            ),
        )

        val result = repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithRoleAnswer("Maybe"))

        assertTrue(result.success, result.issues.joinToString("; ") { it.message })
        assertTrue(result.issues.none { it.code == "unmapped_role_answer" })
    }

    @Test
    fun `MyMUN update respects an organizer-cleared question role`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-cleared-question-role").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        assertTrue(
            repository.applyMymunUpdateSnapshot(
                conference.id.value,
                snapshotWithQuestions(listOf("Question: Are you a high-schooler?")),
            ).success,
        )
        val question = requireNotNull(repository.getConference(conference.id)).questions.single()
        repository.updateQuestionConfiguration(
            conferenceId = conference.id,
            configuration = QuestionConfiguration(
                questionId = question.id,
                questionText = question.questionText,
                visibleInDelegateDetail = true,
                organizerLabel = null,
                role = null,
                handling = StandardQuestionHandlingTypes.Default,
                displayOrder = question.displayOrder,
                rules = emptyList(),
                mappings = TypedAnswerMappings(),
            ),
        )

        val result = repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithRoleAnswer("Maybe"))

        assertTrue(result.success, result.issues.joinToString("; ") { it.message })
        assertTrue(result.issues.none { it.code == "unmapped_role_answer" })
    }

    @Test
    fun `MyMUN update does not repeat an unmapped role answer warning`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-observed-role-answer").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")
        val firstResult = repository.applyMymunUpdateSnapshot(
            conference.id.value,
            snapshotWithRoleAnswer("Maybe"),
        )
        assertTrue(firstResult.issues.any { it.code == "unmapped_role_answer" })

        val repeatedResult = repository.applyMymunUpdateSnapshot(
            conference.id.value,
            snapshotWithRoleAnswer("Maybe"),
        )

        assertTrue(repeatedResult.success, repeatedResult.issues.joinToString("; ") { it.message })
        assertTrue(repeatedResult.issues.none { it.code == "unmapped_role_answer" })
    }

    @Test
    fun `role inference takes precedence over overlapping general question handling`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-question-role-precedence").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("KAMUN")
        val questionText = "Question: Are you a high-schooler and do you need visa support?"

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithQuestions(listOf(questionText))).success)
        val question = requireNotNull(repository.getConference(conference.id)).questions.single()

        assertEquals(StandardQuestionRoles.HighSchoolerStatus, question.role)
        assertEquals(StandardQuestionHandlingTypes.ExpectedTypedAnswer, question.handling)
    }

    @Test
    fun `database migration normalizes legacy role-backed question handling`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-question-handling-migration").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("KAMUN")
        val questionText = "Question: Are you a high-schooler?"

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithQuestions(listOf(questionText))).success)
        val questionId = requireNotNull(repository.getConference(conference.id)).questions.single().id.value
        transaction(database) {
            DelegateQuestionTable.update({ DelegateQuestionTable.id eq questionId }) {
                it[handlingTypeId] = StandardQuestionHandlingTypes.VisaSupport.id
            }
            exec("DELETE FROM schema_migrations WHERE version = 9")
        }

        val migratedRepository = ConferenceRepositoryImpl(DatabaseFactory.connect(dbPath))
        val migratedQuestion = requireNotNull(migratedRepository.getConference(conference.id)).questions.single()

        assertEquals(StandardQuestionHandlingTypes.ExpectedTypedAnswer, migratedQuestion.handling)
    }

    @Test
    fun `question reorder persists display order`() = runBlocking {
        val dbPath = Files.createTempDirectory("munassign-question-order").resolve("data.db")
        val database = DatabaseFactory.connect(dbPath)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("TestConf")

        assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithQuestions(listOf("Question: A", "Question: B"))).success)
        val loaded = requireNotNull(repository.getConference(conference.id))
        val reorderedIds = loaded.questions.sortedByDescending { it.questionText }.map { it.id.value }
        val reordered = repository.reorderQuestionConfigurations(conference.id, reorderedIds.map(::QuestionId))

        assertEquals(listOf("Question: B", "Question: A"), reordered.questions.sortedBy { it.displayOrder }.map { it.questionText })
    }

    private fun snapshotWithDelegates(delegates: List<MymunDelegateRow>): MymunWorkbookSnapshot =
        MymunWorkbookSnapshot(
            workbookName = "test.xlsx",
            delegates = delegates,
            countryPositions = emptyList(),
            delegations = emptyList(),
            questionColumns = emptyList(),
            questionAnswers = emptyList(),
            preferenceCandidates = emptyList(),
            confirmedAssignmentCandidates = emptyList(),
            issues = emptyList(),
        )

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

    private fun snapshotWithCommitteeNames(vararg committeeNames: String): MymunWorkbookSnapshot =
        MymunWorkbookSnapshot(
            workbookName = "committees.xlsx",
            delegates = listOf(delegate()),
            countryPositions = committeeNames.mapIndexed { index, committeeName ->
                MymunCountryPositionRow(
                    rowNumber = 2 + index,
                    committeeName = committeeName,
                    memberName = "Position $index",
                    delegationName = null,
                    delegatesRaw = null,
                )
            },
            delegations = emptyList(),
            questionColumns = emptyList(),
            questionAnswers = emptyList(),
            preferenceCandidates = emptyList(),
            confirmedAssignmentCandidates = emptyList(),
            issues = emptyList(),
        )

    private fun snapshotWithTwoCommittees(
        ipcPositionNames: List<String> = listOf("UNSC Reporter"),
    ): MymunWorkbookSnapshot =
        MymunWorkbookSnapshot(
            workbookName = "ipc.xlsx",
            delegates = listOf(delegate()),
            countryPositions = ipcPositionNames.mapIndexed { index, positionName ->
                MymunCountryPositionRow(
                    rowNumber = 2 + index,
                    committeeName = "IPC",
                    memberName = positionName,
                    delegationName = null,
                    delegatesRaw = null,
                )
            } + listOf(
                MymunCountryPositionRow(
                    rowNumber = 2 + ipcPositionNames.size,
                    committeeName = "UNSC",
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

    private fun snapshotWithQuestion(): MymunWorkbookSnapshot =
        snapshotWithQuestions(listOf("Question: Please explain your choice of comittees."))

    private fun missingPreferenceSnapshot(
        ranks: List<Int> = listOf(1),
        preference: MymunPreferenceCandidate? = null,
    ): MymunWorkbookSnapshot = MymunWorkbookSnapshot(
        workbookName = "missing-preference.xlsx",
        delegates = listOf(delegate()),
        countryPositions = listOf(
            MymunCountryPositionRow(2, "GA", "France", null, null),
            MymunCountryPositionRow(3, "GA", "Germany", null, null),
        ),
        delegations = emptyList(),
        questionColumns = emptyList(),
        questionAnswers = emptyList(),
        preferenceCandidates = listOfNotNull(preference),
        confirmedAssignmentCandidates = emptyList(),
        issues = emptyList(),
        preferenceRanks = ranks,
        missingPreferences = if (preference == null) {
            ranks.map { rank ->
                MymunMissingPreference(
                    delegateRowNumber = 2,
                    rank = rank,
                    location = CellLocation("Delegates", 2, rank + 4, "Preference $rank"),
                )
            }
        } else {
            emptyList()
        },
    )

    private fun gappedPreferenceSnapshot(
        germanyRank: Int = 3,
        missingRank: Int = 2,
    ): MymunWorkbookSnapshot {
        val preferences = listOf(
            MymunPreferenceCandidate(
                delegateRowNumber = 2,
                rank = 1,
                rawPreferenceText = "GA (France)",
                parsedCommitteeName = "GA",
                parsedPositionName = "France",
                resolvedCountryRowNumber = 2,
                location = CellLocation("Delegates", 2, 5, "Preference 1"),
            ),
            MymunPreferenceCandidate(
                delegateRowNumber = 2,
                rank = germanyRank,
                rawPreferenceText = "GA (Germany)",
                parsedCommitteeName = "GA",
                parsedPositionName = "Germany",
                resolvedCountryRowNumber = 3,
                location = CellLocation("Delegates", 2, germanyRank + 4, "Preference $germanyRank"),
            ),
        )
        return MymunWorkbookSnapshot(
            workbookName = "gapped-preference.xlsx",
            delegates = listOf(delegate()),
            countryPositions = listOf(
                MymunCountryPositionRow(2, "GA", "France", null, null),
                MymunCountryPositionRow(3, "GA", "Germany", null, null),
            ),
            delegations = emptyList(),
            questionColumns = emptyList(),
            questionAnswers = emptyList(),
            preferenceCandidates = preferences,
            confirmedAssignmentCandidates = emptyList(),
            issues = emptyList(),
            preferenceRanks = listOf(1, 2, 3),
            missingPreferences = listOf(
                MymunMissingPreference(
                    delegateRowNumber = 2,
                    rank = missingRank,
                    location = CellLocation("Delegates", 2, missingRank + 4, "Preference $missingRank"),
                ),
            ),
        )
    }

    private fun snapshotWithRoleAnswer(answer: String): MymunWorkbookSnapshot {
        val questionText = "Question: Are you a high-schooler?"
        val questionColumn = MymunQuestionColumn(
            sheetName = "Delegates",
            columnNumber = 5,
            displayOrder = 4,
            questionText = questionText,
        )
        return MymunWorkbookSnapshot(
            workbookName = "role-answer.xlsx",
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
            questionColumns = listOf(questionColumn),
            questionAnswers = listOf(
                MymunQuestionAnswerCell(
                    delegateRowNumber = 2,
                    questionColumn = questionColumn,
                    answer = answer,
                    location = CellLocation("Delegates", 2, 5, questionText),
                ),
            ),
            preferenceCandidates = emptyList(),
            confirmedAssignmentCandidates = emptyList(),
            issues = emptyList(),
        )
    }

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

    private fun reservationSnapshot(
        delegates: List<MymunDelegateRow> = listOf(reservationDelegate()),
        delegations: List<MymunDelegationRow> = listOf(delegation()),
    ): MymunWorkbookSnapshot {
        val highSchoolQuestion = MymunQuestionColumn(
            sheetName = "Delegates",
            columnNumber = 5,
            displayOrder = 4,
            questionText = "Question: Are you a high-schooler?",
        )
        return MymunWorkbookSnapshot(
            workbookName = "reservation.xlsx",
            delegates = delegates,
            countryPositions = listOf(
                MymunCountryPositionRow(
                    rowNumber = 2,
                    committeeName = "GA",
                    memberName = "France",
                    delegationName = null,
                    delegatesRaw = null,
                ),
            ),
            delegations = delegations,
            questionColumns = listOf(highSchoolQuestion),
            questionAnswers = delegates.map { delegate ->
                MymunQuestionAnswerCell(
                    delegateRowNumber = delegate.rowNumber,
                    questionColumn = highSchoolQuestion,
                    answer = "Yes",
                    location = CellLocation("Delegates", delegate.rowNumber, 5, highSchoolQuestion.questionText),
                )
            },
            preferenceCandidates = emptyList(),
            confirmedAssignmentCandidates = emptyList(),
            issues = emptyList(),
        )
    }

    private fun reservationDelegate(
        rowNumber: Int = 2,
        firstName: String = "Alex",
        lastName: String = "Kim",
        email: String = "alex@example.test",
        applicationStatus: String? = "Accepted",
        submittedAt: LocalDate? = LocalDate.parse("2026-01-01"),
        feePaid: Boolean = true,
        delegationName: String? = "School Delegation",
    ): MymunDelegateRow =
        delegate(
            rowNumber = rowNumber,
            firstName = firstName,
            lastName = lastName,
            email = email,
            applicationStatus = applicationStatus,
            submittedAt = submittedAt,
            feePaid = feePaid,
            delegationName = delegationName,
        )

    private fun delegation(name: String = "School Delegation"): MymunDelegationRow =
        MymunDelegationRow(
            rowNumber = 2,
            name = name,
            country = null,
            currentDelegates = null,
            expectedDelegates = null,
            applicationStatus = null,
            submittedAt = null,
            createdAt = null,
        )

    private fun delegate(
        rowNumber: Int = 2,
        firstName: String = "Alex",
        lastName: String = "Kim",
        email: String = "alex@example.test",
        applicationStatus: String? = "Accepted",
        submittedAt: LocalDate? = null,
        feePaid: Boolean = true,
        delegationName: String? = null,
    ): MymunDelegateRow =
        MymunDelegateRow(
            rowNumber = rowNumber,
            firstName = firstName,
            lastName = lastName,
            email = email,
            normalizedEmail = email,
            applicationStatus = applicationStatus,
            submittedAt = submittedAt,
            createdAt = null,
            feePaid = feePaid,
            country = null,
            city = null,
            streetAddress = null,
            zipCode = null,
            diet = null,
            phone = null,
            nationality = null,
            dateOfBirth = null,
            delegationName = delegationName,
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
