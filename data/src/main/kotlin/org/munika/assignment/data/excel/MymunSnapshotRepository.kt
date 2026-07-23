package org.munika.assignment.data.excel

import org.munika.assignment.domain.model.MymunUpdateFilenameCandidate
import org.munika.assignment.domain.repository.UpdateResult

/** Persists one parsed MyMUN snapshot into the storage that owns a conference. */
interface MymunSnapshotRepository {
    /** Applies a validated staging snapshot transactionally to the conference's authoritative store. */
    suspend fun applyMymunUpdateSnapshot(
        conferenceId: String,
        snapshot: MymunWorkbookSnapshot,
        sourceFilename: String? = snapshot.workbookName,
        signatureToTrust: MymunUpdateFilenameCandidate? = null,
    ): UpdateResult
}
