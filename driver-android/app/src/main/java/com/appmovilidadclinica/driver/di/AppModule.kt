package com.appmovilidadclinica.driver.di

import android.content.Context
import com.appmovilidadclinica.driver.data.local.SessionDataStore
import com.appmovilidadclinica.driver.data.remote.AuthInterceptor
import com.appmovilidadclinica.driver.data.remote.api.AuthApi
import com.appmovilidadclinica.driver.data.remote.api.BookingApi
import com.appmovilidadclinica.driver.data.remote.api.DriverApi
import com.appmovilidadclinica.driver.data.repository.AuthRepositoryImpl
import com.appmovilidadclinica.driver.data.repository.BookingRepositoryImpl
import com.appmovilidadclinica.driver.data.repository.DriverRepositoryImpl
import com.appmovilidadclinica.driver.domain.repository.AuthRepository
import com.appmovilidadclinica.driver.domain.repository.BookingRepository
import com.appmovilidadclinica.driver.domain.repository.DriverRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object AppModule {

    private const val BASE_URL = "https://sitechfactura.site/api/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun provideContext(): Context = appContext

    private fun provideSessionDataStore(): SessionDataStore {
        return SessionDataStore(appContext)
    }

    private fun provideAuthInterceptor(): AuthInterceptor {
        return AuthInterceptor(provideSessionDataStore())
    }

    private fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(provideAuthInterceptor())
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(provideOkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private fun provideAuthApi(): AuthApi {
        return provideRetrofit().create(AuthApi::class.java)
    }

    private fun provideDriverApi(): DriverApi {
        return provideRetrofit().create(DriverApi::class.java)
    }

    private fun provideBookingApi(): BookingApi {
        return provideRetrofit().create(BookingApi::class.java)
    }

    fun provideAuthRepository(): AuthRepository {
        return AuthRepositoryImpl(
            authApi = provideAuthApi(),
            sessionDataStore = provideSessionDataStore()
        )
    }

    fun provideDriverRepository(): DriverRepository {
        return DriverRepositoryImpl(
            driverApi = provideDriverApi()
        )
    }

    fun provideBookingRepository(): BookingRepository {
        return BookingRepositoryImpl(
            bookingApi = provideBookingApi()
        )
    }
}
