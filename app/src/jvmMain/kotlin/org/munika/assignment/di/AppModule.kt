package org.munika.assignment.di

import java.nio.file.Path
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.munika.assignment.AppDataPathResolver
import org.munika.assignment.AppDataPaths
import org.munika.assignment.AppWindowStateStore
import org.munika.assignment.LastSelectedConferenceStore
import org.munika.assignment.data.db.ConferenceAvailability
import org.munika.assignment.data.db.ConferenceCatalogEntry
import org.munika.assignment.data.db.ConferenceCatalogStore
import org.munika.assignment.data.db.ConferenceStorage
import org.munika.assignment.data.db.ConferenceStorageCoordinator
import org.munika.assignment.data.db.ConferenceStorageKind
import org.munika.assignment.data.db.MunAssignFileInspection
import org.munika.assignment.data.db.MunAssignFileKind
import org.munika.assignment.data.excel.MymunWorkbookUpdater
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.repository.ConferenceRepository
import org.munika.assignment.domain.usecase.ListConferencesUseCase
import org.munika.assignment.export.MarkdownSafetyExportService
import org.munika.assignment.export.ConferenceStatusImageClipboard
import org.munika.assignment.ui.viewmodel.ConferenceFileInspection
import org.munika.assignment.ui.viewmodel.ConferenceFileKind
import org.munika.assignment.ui.viewmodel.ConferenceListAvailability
import org.munika.assignment.ui.viewmodel.ConferenceListItem
import org.munika.assignment.ui.viewmodel.ConferenceListStorageKind
import org.munika.assignment.ui.viewmodel.ConferenceTransferRunner
import org.munika.assignment.ui.viewmodel.ConferenceWorkspaceRunner
import org.munika.assignment.ui.viewmodel.MainViewModel
import org.munika.assignment.ui.viewmodel.WorkbookUpdateRunner

/**
 * Application dependency graph.
 */
val appModule: Module = module {
    single<AppDataPaths> { AppDataPathResolver.resolve() }
    single { AppWindowStateStore(get<AppDataPaths>().windowStatePath) }
    single { LastSelectedConferenceStore(get<AppDataPaths>().lastSelectedConferencePath) }
    single {
        val paths = get<AppDataPaths>()
        ConferenceCatalogStore(paths.conferenceCatalogPath, paths.portablePathBase)
    }
    single {
        val paths = get<AppDataPaths>()
        ConferenceStorageCoordinator(
            localDatabasePath = paths.databasePath,
            catalogStore = get(),
            migrationBackupDirectory = paths.synchronizedMigrationBackupDirectory,
        )
    } bind ConferenceRepository::class
    single<ConferenceStorage> { get<ConferenceStorageCoordinator>() }
    single { MymunWorkbookUpdater(get<ConferenceStorageCoordinator>()) }
    single { MarkdownSafetyExportService(get<AppDataPaths>().markdownExportDirectory) }
    single { ConferenceStatusImageClipboard() }
    single { ListConferencesUseCase(get()) }
    single {
        val storage = get<ConferenceStorage>()
        val repository = get<ConferenceRepository>()
        val workbookUpdater = get<MymunWorkbookUpdater>()
        MainViewModel(
            repository = repository,
            workbookUpdater = WorkbookUpdateRunner { conferenceId, path, signatureToTrust ->
                workbookUpdater.applyUpdate(conferenceId, path, signatureToTrust)
            },
            conferenceTransfer = storage.transferRunner(),
            markdownSafetyExport = get<MarkdownSafetyExportService>(),
            lastSelectedConferenceStore = get<LastSelectedConferenceStore>(),
            workspace = storage.workspaceRunner(),
        )
    }
}

private fun ConferenceStorage.transferRunner(): ConferenceTransferRunner = object : ConferenceTransferRunner {
    override suspend fun exportConference(conferenceId: String, destinationPath: Path) {
        this@transferRunner.exportConference(ConferenceId(conferenceId), destinationPath)
    }

    override suspend fun importConferenceAsNew(sourcePath: Path): String =
        this@transferRunner.importConferenceAsNew(sourcePath, allowSynchronizedSource = false).id.value

    override suspend fun replaceConference(targetConferenceId: String, sourcePath: Path): String =
        this@transferRunner.replaceConference(
            ConferenceId(targetConferenceId),
            sourcePath,
            allowSynchronizedSource = false,
        ).id.value
}

private fun ConferenceStorage.workspaceRunner(): ConferenceWorkspaceRunner = object : ConferenceWorkspaceRunner {
    override suspend fun listEntries(): List<ConferenceListItem> =
        this@workspaceRunner.listCatalogEntries().map(ConferenceCatalogEntry::toListItem)

    override suspend fun createSynchronizedConference(name: String, destinationPath: Path): Conference =
        this@workspaceRunner.createSynchronizedConference(name, destinationPath)

    override suspend fun moveConferenceToSynchronized(conferenceId: String, destinationPath: Path): Conference =
        this@workspaceRunner.moveConferenceToSynchronized(ConferenceId(conferenceId), destinationPath)

    override suspend fun connectSynchronizedConference(path: Path): Conference =
        this@workspaceRunner.connectSynchronizedConference(path)

    override suspend fun locateSynchronizedConference(conferenceId: String, path: Path): Conference =
        this@workspaceRunner.locateSynchronizedConference(ConferenceId(conferenceId), path)

    override suspend fun unlinkSynchronizedConference(conferenceId: String) {
        this@workspaceRunner.unlinkSynchronizedConference(ConferenceId(conferenceId))
    }

    override suspend fun deleteSynchronizedConferenceFile(conferenceId: String) {
        this@workspaceRunner.deleteSynchronizedConferenceFile(ConferenceId(conferenceId))
    }

    override suspend fun reloadConference(conferenceId: String): Conference? =
        this@workspaceRunner.reloadConference(ConferenceId(conferenceId))

    override fun inspectFile(path: Path): ConferenceFileInspection =
        this@workspaceRunner.inspectFile(path).toUiInspection()

    override suspend fun importSynchronizedAsNew(path: Path): Conference =
        this@workspaceRunner.importConferenceAsNew(path, allowSynchronizedSource = true)

    override suspend fun replaceFromSynchronized(conferenceId: String, path: Path): Conference =
        this@workspaceRunner.replaceConference(ConferenceId(conferenceId), path, allowSynchronizedSource = true)
}

private fun ConferenceCatalogEntry.toListItem(): ConferenceListItem = ConferenceListItem(
    id = conferenceId,
    name = cachedName,
    displayOrder = displayOrder,
    storageKind = when (storageKind) {
        ConferenceStorageKind.Local -> ConferenceListStorageKind.Local
        ConferenceStorageKind.Synchronized -> ConferenceListStorageKind.Synchronized
    },
    availability = when (availability) {
        ConferenceAvailability.Available -> ConferenceListAvailability.Available
        ConferenceAvailability.Missing -> ConferenceListAvailability.Missing
        ConferenceAvailability.Invalid -> ConferenceListAvailability.Invalid
        ConferenceAvailability.NewerVersion -> ConferenceListAvailability.NewerVersion
        ConferenceAvailability.Inaccessible -> ConferenceListAvailability.Inaccessible
    },
    path = path?.toString(),
    lastLoadedAtEpochMillis = lastLoadedAtEpochMillis,
)

private fun MunAssignFileInspection.toUiInspection(): ConferenceFileInspection = when (this) {
    is MunAssignFileInspection.Valid -> ConferenceFileInspection(
        kind = when (metadata.kind) {
            MunAssignFileKind.ApplicationDatabase -> ConferenceFileKind.ApplicationDatabase
            MunAssignFileKind.ConferenceTransfer -> ConferenceFileKind.ConferenceTransfer
            MunAssignFileKind.SynchronizedConference -> ConferenceFileKind.SynchronizedConference
        },
    )
    is MunAssignFileInspection.LegacyTransfer -> ConferenceFileInspection(ConferenceFileKind.LegacyTransfer)
    is MunAssignFileInspection.Invalid -> ConferenceFileInspection(ConferenceFileKind.Invalid, message)
}
