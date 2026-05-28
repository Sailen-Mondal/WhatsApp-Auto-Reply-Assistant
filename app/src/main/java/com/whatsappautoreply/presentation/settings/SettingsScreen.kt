package com.whatsappautoreply.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onDebugClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val defaultTone by viewModel.defaultTone.collectAsState(initial = "auto")
    val isSaving by viewModel.isSaving.collectAsState(initial = false)

    LaunchedEffect(Unit) {
        viewModel.loadSettings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // Auto-Reply Settings Section
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

                    val autoReplyEnabled by viewModel.autoReplyEnabled.collectAsState(initial = false)
                    val minDelay by viewModel.minDelay.collectAsState(initial = 5)
                    val maxDelay by viewModel.maxDelay.collectAsState(initial = 60)
                    val cooldown by viewModel.cooldown.collectAsState(initial = 5)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Auto-Reply Globally",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = autoReplyEnabled,
                            onCheckedChange = { viewModel.updateAutoReplyEnabled(it) }
                        )
                    }

                    Divider()

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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = maxDelay.toString(),
                            onValueChange = { viewModel.updateMaxDelay(it.toIntOrNull() ?: 0) },
                            label = { Text("Max") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }



            // Advanced Logic Section
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

                    val excludeGroupChats by viewModel.excludeGroupChats.collectAsState(initial = true)
                    val replyToQuestionsOnly by viewModel.replyToQuestionsOnly.collectAsState(initial = false)
                    val waitForUserSeconds by viewModel.waitForUserSeconds.collectAsState(initial = 0)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Exclude Group Chats",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = excludeGroupChats,
                            onCheckedChange = { viewModel.updateExcludeGroupChats(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reply to Questions Only",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = replyToQuestionsOnly,
                            onCheckedChange = { viewModel.updateReplyToQuestionsOnly(it) }
                        )
                    }

                    Divider()

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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            // Default Settings Section
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

                    val toneOptions = listOf("auto", "flirty", "funny", "professional", "chill", "romantic", "happy", "sad", "angry")
                    
                    var expanded by remember { mutableStateOf(false) }
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = defaultTone,
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
                }
            }

            // Save Button
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

            // Developer Section
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

            // Info Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "This app uses Hugging Face's Zephyr 7B model to generate context-aware replies. Your data is processed locally and via Hugging Face's API.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

