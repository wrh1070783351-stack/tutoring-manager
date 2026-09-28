package com.wrh.keshiguanjia.logic

/** 课表时间的纯逻辑工具（分钟数 <-> 文本、星期标签）。便于 JVM 单元测试。 */
object TimeUtils {

    const val MONDAY = 1
    const val SUNDAY = 7
    val WEEK_RANGE = MONDAY..SUNDAY

    /** 600 -> "10:00" */
    fun minutesToText(minutes: Int): String =
        "%02d:%02d".format(minutes / 60, minutes % 60)

    /** "10:00" -> 600；非法输入返回 null（小时 0-23，分钟 0-59，格式 HH:mm） */
    fun textToMinutes(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /** 1 -> "周一" */
    fun dayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
        5 -> "周五"; 6 -> "周六"; 7 -> "周日"
        else -> ""
    }

    fun todayDayOfWeek(): Int = java.time.LocalDate.now().dayOfWeek.value
}
