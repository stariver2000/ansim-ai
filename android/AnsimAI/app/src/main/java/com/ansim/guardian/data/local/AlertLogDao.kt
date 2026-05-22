package com.ansim.guardian.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AlertLogEntity)

    /** 최신순 정렬 */
    @Query("SELECT * FROM alert_log ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<AlertLogEntity>>

    @Query("DELETE FROM alert_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM alert_log")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM alert_log")
    suspend fun count(): Int
}
