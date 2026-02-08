package com.ops.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import com.ops.app.projects.ProjectsScreen
import com.ops.app.tasklist.TaskListScreen
import com.ops.app.tasklist.TaskListViewModel
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ops.app.nav.Routes
import com.ops.app.projects.ProjectsViewModel
import com.ops.app.sync.SyncDebugScreen
import android.util.Log
import com.ops.app.sync.DevBaseUrl

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("BASEURL", "App start baseUrl = ${DevBaseUrl.CURRENT}")

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
                        onOpenProjects = { nav.navigate(Routes.PROJECTS) },
                        onOpenSyncDebug = { nav.navigate(Routes.ROUTE_SYNC_DEBUG) }
                    )
                }

                composable(Routes.PROJECTS) {
                    ProjectsScreen(
                        vm = projectsVm,
                        onBack = { nav.popBackStack() }
                    )
                }

                composable(Routes.ROUTE_SYNC_DEBUG) {
                    val vm = remember { c.syncDebugViewModel() }
                    SyncDebugScreen(
                        vm = vm,
                        onBack = { nav.popBackStack() }
                    )
                }
            }
        }

    }
}