package org.munika.assignment.data.db

import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.munika.assignment.data.db.repository.ConferenceRepositoryImpl

class MunAssignFileMetadataTest {
    @Test
    fun `application database and exported transfer are identified from contents`() = runBlocking {
        val applicationPath = Files.createTempDirectory("munassign-file-metadata").resolve("renamed.anything")
        val repository = ConferenceRepositoryImpl(DatabaseFactory.connect(applicationPath))
        val conference = repository.createConference("KAMUN")
        val transferPath = applicationPath.parent.resolve("no-transfer-extension.bin")

        ConferenceTransferService(applicationPath).exportConference(conference.id.value, transferPath)

        val inspector = MunAssignFileInspector()
        val application = assertIs<MunAssignFileInspection.Valid>(inspector.inspect(applicationPath))
        val transfer = assertIs<MunAssignFileInspection.Valid>(inspector.inspect(transferPath))
        assertEquals(MunAssignFileKind.ApplicationDatabase, application.metadata.kind)
        assertEquals(MunAssignFileKind.ConferenceTransfer, transfer.metadata.kind)
        assertEquals("KAMUN", transfer.conferenceName)
    }

    @Test
    fun `markerless schema is identified as a legacy transfer`() {
        val path = Files.createTempDirectory("munassign-legacy-metadata").resolve("legacy.munassign")
        DatabaseFactory.connect(path)
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("DROP TABLE munassign_file_metadata")
                statement.executeUpdate("DELETE FROM schema_migrations WHERE version = 13")
                statement.executeUpdate("DELETE FROM schema_migrations WHERE version = 12")
            }
        }

        val inspection = assertIs<MunAssignFileInspection.LegacyTransfer>(MunAssignFileInspector().inspect(path))
        assertEquals(11, inspection.schemaVersion)
    }

    @Test
    fun `synchronized marker keeps a stable file id`() {
        val path = Files.createTempDirectory("munassign-sync-metadata").resolve("conference.sync.munassign")
        DatabaseFactory.connect(path)

        val metadata = MunAssignFileMetadataStore(path).setKind(MunAssignFileKind.SynchronizedConference)

        val inspection = assertIs<MunAssignFileInspection.Valid>(MunAssignFileInspector().inspect(path))
        assertEquals(MunAssignFileKind.SynchronizedConference, inspection.metadata.kind)
        assertEquals(metadata.fileId, inspection.metadata.fileId)
    }
}
