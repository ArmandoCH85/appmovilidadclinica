package com.appmovilidadclinica.driver.di

import android.content.Context
import com.appmovilidadclinica.driver.data.remote.ApiErrorMapper
import com.appmovilidadclinica.driver.data.remote.KtorApiClient
import com.appmovilidadclinica.driver.data.remote.KtorClientFactory
import com.appmovilidadclinica.driver.data.remote.KtorTokenProvider
import com.appmovilidadclinica.driver.data.repository.AuthRepositoryImpl
import com.appmovilidadclinica.driver.data.repository.BookingRepositoryImpl
import com.appmovilidadclinica.driver.data.repository.DriverRepositoryImpl
import com.appmovilidadclinica.driver.shared.domain.repository.AuthRepository
import com.appmovilidadclinica.driver.shared.domain.repository.BookingRepository
import com.appmovilidadclinica.driver.domain.repository.DriverRepository
import com.appmovilidadclinica.driver.shared.data.local.SessionStore
import io.ktor.client.HttpClient

object AppModule {

    private lateinit var appContext: Context
    private lateinit var sessionStore: SessionStore

    fun initialize(context: Context, sessionStore: SessionStore) {
        appContext = context.applicationContext
        this.sessionStore = sessionStore
    }

    fun provideContext(): Context = appContext

    private fun provideKtorTokenProvider(): KtorTokenProvider =
        KtorTokenProvider { sessionStore.currentToken() }

    private fun provideHttpClient(): HttpClient =
        KtorClientFactory.create(tokenProvider = provideKtorTokenProvider())

    private fun provideKtorApiClient(): KtorApiClient = KtorApiClient(provideHttpClient())

    fun provideAuthRepository(): AuthRepository =
        AuthRepositoryImpl(
            apiClient = provideKtorApiClient(),
            sessionStore = sessionStore,
            apiErrorMapper = ApiErrorMapper(),
        )

    fun provideDriverRepository(): DriverRepository =
        DriverRepositoryImpl(
            apiClient = provideKtorApiClient(),
            apiErrorMapper = ApiErrorMapper(),
        )

    fun provideBookingRepository(): BookingRepository =
        BookingRepositoryImpl(
            apiClient = provideKtorApiClient(),
            apiErrorMapper = ApiErrorMapper(),
        )
}
