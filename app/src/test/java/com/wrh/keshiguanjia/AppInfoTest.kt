package com.wrh.keshiguanjia

import com.wrh.keshiguanjia.logic.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 骨架冒烟测试：验证构建配置正确接入测试框架与 BuildConfig。
 */
class AppInfoTest {

    @Test
    fun versionName_followsSemanticFormat() {
        assertTrue(
            "versionName 应符合 x.y.z 格式，实际为 ${AppInfo.VERSION_NAME}",
            AppInfo.VERSION_NAME.matches(Regex("""\d+\.\d+\.\d+"""))
        )
    }

    @Test
    fun currentVersion_is_1_1_0() {
        assertEquals("1.1.0", AppInfo.VERSION_NAME)
    }
}

class TimeUtilsTest {

    @Test
    fun minutesToText_formatsCorrectly() {
        assertEquals("10:00", TimeUtils.minutesToText(600))
        assertEquals("00:00", TimeUtils.minutesToText(0))
        assertEquals("18:30", TimeUtils.minutesToText(18 * 60 + 30))
        assertEquals("23:59", TimeUtils.minutesToText(23 * 60 + 59))
    }

    @Test
    fun textToMinutes_parsesValidInput() {
        assertEquals(600, TimeUtils.textToMinutes("10:00"))
        assertEquals(1110, TimeUtils.textToMinutes("18:30"))
        assertEquals(0, TimeUtils.textToMinutes("00:00"))
    }

    @Test
    fun textToMinutes_rejectsInvalidInput() {
        assertNull(TimeUtils.textToMinutes("24:00"))
        assertNull(TimeUtils.textToMinutes("12:60"))
        assertNull(TimeUtils.textToMinutes("abc"))
        assertNull(TimeUtils.textToMinutes("10"))
        assertNull(TimeUtils.textToMinutes("10:30:00"))
        assertNull(TimeUtils.textToMinutes(""))
        assertNull(TimeUtils.textToMinutes("-1:30"))
    }

    @Test
    fun textAndMinutes_roundTrip() {
        for (total in listOf(0, 540, 600, 1259, 1439)) {
            assertEquals(total, TimeUtils.textToMinutes(TimeUtils.minutesToText(total)))
        }
    }

    @Test
    fun dayLabel_coversWeekOnly() {
        assertEquals("周一", TimeUtils.dayLabel(1))
        assertEquals("周日", TimeUtils.dayLabel(7))
        assertEquals("", TimeUtils.dayLabel(0))
        assertEquals("", TimeUtils.dayLabel(8))
    }
}
