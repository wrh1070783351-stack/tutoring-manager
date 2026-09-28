package com.wrh.keshiguanjia.logic

/** 周视图上的一节课（只读展示模型）。 */
data class LessonSlot(
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val className: String,
    val subject: String,
    val room: String = "",
)

/** 周视图分组逻辑：按星期分组、组内按开始时间排序。 */
object WeekSchedule {

    fun groupByDay(slots: List<LessonSlot>): Map<Int, List<LessonSlot>> =
        slots.groupBy { it.dayOfWeek }
            .mapValues { (_, list) -> list.sortedBy { it.startMinute } }
}
