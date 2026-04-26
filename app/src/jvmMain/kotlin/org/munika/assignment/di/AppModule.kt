package org.munika.assignment.di

import org.koin.dsl.module
import org.munika.assignment.data.db.repository.ConferenceRepositoryImpl
import org.munika.assignment.domain.repository.ConferenceRepository
import org.munika.assignment.domain.usecase.ListConferencesUseCase

val appModule = module {
    single<ConferenceRepository> { ConferenceRepositoryImpl() }
    single { ListConferencesUseCase(get()) }
}
