package com.wrh.keshiguanjia

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wrh.keshiguanjia.data.BackupManager
import com.wrh.keshiguanjia.data.ClassRoom
import com.wrh.keshiguanjia.data.KeshiDatabase
import com.wrh.keshiguanjia.data.Student
import com.wrh.keshiguanjia.data.CsvExporter
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 备份/恢复与 CSV 导出（跑在模拟器上）。 */
@RunWith(AndroidJUnit4::class)
class BackupExportTest {

    private lateinit var context: Context
    private lateinit var db: KeshiDatabase

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, KeshiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun backupProducesValidSqliteFile() {
        runBlocking { db.studentDao().insert(Student(name = "Tom")) }
        val file = BackupManager.backup(context, db)
        assertNotNull(file)
        assertTrue(file!!.exists())
        assertTrue(BackupManager.looksLikeSqlite(file))
        file.delete()
    }

    @Test
    fun restoreRejectsNonSqliteInput() {
        val junk = ByteArrayInputStream("this is not a database".toByteArray())
        assertFalse(BackupManager.restore(context, junk, db))
    }

    @Test
    fun balanceCsv_containsHeadersAndBom() {
        val csv = CsvExporter.balanceTableCsv(emptyList(), emptyMap(), emptyList())
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("学生,班级,计费类型"))
        assertTrue(csv.contains("剩余次数"))
    }

    @Test
    fun paymentsCsv_escapesCommasInNotes() {
        val students = listOf(Student(id = 1, name = "Tom"))
        val payments = listOf(
            com.wrh.keshiguanjia.data.Payment(
                studentId = 1, amountCents = 12345, date = "2026-09-29", note = "含,逗号",
            )
        )
        val csv = CsvExporter.paymentsCsv(students, payments)
        assertTrue(csv.contains("\"含,逗号\""))
        assertTrue(csv.contains("123.45"))
    }

    @Test
    fun writeAndReadBack() {
        val file = CsvExporter.write(context, "test.csv", "\uFEFFhello")
        assertEquals("\uFEFFhello", File(file.path).readText(Charsets.UTF_8))
        file.delete()
    }
}
