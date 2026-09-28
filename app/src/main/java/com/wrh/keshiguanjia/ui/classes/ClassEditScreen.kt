package com.wrh.keshiguanjia.ui.classes

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
import com.wrh.keshiguanjia.logic.TimeUtils
import com.wrh.keshiguanjia.logic.Validators
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
        val closed: Boolean = false,
        val note: String = "",
        val times: List<ClassTimeDraft> = listOf(ClassTimeDraft()),
        val isNew: Boolean = true,
        val loaded: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (classId > 0) {
                val cw = repo.getWithTimes(classId)
                if (cw != null) {
                    _state.update {
                        UiState(
                            name = cw.clazz.name,
                            subject = cw.clazz.subject,
                            closed = cw.clazz.status == ClassRoom.STATUS_CLOSED,
                            note = cw.clazz.note,
                            times = cw.times.map {
                                ClassTimeDraft(it.dayOfWeek, it.startMinute, it.endMinute, it.room)
                            },
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
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun onSubject(v: String) = _state.update { it.copy(subject = v) }
    fun onNote(v: String) = _state.update { it.copy(note = v) }
    fun onClosed(v: Boolean) = _state.update { it.copy(closed = v) }

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
        val times = s.times.map {
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
                status = if (s.closed) ClassRoom.STATUS_CLOSED else ClassRoom.STATUS_OPEN,
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
    // 正在编辑的时间：index 到 起始/结束（true=起始，false=结束）
    var editingTime by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    if (!state.loaded) return

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            FilterChip(
                selected = !state.closed,
                onClick = { vm.onClosed(false) },
                label = { Text("开班") },
                modifier = Modifier.padding(start = 4.dp),
            )
            FilterChip(
                selected = state.closed,
                onClick = { vm.onClosed(true) },
                label = { Text("结班") },
                modifier = Modifier.padding(start = 8.dp),
            )
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
                        DayDropdown(
                            selected = draft.dayOfWeek,
                            onSelect = { vm.updateTime(index, draft.copy(dayOfWeek = it)) },
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { editingTime = index to true }) {
                            Text(TimeUtils.minutesToText(draft.startMinute))
                        }
                        Text(" 至 ", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { editingTime = index to false }) {
                            Text(TimeUtils.minutesToText(draft.endMinute))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { vm.removeTime(index) },
                            enabled = state.times.size > 1,
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除该时间段")
                        }
                    }
                    OutlinedTextField(
                        value = draft.room,
                        onValueChange = { vm.updateTime(index, draft.copy(room = it)) },
                        label = { Text("教室（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
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
        val pickerState = rememberTimePickerState(
            initialHour = initial / 60,
            initialMinute = initial % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { editingTime = null },
            title = { Text(if (isStart) "上课时间" else "下课时间") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val m = pickerState.hour * 60 + pickerState.minute
                    vm.updateTime(index, if (isStart) draft.copy(startMinute = m) else draft.copy(endMinute = m))
                    editingTime = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingTime = null }) { Text("取消") }
            },
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
}

@Composable
private fun DayDropdown(selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) { Text(TimeUtils.dayLabel(selected)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TimeUtils.WEEK_RANGE.forEach { d ->
                DropdownMenuItem(
                    text = { Text(TimeUtils.dayLabel(d)) },
                    onClick = {
                        expanded = false
                        onSelect(d)
                    },
                )
            }
        }
    }
}
