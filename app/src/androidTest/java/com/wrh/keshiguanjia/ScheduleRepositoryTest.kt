package com.wrh.keshiguanjia

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wrh.keshiguanjia.data.ClassRoom
import com.wrh.keshiguanjia.data.ClassTime
import com.wrh.keshiguanjia.data.KeshiDatabase
import com.wrh.keshiguanjia.data.LessonOverride
import com.wrh.keshiguanjia.logic.ScheduleLogic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** 调休操作（停课/加课/调课/撤销）与课表展开的集成测试（内存库，跑在模拟器上）。 */
@RunWith(AndroidJUnit4::class)
class ScheduleRepositoryTest {

    private lateinit var db: KeshiDatabase
    private lateinit var repo: com.wrh.keshiguanjia.data.ScheduleRepository
    private var classId = 0L
    private val monday = LocalDate.parse("2026-09-28")

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, KeshiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = com.wrh.keshiguanjia.data.ScheduleRepository(
            db.classDao(), db.classTimeDao(), db.lessonOverrideDao(), db,
        )
        classId = db.classDao().insert(ClassRoom(name = "Math A"))
        db.classTimeDao().insertAll(
            listOf(ClassTime(classId = classId, dayOfWeek = 1, startMinute = 1110, endMinute = 1230))
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun classesList() = db.classDao().observeAllWithTimes().first()
    private suspend fun overrides() = db.lessonOverrideDao().observeAll().first()

    @Test
    fun cancel_thenUndo() = runBlocking {
        repo.cancelOccurrence(classId, monday.toString())
        var lessons = ScheduleLogic.lessonsForDate(classesList(), overrides(), monday)
        assertEquals(1, lessons.size)
        assertTrue(lessons[0].isCancelled)

        repo.deleteOverride(lessons[0].overrideId!!)
        lessons = ScheduleLogic.lessonsForDate(classesList(), overrides(), monday)
        assertEquals(1, lessons.size)
        assertFalse(lessons[0].isCancelled)
    }

    @Test
    fun addOccurrence_showsAsExtra() = runBlocking {
        val friday = LocalDate.parse("2026-10-02") // 周五，本来无课
        repo.addOccurrence(classId, friday.toString(), 540, 660)
        val lessons = ScheduleLogic.lessonsForDate(classesList(), overrides(), friday)
        assertEquals(1, lessons.size)
        assertTrue(lessons[0].isExtra)
        assertEquals(540, lessons[0].startMinute)

        repo.deleteOverride(lessons[0].overrideId!!)
        assertTrue(ScheduleLogic.lessonsForDate(classesList(), overrides(), friday).isEmpty())
    }

    @Test
    fun reschedule_movesLessonToNewDateKeepingTime() = runBlocking {
        val to = LocalDate.parse("2026-09-25") // 上周五（调休补班场景）
        repo.reschedule(classId, monday.toString(), to.toString(), 1110, 1230)
        assertEquals(1, ScheduleLogic.lessonsForDate(classesList(), overrides(), monday).size)
        assertTrue(ScheduleLogic.lessonsForDate(classesList(), overrides(), monday)[0].isCancelled)

        val moved = ScheduleLogic.lessonsForDate(classesList(), overrides(), to)
        assertEquals(1, moved.size)
        assertTrue(moved[0].isExtra)
        assertEquals(1110, moved[0].startMinute)
        assertEquals(1230, moved[0].endMinute)
    }

    @Test
    fun cancelOnlyThatDate_notWholeWeeklyTime() = runBlocking {
        repo.cancelOccurrence(classId, monday.toString())
        val nextMonday = monday.plusWeeks(1)
        val lessons = ScheduleLogic.lessonsForDate(classesList(), overrides(), nextMonday)
        assertEquals(1, lessons.size)
        assertFalse(lessons[0].isCancelled)
    }
}
