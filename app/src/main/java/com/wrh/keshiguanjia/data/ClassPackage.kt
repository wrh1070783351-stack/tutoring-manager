package com.wrh.keshiguanjia.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 次卡：一次购买记录（续费 = 新增一条）。 */
@Entity(
    tableName = "class_packages",
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
data class ClassPackage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val enrollmentId: Long,
    val totalSessions: Int,
    val bonusSessions: Int = 0,
    /** 金额，单位：分 */
    val amountCents: Long,
    val purchasedAt: Long = System.currentTimeMillis(),
    /** 可选有效期，ISO 日期（如 2027-01-31），空表示不限期 */
    val validUntil: String? = null,
)
