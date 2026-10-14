package org.munika.assignment.data.db

import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.munika.assignment.data.db.repository.ConferenceRepositoryImpl
import org.munika.assignment.domain.model.ConferenceId

class ConferenceStorageCoordinatorTest {
    @Test
    fun `synchronized conference is authoritative and can be connected from another catalog`() = runBlocking {
        val root = Files.createTempDirectory("munassign-coordinator-shared")
        val first = coordinator(root.resolve("first"))
        val synchronizedPath = root.resolve("shared").resolve("KAMUN.sync.munassign")
        val created = first.createSynchronizedConference("KAMUN", synchronizedPath)

        val renamed = first.renameConference(created.id, "KAMUN Shared")
        val second = coordinator(root.resolve("second"))
        val connected = second.connectSynchronizedConference(synchronizedPath)

        assertEquals("KAMUN Shared", renamed.name)
        assertEquals("KAMUN Shared", connected.name)
        assertEquals("KAMUN Shared", ConferenceRepositoryImpl(synchronizedPath).getConference(created.id)?.name)
        assertEquals(ConferenceStorageKind.Synchronized, second.listCatalogEntries().single().storageKind)
    }

    @Test
    fun `moving local conference changes authority only after synchronized file is valid`() = runBlocking {
        val root = Files.createTempDirectory("munassign-coordinator-move")
        val localPath = root.resolve("data.db")
        val coordinator = coordinator(root, localPath)
        val local = coordinator.createConference("Local work")
        val synchronizedPath = root.resolve("Local work.sync.munassign")

        val moved = coordinator.moveConferenceToSynchronized(local.id, synchronizedPath)

        assertEquals(local.id, moved.id)
        assertNull(ConferenceRepositoryImpl(localPath).getConference(local.id))
        assertNotNull(ConferenceRepositoryImpl(synchronizedPath).getConference(local.id))
        val entry = coordinator.listCatalogEntries().single()
        assertEquals(ConferenceStorageKind.Synchronized, entry.storageKind)
        assertNull(ConferenceCatalogStore(root.resolve("conference-catalog.properties")).load().pendingMove)
    }

    @Test
    fun `startup recovers a cataloged move after the synchronized file was committed`() = runBlocking {
        val root = Files.createTempDirectory("munassign-coordinator-recover-move")
        val localPath = root.resolve("data.db")
        val localRepository = ConferenceRepositoryImpl(localPath)
        val local = localRepository.createConference("Recover me")
        val synchronizedPath = root.resolve("Recover me.sync.munassign")
        ConferenceTransferService(localPath).exportConference(local.id.value, synchronizedPath)
        val metadata = MunAssignFileMetadataStore(synchronizedPath).setKind(MunAssignFileKind.SynchronizedConference)
        ConferenceCatalogStore(root.resolve("conference-catalog.properties")).save(
            ConferenceCatalog(
                entries = listOf(ConferenceCatalogEntry.local(local.id.value, local.name, 0)),
                pendingMove = PendingConferenceMove(local.id.value, synchronizedPath, metadata.fileId),
            ),
        )

        val recovered = coordinator(root, localPath)
        val entry = recovered.listCatalogEntries().single()

        assertEquals(ConferenceStorageKind.Synchronized, entry.storageKind)
        assertEquals(metadata.fileId, entry.fileId)
        assertNull(ConferenceRepositoryImpl(localPath).getConference(local.id))
        assertNotNull(recovered.getConference(local.id))
        assertNull(ConferenceCatalogStore(root.resolve("conference-catalog.properties")).load().pendingMove)
    }

    @Test
    fun `missing synchronized file remains listed and failed delete retains link`() = runBlocking {
        val root = Files.createTempDirectory("munassign-coordinator-missing")
        val coordinator = coordinator(root)
        val synchronizedPath = root.resolve("Missing.sync.munassign")
        val created = coordinator.createSynchronizedConference("Missing", synchronizedPath)
        Files.delete(synchronizedPath)

        val entry = coordinator.listCatalogEntries().single()

        assertEquals(ConferenceAvailability.Missing, entry.availability)
        assertNull(coordinator.getConference(created.id))
        assertFailsWith<java.nio.file.NoSuchFileException> {
            coordinator.deleteSynchronizedConferenceFile(created.id)
        }
        assertEquals(created.id.value, coordinator.listCatalogEntries().single().conferenceId)
    }

    @Test
    fun `connecting older synchronized schema creates local backup and migrates in place`() = runBlocking {
        val root = Files.createTempDirectory("munassign-coordinator-migration")
        val creator = coordinator(root.resolve("creator"))
        val synchronizedPath = root.resolve("Older.sync.munassign")
        creator.createSynchronizedConference("Older", synchronizedPath)
        DriverManager.getConnection("jdbc:sqlite:${synchronizedPath.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("DROP TABLE preference_completions")
                statement.executeUpdate(
                    """
                    CREATE TABLE preference_completions (
                        id VARCHAR(36) NOT NULL PRIMARY KEY,
                        delegate_id VARCHAR(36) NOT NULL REFERENCES delegates(id) ON DELETE CASCADE,
                        position_id VARCHAR(36) NOT NULL REFERENCES positions(id) ON DELETE CASCADE,
                        rank INT NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    "CREATE UNIQUE INDEX ux_preference_completions_delegate_rank " +
                        "ON preference_completions(delegate_id, rank)",
                )
                statement.executeUpdate(
                    "CREATE UNIQUE INDEX ux_preference_completions_delegate_position " +
                        "ON preference_completions(delegate_id, position_id)",
                )
                statement.executeUpdate("DELETE FROM schema_migrations WHERE version IN (15, 16)")
            }
        }
        val consumerRoot = root.resolve("consumer")
        val consumer = coordinator(consumerRoot)

        consumer.connectSynchronizedConference(synchronizedPath)

        val inspection = assertIs<MunAssignFileInspection.Valid>(MunAssignFileInspector().inspect(synchronizedPath))
        assertEquals(DatabaseFactory.CURRENT_SCHEMA_VERSION, inspection.schemaVersion)
        assertTrue(Files.list(consumerRoot.resolve("migration-backups")).use { it.findAny().isPresent })
    }

    @Test
    fun `synchronized sources require forced detached import and exports are normal transfers`() = runBlocking {
        val root = Files.createTempDirectory("munassign-coordinator-detached")
        val owner = coordinator(root.resolve("owner"))
        val synchronizedPath = root.resolve("Shared.sync.munassign")
        val shared = owner.createSynchronizedConference("Shared", synchronizedPath)
        val transferPath = root.resolve("Shared.munassign")

        owner.exportConference(shared.id, transferPath)

        val exported = assertIs<MunAssignFileInspection.Valid>(MunAssignFileInspector().inspect(transferPath))
        assertEquals(MunAssignFileKind.ConferenceTransfer, exported.metadata.kind)
        val consumer = coordinator(root.resolve("consumer"))
        assertFailsWith<TransferException> {
            consumer.importConferenceAsNew(synchronizedPath, allowSynchronizedSource = false)
        }

        val detached = consumer.importConferenceAsNew(synchronizedPath, allowSynchronizedSource = true)

        assertTrue(detached.id != shared.id)
        assertEquals(ConferenceStorageKind.Local, consumer.listCatalogEntries().single().storageKind)
        val sourceAfterImport = assertIs<MunAssignFileInspection.Valid>(MunAssignFileInspector().inspect(synchronizedPath))
        assertEquals(MunAssignFileKind.SynchronizedConference, sourceAfterImport.metadata.kind)
    }

    @Test
    fun `replace preserves synchronized file and conference identity while connect validates kind and duplicates`() = runBlocking {
        val root = Files.createTempDirectory("munassign-coordinator-replace")
        val owner = coordinator(root.resolve("owner"))
        val targetPath = root.resolve("Target.sync.munassign")
        val target = owner.createSynchronizedConference("Target", targetPath)
        val targetMetadata = assertIs<MunAssignFileInspection.Valid>(MunAssignFileInspector().inspect(targetPath)).metadata
        val source = owner.createConference("Source")
        val transferPath = root.resolve("Source.munassign")
        owner.exportConference(source.id, transferPath)

        val replaced = owner.replaceConference(target.id, transferPath, allowSynchronizedSource = false)

        assertEquals(target.id, replaced.id)
        assertEquals("Target", replaced.name)
        val metadataAfterReplace = assertIs<MunAssignFileInspection.Valid>(MunAssignFileInspector().inspect(targetPath)).metadata
        assertEquals(targetMetadata, metadataAfterReplace)

        val consumer = coordinator(root.resolve("consumer"))
        assertFailsWith<IllegalArgumentException> { consumer.connectSynchronizedConference(transferPath) }
        consumer.connectSynchronizedConference(targetPath)
        val copiedPath = root.resolve("Target conflict copy.sync.munassign")
        Files.copy(targetPath, copiedPath)
        assertFailsWith<IllegalArgumentException> { consumer.connectSynchronizedConference(copiedPath) }
        consumer.locateSynchronizedConference(target.id, copiedPath)
        assertEquals(copiedPath.toAbsolutePath().normalize(), consumer.listCatalogEntries().single().path)
        consumer.unlinkSynchronizedConference(target.id)
        assertTrue(consumer.listCatalogEntries().isEmpty())
    }

    private fun coordinator(root: java.nio.file.Path, localPath: java.nio.file.Path = root.resolve("data.db")): ConferenceStorageCoordinator {
        Files.createDirectories(root)
        return ConferenceStorageCoordinator(
            localDatabasePath = localPath,
            catalogStore = ConferenceCatalogStore(root.resolve("conference-catalog.properties")),
            migrationBackupDirectory = root.resolve("migration-backups"),
        )
    }
}
