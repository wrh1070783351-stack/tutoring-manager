package com.wrh.keshiguanjia

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wrh.keshiguanjia.data.Enrollment
import com.wrh.keshiguanjia.data.KeshiDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 数据库 v1 → v2 迁移：手工建 v1 库，验证 Room 打开时迁移成功且新表可用。 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @Test
    fun migrate1To2_createsNewTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("mig-test.db")

        // 1) 手工创建与 Room v1 完全一致的表结构
        val raw = context.openOrCreateDatabase("mig-test.db", Context.MODE_PRIVATE, null)
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `students` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `grade` TEXT NOT NULL, `parentPhone` TEXT NOT NULL, " +
                "`wechatId` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `classes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `subject` TEXT NOT NULL, `status` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `class_times` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`classId` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL, `startMinute` INTEGER NOT NULL, " +
                "`endMinute` INTEGER NOT NULL, `room` TEXT NOT NULL, " +
                "FOREIGN KEY(`classId`) REFERENCES `classes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        raw.execSQL("CREATE INDEX IF NOT EXISTS `index_class_times_classId` ON `class_times` (`classId`)")
        raw.execSQL("INSERT INTO students (name, grade, parentPhone, wechatId, note, createdAt) VALUES ('Tom', '', '', '', '', 0)")
        raw.execSQL("INSERT INTO classes (name, subject, status, note, createdAt) VALUES ('Math A', 'Math', 0, '', 0)")
        raw.version = 1
        raw.close()

        // 2) Room 打开 → 自动执行 1_2 + 2_3 + 3_4（一路升到当前版本）
        val db = Room.databaseBuilder(context, KeshiDatabase::class.java, "mig-test.db")
            .addMigrations(KeshiDatabase.MIGRATION_1_2, KeshiDatabase.MIGRATION_2_3, KeshiDatabase.MIGRATION_3_4)
            .build()
        try {
            runBlocking {
                // 旧数据完好
                assertEquals(1, db.studentDao().count())
                // 新表可写可读（迁移创建成功）
                val eid = db.enrollmentDao().insert(
                    Enrollment(studentId = 1, classId = 1, billingType = Enrollment.BILLING_SESSIONS)
                )
                assertTrue(eid > 0)
                db.paymentDao().insert(
                    com.wrh.keshiguanjia.data.Payment(
                        studentId = 1, enrollmentId = eid,
                        amountCents = 99000, date = "2026-09-29",
                    )
                )
                assertEquals(99000L, db.paymentDao().sumForMonth("2026-09"))
            }
        } finally {
            db.close()
            context.deleteDatabase("mig-test.db")
        }
    }
}
