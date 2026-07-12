package com.appmovilidadclinica.passenger.di

import com.appmovilidadclinica.passenger.BuildConfig
import com.appmovilidadclinica.passenger.data.remote.ApiErrorMapper
import com.appmovilidadclinica.passenger.data.remote.AuthApi
import com.appmovilidadclinica.passenger.data.remote.AuthInterceptor
import com.appmovilidadclinica.passenger.data.remote.ReservationsApi
import com.appmovilidadclinica.passenger.data.remote.SessionExpiredNotifier
import com.appmovilidadclinica.passenger.data.remote.StopsApi
import com.appmovilidadclinica.passenger.data.remote.TripsApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * `API_BASE_URL` como const plano: se probo con `BuildConfig.API_BASE_URL`
 * pero nunca se sobreescribia por buildType (debug y release usaban el
 * mismo valor) — indireccion sin proposito real. Si en algun momento existe
 * un backend de staging distinto, ahi si vale la pena volver a
 * `buildConfigField` (ver memoria "android-passenger-module/ponytail-audit").
 */
private const val API_BASE_URL = "https://sitechfactura.site/api/"

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
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        // Logging SOLO en debug — nunca loguear JWT/qr_token en produccion
        // (ver diseño técnico, seccion "Configuracion de red").
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiErrorMapper(json: Json): ApiErrorMapper = ApiErrorMapper(json)

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideTripsApi(retrofit: Retrofit): TripsApi = retrofit.create(TripsApi::class.java)

    @Provides
    @Singleton
    fun provideReservationsApi(retrofit: Retrofit): ReservationsApi =
        retrofit.create(ReservationsApi::class.java)

    @Provides
    @Singleton
    fun provideStopsApi(retrofit: Retrofit): StopsApi = retrofit.create(StopsApi::class.java)
}
