package com.wrh.keshiguanjia.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 班级（如「初二数学周六班」）。 */
@Entity(tableName = "classes")
data class ClassRoom(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subject: String = "",
    /** 0 = 开班，1 = 结班，2 = 预定（已排未开） */
    val status: Int = STATUS_OPEN,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_OPEN = 0
        const val STATUS_CLOSED = 1
        const val STATUS_BOOKED = 2

        /** 列表标签后缀；开班（正常）不显示 */
        fun statusSuffix(status: Int): String = when (status) {
            STATUS_CLOSED -> "（已结班）"
            STATUS_BOOKED -> "（预定）"
            else -> ""
        }
    }
}
