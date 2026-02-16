@file:OptIn(ExperimentalMaterial3Api::class)

package com.ops.app.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ops.core.usecase.OutboxRowUi
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SyncDebugScreen(
    vm: SyncDebugViewModel,
    onBack: () -> Unit
) {
    val s by vm.state.collectAsState()

    var infoEntity by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync Debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    TextButton(onClick = { vm.refresh() }) { Text("Refresh") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                s.resetInfo?.let { info ->
                    ResetBannerCard(
                        info = info,
                        onClear = { vm.clearResetInfo() }
                    )
                }
            }

            item {
                StatusHeaderCard(
                    health = s.health,
                    lastSyncAt = s.lastSyncAt,
                    lastError = s.lastError,
                    outboxTotal = s.outboxTotal
                )
            }

            item {
                OutboxOverviewCard(
                    total = s.outboxTotal,
                    byEntity = s.outboxByEntity
                )
            }

            item {
                CursorCard(
                    cursorByEntity = s.cursorByEntity,
                    onInfoClick = { entity -> infoEntity = entity }
                )
            }

            item {
                OutboxTimelineCard(rows = s.outboxLatest)
            }

            item {
                DebugActionsCard(
                    onRunSync = { vm.runSyncOnce() },
                    onRefresh = { vm.refresh() }
                )
            }
        }
    }

    if (infoEntity != null) {
        AlertDialog(
            onDismissRequest = { infoEntity = null },
            confirmButton = {
                TextButton(onClick = { infoEntity = null }) {
                    Text("OK")
                }
            },
            title = { Text("Cursor – ${infoEntity}") },
            text = {
                Text(
                    "Der Cursor zeigt die letzte vom Server bestätigte Änderung " +
                            "für diese Entity.\n\n" +
                            "Ein höherer Wert bedeutet, dass alle früheren Änderungen " +
                            "bereits verarbeitet wurden."
                )
            }
        )
    }
}

/* ---------------- UI Bausteine ---------------- */

@Composable
private fun StatusHeaderCard(
    health: SyncHealth,
    lastSyncAt: Instant?,
    lastError: String?,
    outboxTotal: Int
) {
    val label = when (health) {
        SyncHealth.IDLE -> "SYNC IDLE"
        SyncHealth.RUNNING -> "SYNC RUNNING"
        SyncHealth.PENDING -> "PENDING CHANGES ($outboxTotal)"
        SyncHealth.ERROR -> "SYNC ERROR"
    }

    val icon = when (health) {
        SyncHealth.IDLE -> Icons.Filled.CheckCircle
        SyncHealth.RUNNING -> Icons.Filled.Sync
        SyncHealth.PENDING -> Icons.Filled.Info
        SyncHealth.ERROR -> Icons.Filled.Error
    }

    val timeFmt = remember {
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    }
    val dateFmt = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())
    }

    // Tick für "vor X sec/min"
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            now = Instant.now()
        }
    }

    val ageSeconds = lastSyncAt?.let { Duration.between(it, now).seconds } ?: null
    val ageLabel = ageSeconds?.let { sec ->
        when {
            sec < 60 -> "${sec}s ago"
            sec < 60 * 60 -> "${sec / 60}m ago"
            else -> "${sec / 3600}h ago"
        }
    }

    val isStale = ageSeconds != null && ageSeconds > 60 * 5 // > 5min

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(label, fontWeight = FontWeight.SemiBold)
                }

                if (health == SyncHealth.RUNNING) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("läuft…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            val lastSyncText =
                if (lastSyncAt == null) "—"
                else "${dateFmt.format(lastSyncAt)}  ${timeFmt.format(lastSyncAt)}" +
                        (ageLabel?.let { "  ($it)" } ?: "")

            Text(
                text = "Last sync: $lastSyncText",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isStale) MaterialTheme.colorScheme.tertiary else LocalContentColor.current
            )

            if (isStale && health != SyncHealth.RUNNING) {
                Text(
                    "⚠️ Sync ist seit >5 Minuten nicht gelaufen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            if (lastError != null) {
                Text(
                    text = "Error: $lastError",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun OutboxOverviewCard(
    total: Int,
    byEntity: Map<String, Int>
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Outbox", fontWeight = FontWeight.SemiBold)
            Text("Total: $total")

            Text("Outbox pro Entity", fontWeight = FontWeight.SemiBold)

            if (byEntity.isEmpty()) {
                Text("✔ Keine offenen Änderungen", style = MaterialTheme.typography.bodyMedium)
            } else {
                val max = (byEntity.values.maxOrNull() ?: 1).coerceAtLeast(1)
                byEntity.entries
                    .sortedByDescending { it.value }
                    .forEach { (entity, count) ->
                        MiniBarRow(label = entity, value = count, max = max)
                    }
            }
        }
    }
}

@Composable
private fun MiniBarRow(label: String, value: Int, max: Int) {
    val frac = (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(value.toString(), fontWeight = FontWeight.SemiBold)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        HorizontalDivider()
    }
}

@Composable
private fun CursorCard(
    cursorByEntity: Map<String, Long>,
    onInfoClick: (String) -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Cursor pro Entity", fontWeight = FontWeight.SemiBold)
            Text(
                "Cursor = letzte verarbeitete Server-Änderung (pro Entity)",
                style = MaterialTheme.typography.bodySmall
            )

            if (cursorByEntity.isEmpty()) {
                Text("— leer —")
            } else {
                cursorByEntity.entries
                    .sortedBy { it.key }
                    .forEach { (entity, cursor) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = "Info",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onInfoClick(entity) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(entity)
                            }
                            Text(cursor.toString(), fontWeight = FontWeight.SemiBold)
                        }
                    }
            }
        }
    }
}

@Composable
private fun OutboxTimelineCard(rows: List<OutboxRowUi>) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Outbox (latest)", fontWeight = FontWeight.SemiBold)

            if (rows.isEmpty()) {
                Text("✔ Keine offenen Änderungen", style = MaterialTheme.typography.bodyMedium)
            } else {
                val fmt = remember {
                    DateTimeFormatter.ofPattern("HH:mm:ss")
                        .withZone(ZoneId.systemDefault())
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { r ->
                        val opLabel = when (r.op) {
                            "UPSERT" -> "UPDATE/CREATE"
                            "DELETE" -> "DELETE"
                            else -> r.op
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("$opLabel ${r.entity}", fontWeight = FontWeight.SemiBold)
                            Text("id=${r.entityId}", style = MaterialTheme.typography.bodySmall)
                            Text(fmt.format(r.at), style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugActionsCard(
    onRunSync: () -> Unit,
    onRefresh: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Debug Actions", fontWeight = FontWeight.SemiBold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefresh) { Text("Refresh") }
                Button(onClick = onRunSync) { Text("Run SyncOnce") }
            }
        }
    }
}

@Composable
private fun ResetBannerCard(
    info: com.ops.core.sync.SyncResetInfo,
    onClear: () -> Unit
) {
    val fmt = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
    }



    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⚠ Server Reset erkannt", fontWeight = FontWeight.SemiBold)
            Text(
                "Zeit: ${
                    fmt.format(
                        Instant.ofEpochMilli(info.atEpochMs)
                    )
                }"
            )
            Text("Entity: ${info.entity}")
            Text("Cursor: local=${info.localCursor} → serverNext=${info.serverNextCursor}")
            info.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClear) { Text("Dismiss") }
            }
        }
    }
}

