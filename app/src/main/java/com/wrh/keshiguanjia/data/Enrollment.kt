package com.wrh.keshiguanjia.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 报名：学生在某班级的报读关系。
 * 次卡制（BILLING_SESSIONS）靠课时包计次；学期制（BILLING_TERM）靠起止日期。
 */
@Entity(
    tableName = "enrollments",
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
    ],
    indices = [Index("studentId"), Index("classId")],
)
data class Enrollment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val classId: Long,
    /** 0 = 次卡制，1 = 学期制 */
    val billingType: Int = BILLING_SESSIONS,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val BILLING_SESSIONS = 0
        const val BILLING_TERM = 1
    }
}
