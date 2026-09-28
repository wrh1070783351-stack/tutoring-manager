package com.wrh.keshiguanjia.ui.students

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.wrh.keshiguanjia.data.Graph
import com.wrh.keshiguanjia.data.Student
import com.wrh.keshiguanjia.data.StudentRepository
import com.wrh.keshiguanjia.logic.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StudentEditViewModel(
    private val repo: StudentRepository,
    private val studentId: Long,
) : ViewModel() {

    data class UiState(
        val name: String = "",
        val grade: String = "",
        val parentPhone: String = "",
        val wechatId: String = "",
        val note: String = "",
        val isNew: Boolean = true,
        val loaded: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (studentId > 0) {
                val s = repo.getById(studentId)
                if (s != null) {
                    _state.update {
                        UiState(
                            name = s.name, grade = s.grade, parentPhone = s.parentPhone,
                            wechatId = s.wechatId, note = s.note, isNew = false, loaded = true,
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
    fun onGrade(v: String) = _state.update { it.copy(grade = v) }
    fun onParentPhone(v: String) = _state.update { it.copy(parentPhone = v) }
    fun onWechatId(v: String) = _state.update { it.copy(wechatId = v) }
    fun onNote(v: String) = _state.update { it.copy(note = v) }

    /** 保存成功返回 true；校验失败时错误写入 state.error。 */
    suspend fun save(): Boolean {
        val s = _state.value
        Validators.validateStudent(s.name)?.let { err ->
            _state.update { it.copy(error = err) }
            return false
        }
        repo.save(
            Student(
                id = if (s.isNew) 0L else studentId,
                name = s.name.trim(),
                grade = s.grade.trim(),
                parentPhone = s.parentPhone.trim(),
                wechatId = s.wechatId.trim(),
                note = s.note.trim(),
            )
        )
        return true
    }

    suspend fun delete() {
        if (!_state.value.isNew) repo.deleteById(studentId)
    }

    companion object {
        fun factory(studentId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                StudentEditViewModel(Graph.studentRepository, studentId) as T
        }
    }
}

@Composable
fun StudentEditScreen(studentId: Long, onDone: () -> Unit) {
    val vm: StudentEditViewModel = viewModel(
        key = "student_$studentId",
        factory = StudentEditViewModel.factory(studentId),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (!state.loaded) return

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.isNew) "新增学生" else "编辑学生",
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
            label = { Text("姓名 *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedTextField(
            value = state.grade,
            onValueChange = vm::onGrade,
            label = { Text("年级（如：初二）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = state.parentPhone,
            onValueChange = vm::onParentPhone,
            label = { Text("家长电话") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = state.wechatId,
            onValueChange = vm::onWechatId,
            label = { Text("微信号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = vm::onNote,
            label = { Text("备注") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (!state.isNew) {
            Spacer(Modifier.padding(top = 24.dp))
            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("删除该学生", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除学生") },
            text = { Text("确定删除「${state.name}」吗？该操作不可恢复。") },
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
