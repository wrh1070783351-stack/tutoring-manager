package com.wrh.keshiguanjia.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Student::class, ClassRoom::class, ClassTime::class],
    version = 1,
    exportSchema = false,
)
abstract class KeshiDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun classDao(): ClassDao
    abstract fun classTimeDao(): ClassTimeDao

    companion object {
        const val NAME = "keshi.db"

        fun build(context: Context): KeshiDatabase =
            Room.databaseBuilder(context, KeshiDatabase::class.java, NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

/**
 * 轻量服务定位器：应用级单例。M1 规模下不引入 Hilt，保持构建简单。
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
}
