package com.wrh.keshiguanjia.ui.stats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wrh.keshiguanjia.data.BackupManager
import com.wrh.keshiguanjia.data.CsvExporter
import com.wrh.keshiguanjia.data.Graph
import com.wrh.keshiguanjia.data.Payment
import com.wrh.keshiguanjia.logic.Billing
import com.wrh.keshiguanjia.logic.MoneyUtils
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatsViewModel(private val appContext: android.content.Context) : ViewModel() {

    data class UiState(
        val studentCount: Int = 0,
        val openClassCount: Int = 0,
        val consumedThisWeek: Int = 0,
        val monthIncomeCents: Long = 0,
        val yearIncomeCents: Long = 0,
        val unpaidCents: Long = 0,
        val enrollments: List<com.wrh.keshiguanjia.data.EnrollmentWithDetails> = emptyList(),
        val consumedAll: Map<Long, Int> = emptyMap(),
        val payments: List<Payment> = emptyList(),
        val students: List<com.wrh.keshiguanjia.data.Student> = emptyList(),
    )

    val state: StateFlow<UiState> = combine(
        combine(
            Graph.studentRepository.observeAll(),
            Graph.classRepository.observeAllWithTimes(),
            Graph.enrollmentRepository.observeAllEnrollments(),
        ) { students, classes, enrollments -> Triple(students, classes, enrollments) },
        combine(
            Graph.enrollmentRepository.observeConsumedAll(),
            Graph.enrollmentRepository.observeAllPayments(),
            Graph.enrollmentRepository.observeConsumedThisWeek(LocalDate.now()),
        ) { consumedAll, payments, consumedThisWeek -> Triple(consumedAll, payments, consumedThisWeek) },
    ) { (students, classes, enrollments), (consumedAll, payments, consumedThisWeek) ->
        val today = LocalDate.now()
        val ym = "%04d-%02d".format(today.year, today.monthValue)
        val year = today.year.toString()
        val paymentsByEnrollment = payments.filter { it.enrollmentId != null }.groupBy { it.enrollmentId!! }
        var unpaid = 0L
        enrollments.forEach { e ->
            val billed = e.packages.sumOf { it.amountCents } + e.termRecords.sumOf { it.amountCents }
            val paid = paymentsByEnrollment[e.enrollment.id].orEmpty().sumOf { it.amountCents }
            if (billed - paid > 0) unpaid += billed - paid
        }
        UiState(
            studentCount = students.size,
            openClassCount = classes.count { it.clazz.status == com.wrh.keshiguanjia.data.ClassRoom.STATUS_OPEN },
            consumedThisWeek = consumedThisWeek,
            monthIncomeCents = payments.filter { it.date.startsWith(ym) }.sumOf { it.amountCents },
            yearIncomeCents = payments.filter { it.date.startsWith(year) }.sumOf { it.amountCents },
            unpaidCents = unpaid,
            enrollments = enrollments,
            consumedAll = consumedAll,
            payments = payments,
            students = students,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun saveSettings(lowBalance: Int, expiryDays: Int) {
        com.wrh.keshiguanjia.logic.Settings.save(appContext, lowBalance, expiryDays)
    }

    companion object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StatsViewModel(Graph.appContextForVm()) as T
    }
}

/** 统计 + 提醒设置 + 数据安全（导出 / 备份 / 恢复）。 */
@Composable
fun StatsScreen() {
    val vm: StatsViewModel = viewModel(factory = StatsViewModel.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lowBalanceText by remember {
        mutableStateOf(com.wrh.keshiguanjia.logic.Settings.lowBalanceThreshold(context).toString())
    }
    var expiryDaysText by remember {
        mutableStateOf(com.wrh.keshiguanjia.logic.Settings.expiryWarnDays(context).toString())
    }
    var settingsSaved by remember { mutableStateOf(false) }
    var restoreResult by remember { mutableStateOf<Boolean?>(null) }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BackupManager.restore(context, input, Graph.database())
                } ?: false
            }
            restoreResult = ok
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        Text(
            "统计",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )

        // 指标网格
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCell("在读学生", "${state.studentCount}", Modifier.weight(1f))
                    MetricCell("开班中", "${state.openClassCount}", Modifier.weight(1f))
                    MetricCell("本周消课", "${state.consumedThisWeek} 次", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCell("本月收入", "¥" + MoneyUtils.yuanText(state.monthIncomeCents), Modifier.weight(1f), emphasize = true)
                    MetricCell("本年收入", "¥" + MoneyUtils.yuanText(state.yearIncomeCents), Modifier.weight(1f), emphasize = true)
                    MetricCell("未收欠款", "¥" + MoneyUtils.yuanText(state.unpaidCents), Modifier.weight(1f), danger = state.unpaidCents > 0)
                }
            }
        }

        // 提醒设置
        SectionTitle("提醒设置")
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lowBalanceText,
                        onValueChange = { lowBalanceText = it; settingsSaved = false },
                        label = { Text("课时不足 ≤ (次)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = expiryDaysText,
                        onValueChange = { expiryDaysText = it; settingsSaved = false },
                        label = { Text("到期提前 (天)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = {
                        val low = lowBalanceText.toIntOrNull() ?: 3
                        val days = expiryDaysText.toIntOrNull() ?: 14
                        lowBalanceText = low.toString()
                        expiryDaysText = days.toString()
                        vm.saveSettings(low, days)
                        settingsSaved = true
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(if (settingsSaved) "已保存 ✓" else "保存设置") }
            }
        }

        // 数据安全
        SectionTitle("数据安全")
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val csv = CsvExporter.balanceTableCsv(state.enrollments, state.consumedAll, state.payments)
                            val file = CsvExporter.write(context, "课时余额表.csv", csv)
                            CsvExporter.shareFile(context, file, "text/csv")
                        }
                    }, modifier = Modifier.weight(1f)) { Text("导出余额 CSV") }
                    OutlinedButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val csv = CsvExporter.paymentsCsv(state.students, state.payments)
                            val file = CsvExporter.write(context, "缴费明细.csv", csv)
                            CsvExporter.shareFile(context, file, "text/csv")
                        }
                    }, modifier = Modifier.weight(1f)) { Text("导出缴费 CSV") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            BackupManager.backup(context, Graph.database())?.let { file ->
                                CsvExporter.shareFile(context, file, "application/octet-stream")
                            }
                        }
                    }, modifier = Modifier.weight(1f)) { Text("备份数据库") }
                    OutlinedButton(onClick = {
                        restoreLauncher.launch(arrayOf("*/*"))
                    }, modifier = Modifier.weight(1f)) { Text("恢复数据库") }
                }
                Text(
                    "备份会生成完整数据文件，请通过分享面板存到微信/网盘等位置；恢复会覆盖当前全部数据并重启应用。建议每周备份一次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // 缴费明细
        SectionTitle("缴费明细（最近 20 条）")
        if (state.payments.isEmpty()) {
            Text(
                "暂无缴费记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        val nameById = state.students.associate { it.id to it.name }
        state.payments.take(20).forEach { p ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (nameById[p.studentId] ?: "?") + "  ¥" + MoneyUtils.yuanText(p.amountCents),
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
                    (Payment.METHOD_LABELS.getOrElse(p.method) { "其他" } + p.note.ifBlank { "" }).trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.padding(top = 6.dp))
            }
        }
    }

    restoreResult?.let { ok ->
        AlertDialog(
            onDismissRequest = { restoreResult = null },
            title = { Text(if (ok) "恢复成功" else "恢复失败") },
            text = {
                Text(
                    if (ok) "数据已恢复，应用即将重启。"
                    else "所选文件不是有效的备份文件。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    restoreResult = null
                    if (ok) BackupManager.restartApp(context)
                }) { Text(if (ok) "立即重启" else "知道了") }
            },
        )
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
private fun MetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
    danger: Boolean = false,
) {
    Column(
        modifier.background(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.shapes.medium,
        ).padding(10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                danger -> MaterialTheme.colorScheme.error
                emphasize -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
