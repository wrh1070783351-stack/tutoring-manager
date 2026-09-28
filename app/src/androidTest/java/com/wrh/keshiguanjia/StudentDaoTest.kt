package com.wrh.keshiguanjia

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wrh.keshiguanjia.data.KeshiDatabase
import com.wrh.keshiguanjia.data.Student
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 学生表 DAO 测试：内存数据库，跑在模拟器上。 */
@RunWith(AndroidJUnit4::class)
class StudentDaoTest {

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
    fun insert_thenGetById_returnsStudent() = runBlocking {
        val id = db.studentDao().insert(Student(name = "Tom", grade = "Grade 8", parentPhone = "13800000001"))
        val loaded = db.studentDao().getById(id)
        assertEquals("Tom", loaded?.name)
        assertEquals("Grade 8", loaded?.grade)
        assertEquals("13800000001", loaded?.parentPhone)
    }

    @Test
    fun update_changesFields() = runBlocking {
        val id = db.studentDao().insert(Student(name = "Tom"))
        db.studentDao().update(Student(id = id, name = "Tommy", note = "renamed"))
        assertEquals("Tommy", db.studentDao().getById(id)?.name)
    }

    @Test
    fun deleteById_removesStudent() = runBlocking {
        val id = db.studentDao().insert(Student(name = "ToBeDeleted"))
        db.studentDao().deleteById(id)
        assertNull(db.studentDao().getById(id))
        assertTrue(db.studentDao().observeAll().first().isEmpty())
    }

    @Test
    fun observeAll_ordersByCreatedAtDesc() = runBlocking {
        db.studentDao().insert(Student(name = "First", createdAt = 1000))
        db.studentDao().insert(Student(name = "Second", createdAt = 2000))
        val all = db.studentDao().observeAll().first()
        assertEquals(listOf("Second", "First"), all.map { it.name })
    }
}
