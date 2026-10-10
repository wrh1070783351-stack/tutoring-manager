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

/** 数据库 v4 → v5 迁移：classes 增加课程周期起止列（可空）。 */
@RunWith(AndroidJUnit4::class)
class Migration45Test {

    @Test
    fun migrate4To5_addsPeriodColumns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("mig45.db")

        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("mig45.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
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
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val raw = helper.writableDatabase
        raw.execSQL("INSERT INTO classes (name, subject, status, note, createdAt) VALUES ('Math A', 'Math', 0, '', 0)")
        raw.close()

        val db = Room.databaseBuilder(context, KeshiDatabase::class.java, "mig45.db")
            .addMigrations(
                KeshiDatabase.MIGRATION_1_2, KeshiDatabase.MIGRATION_2_3,
                KeshiDatabase.MIGRATION_3_4, KeshiDatabase.MIGRATION_4_5,
                KeshiDatabase.MIGRATION_5_6,
            )
            .build()
        try {
            runBlocking {
                // 旧班级可读，周期为 null（不限）
                val old = db.classDao().observeAllWithTimes().first().single()
                assertEquals("Math A", old.clazz.name)
                assertNull(old.clazz.startDate)
                assertNull(old.clazz.endDate)
            }
        } finally {
            db.close()
            context.deleteDatabase("mig45.db")
        }
    }
}
