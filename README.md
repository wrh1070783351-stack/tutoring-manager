# 课时管家

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

```
├── AGENTS.md                 # 协作约定（每次改动必须 commit 并通过测试）
├── docs/
│   ├── 需求草案.md           # 需求与已确认决策
│   └── 开发环境.md           # 工具链、版本选型、常用命令
├── settings.gradle.kts       # 仓库配置（含国内镜像）
├── gradle/libs.versions.toml # 依赖版本目录
└── app/                      # 主模块（Kotlin + Compose）
    └── src/
        ├── main/             # 应用代码与资源
        └── test/             # 单元测试
```

## 构建

```bash
./gradlew :app:assembleDebug      # APK 输出在 app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest  # 单元测试
```

详见 [docs/开发环境.md](docs/开发环境.md)。

## 开发环境

- OS: Windows 10
- Git 2.51 / JDK 18 / Android Studio 2026.1 + Android SDK（API 37）——M0 已就绪
- **项目路径必须保持纯 ASCII**（现为 `F:\zcode\tutoring-manager`），原因与细节见 [docs/开发环境.md](docs/开发环境.md)

## 里程碑进度

- [x] M0 环境搭建 + 工程骨架（构建、单元测试全绿）
- [x] M1 学生管理 + 班级/课程表管理（Room 三表、增删改查、周视图；单测 16 项 + 仪器测试 8 项全绿）
- [ ] M2 报名 + 课时包 + 缴费记录
- [ ] M3 点名消课 + 今日课表
- [ ] M4 提醒 + 统计 + 导出/备份
- [ ] M5 打包 APK，真机试用
