package com.wrh.keshiguanjia

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wrh.keshiguanjia.logic.DayLesson
import com.wrh.keshiguanjia.ui.schedule.WeeklyScheduleImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** 生成式周课表图片：整周全部包含、高度随内容自适应。 */
@RunWith(AndroidJUnit4::class)
class WeeklyScheduleImageTest {

    @Test
    fun render_containsWholeWeek_andGrowsWithContent() {
        val monday = LocalDate.parse("2026-09-28")
        val week = (0..6).map { monday.plusDays(it.toLong()) }
        val lessons = mapOf(
            monday to listOf(
                DayLesson(1, "Math A", "Math", 1110, 1230, "Room A"),
                DayLesson(2, "English B", "English", 900, 1020, ""),
            ),
            // 其余 6 天无课
        )
        val bmp = WeeklyScheduleImage.render(week, lessons, today = monday)
        assertTrue("宽度应为 1080", bmp.width == 1080)
        // 1 天 2 课 + 6 天空 day 块，高度应显著大于仅屏幕高的一半
        assertTrue("高度应随内容增长，实际 ${bmp.height}", bmp.height > 1400)
        assertEquals(BitmapConfig_ARGB_8888(bmp), true)
        bmp.recycle()
    }

    @Test
    fun render_emptyWeek_isCompact() {
        val monday = LocalDate.parse("2026-09-28")
        val week = (0..6).map { monday.plusDays(it.toLong()) }
        val bmp = WeeklyScheduleImage.render(week, emptyMap(), today = monday)
        assertTrue(bmp.height > 700) // 至少容下标题 + 7 个"无课"块
        bmp.recycle()
    }

    private fun BitmapConfig_ARGB_8888(bmp: android.graphics.Bitmap): Boolean =
        bmp.config == android.graphics.Bitmap.Config.ARGB_8888
}
