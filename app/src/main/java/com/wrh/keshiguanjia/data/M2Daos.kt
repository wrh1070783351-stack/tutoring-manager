package com.wrh.keshiguanjia.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** 报名及其班级、课时包/学期记录的聚合查询结果。 */
data class EnrollmentWithDetails(
    @Embedded val enrollment: Enrollment,
    @Relation(parentColumn = "classId", entityColumn = "id")
    val clazz: ClassRoom,
    @Relation(parentColumn = "id", entityColumn = "enrollmentId")
    val packages: List<ClassPackage>,
    @Relation(parentColumn = "id", entityColumn = "enrollmentId")
    val termRecords: List<TermRecord>,
)

@Dao
interface EnrollmentDao {

    @Transaction
    @Query("SELECT * FROM enrollments WHERE studentId = :studentId ORDER BY createdAt DESC")
    fun observeForStudent(studentId: Long): Flow<List<EnrollmentWithDetails>>

    @Query("SELECT * FROM enrollments WHERE id = :id")
    suspend fun getById(id: Long): Enrollment?

    @Query("SELECT COUNT(*) FROM enrollments WHERE studentId = :studentId AND classId = :classId")
    suspend fun countFor(studentId: Long, classId: Long): Int

    @Insert
    suspend fun insert(enrollment: Enrollment): Long

    @Query("DELETE FROM enrollments WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ClassPackageDao {

    @Insert
    suspend fun insert(packageItem: ClassPackage): Long
}

@Dao
interface TermRecordDao {

    @Insert
    suspend fun insert(term: TermRecord): Long
}

@Dao
interface PaymentDao {

    @Query("SELECT * FROM payments WHERE studentId = :studentId ORDER BY date DESC, createdAt DESC")
    fun observeForStudent(studentId: Long): Flow<List<Payment>>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM payments WHERE date LIKE :yearMonthPrefix || '%'")
    suspend fun sumForMonth(yearMonthPrefix: String): Long

    @Insert
    suspend fun insert(payment: Payment): Long

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface LessonOverrideDao {

    @Query("SELECT * FROM lesson_overrides ORDER BY date")
    fun observeAll(): Flow<List<LessonOverride>>

    @Query("SELECT * FROM lesson_overrides WHERE classId = :classId AND date = :date")
    suspend fun forClassDate(classId: Long, date: String): List<LessonOverride>

    @Insert
    suspend fun insert(overrideItem: LessonOverride): Long

    @Query("DELETE FROM lesson_overrides WHERE id = :id")
    suspend fun deleteById(id: Long)
}
