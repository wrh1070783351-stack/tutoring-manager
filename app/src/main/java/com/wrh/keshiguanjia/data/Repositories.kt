package com.wrh.keshiguanjia.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class StudentRepository(private val dao: StudentDao) {
    fun observeAll(): Flow<List<Student>> = dao.observeAll()
    suspend fun getById(id: Long): Student? = dao.getById(id)
    suspend fun count(): Int = dao.count()
    suspend fun save(student: Student): Long =
        if (student.id == 0L) dao.insert(student) else {
            dao.update(student); student.id
        }
    suspend fun delete(student: Student) = dao.delete(student)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
}

class ClassRepository(
    private val classDao: ClassDao,
    private val timeDao: ClassTimeDao,
    private val db: KeshiDatabase,
) {
    fun observeAllWithTimes(): Flow<List<ClassWithTimes>> = classDao.observeAllWithTimes()

    suspend fun getWithTimes(id: Long): ClassWithTimes? = classDao.getWithTimes(id)
    suspend fun count(): Int = classDao.count()

    /** 保存班级并整体替换其时间段（编辑场景最简单可靠的做法）。 */
    suspend fun saveWithTimes(clazz: ClassRoom, times: List<ClassTime>): Long =
        db.withTransaction {
            val id = if (clazz.id == 0L) classDao.insert(clazz) else {
                classDao.update(clazz); clazz.id
            }
            timeDao.deleteByClassId(id)
            timeDao.insertAll(times.map { it.copy(id = 0, classId = id) })
            id
        }

    suspend fun delete(clazz: ClassRoom) = classDao.delete(clazz)
    suspend fun deleteById(id: Long) = classDao.deleteById(id)
}
