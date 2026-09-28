package com.wrh.keshiguanjia.logic

import com.wrh.keshiguanjia.data.Enrollment

/** 编辑界面用的「未落库」时间段草稿：一个时间段可勾选多个星期（如周六+周日同时 10:00）。 */
data class ClassTimeDraft(
    val days: Set<Int> = emptySet(),
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 11 * 60,
    val room: String = "",
)

/** 草稿展开后的具体上课时间（星期 × 时间段）。 */
data class ExpandedClassTime(
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val room: String,
)

object ClassTimeExpansion {

    /** 把多选草稿展开为逐条上课时间（按星期排序）。 */
    fun expand(drafts: List<ClassTimeDraft>): List<ExpandedClassTime> =
        drafts.flatMap { d ->
            d.days.sorted().map { day ->
                ExpandedClassTime(
                    dayOfWeek = day,
                    startMinute = d.startMinute,
                    endMinute = d.endMinute,
                    room = d.room,
                )
            }
        }

    /** 编辑回填：把已有上课时间按「时间段+教室」聚合成多选草稿。 */
    fun collapse(times: List<com.wrh.keshiguanjia.data.ClassTime>): List<ClassTimeDraft> =
        times.groupBy { Triple(it.startMinute, it.endMinute, it.room) }
            .map { (_, group) ->
                ClassTimeDraft(
                    days = group.map { it.dayOfWeek }.toSet(),
                    startMinute = group[0].startMinute,
                    endMinute = group[0].endMinute,
                    room = group[0].room,
                )
            }
            .sortedWith(compareBy({ it.startMinute }, { it.days.minOrNull() }))
}

/** 输入校验：返回错误文案，null 表示通过。 */
object Validators {

    fun validateStudent(name: String): String? =
        if (name.isBlank()) "姓名不能为空" else null

    fun validateClass(name: String, times: List<ClassTimeDraft>): String? = when {
        name.isBlank() -> "班级名不能为空"
        times.isEmpty() -> "请至少设置一个上课时间段"
        times.any { it.days.isEmpty() } -> "请为每个时间段选择上课星期"
        times.any { it.endMinute <= it.startMinute } -> "下课时间必须晚于上课时间"
        else -> null
    }
}
