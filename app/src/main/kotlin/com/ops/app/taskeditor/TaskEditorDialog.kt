package com.ops.app.taskeditor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ops.core.model.ProjectId
import com.ops.core.model.TaskPriority
import com.ops.core.model.TaskStatus
import android.app.DatePickerDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ops.core.model.ProjectModel
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorDialog(
    state: TaskEditorState,
    projects: List<ProjectModel>,
    onAddProjectClick: () -> Unit,
    onProjectChange: (ProjectId?) -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        status: TaskStatus,
        priority: TaskPriority,
        dueAt: Long?,
        projectId: ProjectId?
    ) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember(state.id) { mutableStateOf(state.title) }
    var desc by remember(state.id) { mutableStateOf(state.description) }
    var status by remember(state.id) { mutableStateOf(state.status) }
    var priority by remember(state.id) { mutableStateOf(state.priority) }

    val context = LocalContext.current
    var dueAt by remember(state.id) { mutableStateOf(state.dueAt) }

    val canSave = title.trim().isNotEmpty()

    var projectId by remember(state.id) { mutableStateOf(state.projectId) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.mode == TaskEditorState.Mode.ADD) "Neue Task" else "Task bearbeiten") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(scrollState)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Beschreibung (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        ProjectDropdown(
                            projects = projects,
                            selected = projectId,
                            onSelect = { projectId = it }
                        )
                    }

                    TextButton(
                        onClick = onAddProjectClick,
                    ) {
                        Text("+ Projekt")
                    }
                }

                val dateText = if (dueAt == null) {
                    "Fälligkeitsdatum setzen"
                } else {
                    "Fällig: " + DateFormat.getDateInstance().format(Date(dueAt!!))
                }

                TextButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = dueAt ?: System.currentTimeMillis()
                        }

                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val picked = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, day)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis

                                dueAt = picked
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                ) {
                    Text(dateText)
                }

                Text("Status", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = status == TaskStatus.OPEN,
                        onClick = { status = TaskStatus.OPEN },
                        label = { Text("OFFEN") }
                    )
                    FilterChip(
                        selected = status == TaskStatus.DONE,
                        onClick = { status = TaskStatus.DONE },
                        label = { Text("ERLEDIGT") }
                    )
                }

                Text("Priorität", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = priority == TaskPriority.LOW,
                        onClick = { priority = TaskPriority.LOW },
                        label = { Text("LOW") }
                    )
                    FilterChip(
                        selected = priority == TaskPriority.NORMAL,
                        onClick = { priority = TaskPriority.NORMAL },
                        label = { Text("NORMAL") }
                    )
                    FilterChip(
                        selected = priority == TaskPriority.HIGH,
                        onClick = { priority = TaskPriority.HIGH },
                        label = { Text("HIGH") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        title.trim(),
                        desc,
                        status,
                        priority,
                        dueAt,
                        projectId
                    )
                }
            ) { Text("Speichern") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.mode == TaskEditorState.Mode.EDIT) {
                    TextButton(onClick = onDelete) { Text("Löschen") }
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectDropdown(
    projects: List<ProjectModel>,
    selected: ProjectId?,
    onSelect: (ProjectId?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedName = remember(projects, selected) {
        projects.firstOrNull { it.id == selected }?.name ?: "Kein Projekt"
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Projekt") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(
                    type = MenuAnchorType.PrimaryNotEditable,
                    enabled = true
                )
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Kein Projekt") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )

            projects.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name) },
                    onClick = {
                        onSelect(p.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
