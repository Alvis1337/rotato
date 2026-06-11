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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableIntStateOf
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
    val sfwTagTiers by vm.sfwTagTiers.collectAsStateWithLifecycle()
    val nsfwTagTiers by vm.nsfwTagTiers.collectAsStateWithLifecycle()
    val nsfwMode by vm.nsfwMode.collectAsStateWithLifecycle()
    val allTagTiers by vm.allTagTiers.collectAsStateWithLifecycle()
    val profiles by vm.interestProfiles.collectAsStateWithLifecycle()
    val tasteProfile by vm.tasteProfile.collectAsStateWithLifecycle()
    val coTagMap by vm.coTagMap.collectAsStateWithLifecycle()
    val editingProfile by vm.editingProfile.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

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
            if (selectedTab == 1) {
                FloatingActionButton(onClick = { vm.startEditProfile(null) }) {
                    Icon(Icons.Default.Add, contentDescription = "New profile")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Tiers") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Profiles") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("My Taste") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Co-tags") })
            }
            when (selectedTab) {
                0 -> TiersTab(
                    sfwTagTiers = sfwTagTiers,
                    nsfwTagTiers = nsfwTagTiers,
                    nsfwMode = nsfwMode,
                    onSetTier = { tag, tier, isNsfw -> vm.setTagTier(tag, tier, isNsfw) },
                    onRemove = { tag, isNsfw -> vm.removeTagTier(tag, isNsfw) },
                )
                1 -> ProfilesTab(profiles = profiles, onEdit = { vm.startEditProfile(it) }, onDelete = { vm.deleteProfile(it) })
                2 -> MyTasteTab(tasteProfile = tasteProfile, tagTiers = allTagTiers, onSetTier = { tag, tier -> vm.setTagTier(tag, tier) })
                3 -> CoTagsTab(coTagMap = coTagMap)
            }
        }
    }
}

// ─── Tab wrappers ────────────────────────────────────────────────────────────

@Composable
private fun TiersTab(
    sfwTagTiers: Map<String, TagTier>,
    nsfwTagTiers: Map<String, TagTier>,
    nsfwMode: Boolean,
    onSetTier: (String, TagTier, Boolean) -> Unit,
    onRemove: (String, Boolean) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            TagTiersSection(
                title = "SFW",
                tagTiers = sfwTagTiers,
                isNsfw = false,
                onSetTier = onSetTier,
                onRemove = onRemove,
            )
        }
        if (nsfwMode) {
            item {
                TagTiersSection(
                    title = "NSFW",
                    tagTiers = nsfwTagTiers,
                    isNsfw = true,
                    onSetTier = onSetTier,
                    onRemove = onRemove,
                )
            }
        }
    }
}

@Composable
private fun ProfilesTab(
    profiles: List<InterestProfile>,
    onEdit: (InterestProfile) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        if (profiles.isEmpty()) {
            item {
                Text(
                    "No profiles yet. Tap + to create one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            item {
                Text(
                    "Select profiles in Discover settings to activate them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(profiles, key = { it.id }) { profile ->
                ProfileRow(
                    profile = profile,
                    onEdit = { onEdit(profile) },
                    onDelete = { onDelete(profile.id) },
                )
            }
        }
    }
}

@Composable
private fun MyTasteTab(tasteProfile: List<Pair<String, Int>>, tagTiers: Map<String, TagTier>, onSetTier: (String, TagTier) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { TasteProfileSection(tasteProfile = tasteProfile, tagTiers = tagTiers, onSetTier = onSetTier) }
    }
}

@Composable
private fun CoTagsTab(coTagMap: Map<String, List<String>>) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { CoTagExplorerSection(coTagMap = coTagMap) }
    }
}

// ─── Tag Tiers ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagTiersSection(
    title: String,
    tagTiers: Map<String, TagTier>,
    isNsfw: Boolean,
    onSetTier: (String, TagTier, Boolean) -> Unit,
    onRemove: (String, Boolean) -> Unit,
) {
    var input by rememberSaveable(isNsfw) { mutableStateOf("") }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (isNsfw) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Add a $title tag") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                val tag = input.trim().lowercase()
                if (tag.isNotBlank() && tag !in tagTiers) {
                    onSetTier(tag, TagTier.LIKE, isNsfw)
                    input = ""
                }
            }),
            trailingIcon = {
                if (input.isNotBlank()) {
                    IconButton(onClick = {
                        val tag = input.trim().lowercase()
                        if (tag.isNotBlank() && tag !in tagTiers) {
                            onSetTier(tag, TagTier.LIKE, isNsfw)
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
                "No $title tags yet. Type a tag above and press Done.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            tagTiers.entries.sortedWith(compareBy({ it.value.ordinal }, { it.key })).forEach { (tag, tier) ->
                TagTierRow(
                    tag = tag,
                    tier = tier,
                    isNsfw = isNsfw,
                    onSetTier = onSetTier,
                    onRemove = onRemove,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TagTierRow(
    tag: String,
    tier: TagTier,
    isNsfw: Boolean,
    onSetTier: (String, TagTier, Boolean) -> Unit,
    onRemove: (String, Boolean) -> Unit,
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
        TierPicker(current = tier, onPick = { onSetTier(tag, it, isNsfw) })
        IconButton(onClick = { onRemove(tag, isNsfw) }, modifier = Modifier.size(32.dp)) {
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(profile.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (profile.isActive) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Text(
                                "Active",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
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
private fun TasteProfileSection(
    tasteProfile: List<Pair<String, Int>>,
    tagTiers: Map<String, TagTier>,
    onSetTier: (String, TagTier) -> Unit,
) {
    if (tasteProfile.isEmpty()) {
        Text(
            "Save more images to generate your taste profile.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }
    Text(
        "Tap a tag to assign a tier.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
    val maxCount = tasteProfile.firstOrNull()?.second?.toFloat() ?: 1f
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tasteProfile.take(60).forEach { (tag, count) ->
            val weight = (count / maxCount).coerceIn(0.4f, 1f)
            val fontSize = (10 + (weight * 8)).sp
            val currentTier = tagTiers[tag]
            TasteTagChip(
                tag = tag,
                fontSize = fontSize,
                currentTier = currentTier,
                onSetTier = { onSetTier(tag, it) },
            )
        }
    }
}

@Composable
private fun TasteTagChip(
    tag: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    currentTier: TagTier?,
    onSetTier: (TagTier) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val tierColor = when (currentTier) {
        TagTier.LOVE -> Color(0xFFB71C1C)
        else -> null
    }
    Box {
        SuggestionChip(
            onClick = { expanded = true },
            label = { Text(tag, fontSize = fontSize) },
            colors = if (currentTier != null) androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(
                containerColor = when (currentTier) {
                    TagTier.LOVE -> Color(0xFFB71C1C).copy(alpha = 0.15f)
                    TagTier.LIKE -> MaterialTheme.colorScheme.primaryContainer
                    TagTier.DISLIKE -> MaterialTheme.colorScheme.secondaryContainer
                    TagTier.NEVER -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surface
                }
            ) else androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors()
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            listOf(TagTier.LOVE to "♥ Love", TagTier.LIKE to "↑ Like", TagTier.DISLIKE to "↓ Dislike", TagTier.NEVER to "✕ Never").forEach { (tier, label) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label, fontWeight = if (currentTier == tier) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSetTier(tier); expanded = false }
                )
            }
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
