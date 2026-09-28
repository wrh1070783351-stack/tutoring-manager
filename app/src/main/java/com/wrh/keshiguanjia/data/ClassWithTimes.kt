package com.wrh.keshiguanjia.data

import androidx.room.Embedded
import androidx.room.Relation

/** 班级与其每周时间段的一对多查询结果。 */
data class ClassWithTimes(
    @Embedded val clazz: ClassRoom,
    @Relation(parentColumn = "id", entityColumn = "classId")
    val times: List<ClassTime>,
)
