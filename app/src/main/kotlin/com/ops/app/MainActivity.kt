package com.ops.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import com.ops.app.projects.ProjectsScreen
import com.ops.app.tasklist.TaskListScreen
import com.ops.app.tasklist.TaskListViewModel
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ops.app.nav.Routes
import com.ops.app.projects.ProjectsViewModel


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val nav = rememberNavController()

            val app = application as OpsApp
            val c = app.container

            val taskVm: TaskListViewModel = viewModel(
                factory = SimpleVmFactory {
                    TaskListViewModel(
                        repo = c.taskRepository,
                        projectRepository = c.projectRepository,
                        syncOnce = c.syncOnce,
                        syncConfig = c.syncConfig
                    )
                }
            )

            val projectsVm: ProjectsViewModel = viewModel(
                factory = SimpleVmFactory {
                    ProjectsViewModel(
                        repo = c.projectRepository
                    )
                }
            )

            NavHost(navController = nav, startDestination = Routes.TASKS) {

                composable(Routes.TASKS) {
                    TaskListScreen(
                        vm = taskVm,
                        onOpenProjects = { nav.navigate(Routes.PROJECTS) }
                    )
                }

                composable(Routes.PROJECTS) {
                    ProjectsScreen(
                        vm = projectsVm,
                        onBack = { nav.popBackStack() }
                    )
                }
            }
        }

    }
}