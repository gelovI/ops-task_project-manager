@file:OptIn(ExperimentalMaterial3Api::class)

package com.ops.app.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

@Composable
fun ProjectEditorDialog(
    state: ProjectEditorState,
    onDismiss: () -> Unit,
    onSave: (name: String, color: Long?) -> Unit
) {
    var name by remember(state.id) { mutableStateOf(state.name) }
    var color by remember(state.id) { mutableStateOf(state.color) }

    val canSave = name.trim().isNotEmpty()

    val palette: List<Int> = listOf(
        0xFF6B6B6B.toInt(),
        0xFFE53935.toInt(),
        0xFFD81B60.toInt(),
        0xFF8E24AA.toInt(),
        0xFF3949AB.toInt(),
        0xFF1E88E5.toInt(),
        0xFF00897B.toInt(),
        0xFF43A047.toInt(),
        0xFFF9A825.toInt(),
        0xFFFB8C00.toInt()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.mode == ProjectEditorState.Mode.ADD) "Neues Projekt" else "Projekt bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Farbe", style = MaterialTheme.typography.labelLarge)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val selectedArgb = state.color?.toInt()

                    palette.forEach { argb ->
                        val selected = selectedArgb == argb
                        Box(
                            modifier = Modifier
                                .size(if (selected) 28.dp else 24.dp)
                                .background(Color(argb), CircleShape)
                                .clickable { color = argb.toLong() }
                        )
                    }

                }

                TextButton(onClick = { color = null }) {
                    Text("Keine Farbe")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(name.trim(), color) }
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}