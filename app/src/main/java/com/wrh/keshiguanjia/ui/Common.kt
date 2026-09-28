package com.wrh.keshiguanjia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wrh.keshiguanjia.logic.ScheduleLogic
import com.wrh.keshiguanjia.logic.TimeUtils
import java.time.LocalDate
import java.time.YearMonth

/** 列表为空时的提示占位。 */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}

/** 24 小时制时间选择对话框，返回当天分钟数。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogM3(
    title: String,
    initialMinute: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 自绘日历选择对话框（替代手填日期 / Material3 DatePicker，避免列拥挤）。
 * 点选某天后需按「确定」返回 ISO 日期。
 */
@Composable
fun SimpleCalendarDialog(
    title: String,
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(initialDate)) }
    var picked by remember { mutableStateOf(initialDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上个月")
                    }
                    Text(
                        "${month.year} 年 ${month.monthValue} 月",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { month = month.plusMonths(1) }) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下个月")
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    TimeUtils.WEEK_RANGE.forEach { d ->
                        Text(
                            TimeUtils.dayLabel(d).removePrefix("周"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                ScheduleLogic.monthGridCells(month).chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                                if (date != null) {
                                    val selected = date == picked
                                    val isToday = date == LocalDate.now()
                                    Text(
                                        "${date.dayOfMonth}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            selected -> MaterialTheme.colorScheme.onPrimary
                                            isToday -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else androidx.compose.ui.graphics.Color.Transparent,
                                                androidx.compose.foundation.shape.CircleShape,
                                            )
                                            .clickable { picked = date }
                                            .padding(vertical = 6.dp),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(picked) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 日历点选的日期字段（只读展示 + 日历弹层）。 */
@Composable
fun DateField(
    label: String,
    value: String,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var show by remember { mutableStateOf(false) }
    val initial = remember(value) {
        runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
    }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = modifier.clickable { show = true },
    )
    if (show) {
        SimpleCalendarDialog(
            title = label,
            initialDate = initial,
            onConfirm = {
                onPick(it)
                show = false
            },
            onDismiss = { show = false },
        )
    }
}
