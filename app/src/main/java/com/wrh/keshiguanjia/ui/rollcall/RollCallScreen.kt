package com.wrh.keshiguanjia.ui.rollcall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wrh.keshiguanjia.data.Attendance
import com.wrh.keshiguanjia.data.Enrollment
import com.wrh.keshiguanjia.data.EnrollmentWithDetails
import com.wrh.keshiguanjia.data.Graph
import com.wrh.keshiguanjia.logic.Billing
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RollCallViewModel(
    private val repo: com.wrh.keshiguanjia.data.EnrollmentRepository,
    private val classId: Long,
    private val date: LocalDate,
) : ViewModel() {

    /** 每个报名：详情 + 已消课数 + 当日出勤状态 */
    data class Row(
        val details: EnrollmentWithDetails,
        val consumed: Int,
        val attendance: Attendance?,
    ) {
        val remaining: Int
            get() = if (details.enrollment.billingType == Enrollment.BILLING_SESSIONS) {
                Billing.remainingSessions(details.packages, consumed)
            } else {
                Int.MAX_VALUE
            }

        val isLowBalance: Boolean
            get() = details.enrollment.billingType == Enrollment.BILLING_SESSIONS && remaining <= LOW_BALANCE_THRESHOLD

        companion object {
            const val LOW_BALANCE_THRESHOLD = 3
        }
    }

    private val attendanceFlow = repo.observeAttendanceForClassDate(classId, date.toString())
    private val consumedFlow = repo.observeConsumedAll()

    val rows: StateFlow<List<Row>> = combine(
        repo.observeForClass(classId), consumedFlow, attendanceFlow,
    ) { enrollments, consumedMap, attendanceList ->
        val attendanceByStudent = attendanceList.associateBy { it.studentId }
        enrollments.map { e ->
            Row(
                details = e,
                consumed = consumedMap[e.enrollment.id] ?: 0,
                attendance = attendanceByStudent[e.enrollment.studentId],
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun mark(row: Row, status: Int) {
        viewModelScope.launch {
            repo.markAttendance(
                studentId = row.details.enrollment.studentId,
                enrollmentId = row.details.enrollment.id,
                classId = classId,
                dateIso = date.toString(),
                status = status,
            )
        }
    }

    fun clear(row: Row) {
        viewModelScope.launch {
            repo.clearAttendance(row.details.enrollment.studentId, classId, date.toString())
        }
    }

    fun markAllAttended(rows: List<Row>) {
        viewModelScope.launch {
            rows.filter { it.attendance == null }.forEach { row ->
                repo.markAttendance(
                    studentId = row.details.enrollment.studentId,
                    enrollmentId = row.details.enrollment.id,
                    classId = classId,
                    dateIso = date.toString(),
                    status = Attendance.STATUS_ATTENDED,
                )
            }
        }
    }

    companion object {
        fun factory(classId: Long, date: LocalDate) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RollCallViewModel(Graph.enrollmentRepository, classId, date) as T
        }
    }
}

/** 班级点名：按日期对学生批量标记到课/请假/缺勤，余额 ≤3 次红色提醒。 */
@Composable
fun RollCallScreen(
    classId: Long,
    dateIso: String,
    onBack: () -> Unit,
) {
    val date = remember { LocalDate.parse(dateIso) }
    val vm: RollCallViewModel = viewModel(
        key = "rollcall_${classId}_$dateIso",
        factory = RollCallViewModel.factory(classId, date),
    )
    val rows by vm.rows.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "点名 · ${date.monthValue}月${date.dayOfMonth}日",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                val attended = rows.count { it.attendance?.status == Attendance.STATUS_ATTENDED }
                val leave = rows.count { it.attendance?.status == Attendance.STATUS_LEAVE }
                val absent = rows.count { it.attendance?.status == Attendance.STATUS_ABSENT }
                Text(
                    "到课 $attended · 请假 $leave · 缺勤 $absent · 未标 ${rows.size - attended - leave - absent}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = { vm.markAllAttended(rows) }) { Text("全部到课") }
        }

        if (rows.isEmpty()) {
            Text(
                "该班级还没有学生报名",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(24.dp),
            )
        }

        LazyColumn {
            items(rows, key = { it.details.enrollment.id }) { row ->
                RollCallRow(row, onMark = { vm.mark(row, it) }, onClear = { vm.clear(row) })
            }
        }
    }
}

@Composable
private fun RollCallRow(
    row: RollCallViewModel.Row,
    onMark: (Int) -> Unit,
    onClear: () -> Unit,
) {
    val details = row.details
    val current = row.attendance?.status
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = if (current != null) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    details.student.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (details.enrollment.billingType == Enrollment.BILLING_SESSIONS) {
                    Text(
                        "剩 ${row.remaining} 次",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (row.isLowBalance) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "学期制",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (row.isLowBalance) {
                Text(
                    "课时不足，请提醒续费",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Attendance.STATUS_LABELS.forEachIndexed { index, label ->
                    FilterChip(
                        selected = current == index,
                        onClick = { onMark(index) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                if (current != null) {
                    TextButton(onClick = onClear) { Text("撤销") }
                }
            }
        }
    }
}
