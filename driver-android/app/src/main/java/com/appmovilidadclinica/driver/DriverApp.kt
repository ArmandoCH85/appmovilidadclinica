package com.appmovilidadclinica.driver

import android.app.Application
import com.appmovilidadclinica.driver.data.remote.ApiErrorMapper
import com.appmovilidadclinica.driver.data.remote.KtorApiClient
import com.appmovilidadclinica.driver.data.remote.KtorClientFactory
import com.appmovilidadclinica.driver.data.remote.KtorTokenProvider
import com.appmovilidadclinica.driver.data.repository.AuthRepositoryImpl
import com.appmovilidadclinica.driver.data.repository.BookingRepositoryImpl
import com.appmovilidadclinica.driver.data.repository.DriverRepositoryImpl
import com.appmovilidadclinica.driver.shared.data.local.SessionStore
import com.appmovilidadclinica.driver.shared.di.platformModule
import com.appmovilidadclinica.driver.shared.di.storageModule
import com.appmovilidadclinica.driver.shared.domain.repository.AuthRepository
import com.appmovilidadclinica.driver.shared.domain.repository.BookingRepository
import com.appmovilidadclinica.driver.shared.domain.repository.DriverRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class DriverApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@DriverApp)
            modules(
                platformModule,
                storageModule,
                appModule,
            )
        }
    }
}

/**
 * Modulo Android-only: bindings que dependen de OkHttp engine y
 * wrapper API HTTP. Se carga en :app. Koin provee [AuthRepository],
 * [DriverRepository], [BookingRepository] como `single { ... }`
 * para que las screens en :shared (multiplatform) puedan inyectarlas
 * via `koinInject<T>()`.
 */
val appModule = module {
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

