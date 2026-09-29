package com.wrh.keshiguanjia.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
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
import com.wrh.keshiguanjia.ui.schedule.MonthSchedule
import com.wrh.keshiguanjia.ui.schedule.ShareButton
import com.wrh.keshiguanjia.ui.schedule.ShareableBox
import com.wrh.keshiguanjia.ui.schedule.rememberCaptureState
import com.wrh.keshiguanjia.ui.schedule.WeeklySchedule
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val appContext: android.content.Context) : ViewModel() {

    private val scheduleRepo = Graph.scheduleRepository
    private val enrollmentRepo = Graph.enrollmentRepository

    private val weekMonday: LocalDate = LocalDate.now()

    /** 本周 7 天（周一起） */
    val weekDates: List<LocalDate> = (0..6).map { weekMonday.plusDays(it.toLong()) }

    /** 周视图数据：按本周 7 天逐日展开（含停课/加课调整） */
    val week: StateFlow<Map<LocalDate, List<DayLesson>>> = combine(
        scheduleRepo.observeClassesWithTimes(),
        scheduleRepo.observeOverrides(),
    ) { classes, overrides ->
        weekDates.associateWith { ScheduleLogic.lessonsForDate(classes, overrides, it) }
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

    // ---- 提醒中心（M4）----

    val reminders: StateFlow<List<com.wrh.keshiguanjia.logic.ReminderItem>> = combine(
        enrollmentRepo.observeAllEnrollments(),
        enrollmentRepo.observeConsumedAll(),
        enrollmentRepo.observeAllPayments(),
    ) { enrollments, consumed, payments ->
        com.wrh.keshiguanjia.logic.Reminders.build(
            enrollments = enrollments,
            consumedByEnrollment = consumed,
            payments = payments,
            today = LocalDate.now(),
            lowBalanceThreshold = com.wrh.keshiguanjia.logic.Settings.lowBalanceThreshold(appContext),
            expiryWarnDays = com.wrh.keshiguanjia.logic.Settings.expiryWarnDays(appContext),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(Graph.appContextForVm()) as T
    }
}

/** 首页：今日课表（周视图）+ 月历视图（调休停课/调课/加课）+ 课表分享 + 点名入口。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenRollCall: (classId: Long, date: LocalDate) -> Unit) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val week by vm.week.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val monthLessons by vm.monthLessons.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    var viewMode by rememberSaveable { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showReminders by remember { mutableStateOf(false) }
    val capture = rememberCaptureState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (viewMode == 0) "本周课程表" else "月历课表",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            ShareButton(
                capture,
                if (viewMode == 0) "schedule-week.png" else "schedule-month.png",
                captureBlock = if (viewMode == 0) {
                    {
                        // 生成式：程序绘制完整周表位图（高度自适应，绝不截断）
                        com.wrh.keshiguanjia.ui.schedule.WeeklyScheduleImage
                            .render(vm.weekDates, week).asImageBitmap()
                    }
                } else {
                    null
                },
            )
            FilterChip(selected = viewMode == 0, onClick = { viewMode = 0 }, label = { Text("周") })
            FilterChip(selected = viewMode == 1, onClick = { viewMode = 1 }, label = { Text("月") }, modifier = Modifier.padding(start = 4.dp))
        }

        if (reminders.isNotEmpty()) {
            val low = reminders.count { it.kind == com.wrh.keshiguanjia.logic.ReminderItem.KIND_LOW_BALANCE }
            val expiring = reminders.count { it.kind == com.wrh.keshiguanjia.logic.ReminderItem.KIND_EXPIRING }
            val unpaid = reminders.count { it.kind == com.wrh.keshiguanjia.logic.ReminderItem.KIND_UNPAID }
            Card(
                onClick = { showReminders = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                ),
            ) {
                Text(
                    buildString {
                        append("提醒 ${reminders.size} 条：")
                        val parts = mutableListOf<String>()
                        if (low > 0) parts += "课时不足 $low"
                        if (expiring > 0) parts += "即将到期 $expiring"
                        if (unpaid > 0) parts += "欠费 $unpaid"
                        append(parts.joinToString(" · "))
                        append("（点击查看）")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        ShareableBox(
            capture,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (viewMode == 0) {
                WeeklySchedule(
                    weekDates = vm.weekDates,
                    lessonsByDate = week,
                    modifier = Modifier.fillMaxSize(),
                    lessonTrailing = { lesson, date ->
                        if (!lesson.isCancelled) {
                            TextButton(onClick = { onOpenRollCall(lesson.classId, date) }) { Text("点名") }
                        }
                    },
                )
            } else {
                MonthSchedule(
                    month = month,
                    lessonsByDate = monthLessons,
                    onPrev = vm::prevMonth,
                    onNext = vm::nextMonth,
                    onDayClick = { selectedDate = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showReminders) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showReminders = false },
            title = { Text("提醒中心（${reminders.size}）") },
            text = {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    com.wrh.keshiguanjia.logic.ReminderItem.KIND_LABELS.forEachIndexed { kind, kindLabel ->
                        val items = reminders.filter { it.kind == kind }
                        if (items.isNotEmpty()) {
                            Text(
                                kindLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (kind == com.wrh.keshiguanjia.logic.ReminderItem.KIND_UNPAID || kind == com.wrh.keshiguanjia.logic.ReminderItem.KIND_LOW_BALANCE)
                                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                            items.forEach { r ->
                                Text(
                                    "${r.studentName} · ${r.className} —— ${r.detail}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showReminders = false }) { Text("知道了") } },
        )
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
            onRollCall = onOpenRollCall,
        )
    }
}

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
    onRollCall: (classId: Long, date: LocalDate) -> Unit,
) {
    var rescheduleLesson by remember { mutableStateOf<DayLesson?>(null) }
    var showAddForm by remember { mutableStateOf(false) }
    var addClassId by remember { mutableStateOf(0L) }
    var addStart by remember { mutableStateOf(9 * 60) }
    var addEnd by remember { mutableStateOf(11 * 60) }
    var editingAddTime by remember { mutableStateOf<Boolean?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.monthValue} 月 ${date.dayOfMonth} 日课表") },
        text = {
            Column {
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
                            lesson.isExtra && lesson.overrideId != null -> {
                                TextButton(onClick = { onRollCall(lesson.classId, date) }) { Text("点名") }
                                TextButton(onClick = { onDeleteOverride(lesson.overrideId!!) }) { Text("删除") }
                            }
                            !lesson.isCancelled && !lesson.isExtra -> {
                                TextButton(onClick = { onRollCall(lesson.classId, date) }) { Text("点名") }
                                TextButton(onClick = { onCancel(lesson) }) { Text("停课") }
                                TextButton(onClick = { rescheduleLesson = lesson }) { Text("调课") }
                            }
                        }
                    }
                }
                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 6.dp))
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
        var targetDate by remember { mutableStateOf(date) }
        com.wrh.keshiguanjia.ui.SimpleCalendarDialog(
            title = "调至哪一天？",
            initialDate = date,
            onConfirm = {
                targetDate = it
                onReschedule(lesson, targetDate)
                rescheduleLesson = null
            },
            onDismiss = { rescheduleLesson = null },
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
    androidx.compose.foundation.layout.Box {
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
