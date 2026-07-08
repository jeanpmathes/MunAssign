package org.munika.assignment.export

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.munika.assignment.domain.model.Conference

/**
 * Boundary used by the ViewModel to write markdown safety snapshots without depending on files.
 */
interface MarkdownSafetyExportRunner {
    suspend fun exportAll(
        conferences: List<Conference>,
        trigger: MarkdownSafetyExportTrigger,
    ): MarkdownSafetyExportResult
}

data class MarkdownSafetyExportResult(
    val directory: Path,
    val writtenFiles: List<Path>,
    val failures: List<MarkdownSafetyExportFailure>,
) {
    val hasFailures: Boolean
        get() = failures.isNotEmpty()
}

data class MarkdownSafetyExportFailure(
    val conferenceName: String,
    val message: String,
)

/**
 * Writes timestamped markdown recovery snapshots beneath the local MunAssign data directory.
 */
class MarkdownSafetyExportService(
    private val exportDirectory: Path = defaultExportDirectory(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : MarkdownSafetyExportRunner {
    override suspend fun exportAll(
        conferences: List<Conference>,
        trigger: MarkdownSafetyExportTrigger,
    ): MarkdownSafetyExportResult {
        Files.createDirectories(exportDirectory)
        val exportedAt = Instant.now(clock)
        val writtenFiles = mutableListOf<Path>()
        val failures = mutableListOf<MarkdownSafetyExportFailure>()

        conferences.forEach { conference ->
            try {
                val markdown = ConferenceMarkdownExporter.render(
                    conference = conference,
                    exportedAt = exportedAt,
                    trigger = trigger,
                    zoneId = zoneId,
                )
                val path = nextAvailablePath(conference.name, exportedAt, trigger)
                Files.writeString(path, markdown)
                writtenFiles.add(path)
                pruneOldExports(conference.name, trigger)
            } catch (exception: Exception) {
                failures += MarkdownSafetyExportFailure(
                    conferenceName = conference.name,
                    message = exception.message?.takeIf { it.isNotBlank() }
                        ?: exception::class.simpleName.orEmpty().ifBlank { "Unknown error" },
                )
            }
        }

        return MarkdownSafetyExportResult(
            directory = exportDirectory,
            writtenFiles = writtenFiles,
            failures = failures,
        )
    }

    internal fun nextAvailablePath(
        conferenceName: String,
        exportedAt: Instant,
        trigger: MarkdownSafetyExportTrigger,
    ): Path {
        val baseName = "${timestampLabel(exportedAt)}-${trigger.fileLabel}-${conferenceName.fileSafeName()}"
        var candidate = exportDirectory.resolve("$baseName.md")
        var suffix = 2
        while (Files.exists(candidate)) {
            candidate = exportDirectory.resolve("$baseName-$suffix.md")
            suffix += 1
        }
        return candidate
    }

    private fun timestampLabel(exportedAt: Instant): String =
        TimestampFormatter.withZone(zoneId).format(exportedAt)

    /** Keeps only the newest [MaxRetainedExportsPerConferenceTrigger] files for this conference/trigger pair. */
    private fun pruneOldExports(conferenceName: String, trigger: MarkdownSafetyExportTrigger) {
        try {
            val pattern = Regex(
                "^\\d{8}-\\d{6}-${Regex.escape(trigger.fileLabel)}-${Regex.escape(conferenceName.fileSafeName())}(-\\d+)?\\.md$",
            )
            val matches = Files.newDirectoryStream(exportDirectory).use { stream ->
                stream.filter { pattern.matches(it.fileName.toString()) }
            }.sortedBy { it.fileName.toString() }
            matches.dropLast(MaxRetainedExportsPerConferenceTrigger).forEach { Files.deleteIfExists(it) }
        } catch (_: Exception) {
            // Retention is best-effort; a pruning failure must not affect the export that just succeeded.
        }
    }

    private companion object {
        val TimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        const val MaxRetainedExportsPerConferenceTrigger = 20
    }
}

/** Returns the folder that contains automatic markdown safety snapshots. */
fun defaultExportDirectory(home: Path = Path.of(System.getProperty("user.home"))): Path =
    home.resolve(".munassign").resolve("markdown-exports")

private fun String.fileSafeName(): String =
    trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "conference" }
