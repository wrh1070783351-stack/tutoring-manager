package com.wrh.keshiguanjia

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
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
        // 点击字段文本 → 日历弹层出现（interactionSource 监听按压）
        composeRule.onNodeWithText("2026-10-10").performClick()
        composeRule.onNodeWithText("确定").assertExists()

        // 点选 15 日并确定
        composeRule.onNodeWithText("15").performClick()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.runOnIdle {
            assertEquals(LocalDate.parse("2026-10-15"), picked)
        }
    }

    @Test
    fun dateField_insideScrollableColumn_stillOpensCalendar() {
        // 复现编辑班级页场景：字段位于可滚动 Column 中、上方有占位内容
        var picked: LocalDate? = null
        composeRule.setContent {
            KeshiGuanjiaTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    repeat(8) { Text("占位内容 $it", modifier = Modifier.padding(vertical = 12.dp)) }
                    DateField(
                        label = "开课日期",
                        value = "2026-10-10",
                        onPick = { picked = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    repeat(12) { Text("尾部内容 $it", modifier = Modifier.padding(vertical = 12.dp)) }
                }
            }
        }
        composeRule.onNodeWithText("2026-10-10").performClick()
        composeRule.onNodeWithText("确定").assertExists()
        composeRule.onNodeWithText("20").performClick()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.runOnIdle {
            assertEquals(LocalDate.parse("2026-10-20"), picked)
        }
    }
}
