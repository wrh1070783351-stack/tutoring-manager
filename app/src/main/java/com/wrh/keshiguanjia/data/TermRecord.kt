package com.wrh.keshiguanjia.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 学期制报名的一次缴费期（续期 = 新增一条，允许重叠交叠）。 */
@Entity(
    tableName = "term_records",
    foreignKeys = [
        ForeignKey(
            entity = Enrollment::class,
            parentColumns = ["id"],
            childColumns = ["enrollmentId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("enrollmentId")],
)
data class TermRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val enrollmentId: Long,
    /** ISO 日期 */
    val startDate: String,
    val endDate: String,
    /** 金额，单位：分 */
    val amountCents: Long,
)
