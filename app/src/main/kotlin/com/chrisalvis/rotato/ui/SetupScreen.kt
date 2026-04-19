package com.chrisalvis.rotato.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    vm: SetupViewModel = viewModel()
) {
    val step by vm.step.collectAsStateWithLifecycle()
    val connectState by vm.connectState.collectAsStateWithLifecycle()
    val feedCount by vm.feedCount.collectAsStateWithLifecycle()

    LaunchedEffect(step) {
        if (step == SetupStep.DONE && connectState is ConnectState.Idle) {
            // stay on done screen — user taps "Start Discovering"
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
            },
            label = "setup_step"
        ) { currentStep ->
            when (currentStep) {
                SetupStep.WELCOME -> WelcomePage(onGetStarted = { vm.advanceToConnect() })
                SetupStep.CONNECT -> ConnectPage(
                    connectState = connectState,
                    onConnect = { url, key, hdrs -> vm.connect(url, key, hdrs) },
                    onSkip = {
                        vm.skip()
                        onSetupComplete()
                    }
                )
                SetupStep.DONE -> DonePage(
                    feedCount = feedCount,
                    onStart = onSetupComplete
                )
            }
        }
    }
}

@Composable
private fun WelcomePage(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = "rotato",
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Anime wallpapers, automatically.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(64.dp))
        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Get Started")
        }
    }
}

@Composable
private fun ConnectPage(
    connectState: ConnectState,
    onConnect: (String, String, Map<String, String>) -> Unit,
    onSkip: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    // List of (key, value) pairs for custom headers
    var customHeaders by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    val isConnecting = connectState is ConnectState.Connecting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Connect to your server",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Point rotato at your animebacks instance to sync your feeds automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Server URL") },
            placeholder = { Text("http://192.168.1.1:3000") },
            singleLine = true,
            enabled = !isConnecting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key (optional)") },
            singleLine = true,
            enabled = !isConnecting,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        // Advanced: custom headers
        TextButton(
            onClick = { showAdvanced = !showAdvanced },
            enabled = !isConnecting,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Icon(
                imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Custom headers", style = MaterialTheme.typography.labelMedium)
        }
        AnimatedVisibility(visible = showAdvanced) {
            Column(modifier = Modifier.fillMaxWidth()) {
                customHeaders.forEachIndexed { idx, (k, v) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = k,
                            onValueChange = { nk -> customHeaders = customHeaders.toMutableList().also { it[idx] = nk to v } },
                            label = { Text("Name") },
                            singleLine = true,
                            enabled = !isConnecting,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = v,
                            onValueChange = { nv -> customHeaders = customHeaders.toMutableList().also { it[idx] = k to nv } },
                            label = { Text("Value") },
                            singleLine = true,
                            enabled = !isConnecting,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { customHeaders = customHeaders.toMutableList().also { it.removeAt(idx) } },
                            enabled = !isConnecting
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove header", modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                TextButton(
                    onClick = { customHeaders = customHeaders + ("" to "") },
                    enabled = !isConnecting
                ) {
                    Text("+ Add header")
                }
            }
        }
        if (connectState is ConnectState.Error) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = connectState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val hdrs = customHeaders
                    .filter { (k, _) -> k.isNotBlank() }
                    .associate { (k, v) -> k.trim() to v.trim() }
                onConnect(url, apiKey, hdrs)
            },
            enabled = url.isNotBlank() && !isConnecting,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Connect")
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSkip, enabled = !isConnecting) {
            Text("Skip for now")
        }
    }
}

@Composable
private fun DonePage(feedCount: Int, onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "All set!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (feedCount > 0) "$feedCount feed(s) synced from your server"
                   else "No feeds found — you can add them later in Settings",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Start Discovering")
        }
    }
}
