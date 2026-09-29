package com.wrh.keshiguanjia

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wrh.keshiguanjia.data.Attendance
import com.wrh.keshiguanjia.data.ClassRoom
import com.wrh.keshiguanjia.data.Enrollment
import com.wrh.keshiguanjia.data.Graph
import com.wrh.keshiguanjia.data.KeshiDatabase
import com.wrh.keshiguanjia.data.Student
import com.wrh.keshiguanjia.logic.Billing
import com.wrh.keshiguanjia.logic.BillingDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 报名/续费/缴费事务与级联删除（内存库，跑在模拟器上）。 */
@RunWith(AndroidJUnit4::class)
class EnrollmentRepositoryTest {

    private lateinit var db: KeshiDatabase
    private lateinit var repo: com.wrh.keshiguanjia.data.EnrollmentRepository
    private var studentId = 0L
    private var classId = 0L

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, KeshiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = com.wrh.keshiguanjia.data.EnrollmentRepository(
            db.enrollmentDao(), db.classPackageDao(), db.termRecordDao(), db.paymentDao(), db, db.attendanceDao(),
        )
        studentId = db.studentDao().insert(Student(name = "Tom"))
        classId = db.classDao().insert(ClassRoom(name = "Math A", subject = "Math"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun enrollSessions_createsPackageAndPayment() = runBlocking {
        val eid = repo.enroll(
            studentId,
            BillingDraft(
                classId = classId, billingType = Enrollment.BILLING_SESSIONS,
                sessions = 20, bonusSessions = 2, amountCents = 200000,
                payDate = "2026-09-29", method = 1,
            ),
        )
        val details = repo.observeForStudent(studentId).first().single()
        assertEquals(eid, details.enrollment.id)
        assertEquals("Math A", details.clazz.name)
        assertEquals(1, details.packages.size)
        assertEquals(22, Billing.purchasedSessions(details.packages))
        val payments = repo.observePaymentsForStudent(studentId).first()
        assertEquals(1, payments.size)
        assertEquals(200000L, payments[0].amountCents)
    }

    @Test
    fun enrollTerm_createsTermRecord() = runBlocking {
        repo.enroll(
            studentId,
            BillingDraft(
                classId = classId, billingType = Enrollment.BILLING_TERM,
                startDate = "2026-09-01", endDate = "2027-01-31",
                amountCents = 500000, payDate = "2026-09-01",
            ),
        )
        val details = repo.observeForStudent(studentId).first().single()
        assertEquals(0, details.packages.size)
        assertEquals("2027-01-31", Billing.termValidUntil(details.termRecords.map { it.endDate }))
    }

    @Test
    fun renew_appendsPackageAndPayment() = runBlocking {
        val eid = repo.enroll(
            studentId,
            BillingDraft(
                classId = classId, billingType = Enrollment.BILLING_SESSIONS,
                sessions = 10, amountCents = 100000, payDate = "2026-09-01",
            ),
        )
        val enrollment = Enrollment(id = eid, studentId = studentId, classId = classId)
        repo.renew(
            enrollment,
            BillingDraft(
                classId = classId, billingType = Enrollment.BILLING_SESSIONS,
                sessions = 20, amountCents = 180000, payDate = "2026-10-01",
            ),
        )
        val details = repo.observeForStudent(studentId).first().single()
        assertEquals(2, details.packages.size)
        assertEquals(30, Billing.purchasedSessions(details.packages))
        assertEquals(2, repo.observePaymentsForStudent(studentId).first().size)
    }

    @Test
    fun duplicateEnrollmentDetectable() = runBlocking {
        repo.enroll(studentId, BillingDraft(classId = classId, billingType = Enrollment.BILLING_SESSIONS, sessions = 10, amountCents = 0, payDate = "2026-09-29"))
        assertTrue(repo.countFor(studentId, classId) > 0)
        assertTrue(repo.countFor(studentId, classId + 1) == 0)
    }

    @Test
    fun enrollWithoutPayment_noPaymentRecord_thenRegisterLater() = runBlocking {
        // 定课未缴费：计费记录存在，但没有任何缴费流水
        repo.enroll(
            studentId,
            BillingDraft(
                classId = classId, billingType = Enrollment.BILLING_SESSIONS,
                sessions = 10, amountCents = 100000, payDate = "2026-09-29",
                paymentReceived = false,
            ),
        )
        assertTrue(repo.observePaymentsForStudent(studentId).first().isEmpty())

        // 期末登记缴费 → 生成流水
        repo.addPayment(studentId, enrollmentId = db.enrollmentDao().getById(1)!!.id, amountCents = 100000, dateIso = "2027-01-15", method = 2)
        val payments = repo.observePaymentsForStudent(studentId).first()
        assertEquals(1, payments.size)
        assertEquals(100000L, payments[0].amountCents)
    }

    @Test
    fun deletingStudent_cascadesEverything() = runBlocking {
        val eid = repo.enroll(
            studentId,
            BillingDraft(classId = classId, billingType = Enrollment.BILLING_SESSIONS, sessions = 10, amountCents = 100000, payDate = "2026-09-29"),
        )
        db.studentDao().deleteById(studentId)
        assertTrue(db.enrollmentDao().observeForStudent(studentId).first().isEmpty())
        assertEquals(null, db.enrollmentDao().getById(eid))
        assertTrue(db.paymentDao().observeForStudent(studentId).first().isEmpty())
    }

    @Test
    fun deleteEnrollment_cascadesPackagesButKeepsPayments() = runBlocking {
        val eid = repo.enroll(
            studentId,
            BillingDraft(classId = classId, billingType = Enrollment.BILLING_SESSIONS, sessions = 10, amountCents = 100000, payDate = "2026-09-29"),
        )
        repo.deleteEnrollment(eid)
        assertEquals(null, db.enrollmentDao().getById(eid))
        // 课时包随报名级联删除；缴费流水保留（历史记录）
        assertTrue(repo.observeForStudent(studentId).first().isEmpty())
        assertEquals(1, repo.observePaymentsForStudent(studentId).first().size)
    }

    @Test
    fun termSessions_accumulateAttendedOnly_thenSettle() = runBlocking {
        val eid = repo.enroll(
            studentId,
            BillingDraft(
                classId = classId, billingType = Enrollment.BILLING_TERM_SESSIONS,
                startDate = "2026-09-01", endDate = "2027-01-31",
                unitPriceCents = 20000, payDate = "2026-09-01",
            ),
        )
        // 到课 2 次 + 请假 1 + 缺勤 1 + 学期窗口外的到课 1 → 只计窗口内到课 2 次
        repo.markAttendance(studentId, eid, classId, "2026-09-10", Attendance.STATUS_ATTENDED)
        repo.markAttendance(studentId, eid, classId, "2026-09-17", Attendance.STATUS_ATTENDED)
        repo.markAttendance(studentId, eid, classId, "2026-09-24", Attendance.STATUS_LEAVE)
        repo.markAttendance(studentId, eid, classId, "2026-10-01", Attendance.STATUS_ABSENT)
        repo.markAttendance(studentId, eid, classId, "2026-08-20", Attendance.STATUS_ATTENDED)

        val term = repo.observeForStudent(studentId).first().single().termRecords.single()
        assertEquals(20000L, term.unitPriceCents)
        assertEquals(0L, term.amountCents) // 未结算

        val count = repo.settleTerm(term)
        assertEquals(2, count)
        val settled = repo.observeForStudent(studentId).first().single().termRecords.single()
        assertEquals(40000L, settled.amountCents)
        // 结算只登记应收，不自动生成缴费流水（收款走登记缴费）
        assertEquals(0, repo.observePaymentsForStudent(studentId).first().size)
    }

    @Test
    fun termCountsAndAttendedStats() = runBlocking {
        val eid = repo.enroll(
            studentId,
            BillingDraft(
                classId = classId, billingType = Enrollment.BILLING_TERM_SESSIONS,
                startDate = "2026-09-01", endDate = "2027-01-31",
                unitPriceCents = 20000, payDate = "2026-09-01",
            ),
        )
        repo.markAttendance(studentId, eid, classId, "2026-09-10", Attendance.STATUS_ATTENDED)
        repo.markAttendance(studentId, eid, classId, "2026-09-17", Attendance.STATUS_LEAVE)

        val counts = db.attendanceDao().observeTermAttendedCounts().first()
        val row = counts.single { it.enrollmentId == eid }
        assertEquals(1, row.attended)

        val stats = db.attendanceDao().observeAttendedForStudent(studentId).first()
        assertEquals(1, stats.size)
        assertEquals(classId, stats[0].attendance.classId)
    }
}
