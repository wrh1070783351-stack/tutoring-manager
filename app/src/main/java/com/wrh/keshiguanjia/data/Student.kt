package com.wrh.keshiguanjia.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 学生档案。M1 阶段仅基础信息；报名/课时余额自 M2 起关联。 */
@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val grade: String = "",
    val parentPhone: String = "",
    val wechatId: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
