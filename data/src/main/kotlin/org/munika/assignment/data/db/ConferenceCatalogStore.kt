package org.munika.assignment.data.db

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/** Storage location used by one conference entry. */
enum class ConferenceStorageKind {
    Local,
    Synchronized,
}

/** Last known usability of a conference catalog entry. */
enum class ConferenceAvailability {
    Available,
    Missing,
    Invalid,
    NewerVersion,
    Inaccessible,
}

/** Machine-local reference to either the application database or one synchronized conference file. */
data class ConferenceCatalogEntry(
    val conferenceId: String,
    val cachedName: String,
    val displayOrder: Int,
    val storageKind: ConferenceStorageKind,
    val fileId: String? = null,
    val path: Path? = null,
    val availability: ConferenceAvailability = ConferenceAvailability.Available,
    val lastLoadedAtEpochMillis: Long? = null,
) {
    init {
        require(conferenceId.isNotBlank()) { "Conference catalog ID must not be blank." }
        require(cachedName.isNotBlank()) { "Conference catalog name must not be blank." }
        require(displayOrder >= 0) { "Conference catalog display order must not be negative." }
        when (storageKind) {
            ConferenceStorageKind.Local -> require(fileId == null && path == null) {
                "Local conference entries must not have synchronized file data."
            }
            ConferenceStorageKind.Synchronized -> {
                require(!fileId.isNullOrBlank()) { "Synchronized conference entries require a file ID." }
                require(path != null) { "Synchronized conference entries require a path." }
            }
        }
    }

    companion object {
        fun local(conferenceId: String, cachedName: String, displayOrder: Int): ConferenceCatalogEntry =
            ConferenceCatalogEntry(conferenceId, cachedName, displayOrder, ConferenceStorageKind.Local)

        fun synchronized(
            conferenceId: String,
            cachedName: String,
            displayOrder: Int,
            fileId: String,
            path: Path,
            availability: ConferenceAvailability = ConferenceAvailability.Available,
            lastLoadedAtEpochMillis: Long? = null,
        ): ConferenceCatalogEntry = ConferenceCatalogEntry(
            conferenceId = conferenceId,
            cachedName = cachedName,
            displayOrder = displayOrder,
            storageKind = ConferenceStorageKind.Synchronized,
            fileId = fileId,
            path = path.toAbsolutePath().normalize(),
            availability = availability,
            lastLoadedAtEpochMillis = lastLoadedAtEpochMillis,
        )
    }
}

/** Recoverable cross-file transition from a local row to a synchronized file. */
data class PendingConferenceMove(
    val conferenceId: String,
    val synchronizedPath: Path,
    val fileId: String,
)

/** Complete machine-local conference source catalog. */
data class ConferenceCatalog(
    val entries: List<ConferenceCatalogEntry> = emptyList(),
    val pendingMove: PendingConferenceMove? = null,
) {
    init {
        require(entries.map { it.conferenceId }.distinct().size == entries.size) {
            "Conference catalog IDs must be unique."
        }
        val synchronizedFileIds = entries.mapNotNull { it.fileId }
        require(synchronizedFileIds.distinct().size == synchronizedFileIds.size) {
            "Synchronized file IDs must be unique."
        }
    }
}

/** Atomically persists the machine-local conference catalog and portable path encoding. */
class ConferenceCatalogStore(
    private val path: Path,
    private val portablePathBase: Path? = null,
) {
    fun load(): ConferenceCatalog {
        if (!Files.isRegularFile(path)) return ConferenceCatalog()
        val properties = Properties()
        Files.newInputStream(path).use(properties::load)
        require(properties.getProperty("version") == CatalogVersion) { "Unsupported conference catalog version." }
        val entryCount = properties.required("entry.count").toInt()
        val entries = (0 until entryCount).map { index -> properties.readEntry(index) }
        val pendingMove = if (properties.getProperty("pending.conferenceId") == null) {
            null
        } else {
            PendingConferenceMove(
                conferenceId = properties.required("pending.conferenceId"),
                synchronizedPath = properties.readPath("pending.pathKind", "pending.path"),
                fileId = properties.required("pending.fileId"),
            )
        }
        return ConferenceCatalog(entries, pendingMove)
    }

    fun save(catalog: ConferenceCatalog) {
        val properties = Properties().apply {
            setProperty("version", CatalogVersion)
            setProperty("entry.count", catalog.entries.size.toString())
            catalog.entries.forEachIndexed { index, entry -> writeEntry(index, entry) }
            catalog.pendingMove?.let { pending ->
                setProperty("pending.conferenceId", pending.conferenceId)
                setProperty("pending.fileId", pending.fileId)
                writePath("pending.pathKind", "pending.path", pending.synchronizedPath)
            }
        }
        path.parent?.let(Files::createDirectories)
        val temporaryPath = path.resolveSibling("${path.fileName}.tmp")
        Files.newOutputStream(temporaryPath).use { output ->
            properties.store(output, "MunAssign conference catalog")
        }
        try {
            Files.move(
                temporaryPath,
                path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun Properties.writeEntry(index: Int, entry: ConferenceCatalogEntry) {
        val prefix = "entry.$index"
        setProperty("$prefix.conferenceId", entry.conferenceId)
        setProperty("$prefix.cachedName", entry.cachedName)
        setProperty("$prefix.displayOrder", entry.displayOrder.toString())
        setProperty("$prefix.storageKind", entry.storageKind.name)
        setProperty("$prefix.availability", entry.availability.name)
        entry.lastLoadedAtEpochMillis?.let { setProperty("$prefix.lastLoadedAt", it.toString()) }
        if (entry.storageKind == ConferenceStorageKind.Synchronized) {
            setProperty("$prefix.fileId", requireNotNull(entry.fileId))
            writePath("$prefix.pathKind", "$prefix.path", requireNotNull(entry.path))
        }
    }

    private fun Properties.readEntry(index: Int): ConferenceCatalogEntry {
        val prefix = "entry.$index"
        val storageKind = ConferenceStorageKind.valueOf(required("$prefix.storageKind"))
        return ConferenceCatalogEntry(
            conferenceId = required("$prefix.conferenceId"),
            cachedName = required("$prefix.cachedName"),
            displayOrder = required("$prefix.displayOrder").toInt(),
            storageKind = storageKind,
            fileId = if (storageKind == ConferenceStorageKind.Synchronized) required("$prefix.fileId") else null,
            path = if (storageKind == ConferenceStorageKind.Synchronized) {
                readPath("$prefix.pathKind", "$prefix.path")
            } else {
                null
            },
            availability = ConferenceAvailability.valueOf(required("$prefix.availability")),
            lastLoadedAtEpochMillis = getProperty("$prefix.lastLoadedAt")?.toLong(),
        )
    }

    private fun Properties.writePath(kindKey: String, valueKey: String, candidate: Path) {
        val normalized = candidate.toAbsolutePath().normalize()
        val base = portablePathBase?.toAbsolutePath()?.normalize()
        if (base != null && normalized.startsWith(base)) {
            setProperty(kindKey, RelativePathKind)
            setProperty(valueKey, base.relativize(normalized).toString())
        } else {
            setProperty(kindKey, AbsolutePathKind)
            setProperty(valueKey, normalized.toString())
        }
    }

    private fun Properties.readPath(kindKey: String, valueKey: String): Path {
        val value = Path.of(required(valueKey))
        return when (required(kindKey)) {
            RelativePathKind -> requireNotNull(portablePathBase) {
                "Conference catalog contains a portable path without a portable base."
            }.resolve(value).toAbsolutePath().normalize()
            AbsolutePathKind -> value.toAbsolutePath().normalize()
            else -> error("Unsupported conference catalog path kind.")
        }
    }

    private fun Properties.required(key: String): String =
        requireNotNull(getProperty(key)) { "Conference catalog is missing $key." }

    private companion object {
        const val CatalogVersion = "1"
        const val RelativePathKind = "relative"
        const val AbsolutePathKind = "absolute"
    }
}
