package com.appmovilidadclinica.driver

import android.app.Application
import com.appmovilidadclinica.driver.data.remote.ApiErrorMapper
import com.appmovilidadclinica.driver.data.remote.KtorApiClient
import com.appmovilidadclinica.driver.data.remote.KtorClientFactory
import com.appmovilidadclinica.driver.data.remote.KtorTokenProvider
import com.appmovilidadclinica.driver.data.repository.AuthRepositoryImpl
import com.appmovilidadclinica.driver.shared.data.local.SessionStore
import com.appmovilidadclinica.driver.shared.di.platformModule
import com.appmovilidadclinica.driver.shared.di.storageModule
import com.appmovilidadclinica.driver.shared.domain.repository.AuthRepository
import org.koin.android.ext.android.inject
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
 * wrapper API HTTP. Se carga en :app. Koin provee [AuthRepository]
 * como `single { AuthRepositoryImpl(get(), get(), get()) }` para
 * que LoginScreen en :shared (multiplatform) pueda inyectarlo via
 * `koinInject<AuthRepository>()`.
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
}
