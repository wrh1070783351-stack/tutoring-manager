package com.wrh.keshiguanjia.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 班级的每周固定上课时间段。一天可有多段；删除班级时级联删除。
 * 时间以当天 0 点起的分钟数存储（如 10:00 → 600），避免时区/格式歧义。
 */
@Entity(
    tableName = "class_times",
    foreignKeys = [
        ForeignKey(
            entity = ClassRoom::class,
            parentColumns = ["id"],
            childColumns = ["classId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("classId")],
)
data class ClassTime(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    /** 1 = 周一 … 7 = 周日（与 java.time.DayOfWeek.value 一致） */
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val room: String = "",
)
