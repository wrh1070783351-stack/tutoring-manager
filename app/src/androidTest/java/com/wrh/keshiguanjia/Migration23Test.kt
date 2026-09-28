package com.wrh.keshiguanjia

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wrh.keshiguanjia.data.Attendance
import com.wrh.keshiguanjia.data.KeshiDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 数据库 v2 → v3 迁移：手工构造 v2 库（复用 MIGRATION_1_2 生成 v2 表），验证 v3 考勤表可用。 */
@RunWith(AndroidJUnit4::class)
class Migration23Test {

    @Test
    fun migrate2To3_createsAttendanceTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("mig23.db")

        // 1) 用 SupportSQLiteOpenHelper.Callback(2) 建立 v2 结构：
        //    v1 三张表 + MIGRATION_1_2（即 v2 的五张新表）
        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("mig23.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `students` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`name` TEXT NOT NULL, `grade` TEXT NOT NULL, `parentPhone` TEXT NOT NULL, " +
                                "`wechatId` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `classes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`name` TEXT NOT NULL, `subject` TEXT NOT NULL, `status` INTEGER NOT NULL, " +
                                "`note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `class_times` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`classId` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL, `startMinute` INTEGER NOT NULL, " +
                                "`endMinute` INTEGER NOT NULL, `room` TEXT NOT NULL, " +
                                "FOREIGN KEY(`classId`) REFERENCES `classes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_class_times_classId` ON `class_times` (`classId`)")
                        KeshiDatabase.MIGRATION_1_2.migrate(db)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val raw = helper.writableDatabase
        raw.execSQL("INSERT INTO students (name, grade, parentPhone, wechatId, note, createdAt) VALUES ('Tom', '', '', '', '', 0)")
        raw.execSQL("INSERT INTO classes (name, subject, status, note, createdAt) VALUES ('Math A', 'Math', 0, '', 0)")
        raw.execSQL(
            "INSERT INTO enrollments (studentId, classId, billingType, createdAt) VALUES (1, 1, 0, 0)"
        )
        raw.close()

        // 2) Room 打开（user_version=2）→ 执行 MIGRATION_2_3 → 考勤表可用
        val db = Room.databaseBuilder(context, KeshiDatabase::class.java, "mig23.db")
            .addMigrations(KeshiDatabase.MIGRATION_1_2, KeshiDatabase.MIGRATION_2_3)
            .build()
        try {
            runBlocking {
                assertEquals(1, db.studentDao().count())
                db.attendanceDao().insert(
                    Attendance(
                        studentId = 1, classId = 1, enrollmentId = 1,
                        date = "2026-09-29", status = Attendance.STATUS_ATTENDED,
                    )
                )
                assertEquals(1, db.attendanceDao().observeConsumedAll().first().size)
            }
        } finally {
            db.close()
            context.deleteDatabase("mig23.db")
        }
    }
}
