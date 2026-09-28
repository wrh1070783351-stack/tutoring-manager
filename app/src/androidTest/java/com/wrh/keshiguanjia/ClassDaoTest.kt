package com.wrh.keshiguanjia

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wrh.keshiguanjia.data.ClassRoom
import com.wrh.keshiguanjia.data.ClassTime
import com.wrh.keshiguanjia.data.KeshiDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 班级/时间段 DAO 与级联删除测试：内存数据库，跑在模拟器上。 */
@RunWith(AndroidJUnit4::class)
class ClassDaoTest {

    private lateinit var db: KeshiDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, KeshiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveWithTimes_replacesWholeSchedule() = runBlocking {
        val repo = com.wrh.keshiguanjia.data.ClassRepository(
            db.classDao(), db.classTimeDao(), db,
        )
        val classId = repo.saveWithTimes(
            ClassRoom(name = "Math A", subject = "Math"),
            listOf(
                ClassTime(classId = 0, dayOfWeek = 6, startMinute = 600, endMinute = 720, room = "Room B"),
                ClassTime(classId = 0, dayOfWeek = 1, startMinute = 1110, endMinute = 1230, room = "Room A"),
            ),
        )
        // 编辑：整体替换为单个时间段
        repo.saveWithTimes(
            ClassRoom(id = classId, name = "Math A2", subject = "Math"),
            listOf(ClassTime(classId = 0, dayOfWeek = 3, startMinute = 540, endMinute = 660)),
        )
        val loaded = repo.getWithTimes(classId)!!
        assertEquals("Math A2", loaded.clazz.name)
        assertEquals(1, loaded.times.size)
        assertEquals(540, loaded.times[0].startMinute)
    }

    @Test
    fun observeAllWithTimes_embedsRelation() = runBlocking {
        val classId = db.classDao().insert(ClassRoom(name = "English B"))
        db.classTimeDao().insertAll(
            listOf(
                ClassTime(classId = classId, dayOfWeek = 2, startMinute = 1140, endMinute = 1260),
            )
        )
        val all = db.classDao().observeAllWithTimes().first()
        val target = all.first { it.clazz.id == classId }
        assertEquals(1, target.times.size)
        assertEquals(2, target.times[0].dayOfWeek)
    }

    @Test
    fun deletingClass_cascadesTimes() = runBlocking {
        val classId = db.classDao().insert(ClassRoom(name = "Physics C"))
        db.classTimeDao().insertAll(
            listOf(
                ClassTime(classId = classId, dayOfWeek = 5, startMinute = 600, endMinute = 720),
                ClassTime(classId = classId, dayOfWeek = 5, startMinute = 800, endMinute = 900),
            )
        )
        assertEquals(2, db.classTimeDao().getForClass(classId).size)
        db.classDao().deleteById(classId)
        assertTrue(db.classTimeDao().getForClass(classId).isEmpty())
        assertTrue(db.classDao().observeAllWithTimes().first().none { it.clazz.id == classId })
    }
}
