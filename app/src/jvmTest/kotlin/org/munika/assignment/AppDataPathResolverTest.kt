package org.munika.assignment

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppDataPathResolverTest {
    @Test
    fun `same Windows drive uses home data directory`() {
        val paths = AppDataPathResolver.resolve(
            home = Path.of("C:\\Users\\organizer"),
            packagedAppHome = Path.of("C:\\Tools\\MunAssign"),
            osName = "Windows 11",
        )

        assertEquals(Path.of("C:\\Users\\organizer\\.munassign\\data.db"), paths.databasePath)
        assertEquals(Path.of("C:\\Users\\organizer\\.munassign\\markdown-exports"), paths.markdownExportDirectory)
    }

    @Test
    fun `different Windows drive uses app-local data directory`() {
        val paths = AppDataPathResolver.resolve(
            home = Path.of("C:\\Users\\organizer"),
            packagedAppHome = Path.of("E:\\MunAssign"),
            osName = "Windows 11",
        )

        assertEquals(Path.of("E:\\MunAssign\\data\\data.db"), paths.databasePath)
        assertEquals(Path.of("E:\\MunAssign\\data\\markdown-exports"), paths.markdownExportDirectory)
    }

    @Test
    fun `missing packaged app home uses home data directory`() {
        val paths = AppDataPathResolver.resolve(
            home = Path.of("C:\\Users\\organizer"),
            packagedAppHome = null,
            osName = "Windows 11",
        )

        assertEquals(Path.of("C:\\Users\\organizer\\.munassign\\data.db"), paths.databasePath)
    }

    @Test
    fun `existing home database is not copied into app-local data directory`() {
        val tempRoot = Files.createTempDirectory("munassign-paths")
        val home = tempRoot.resolve("home")
        val packagedAppHome = tempRoot.resolve("portable")
        val homeDatabase = home.resolve(".munassign").resolve("data.db")
        Files.createDirectories(homeDatabase.parent)
        Files.writeString(homeDatabase, "existing")

        val paths = AppDataPathResolver.resolve(
            home = Path.of("C:\\Users\\organizer"),
            packagedAppHome = Path.of("E:\\MunAssign"),
            osName = "Windows 11",
        )

        assertEquals(Path.of("E:\\MunAssign\\data\\data.db"), paths.databasePath)
        assertFalse(Files.exists(packagedAppHome.resolve("data").resolve("data.db")))
    }
}
