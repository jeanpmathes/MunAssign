package org.munika.assignment.data.db

import java.nio.file.Path
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId

/**
 * Catalog and filesystem lifecycle boundary for local and synchronized conferences.
 *
 * Ordinary conference reads and mutations remain available through `ConferenceRepository`;
 * this boundary owns only source listing, transfer, and synchronized-file lifecycle operations.
 */
interface ConferenceStorage {
    /** Returns catalog order and refreshed availability without removing unusable links. */
    suspend fun listCatalogEntries(): List<ConferenceCatalogEntry>

    /** Creates and validates a new synchronized file without overwriting an existing target. */
    suspend fun createSynchronizedConference(name: String, destinationPath: Path): Conference

    /** Moves a local conference through a recoverable, verified synchronized-file transition. */
    suspend fun moveConferenceToSynchronized(conferenceId: ConferenceId, destinationPath: Path): Conference

    /** Links a marked single-conference synchronized file after validating both identities. */
    suspend fun connectSynchronizedConference(path: Path): Conference

    /** Updates a missing link only when [path] contains its existing file and conference identities. */
    suspend fun locateSynchronizedConference(conferenceId: ConferenceId, path: Path): Conference

    /** Removes only this installation's catalog reference, leaving the shared file untouched. */
    suspend fun unlinkSynchronizedConference(conferenceId: ConferenceId)

    /** Deletes the shared file before unlinking, retaining the link when deletion fails. */
    suspend fun deleteSynchronizedConferenceFile(conferenceId: ConferenceId)

    /** Reloads the conference directly from its current authoritative database. */
    suspend fun reloadConference(conferenceId: ConferenceId): Conference?

    /** Writes a detached normal conference transfer from either storage kind. */
    suspend fun exportConference(conferenceId: ConferenceId, destinationPath: Path)

    /** Imports a detached local copy, optionally permitting an explicitly overridden synchronized source. */
    suspend fun importConferenceAsNew(sourcePath: Path, allowSynchronizedSource: Boolean): Conference

    /** Replaces conference contents while retaining the target identity and synchronized metadata. */
    suspend fun replaceConference(
        conferenceId: ConferenceId,
        sourcePath: Path,
        allowSynchronizedSource: Boolean,
    ): Conference

    /** Classifies a candidate file read-only from its embedded metadata and schema. */
    fun inspectFile(path: Path): MunAssignFileInspection
}
