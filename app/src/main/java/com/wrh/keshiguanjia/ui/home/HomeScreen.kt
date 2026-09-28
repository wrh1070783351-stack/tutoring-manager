package com.wrh.keshiguanjia.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wrh.keshiguanjia.data.ClassWithTimes
import com.wrh.keshiguanjia.data.Graph
import com.wrh.keshiguanjia.logic.DayLesson
import com.wrh.keshiguanjia.logic.ScheduleLogic
import com.wrh.keshiguanjia.logic.TimeUtils
import com.wrh.keshiguanjia.ui.TimePickerDialogM3
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val scheduleRepo = Graph.scheduleRepository

    /** 周视图数据：按本周 7 天逐日展开（含停课/加课调整） */
    private val weekMonday: LocalDate = LocalDate.now()

    val week: StateFlow<Map<Int, List<DayLesson>>> = combine(
        scheduleRepo.observeClassesWithTimes(),
        scheduleRepo.observeOverrides(),
    ) { classes, overrides ->
        weekMonday.minusDays((weekMonday.dayOfWeek.value - 1).toLong()).let { monday ->
            (0..6).associate { offset ->
                val d = monday.plusDays(offset.toLong())
                d.dayOfWeek.value to ScheduleLogic.lessonsForDate(classes, overrides, d)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 月视图 */
    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    val classes: StateFlow<List<ClassWithTimes>> =
        scheduleRepo.observeClassesWithTimes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthLessons: StateFlow<Map<LocalDate, List<DayLesson>>> =
        combine(scheduleRepo.observeClassesWithTimes(), scheduleRepo.observeOverrides(), _month) { classes, overrides, m ->
            ScheduleLogic.lessonsForMonth(classes, overrides, m)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun prevMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    fun cancelOccurrence(classId: Long, date: LocalDate) = viewModelScope.launch {
        scheduleRepo.cancelOccurrence(classId, date.toString())
    }

    fun addOccurrence(classId: Long, date: LocalDate, startMinute: Int, endMinute: Int) = viewModelScope.launch {
        scheduleRepo.addOccurrence(classId, date.toString(), startMinute, endMinute)
    }

    fun reschedule(classId: Long, from: LocalDate, to: LocalDate, startMinute: Int, endMinute: Int) = viewModelScope.launch {
        scheduleRepo.reschedule(classId, from.toString(), to.toString(), startMinute, endMinute)
    }

    fun deleteOverride(id: Long) = viewModelScope.launch {
        scheduleRepo.deleteOverride(id)
    }

    companion object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel() as T
    }
}

/** 首页：今日课表（周视图）+ 月历视图（调休停课/调课/加课）。 */
@Composable
fun HomeScreen() {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val week by vm.week.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val monthLessons by vm.monthLessons.collectAsStateWithLifecycle()
    var viewMode by rememberSaveable { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val today = remember { TimeUtils.todayDayOfWeek() }
    val listState = rememberLazyListState()

    LaunchedEffect(viewMode) {
        if (viewMode == 0) listState.animateScrollToItem((today - TimeUtils.MONDAY).coerceIn(0, 6))
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (viewMode == 0) "本周课程表" else "月历课表",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            FilterChip(selected = viewMode == 0, onClick = { viewMode = 0 }, label = { Text("周") })
            FilterChip(selected = viewMode == 1, onClick = { viewMode = 1 }, label = { Text("月") }, modifier = Modifier.padding(start = 4.dp))
        }

        if (viewMode == 0) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(TimeUtils.WEEK_RANGE.toList()) { day ->
                    DayColumn(day, week[day].orEmpty(), isToday = day == today)
                }
            }
        } else {
            MonthView(
                month = month,
                monthLessons = monthLessons,
                onPrev = vm::prevMonth,
                onNext = vm::nextMonth,
                onDayClick = { selectedDate = it },
                modifier = Modifier.weight(1f),
            )
        }
    }

    selectedDate?.let { date ->
        DayLessonsDialog(
            date = date,
            lessons = monthLessons[date].orEmpty(),
            classes = vm.classes.collectAsStateWithLifecycle().value,
            onDismiss = { selectedDate = null },
            onCancel = { vm.cancelOccurrence(it.classId, date); false },
            onReschedule = { lesson, to -> vm.reschedule(lesson.classId, date, to, lesson.startMinute, lesson.endMinute); true },
            onAdd = { classId, start, end -> vm.addOccurrence(classId, date, start, end); true },
            onDeleteOverride = { vm.deleteOverride(it) },
        )
    }
}

@Composable
private fun DayColumn(day: Int, slots: List<DayLesson>, isToday: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxHeight()
            .width(150.dp),
        colors = if (isToday) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                TimeUtils.dayLabel(day) + if (isToday) " · 今天" else "",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (slots.isEmpty()) {
                Text(
                    "无课",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            slots.forEachIndexed { index, slot ->
                if (index > 0) HorizontalDivider(Modifier.padding(top = 8.dp))
                Column(Modifier.padding(top = 8.dp)) {
                    Text(
                        TimeUtils.minutesToText(slot.startMinute) + " - " + TimeUtils.minutesToText(slot.endMinute),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        slot.className + if (slot.isCancelled) "（已停课）" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (slot.isCancelled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    )
                    if (slot.subject.isNotBlank()) {
                        Text(slot.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (slot.room.isNotBlank()) {
                        Text(slot.room, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthView(
    month: YearMonth,
    monthLessons: Map<LocalDate, List<DayLesson>>,
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
                        val lessons = monthLessons[date].orEmpty()
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
        Text(
            "点某一天可停课 / 调课 / 临时加课",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(8.dp),
        )
    }
}

/** 某日课表明细 + 停课 / 调课 / 加课操作。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayLessonsDialog(
    date: LocalDate,
    lessons: List<DayLesson>,
    classes: List<ClassWithTimes>,
    onDismiss: () -> Unit,
    onCancel: (DayLesson) -> Boolean,
    onReschedule: (DayLesson, LocalDate) -> Boolean,
    onAdd: (classId: Long, startMinute: Int, endMinute: Int) -> Boolean,
    onDeleteOverride: (Long) -> Unit,
) {
    var rescheduleLesson by remember { mutableStateOf<DayLesson?>(null) }
    var showAddForm by remember { mutableStateOf(false) }
    var addClassId by remember { mutableStateOf(0L) }
    var addStart by remember { mutableStateOf(9 * 60) }
    var addEnd by remember { mutableStateOf(11 * 60) }
    var editingAddTime by remember { mutableStateOf<Boolean?>(null) } // true=开始 false=结束

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.monthValue} 月 ${date.dayOfMonth} 日课表") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (lessons.isEmpty()) {
                    Text("当日无课", color = MaterialTheme.colorScheme.outline)
                }
                lessons.forEach { lesson ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                TimeUtils.minutesToText(lesson.startMinute) + " - " + TimeUtils.minutesToText(lesson.endMinute) +
                                    (if (lesson.isExtra) " · 加课" else "") +
                                    (if (lesson.isCancelled) " · 已停课" else ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (lesson.isCancelled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                lesson.className,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (lesson.isCancelled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        when {
                            lesson.isCancelled && lesson.overrideId != null ->
                                TextButton(onClick = { onDeleteOverride(lesson.overrideId!!) }) { Text("撤销停课") }
                            lesson.isExtra && lesson.overrideId != null ->
                                TextButton(onClick = { onDeleteOverride(lesson.overrideId!!) }) { Text("删除") }
                            !lesson.isCancelled && !lesson.isExtra -> {
                                TextButton(onClick = { onCancel(lesson) }) { Text("停课") }
                                TextButton(onClick = { rescheduleLesson = lesson }) { Text("调课") }
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                if (!showAddForm) {
                    TextButton(onClick = { showAddForm = true }) { Text("+ 临时加课") }
                } else {
                    Text("临时加课", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("班级：", style = MaterialTheme.typography.bodyMedium)
                        AddClassDropdown(classes, addClassId) { addClassId = it }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { editingAddTime = true }) { Text(TimeUtils.minutesToText(addStart)) }
                        Text("至", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { editingAddTime = false }) { Text(TimeUtils.minutesToText(addEnd)) }
                    }
                    TextButton(onClick = {
                        if (addClassId > 0 && addEnd > addStart) {
                            onAdd(addClassId, addStart, addEnd)
                            showAddForm = false
                        }
                    }) { Text("确认加课") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )

    rescheduleLesson?.let { lesson ->
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        AlertDialog(
            onDismissRequest = { rescheduleLesson = null },
            title = { Text("调至哪一天？") },
            text = {
                androidx.compose.material3.DatePicker(
                    state = pickerState,
                    showModeToggle = false,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val to = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        onReschedule(lesson, to)
                    }
                    rescheduleLesson = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { rescheduleLesson = null }) { Text("取消") } },
        )
    }

    editingAddTime?.let { isStart ->
        TimePickerDialogM3(
            title = if (isStart) "上课时间" else "下课时间",
            initialMinute = if (isStart) addStart else addEnd,
            onConfirm = {
                if (isStart) addStart = it else addEnd = it
                editingAddTime = null
            },
            onDismiss = { editingAddTime = null },
        )
    }
}

@Composable
private fun AddClassDropdown(
    classes: List<ClassWithTimes>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = classes.firstOrNull { it.clazz.id == selectedId }?.clazz?.name ?: "选择班级"
    Box {
        TextButton(onClick = { expanded = true }) { Text(selectedName) }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            classes.forEach { cw ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(cw.clazz.name) },
                    onClick = {
                        expanded = false
                        onSelect(cw.clazz.id)
                    },
                )
            }
        }
    }
}
