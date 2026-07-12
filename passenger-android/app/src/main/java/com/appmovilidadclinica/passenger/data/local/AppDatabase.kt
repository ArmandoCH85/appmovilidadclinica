package com.appmovilidadclinica.passenger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ReservationEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reservationDao(): ReservationDao

    companion object {
        const val DATABASE_NAME = "passenger.db"
    }
}
