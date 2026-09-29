package com.wrh.keshiguanjia

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wrh.keshiguanjia.data.KeshiDatabase
import com.wrh.keshiguanjia.data.TermRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 数据库 v3 → v4 迁移：老学期记录（无单价列）迁移后可读且单价默认 0，新列可写。 */
@RunWith(AndroidJUnit4::class)
class Migration34Test {

    @Test
    fun migrate3To4_addsUnitPriceColumn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("mig34.db")

        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("mig34.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
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
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val raw = helper.writableDatabase
        raw.execSQL("INSERT INTO students (name, grade, parentPhone, wechatId, note, createdAt) VALUES ('Tom', '', '', '', '', 0)")
        raw.execSQL("INSERT INTO classes (name, subject, status, note, createdAt) VALUES ('Math A', 'Math', 0, '', 0)")
        raw.execSQL("INSERT INTO enrollments (studentId, classId, billingType, createdAt) VALUES (1, 1, 0, 0)")
        raw.execSQL(
            "INSERT INTO term_records (enrollmentId, startDate, endDate, amountCents) VALUES (1, '2026-09-01', '2027-01-31', 500000)"
        )
        raw.close()

        val db = Room.databaseBuilder(context, KeshiDatabase::class.java, "mig34.db")
            .addMigrations(KeshiDatabase.MIGRATION_1_2, KeshiDatabase.MIGRATION_2_3, KeshiDatabase.MIGRATION_3_4)
            .build()
        try {
            runBlocking {
                assertEquals(1, db.studentDao().count())
                // 旧学期记录可读，单价默认 0
                val old = db.termRecordDao().getById(1)
                assertEquals(500000L, old?.amountCents)
                assertEquals(0L, old?.unitPriceCents)
                // 新列可写
                db.termRecordDao().insert(
                    TermRecord(enrollmentId = 1, startDate = "2027-02-01", endDate = "2027-07-15", amountCents = 0, unitPriceCents = 20000)
                )
                assertEquals(20000L, db.termRecordDao().getById(2)?.unitPriceCents)
            }
        } finally {
            db.close()
            context.deleteDatabase("mig34.db")
        }
    }
}
