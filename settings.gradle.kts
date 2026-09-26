// 「课时管家」- 补习班课时与缴费管理
// 仓库同时配置国内镜像（阿里云）作为加速，官方仓库兜底
pluginManagement {
    repositories {
        google()
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
    }
}

rootProject.name = "tutoring-manager"
include(":app")
