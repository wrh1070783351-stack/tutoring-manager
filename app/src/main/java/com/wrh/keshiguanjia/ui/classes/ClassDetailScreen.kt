package com.wrh.keshiguanjia.ui.classes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.wrh.keshiguanjia.data.ClassWithTimes
import com.wrh.keshiguanjia.data.Enrollment
import com.wrh.keshiguanjia.data.EnrollmentWithDetails
import com.wrh.keshiguanjia.data.Graph
import com.wrh.keshiguanjia.data.Student
import com.wrh.keshiguanjia.logic.Billing
import com.wrh.keshiguanjia.logic.BillingDraft
import com.wrh.keshiguanjia.logic.MoneyUtils
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClassDetailViewModel(
    private val classId: Long,
) : ViewModel() {

    private val repo = Graph.enrollmentRepository

    val clazz: StateFlow<ClassWithTimes?> = Graph.classRepository.observeAllWithTimes()
        .map { list -> list.firstOrNull { it.clazz.id == classId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val rows: StateFlow<List<EnrollmentWithDetails>> = repo.observeForClass(classId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 全部学生（添加时筛掉已在本班的） */
    val students: StateFlow<List<Student>> = Graph.studentRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val consumedAll: StateFlow<Map<Long, Int>> = repo.observeConsumedAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun addStudent(studentId: Long, billingType: Int, amountText: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            if (repo.countFor(studentId, classId) > 0) {
                onResult("该学生已在此班级")
                return@launch
            }
            val amountCents = if (amountText.isBlank()) 0L else MoneyUtils.parseYuanToCents(amountText)
            if (amountText.isNotBlank() && amountCents == null) {
                onResult("金额格式错误")
                return@launch
            }
            val draft = BillingDraft(
                classId = classId,
                billingType = billingType,
                sessions = if (billingType == Enrollment.BILLING_SESSIONS) 20 else 0,
                startDate = if (billingType == Enrollment.BILLING_TERM) LocalDate.now().toString() else null,
                endDate = if (billingType == Enrollment.BILLING_TERM) LocalDate.now().plusMonths(4).toString() else null,
                amountCents = amountCents ?: 0,
                payDate = LocalDate.now().toString(),
                paymentReceived = false, // 默认未收挂账，收款后到学生详情登记
            )
            repo.enroll(studentId, draft)
            onResult(null)
        }
    }

    fun removeEnrollment(id: Long) {
        viewModelScope.launch { repo.deleteEnrollment(id) }
    }

    companion object {
        fun factory(classId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ClassDetailViewModel(classId) as T
        }
    }
}

/** 班级详情：信息 + 学生名单（添加/移除）。 */
@Composable
fun ClassDetailScreen(
    classId: Long,
    onBack: () -> Unit,
    onEditClass: () -> Unit,
) {
    val vm: ClassDetailViewModel = viewModel(
        key = "class_detail_$classId",
        factory = ClassDetailViewModel.factory(classId),
    )
    val clazz by vm.clazz.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val students by vm.students.collectAsStateWithLifecycle()
    val consumedAll by vm.consumedAll.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<EnrollmentWithDetails?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                clazz?.clazz?.name ?: "班级详情",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEditClass) { Text("编辑") }
        }

        clazz?.let { cw ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        ClassRoom.statusSuffix(cw.clazz.status).ifBlank { "开班中" } .removePrefix("（").removeSuffix("）")
                            .let { "状态：$it" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (cw.clazz.subject.isNotBlank()) {
                        Text("科目：${cw.clazz.subject}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "上课：" + cw.timesSummary().ifBlank { "未设置" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "学生（${rows.size}）",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { showAdd = true }) { Text("+ 添加学生") }
        }

        if (rows.isEmpty()) {
            Text(
                "还没有学生，点「添加学生」把学生报进这个班",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn {
            items(rows, key = { it.enrollment.id }) { row ->
                val consumed = consumedAll[row.enrollment.id] ?: 0
                ListItem(
                    headlineContent = { Text(row.student.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        if (row.enrollment.billingType == Enrollment.BILLING_SESSIONS) {
                            val remaining = Billing.remainingSessions(row.packages, consumed)
                            Text(
                                "次卡 · 剩余 $remaining 次",
                                color = if (remaining <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(
                                "学期 · " + (Billing.termValidUntil(row.termRecords.map { it.endDate })?.let { "至 $it" } ?: "未设期限"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    trailingContent = {
                        TextButton(onClick = { removeTarget = row }) {
                            Text("移除", color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (showAdd) {
        AddStudentDialog(
            candidates = students.filter { s -> rows.none { it.enrollment.studentId == s.id } },
            onDismiss = { showAdd = false },
        ) { studentId, billingType, amountText, onResult ->
            vm.addStudent(studentId, billingType, amountText) { err ->
                onResult(err)
                if (err == null) showAdd = false
            }
        }
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("移除学生") },
            text = { Text("把「${target.student.name}」移出该班级？其课时包/学期与消课记录将一并删除（缴费流水保留），操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeEnrollment(target.enrollment.id)
                    removeTarget = null
                }) { Text("移除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun AddStudentDialog(
    candidates: List<Student>,
    onDismiss: () -> Unit,
    onConfirm: (studentId: Long, billingType: Int, amountText: String, onResult: (String?) -> Unit) -> Unit,
) {
    var studentId by remember { mutableStateOf(0L) }
    var billingType by remember { mutableStateOf(Enrollment.BILLING_SESSIONS) }
    var amountText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加学生到班级") },
        text = {
            Column {
                Text("学生 *", style = MaterialTheme.typography.titleSmall)
                StudentDropdown(candidates, studentId) { studentId = it; error = null }
                Text("计费类型", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Row {
                    FilterChip(
                        selected = billingType == Enrollment.BILLING_SESSIONS,
                        onClick = { billingType = Enrollment.BILLING_SESSIONS },
                        label = { Text("次卡（默认 20 次）") },
                    )
                    FilterChip(
                        selected = billingType == Enrollment.BILLING_TERM,
                        onClick = { billingType = Enrollment.BILLING_TERM },
                        label = { Text("学期（默认 4 个月）") },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedTextField(
                    amountText,
                    { amountText = it; error = null },
                    label = { Text("约定金额（元，可留空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "默认登记为未收（期末结），收款后到学生详情页登记缴费",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (studentId <= 0) {
                    error = "请选择学生"
                } else {
                    onConfirm(studentId, billingType, amountText) { err ->
                        if (err != null) error = err
                    }
                }
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun StudentDropdown(
    candidates: List<Student>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = candidates.firstOrNull { it.id == selectedId }?.name ?: "选择学生"
    Column {
        OutlinedButton(onClick = { expanded = true }) { Text(selectedName) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (candidates.isEmpty()) {
                DropdownMenuItem(text = { Text("没有可选学生（都已在本班）") }, onClick = { expanded = false })
            }
            candidates.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.name + (s.grade.ifBlank { "" }.let { if (it.isBlank()) "" else " · $it" })) },
                    onClick = {
                        expanded = false
                        onSelect(s.id)
                    },
                )
            }
        }
    }
}
