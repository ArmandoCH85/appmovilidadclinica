package com.appmovilidadclinica.passenger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReservationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReservationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entities: List<ReservationEntity>)

    @Update
    suspend fun update(entity: ReservationEntity)

    @Query("UPDATE reservations SET status = :status WHERE reservationId = :reservationId")
    suspend fun updateStatus(reservationId: Long, status: String)

    @Query("SELECT * FROM reservations ORDER BY confirmedAtEpochMillis DESC")
    fun observeAll(): Flow<List<ReservationEntity>>

    @Query("SELECT * FROM reservations WHERE reservationId = :reservationId")
    fun observeById(reservationId: Long): Flow<ReservationEntity?>

    @Query("SELECT * FROM reservations WHERE reservationId = :reservationId")
    suspend fun getById(reservationId: Long): ReservationEntity?

    @Query("SELECT reservationId FROM reservations")
    suspend fun getAllIds(): List<Long>

    @Query("DELETE FROM reservations WHERE reservationId NOT IN (:ids)")
    suspend fun deleteOrphans(ids: List<Long>)
}
