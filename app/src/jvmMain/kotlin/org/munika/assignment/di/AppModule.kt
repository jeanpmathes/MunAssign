package org.munika.assignment.di

import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.munika.assignment.AppDataPathResolver
import org.munika.assignment.AppDataPaths
import org.munika.assignment.AppWindowStateStore
import org.munika.assignment.LastSelectedConferenceStore
import org.munika.assignment.data.db.ConferenceTransferService
import org.munika.assignment.data.db.repository.ConferenceRepositoryImpl
import org.munika.assignment.data.excel.MymunWorkbookUpdater
import org.munika.assignment.domain.repository.ConferenceRepository
import org.munika.assignment.domain.usecase.ListConferencesUseCase
import org.munika.assignment.export.MarkdownSafetyExportService
import org.munika.assignment.export.ConferenceStatusImageClipboard
import org.munika.assignment.ui.viewmodel.MainViewModel

/**
 * Application dependency graph.
 */
val appModule: Module = module {
    single<AppDataPaths> { AppDataPathResolver.resolve() }
    single { AppWindowStateStore(get<AppDataPaths>().windowStatePath) }
    single { LastSelectedConferenceStore(get<AppDataPaths>().lastSelectedConferencePath) }
    single { ConferenceRepositoryImpl(get<AppDataPaths>().databasePath) } bind ConferenceRepository::class
    single { MymunWorkbookUpdater(get()) }
    single { ConferenceTransferService(get<AppDataPaths>().databasePath) }
    single { MarkdownSafetyExportService(get<AppDataPaths>().markdownExportDirectory) }
    single { ConferenceStatusImageClipboard() }
    single { ListConferencesUseCase(get()) }
    single {
        MainViewModel(
            get<ConferenceRepository>(),
            get<MymunWorkbookUpdater>(),
            get<ConferenceTransferService>(),
            get<MarkdownSafetyExportService>(),
            get<LastSelectedConferenceStore>(),
        )
    }
}
