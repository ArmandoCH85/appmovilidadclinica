package com.appmovilidadclinica.driver.shared.di

import com.appmovilidadclinica.driver.shared.data.remote.ApiErrorMapper
import com.appmovilidadclinica.driver.shared.data.remote.KtorApiClient
import com.appmovilidadclinica.driver.shared.data.remote.KtorClientFactory
import com.appmovilidadclinica.driver.shared.data.remote.KtorTokenProvider
import com.appmovilidadclinica.driver.shared.data.repository.AuthRepositoryImpl
import com.appmovilidadclinica.driver.shared.data.repository.BookingRepositoryImpl
import com.appmovilidadclinica.driver.shared.data.repository.DriverRepositoryImpl
import com.appmovilidadclinica.driver.shared.data.local.SessionStore
import com.appmovilidadclinica.driver.shared.domain.repository.AuthRepository
import com.appmovilidadclinica.driver.shared.domain.repository.BookingRepository
import com.appmovilidadclinica.driver.shared.domain.repository.DriverRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Repos Android-only (Ktor con OkHttp engine, mappers Java).
 * Cargado por [androidModule] cuando se compila target Android.
 *
 * En iOS los repos viven como stub (no compilan todavia las impls;
 * ver [iosDataModule] abajo).
 */
val androidDataModule: Module = module {
    single {
        KtorTokenProvider { (get<SessionStore>()).currentToken() }
    }
    single {
        KtorApiClient(
            KtorClientFactory.create(tokenProvider = get()),
        )
    }
    single { ApiErrorMapper() }
    single<AuthRepository> {
        AuthRepositoryImpl(
            apiClient = get(),
            sessionStore = get(),
            apiErrorMapper = get(),
        )
    }
    single<DriverRepository> {
        DriverRepositoryImpl(
            apiClient = get(),
            apiErrorMapper = get(),
        )
    }
    single<BookingRepository> {
        BookingRepositoryImpl(
            apiClient = get(),
            apiErrorMapper = get(),
        )
    }
}
