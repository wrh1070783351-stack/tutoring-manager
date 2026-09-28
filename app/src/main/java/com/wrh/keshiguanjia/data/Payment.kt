package com.wrh.keshiguanjia.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 缴费记录。报名/续费时自动生成，也可单独补记。 */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("studentId")],
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    /** 关联报名，可空（补记杂费等） */
    val enrollmentId: Long? = null,
    /** 金额，单位：分 */
    val amountCents: Long,
    /** 缴费日期，ISO */
    val date: String,
    /** 0=现金 1=微信 2=支付宝 3=银行转账 4=其他 */
    val method: Int = METHOD_CASH,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val METHOD_CASH = 0
        const val METHOD_WECHAT = 1
        const val METHOD_ALIPAY = 2
        const val METHOD_BANK = 3
        const val METHOD_OTHER = 4
        val METHOD_LABELS = listOf("现金", "微信", "支付宝", "银行转账", "其他")
    }
}
