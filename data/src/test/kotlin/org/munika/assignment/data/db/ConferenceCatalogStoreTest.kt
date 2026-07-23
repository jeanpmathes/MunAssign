package org.munika.assignment.data.db

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ConferenceCatalogStoreTest {
    @Test
    fun `catalog round trips mixed ordered entries and unavailable state`() {
        val root = Files.createTempDirectory("munassign-catalog")
        val catalogPath = root.resolve("state").resolve("conference-catalog.properties")
        val synchronizedPath = root.resolve("shared").resolve("KAMUN.sync.munassign")
        val store = ConferenceCatalogStore(catalogPath, portablePathBase = root)
        val expected = ConferenceCatalog(
            entries = listOf(
                ConferenceCatalogEntry.local("local-id", "Local", displayOrder = 0),
                ConferenceCatalogEntry.synchronized(
                    conferenceId = "sync-id",
                    cachedName = "Shared",
                    displayOrder = 1,
                    fileId = "file-id",
                    path = synchronizedPath,
                    availability = ConferenceAvailability.Missing,
                    lastLoadedAtEpochMillis = 1234L,
                ),
            ),
            pendingMove = PendingConferenceMove("local-id", synchronizedPath, "file-id"),
        )

        store.save(expected)

        assertEquals(expected, store.load())
        val serialized = Files.readString(catalogPath)
        assertFalse(serialized.contains(root.toString()))
        assertFalse(Files.exists(catalogPath.resolveSibling("conference-catalog.properties.tmp")))
    }

    @Test
    fun `paths outside portable base remain absolute`() {
        val root = Files.createTempDirectory("munassign-catalog-portable")
        val outside = Files.createTempDirectory("munassign-catalog-outside").resolve("Shared.sync.munassign")
        val catalogPath = root.resolve("conference-catalog.properties")
        val store = ConferenceCatalogStore(catalogPath, portablePathBase = root)
        val catalog = ConferenceCatalog(
            listOf(
                ConferenceCatalogEntry.synchronized(
                    conferenceId = "sync-id",
                    cachedName = "Shared",
                    displayOrder = 0,
                    fileId = "file-id",
                    path = outside,
                ),
            ),
        )

        store.save(catalog)

        assertEquals(outside.toAbsolutePath().normalize(), store.load().entries.single().path)
        assertEquals("absolute", loadProperty(catalogPath, "entry.0.pathKind"))
    }

    private fun loadProperty(path: java.nio.file.Path, key: String): String {
        val properties = java.util.Properties()
        Files.newInputStream(path).use(properties::load)
        return properties.getProperty(key)
    }
}
