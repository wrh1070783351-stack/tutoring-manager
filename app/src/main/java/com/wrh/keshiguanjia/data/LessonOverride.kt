package com.wrh.keshiguanjia.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 课表调整（调休场景）：针对「某班某一天」的例外记录。
 *  - TYPE_CANCEL：该班该日固定课停课（撤销 = 删除本记录）
 *  - TYPE_ADD：该班该日额外加课（临时约课/调课落点），时间由 startMinute/endMinute 指定
 * 调课 = 原日期一条 CANCEL + 新日期一条 ADD（同一事务）。
 */
@Entity(
    tableName = "lesson_overrides",
    foreignKeys = [
        ForeignKey(
            entity = ClassRoom::class,
            parentColumns = ["id"],
            childColumns = ["classId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("classId"), Index("date")],
)
data class LessonOverride(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    /** ISO 日期（如 2026-10-08） */
    val date: String,
    /** 0 = 加课，1 = 停课 */
    val type: Int = TYPE_ADD,
    val startMinute: Int = 0,
    val endMinute: Int = 0,
    val note: String = "",
) {
    companion object {
        const val TYPE_ADD = 0
        const val TYPE_CANCEL = 1
    }
}
