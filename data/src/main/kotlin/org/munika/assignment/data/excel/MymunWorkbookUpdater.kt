package org.munika.assignment.data.excel

import java.nio.file.Path
import org.munika.assignment.domain.repository.UpdateResult
import org.munika.assignment.domain.model.MymunUpdateFilenameCandidate

/**
 * Coordinates a MyMUN workbook update from file parsing through validated persistence.
 */
class MymunWorkbookUpdater(
    private val repository: MymunSnapshotRepository,
    private val parser: MyMunWorkbookParser = MyMunWorkbookParser(),
) {
    /** Parses [workbookPath] and atomically applies it when staging has no blocking errors. */
    suspend fun applyUpdate(
        conferenceId: String,
        workbookPath: Path,
        signatureToTrust: MymunUpdateFilenameCandidate? = null,
    ): UpdateResult {
        val snapshot = parser.parse(workbookPath)
        return repository.applyMymunUpdateSnapshot(
            conferenceId = conferenceId,
            snapshot = snapshot,
            sourceFilename = workbookPath.fileName.toString(),
            signatureToTrust = signatureToTrust,
        )
    }
}
