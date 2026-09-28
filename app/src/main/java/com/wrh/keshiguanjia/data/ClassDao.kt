package com.wrh.keshiguanjia.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassDao {

    @Transaction
    @Query("SELECT * FROM classes ORDER BY createdAt DESC")
    fun observeAllWithTimes(): Flow<List<ClassWithTimes>>

    @Transaction
    @Query("SELECT * FROM classes WHERE id = :id")
    suspend fun getWithTimes(id: Long): ClassWithTimes?

    @Query("SELECT COUNT(*) FROM classes")
    suspend fun count(): Int

    @Insert
    suspend fun insert(clazz: ClassRoom): Long

    @Update
    suspend fun update(clazz: ClassRoom)

    @Delete
    suspend fun delete(clazz: ClassRoom)

    @Query("DELETE FROM classes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
