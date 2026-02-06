package com.ops.app.taskeditor

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.TextFieldValue

@Composable
fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue("")) }
    val canCreate = name.text.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neues Projekt") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Projektname") }
            )
        },
        confirmButton = {
            TextButton(
                enabled = canCreate,
                onClick = { onCreate(name.text.trim()) }
            ) { Text("Erstellen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
