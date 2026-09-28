package com.wrh.keshiguanjia.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassTimeDao {

    @Query("SELECT * FROM class_times WHERE classId = :classId ORDER BY dayOfWeek, startMinute")
    fun observeForClass(classId: Long): Flow<List<ClassTime>>

    @Query("SELECT * FROM class_times WHERE classId = :classId ORDER BY dayOfWeek, startMinute")
    suspend fun getForClass(classId: Long): List<ClassTime>

    @Insert
    suspend fun insertAll(times: List<ClassTime>)

    @Query("DELETE FROM class_times WHERE classId = :classId")
    suspend fun deleteByClassId(classId: Long)
}
