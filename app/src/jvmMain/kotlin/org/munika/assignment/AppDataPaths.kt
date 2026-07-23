package org.munika.assignment

import java.nio.file.Path

/**
 * Resolved filesystem locations for MunAssign's local database, exports, and app-local state.
 */
data class AppDataPaths(
    val dataRoot: Path,
    val databasePath: Path,
    val markdownExportDirectory: Path,
    val windowStatePath: Path,
    val lastSelectedConferencePath: Path,
    val conferenceCatalogPath: Path,
    val synchronizedMigrationBackupDirectory: Path,
    val portablePathBase: Path?,
)

/**
 * Selects the local data root for normal and portable Windows launches.
 */
object AppDataPathResolver {
    /**
     * Resolves active app data paths without copying any existing database between locations.
     */
    fun resolve(
        home: Path = Path.of(System.getProperty("user.home")),
        packagedAppHome: Path? = detectPackagedAppHome(),
        osName: String = System.getProperty("os.name"),
    ): AppDataPaths {
        val dataRoot = if (packagedAppHome != null && osName.isWindows() && home.isOnDifferentDriveThan(packagedAppHome)) {
            packagedAppHome.resolve("data")
        } else {
            home.resolve(".munassign")
        }
        return AppDataPaths(
            dataRoot = dataRoot,
            databasePath = dataRoot.resolve("data.db"),
            markdownExportDirectory = dataRoot.resolve("markdown-exports"),
            windowStatePath = dataRoot.resolve("window-state.properties"),
            lastSelectedConferencePath = dataRoot.resolve("last-selected-conference.properties"),
            conferenceCatalogPath = dataRoot.resolve("conference-catalog.properties"),
            synchronizedMigrationBackupDirectory = dataRoot.resolve("synchronized-migration-backups"),
            portablePathBase = packagedAppHome?.takeIf {
                osName.isWindows() && home.isOnDifferentDriveThan(it)
            },
        )
    }

    private fun detectPackagedAppHome(): Path? {
        val runtimeHome = Path.of(System.getProperty("java.home"))
        val appHome = runtimeHome.parent ?: return null
        return appHome.takeIf { it.resolve("app").toFile().isDirectory && it.resolve("runtime").toFile().isDirectory }
    }

    private fun String.isWindows(): Boolean =
        startsWith("Windows", ignoreCase = true)

    private fun Path.isOnDifferentDriveThan(other: Path): Boolean {
        val thisRoot = toAbsolutePath().root?.toString()?.lowercase()
        val otherRoot = other.toAbsolutePath().root?.toString()?.lowercase()
        return thisRoot != null && otherRoot != null && thisRoot != otherRoot
    }
}
