package com.whatsappautoreply.presentation.settings

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatsappautoreply.R

import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onDebugClick: () -> Unit = {},
    onBrainClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val defaultTone by viewModel.defaultTone.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saveMessage by viewModel.saveMessage.collectAsStateWithLifecycle()

    val autoReplyEnabled by viewModel.autoReplyEnabled.collectAsStateWithLifecycle()
    val minDelay by viewModel.minDelay.collectAsStateWithLifecycle()
    val maxDelay by viewModel.maxDelay.collectAsStateWithLifecycle()
    val cooldown by viewModel.cooldown.collectAsStateWithLifecycle()

    val excludeGroupChats by viewModel.excludeGroupChats.collectAsStateWithLifecycle()
    val replyToQuestionsOnly by viewModel.replyToQuestionsOnly.collectAsStateWithLifecycle()
    val waitForUserSeconds by viewModel.waitForUserSeconds.collectAsStateWithLifecycle()

    val quietHoursEnabled by viewModel.quietHoursEnabled.collectAsStateWithLifecycle()
    val quietHoursStart by viewModel.quietHoursStart.collectAsStateWithLifecycle()
    val quietHoursEnd by viewModel.quietHoursEnd.collectAsStateWithLifecycle()

    var apiKeyVisible by remember { mutableStateOf(false) }

    LaunchedEffect(saveMessage) {
        saveMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearSaveMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ─── 1. App Branding Header Card ─────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = "WhatsApp Auto-Reply Assistant Logo",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Column {
                        Text(
                            text = "WhatsApp Auto-Reply",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "AI Notification Assistant",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "v2.0 • Multi-Provider LLM Engine",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // ─── 2. Agent Brain Section ───────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onBrainClick,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🧠", style = MaterialTheme.typography.headlineMedium)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Agent Brain",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Edit OWL's personality, memory, user profile, and operating rules",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Open Brain Editor",
                        modifier = Modifier.rotate(180f),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // ─── 3. Global & Auto-Reply Settings Section ─────────────────────
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Auto-Reply Settings",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = autoReplyEnabled,
                                role = Role.Switch,
                                onValueChange = { viewModel.updateAutoReplyEnabled(it) }
                            )
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Auto-Reply Globally",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = autoReplyEnabled,
                            onCheckedChange = null
                        )
                    }

                    HorizontalDivider()

                    Text(
                        text = "Delay Range (seconds)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = minDelay.toString(),
                            onValueChange = { viewModel.updateMinDelay(it.toIntOrNull() ?: 0) },
                            label = { Text("Min") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = maxDelay.toString(),
                            onValueChange = { viewModel.updateMaxDelay(it.toIntOrNull() ?: 0) },
                            label = { Text("Max") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                        )
                    }

                    Text(
                        text = "Cooldown (seconds)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = cooldown.toString(),
                        onValueChange = { viewModel.updateCooldown(it.toIntOrNull() ?: 0) },
                        label = { Text("Cooldown between replies") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                    )
                }
            }

            // ─── 4. Advanced Logic Section ───────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Advanced Logic",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = excludeGroupChats,
                                role = Role.Switch,
                                onValueChange = { viewModel.updateExcludeGroupChats(it) }
                            )
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Exclude Group Chats",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = excludeGroupChats,
                            onCheckedChange = null
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = replyToQuestionsOnly,
                                role = Role.Switch,
                                onValueChange = { viewModel.updateReplyToQuestionsOnly(it) }
                            )
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reply to Questions Only",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = replyToQuestionsOnly,
                            onCheckedChange = null
                        )
                    }

                    HorizontalDivider()

                    Text(
                        text = "Wait for User Activity (seconds)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Don't auto-reply if you sent a message recently.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = waitForUserSeconds.toString(),
                        onValueChange = { viewModel.updateWaitForUserSeconds(it.toIntOrNull() ?: 0) },
                        label = { Text("Wait time (0 to disable)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                    )
                }
            }

            // ─── 5. Quiet Hours Section ──────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Quiet Hours",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = quietHoursEnabled,
                                role = Role.Switch,
                                onValueChange = { viewModel.updateQuietHoursEnabled(it) }
                            )
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Quiet Hours",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = quietHoursEnabled,
                            onCheckedChange = null
                        )
                    }

                    if (quietHoursEnabled) {
                        Text(
                            text = "Auto-reply will pause during these hours (24h format)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = quietHoursStart.toString(),
                                onValueChange = { viewModel.updateQuietHoursStart(it.toIntOrNull()?.coerceIn(0, 23) ?: 22) },
                                label = { Text("Start Hour (0-23)") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                            )
                            OutlinedTextField(
                                value = quietHoursEnd.toString(),
                                onValueChange = { viewModel.updateQuietHoursEnd(it.toIntOrNull()?.coerceIn(0, 23) ?: 7) },
                                label = { Text("End Hour (0-23)") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                            )
                        }
                    }
                }
            }

            // ─── 6. API Key & Model Provider Section ────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "LLM Provider Key",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Enter your OpenRouter or Gemini API Key. Left blank will use default bootstrap key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { viewModel.updateApiKey(it) },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (apiKeyVisible) "Hide key" else "Show key"
                                )
                            }
                        },
                        singleLine = true
                    )
                }
            }

            // ─── 7. Default Tone Section ─────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Default Reply Settings",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Default tone for new chats (can be overridden per chat)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val toneOptions = listOf("auto", "friendly", "professional", "flirty", "funny", "chill", "romantic", "formal")
                    var expanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = defaultTone.replaceFirstChar { it.uppercaseChar() },
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Default Tone") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            toneOptions.forEach { tone ->
                                DropdownMenuItem(
                                    text = { Text(tone.replaceFirstChar { it.uppercaseChar() }) },
                                    onClick = {
                                        viewModel.updateDefaultTone(tone)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = "The AI has its own personality and adapts naturally. This is a gentle hint, not a hard mode switch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ─── 8. Save Button ───────────────────────────────────────────────
            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isSaving) "Saving..." else "Save Settings")
            }

            // ─── 9. Developer Section ─────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Developer Tools",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(
                        onClick = onDebugClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View LLM Debug Panel")
                    }
                }
            }

            // ─── 10. Info Section ─────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "This app uses OpenRouter AI models to generate context-aware replies with a natural, human-like persona. Your data is processed locally and via OpenRouter's API.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
