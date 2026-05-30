package com.chrisalvis.rotato.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Default.AutoAwesome,
        title = "Welcome to rotato",
        body = "Automatically rotate your wallpaper with curated anime art from community sources.",
    ),
    OnboardingPage(
        icon = Icons.Default.Explore,
        title = "Discover",
        body = "Browse and search thousands of wallpapers. Swipe right to instantly add one to your rotation queue.",
    ),
    OnboardingPage(
        icon = Icons.Default.PhotoLibrary,
        title = "Library",
        body = "Your rotation pool lives here. Tap an image to preview, set as wallpaper, or remove it. Drag to reorder.",
    ),
    OnboardingPage(
        icon = Icons.Default.Collections,
        title = "Collections",
        body = "Organise favourites into named collections. Sync a whole collection to your rotation with one tap.",
    ),
)

// Total pages: info pages + plugin store page + permissions page
private val TOTAL_PAGES = pages.size + 2
private val PLUGIN_STORE_PAGE = pages.size       // index of plugin store page
private val PERMISSIONS_PAGE = pages.size + 1    // index of permissions page

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    vm: SetupViewModel = viewModel()
) {
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onSetupComplete() }

    val pagerState = rememberPagerState(pageCount = { TOTAL_PAGES })
    val scope = rememberCoroutineScope()

    val isLastPage = pagerState.currentPage == TOTAL_PAGES - 1

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Skip / page counter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${pagerState.currentPage + 1} / $TOTAL_PAGES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isLastPage) {
                    TextButton(onClick = onSetupComplete) { Text("Skip") }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when {
                    page < pages.size -> OnboardingPageContent(page = pages[page])
                    page == PLUGIN_STORE_PAGE -> PluginStorePage(vm = vm)
                    else -> PermissionsPage()
                }
            }

            // Dot indicators
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(TOTAL_PAGES) { i ->
                    val width by animateDpAsState(
                        targetValue = if (i == pagerState.currentPage) 20.dp else 6.dp,
                        animationSpec = tween(300),
                        label = "dot_width_$i"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            // Action button
            Button(
                onClick = {
                    if (isLastPage) {
                        vm.complete()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onSetupComplete()
                        }
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding()
                    .height(56.dp)
            ) {
                Text(if (isLastPage) "Get Started" else "Next")
            }
        }
    }
}

@Composable
private fun PluginStorePage(vm: SetupViewModel) {
    val storeState by vm.storeState.collectAsStateWithLifecycle()
    val storeEntries by vm.storeEntries.collectAsStateWithLifecycle()
    val installingIds by vm.installingIds.collectAsStateWithLifecycle()
    val installedIds by vm.installedIds.collectAsStateWithLifecycle()

    // Auto-load the store when this page is shown
    LaunchedEffect(Unit) { vm.loadOfficialStore() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Install Plugins",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Rotato doesn't include any sources out of the box. Install plugins from the official store to start fetching wallpapers.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))

        when (storeState) {
            SetupStoreState.IDLE, SetupStoreState.LOADING -> {
                CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            }
            SetupStoreState.ERROR -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Couldn't reach the plugin store.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.loadOfficialStore() }) { Text("Retry") }
                    Text("You can also add plugins later from Sources → Plugin Store.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            SetupStoreState.LOADED -> {
                val safeEntries = storeEntries.filter { it.safeContent }
                val nsfwEntries = storeEntries.filter { !it.safeContent }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (safeEntries.isNotEmpty()) {
                        item {
                            Text("SAFE CONTENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                        }
                        items(safeEntries, key = { it.id }) { entry ->
                            SetupPluginRow(entry, installingIds, installedIds, vm)
                        }
                    }
                    if (nsfwEntries.isNotEmpty()) {
                        item {
                            Text("NSFW / 18+", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
                        }
                        items(nsfwEntries, key = { it.id }) { entry ->
                            SetupPluginRow(entry, installingIds, installedIds, vm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupPluginRow(
    entry: com.chrisalvis.rotato.data.plugins.PluginStoreEntry,
    installingIds: Set<String>,
    installedIds: Set<String>,
    vm: SetupViewModel,
) {
    val isInstalling = entry.id in installingIds
    val isInstalled = entry.id in installedIds
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (entry.description.isNotBlank()) {
                Text(entry.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        when {
            isInstalled -> Icon(Icons.Default.Check, contentDescription = "Installed", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            isInstalling -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else -> OutlinedButton(
                onClick = { vm.installPlugin(entry) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) { Text("Add", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PermissionsPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            text = "One last thing",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "rotato will ask for notification permission so it can show a quick control after each wallpaper change. You can always change this later in Settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        PermissionTip(text = "Notifications — see and control the current wallpaper from your shade")
        Spacer(Modifier.height(8.dp))
        PermissionTip(text = "No account, no tracking, no ads. Ever.")
    }
}

@Composable
private fun PermissionTip(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}


private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Default.AutoAwesome,
        title = "Welcome to rotato",
        body = "Automatically rotate your wallpaper with curated anime art from Danbooru, Gelbooru, Safebooru, and more.",
    ),
    OnboardingPage(
        icon = Icons.Default.Explore,
        title = "Discover",
        body = "Browse and search thousands of wallpapers. Swipe right to instantly add one to your rotation queue.",
    ),
    OnboardingPage(
        icon = Icons.Default.PhotoLibrary,
        title = "Library",
        body = "Your rotation pool lives here. Tap an image to preview, set as wallpaper, or remove it. Drag to reorder.",
    ),
    OnboardingPage(
        icon = Icons.Default.Collections,
        title = "Collections",
        body = "Organise favourites into named collections. Sync a whole collection to your rotation with one tap.",
    ),
)

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    vm: SetupViewModel = viewModel()
) {
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onSetupComplete() }

    val pagerState = rememberPagerState(pageCount = { pages.size + 1 })
    val scope = rememberCoroutineScope()

    val isLastPage = pagerState.currentPage == pages.size

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Skip / page counter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${pagerState.currentPage + 1} / ${pages.size + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isLastPage) {
                    TextButton(onClick = onSetupComplete) { Text("Skip") }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                if (page < pages.size) {
                    OnboardingPageContent(page = pages[page])
                } else {
                    PermissionsPage()
                }
            }

            // Dot indicators
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size + 1) { i ->
                    val width by animateDpAsState(
                        targetValue = if (i == pagerState.currentPage) 20.dp else 6.dp,
                        animationSpec = tween(300),
                        label = "dot_width_$i"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            // Action button
            Button(
                onClick = {
                    if (isLastPage) {
                        vm.complete()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onSetupComplete()
                        }
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding()
                    .height(56.dp)
            ) {
                Text(if (isLastPage) "Get Started" else "Next")
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PermissionsPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            text = "One last thing",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "rotato will ask for notification permission so it can show a quick control after each wallpaper change. You can always change this later in Settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        PermissionTip(text = "Notifications — see and control the current wallpaper from your shade")
        Spacer(Modifier.height(8.dp))
        PermissionTip(text = "No account, no tracking, no ads. Ever.")
    }
}

@Composable
private fun PermissionTip(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
