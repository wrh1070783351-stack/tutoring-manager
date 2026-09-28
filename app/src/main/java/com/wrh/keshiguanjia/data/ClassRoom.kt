package com.wrh.keshiguanjia.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 班级（如「初二数学周六班」）。 */
@Entity(tableName = "classes")
data class ClassRoom(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subject: String = "",
    /** 0 = 开班，1 = 结班 */
    val status: Int = STATUS_OPEN,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_OPEN = 0
        const val STATUS_CLOSED = 1
    }
}
