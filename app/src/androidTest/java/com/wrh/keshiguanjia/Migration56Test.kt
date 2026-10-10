package com.wrh.keshiguanjia

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wrh.keshiguanjia.data.KeshiDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** 数据库 v5 → v6 数据对齐迁移：老班级（未设周期）按最早上课记录推断开课日期。 */
@RunWith(AndroidJUnit4::class)
class Migration56Test {

    @Test
    fun migrate5To6_backfillsStartDateFromEarliestAttendance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("mig56.db")

        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("mig56.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
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
                        KeshiDatabase.MIGRATION_2_3.migrate(db)
                        KeshiDatabase.MIGRATION_3_4.migrate(db)
                        KeshiDatabase.MIGRATION_4_5.migrate(db)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val raw = helper.writableDatabase
        // 班级 1：有考勤（最早 9/10）→ 应被回填 startDate=9/10
        // 班级 2：无任何考勤 → 保持 NULL（无法推断，不臆造）
        raw.execSQL("INSERT INTO students (name, grade, parentPhone, wechatId, note, createdAt) VALUES ('Tom', '', '', '', '', 0)")
        raw.execSQL("INSERT INTO classes (name, subject, status, note, createdAt) VALUES ('Math A', 'Math', 0, '', 0)")
        raw.execSQL("INSERT INTO classes (name, subject, status, note, createdAt) VALUES ('Empty C', '', 0, '', 0)")
        raw.execSQL("INSERT INTO enrollments (studentId, classId, billingType, createdAt) VALUES (1, 1, 0, 0)")
        raw.execSQL(
            "INSERT INTO attendance (studentId, classId, enrollmentId, date, status, note, createdAt) VALUES (1, 1, 1, '2026-09-17', 0, '', 0)"
        )
        raw.execSQL(
            "INSERT INTO attendance (studentId, classId, enrollmentId, date, status, note, createdAt) VALUES (1, 1, 1, '2026-09-10', 0, '', 0)"
        )
        raw.close()

        val db = Room.databaseBuilder(context, KeshiDatabase::class.java, "mig56.db")
            .addMigrations(
                KeshiDatabase.MIGRATION_1_2, KeshiDatabase.MIGRATION_2_3, KeshiDatabase.MIGRATION_3_4,
                KeshiDatabase.MIGRATION_4_5, KeshiDatabase.MIGRATION_5_6,
            )
            .build()
        try {
            runBlocking {
                val classes = db.classDao().observeAllWithTimes().first()
                val mathA = classes.first { it.clazz.name == "Math A" }
                assertEquals("2026-09-10", mathA.clazz.startDate) // 最早考勤日
                assertNull(mathA.clazz.endDate) // 进行中的班保持不限
                val empty = classes.first { it.clazz.name == "Empty C" }
                assertNull(empty.clazz.startDate) // 无考勤不臆造
            }
        } finally {
            db.close()
            context.deleteDatabase("mig56.db")
        }
    }
}
