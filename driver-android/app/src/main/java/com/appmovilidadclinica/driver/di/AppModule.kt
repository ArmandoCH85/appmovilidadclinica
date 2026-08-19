package com.appmovilidadclinica.driver.di

import android.content.Context
import com.appmovilidadclinica.driver.data.local.SessionDataStore
import com.appmovilidadclinica.driver.data.remote.ApiErrorMapper
import com.appmovilidadclinica.driver.data.remote.KtorApiClient
import com.appmovilidadclinica.driver.data.remote.KtorClientFactory
import com.appmovilidadclinica.driver.data.remote.KtorTokenProvider
import com.appmovilidadclinica.driver.data.repository.AuthRepositoryImpl
import com.appmovilidadclinica.driver.data.repository.BookingRepositoryImpl
import com.appmovilidadclinica.driver.data.repository.DriverRepositoryImpl
import com.appmovilidadclinica.driver.domain.repository.AuthRepository
import com.appmovilidadclinica.driver.domain.repository.BookingRepository
import com.appmovilidadclinica.driver.domain.repository.DriverRepository
import io.ktor.client.HttpClient

object AppModule {

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun provideContext(): Context = appContext

    private val sessionDataStore: SessionDataStore by lazy {
        SessionDataStore(appContext)
    }

    private fun provideKtorTokenProvider(): KtorTokenProvider =
        KtorTokenProvider { sessionDataStore.currentToken() }

    private fun provideHttpClient(): HttpClient =
        KtorClientFactory.create(tokenProvider = provideKtorTokenProvider())

    private fun provideKtorApiClient(): KtorApiClient = KtorApiClient(provideHttpClient())

    fun provideAuthRepository(): AuthRepository =
        AuthRepositoryImpl(
            apiClient = provideKtorApiClient(),
            sessionDataStore = sessionDataStore,
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