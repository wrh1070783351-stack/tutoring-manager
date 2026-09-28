package com.wrh.keshiguanjia.data

import androidx.room.withTransaction
import com.wrh.keshiguanjia.logic.BillingDraft
import kotlinx.coroutines.flow.Flow

/**
 * 报名/课时包/缴费的业务入口。
 * 消课计数自 M3 引入考勤表后接入，M2 阶段剩余次数 = 已购总次数。
 */
class EnrollmentRepository(
    private val enrollmentDao: EnrollmentDao,
    private val packageDao: ClassPackageDao,
    private val termDao: TermRecordDao,
    private val paymentDao: PaymentDao,
    private val db: KeshiDatabase,
    private val attendanceDao: AttendanceDao,
) {
    fun observeForStudent(studentId: Long): Flow<List<EnrollmentWithDetails>> =
        enrollmentDao.observeForStudent(studentId)

    fun observePaymentsForStudent(studentId: Long): Flow<List<Payment>> =
        paymentDao.observeForStudent(studentId)

    suspend fun countFor(studentId: Long, classId: Long): Int =
        enrollmentDao.countFor(studentId, classId)

    /** 报名并生成首个计费记录与对应缴费记录（同一事务）。 */
    suspend fun enroll(studentId: Long, draft: BillingDraft): Long = db.withTransaction {
        val enrollmentId = enrollmentDao.insert(
            Enrollment(studentId = studentId, classId = draft.classId, billingType = draft.billingType)
        )
        applyBilling(enrollmentId, studentId, draft)
        enrollmentId
    }

    /** 续费：向既有报名追加课时包/学期记录，并生成缴费记录。 */
    suspend fun renew(enrollment: Enrollment, draft: BillingDraft) = db.withTransaction {
        applyBilling(enrollment.id, enrollment.studentId, draft)
    }

    private suspend fun applyBilling(enrollmentId: Long, studentId: Long, draft: BillingDraft) {
        when (draft.billingType) {
            Enrollment.BILLING_SESSIONS -> packageDao.insert(
                ClassPackage(
                    enrollmentId = enrollmentId,
                    totalSessions = draft.sessions,
                    bonusSessions = draft.bonusSessions,
                    amountCents = draft.amountCents,
                    purchasedAt = System.currentTimeMillis(),
                    validUntil = draft.validUntil,
                )
            )
            Enrollment.BILLING_TERM -> termDao.insert(
                TermRecord(
                    enrollmentId = enrollmentId,
                    startDate = draft.startDate!!,
                    endDate = draft.endDate!!,
                    amountCents = draft.amountCents,
                )
            )
        }
        // 缴费记录：仅当场收到钱才生成；未收（期末结）时只有计费记录，欠款由详情页登记缴费补上
        if (draft.paymentReceived && draft.amountCents > 0) {
            paymentDao.insert(
                Payment(
                    studentId = studentId,
                    enrollmentId = enrollmentId,
                    amountCents = draft.amountCents,
                    date = draft.payDate,
                    method = draft.method,
                    note = draft.note,
                )
            )
        }
    }

    /** 补记一笔缴费（登记欠款到账等）。 */
    suspend fun addPayment(
        studentId: Long, enrollmentId: Long?, amountCents: Long,
        dateIso: String, method: Int, note: String = "",
    ) = paymentDao.insert(
        Payment(
            studentId = studentId, enrollmentId = enrollmentId,
            amountCents = amountCents, date = dateIso, method = method, note = note,
        )
    )

    suspend fun deletePaymentById(id: Long) = paymentDao.deleteById(id)

    // ---- 点名消课（M3）----

    fun observeForClass(classId: Long): Flow<List<EnrollmentWithDetails>> =
        enrollmentDao.observeForClass(classId)

    fun observeAttendanceForClassDate(classId: Long, dateIso: String): Flow<List<Attendance>> =
        attendanceDao.observeForClassDate(classId, dateIso)

    fun observeConsumedAll(): Flow<Map<Long, Int>> = attendanceDao.observeConsumedAll()

    fun observeConsumedForStudent(studentId: Long): Flow<Map<Long, Int>> =
        attendanceDao.observeConsumedForStudent(studentId)

    fun observeAttendanceForStudent(studentId: Long): Flow<List<Attendance>> =
        attendanceDao.observeForStudent(studentId)

    /** 标记某个学生的出勤；已存在则修改（可反复改状态）。 */
    suspend fun markAttendance(studentId: Long, enrollmentId: Long, classId: Long, dateIso: String, status: Int) {
        val existing = attendanceDao.getFor(studentId, classId, dateIso)
        when (existing) {
            null -> attendanceDao.insert(
                Attendance(
                    studentId = studentId, classId = classId,
                    enrollmentId = enrollmentId, date = dateIso, status = status,
                )
            )
            else -> attendanceDao.updateRecord(existing.copy(status = status))
        }
    }

    /** 清除某学生某班某天的出勤记录（撤销）。 */
    suspend fun clearAttendance(studentId: Long, classId: Long, dateIso: String) {
        attendanceDao.getFor(studentId, classId, dateIso)?.let { attendanceDao.deleteById(it.id) }
    }
}

/** 课表调整（调休）的业务入口。 */
class ScheduleRepository(
    private val classDao: ClassDao,
    private val classTimeDao: ClassTimeDao,
    private val overrideDao: LessonOverrideDao,
    private val db: KeshiDatabase,
) {
    fun observeClassesWithTimes(): Flow<List<ClassWithTimes>> = classDao.observeAllWithTimes()
    fun observeOverrides(): Flow<List<LessonOverride>> = overrideDao.observeAll()

    suspend fun overrideForClassDate(classId: Long, dateIso: String): List<LessonOverride> =
        overrideDao.forClassDate(classId, dateIso)

    /** 某日固定课停课。 */
    suspend fun cancelOccurrence(classId: Long, dateIso: String, note: String = "") =
        overrideDao.insert(
            LessonOverride(
                classId = classId, date = dateIso,
                type = LessonOverride.TYPE_CANCEL, note = note,
            )
        )

    /** 某日加课（临时约课）。 */
    suspend fun addOccurrence(classId: Long, dateIso: String, startMinute: Int, endMinute: Int, note: String = "") =
        overrideDao.insert(
            LessonOverride(
                classId = classId, date = dateIso,
                type = LessonOverride.TYPE_ADD,
                startMinute = startMinute, endMinute = endMinute, note = note,
            )
        )

    /** 调课：原日期停课 + 新日期按原时间加课（同一事务）。 */
    suspend fun reschedule(
        classId: Long, fromDateIso: String, toDateIso: String,
        startMinute: Int, endMinute: Int, note: String = "",
    ) = db.withTransaction {
        overrideDao.insert(
            LessonOverride(
                classId = classId, date = fromDateIso,
                type = LessonOverride.TYPE_CANCEL, note = note,
            )
        )
        overrideDao.insert(
            LessonOverride(
                classId = classId, date = toDateIso,
                type = LessonOverride.TYPE_ADD,
                startMinute = startMinute, endMinute = endMinute, note = note,
            )
        )
    }

    /** 撤销某条调整记录（撤销停课 / 删除加课）。 */
    suspend fun deleteOverride(id: Long) = overrideDao.deleteById(id)
}
