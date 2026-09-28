package com.wrh.keshiguanjia.ui.students

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.wrh.keshiguanjia.ui.EmptyHint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class StudentsViewModel(private val repo: StudentRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val students: StateFlow<List<Student>> =
        combine(repo.observeAll(), _query) { all, q ->
            if (q.isBlank()) all else all.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.grade.contains(q, ignoreCase = true) ||
                    it.parentPhone.contains(q, ignoreCase = true) ||
                    it.note.contains(q, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(q: String) {
        _query.value = q
    }

    companion object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StudentsViewModel(Graph.studentRepository) as T
    }
}

@Composable
fun StudentListScreen(onEdit: (Long) -> Unit) {
    val vm: StudentsViewModel = viewModel(factory = StudentsViewModel.Factory)
    val students by vm.students.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(-1L) }) {
                Icon(Icons.Filled.Add, contentDescription = "添加学生")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "学生",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text("搜索姓名 / 年级 / 电话 / 备注") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (students.isEmpty()) {
                EmptyHint(if (query.isBlank()) "还没有学生，点右下角 + 添加" else "没有匹配的学生")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(students, key = { it.id }) { s ->
                        ListItem(
                            headlineContent = { Text(s.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                val second = listOfNotNull(
                                    s.grade.ifBlank { null },
                                    s.parentPhone.ifBlank { null },
                                ).joinToString(" · ")
                                Text(second.ifBlank { "暂无补充信息" })
                            },
                            modifier = Modifier.clickable { onEdit(s.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
