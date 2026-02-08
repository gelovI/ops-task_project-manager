@file:OptIn(ExperimentalMaterial3Api::class)

package com.ops.app.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SyncDebugScreen(
    vm: SyncDebugViewModel,
    onBack: () -> Unit
) {
    val st by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync Debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    TextButton(onClick = vm::refresh) { Text("Refresh") }
                }
            )
        }
    ) { padding ->
        when {
            st.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }

            st.error != null -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Fehler: ${st.error}", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = vm::refresh) { Text("Nochmal") }
            }

            else -> {
                val snap = st.snapshot!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        ElevatedCard {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Outbox", style = MaterialTheme.typography.titleMedium)
                                Text("Total: ${snap.outboxTotal}")
                            }
                        }
                    }

                    item {
                        ElevatedCard {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Outbox pro Entity", style = MaterialTheme.typography.titleMedium)
                                if (snap.outboxByEntity.isEmpty()) {
                                    Text("— leer —")
                                } else {
                                    snap.outboxByEntity.forEach { row ->
                                        Text("${row.entity}: ${row.count}")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        ElevatedCard {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Cursor pro Entity", style = MaterialTheme.typography.titleMedium)
                                if (snap.cursors.isEmpty()) {
                                    Text("— keine Einträge —")
                                } else {
                                    snap.cursors.forEach { c ->
                                        Text("${c.entity}: ${c.cursor}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}