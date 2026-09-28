package com.wrh.keshiguanjia.logic

/** 编辑界面用的「未落库」时间段草稿。 */
data class ClassTimeDraft(
    val dayOfWeek: Int = TimeUtils.SUNDAY,
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 11 * 60,
    val room: String = "",
)

/** 输入校验：返回错误文案，null 表示通过。 */
object Validators {

    fun validateStudent(name: String): String? =
        if (name.isBlank()) "姓名不能为空" else null

    fun validateClass(name: String, times: List<ClassTimeDraft>): String? = when {
        name.isBlank() -> "班级名不能为空"
        times.isEmpty() -> "请至少设置一个上课时间段"
        times.any { it.endMinute <= it.startMinute } -> "下课时间必须晚于上课时间"
        else -> null
    }
}
