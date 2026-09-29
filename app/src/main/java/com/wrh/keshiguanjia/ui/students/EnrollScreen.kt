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
import androidx.compose.material3.Button
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
import com.wrh.keshiguanjia.data.ClassWithTimes
import com.wrh.keshiguanjia.data.Enrollment
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EnrollViewModel(
    private val enrollmentRepository: com.wrh.keshiguanjia.data.EnrollmentRepository,
    private val studentId: Long,
) : ViewModel() {

    val classes: StateFlow<List<ClassWithTimes>> =
        Graph.classRepository.observeAllWithTimes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class UiState(
        val classId: Long = 0,
        val billingType: Int = Enrollment.BILLING_SESSIONS,
        val sessionsText: String = "20",
        val bonusText: String = "0",
        val amountText: String = "",
        val validUntilText: String = "",
        val startDateText: String = LocalDate.now().toString(),
        val endDateText: String = LocalDate.now().plusMonths(4).toString(),
        val payDateText: String = LocalDate.now().toString(),
        val method: Int = 1,
        /** true = 当场收费；false = 定课未缴费（期末一起结） */
        val paidNow: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onClassId(v: Long) = _state.update { it.copy(classId = v, error = null) }
    fun onBillingType(v: Int) = _state.update { it.copy(billingType = v, error = null) }
    fun onSessions(v: String) = _state.update { it.copy(sessionsText = v, error = null) }
    fun onBonus(v: String) = _state.update { it.copy(bonusText = v) }
    fun onAmount(v: String) = _state.update { it.copy(amountText = v, error = null) }
    fun onValidUntil(v: String) = _state.update { it.copy(validUntilText = v) }
    fun onStartDate(v: String) = _state.update { it.copy(startDateText = v, error = null) }
    fun onEndDate(v: String) = _state.update { it.copy(endDateText = v, error = null) }
    fun onPayDate(v: String) = _state.update { it.copy(payDateText = v) }
    fun onMethod(v: Int) = _state.update { it.copy(method = v) }
    fun onPaidNow(v: Boolean) = _state.update { it.copy(paidNow = v, error = null) }

    /** 保存成功返回 true。 */
    suspend fun save(): Boolean {
        val s = _state.value
        val amountCents = if (s.amountText.isBlank()) 0L else MoneyUtils.parseYuanToCents(s.amountText)
        if (s.amountText.isNotBlank() && amountCents == null) {
            _state.update { it.copy(error = "金额格式错误") }
            return false
        }
        val draft = BillingDraft(
            classId = s.classId,
            billingType = s.billingType,
            sessions = s.sessionsText.toIntOrNull() ?: 0,
            bonusSessions = s.bonusText.toIntOrNull() ?: 0,
            startDate = if (s.billingType == Enrollment.BILLING_TERM) s.startDateText.trim() else null,
            endDate = if (s.billingType == Enrollment.BILLING_TERM) s.endDateText.trim() else null,
            amountCents = amountCents ?: 0,
            payDate = s.payDateText.trim(),
            method = s.method,
            validUntil = s.validUntilText.trim().ifBlank { null },
            paymentReceived = s.paidNow,
        )
        Billing.validate(draft)?.let { err ->
            _state.update { it.copy(error = err) }
            return false
        }
        if (enrollmentRepository.countFor(studentId, s.classId) > 0) {
            _state.update { it.copy(error = "该学生已在此班级，如需续费请到学生详情页操作") }
            return false
        }
        enrollmentRepository.enroll(studentId, draft)
        return true
    }

    companion object {
        fun factory(studentId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EnrollViewModel(Graph.enrollmentRepository, studentId) as T
        }
    }
}

/** 给学生报名新班级。 */
@Composable
fun EnrollScreen(studentId: Long, onDone: () -> Unit) {
    val vm: EnrollViewModel = viewModel(
        key = "enroll_$studentId",
        factory = EnrollViewModel.factory(studentId),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val classes by vm.classes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "报名新班级",
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

        Text("班级 *", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        ClassDropdown(classes, state.classId, vm::onClassId)

        Text("计费类型", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row {
            FilterChip(
                selected = state.billingType == Enrollment.BILLING_SESSIONS,
                onClick = { vm.onBillingType(Enrollment.BILLING_SESSIONS) },
                label = { Text("次卡") },
            )
            FilterChip(
                selected = state.billingType == Enrollment.BILLING_TERM,
                onClick = { vm.onBillingType(Enrollment.BILLING_TERM) },
                label = { Text("学期") },
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (state.billingType == Enrollment.BILLING_SESSIONS) {
            OutlinedTextField(state.sessionsText, vm::onSessions, label = { Text("购买次数 *") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(state.bonusText, vm::onBonus, label = { Text("赠送次数") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            com.wrh.keshiguanjia.ui.DateField("有效期（可选）", state.validUntilText.ifBlank { java.time.LocalDate.now().toString() }, { vm.onValidUntil(it.toString()) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        } else {
            com.wrh.keshiguanjia.ui.DateField("开始日期", state.startDateText, { vm.onStartDate(it.toString()) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            com.wrh.keshiguanjia.ui.DateField("结束日期", state.endDateText, { vm.onEndDate(it.toString()) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }

        OutlinedTextField(state.amountText, vm::onAmount, label = { Text("约定金额（元，可 + - × ÷）") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

        Text("缴费状态", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row {
            FilterChip(
                selected = state.paidNow,
                onClick = { vm.onPaidNow(true) },
                label = { Text("已收") },
            )
            FilterChip(
                selected = !state.paidNow,
                onClick = { vm.onPaidNow(false) },
                label = { Text("未收 · 期末结") },
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (state.paidNow) {
            Text("缴费日期", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            com.wrh.keshiguanjia.ui.DateField("缴费日期", state.payDateText, { vm.onPayDate(it.toString()) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))

            Text("缴费方式", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Row {
                Payment.METHOD_LABELS.take(3).forEachIndexed { i, label ->
                    FilterChip(
                        selected = state.method == i,
                        onClick = { vm.onMethod(i) },
                        label = { Text(label) },
                        modifier = Modifier.padding(start = if (i == 0) 0.dp else 8.dp),
                    )
                }
            }
        } else {
            Text(
                "将登记为欠款，收款后可在学生详情页补记缴费",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun ClassDropdown(
    classes: List<ClassWithTimes>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = classes.firstOrNull { it.clazz.id == selectedId }?.clazz?.name ?: "选择班级"
    Column {
        OutlinedButton(onClick = { expanded = true }) { Text(selectedName) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            classes.forEach { cw ->
                DropdownMenuItem(
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
