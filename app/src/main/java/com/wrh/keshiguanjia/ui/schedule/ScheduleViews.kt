package com.wrh.keshiguanjia.ui.schedule

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.wrh.keshiguanjia.logic.DayLesson
import com.wrh.keshiguanjia.logic.ScheduleLogic
import com.wrh.keshiguanjia.logic.TimeUtils
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

/** 截图捕获器：ShareableBox 会把捕获函数挂到它上面。 */
class CaptureState {
    var capture: (suspend () -> ImageBitmap)? = null
}

@Composable
fun rememberCaptureState(): CaptureState = remember { CaptureState() }

/**
 * 可整体截图的容器：内容被录制进 GraphicsLayer，供分享按钮导出。
 */
@Composable
fun ShareableBox(
    state: CaptureState,
    modifier: Modifier = Modifier,
    contentBackground: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val graphicsLayer = rememberGraphicsLayer()
    val bgColor = MaterialTheme.colorScheme.background
    Box(
        modifier
            .drawWithCache {
                onDrawWithContent {
                    graphicsLayer.record {
                        if (contentBackground) drawRect(bgColor)
                        this@onDrawWithContent.drawContent()
                    }
                    drawLayer(graphicsLayer)
                }
            },
    ) {
        content()
        state.capture = { graphicsLayer.toImageBitmap() }
    }
}

/** 分享按钮：把捕获的位图写入缓存并通过系统分享面板发出。 */
@Composable
fun ShareButton(state: CaptureState, fileName: String, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }

    IconButton(onClick = {
        scope.launch {
            state.capture?.let { capture ->
                runCatching { shareImage(context, capture(), fileName) }
                    .onFailure { error = "截图失败：${it.message}" }
            }
        }
    }, modifier = modifier) {
        Icon(Icons.Filled.Share, contentDescription = "分享课表截图")
    }
    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
    }
}

private fun shareImage(context: Context, bitmap: ImageBitmap, fileName: String) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, fileName)
    file.outputStream().use { out ->
        bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享课表截图"))
}

/** 周视图（竖向日程式：按天分组，今天自动滚动到顶部附近）。 */
@Composable
fun WeeklySchedule(
    weekDates: List<LocalDate>,
    lessonsByDate: Map<LocalDate, List<DayLesson>>,
    modifier: Modifier = Modifier,
    lessonTrailing: @Composable (DayLesson, LocalDate) -> Unit = { _, _ -> },
) {
    val today = remember { LocalDate.now() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val index = weekDates.indexOf(today).coerceAtLeast(0)
        if (index > 0) listState.scrollToItem(index)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        weekDates.forEach { date ->
            item(key = date.toString()) {
                val lessons = lessonsByDate[date].orEmpty()
                val isToday = date == today
                Text(
                    TimeUtils.dayLabel(date.dayOfWeek.value) +
                        " ${date.monthValue}/${date.dayOfMonth}" +
                        (if (isToday) " · 今天" else ""),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
                if (lessons.isEmpty()) {
                    Text(
                        "无课",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                lessons.forEachIndexed { index, lesson ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                TimeUtils.minutesToText(lesson.startMinute) + " - " +
                                    TimeUtils.minutesToText(lesson.endMinute) +
                                    (if (lesson.isCancelled) " · 已停课" else "") +
                                    (if (lesson.isExtra) " · 加课" else ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (lesson.isCancelled) MaterialTheme.colorScheme.outline
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                lesson.className,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (lesson.isCancelled) MaterialTheme.colorScheme.outline
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            val extras = listOf(lesson.subject, lesson.room).filter { it.isNotBlank() }
                            if (extras.isNotEmpty()) {
                                Text(
                                    extras.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        lessonTrailing(lesson, date)
                    }
                }
            }
        }
    }
}

/** 月历视图（含月份切换）。 */
@Composable
fun MonthSchedule(
    month: YearMonth,
    lessonsByDate: Map<LocalDate, List<DayLesson>>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    Column(modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上个月") }
            Text(
                "${month.year} 年 ${month.monthValue} 月",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNext) { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下个月") }
        }
        Row {
            TimeUtils.WEEK_RANGE.forEach { d ->
                Text(
                    TimeUtils.dayLabel(d).removePrefix("周"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        ScheduleLogic.monthGridCells(month).chunked(7).forEach { weekCells ->
            Row {
                weekCells.forEach { date ->
                    if (date == null) {
                        Box(Modifier.weight(1f).heightIn(min = 64.dp))
                    } else {
                        val lessons = lessonsByDate[date].orEmpty()
                        val isToday = date == today
                        Box(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 64.dp)
                                .padding(1.dp)
                                .background(
                                    if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    MaterialTheme.shapes.small,
                                )
                                .clickable { onDayClick(date) }
                                .padding(3.dp),
                        ) {
                            Column {
                                Text(
                                    "${date.dayOfMonth}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                lessons.take(2).forEach { lesson ->
                                    Text(
                                        TimeUtils.minutesToText(lesson.startMinute) + " " + lesson.className,
                                        fontSize = 8.sp,
                                        lineHeight = 10.sp,
                                        maxLines = 1,
                                        color = if (lesson.isCancelled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                if (lessons.size > 2) {
                                    Text("+${lessons.size - 2}", fontSize = 8.sp, lineHeight = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
