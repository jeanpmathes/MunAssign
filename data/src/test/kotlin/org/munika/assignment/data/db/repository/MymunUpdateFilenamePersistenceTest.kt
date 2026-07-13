package org.munika.assignment.data.db.repository

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.munika.assignment.data.db.DatabaseFactory
import org.munika.assignment.data.db.tables.MymunUpdateIssueTable
import org.munika.assignment.data.db.tables.MymunUpdateRunTable
import org.munika.assignment.data.db.tables.SchemaMigrationTable
import org.munika.assignment.data.excel.MymunValidationIssue
import org.munika.assignment.data.excel.MymunValidationSeverity
import org.munika.assignment.data.excel.MymunWorkbookSnapshot
import org.munika.assignment.domain.model.MymunUpdateFilenameCandidate

class MymunUpdateFilenamePersistenceTest {
    @Test
    fun `migration backfills latest successful filename and ignores newer failed run`() = runBlocking {
        val path = Files.createTempDirectory("trust-backfill").resolve("data.db")
        val database = DatabaseFactory.connect(path)
        val repository = ConferenceRepositoryImpl(database)
        val conference = repository.createConference("KAMUN")
        transaction(database) {
            MymunUpdateRunTable.insert {
                it[id] = "successful"
                it[conferenceId] = conference.id.value
                it[startedAtEpochMillis] = 90L
                it[completedAtEpochMillis] = 100L
                it[sourceFilename] = "mymun-2026-KAMUN_ The Black Forest Summit 2026-Applications-Export.xlsx"
            }
            MymunUpdateRunTable.insert {
                it[id] = "failed"
                it[conferenceId] = conference.id.value
                it[startedAtEpochMillis] = 190L
                it[completedAtEpochMillis] = 200L
                it[sourceFilename] = "WRONG-CONFERENCE.xlsx"
            }
            MymunUpdateIssueTable.insert {
                it[id] = "failure-issue"
                it[updateRunId] = "failed"
                it[severity] = "Error"
                it[code] = "broken"
                it[message] = "Broken"
                it[sheetName] = null
                it[rowNumber] = null
                it[columnNumber] = null
                it[columnName] = null
            }
            TransactionManager.current().exec("DROP TABLE mymun_update_filename_signature_parts")
            TransactionManager.current().exec("DROP TABLE mymun_update_filename_signatures")
            SchemaMigrationTable.deleteWhere { version eq 11 }
        }
        TransactionManager.closeAndUnregister(database)

        val migratedRepository = ConferenceRepositoryImpl(DatabaseFactory.connect(path))
        val signatures = migratedRepository.getConference(conference.id)!!.mymunUpdateFilenameSignatures

        assertEquals(1, signatures.size)
        assertEquals(listOf("KAMUN The Black Forest Summit 2026"), signatures.single().parts.map { it.displayValue })
    }

    @Test
    fun `successful update trusts candidate and supports individual deletion`() = runBlocking {
        val repository = repository("trust-success")
        val conference = repository.createConference("KAMUN")
        val candidate = MymunUpdateFilenameCandidate.fromFilename(
            "mymun-2026-KAMUN_ The Black Forest Summit 2026-Applications-Export.xlsx",
        )

        val result = repository.applyMymunUpdateSnapshot(
            conferenceId = conference.id.value,
            snapshot = snapshot(
                MymunValidationIssue(
                    severity = MymunValidationSeverity.Warning,
                    code = "review",
                    message = "Review value",
                    location = null,
                ),
            ),
            sourceFilename = "source.xlsx",
            signatureToTrust = candidate,
        )

        assertTrue(result.success)
        repository.applyMymunUpdateSnapshot(
            conferenceId = conference.id.value,
            snapshot = snapshot(),
            signatureToTrust = candidate,
        )
        val trusted = repository.getConference(conference.id)!!.mymunUpdateFilenameSignatures.single()
        assertEquals(listOf("KAMUN The Black Forest Summit 2026"), trusted.parts.map { it.displayValue })

        val updated = repository.deleteMymunUpdateFilenameSignature(conference.id, trusted.id)
        assertTrue(updated.mymunUpdateFilenameSignatures.isEmpty())
    }

    @Test
    fun `blocking update does not trust candidate`() = runBlocking {
        val repository = repository("trust-failure")
        val conference = repository.createConference("KAMUN")

        val result = repository.applyMymunUpdateSnapshot(
            conferenceId = conference.id.value,
            snapshot = snapshot(
                MymunValidationIssue(
                    severity = MymunValidationSeverity.Error,
                    code = "broken",
                    message = "Broken workbook",
                    location = null,
                ),
            ),
            signatureToTrust = MymunUpdateFilenameCandidate.fromFilename("KAMUN.xlsx"),
        )

        assertTrue(!result.success)
        assertTrue(repository.getConference(conference.id)!!.mymunUpdateFilenameSignatures.isEmpty())
    }

    private fun repository(name: String): ConferenceRepositoryImpl {
        val path = Files.createTempDirectory(name).resolve("data.db")
        return ConferenceRepositoryImpl(DatabaseFactory.connect(path))
    }

    private fun snapshot(vararg issues: MymunValidationIssue): MymunWorkbookSnapshot =
        MymunWorkbookSnapshot(
            workbookName = "source.xlsx",
            delegates = emptyList(),
            countryPositions = emptyList(),
            delegations = emptyList(),
            questionColumns = emptyList(),
            questionAnswers = emptyList(),
            preferenceCandidates = emptyList(),
            confirmedAssignmentCandidates = emptyList(),
            issues = issues.toList(),
        )
}
