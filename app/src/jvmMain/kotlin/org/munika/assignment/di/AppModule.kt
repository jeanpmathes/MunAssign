package org.munika.assignment.di

import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.munika.assignment.data.db.ConferenceTransferService
import org.munika.assignment.data.db.repository.ConferenceRepositoryImpl
import org.munika.assignment.data.excel.MymunWorkbookUpdater
import org.munika.assignment.domain.repository.ConferenceRepository
import org.munika.assignment.domain.usecase.ListConferencesUseCase
import org.munika.assignment.ui.viewmodel.MainViewModel

/**
 * Application dependency graph.
 */
val appModule: Module = module {
    single { ConferenceRepositoryImpl() } bind ConferenceRepository::class
    single { MymunWorkbookUpdater(get()) }
    single { ConferenceTransferService() }
    single { ListConferencesUseCase(get()) }
    single { MainViewModel(get<ConferenceRepository>(), get<MymunWorkbookUpdater>(), get<ConferenceTransferService>()) }
}
