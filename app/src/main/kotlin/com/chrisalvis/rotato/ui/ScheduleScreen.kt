package com.chrisalvis.rotato.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.ScheduleEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Day order: Mon→Sun displayed left to right
private val DAY_ORDER = listOf(
    Calendar.MONDAY to "M",
    Calendar.TUESDAY to "Tu",
    Calendar.WEDNESDAY to "W",
    Calendar.THURSDAY to "Th",
    Calendar.FRIDAY to "F",
    Calendar.SATURDAY to "Sa",
    Calendar.SUNDAY to "Su",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onNavigateBack: () -> Unit,
    vm: ScheduleViewModel = viewModel(),
) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val editEntry by vm.editEntry.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        launch {
            vm.lockedListWarning.collect { name ->
                snackbarHostState.showSnackbar("\"$name\" is locked — unlock it in Collections for the schedule to apply")
            }
        }
        vm.triggerResult.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (editEntry != null) {
        ScheduleEditDialog(
            entry = editEntry!!,
            lists = lists,
            onSave = { vm.saveEdit(it) },
            onDismiss = { vm.dismissEdit() },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Schedule", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (lists.isNotEmpty()) {
                FloatingActionButton(onClick = { vm.startAdd() }) {
                    Icon(Icons.Default.Add, contentDescription = "Add schedule")
                }
            }
        },
    ) { padding ->
        if (lists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Create a collection first, then add it to a schedule.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No schedules yet.\nTap + to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    val targetList = lists.find { it.id == entry.listId }
                    ScheduleEntryCard(
                        entry = entry,
                        listName = targetList?.name ?: "Unknown list",
                        isListLocked = targetList?.isLocked == true,
                        wasBlockedByLock = entry.lastLockedMs > 0L,
                        lastFiredMs = entry.lastFiredMs,
                        lastFiredResult = entry.lastFiredResult,
                        onEdit = { vm.startEdit(entry) },
                        onDelete = { vm.delete(entry) },
                        onToggleEnabled = { vm.setEnabled(entry, it) },
                        onTriggerNow = { vm.triggerNow(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleEntryCard(
    entry: ScheduleEntry,
    listName: String,
    isListLocked: Boolean,
    wasBlockedByLock: Boolean,
    lastFiredMs: Long,
    lastFiredResult: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTriggerNow: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(listName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (isListLocked) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Locked",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Text(
                        formatTime(entry.startHour, entry.startMinute),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Switch(checked = entry.enabled, onCheckedChange = onToggleEnabled)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DAY_ORDER.forEach { (dayConst, label) ->
                    val active = dayConst in entry.days
                    AssistChip(
                        onClick = {},
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            labelColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                }
            }
            if (entry.enabled && (isListLocked || wasBlockedByLock)) {
                Text(
                    if (isListLocked)
                        "Collection is locked — unlock it in Collections for the schedule to apply"
                    else
                        "Last trigger was blocked by a locked collection — unlock it in Collections",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (lastFiredMs > 0L) {
                val timeStr = remember(lastFiredMs) {
                    SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date(lastFiredMs))
                }
                Text(
                    "Last triggered: $timeStr — $lastFiredResult",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (lastFiredResult.contains("empty pool") || lastFiredResult.contains("blocked"))
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Never triggered",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.enabled) {
                TextButton(
                    onClick = onTriggerNow,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Apply now", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditDialog(
    entry: ScheduleEntry,
    lists: List<LocalList>,
    onSave: (ScheduleEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var days by remember { mutableStateOf(entry.days) }
    var hour by remember { mutableIntStateOf(entry.startHour) }
    var minute by remember { mutableIntStateOf(entry.startMinute) }
    var selectedListId by remember { mutableStateOf(entry.listId.ifBlank { lists.firstOrNull()?.id ?: "" }) }
    var showTimePicker by remember { mutableStateOf(false) }
    var expandListDropdown by remember { mutableStateOf(false) }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Pick time") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour
                    minute = state.minute
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry.listId.isBlank()) "New Schedule" else "Edit Schedule") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Day chips
                Text("Days", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_ORDER.forEach { (dayConst, label) ->
                        val active = dayConst in days
                        FilterChip(
                            selected = active,
                            onClick = { days = if (active) days - dayConst else days + dayConst },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(32.dp),
                        )
                    }
                }

                // Time picker trigger
                Text("Start time", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(formatTime(hour, minute))
                }

                // List picker dropdown
                Text("Collection", style = MaterialTheme.typography.labelMedium)
                ExposedDropdownMenuBox(
                    expanded = expandListDropdown,
                    onExpandedChange = { expandListDropdown = it },
                ) {
                    OutlinedTextField(
                        value = lists.find { it.id == selectedListId }?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Collection") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandListDropdown) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expandListDropdown,
                        onDismissRequest = { expandListDropdown = false },
                    ) {
                        lists.forEach { list ->
                            DropdownMenuItem(
                                text = { Text(list.name) },
                                onClick = {
                                    selectedListId = list.id
                                    expandListDropdown = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (days.isNotEmpty() && selectedListId.isNotBlank()) {
                        onSave(entry.copy(days = days, startHour = hour, startMinute = minute, listId = selectedListId))
                    }
                },
                enabled = days.isNotEmpty() && selectedListId.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatTime(hour: Int, minute: Int): String {
    val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val ampm = if (hour < 12) "AM" else "PM"
    return "%d:%02d %s".format(h, minute, ampm)
}
