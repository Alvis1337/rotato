package com.chrisalvis.rotato.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrisalvis.rotato.data.LocalList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NsfwPrivacySettingsScreen(
    viewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
) {
    val nsfwBlurEnabled by viewModel.nsfwBlurEnabled.collectAsStateWithLifecycle()
    val nsfwHomeOnly by viewModel.nsfwHomeOnly.collectAsStateWithLifecycle()
    val stealthCollectionId by viewModel.stealthCollectionId.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("NSFW & Privacy", fontWeight = FontWeight.Bold) }
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
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    SettingsSection(title = "NSFW") {
                        SettingsToggleRow(
                            title = "Blur NSFW previews",
                            subtitle = "Blur explicit images/videos in grids until tapped — also acts as a safety net if one slips through with NSFW mode off",
                            checked = nsfwBlurEnabled,
                            onCheckedChange = { viewModel.setNsfwBlurEnabled(it) }
                        )
                        SettingsToggleRow(
                            title = "NSFW → home screen only",
                            subtitle = "Explicit wallpapers are restricted to the home screen regardless of your Wallpaper Target above — lock screen stays clean",
                            checked = nsfwHomeOnly,
                            onCheckedChange = { viewModel.setNsfwHomeOnly(it) }
                        )
                        StealthCollectionDropdown(
                            selectedCollectionId = stealthCollectionId,
                            lists = collections,
                            onSelect = viewModel::setStealthCollectionId
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StealthCollectionDropdown(
    selectedCollectionId: String,
    lists: List<LocalList>,
    onSelect: (String) -> Unit,
) {
    SettingsDropdown(
        label = "Stealth collection",
        description = "The QS \"Stealth Mode\" tile switches rotation entirely to this collection and forces NSFW off, until you tap it again.",
        items = listOf<LocalList?>(null) + lists,
        selectedLabel = lists.firstOrNull { it.id == selectedCollectionId }?.name ?: "Not set",
        itemLabel = { it?.name ?: "Not set" },
        onSelect = { onSelect(it?.id ?: "") },
    )
}
