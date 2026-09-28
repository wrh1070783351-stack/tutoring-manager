package com.wrh.keshiguanjia

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wrh.keshiguanjia.data.ClassRoom
import com.wrh.keshiguanjia.data.ClassTime
import com.wrh.keshiguanjia.data.Graph
import com.wrh.keshiguanjia.data.KeshiDatabase
import com.wrh.keshiguanjia.data.Student
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 向模拟器上的正式数据库种入样例数据（仅当库为空时），用于 UI 截图验收。
 * 数据使用英文假名，绝不写入真实学生信息。
 */
@RunWith(AndroidJUnit4::class)
class SeedDataTest {

    @Test
    fun seedSampleDataIfNeeded() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Graph.init(context)
        val db = KeshiDatabase.build(context)

        if (db.studentDao().count() == 0) {
            db.studentDao().insert(Student(name = "Tom", grade = "Grade 8", parentPhone = "13800000001", note = "sample"))
            db.studentDao().insert(Student(name = "Amy", grade = "Grade 7", parentPhone = "13800000002", note = "sample"))
            db.studentDao().insert(Student(name = "Leo", grade = "Grade 9", parentPhone = "13800000003", note = "sample"))
        }

        if (db.classDao().count() == 0) {
            val repo = Graph.classRepository
            val mathId = repo.saveWithTimes(
                ClassRoom(name = "Math A", subject = "Math"),
                listOf(
                    ClassTime(classId = 0, dayOfWeek = 1, startMinute = 1110, endMinute = 1230, room = "Room A"),
                    ClassTime(classId = 0, dayOfWeek = 6, startMinute = 600, endMinute = 720, room = "Room B"),
                ),
            )
            assertTrue(mathId > 0)
            repo.saveWithTimes(
                ClassRoom(name = "English B", subject = "English"),
                listOf(
                    ClassTime(classId = 0, dayOfWeek = 2, startMinute = 1140, endMinute = 1260, room = "Room C"),
                    ClassTime(classId = 0, dayOfWeek = 7, startMinute = 540, endMinute = 660, room = "Room A"),
                ),
            )
            repo.saveWithTimes(
                ClassRoom(name = "Physics C", subject = "Physics"),
                listOf(
                    ClassTime(classId = 0, dayOfWeek = 5, startMinute = 1140, endMinute = 1260, room = "Lab 1"),
                ),
            )
        }

        // 报名样例：Tom 报 Math A 次卡（20+2 次，¥2000，微信）
        if (db.enrollmentDao().countFor(studentNameId("Tom", db), mathClassId(db)) == 0) {
            val sid = studentNameId("Tom", db)
            val cid = mathClassId(db)
            if (sid > 0 && cid > 0) {
                Graph.enrollmentRepository.enroll(
                    sid,
                    com.wrh.keshiguanjia.logic.BillingDraft(
                        classId = cid,
                        billingType = com.wrh.keshiguanjia.data.Enrollment.BILLING_SESSIONS,
                        sessions = 20, bonusSessions = 2,
                        amountCents = 200000, payDate = "2026-09-01", method = 1, note = "sample",
                    ),
                )
            }
        }

        // 调休演示：下周一 Math A 停课（若当天尚无调整记录）
        if (db.lessonOverrideDao().forClassDate(mathClassId(db), nextMonday().toString()).isEmpty() && mathClassId(db) > 0) {
            Graph.scheduleRepository.cancelOccurrence(mathClassId(db), nextMonday().toString(), note = "sample 调休")
        }
        db.close()
    }

    private fun nextMonday(): java.time.LocalDate {
        var d = java.time.LocalDate.now().plusDays(1)
        while (d.dayOfWeek.value != 1) d = d.plusDays(1)
        return d
    }

    private suspend fun studentNameId(name: String, db: KeshiDatabase): Long =
        com.wrh.keshiguanjia.data.StudentRepository(db.studentDao())
            .observeAll().first().firstOrNull { it.name == name }?.id ?: 0

    private suspend fun mathClassId(db: KeshiDatabase): Long =
        db.classDao().observeAllWithTimes().first()
            .firstOrNull { it.clazz.name == "Math A" }?.clazz?.id ?: 0
}
