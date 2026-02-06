@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.ops.app.projects

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ops.app.ui.projectColorOrNull
import com.ops.core.model.ProjectModel
import androidx.compose.foundation.background

@Composable
fun ProjectsScreen(
    vm: ProjectsViewModel,
    onBack: () -> Unit
) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    val editorState by vm.editor.collectAsStateWithLifecycle()

    val ui by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projekte") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = vm::onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        if (projects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Noch keine Projekte")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects, key = { it.id.value }) { p ->
                    ProjectRow(
                        project = p,
                        onClick = { vm.onRowClick(p.id) },
                        onLongPress = { vm.requestDelete(p) }
                    )
                }
            }
        }
    }

    // Editor Dialog (Add/Edit)
    if (editorState != null) {
        ProjectEditorDialog(
            state = editorState!!,
            onDismiss = vm::dismissEditor,
            onSave = { name, color ->
                vm.save(name, color)
            }
        )
    }

    // Delete confirm (Long-Press)
    val candidate = ui.confirmDelete
    if (candidate != null) {
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("Projekt löschen?") },
            text = { Text("„${candidate.name}“ wird archiviert/gelöscht.") },
            confirmButton = {
                TextButton(onClick = vm::confirmDelete) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelDelete) { Text("Abbrechen") }
            }
        )
    }
}
@Composable
private fun ProjectRow(
    project: ProjectModel,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val dotColor = remember(project.color) {
        projectColorOrNull(project.color) ?: Color(0xFF6B6B6B)
    }

    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
