package com.appmovilidadclinica.passenger.di

import android.content.Context
import androidx.room.Room
import com.appmovilidadclinica.passenger.data.local.AppDatabase
import com.appmovilidadclinica.passenger.data.local.ReservationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME).build()

    @Provides
    @Singleton
    fun provideReservationDao(database: AppDatabase): ReservationDao = database.reservationDao()
}