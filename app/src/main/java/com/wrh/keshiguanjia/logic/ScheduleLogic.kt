package com.wrh.keshiguanjia.logic

import com.wrh.keshiguanjia.data.ClassWithTimes
import com.wrh.keshiguanjia.data.LessonOverride
import java.time.LocalDate

/** 月视图 / 日视图上的一节课。 */
data class DayLesson(
    val classId: Long,
    val className: String,
    val subject: String,
    val startMinute: Int,
    val endMinute: Int,
    val room: String,
    /** 该日临时加课 */
    val isExtra: Boolean = false,
    /** 该日固定课已停 */
    val isCancelled: Boolean = false,
    /** 停课/加课对应的调整记录 id（撤销用） */
    val overrideId: Long? = null,
)

/**
 * 把「每周固定课表 + 每日调整记录」展开成具体日期的课表。
 * 规则：某班某日若有停课记录，则该班当日固定课全部取消；加课记录按其时间追加。
 */
object ScheduleLogic {

    fun lessonsForDate(
        classes: List<ClassWithTimes>,
        overrides: List<LessonOverride>,
        date: LocalDate,
    ): List<DayLesson> {
        val dateIso = date.toString()
        val dayOfWeek = date.dayOfWeek.value
        val overridesByClass = overrides.filter { it.date == dateIso }.groupBy { it.classId }

        val result = mutableListOf<DayLesson>()
        for (cw in classes) {
            val ov = overridesByClass[cw.clazz.id].orEmpty()
            val cancelled = ov.any { it.type == LessonOverride.TYPE_CANCEL }
            cw.times.filter { it.dayOfWeek == dayOfWeek }.forEach { t ->
                result += DayLesson(
                    classId = cw.clazz.id,
                    className = cw.clazz.name,
                    subject = cw.clazz.subject,
                    startMinute = t.startMinute,
                    endMinute = t.endMinute,
                    room = t.room,
                    isCancelled = cancelled,
                    overrideId = ov.firstOrNull { it.type == LessonOverride.TYPE_CANCEL }?.id,
                )
            }
            ov.filter { it.type == LessonOverride.TYPE_ADD }.forEach { o ->
                result += DayLesson(
                    classId = cw.clazz.id,
                    className = cw.clazz.name,
                    subject = cw.clazz.subject,
                    startMinute = o.startMinute,
                    endMinute = o.endMinute,
                    room = "",
                    isExtra = true,
                    overrideId = o.id,
                )
            }
        }
        return result.sortedBy { it.startMinute }
    }

    fun lessonsForMonth(
        classes: List<ClassWithTimes>,
        overrides: List<LessonOverride>,
        month: java.time.YearMonth,
    ): Map<LocalDate, List<DayLesson>> {
        val map = mutableMapOf<LocalDate, List<DayLesson>>()
        var d = month.atDay(1)
        while (d.month == month.month) {
            map[d] = lessonsForDate(classes, overrides, d)
            d = d.plusDays(1)
        }
        return map
    }

    /** 月视图网格：含当月所有日期（周一开头补位到整周），补位日为 null。 */
    fun monthGridCells(month: java.time.YearMonth): List<LocalDate?> {
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value - TimeUtils.MONDAY
        val cells = mutableListOf<LocalDate?>()
        repeat(leading) { cells += null }
        var d = first
        while (d.month == month.month) {
            cells += d
            d = d.plusDays(1)
        }
        while (cells.size % 7 != 0) cells += null
        return cells
    }
}
