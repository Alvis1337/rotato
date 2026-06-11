package com.chrisalvis.rotato.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrisalvis.rotato.data.InterestProfile
import com.chrisalvis.rotato.data.TagTier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasteScreen(vm: TasteViewModel = viewModel()) {
    val tagTiers by vm.tagTiers.collectAsStateWithLifecycle()
    val profiles by vm.interestProfiles.collectAsStateWithLifecycle()
    val tasteProfile by vm.tasteProfile.collectAsStateWithLifecycle()
    val coTagMap by vm.coTagMap.collectAsStateWithLifecycle()
    val editingProfile by vm.editingProfile.collectAsStateWithLifecycle()

    if (editingProfile != null) {
        ProfileEditorSheet(
            initial = editingProfile!!,
            onSave = vm::saveProfile,
            onDismiss = vm::cancelEditProfile,
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Taste") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.startEditProfile(null) }) {
                Icon(Icons.Default.Add, contentDescription = "New profile")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item { SectionHeader("Tag Tiers") }
            item { TagTiersSection(tagTiers = tagTiers, onSetTier = vm::setTagTier, onRemove = vm::removeTagTier) }

            item { SectionHeader("Interest Profiles") }
            if (profiles.isEmpty()) {
                item {
                    Text(
                        "No profiles yet. Tap + to create one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(profiles, key = { it.id }) { profile ->
                    ProfileRow(
                        profile = profile,
                        onToggle = { vm.toggleProfile(profile.id) },
                        onEdit = { vm.startEditProfile(profile) },
                        onDelete = { vm.deleteProfile(profile.id) },
                    )
                }
            }

            item { SectionHeader("My Taste") }
            item { TasteProfileSection(tasteProfile = tasteProfile) }

            item { SectionHeader("Co-tag Explorer") }
            item { CoTagExplorerSection(coTagMap = coTagMap) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ─── Tag Tiers ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagTiersSection(
    tagTiers: Map<String, TagTier>,
    onSetTier: (String, TagTier) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Add a tag") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                val tag = input.trim().lowercase()
                if (tag.isNotBlank() && tag !in tagTiers) {
                    onSetTier(tag, TagTier.LIKE)
                    input = ""
                }
            }),
            trailingIcon = {
                if (input.isNotBlank()) {
                    IconButton(onClick = {
                        val tag = input.trim().lowercase()
                        if (tag.isNotBlank() && tag !in tagTiers) {
                            onSetTier(tag, TagTier.LIKE)
                            input = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        if (tagTiers.isEmpty()) {
            Text(
                "No tags yet. Type a tag above and press Done.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            tagTiers.entries.sortedWith(compareBy({ it.value.ordinal }, { it.key })).forEach { (tag, tier) ->
                TagTierRow(tag = tag, tier = tier, onSetTier = onSetTier, onRemove = onRemove)
            }
        }
    }
}

@Composable
private fun TagTierRow(
    tag: String,
    tier: TagTier,
    onSetTier: (String, TagTier) -> Unit,
    onRemove: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            tag,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TierPicker(current = tier, onPick = { onSetTier(tag, it) })
        IconButton(onClick = { onRemove(tag) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TierPicker(current: TagTier, onPick: (TagTier) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TierChip(TagTier.LOVE, current, "♥", onPick)
        TierChip(TagTier.LIKE, current, "↑", onPick)
        TierChip(TagTier.DISLIKE, current, "↓", onPick)
        TierChip(TagTier.NEVER, current, "✕", onPick)
    }
}

@Composable
private fun TierChip(tier: TagTier, current: TagTier, label: String, onPick: (TagTier) -> Unit) {
    val selected = tier == current
    val containerColor by animateColorAsState(
        targetValue = when {
            !selected -> MaterialTheme.colorScheme.surfaceVariant
            tier == TagTier.LOVE -> Color(0xFFB71C1C)
            tier == TagTier.LIKE -> MaterialTheme.colorScheme.primary
            tier == TagTier.DISLIKE -> MaterialTheme.colorScheme.secondary
            tier == TagTier.NEVER -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        },
        label = "tierColor"
    )
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .clickable { onPick(tier) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = contentColor, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

// ─── Interest Profiles ───────────────────────────────────────────────────────

@Composable
private fun ProfileRow(
    profile: InterestProfile,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (profile.isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (profile.includeTags.isNotEmpty()) {
                    Text(
                        "Include: ${profile.includeTags.take(3).joinToString(", ")}${if (profile.includeTags.size > 3) "…" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (profile.excludeTags.isNotEmpty()) {
                    Text(
                        "Exclude: ${profile.excludeTags.take(3).joinToString(", ")}${if (profile.excludeTags.size > 3) "…" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            FilterChip(
                selected = profile.isActive,
                onClick = onToggle,
                label = { Text(if (profile.isActive) "On" else "Off") },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditorSheet(
    initial: InterestProfile,
    onSave: (InterestProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var includeInput by rememberSaveable { mutableStateOf("") }
    var excludeInput by rememberSaveable { mutableStateOf("") }
    var includeTags by remember { mutableStateOf(initial.includeTags) }
    var excludeTags by remember { mutableStateOf(initial.excludeTags) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (initial.name.isBlank()) "New Profile" else "Edit Profile",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            TagInputRow(
                label = "Include tags",
                input = includeInput,
                onInputChange = { includeInput = it },
                tags = includeTags,
                onAdd = { tag ->
                    if (tag.isNotBlank() && tag !in includeTags) includeTags = includeTags + tag
                    includeInput = ""
                },
                onRemove = { includeTags = includeTags - it }
            )
            Spacer(Modifier.height(12.dp))
            TagInputRow(
                label = "Exclude tags",
                input = excludeInput,
                onInputChange = { excludeInput = it },
                tags = excludeTags,
                onAdd = { tag ->
                    if (tag.isNotBlank() && tag !in excludeTags) excludeTags = excludeTags + tag
                    excludeInput = ""
                },
                onRemove = { excludeTags = excludeTags - it }
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            onSave(initial.copy(name = name.trim(), includeTags = includeTags, excludeTags = excludeTags))
                        }
                    },
                    enabled = name.isNotBlank()
                ) { Text("Save") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagInputRow(
    label: String,
    input: String,
    onInputChange: (String) -> Unit,
    tags: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    OutlinedTextField(
        value = input,
        onValueChange = onInputChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onAdd(input.trim().lowercase()) }),
        trailingIcon = {
            if (input.isNotBlank()) {
                IconButton(onClick = { onAdd(input.trim().lowercase()) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
    )
    if (tags.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { onRemove(tag) },
                    label = { Text(tag) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
                )
            }
        }
    }
}

// ─── My Taste ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TasteProfileSection(tasteProfile: List<Pair<String, Int>>) {
    if (tasteProfile.isEmpty()) {
        Text(
            "Save more images to generate your taste profile.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }
    val maxCount = tasteProfile.firstOrNull()?.second?.toFloat() ?: 1f
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tasteProfile.take(60).forEach { (tag, count) ->
            val weight = (count / maxCount).coerceIn(0.4f, 1f)
            val fontSize = (10 + (weight * 8)).sp
            SuggestionChip(
                onClick = {},
                label = { Text(tag, fontSize = fontSize) }
            )
        }
    }
}

// ─── Co-tag Explorer ─────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoTagExplorerSection(coTagMap: Map<String, List<String>>) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, coTagMap) {
        if (query.isBlank()) emptyList()
        else coTagMap.entries
            .filter { it.key.contains(query.trim().lowercase()) }
            .sortedByDescending { it.value.size }
            .take(5)
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search a tag") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            }
        )
        if (query.isBlank()) {
            Text(
                "Type a tag to see what commonly appears with it in your saves.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else if (results.isEmpty()) {
            Text(
                "No co-tag data yet for \"$query\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            results.forEach { (tag, coTags) ->
                Spacer(Modifier.height(8.dp))
                Text(tag, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                FlowRow(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    coTags.forEach { coTag ->
                        AssistChip(onClick = { query = coTag }, label = { Text(coTag) })
                    }
                }
            }
        }
    }
}
