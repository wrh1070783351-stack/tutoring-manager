package com.wrh.keshiguanjia.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Student::class, ClassRoom::class, ClassTime::class,
        Enrollment::class, ClassPackage::class, TermRecord::class,
        Payment::class, LessonOverride::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class KeshiDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun classDao(): ClassDao
    abstract fun classTimeDao(): ClassTimeDao
    abstract fun enrollmentDao(): EnrollmentDao
    abstract fun classPackageDao(): ClassPackageDao
    abstract fun termRecordDao(): TermRecordDao
    abstract fun paymentDao(): PaymentDao
    abstract fun lessonOverrideDao(): LessonOverrideDao

    companion object {
        const val NAME = "keshi.db"

        /** v1 → v2：新增报名/课时包/学期/缴费/课表调整五张表（纯新增，不动旧表）。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `enrollments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`studentId` INTEGER NOT NULL, " +
                        "`classId` INTEGER NOT NULL, " +
                        "`billingType` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`classId`) REFERENCES `classes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_enrollments_studentId` ON `enrollments` (`studentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_enrollments_classId` ON `enrollments` (`classId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `class_packages` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`enrollmentId` INTEGER NOT NULL, " +
                        "`totalSessions` INTEGER NOT NULL, " +
                        "`bonusSessions` INTEGER NOT NULL, " +
                        "`amountCents` INTEGER NOT NULL, " +
                        "`purchasedAt` INTEGER NOT NULL, " +
                        "`validUntil` TEXT, " +
                        "FOREIGN KEY(`enrollmentId`) REFERENCES `enrollments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_class_packages_enrollmentId` ON `class_packages` (`enrollmentId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `term_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`enrollmentId` INTEGER NOT NULL, " +
                        "`startDate` TEXT NOT NULL, " +
                        "`endDate` TEXT NOT NULL, " +
                        "`amountCents` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`enrollmentId`) REFERENCES `enrollments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_term_records_enrollmentId` ON `term_records` (`enrollmentId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `payments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`studentId` INTEGER NOT NULL, " +
                        "`enrollmentId` INTEGER, " +
                        "`amountCents` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`method` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_studentId` ON `payments` (`studentId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lesson_overrides` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`classId` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`type` INTEGER NOT NULL, " +
                        "`startMinute` INTEGER NOT NULL, " +
                        "`endMinute` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "FOREIGN KEY(`classId`) REFERENCES `classes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_overrides_classId` ON `lesson_overrides` (`classId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_overrides_date` ON `lesson_overrides` (`date`)")
            }
        }

        fun build(context: Context): KeshiDatabase =
            Room.databaseBuilder(context, KeshiDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/**
 * 轻量服务定位器：应用级单例。规模小暂不引入 Hilt，保持构建简单。
 */
object Graph {
    private lateinit var db: KeshiDatabase

    fun init(context: Context) {
        if (!Graph::db.isInitialized) {
            db = KeshiDatabase.build(context.applicationContext)
        }
    }

    internal fun database(): KeshiDatabase = db

    val studentRepository: StudentRepository by lazy { StudentRepository(db.studentDao()) }
    val classRepository: ClassRepository by lazy { ClassRepository(db.classDao(), db.classTimeDao(), db) }
    val enrollmentRepository: EnrollmentRepository by lazy {
        EnrollmentRepository(db.enrollmentDao(), db.classPackageDao(), db.termRecordDao(), db.paymentDao(), db)
    }
    val scheduleRepository: ScheduleRepository by lazy { ScheduleRepository(db.classDao(), db.classTimeDao(), db.lessonOverrideDao(), db) }
}
