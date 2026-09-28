package com.wrh.keshiguanjia.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.MapInfo
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 报名及其班级、课时包/学期记录、学生的聚合查询结果。 */
data class EnrollmentWithDetails(
    @Embedded val enrollment: Enrollment,
    @Relation(parentColumn = "classId", entityColumn = "id")
    val clazz: ClassRoom,
    @Relation(parentColumn = "studentId", entityColumn = "id")
    val student: Student,
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

    @Transaction
    @Query("SELECT * FROM enrollments WHERE classId = :classId ORDER BY createdAt DESC")
    fun observeForClass(classId: Long): Flow<List<EnrollmentWithDetails>>

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

@Dao
interface AttendanceDao {

    @Query("SELECT * FROM attendance WHERE classId = :classId AND date = :date")
    fun observeForClassDate(classId: Long, date: String): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId ORDER BY date DESC")
    fun observeForStudent(studentId: Long): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId AND classId = :classId AND date = :date")
    suspend fun getFor(studentId: Long, classId: Long, date: String): Attendance?

    @Insert
    suspend fun insert(attendance: Attendance): Long

    @Update
    suspend fun updateRecord(attendance: Attendance)

    /** 消课计数：到课 + 缺勤（请假不扣） */
    @MapInfo(keyColumn = "enrollmentId", valueColumn = "consumed")
    @Query(
        "SELECT enrollmentId, COUNT(*) AS consumed FROM attendance " +
            "WHERE status IN (0, 2) GROUP BY enrollmentId"
    )
    fun observeConsumedAll(): Flow<Map<Long, Int>>

    @MapInfo(keyColumn = "enrollmentId", valueColumn = "consumed")
    @Query(
        "SELECT enrollmentId, COUNT(*) AS consumed FROM attendance " +
            "WHERE studentId = :studentId AND status IN (0, 2) GROUP BY enrollmentId"
    )
    fun observeConsumedForStudent(studentId: Long): Flow<Map<Long, Int>>

    @Query("DELETE FROM attendance WHERE id = :id")
    suspend fun deleteById(id: Long)
}
