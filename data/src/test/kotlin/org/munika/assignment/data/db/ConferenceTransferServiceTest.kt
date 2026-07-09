package org.munika.assignment.data.db

import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.munika.assignment.data.db.repository.ConferenceRepositoryImpl
import org.munika.assignment.data.db.tables.AnswerIssueResolutionTable
import org.munika.assignment.data.excel.MymunWorkbookUpdater
import org.munika.assignment.domain.model.AnswerIssueSeverity
import org.munika.assignment.domain.model.ConferenceId

class ConferenceTransferServiceTest {
    @Test
    fun `exported conference imports into fresh database with remapped ids and owned data`() = runBlocking {
        val sourcePath = tempDatabase("munassign-transfer-source")
        val sourceDatabase = DatabaseFactory.connect(sourcePath)
        val sourceRepository = ConferenceRepositoryImpl(sourceDatabase)
        val sourceConference = populatedConference(sourceRepository, sourceDatabase, "KAMUN")
        val sourceLoaded = requireNotNull(sourceRepository.getConference(sourceConference.id))
        val transferPath = Files.createTempDirectory("munassign-transfer-file").resolve("kamun.munassign")

        ConferenceTransferService(sourcePath).exportConference(sourceConference.id.value, transferPath)

        val targetPath = tempDatabase("munassign-transfer-target")
        val targetDatabase = DatabaseFactory.connect(targetPath)
        val targetRepository = ConferenceRepositoryImpl(targetDatabase)
        val importedId = ConferenceTransferService(targetPath).importConferenceAsNew(transferPath)

        val imported = requireNotNull(targetRepository.getConference(ConferenceId(importedId)))
        assertEquals(sourceLoaded.name, imported.name)
        assertNotEquals(sourceLoaded.id, imported.id)
        assertEquals(sourceLoaded.committees.size, imported.committees.size)
        assertEquals(sourceLoaded.delegates.size, imported.delegates.size)
        assertEquals(sourceLoaded.questions.size, imported.questions.size)
        assertEquals(sourceLoaded.reservations.size, imported.reservations.size)
        assertEquals(sourceLoaded.delegates.sumOf { it.comments.size }, imported.delegates.sumOf { it.comments.size })
        assertEquals(sourceLoaded.delegates.sumOf { it.preferences.size }, imported.delegates.sumOf { it.preferences.size })
        assertEquals(sourceLoaded.delegates.sumOf { it.answers.size }, imported.delegates.sumOf { it.answers.size })
        assertEquals(sourceLoaded.delegates.sumOf { it.issueResolutions.size }, imported.delegates.sumOf { it.issueResolutions.size })
        assertTrue(imported.committees.flatMap { it.positions }.flatMap { it.slots }.isNotEmpty())
        assertTrue(imported.delegates.none { sourceLoaded.delegates.map { source -> source.id }.contains(it.id) })
    }

    @Test
    fun `import as new allows duplicate conference names`() = runBlocking {
        val sourcePath = tempDatabase("munassign-transfer-duplicate-source")
        val sourceRepository = ConferenceRepositoryImpl(DatabaseFactory.connect(sourcePath))
        val sourceConference = populatedConference(sourceRepository, DatabaseFactory.connect(sourcePath), "KAMUN")
        val transferPath = Files.createTempDirectory("munassign-transfer-duplicate-file").resolve("kamun.munassign")
        ConferenceTransferService(sourcePath).exportConference(sourceConference.id.value, transferPath)

        val targetPath = tempDatabase("munassign-transfer-duplicate-target")
        val targetRepository = ConferenceRepositoryImpl(DatabaseFactory.connect(targetPath))
        targetRepository.createConference("KAMUN")

        ConferenceTransferService(targetPath).importConferenceAsNew(transferPath)

        assertEquals(listOf("KAMUN", "KAMUN"), targetRepository.listConferences().map { it.name })
    }

    @Test
    fun `replace selected preserves local conference identity name and order`() = runBlocking {
        val sourcePath = tempDatabase("munassign-transfer-replace-source")
        val sourceRepository = ConferenceRepositoryImpl(DatabaseFactory.connect(sourcePath))
        val sourceConference = populatedConference(sourceRepository, DatabaseFactory.connect(sourcePath), "Imported")
        val transferPath = Files.createTempDirectory("munassign-transfer-replace-file").resolve("imported.munassign")
        ConferenceTransferService(sourcePath).exportConference(sourceConference.id.value, transferPath)

        val targetPath = tempDatabase("munassign-transfer-replace-target")
        val targetDatabase = DatabaseFactory.connect(targetPath)
        val targetRepository = ConferenceRepositoryImpl(targetDatabase)
        val first = targetRepository.createConference("First")
        val local = targetRepository.createConference("Local")
        targetRepository.reorderConferences(listOf(local.id, first.id))

        val replacedId = ConferenceTransferService(targetPath).replaceConference(local.id.value, transferPath)

        val replaced = requireNotNull(targetRepository.getConference(ConferenceId(replacedId)))
        assertEquals(local.id, replaced.id)
        assertEquals("Local", replaced.name)
        assertEquals(0, replaced.displayOrder)
        assertTrue(replaced.committees.isNotEmpty())
        assertEquals(listOf("Local", "First"), targetRepository.listConferences().map { it.name })
    }

    @Test
    fun `newer transfer file is rejected without changing target database`() = runBlocking {
        val sourcePath = tempDatabase("munassign-transfer-newer-source")
        val sourceRepository = ConferenceRepositoryImpl(DatabaseFactory.connect(sourcePath))
        val sourceConference = populatedConference(sourceRepository, DatabaseFactory.connect(sourcePath), "KAMUN")
        val transferPath = Files.createTempDirectory("munassign-transfer-newer-file").resolve("newer.munassign")
        ConferenceTransferService(sourcePath).exportConference(sourceConference.id.value, transferPath)
        setSchemaVersion(transferPath, DatabaseFactory.CURRENT_SCHEMA_VERSION + 1)

        val targetPath = tempDatabase("munassign-transfer-newer-target")
        val targetRepository = ConferenceRepositoryImpl(DatabaseFactory.connect(targetPath))
        targetRepository.createConference("Existing")

        val error = assertFailsWith<TransferException> {
            ConferenceTransferService(targetPath).importConferenceAsNew(transferPath)
        }

        assertTrue(error.message.orEmpty().contains("newer MunAssign"))
        assertEquals(listOf("Existing"), targetRepository.listConferences().map { it.name })
    }

    @Test
    fun `older transfer file is migrated from a temp copy before import`() = runBlocking {
        val sourcePath = tempDatabase("munassign-transfer-older-source")
        val sourceRepository = ConferenceRepositoryImpl(DatabaseFactory.connect(sourcePath))
        val sourceConference = populatedConference(sourceRepository, DatabaseFactory.connect(sourcePath), "KAMUN")
        val transferPath = Files.createTempDirectory("munassign-transfer-older-file").resolve("older.munassign")
        ConferenceTransferService(sourcePath).exportConference(sourceConference.id.value, transferPath)
        deleteSchemaVersion(transferPath, DatabaseFactory.CURRENT_SCHEMA_VERSION)

        val targetPath = tempDatabase("munassign-transfer-older-target")
        val targetRepository = ConferenceRepositoryImpl(DatabaseFactory.connect(targetPath))

        val importedId = ConferenceTransferService(targetPath).importConferenceAsNew(transferPath)

        assertNotNull(targetRepository.getConference(ConferenceId(importedId)))
        assertEquals(DatabaseFactory.CURRENT_SCHEMA_VERSION - 1, maxSchemaVersion(transferPath))
    }

    @Test
    fun `import succeeds despite a brief concurrent write lock thanks to busy timeout`() = runBlocking {
        val sourcePath = tempDatabase("munassign-busy-source")
        val sourceRepository = ConferenceRepositoryImpl(DatabaseFactory.connect(sourcePath))
        val sourceConference = populatedConference(sourceRepository, DatabaseFactory.connect(sourcePath), "KAMUN")
        val transferPath = Files.createTempDirectory("munassign-busy-file").resolve("kamun.munassign")
        ConferenceTransferService(sourcePath).exportConference(sourceConference.id.value, transferPath)

        val targetPath = tempDatabase("munassign-busy-target")
        val targetRepository = ConferenceRepositoryImpl(DatabaseFactory.connect(targetPath))
        targetRepository.createConference("Existing")

        val lockHeld = CountDownLatch(1)
        val lockThread = Thread {
            DriverManager.getConnection("jdbc:sqlite:${targetPath.toAbsolutePath()}").use { connection ->
                connection.autoCommit = false
                connection.createStatement().use { it.executeUpdate("UPDATE conferences SET name = name") }
                lockHeld.countDown()
                Thread.sleep(800)
                connection.commit()
            }
        }
        lockThread.start()
        lockHeld.await()

        val importedId = ConferenceTransferService(targetPath).importConferenceAsNew(transferPath)

        lockThread.join()
        assertTrue(targetRepository.getConference(ConferenceId(importedId)) != null)
    }

    @Test
    fun `invalid transfer file is rejected without changing target database`() = runBlocking {
        val invalidPath = Files.createTempDirectory("munassign-invalid-transfer").resolve("invalid.munassign")
        Files.writeString(invalidPath, "not sqlite")
        val targetPath = tempDatabase("munassign-invalid-target")
        val targetRepository = ConferenceRepositoryImpl(DatabaseFactory.connect(targetPath))
        targetRepository.createConference("Existing")

        assertFailsWith<TransferException> {
            ConferenceTransferService(targetPath).importConferenceAsNew(invalidPath)
        }

        assertEquals(listOf("Existing"), targetRepository.listConferences().map { it.name })
    }

    @Test
    fun `transfer file with zero or multiple conferences is rejected`() = runBlocking {
        val emptyTransferPath = tempDatabase("munassign-empty-transfer")
        DatabaseFactory.connect(emptyTransferPath)
        val multipleTransferPath = tempDatabase("munassign-multiple-transfer")
        val multipleRepository = ConferenceRepositoryImpl(DatabaseFactory.connect(multipleTransferPath))
        multipleRepository.createConference("First")
        multipleRepository.createConference("Second")
        val targetPath = tempDatabase("munassign-cardinality-target")
        val targetRepository = ConferenceRepositoryImpl(DatabaseFactory.connect(targetPath))
        targetRepository.createConference("Existing")

        assertFailsWith<TransferException> {
            ConferenceTransferService(targetPath).importConferenceAsNew(emptyTransferPath)
        }
        assertFailsWith<TransferException> {
            ConferenceTransferService(targetPath).importConferenceAsNew(multipleTransferPath)
        }

        assertEquals(listOf("Existing"), targetRepository.listConferences().map { it.name })
    }

    private suspend fun populatedConference(
        repository: ConferenceRepositoryImpl,
        database: org.jetbrains.exposed.v1.jdbc.Database,
        name: String,
    ): org.munika.assignment.domain.model.Conference {
        val conference = repository.createConference(name)
        val workbook = requireNotNull(javaClass.classLoader.getResource("export.xlsx"))
        val update = MymunWorkbookUpdater(repository).applyUpdate(conference.id.value, Path.of(workbook.toURI()))
        assertTrue(update.success, update.issues.joinToString("; ") { it.message })
        val loaded = requireNotNull(repository.getConference(conference.id))
        val delegate = loaded.delegates.first()
        repository.addDelegateComment(conference.id, delegate.id, "Transfer note")
        repository.createReservation(conference.id, "Transfer hold", highSchoolCount = 1, universityCount = 1, delegationId = null)
        val question = requireNotNull(loaded.questions.firstOrNull())
        transaction(database) {
            AnswerIssueResolutionTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[conferenceId] = conference.id.value
                it[delegateId] = delegate.id.value
                it[questionId] = question.id.value
                it[issueKey] = "transfer-test"
                it[issueMessage] = "Transfer test issue"
                it[severity] = AnswerIssueSeverity.Notice.name
                it[justification] = "Handled"
                it[resolvedAtEpochMillis] = 1L
            }
        }
        return requireNotNull(repository.getConference(conference.id))
    }

    private fun tempDatabase(prefix: String): Path =
        Files.createTempDirectory(prefix).resolve("data.db")

    private fun setSchemaVersion(path: Path, version: Int) {
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "INSERT INTO schema_migrations(version, name, applied_at_epoch_millis) VALUES($version, 'future', 1)",
                )
            }
        }
    }

    private fun deleteSchemaVersion(path: Path, version: Int) {
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("DELETE FROM schema_migrations WHERE version = $version")
            }
        }
    }

    private fun maxSchemaVersion(path: Path): Int =
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT MAX(version) FROM schema_migrations").use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }
}
