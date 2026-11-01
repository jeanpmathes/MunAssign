package org.munika.assignment.data.db

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.munika.assignment.data.db.repository.ConferenceRepositoryImpl
import org.munika.assignment.data.excel.MymunSnapshotRepository
import org.munika.assignment.data.excel.MymunWorkbookSnapshot
import org.munika.assignment.domain.model.AnswerIssueResolutionId
import org.munika.assignment.domain.model.CommitteeId
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.DelegateCommentId
import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.DelegationId
import org.munika.assignment.domain.model.MymunUpdateFilenameCandidate
import org.munika.assignment.domain.model.MymunUpdateFilenameSignatureId
import org.munika.assignment.domain.model.PaymentTier
import org.munika.assignment.domain.model.PositionId
import org.munika.assignment.domain.model.QuestionConfiguration
import org.munika.assignment.domain.model.QuestionId
import org.munika.assignment.domain.model.ReservationId
import org.munika.assignment.domain.model.SlotId
import org.munika.assignment.domain.repository.ConferenceRepository
import org.munika.assignment.domain.repository.PreferenceCompletionSelection
import org.munika.assignment.domain.repository.PreferenceCorrectionSelection
import org.munika.assignment.domain.repository.UpdateResult

/**
 * Routes conference persistence to the local application database or a linked synchronized file.
 *
 * The machine-local catalog owns source selection and ordering. Synchronized files remain the
 * authoritative stores for their conference graphs and are never copied into the local database.
 */
class ConferenceStorageCoordinator(
    private val localDatabasePath: Path,
    private val catalogStore: ConferenceCatalogStore,
    private val migrationBackupDirectory: Path,
    private val transferService: ConferenceTransferService = ConferenceTransferService(localDatabasePath),
    private val inspector: MunAssignFileInspector = MunAssignFileInspector(),
) : ConferenceRepository, MymunSnapshotRepository, ConferenceStorage {
    private val localRepository = ConferenceRepositoryImpl(localDatabasePath)
    private val synchronizedRepositories = mutableMapOf<Path, ConferenceRepositoryImpl>()
    private val initializationMutex = Mutex()
    private var initialized = false
    private var catalog = ConferenceCatalog()

    /** Returns ordered source entries, refreshing synchronized availability without removing links. */
    override suspend fun listCatalogEntries(): List<ConferenceCatalogEntry> {
        ensureInitialized()
        refreshSynchronizedAvailability()
        return catalog.entries.sortedBy { it.displayOrder }
    }

    /** Creates a new authoritative synchronized conference at a path that must not already exist. */
    override suspend fun createSynchronizedConference(name: String, destinationPath: Path): Conference {
        ensureInitialized()
        val destination = destinationPath.toAbsolutePath().normalize()
        require(!Files.exists(destination)) { "A file already exists at the selected synchronized location." }
        destination.parent?.let(Files::createDirectories)
        val temporaryPath = newTemporaryDatabasePath(destination)
        try {
            val database = DatabaseFactory.connect(temporaryPath)
            val repository = ConferenceRepositoryImpl(database)
            val created = repository.createConference(name)
            TransactionManager.closeAndUnregister(database)
            val metadata = MunAssignFileMetadataStore(temporaryPath)
                .setKind(MunAssignFileKind.SynchronizedConference)
            moveAtomically(temporaryPath, destination)
            appendSynchronizedEntry(created, metadata.fileId, destination)
            synchronizedRepositories[destination] = ConferenceRepositoryImpl(destination)
            return created
        } catch (exception: Exception) {
            Files.deleteIfExists(temporaryPath)
            throw exception
        }
    }

    /** Moves one local conference into a verified synchronized file with recoverable catalog state. */
    override suspend fun moveConferenceToSynchronized(conferenceId: ConferenceId, destinationPath: Path): Conference {
        ensureInitialized()
        val entry = requireEntry(conferenceId)
        require(entry.storageKind == ConferenceStorageKind.Local) { "Only local conferences can be moved." }
        val destination = destinationPath.toAbsolutePath().normalize()
        require(!Files.exists(destination)) { "A file already exists at the selected synchronized location." }
        destination.parent?.let(Files::createDirectories)
        val temporaryPath = newTemporaryDatabasePath(destination)
        try {
            transferService.exportConference(localDatabasePath, conferenceId.value, temporaryPath)
            val metadata = MunAssignFileMetadataStore(temporaryPath)
                .setKind(MunAssignFileKind.SynchronizedConference)
            moveAtomically(temporaryPath, destination)
            catalog = catalog.copy(
                pendingMove = PendingConferenceMove(conferenceId.value, destination, metadata.fileId),
            ).also(catalogStore::save)
            recoverPendingMove()
            return requireNotNull(getConference(conferenceId))
        } catch (exception: Exception) {
            Files.deleteIfExists(temporaryPath)
            throw exception
        }
    }

    /** Connects an existing synchronized conference without copying its contents. */
    override suspend fun connectSynchronizedConference(path: Path): Conference {
        ensureInitialized()
        val prepared = prepareSynchronizedFile(path.toAbsolutePath().normalize(), expectedFileId = null)
        require(catalog.entries.none { it.fileId == prepared.metadata.fileId }) {
            "This synchronized conference is already connected. Use Locate to change its path."
        }
        require(catalog.entries.none { it.conferenceId == prepared.conferenceId }) {
            "A conference with this identity is already present. Import a detached copy instead."
        }
        val repository = repositoryAt(prepared.path)
        val conference = requireNotNull(repository.getConference(ConferenceId(prepared.conferenceId)))
        appendSynchronizedEntry(conference, prepared.metadata.fileId, prepared.path)
        return conference
    }

    /** Changes a synchronized link only when the selected file has the expected stable file UUID. */
    override suspend fun locateSynchronizedConference(conferenceId: ConferenceId, path: Path): Conference {
        ensureInitialized()
        val current = requireEntry(conferenceId)
        require(current.storageKind == ConferenceStorageKind.Synchronized) { "Only synchronized conferences can be located." }
        val prepared = prepareSynchronizedFile(path.toAbsolutePath().normalize(), requireNotNull(current.fileId))
        require(prepared.conferenceId == conferenceId.value) { "The selected file contains a different conference." }
        val conference = requireNotNull(repositoryAt(prepared.path).getConference(conferenceId))
        replaceEntry(
            current.copy(
                cachedName = conference.name,
                path = prepared.path,
                availability = ConferenceAvailability.Available,
                lastLoadedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        return conference
    }

    /** Removes a synchronized link from this installation without modifying the shared file. */
    override suspend fun unlinkSynchronizedConference(conferenceId: ConferenceId) {
        ensureInitialized()
        val entry = requireEntry(conferenceId)
        require(entry.storageKind == ConferenceStorageKind.Synchronized) { "Only synchronized conferences can be unlinked." }
        catalog = catalog.copy(entries = normalizedEntries(catalog.entries.filterNot { it.conferenceId == conferenceId.value }))
            .also(catalogStore::save)
    }

    /** Deletes the shared file and then removes its catalog link; failures retain the link. */
    override suspend fun deleteSynchronizedConferenceFile(conferenceId: ConferenceId) {
        ensureInitialized()
        val entry = requireEntry(conferenceId)
        require(entry.storageKind == ConferenceStorageKind.Synchronized) { "Only synchronized conference files can be deleted." }
        Files.delete(requireNotNull(entry.path))
        unlinkSynchronizedConference(conferenceId)
    }

    /** Reloads a conference from its authoritative source and updates cached synchronized status. */
    override suspend fun reloadConference(conferenceId: ConferenceId): Conference? = getConference(conferenceId)

    /** Exports either storage kind as a detached normal transfer. */
    override suspend fun exportConference(conferenceId: ConferenceId, destinationPath: Path) {
        ensureInitialized()
        transferService.exportConference(databasePathFor(requireEntry(conferenceId)), conferenceId.value, destinationPath)
    }

    /** Imports a detached conference into local storage and appends it to the catalog. */
    override suspend fun importConferenceAsNew(sourcePath: Path, allowSynchronizedSource: Boolean): Conference {
        ensureInitialized()
        val importedId = transferService.importConferenceAsNew(localDatabasePath, sourcePath, allowSynchronizedSource)
        val imported = requireNotNull(localRepository.getConference(ConferenceId(importedId)))
        appendLocalEntry(imported)
        return imported
    }

    /** Replaces local or synchronized contents while preserving the target identity and file marker. */
    override suspend fun replaceConference(
        conferenceId: ConferenceId,
        sourcePath: Path,
        allowSynchronizedSource: Boolean,
    ): Conference {
        ensureInitialized()
        val entry = requireEntry(conferenceId)
        if (entry.storageKind == ConferenceStorageKind.Synchronized) {
            prepareSynchronizedFile(requireNotNull(entry.path), requireNotNull(entry.fileId))
        }
        transferService.replaceConference(
            targetDatabasePath = databasePathFor(entry),
            targetConferenceId = conferenceId.value,
            sourcePath = sourcePath,
            allowSynchronizedSource = allowSynchronizedSource,
        )
        val replaced = requireNotNull(repositoryFor(entry).getConference(conferenceId))
        replaceEntry(entry.copy(cachedName = replaced.name, availability = ConferenceAvailability.Available))
        return replaced
    }

    override fun inspectFile(path: Path): MunAssignFileInspection = inspector.inspect(path)

    override suspend fun listConferences(): List<Conference> {
        ensureInitialized()
        val localById = localRepository.listConferences().associateBy { it.id.value }
        return catalog.entries.sortedBy { it.displayOrder }.mapNotNull { entry ->
            when (entry.storageKind) {
                ConferenceStorageKind.Local -> localById[entry.conferenceId]
                ConferenceStorageKind.Synchronized -> runCatching {
                    repositoryFor(entry).listConferences().singleOrNull()
                }.getOrNull()
            }
        }
    }

    override suspend fun createConference(name: String): Conference {
        ensureInitialized()
        return localRepository.createConference(name).also { appendLocalEntry(it) }
    }

    override suspend fun renameConference(conferenceId: ConferenceId, newName: String): Conference =
        routed(conferenceId) { renameConference(conferenceId, newName) }.also { renamed ->
            replaceEntry(requireEntry(conferenceId).copy(cachedName = renamed.name))
        }

    override suspend fun deleteConference(conferenceId: ConferenceId) {
        ensureInitialized()
        val entry = requireEntry(conferenceId)
        require(entry.storageKind == ConferenceStorageKind.Local) {
            "Choose Unlink or Delete shared file for a synchronized conference."
        }
        localRepository.deleteConference(conferenceId)
        catalog = catalog.copy(entries = normalizedEntries(catalog.entries.filterNot { it.conferenceId == conferenceId.value }))
            .also(catalogStore::save)
    }

    override suspend fun reorderConferences(conferenceIdsInOrder: List<ConferenceId>) {
        ensureInitialized()
        val ids = conferenceIdsInOrder.map { it.value }
        require(ids.toSet() == catalog.entries.map { it.conferenceId }.toSet() && ids.size == catalog.entries.size) {
            "Conference reorder must include every known conference exactly once."
        }
        val byId = catalog.entries.associateBy { it.conferenceId }
        catalog = catalog.copy(entries = ids.mapIndexed { index, id -> requireNotNull(byId[id]).copy(displayOrder = index) })
            .also(catalogStore::save)
        val localIds = ids.filter { byId[it]?.storageKind == ConferenceStorageKind.Local }.map(::ConferenceId)
        localRepository.reorderConferences(localIds)
    }

    override suspend fun deleteMymunUpdateFilenameSignature(
        conferenceId: ConferenceId,
        signatureId: MymunUpdateFilenameSignatureId,
    ): Conference = routed(conferenceId) { deleteMymunUpdateFilenameSignature(conferenceId, signatureId) }

    override suspend fun setCommitteeSlotsPerPosition(
        conferenceId: ConferenceId,
        committeeId: CommitteeId,
        slotsPerPosition: Int,
    ): Conference = routed(conferenceId) { setCommitteeSlotsPerPosition(conferenceId, committeeId, slotsPerPosition) }

    override suspend fun setCommitteeHighSchoolStatus(
        conferenceId: ConferenceId,
        committeeId: CommitteeId,
        isHighSchoolCommittee: Boolean,
    ): Conference = routed(conferenceId) {
        setCommitteeHighSchoolStatus(conferenceId, committeeId, isHighSchoolCommittee)
    }

    override suspend fun setCommitteeMode(
        conferenceId: ConferenceId,
        committeeId: CommitteeId,
        mode: CommitteeMode,
    ): Conference = routed(conferenceId) { setCommitteeMode(conferenceId, committeeId, mode) }

    override suspend fun setCommitteePaymentTier(
        conferenceId: ConferenceId,
        committeeId: CommitteeId,
        paymentTier: PaymentTier,
    ): Conference = routed(conferenceId) {
        setCommitteePaymentTier(conferenceId, committeeId, paymentTier)
    }

    override suspend fun setDelegatePaymentTier(
        conferenceId: ConferenceId,
        delegateId: DelegateId,
        paymentTier: PaymentTier,
    ): Conference = routed(conferenceId) {
        setDelegatePaymentTier(conferenceId, delegateId, paymentTier)
    }

    override suspend fun setPositionMappedCommittee(
        conferenceId: ConferenceId,
        positionId: PositionId,
        mappedCommitteeId: CommitteeId?,
    ): Conference = routed(conferenceId) {
        setPositionMappedCommittee(conferenceId, positionId, mappedCommitteeId)
    }

    override suspend fun setDelegationHighSchoolStatus(
        conferenceId: ConferenceId,
        delegationId: DelegationId,
        isHighSchoolDelegation: Boolean,
    ): Conference = routed(conferenceId) {
        setDelegationHighSchoolStatus(conferenceId, delegationId, isHighSchoolDelegation)
    }

    override suspend fun markDelegationsReviewed(conferenceId: ConferenceId): Conference =
        routed(conferenceId) { markDelegationsReviewed(conferenceId) }

    override suspend fun assignDelegateToSlot(
        conferenceId: ConferenceId,
        slotId: SlotId,
        delegateId: DelegateId,
        moveExistingPending: Boolean,
    ): Conference = routed(conferenceId) {
        assignDelegateToSlot(conferenceId, slotId, delegateId, moveExistingPending)
    }

    override suspend fun swapPendingAssignments(
        conferenceId: ConferenceId,
        sourceSlotId: SlotId,
        targetSlotId: SlotId,
        expectedSourceDelegateId: DelegateId,
        expectedTargetDelegateId: DelegateId,
    ): Conference = routed(conferenceId) {
        swapPendingAssignments(
            conferenceId,
            sourceSlotId,
            targetSlotId,
            expectedSourceDelegateId,
            expectedTargetDelegateId,
        )
    }

    override suspend fun setPositionPendingAssignments(
        conferenceId: ConferenceId,
        positionId: PositionId,
        selectedDelegateIds: List<DelegateId>,
    ): Conference = routed(conferenceId) {
        setPositionPendingAssignments(conferenceId, positionId, selectedDelegateIds)
    }

    override suspend fun clearPendingSlot(conferenceId: ConferenceId, slotId: SlotId): Conference =
        routed(conferenceId) { clearPendingSlot(conferenceId, slotId) }

    override suspend fun deactivateDelegate(conferenceId: ConferenceId, delegateId: DelegateId): Conference =
        routed(conferenceId) { deactivateDelegate(conferenceId, delegateId) }

    override suspend fun reactivateDelegate(conferenceId: ConferenceId, delegateId: DelegateId): Conference =
        routed(conferenceId) { reactivateDelegate(conferenceId, delegateId) }

    override suspend fun createReservation(
        conferenceId: ConferenceId,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: DelegationId?,
    ): Conference = routed(conferenceId) {
        createReservation(conferenceId, name, highSchoolCount, universityCount, delegationId)
    }

    override suspend fun updateReservation(
        conferenceId: ConferenceId,
        reservationId: ReservationId,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: DelegationId?,
    ): Conference = routed(conferenceId) {
        updateReservation(conferenceId, reservationId, name, highSchoolCount, universityCount, delegationId)
    }

    override suspend fun deleteReservation(
        conferenceId: ConferenceId,
        reservationId: ReservationId,
    ): Conference = routed(conferenceId) { deleteReservation(conferenceId, reservationId) }

    override suspend fun assignDelegateReservation(
        conferenceId: ConferenceId,
        delegateId: DelegateId,
        reservationId: ReservationId,
    ): Conference = routed(conferenceId) { assignDelegateReservation(conferenceId, delegateId, reservationId) }

    override suspend fun removeDelegateReservation(
        conferenceId: ConferenceId,
        delegateId: DelegateId,
    ): Conference = routed(conferenceId) { removeDelegateReservation(conferenceId, delegateId) }

    override suspend fun addDelegateComment(
        conferenceId: ConferenceId,
        delegateId: DelegateId,
        body: String,
    ): Conference = routed(conferenceId) { addDelegateComment(conferenceId, delegateId, body) }

    override suspend fun updateDelegateComment(
        conferenceId: ConferenceId,
        commentId: DelegateCommentId,
        body: String,
    ): Conference = routed(conferenceId) { updateDelegateComment(conferenceId, commentId, body) }

    override suspend fun deleteDelegateComment(
        conferenceId: ConferenceId,
        commentId: DelegateCommentId,
    ): Conference = routed(conferenceId) { deleteDelegateComment(conferenceId, commentId) }

    override suspend fun updateQuestionConfiguration(
        conferenceId: ConferenceId,
        configuration: QuestionConfiguration,
    ): Conference = routed(conferenceId) { updateQuestionConfiguration(conferenceId, configuration) }

    override suspend fun updateQuestionConfigurations(
        conferenceId: ConferenceId,
        configurations: List<QuestionConfiguration>,
    ): Conference = routed(conferenceId) { updateQuestionConfigurations(conferenceId, configurations) }

    override suspend fun reorderQuestionConfigurations(
        conferenceId: ConferenceId,
        questionIdsInDisplayOrder: List<QuestionId>,
    ): Conference = routed(conferenceId) { reorderQuestionConfigurations(conferenceId, questionIdsInDisplayOrder) }

    override suspend fun resolveAnswerIssue(
        conferenceId: ConferenceId,
        delegateId: DelegateId,
        questionId: QuestionId?,
        issueKey: String,
        justification: String?,
    ): Conference = routed(conferenceId) {
        resolveAnswerIssue(conferenceId, delegateId, questionId, issueKey, justification)
    }

    override suspend fun updateAnswerIssueResolutionJustification(
        conferenceId: ConferenceId,
        resolutionId: AnswerIssueResolutionId,
        justification: String?,
    ): Conference = routed(conferenceId) {
        updateAnswerIssueResolutionJustification(conferenceId, resolutionId, justification)
    }

    override suspend fun unresolveAnswerIssue(
        conferenceId: ConferenceId,
        resolutionId: AnswerIssueResolutionId,
    ): Conference = routed(conferenceId) { unresolveAnswerIssue(conferenceId, resolutionId) }

    override suspend fun savePreferenceCompletions(
        conferenceId: ConferenceId,
        selections: List<PreferenceCompletionSelection>,
    ): Conference = routed(conferenceId) { savePreferenceCompletions(conferenceId, selections) }

    override suspend fun clearPreferenceCompletion(
        conferenceId: ConferenceId,
        delegateId: DelegateId,
        rank: Int,
    ): Conference = routed(conferenceId) { clearPreferenceCompletion(conferenceId, delegateId, rank) }

    override suspend fun savePreferenceCorrections(
        conferenceId: ConferenceId,
        selections: List<PreferenceCorrectionSelection>,
    ): Conference = routed(conferenceId) { savePreferenceCorrections(conferenceId, selections) }

    override suspend fun resetPreferenceCorrections(
        conferenceId: ConferenceId,
    ): Conference = routed(conferenceId) { resetPreferenceCorrections(conferenceId) }

    override suspend fun getConference(conferenceId: ConferenceId): Conference? {
        ensureInitialized()
        val entry = catalog.entries.firstOrNull { it.conferenceId == conferenceId.value } ?: return null
        return try {
            val conference = repositoryFor(entry).getConference(conferenceId)
            if (entry.storageKind == ConferenceStorageKind.Synchronized && conference != null) {
                replaceEntry(
                    entry.copy(
                        cachedName = conference.name,
                        availability = ConferenceAvailability.Available,
                        lastLoadedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }
            conference
        } catch (exception: Exception) {
            if (entry.storageKind == ConferenceStorageKind.Synchronized) {
                markUnavailable(entry, availabilityFor(exception))
                null
            } else {
                throw exception
            }
        }
    }

    override suspend fun applyMymunUpdateSnapshot(
        conferenceId: String,
        snapshot: MymunWorkbookSnapshot,
        sourceFilename: String?,
        signatureToTrust: MymunUpdateFilenameCandidate?,
    ): UpdateResult {
        val id = ConferenceId(conferenceId)
        val repository = repositoryFor(requireEntryAfterInitialization(id))
        return repository.applyMymunUpdateSnapshot(conferenceId, snapshot, sourceFilename, signatureToTrust)
    }

    private suspend fun <T> routed(
        conferenceId: ConferenceId,
        action: suspend ConferenceRepository.() -> T,
    ): T = repositoryFor(requireEntryAfterInitialization(conferenceId)).action()

    private suspend fun requireEntryAfterInitialization(conferenceId: ConferenceId): ConferenceCatalogEntry {
        ensureInitialized()
        return requireEntry(conferenceId)
    }

    private suspend fun ensureInitialized() {
        if (initialized) return
        initializationMutex.withLock {
            if (initialized) return
            catalog = catalogStore.load()
            recoverPendingMove()
            reconcileLocalEntries()
            initialized = true
        }
    }

    private suspend fun reconcileLocalEntries() {
        val localConferences = localRepository.listConferences()
        val localIds = localConferences.map { it.id.value }.toSet()
        val retained = catalog.entries.filterNot {
            it.storageKind == ConferenceStorageKind.Local && it.conferenceId !in localIds
        }.toMutableList()
        localConferences.forEach { conference ->
            if (retained.none { it.conferenceId == conference.id.value }) {
                retained += ConferenceCatalogEntry.local(
                    conference.id.value,
                    conference.name,
                    retained.size,
                )
            }
        }
        val reconciled = catalog.copy(entries = normalizedEntries(retained))
        if (reconciled != catalog) {
            catalog = reconciled.also(catalogStore::save)
        }
    }

    private suspend fun recoverPendingMove() {
        val pending = catalog.pendingMove ?: return
        val inspection = inspector.inspect(pending.synchronizedPath)
        if (
            inspection is MunAssignFileInspection.Valid &&
            inspection.metadata.kind == MunAssignFileKind.SynchronizedConference &&
            inspection.metadata.fileId == pending.fileId &&
            inspection.conferenceId == pending.conferenceId
        ) {
            val current = catalog.entries.firstOrNull { it.conferenceId == pending.conferenceId }
            val replacement = ConferenceCatalogEntry.synchronized(
                conferenceId = pending.conferenceId,
                cachedName = inspection.conferenceName ?: current?.cachedName ?: "Synchronized conference",
                displayOrder = current?.displayOrder ?: catalog.entries.size,
                fileId = pending.fileId,
                path = pending.synchronizedPath,
                lastLoadedAtEpochMillis = System.currentTimeMillis(),
            )
            val entries = catalog.entries.filterNot { it.conferenceId == pending.conferenceId } + replacement
            catalog = catalog.copy(entries = normalizedEntries(entries), pendingMove = pending).also(catalogStore::save)
            if (localRepository.getConference(ConferenceId(pending.conferenceId)) != null) {
                localRepository.deleteConference(ConferenceId(pending.conferenceId))
            }
        }
        catalog = catalog.copy(pendingMove = null).also(catalogStore::save)
    }

    private suspend fun refreshSynchronizedAvailability() {
        val refreshed = catalog.entries.map { entry ->
            if (entry.storageKind != ConferenceStorageKind.Synchronized) return@map entry
            when (val inspection = inspector.inspect(requireNotNull(entry.path))) {
                is MunAssignFileInspection.Invalid -> entry.copy(
                    availability = when {
                        inspection.inaccessible -> ConferenceAvailability.Inaccessible
                        Files.exists(requireNotNull(entry.path)) -> ConferenceAvailability.Invalid
                        else -> ConferenceAvailability.Missing
                    },
                )
                is MunAssignFileInspection.LegacyTransfer -> entry.copy(availability = ConferenceAvailability.Invalid)
                is MunAssignFileInspection.Valid -> when {
                    inspection.schemaVersion > DatabaseFactory.CURRENT_SCHEMA_VERSION ->
                        entry.copy(availability = ConferenceAvailability.NewerVersion)
                    inspection.metadata.kind != MunAssignFileKind.SynchronizedConference ||
                        inspection.metadata.fileId != entry.fileId || inspection.conferenceId != entry.conferenceId ->
                        entry.copy(availability = ConferenceAvailability.Invalid)
                    else -> entry.copy(availability = ConferenceAvailability.Available)
                }
            }
        }
        if (refreshed != catalog.entries) {
            catalog = catalog.copy(entries = refreshed).also(catalogStore::save)
        }
    }

    private suspend fun prepareSynchronizedFile(path: Path, expectedFileId: String?): PreparedSynchronizedFile {
        val initial = inspector.inspect(path)
        val valid = initial as? MunAssignFileInspection.Valid ?: throw IllegalArgumentException(
            when (initial) {
                is MunAssignFileInspection.Invalid -> initial.message
                is MunAssignFileInspection.LegacyTransfer -> "The selected file is a normal legacy transfer, not a synchronized conference."
                else -> "The selected file is not a synchronized conference."
            },
        )
        require(valid.metadata.kind == MunAssignFileKind.SynchronizedConference) {
            "The selected file is not a synchronized conference. Import it instead."
        }
        require(valid.conferenceCount == 1 && valid.conferenceId != null && valid.conferenceName != null) {
            "Synchronized conference files must contain exactly one conference."
        }
        require(expectedFileId == null || valid.metadata.fileId == expectedFileId) {
            "The selected file is a different synchronized conference."
        }
        require(valid.schemaVersion <= DatabaseFactory.CURRENT_SCHEMA_VERSION) {
            "This synchronized conference was created by a newer MunAssign version."
        }
        if (valid.schemaVersion < DatabaseFactory.CURRENT_SCHEMA_VERSION) {
            Files.createDirectories(migrationBackupDirectory)
            val backup = migrationBackupDirectory.resolve(
                "${valid.metadata.fileId}-${System.currentTimeMillis()}.sync.munassign",
            )
            Files.copy(path, backup, StandardCopyOption.COPY_ATTRIBUTES)
            TransactionManager.closeAndUnregister(DatabaseFactory.connect(path))
        }
        val migrated = inspector.inspect(path) as? MunAssignFileInspection.Valid
            ?: error("The synchronized conference could not be read after migration.")
        return PreparedSynchronizedFile(
            path = path,
            metadata = migrated.metadata,
            conferenceId = requireNotNull(migrated.conferenceId),
        )
    }

    private suspend fun repositoryFor(entry: ConferenceCatalogEntry): ConferenceRepositoryImpl =
        when (entry.storageKind) {
            ConferenceStorageKind.Local -> localRepository
            ConferenceStorageKind.Synchronized -> {
                val prepared = prepareSynchronizedFile(requireNotNull(entry.path), requireNotNull(entry.fileId))
                require(prepared.conferenceId == entry.conferenceId) { "The synchronized file identity no longer matches its link." }
                repositoryAt(prepared.path)
            }
        }

    private fun repositoryAt(path: Path): ConferenceRepositoryImpl =
        synchronizedRepositories.getOrPut(path.toAbsolutePath().normalize()) { ConferenceRepositoryImpl(path) }

    private fun databasePathFor(entry: ConferenceCatalogEntry): Path =
        if (entry.storageKind == ConferenceStorageKind.Local) localDatabasePath else requireNotNull(entry.path)

    private fun requireEntry(conferenceId: ConferenceId): ConferenceCatalogEntry =
        requireNotNull(catalog.entries.firstOrNull { it.conferenceId == conferenceId.value }) {
            "Conference ${conferenceId.value} is not present in the conference catalog."
        }

    private fun appendLocalEntry(conference: Conference) {
        val entry = ConferenceCatalogEntry.local(conference.id.value, conference.name, catalog.entries.size)
        catalog = catalog.copy(entries = normalizedEntries(catalog.entries + entry)).also(catalogStore::save)
    }

    private fun appendSynchronizedEntry(conference: Conference, fileId: String, path: Path) {
        val entry = ConferenceCatalogEntry.synchronized(
            conferenceId = conference.id.value,
            cachedName = conference.name,
            displayOrder = catalog.entries.size,
            fileId = fileId,
            path = path,
            lastLoadedAtEpochMillis = System.currentTimeMillis(),
        )
        catalog = catalog.copy(entries = normalizedEntries(catalog.entries + entry)).also(catalogStore::save)
    }

    private fun replaceEntry(replacement: ConferenceCatalogEntry) {
        catalog = catalog.copy(
            entries = catalog.entries.map { if (it.conferenceId == replacement.conferenceId) replacement else it },
        ).also(catalogStore::save)
    }

    private fun markUnavailable(entry: ConferenceCatalogEntry, availability: ConferenceAvailability) {
        replaceEntry(entry.copy(availability = availability))
    }

    private fun availabilityFor(exception: Exception): ConferenceAvailability = when {
        exception is java.nio.file.NoSuchFileException || exception is java.io.FileNotFoundException ->
            ConferenceAvailability.Missing
        exception is SecurityException || exception is java.nio.file.AccessDeniedException ->
            ConferenceAvailability.Inaccessible
        exception.message.orEmpty().contains("newer MunAssign") -> ConferenceAvailability.NewerVersion
        else -> ConferenceAvailability.Invalid
    }

    private fun normalizedEntries(entries: List<ConferenceCatalogEntry>): List<ConferenceCatalogEntry> =
        entries.sortedBy { it.displayOrder }.mapIndexed { index, entry -> entry.copy(displayOrder = index) }

    private fun newTemporaryDatabasePath(destination: Path): Path {
        val temporary = Files.createTempFile(destination.parent, "munassign-sync-", ".tmp")
        Files.delete(temporary)
        return temporary
    }

    private fun moveAtomically(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private data class PreparedSynchronizedFile(
        val path: Path,
        val metadata: MunAssignFileMetadata,
        val conferenceId: String,
    )
}
