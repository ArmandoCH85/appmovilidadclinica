package com.appmovilidadclinica.passenger.di

import com.appmovilidadclinica.passenger.data.local.SessionDataStore
import com.appmovilidadclinica.passenger.data.remote.ApiErrorMapper
import com.appmovilidadclinica.passenger.data.remote.KtorApiClient
import com.appmovilidadclinica.passenger.data.remote.KtorClientFactory
import com.appmovilidadclinica.passenger.data.remote.KtorTokenProvider
import com.appmovilidadclinica.passenger.data.remote.SessionExpiredNotifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideSessionExpiredNotifier(): SessionExpiredNotifier = SessionExpiredNotifier()

    @Provides
    @Singleton
    fun provideKtorTokenProvider(sessionDataStore: SessionDataStore): KtorTokenProvider =
        KtorTokenProvider { sessionDataStore.currentToken() }

    @Provides
    @Singleton
    fun provideHttpClient(tokenProvider: KtorTokenProvider): HttpClient =
        KtorClientFactory.create(tokenProvider = tokenProvider)

    @Provides
    @Singleton
    fun provideKtorApiClient(client: HttpClient): KtorApiClient = KtorApiClient(client)

    @Provides
    @Singleton
    fun provideApiErrorMapper(): ApiErrorMapper = ApiErrorMapper()
}