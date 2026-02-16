@file:OptIn(ExperimentalMaterial3Api::class)

package com.ops.app.tasklist

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ops.app.taskeditor.TaskEditorDialog
import com.ops.app.ui.projectColorOrNull
import com.ops.core.model.ProjectId
import com.ops.core.model.TaskId
import com.ops.core.model.TaskModel
import kotlinx.coroutines.launch

data class ProjectMeta(val name: String, val color: Long?)

@Composable
fun TaskListScreen(
    vm: TaskListViewModel,
    onOpenProjects: () -> Unit,
    onOpenSyncDebug: () -> Unit
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                vm.requestAutoSync("resume")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state by vm.uiState.collectAsStateWithLifecycle()
    val editor by vm.editor.collectAsStateWithLifecycle()

    val projects by vm.projects.collectAsStateWithLifecycle()

    val projectMetaById: Map<ProjectId, ProjectMeta> = remember(projects) {
        projects.associate { p ->
            p.id to ProjectMeta(
                name = p.name.takeIf { it.isNotBlank() } ?: "Unbenannt",
                color = p.color
            )
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val filter by vm.filter.collectAsStateWithLifecycle()

    val rotating by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(
            title = { Text("Tasks") },
            actions = {
                TextButton(onClick = onOpenProjects) { Text("Projekte") }
                TextButton(onClick = onOpenSyncDebug) { Text("SyncDebug") }
                TextButton(onClick = { vm.toggleBaseUrl() }) {
                    Text(vm.baseUrlLabel)
                }
                IconButton(
                    onClick = { vm.syncNow() },
                    enabled = !state.syncing.syncing
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "Sync",
                        modifier = Modifier.graphicsLayer {
                            rotationZ = if (state.syncing.syncing) rotating else 0f
                        }
                    )
                }
            }
        ) },
        floatingActionButton = {
            FloatingActionButton(onClick = vm::onAddClick) {
                Text("+")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Sync-Status – IMMER sichtbar
            SyncStatusBar(
                state = state.syncing,
                onSync = vm::syncNow,
                onOpenDebug = onOpenSyncDebug
            )

            Spacer(Modifier.height(8.dp))

            // Filter
            FilterRow(
                selected = filter,
                onSelect = vm::setFilter
            )

            Spacer(Modifier.height(8.dp))

            // Content
            when {
                state.loading -> {
                    Box(Modifier.fillMaxSize()) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                state.error != null -> {
                    Box(Modifier.fillMaxSize()) {
                        Text(
                            text = state.error ?: "Unknown error",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                state.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize()) {
                        Text(
                            text = "Keine Tasks",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                else -> {
                    TaskList(
                        items = state.items,
                        filter = filter,
                        projectMetaById = projectMetaById,
                        onClick = vm::onEditClick
                    )
                }
            }
        }
    }

    if (editor != null) {
        TaskEditorDialog(
            state = editor!!,
            projects = projects,
            onAddProjectClick = onOpenProjects,
            onProjectChange = vm::onProjectChange,
            onDismiss = vm::onDismissEditor,
            onSave = { title, description, status, priority, dueAt, projectId ->
                vm.onSaveEditor(title, description, status, priority, dueAt, projectId)
                vm.onDismissEditor()
            },
            onDelete = {
                vm.onDeleteEditor()
                vm.onDismissEditor()

                scope.launch {
                    val res = snackbarHostState.showSnackbar(
                        message = "Task gelöscht",
                        actionLabel = "Rückgängig",
                        withDismissAction = true,
                        duration = SnackbarDuration.Long
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        vm.undoDelete()
                    }
                }
            }
        )
    }
}

@Composable
fun SyncStatusBar(
    state: SyncUiState,
    onSync: () -> Unit,
    onOpenDebug: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when {
                state.syncing -> "🔄 Synchronisiere…"
                state.error != null -> "⚠️ Sync fehlgeschlagen"
                state.lastSyncAt != null -> "✔️ Sync aktuell"
                else -> "⏸️ Offline"
            },
            style = MaterialTheme.typography.bodyMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpenDebug) {
                Text("Details")
            }

            Button(
                onClick = onSync,
                enabled = !state.syncing
            ) {
                Text("Sync")
            }
        }
    }
}

@Composable
private fun TaskList(
    items: List<TaskModel>,
    filter: TaskListViewModel.TaskFilter,
    projectMetaById: Map<ProjectId, ProjectMeta>,
    onClick: (TaskId) -> Unit
) {
    val open = remember(items) { items.filterNot { it.isDone } }
    val done = remember(items) { items.filter { it.isDone } }

    var showDone by rememberSaveable { mutableStateOf(false) }

    fun metaFor(task: TaskModel): ProjectMeta? =
        task.projectId?.let { projectMetaById[it] }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (filter) {
            TaskListViewModel.TaskFilter.ALL -> {
                items(open, key = { it.id.value }) { task ->
                    val meta = metaFor(task)
                    TaskRow(
                        task = task,
                        projectName = meta?.name,
                        projectColor = meta?.color,
                        onClick = { onClick(task.id) }
                    )
                }

                if (done.isNotEmpty()) {
                    item(key = "done-header") {
                        DoneSectionHeader(
                            count = done.size,
                            expanded = showDone,
                            onToggle = { showDone = !showDone }
                        )
                    }
                    if (showDone) {
                        items(done, key = { it.id.value }) { task ->
                            val meta = metaFor(task)
                            TaskRow(
                                task = task,
                                projectName = meta?.name,
                                projectColor = meta?.color,
                                onClick = { onClick(task.id) }
                            )
                        }
                    }
                }
            }

            TaskListViewModel.TaskFilter.OPEN -> {
                items(open, key = { it.id.value }) { task ->
                    val meta = metaFor(task)
                    TaskRow(
                        task = task,
                        projectName = meta?.name,
                        projectColor = meta?.color,
                        onClick = { onClick(task.id) }
                    )
                }
            }

            TaskListViewModel.TaskFilter.DONE -> {
                items(done, key = { it.id.value }) { task ->
                    val meta = metaFor(task)
                    TaskRow(
                        task = task,
                        projectName = meta?.name,
                        projectColor = meta?.color,
                        onClick = { onClick(task.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DoneSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Erledigt ($count)",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun priorityColor(p: com.ops.core.model.TaskPriority) = when (p) {
    com.ops.core.model.TaskPriority.LOW -> MaterialTheme.colorScheme.secondary
    com.ops.core.model.TaskPriority.NORMAL -> MaterialTheme.colorScheme.tertiary
    com.ops.core.model.TaskPriority.HIGH -> MaterialTheme.colorScheme.error
}

@Composable
private fun TaskRow(
    task: TaskModel,
    projectName: String?,
    projectColor: Long?,
    onClick: () -> Unit
) {
    val done = task.isDone
    val pColor = priorityColor(task.priority)
    val stripeColor = remember(task.priority, projectColor) {
        val overlay = projectColorOrNull(projectColor)
        if (overlay != null) {
            mixColors(base = pColor, overlay = overlay, ratio = 0.3f)
        } else {
            pColor
        }
    }

    Surface(
        tonalElevation = if (done) 0.dp else 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (done) 0.55f else 1f)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(stripeColor.copy(alpha = if (done) 0.35f else 1f))
            )

            Column(
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .padding(14.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f)
                    )

                    if (done) {
                        Spacer(Modifier.width(8.dp))
                        AssistChip(onClick = {}, label = { Text("DONE") })
                    }
                }

                if (projectName != null) {
                    Spacer(Modifier.height(6.dp))

                    AssistChip(
                        onClick = {},
                        enabled = false,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (projectColor != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                color = Color(projectColor),
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }

                                Text(projectName, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    )
                }

                if (task.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (done) TextDecoration.LineThrough else null
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    selected: TaskListViewModel.TaskFilter,
    onSelect: (TaskListViewModel.TaskFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == TaskListViewModel.TaskFilter.ALL,
            onClick = { onSelect(TaskListViewModel.TaskFilter.ALL) },
            label = { Text("Alle") }
        )
        FilterChip(
            selected = selected == TaskListViewModel.TaskFilter.OPEN,
            onClick = { onSelect(TaskListViewModel.TaskFilter.OPEN) },
            label = { Text("Offen") }
        )
        FilterChip(
            selected = selected == TaskListViewModel.TaskFilter.DONE,
            onClick = { onSelect(TaskListViewModel.TaskFilter.DONE) },
            label = { Text("Erledigt") }
        )
    }
}

private fun mixColors(
    base: Color,
    overlay: Color,
    ratio: Float = 0.25f // wie stark Projektfarbe einfließt
): Color {
    fun mix(a: Float, b: Float) = (a * (1 - ratio) + b * ratio)

    return Color(
        red = mix(base.red, overlay.red),
        green = mix(base.green, overlay.green),
        blue = mix(base.blue, overlay.blue),
        alpha = 1f
    )
}


