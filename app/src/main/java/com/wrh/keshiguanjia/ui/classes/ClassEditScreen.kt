package com.wrh.keshiguanjia.ui.classes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.wrh.keshiguanjia.data.ClassRoom
import com.wrh.keshiguanjia.data.ClassTime
import com.wrh.keshiguanjia.data.Graph
import com.wrh.keshiguanjia.logic.ClassTimeDraft
import com.wrh.keshiguanjia.logic.ClassTimeExpansion
import com.wrh.keshiguanjia.logic.TimeUtils
import com.wrh.keshiguanjia.logic.Validators
import com.wrh.keshiguanjia.ui.TimePickerDialogM3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ClassEditViewModel(
    private val repo: com.wrh.keshiguanjia.data.ClassRepository,
    private val classId: Long,
) : ViewModel() {

    data class UiState(
        val name: String = "",
        val subject: String = "",
        val status: Int = ClassRoom.STATUS_OPEN,
        val note: String = "",
        val times: List<ClassTimeDraft> = listOf(ClassTimeDraft()),
        val isNew: Boolean = true,
        val loaded: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 载入完成时的快照，用于判断是否有未保存修改。 */
    private var pristine: UiState? = null

    fun isDirty(): Boolean {
        val p = pristine ?: return false
        return _state.value.copy(error = null) != p
    }

    init {
        viewModelScope.launch {
            if (classId > 0) {
                val cw = repo.getWithTimes(classId)
                if (cw != null) {
                    _state.update {
                        UiState(
                            name = cw.clazz.name,
                            subject = cw.clazz.subject,
                            status = cw.clazz.status,
                            note = cw.clazz.note,
                            times = ClassTimeExpansion.collapse(cw.times),
                            isNew = false,
                            loaded = true,
                        )
                    }
                } else {
                    _state.update { it.copy(loaded = true) }
                }
            } else {
                _state.update { it.copy(loaded = true) }
            }
            pristine = _state.value.copy(error = null)
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun onSubject(v: String) = _state.update { it.copy(subject = v) }
    fun onNote(v: String) = _state.update { it.copy(note = v) }
    fun onStatus(v: Int) = _state.update { it.copy(status = v) }

    fun addTime() = _state.update { it.copy(times = it.times + ClassTimeDraft(), error = null) }
    fun removeTime(index: Int) = _state.update { s ->
        s.copy(times = s.times.filterIndexed { i, _ -> i != index })
    }

    fun updateTime(index: Int, draft: ClassTimeDraft) = _state.update { s ->
        s.copy(times = s.times.mapIndexed { i, d -> if (i == index) draft else d }, error = null)
    }

    suspend fun save(): Boolean {
        val s = _state.value
        Validators.validateClass(s.name, s.times)?.let { err ->
            _state.update { it.copy(error = err) }
            return false
        }
        val times = ClassTimeExpansion.expand(s.times).map {
            ClassTime(
                classId = 0,
                dayOfWeek = it.dayOfWeek,
                startMinute = it.startMinute,
                endMinute = it.endMinute,
                room = it.room.trim(),
            )
        }
        repo.saveWithTimes(
            ClassRoom(
                id = if (s.isNew) 0L else classId,
                name = s.name.trim(),
                subject = s.subject.trim(),
                status = s.status,
                note = s.note.trim(),
            ),
            times,
        )
        return true
    }

    suspend fun delete() {
        if (!_state.value.isNew) repo.deleteById(classId)
    }

    companion object {
        fun factory(classId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ClassEditViewModel(Graph.classRepository, classId) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassEditScreen(classId: Long, onDone: () -> Unit) {
    val vm: ClassEditViewModel = viewModel(
        key = "class_$classId",
        factory = ClassEditViewModel.factory(classId),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    // 正在编辑的时间：index 到 起始/结束（true=起始，false=结束）
    var editingTime by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    val handleBack: () -> Unit = {
        if (vm.isDirty()) showDiscardConfirm = true else onDone()
    }
    BackHandler { handleBack() }

    if (!state.loaded) return

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = handleBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                if (state.isNew) "新增班级" else "编辑班级",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { scope.launch { if (vm.save()) onDone() } }) { Text("保存") }
        }
        if (state.error != null) {
            Text(
                state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        OutlinedTextField(
            value = state.name,
            onValueChange = vm::onName,
            label = { Text("班级名（如：初二数学周六班）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedTextField(
            value = state.subject,
            onValueChange = vm::onSubject,
            label = { Text("科目（如：数学）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("状态：", style = MaterialTheme.typography.bodyMedium)
            listOf(
                ClassRoom.STATUS_OPEN to "开班",
                ClassRoom.STATUS_BOOKED to "预定",
                ClassRoom.STATUS_CLOSED to "结班",
            ).forEachIndexed { i, (value, label) ->
                FilterChip(
                    selected = state.status == value,
                    onClick = { vm.onStatus(value) },
                    label = { Text(label) },
                    modifier = Modifier.padding(start = if (i == 0) 4.dp else 8.dp),
                )
            }
        }

        Text(
            "每周上课时间",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        state.times.forEachIndexed { index, draft ->
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (draft.days.isEmpty()) "选择上课星期："
                            else draft.days.sorted().joinToString("、") { TimeUtils.dayLabel(it) },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { vm.removeTime(index) },
                            enabled = state.times.size > 1,
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除该时间段")
                        }
                    }
                    // 两行等宽布局：一行 7 个 chip 会超出屏幕宽度把「日」挤出可视区
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    ) {
                        TimeUtils.WEEK_RANGE.take(4).forEach { day ->
                            DayChip(
                                day = day,
                                selected = day in draft.days,
                                onToggle = {
                                    vm.updateTime(
                                        index,
                                        draft.copy(days = if (day in draft.days) draft.days - day else draft.days + day),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    ) {
                        TimeUtils.WEEK_RANGE.drop(4).forEach { day ->
                            DayChip(
                                day = day,
                                selected = day in draft.days,
                                onToggle = {
                                    vm.updateTime(
                                        index,
                                        draft.copy(days = if (day in draft.days) draft.days - day else draft.days + day),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        OutlinedButton(onClick = { editingTime = index to true }) {
                            Text(TimeUtils.minutesToText(draft.startMinute))
                        }
                        Text(" 至 ", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { editingTime = index to false }) {
                            Text(TimeUtils.minutesToText(draft.endMinute))
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = draft.room,
                            onValueChange = { vm.updateTime(index, draft.copy(room = it)) },
                            label = { Text("教室（可选）") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { vm.addTime() },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("+ 添加时间段") }

        if (!state.isNew) {
            Spacer(Modifier.padding(top = 24.dp))
            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("删除该班级", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    editingTime?.let { (index, isStart) ->
        val draft = state.times.getOrNull(index) ?: return
        val initial = if (isStart) draft.startMinute else draft.endMinute
        TimePickerDialogM3(
            title = if (isStart) "上课时间" else "下课时间",
            initialMinute = initial,
            onConfirm = { m ->
                vm.updateTime(index, if (isStart) draft.copy(startMinute = m) else draft.copy(endMinute = m))
                editingTime = null
            },
            onDismiss = { editingTime = null },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除班级") },
            text = { Text("确定删除「${state.name}」吗？其课表时段将一并删除，操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        vm.delete()
                        onDone()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("放弃修改？") },
            text = { Text("当前页面有未保存的内容，离开将丢失这些修改。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    onDone()
                }) { Text("放弃", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("继续编辑") }
            },
        )
    }
}

/** 单个星期 chip（等宽）。 */
@Composable
private fun DayChip(day: Int, selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = {
            Text(
                TimeUtils.dayLabel(day).removePrefix("周"),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        },
        modifier = modifier,
    )
}
