package com.wrh.keshiguanjia.ui.students

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.wrh.keshiguanjia.data.Enrollment
import com.wrh.keshiguanjia.data.EnrollmentWithDetails
import com.wrh.keshiguanjia.data.Graph
import com.wrh.keshiguanjia.data.Payment
import com.wrh.keshiguanjia.logic.Billing
import com.wrh.keshiguanjia.logic.BillingDraft
import com.wrh.keshiguanjia.logic.MoneyUtils
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StudentDetailViewModel(
    studentRepository: com.wrh.keshiguanjia.data.StudentRepository,
    private val enrollmentRepository: com.wrh.keshiguanjia.data.EnrollmentRepository,
    private val studentId: Long,
) : ViewModel() {

    val student: StateFlow<com.wrh.keshiguanjia.data.Student?> = flow {
        emit(studentRepository.getById(studentId))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val enrollments: StateFlow<List<EnrollmentWithDetails>> =
        enrollmentRepository.observeForStudent(studentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val payments: StateFlow<List<Payment>> =
        enrollmentRepository.observePaymentsForStudent(studentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun renew(enrollment: EnrollmentWithDetails, draft: BillingDraft, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            Billing.validate(draft.copy(classId = enrollment.enrollment.classId))?.let { onResult(it) }
                ?: run {
                    enrollmentRepository.renew(enrollment.enrollment, draft)
                    onResult(null)
                }
        }
    }

    companion object {
        fun factory(studentId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                StudentDetailViewModel(Graph.studentRepository, Graph.enrollmentRepository, studentId) as T
        }
    }
}

/** 学生详情：档案 + 报名班级（余额/有效期） + 缴费记录。 */
@Composable
fun StudentDetailScreen(
    studentId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onEnroll: () -> Unit,
) {
    val vm: StudentDetailViewModel = viewModel(
        key = "student_detail_$studentId",
        factory = StudentDetailViewModel.factory(studentId),
    )
    val student by vm.student.collectAsStateWithLifecycle()
    val enrollments by vm.enrollments.collectAsStateWithLifecycle()
    val payments by vm.payments.collectAsStateWithLifecycle()
    var renewTarget by remember { mutableStateOf<EnrollmentWithDetails?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "学生详情",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEdit) { Text("编辑") }
        }

        student?.let { s ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    listOf("年级" to s.grade, "家长电话" to s.parentPhone, "微信" to s.wechatId, "备注" to s.note)
                        .filter { it.second.isNotBlank() }
                        .forEach { (label, value) ->
                            Text(
                                "$label：$value",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                }
            }
        }

        SectionTitle("我的班级（${enrollments.size}）")
        if (enrollments.isEmpty()) {
            Text(
                "还未报名任何班级",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        enrollments.forEach { ew ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ew.clazz.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (ew.enrollment.billingType == Enrollment.BILLING_SESSIONS) "次卡" else "学期",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (ew.enrollment.billingType == Enrollment.BILLING_SESSIONS) {
                        val total = Billing.purchasedSessions(ew.packages)
                        val validUntil = ew.packages.mapNotNull { it.validUntil }.minOrNull()
                        Text(
                            "已购 $total 次" + (validUntil?.let { "，有效期至 $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val until = Billing.termValidUntil(ew.termRecords.map { it.endDate })
                        Text(
                            (until?.let { "有效期至 $it" } ?: "未设置期限"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { renewTarget = ew }) { Text("续费") }
                }
            }
        }
        OutlinedButton(
            onClick = onEnroll,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        ) { Text("报名新班级") }

        SectionTitle("缴费记录（${payments.size}）")
        if (payments.isEmpty()) {
            Text(
                "暂无缴费记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        payments.forEach { p ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "¥" + MoneyUtils.yuanText(p.amountCents),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        p.date,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    listOf(Payment.METHOD_LABELS.getOrElse(p.method) { "其他" }, p.note)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.padding(bottom = 16.dp))
    }

    renewTarget?.let { target ->
        RenewDialog(
            enrollment = target,
            onDismiss = { renewTarget = null },
        ) { draft, onResult ->
            vm.renew(target, draft, onResult)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun RenewDialog(
    enrollment: EnrollmentWithDetails,
    onDismiss: () -> Unit,
    onConfirm: (BillingDraft, (String?) -> Unit) -> Unit,
) {
    val isSessions = enrollment.enrollment.billingType == Enrollment.BILLING_SESSIONS
    val today = remember { LocalDate.now().toString() }
    var sessionsText by remember { mutableStateOf(if (isSessions) "20" else "") }
    var bonusText by remember { mutableStateOf("0") }
    var amountText by remember { mutableStateOf("") }
    var validUntilText by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf(LocalDate.now().plusMonths(4).toString()) }
    var method by remember { mutableStateOf(1) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isSessions) "续费课时包" else "续期学期") },
        text = {
            Column {
                if (isSessions) {
                    OutlinedTextField(sessionsText, { sessionsText = it }, label = { Text("购买次数 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(bonusText, { bonusText = it }, label = { Text("赠送次数") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    OutlinedTextField(validUntilText, { validUntilText = it }, label = { Text("有效期（yyyy-MM-dd，可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    OutlinedTextField(amountText, { amountText = it }, label = { Text("金额（元）") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                } else {
                    OutlinedTextField(startDate, { startDate = it }, label = { Text("开始日期（yyyy-MM-dd）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(endDate, { endDate = it }, label = { Text("结束日期") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    OutlinedTextField(amountText, { amountText = it }, label = { Text("金额（元）") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("方式：", style = MaterialTheme.typography.bodyMedium)
                    Payment.METHOD_LABELS.take(3).forEachIndexed { i, label ->
                        FilterChip(
                            selected = method == i,
                            onClick = { method = i },
                            label = { Text(label) },
                            modifier = Modifier.padding(start = if (i == 0) 4.dp else 6.dp),
                        )
                    }
                }
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amountCents = if (amountText.isBlank()) 0L else MoneyUtils.parseYuanToCents(amountText)
                if (amountText.isNotBlank() && amountCents == null) {
                    error = "金额格式错误"
                    return@Button
                }
                val draft = BillingDraft(
                    classId = enrollment.enrollment.classId,
                    billingType = enrollment.enrollment.billingType,
                    sessions = sessionsText.toIntOrNull() ?: 0,
                    bonusSessions = bonusText.toIntOrNull() ?: 0,
                    startDate = if (isSessions) null else startDate.trim(),
                    endDate = if (isSessions) null else endDate.trim(),
                    amountCents = amountCents ?: 0,
                    payDate = today,
                    method = method,
                    validUntil = validUntilText.trim().ifBlank { null },
                )
                onConfirm(draft) { err ->
                    if (err == null) onDismiss() else error = err
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
