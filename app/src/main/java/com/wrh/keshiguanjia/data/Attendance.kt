package com.wrh.keshiguanjia.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消课/考勤记录：某学生某班某天上了一次课的出勤结果。
 *  - 到课、缺勤各扣 1 次课时（次卡制）；请假不扣。
 *  - (studentId, classId, date) 唯一：重复点名即修改原记录，历史可追溯。
 */
@Entity(
    tableName = "attendance",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ClassRoom::class,
            parentColumns = ["id"],
            childColumns = ["classId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Enrollment::class,
            parentColumns = ["id"],
            childColumns = ["enrollmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("studentId"),
        Index("classId"),
        Index("enrollmentId"),
        Index(value = ["studentId", "classId", "date"], unique = true),
    ],
)
data class Attendance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val classId: Long,
    val enrollmentId: Long,
    /** ISO 日期 */
    val date: String,
    /** 0=到课 1=请假 2=缺勤 */
    val status: Int = STATUS_ATTENDED,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_ATTENDED = 0
        const val STATUS_LEAVE = 1
        const val STATUS_ABSENT = 2
        val STATUS_LABELS = listOf("到课", "请假", "缺勤")
    }
}
