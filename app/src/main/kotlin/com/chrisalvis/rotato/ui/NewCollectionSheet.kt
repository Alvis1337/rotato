package com.chrisalvis.rotato.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * "What kind of collection?" Each kind is explained in a line so people can tell plain,
 * smart and anime collections apart before any options appear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewCollectionSheet(
    malConnected: Boolean,
    managedMalCount: Int,
    onPlain: () -> Unit,
    onSmart: () -> Unit,
    onAnime: () -> Unit,
    onSyncAnime: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("New collection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            KindRow(
                icon = Icons.Default.BookmarkBorder,
                title = "Empty collection",
                body = "Start empty and save images into it from Discover or your Library.",
                onClick = onPlain,
            )
            KindRow(
                icon = Icons.Default.AutoAwesome,
                title = "Smart collection",
                body = "Fills itself with the images you've saved that share tags, like scenery or a favourite character, and keeps up as you save more.",
                onClick = onSmart,
            )
            KindRow(
                icon = Icons.Default.Movie,
                title = "From a show you watch",
                body = if (malConnected) "Pick an anime from your MyAnimeList and Rotato gathers its art from your sources."
                       else "Connect MyAnimeList in Settings › Integrations, then turn any show you've watched into a collection.",
                onClick = onAnime,
            )
            if (managedMalCount > 0) {
                TextButton(onClick = onSyncAnime) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Top up my $managedMalCount anime collection${if (managedMalCount != 1) "s" else ""}")
                }
            }
        }
    }
}

@Composable
private fun KindRow(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
