package com.wrh.keshiguanjia

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wrh.keshiguanjia.ui.DateField
import com.wrh.keshiguanjia.ui.theme.KeshiGuanjiaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate

/** 日历点选日期字段：点击打开日历弹层、选日确定后回传日期。 */
@RunWith(AndroidJUnit4::class)
class DateFieldTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingDateField_opensCalendar_andReturnsPickedDate() {
        var picked: LocalDate? = null
        composeRule.setContent {
            KeshiGuanjiaTheme {
                DateField(label = "开课日期", value = "2026-10-10", onPick = { picked = it })
            }
        }
        // 点击字段（透明拦截层）→ 日历弹层出现
        composeRule.onNodeWithTag("date_field_overlay").performClick()
        composeRule.onNodeWithText("确定").assertExists()

        // 点选 15 日并确定
        composeRule.onNodeWithText("15").performClick()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.runOnIdle {
            assertEquals(LocalDate.parse("2026-10-15"), picked)
        }
    }
}
