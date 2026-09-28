package com.wrh.keshiguanjia

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wrh.keshiguanjia.data.Attendance
import com.wrh.keshiguanjia.data.ClassRoom
import com.wrh.keshiguanjia.data.Enrollment
import com.wrh.keshiguanjia.data.KeshiDatabase
import com.wrh.keshiguanjia.data.Student
import com.wrh.keshiguanjia.logic.Billing
import com.wrh.keshiguanjia.logic.BillingDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 点名消课：标记/修改/撤销、消课计数、剩余次数联动（内存库，跑在模拟器上）。 */
@RunWith(AndroidJUnit4::class)
class AttendanceRepositoryTest {

    private lateinit var db: KeshiDatabase
    private lateinit var repo: com.wrh.keshiguanjia.data.EnrollmentRepository
    private var studentId = 0L
    private var classId = 0L
    private var enrollmentId = 0L
    private val date = "2026-09-29"

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
        classId = db.classDao().insert(ClassRoom(name = "Math A"))
        enrollmentId = repo.enroll(
            studentId,
            BillingDraft(
                classId = classId, billingType = Enrollment.BILLING_SESSIONS,
                sessions = 5, amountCents = 50000, payDate = date,
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun consumed(): Int =
        db.attendanceDao().observeConsumedForStudent(studentId).first()[enrollmentId] ?: 0

    @Test
    fun markAttended_consumesOneSession() = runBlocking {
        repo.markAttendance(studentId, enrollmentId, classId, date, Attendance.STATUS_ATTENDED)
        assertEquals(1, consumed())
        val details = repo.observeForStudent(studentId).first().single()
        assertEquals(4, Billing.remainingSessions(details.packages, consumed()))
    }

    @Test
    fun leave_doesNotConsume() = runBlocking {
        repo.markAttendance(studentId, enrollmentId, classId, date, Attendance.STATUS_LEAVE)
        assertEquals(0, consumed())
    }

    @Test
    fun absent_consumesOneSession() = runBlocking {
        repo.markAttendance(studentId, enrollmentId, classId, date, Attendance.STATUS_ABSENT)
        assertEquals(1, consumed())
    }

    @Test
    fun remarking_updatesExistingRecord() = runBlocking {
        repo.markAttendance(studentId, enrollmentId, classId, date, Attendance.STATUS_ATTENDED)
        repo.markAttendance(studentId, enrollmentId, classId, date, Attendance.STATUS_LEAVE)
        val record = db.attendanceDao().getFor(studentId, classId, date)
        assertEquals(Attendance.STATUS_LEAVE, record?.status)
        assertEquals(0, consumed())
    }

    @Test
    fun clear_removesRecord() = runBlocking {
        repo.markAttendance(studentId, enrollmentId, classId, date, Attendance.STATUS_ATTENDED)
        repo.clearAttendance(studentId, classId, date)
        assertNull(db.attendanceDao().getFor(studentId, classId, date))
        assertEquals(0, consumed())
    }

    @Test
    fun differentDates_consumeIndependently() = runBlocking {
        repo.markAttendance(studentId, enrollmentId, classId, "2026-09-22", Attendance.STATUS_ATTENDED)
        repo.markAttendance(studentId, enrollmentId, classId, date, Attendance.STATUS_ATTENDED)
        assertEquals(2, consumed())
        val details = repo.observeForStudent(studentId).first().single()
        assertEquals(3, Billing.remainingSessions(details.packages, consumed()))
    }
}
