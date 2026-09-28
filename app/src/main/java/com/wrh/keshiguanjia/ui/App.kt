package com.wrh.keshiguanjia.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wrh.keshiguanjia.ui.classes.ClassEditScreen
import com.wrh.keshiguanjia.ui.classes.ClassListScreen
import com.wrh.keshiguanjia.ui.home.HomeScreen
import com.wrh.keshiguanjia.ui.students.StudentEditScreen
import com.wrh.keshiguanjia.ui.students.StudentListScreen

object Routes {
    const val HOME = "home"
    const val STUDENTS = "students"
    const val CLASSES = "classes"
    const val STUDENT_EDIT = "student/{studentId}"
    const val CLASS_EDIT = "class/{classId}"

    fun studentEdit(id: Long) = "student/$id"
    fun classEdit(id: Long) = "class/$id"
}

private data class TopLevelDestination(val route: String, val label: String, val icon: ImageVector)

@Composable
fun KeshiApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val destinations = listOf(
        TopLevelDestination(Routes.HOME, "今日课表", Icons.Filled.Home),
        TopLevelDestination(Routes.STUDENTS, "学生", Icons.Filled.Person),
        TopLevelDestination(Routes.CLASSES, "班级", Icons.Filled.List),
    )

    Scaffold(
        bottomBar = {
            if (destinations.any { it.route == currentRoute }) {
                NavigationBar {
                    destinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(Routes.HOME) { HomeScreen() }
            composable(Routes.STUDENTS) {
                StudentListScreen(onEdit = { id -> navController.navigate(Routes.studentEdit(id)) })
            }
            composable(Routes.CLASSES) {
                ClassListScreen(onEdit = { id -> navController.navigate(Routes.classEdit(id)) })
            }
            composable(Routes.STUDENT_EDIT) { entry ->
                val id = entry.arguments?.getString("studentId")?.toLongOrNull() ?: -1L
                StudentEditScreen(studentId = id, onDone = { navController.popBackStack() })
            }
            composable(Routes.CLASS_EDIT) { entry ->
                val id = entry.arguments?.getString("classId")?.toLongOrNull() ?: -1L
                ClassEditScreen(classId = id, onDone = { navController.popBackStack() })
            }
        }
    }
}
