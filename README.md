# 补习班课时管理 App

一个帮助补习班老师管理**学生课时**与**缴费情况**的安卓手机应用。

## 项目目标

- 集中管理学生档案与报读的课程/课时包
- 上课签到即自动消课，随时查看每个学生的剩余课时
- 记录缴费（课时包续费）、欠费提醒，替代手工 Excel 记账
- 关键节点（课时不足、课时包到期）有提醒，避免遗漏

## 需求文档

- [需求草案](docs/需求草案.md)（随讨论持续更新）

## 技术栈（已定）

- **Kotlin + Jetpack Compose**（Material 3），单 Activity + Navigation
- **Room** 本地数据库（SQLite），完全离线使用，无服务器
- 架构：MVVM + Repository

## 目录结构

（项目骨架搭建后补充）

## 开发环境

- OS: Windows 10
- Git 2.51 / JDK 18（已装）
- Android Studio + Android SDK：**待安装**（M0 任务，见需求草案里程碑）
