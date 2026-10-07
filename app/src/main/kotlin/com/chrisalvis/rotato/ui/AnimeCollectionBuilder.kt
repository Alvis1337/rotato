package com.chrisalvis.rotato.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.chrisalvis.rotato.data.AnimeTagResolver
import com.chrisalvis.rotato.data.AspectRatio
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.MalAnimeEntry
import com.chrisalvis.rotato.data.MinResolution
import com.chrisalvis.rotato.data.TagMatch
import com.chrisalvis.rotato.data.malStatusLabel
import com.chrisalvis.rotato.data.plugins.normalizeBooruQuery
import kotlinx.coroutines.delay

/** Everything the builder collected, handed to the view model to create or update the collection. */
internal data class AnimeCollectionDraft(
    val name: String,
    val animeTitle: String,
    val seriesTag: String,
    val characterTags: List<String>,
    val matchAny: Boolean,
    val fillCount: Int,
    val autoAddToLibrary: Boolean,
    val pluginId: String?,
    val instanceId: String?,
    val nsfwOverride: Boolean?,
    val minResolution: MinResolution,
    val aspectRatio: AspectRatio,
    val useMalFilter: Boolean,
)

private enum class BuilderStep { PICK, LOOK, FINISH }

/**
 * Full-screen, three-step way to turn a show from the user's MyAnimeList into a wallpaper
 * collection: pick a show (posters, English titles), check how image sites tag it and which
 * characters to include with a live preview, then name it and decide how it's used.
 */
@Composable
internal fun AnimeCollectionBuilder(
    entries: List<MalAnimeEntry>,
    loggedIn: Boolean,
    refreshing: Boolean,
    activeSources: List<LocalSource>,
    existingList: LocalList? = null,
    onRefresh: () -> Unit,
    onPreview: suspend (tags: String, matchAny: Boolean) -> List<BrainrotWallpaper>,
    onQuickStart: (List<MalAnimeEntry>, Boolean) -> Unit,
    onConfirm: (AnimeCollectionDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val existing = existingList?.malConfig
    var step by remember { mutableStateOf(if (existing != null) BuilderStep.LOOK else BuilderStep.PICK) }
    var picked by remember(entries) {
        mutableStateOf(existing?.let { cfg -> entries.find { it.title == cfg.animeTitle } ?: MalAnimeEntry(cfg.animeTitle, 0) })
    }
    // Look step
    var seriesTag by remember { mutableStateOf(existing?.animeQuery.orEmpty()) }
    val characters = remember { mutableStateListOf<String>().apply { existing?.characterTags?.let { addAll(it) } } }
    var together by remember { mutableStateOf(existing != null && !existing.matchAny && existing.characterTags.size > 1) }
    // Finish step
    var name by remember { mutableStateOf(existingList?.name.orEmpty()) }
    var fillCount by remember { mutableIntStateOf(existing?.fillCount ?: 25) }
    var autoAdd by remember { mutableStateOf(existing?.autoAddToLibrary ?: (existingList?.useAsRotation == true)) }
    var pluginId by remember { mutableStateOf(existing?.sourcePluginId) }
    var instanceId by remember { mutableStateOf(existing?.sourceInstanceId?.takeIf { it.isNotBlank() }) }
    var nsfwOverride by remember { mutableStateOf(existing?.nsfwOverride) }
    var minResolution by remember { mutableStateOf(existing?.minResolution ?: MinResolution.ANY) }
    var aspectRatio by remember { mutableStateOf(existing?.aspectRatio ?: AspectRatio.ANY) }

    fun choose(entry: MalAnimeEntry) {
        if (picked?.title != entry.title) {
            seriesTag = ""
            characters.clear()
        }
        picked = entry
        if (existingList == null || name.isBlank()) name = entry.displayTitle
        step = BuilderStep.LOOK
    }

    fun back() {
        when (step) {
            BuilderStep.PICK -> onDismiss()
            BuilderStep.LOOK -> if (existingList != null) onDismiss() else step = BuilderStep.PICK
            BuilderStep.FINISH -> step = BuilderStep.LOOK
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
        BackHandler { back() }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding()) {
                BuilderTopBar(
                    title = when (step) {
                        BuilderStep.PICK -> "New anime collection"
                        BuilderStep.LOOK -> picked?.displayTitle ?: ""
                        BuilderStep.FINISH -> "Almost done"
                    },
                    stepIndex = step.ordinal,
                    showBack = step != BuilderStep.PICK && !(existingList != null && step == BuilderStep.LOOK),
                    onBack = ::back,
                    onClose = onDismiss,
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    AnimatedContent(targetState = step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "step") { s ->
                        Box(Modifier.widthIn(max = 960.dp).fillMaxSize()) {
                            when (s) {
                                BuilderStep.PICK -> PickShowStep(entries, loggedIn, refreshing, onRefresh, onQuickStart, ::choose)
                                BuilderStep.LOOK -> picked?.let { entry ->
                                    LookStep(
                                        entry = entry,
                                        seriesTag = seriesTag,
                                        onSeriesTag = { seriesTag = it },
                                        characters = characters,
                                        together = together,
                                        onTogether = { together = it },
                                        onPreview = onPreview,
                                    )
                                }
                                BuilderStep.FINISH -> FinishStep(
                                    name = name, onName = { name = it },
                                    fillCount = fillCount, onFillCount = { fillCount = it },
                                    autoAdd = autoAdd, onAutoAdd = { autoAdd = it },
                                    activeSources = activeSources,
                                    pluginId = pluginId, instanceId = instanceId,
                                    onSource = { p, i -> pluginId = p; instanceId = i },
                                    nsfwOverride = nsfwOverride, onNsfw = { nsfwOverride = it },
                                    minResolution = minResolution, onMinResolution = { minResolution = it },
                                    aspectRatio = aspectRatio, onAspectRatio = { aspectRatio = it },
                                    startExpanded = existing != null && (existing.hasSourceOverride || existing.minResolution != MinResolution.ANY ||
                                        existing.aspectRatio != AspectRatio.ANY || existing.nsfwOverride != null),
                                )
                            }
                        }
                    }
                }
                if (step != BuilderStep.PICK) {
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (step == BuilderStep.LOOK) {
                            Text(
                                if (characters.isEmpty()) "Any art of the show" else "${characters.size} character${if (characters.size != 1) "s" else ""} picked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = { step = BuilderStep.FINISH }) { Text("Next") }
                        } else {
                            val entry = picked
                            Button(
                                enabled = entry != null && name.isNotBlank(),
                                onClick = {
                                    entry ?: return@Button
                                    onConfirm(
                                        AnimeCollectionDraft(
                                            name = name.trim(),
                                            animeTitle = entry.title,
                                            seriesTag = seriesTag.ifBlank { AnimeTagResolver.candidateQueries(entry).firstOrNull().orEmpty() },
                                            characterTags = characters.toList(),
                                            matchAny = characters.size > 1 && !together,
                                            fillCount = fillCount,
                                            autoAddToLibrary = autoAdd,
                                            pluginId = pluginId,
                                            instanceId = instanceId,
                                            nsfwOverride = nsfwOverride,
                                            minResolution = minResolution,
                                            aspectRatio = aspectRatio,
                                            useMalFilter = existing?.useMalFilter ?: false,
                                        )
                                    )
                                },
                            ) { Text(if (existingList != null) "Save changes" else "Create collection") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuilderTopBar(title: String, stepIndex: Int, showBack: Boolean, onBack: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }
        // Three-segment progress: Pick a show · Check the look · Finish
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Pick a show", "Check the look", "Finish").forEachIndexed { i, label ->
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                            .background(if (i <= stepIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i == stepIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

// ─── Step 1: pick a show ─────────────────────────────────────────────────────────────────────

private enum class ShowSort(val label: String) { FAVOURITES("Your favourites"), AZ("A–Z"), NEWEST("Newest") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickShowStep(
    entries: List<MalAnimeEntry>,
    loggedIn: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onQuickStart: (List<MalAnimeEntry>, Boolean) -> Unit,
    onPick: (MalAnimeEntry) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(ShowSort.FAVOURITES) }
    var quickStarted by remember { mutableStateOf(false) }
    val statuses = remember(entries) { entries.map { it.status }.filter { it.isNotBlank() }.distinct() }
    val shown = remember(entries, query, status, sort) {
        val q = query.trim()
        entries
            .filter { status == null || it.status == status }
            .filter { e ->
                q.isBlank() || e.displayTitle.contains(q, true) || e.title.contains(q, true) || e.synonyms.any { it.contains(q, true) }
            }
            .let { list ->
                when (sort) {
                    ShowSort.FAVOURITES -> list.sortedWith(compareByDescending<MalAnimeEntry> { it.score }.thenBy { it.displayTitle.lowercase() })
                    ShowSort.AZ -> list.sortedBy { it.displayTitle.lowercase() }
                    ShowSort.NEWEST -> list.sortedByDescending { it.year }
                }
            }
    }
    val favourites = remember(entries) { entries.filter { it.score >= 8 }.sortedByDescending { it.score }.take(3) }

    if (entries.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                if (loggedIn) "Your MyAnimeList hasn't loaded yet" else "Connect MyAnimeList first",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (loggedIn) "Pull your list and pick any show you've watched."
                else "Sign in under Settings › Integrations and your shows will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (loggedIn) {
                Spacer(Modifier.height(16.dp))
                if (refreshing) CircularProgressIndicator() else Button(onClick = onRefresh) { Text("Load my list") }
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 132.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Pick a show you love and Rotato gathers its art from your sources into a collection you can rotate through.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search your list") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (statuses.size > 1) {
                        FilterChip(selected = status == null, onClick = { status = null }, label = { Text("All") })
                        statuses.forEach { st ->
                            FilterChip(selected = status == st, onClick = { status = if (status == st) null else st }, label = { Text(malStatusLabel(st)) })
                        }
                    }
                    ShowSort.entries.forEach { s ->
                        if (s == ShowSort.NEWEST && entries.none { it.year > 0 }) return@forEach
                        if (s == ShowSort.FAVOURITES && entries.none { it.score > 0 }) return@forEach
                        FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.label) })
                    }
                }
                if (refreshing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text("Getting English titles and posters from MyAnimeList…", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (loggedIn && entries.none { it.hasDetails }) {
                    TextButton(onClick = onRefresh, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Show English titles and posters")
                    }
                }
                if (favourites.size >= 2 && query.isBlank() && status == null && !quickStarted) {
                    QuickStartCard(favourites) { addToRotation ->
                        quickStarted = true
                        onQuickStart(favourites, addToRotation)
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Nothing on your list matches \"${query.trim()}\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(shown, key = { it.title }) { entry -> ShowPoster(entry) { onPick(entry) } }
    }
}

@Composable
private fun QuickStartCard(favourites: List<MalAnimeEntry>, onGo: (Boolean) -> Unit) {
    var addToRotation by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Quick start", fontWeight = FontWeight.SemiBold)
            }
            Text(
                "One tap makes a collection for each of your top shows: ${favourites.joinToString(", ") { it.displayTitle }}.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = addToRotation, onCheckedChange = { addToRotation = it })
                Spacer(Modifier.width(8.dp))
                Text("Add them to my wallpaper rotation", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Button(onClick = { onGo(addToRotation) }) { Text("Make ${favourites.size}") }
            }
        }
    }
}

@Composable
private fun ShowPoster(entry: MalAnimeEntry, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
                )
            )
            .clickable(onClick = onClick)
    ) {
        if (entry.picture.isNotBlank()) {
            AsyncImage(model = entry.picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                entry.displayTitle.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.6f).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        )
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(
                entry.displayTitle,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            entry.originalTitle?.let {
                Text(it, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (entry.score > 0) PosterPill { Icon(Icons.Default.Star, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(12.dp)); Text(" ${entry.score}", color = Color.White, fontSize = 11.sp) }
            if (entry.status == "watching") PosterPill { Text("Watching", color = Color.White, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun PosterPill(content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) { content() }
}

// ─── Step 2: check the look ──────────────────────────────────────────────────────────────────

private fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f).replace(".0M", "M")
    n >= 10_000 -> "${n / 1000}k"
    n >= 1_000 -> "%.1fk".format(n / 1000f).replace(".0k", "k")
    else -> "$n"
}

/** "midoriya_izuku" → "Midoriya Izuku"; drops a "(series)" qualifier. */
private fun characterLabel(tag: String): String =
    tag.replace(Regex("_\\([^)]*\\)$"), "").replace('_', ' ')
        .split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookStep(
    entry: MalAnimeEntry,
    seriesTag: String,
    onSeriesTag: (String) -> Unit,
    characters: MutableList<String>,
    together: Boolean,
    onTogether: (Boolean) -> Unit,
    onPreview: suspend (String, Boolean) -> List<BrainrotWallpaper>,
) {
    var series by remember(entry.title) { mutableStateOf<List<TagMatch>?>(null) }
    var castLoading by remember { mutableStateOf(false) }
    var cast by remember { mutableStateOf<List<TagMatch>>(emptyList()) }
    var customTag by remember { mutableStateOf("") }
    var showCustom by remember { mutableStateOf(false) }
    var characterSearch by remember { mutableStateOf("") }
    var characterResults by remember { mutableStateOf<List<TagMatch>>(emptyList()) }
    val fallback = remember(entry.title) { AnimeTagResolver.candidateQueries(entry).firstOrNull().orEmpty() }

    LaunchedEffect(entry.title) {
        val found = AnimeTagResolver.seriesTags(entry)
        series = found
        if (seriesTag.isBlank()) onSeriesTag(found.firstOrNull()?.name ?: fallback)
    }
    LaunchedEffect(seriesTag) {
        if (seriesTag.isBlank()) return@LaunchedEffect
        castLoading = true
        cast = AnimeTagResolver.characters(seriesTag)
        castLoading = false
    }
    LaunchedEffect(characterSearch) {
        if (characterSearch.trim().length < 2) { characterResults = emptyList(); return@LaunchedEffect }
        delay(300)
        characterResults = AnimeTagResolver.search(characterSearch)
    }

    val query = if (characters.isNotEmpty()) characters.joinToString(" ") else seriesTag
    val matchAny = characters.size > 1 && !together
    var preview by remember { mutableStateOf<List<BrainrotWallpaper>?>(null) }
    LaunchedEffect(query, matchAny) {
        if (query.isBlank()) return@LaunchedEffect
        preview = null
        delay(350)
        preview = onPreview(query, matchAny)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (entry.picture.isNotBlank()) {
                AsyncImage(
                    model = entry.picture, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.width(64.dp).aspectRatio(0.68f).clip(RoundedCornerShape(12.dp)),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(entry.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                entry.originalTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val meta = listOfNotNull(
                    entry.year.takeIf { it > 0 }?.toString(),
                    entry.status.takeIf { it.isNotBlank() }?.let(::malStatusLabel),
                    entry.score.takeIf { it > 0 }?.let { "You rated it $it" },
                )
                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // How sites tag it
        BuilderSection(
            title = "How image sites tag it",
            hint = "Sites tag a series once, usually under its own name, so seasons and subtitles are dropped.",
        ) {
            val found = series
            when {
                found == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Text("Looking it up…", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    val options = (found.map { it.name to "${it.label} · ${compactCount(it.postCount)}" } +
                        listOfNotNull(seriesTag.takeIf { t -> t.isNotBlank() && found.none { it.name == t } }?.let { it to it.replace('_', ' ') }))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        options.forEach { (tag, label) ->
                            FilterChip(
                                selected = seriesTag == tag,
                                onClick = { onSeriesTag(tag); characters.clear() },
                                label = { Text(label) },
                                leadingIcon = if (seriesTag == tag) ({ Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp)) }) else null,
                            )
                        }
                        FilterChip(selected = showCustom, onClick = { showCustom = !showCustom }, label = { Text("Use another tag") })
                    }
                    if (found.isEmpty()) {
                        Text(
                            "Couldn't confirm a tag for this show, so Rotato will search for \"${seriesTag.replace('_', ' ')}\". Check the preview below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AnimatedVisibility(showCustom) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customTag,
                                onValueChange = { customTag = it },
                                placeholder = { Text("e.g. my_hero_academia") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(
                                enabled = customTag.isNotBlank(),
                                onClick = { onSeriesTag(normalizeBooruQuery(customTag)); characters.clear(); showCustom = false },
                            ) { Text("Use") }
                        }
                    }
                }
            }
        }

        // Characters
        BuilderSection(
            title = "Only certain characters?",
            hint = "Optional. Leave empty for any art of the show.",
        ) {
            if (castLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Text("Finding the cast…", style = MaterialTheme.typography.bodySmall)
                }
            }
            val chips = (cast.map { it.name } + characters).distinct()
            if (chips.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    chips.forEach { tag ->
                        val on = tag in characters
                        FilterChip(
                            selected = on,
                            onClick = { if (on) characters.remove(tag) else characters.add(tag) },
                            label = { Text(characterLabel(tag)) },
                        )
                    }
                }
            } else if (!castLoading) {
                Text("No cast list found. You can still search for a character below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = characterSearch,
                onValueChange = { characterSearch = it },
                placeholder = { Text("Add someone else") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (characterResults.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    characterResults.forEach { m ->
                        FilterChip(
                            selected = m.name in characters,
                            onClick = {
                                if (m.name !in characters) characters.add(m.name)
                                characterSearch = ""
                            },
                            label = { Text("${characterLabel(m.name)} · ${compactCount(m.postCount)}") },
                        )
                    }
                }
            }
            if (characters.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !together, onClick = { onTogether(false) }, label = { Text("Any of them") })
                    FilterChip(selected = together, onClick = { onTogether(true) }, label = { Text("Together in one image") })
                }
            }
        }

        // Preview
        BuilderSection(title = "Preview", hint = "A sample of what your sources have right now.") {
            val items = preview
            when {
                query.isBlank() || items == null -> PreviewGrid(List(6) { null })
                items.isEmpty() -> Text(
                    if (characters.isNotEmpty()) "Nothing found for this mix. Try fewer characters or \"Any of them\"."
                    else "Nothing found with this tag on your sources. Try another tag above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> PreviewGrid(items)
            }
        }
    }
}

@Composable
private fun PreviewGrid(items: List<BrainrotWallpaper?>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { wp ->
                    Box(
                        Modifier.weight(1f).aspectRatio(0.75f).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (wp != null) {
                            AsyncImage(
                                model = wp.thumbUrl.ifBlank { wp.sampleUrl },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BuilderSection(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

// ─── Step 3: finish ──────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinishStep(
    name: String,
    onName: (String) -> Unit,
    fillCount: Int,
    onFillCount: (Int) -> Unit,
    autoAdd: Boolean,
    onAutoAdd: (Boolean) -> Unit,
    activeSources: List<LocalSource>,
    pluginId: String?,
    instanceId: String?,
    onSource: (String?, String?) -> Unit,
    nsfwOverride: Boolean?,
    onNsfw: (Boolean?) -> Unit,
    minResolution: MinResolution,
    onMinResolution: (MinResolution) -> Unit,
    aspectRatio: AspectRatio,
    onAspectRatio: (AspectRatio) -> Unit,
    startExpanded: Boolean,
) {
    var more by remember { mutableStateOf(startExpanded) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text("Collection name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        BuilderSection(title = "Start with", hint = "You can always add more later from the collection's menu.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10 to "A few", 25 to "A good mix", 50 to "Plenty", 100 to "Loads").forEach { (n, label) ->
                    FilterChip(selected = fillCount == n, onClick = { onFillCount(n) }, label = { Text("$label · $n") })
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onAutoAdd(!autoAdd) }
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Use as wallpapers", fontWeight = FontWeight.SemiBold)
                Text(
                    "Adds these images to your Library so they rotate on your screens. Off keeps them as a collection to browse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = autoAdd, onCheckedChange = onAutoAdd)
        }
        TextButton(onClick = { more = !more }, contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (more) "Fewer options" else "More options")
        }
        AnimatedVisibility(more) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (activeSources.size > 1) {
                    BuilderSection(title = "Where from") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(selected = pluginId == null, onClick = { onSource(null, null) }, label = { Text("All my sources") })
                            activeSources.forEach { src ->
                                val sel = pluginId == src.pluginId && (instanceId ?: "") == src.instanceId
                                val label = if (src.pluginId == "REDDIT" && src.instanceId.isNotBlank()) "r/${src.instanceId}"
                                    else src.pluginId.lowercase().replaceFirstChar { it.uppercase() }
                                FilterChip(selected = sel, onClick = { onSource(src.pluginId, src.instanceId.ifBlank { null }) }, label = { Text(label) })
                            }
                        }
                    }
                }
                BuilderSection(title = "Image quality", hint = "Higher means sharper wallpapers but fewer matches.") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            MinResolution.ANY to "Any", MinResolution.HD to "720p+", MinResolution.FHD to "1080p+",
                            MinResolution.QHD to "1440p+", MinResolution.UHD to "4K",
                        ).forEach { (r, label) -> FilterChip(selected = minResolution == r, onClick = { onMinResolution(r) }, label = { Text(label) }) }
                    }
                }
                BuilderSection(title = "Shape") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            AspectRatio.ANY to "Any", AspectRatio.PORTRAIT to "Tall (phone)", AspectRatio.WIDE to "Wide",
                            AspectRatio.STANDARD to "4:3", AspectRatio.ULTRAWIDE to "Ultrawide",
                        ).forEach { (a, label) -> FilterChip(selected = aspectRatio == a, onClick = { onAspectRatio(a) }, label = { Text(label) }) }
                    }
                }
                if (!LocalNsfwHidden.current) {
                    BuilderSection(title = "NSFW", hint = "Auto follows your NSFW setting. Off keeps this collection safe even when NSFW is on.") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = nsfwOverride != false, onClick = { onNsfw(null) }, label = { Text("Auto") })
                            FilterChip(selected = nsfwOverride == false, onClick = { onNsfw(false) }, label = { Text("Off") })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.heightIn(min = 24.dp))
    }
}
