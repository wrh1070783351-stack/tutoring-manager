package com.wrh.keshiguanjia.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.wrh.keshiguanjia.data.Student
import java.io.File

/** CSV 导出（带 BOM，Excel 打开中文不乱码）与文件分享。 */
object CsvExporter {

    private fun esc(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** 学生课时余额表：一行 = 一个学生 × 一个班级。 */
    fun balanceTableCsv(
        enrollments: List<EnrollmentWithDetails>,
        consumedByEnrollment: Map<Long, Int>,
        payments: List<Payment>,
    ): String {
        val paymentsByEnrollment = payments.filter { it.enrollmentId != null }
            .groupBy { it.enrollmentId!! }
        val sb = StringBuilder("\uFEFF")
        sb.appendLine("学生,班级,计费类型,已购次数,已消次数,剩余次数,有效期至,约定金额,已收金额,未收金额")
        enrollments.forEach { e ->
            val isSessions = e.enrollment.billingType == Enrollment.BILLING_SESSIONS
            val purchased = if (isSessions) com.wrh.keshiguanjia.logic.Billing.purchasedSessions(e.packages) else 0
            val consumed = consumedByEnrollment[e.enrollment.id] ?: 0
            val remaining = if (isSessions) (purchased - consumed) else 0
            val validUntil = if (isSessions) {
                e.packages.mapNotNull { it.validUntil }.minOrNull() ?: ""
            } else {
                e.termRecords.map { it.endDate }.maxOrNull() ?: ""
            }
            val billed = e.packages.sumOf { it.amountCents } + e.termRecords.sumOf { it.amountCents }
            val paid = paymentsByEnrollment[e.enrollment.id].orEmpty().sumOf { it.amountCents }
            sb.appendLine(
                listOf(
                    esc(e.student.name), esc(e.clazz.name),
                    if (isSessions) "次卡" else "学期",
                    if (isSessions) purchased.toString() else "",
                    if (isSessions) consumed.toString() else "",
                    if (isSessions) remaining.toString() else "",
                    validUntil,
                    com.wrh.keshiguanjia.logic.MoneyUtils.yuanText(billed),
                    com.wrh.keshiguanjia.logic.MoneyUtils.yuanText(paid),
                    com.wrh.keshiguanjia.logic.MoneyUtils.yuanText((billed - paid).coerceAtLeast(0)),
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    /** 缴费明细表。 */
    fun paymentsCsv(students: List<Student>, payments: List<Payment>): String {
        val nameById = students.associate { it.id to it.name }
        val sb = StringBuilder("\uFEFF")
        sb.appendLine("日期,学生,金额(元),方式,备注")
        payments.forEach { p ->
            sb.appendLine(
                listOf(
                    p.date, esc(nameById[p.studentId] ?: "?"),
                    com.wrh.keshiguanjia.logic.MoneyUtils.yuanText(p.amountCents),
                    Payment.METHOD_LABELS.getOrElse(p.method) { "其他" },
                    esc(p.note),
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    /** 写入缓存 share 目录并返回文件。 */
    fun write(context: Context, fileName: String, content: String): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        return File(dir, fileName).apply { writeText(content, Charsets.UTF_8) }
    }

    /** 通过系统分享面板发送文件。 */
    fun shareFile(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享 ${file.name}"))
    }
}
