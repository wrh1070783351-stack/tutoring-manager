package com.wrh.keshiguanjia.ui.classes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import com.wrh.keshiguanjia.data.ClassWithTimes
import com.wrh.keshiguanjia.data.Graph
import com.wrh.keshiguanjia.logic.TimeUtils
import com.wrh.keshiguanjia.ui.EmptyHint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ClassesViewModel : ViewModel() {

    val classes: StateFlow<List<ClassWithTimes>> =
        Graph.classRepository.observeAllWithTimes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ClassesViewModel() as T
    }
}

/** 时间段摘要：「周一 10:00 - 12:00、周六 14:00 - 16:00」。 */
internal fun ClassWithTimes.timesSummary(): String =
    times.sortedWith(compareBy({ it.dayOfWeek }, { it.startMinute }))
        .joinToString("；") {
            TimeUtils.dayLabel(it.dayOfWeek) + " " +
                TimeUtils.minutesToText(it.startMinute) + " - " + TimeUtils.minutesToText(it.endMinute)
        }

/** 列表副标题：科目与时间段用 · 连接；时间段缺失时提示未设置。 */
internal fun classSubtitle(subject: String, timesSummary: String): String =
    listOfNotNull(
        subject.ifBlank { null },
        timesSummary.ifBlank { "未设置上课时间" },
    ).joinToString(" · ").ifBlank { "未设置上课时间" }

@Composable
fun ClassListScreen(onEdit: (Long) -> Unit) {
    val vm: ClassesViewModel = viewModel(factory = ClassesViewModel.Factory)
    val classes by vm.classes.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(-1L) }) {
                Icon(Icons.Filled.Add, contentDescription = "添加班级")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "班级",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp),
            )
            if (classes.isEmpty()) {
                EmptyHint("还没有班级，点右下角 + 添加")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(classes, key = { it.clazz.id }) { cw ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    cw.clazz.name + if (cw.clazz.status == 1) "（已结班）" else "",
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (cw.clazz.status == 1) MaterialTheme.colorScheme.outline
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            supportingContent = {
                                Text(classSubtitle(cw.clazz.subject, cw.timesSummary()))
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.clickable { onEdit(cw.clazz.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
