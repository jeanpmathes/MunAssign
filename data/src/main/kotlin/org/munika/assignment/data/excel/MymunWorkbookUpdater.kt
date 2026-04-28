package org.munika.assignment.data.excel

import java.nio.file.Path
import org.munika.assignment.data.db.repository.ConferenceRepositoryImpl
import org.munika.assignment.domain.repository.UpdateResult

/**
 * Coordinates a MyMUN workbook update from file parsing through validated persistence.
 */
class MymunWorkbookUpdater(
    private val repository: ConferenceRepositoryImpl,
    private val parser: MyMunWorkbookParser = MyMunWorkbookParser(),
) {
    suspend fun applyUpdate(conferenceId: String, workbookPath: Path): UpdateResult {
        val snapshot = parser.parse(workbookPath)
        return repository.applyMymunUpdateSnapshot(
            conferenceId = conferenceId,
            snapshot = snapshot,
            sourceFilename = workbookPath.fileName.toString(),
        )
    }
}
