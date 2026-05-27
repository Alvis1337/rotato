package com.chrisalvis.rotato.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val needsExactAlarm = vm.needsExactAlarmPermission
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.triggerResult.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (editEntry != null) {
        ScheduleEditDialog(
            entry = editEntry!!,
            lists = lists,
            isEditing = entries.any { it.id == editEntry!!.id },
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
            FloatingActionButton(onClick = { vm.startAdd() }) {
                Icon(Icons.Default.Add, contentDescription = "Add schedule")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (needsExactAlarm) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        "Exact alarm permission not granted — schedules may fire late. " +
                                "Grant \"Alarms & reminders\" in system settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "No schedules yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Schedules swap your active wallpaper collection at a set time each week — great for different vibes by day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    FilledTonalButton(onClick = { vm.startAdd() }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add schedule")
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    val targetList = lists.find { it.id == entry.listId }
                    ScheduleEntryCard(
                        entry = entry,
                        listName = when {
                            entry.listId.isBlank() -> "Main rotation queue"
                            targetList != null -> targetList.name
                            else -> "Unknown collection"
                        },
                        isListLocked = entry.listId.isNotBlank() && targetList?.isLocked == true,
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
        } // end outer Column
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
            if (!entry.enabled) {
                Text(
                    "Paused — this schedule won't run",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DAY_ORDER.forEach { (dayConst, label) ->
                    val active = dayConst in entry.days
                    SuggestionChip(
                        onClick = {},
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            labelColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outlineVariant,
                        ),
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
    isEditing: Boolean,
    onSave: (ScheduleEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var days by remember(entry.id) { mutableStateOf(entry.days) }
    var hour by remember(entry.id) { mutableIntStateOf(entry.startHour) }
    var minute by remember(entry.id) { mutableIntStateOf(entry.startMinute) }
    var selectedListId by remember(entry.id) { mutableStateOf(entry.listId) }
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
        title = { Text(if (isEditing) "Edit Schedule" else "New Schedule") },
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
                Text("Use collection", style = MaterialTheme.typography.labelMedium)
                ExposedDropdownMenuBox(
                    expanded = expandListDropdown,
                    onExpandedChange = { expandListDropdown = it },
                ) {
                    OutlinedTextField(
                        value = lists.find { it.id == selectedListId }?.name ?: "Main rotation queue",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Use collection") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandListDropdown) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expandListDropdown,
                        onDismissRequest = { expandListDropdown = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Main rotation queue") },
                            onClick = {
                                selectedListId = ""
                                expandListDropdown = false
                            },
                        )
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
                    if (days.isNotEmpty()) {
                        onSave(entry.copy(days = days, startHour = hour, startMinute = minute, listId = selectedListId))
                    }
                },
                enabled = days.isNotEmpty(),
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
