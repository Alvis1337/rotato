package com.chrisalvis.rotato.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.PlaceRules
import com.chrisalvis.rotato.data.StorageTools

/** Settings › Tools: utilities that look after your wallpapers and the phone they're on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(onNavigateBack: () -> Unit) {
    val vm: ToolsViewModel = viewModel()
    val context = LocalContext.current
    val toast by vm.toast.collectAsStateWithLifecycle()
    LaunchedEffect(toast) {
        toast?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.toastShown() }
    }
    LaunchedEffect(Unit) {
        vm.scanStorage()
        vm.countUntagged()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text("Tools", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ToolCard { StorageSection(vm) }
            ToolCard { DoctorSection(vm) }
            ToolCard { HdSection(vm) }
            ToolCard { TaggerSection(vm) }
            ToolCard { OfflineSection(vm) }
            ToolCard { PlaceSection(vm) }
        }
    }
}

@Composable
private fun ToolCard(content: @Composable () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Progress(p: ToolProgress) {
    if (p.running) {
        if (p.total > 0) LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (p.total > 0) Hint("${p.done} of ${p.total}")
    }
    p.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun StorageSection(vm: ToolsViewModel) {
    val items by vm.storage.collectAsStateWithLifecycle()
    val busy by vm.storageBusy.collectAsStateWithLifecycle()
    SettingsSection(title = "Storage") {
        val total = items.sumOf { it.bytes }
        Hint(if (items.isEmpty()) "Measuring…" else "Rotato is using ${StorageTools.format(total)}. Only caches and leftovers can be cleared here; your Library and collections are never touched.")
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        items.filter { it.bytes > 0 || !it.kind.clearable }.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(item.kind.label, style = MaterialTheme.typography.bodyMedium)
                    Hint(StorageTools.format(item.bytes) + (if (item.files > 0) " · ${item.files} files" else "") + " · " + item.kind.detail)
                }
                if (item.kind.clearable) {
                    TextButton(onClick = { vm.clear(item.kind) }, enabled = !busy && item.bytes > 0) { Text("Clear") }
                }
            }
        }
    }
}

@Composable
private fun DoctorSection(vm: ToolsViewModel) {
    val progress by vm.doctor.collectAsStateWithLifecycle()
    val report by vm.doctorReport.collectAsStateWithLifecycle()
    SettingsSection(title = "Collection doctor") {
        Hint("Finds saved wallpapers whose image is gone. Posts that still exist get their new link; the rest can be removed.")
        Progress(progress)
        val dead = report?.dead.orEmpty()
        dead.take(8).forEach { e ->
            Hint("• ${e.tags.firstOrNull()?.replace('_', ' ') ?: e.source.ifBlank { "Photo" }} (${e.source.ifBlank { "device" }} #${e.sourceId.take(12)})")
        }
        if (dead.size > 8) Hint("…and ${dead.size - 8} more")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (dead.isEmpty()) {
                Button(onClick = vm::checkCollections, enabled = !progress.running) { Text("Check collections") }
            } else {
                Button(onClick = vm::repairDead, enabled = !progress.running) { Text("Repair links") }
                OutlinedButton(onClick = vm::removeDead, enabled = !progress.running) { Text("Remove ${dead.size}") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HdSection(vm: ToolsViewModel) {
    val progress by vm.hd.collectAsStateWithLifecycle()
    val matches by vm.hdMatches.collectAsStateWithLifecycle()
    var skipped by remember(matches) { mutableStateOf(emptySet<String>()) }
    SettingsSection(title = "HD upgrade") {
        Hint("Looks for sharper copies of saved wallpapers that are smaller than this screen, using Danbooru's reverse image search. Nothing changes until you pick Upgrade.")
        Progress(progress)
        matches.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = m.entry.id !in skipped, onCheckedChange = { on -> skipped = if (on) skipped - m.entry.id else skipped + m.entry.id })
                AsyncImage(
                    model = m.thumbUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("${m.entry.resolution.ifBlank { "?" }} → ${m.resolution}", style = MaterialTheme.typography.bodyMedium)
                    Hint("${m.similarity.toInt()}% match · ${m.tags.firstOrNull()?.replace('_', ' ') ?: "Danbooru #${m.postId}"}")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chosen = matches.filter { it.entry.id !in skipped }
            if (matches.isNotEmpty()) {
                Button(onClick = { vm.applyHd(chosen) }, enabled = chosen.isNotEmpty() && !progress.running) { Text("Upgrade ${chosen.size}") }
            }
            OutlinedButton(onClick = vm::findHd, enabled = !progress.running) { Text(if (matches.isEmpty()) "Find sharper copies" else "Search again") }
        }
    }
}

@Composable
private fun TaggerSection(vm: ToolsViewModel) {
    val progress by vm.tagger.collectAsStateWithLifecycle()
    val untagged by vm.untagged.collectAsStateWithLifecycle()
    SettingsSection(title = "Tag my photos") {
        Hint("Names what's in your own photos (beach, sunset, cat) on this phone, so you can search them in the Library and smart collections can pick them up. Nothing is uploaded.")
        Progress(progress)
        untagged?.let { n -> if (!progress.running) Hint(if (n == 0) "All your photos are tagged" else "$n photos without tags") }
        Button(onClick = vm::tagPhotos, enabled = !progress.running && (untagged ?: 1) > 0) { Text("Tag photos") }
    }
}

@Composable
private fun OfflineSection(vm: ToolsViewModel) {
    val context = LocalContext.current
    val enabled by vm.offlineEnabled.collectAsStateWithLifecycle()
    SettingsSection(title = "Offline pack") {
        Hint("Your rotation is already on the phone. This also saves ahead what your schedules will switch to, on Wi-Fi, so they work with no signal (up to ${com.chrisalvis.rotato.data.OfflinePack.MAX_FILES} images).")
        SettingsToggleRow(
            title = "Keep schedules ready offline",
            subtitle = com.chrisalvis.rotato.data.OfflinePack.lastRun(context).takeIf { it > 0 }?.let {
                "${com.chrisalvis.rotato.data.OfflinePack.lastCount(context)} images saved · updated " +
                    android.text.format.DateUtils.getRelativeTimeSpanString(it).toString().lowercase()
            } ?: "Updates once a day on Wi-Fi",
            checked = enabled,
            onCheckedChange = vm::setOfflineEnabled,
        )
        if (enabled) TextButton(onClick = vm::refreshOffline) { Text("Update now") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceSection(vm: ToolsViewModel) {
    val context = LocalContext.current
    val enabled by vm.placeEnabled.collectAsStateWithLifecycle()
    val rules by vm.placeRules.collectAsStateWithLifecycle()
    val elsewhere by vm.elsewhere.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    var hasLocation by remember { mutableStateOf(PlaceRules.hasLocation(context)) }
    var hasBackground by remember { mutableStateOf(PlaceRules.hasBackgroundLocation(context)) }
    var ssid by remember { mutableStateOf(vm.currentSsid()) }
    LifecycleResumeEffect(Unit) {
        hasLocation = PlaceRules.hasLocation(context)
        hasBackground = PlaceRules.hasBackgroundLocation(context)
        ssid = vm.currentSsid()
        onPauseOrDispose { }
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasBackground = granted
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasLocation = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        ssid = vm.currentSsid()
    }
    val usable = collections.filter { !it.isLocked }
    fun name(id: String) = usable.firstOrNull { it.id == id }?.name ?: "a removed collection"

    SettingsSection(title = "Place rules") {
        Hint("Rotate from a different collection depending on the Wi-Fi you're on: one at home, one at work. Android only shares the Wi-Fi name with location access.")
        SettingsToggleRow(
            title = "Switch by Wi-Fi",
            subtitle = if (enabled) "On. Switches when you join a network below" else "Off",
            checked = enabled,
            onCheckedChange = vm::setPlaceEnabled,
        )
        if (!enabled) return@SettingsSection
        when {
            !hasLocation -> FilledTonalButton(onClick = {
                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text("Allow location to read the Wi-Fi name") }
            !hasBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                Hint("To switch while Rotato is closed, pick \"Allow all the time\" on the next screen.")
                FilledTonalButton(onClick = { backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) { Text("Allow in the background") }
            }
        }
        rules.forEach { rule ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(rule.ssid + if (rule.ssid == ssid) " (here now)" else "", style = MaterialTheme.typography.bodyMedium)
                    Hint("Rotates ${name(rule.collectionId)}")
                }
                IconButton(onClick = { vm.removeRule(rule.ssid) }) { Icon(Icons.Default.Close, contentDescription = "Remove rule for ${rule.ssid}") }
            }
        }
        val here = ssid
        if (hasLocation && here != null && rules.none { it.ssid == here } && usable.isNotEmpty()) {
            CollectionPicker(label = "On \"$here\", rotate…", collections = usable) { vm.addRule(here, it.id) }
        } else if (hasLocation && here == null) {
            Hint("Connect to a Wi-Fi network to add it here.")
        }
        HorizontalDivider()
        Text("Everywhere else", style = MaterialTheme.typography.bodyMedium)
        Hint(if (elsewhere.isBlank()) "Rotation stays as it is" else "Rotates ${name(elsewhere)}")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CollectionPicker(label = if (elsewhere.isBlank()) "Pick a collection" else "Change", collections = usable) { vm.setElsewhere(it.id) }
            if (elsewhere.isNotBlank()) TextButton(onClick = { vm.setElsewhere("") }) { Text("Leave as is") }
        }
    }
}

@Composable
private fun CollectionPicker(label: String, collections: List<LocalList>, onPick: (LocalList) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            collections.forEach { list ->
                DropdownMenuItem(text = { Text(list.name) }, onClick = { open = false; onPick(list) })
            }
        }
    }
}
