package com.whatsappautoreply.presentation

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.whatsappautoreply.presentation.analytics.AnalyticsScreen
import com.whatsappautoreply.presentation.chatdetail.ChatDetailScreen
import com.whatsappautoreply.presentation.chatlist.ChatListScreen
import com.whatsappautoreply.presentation.debug.LLMDebugScreen
import com.whatsappautoreply.presentation.settings.SettingsScreen
import com.whatsappautoreply.presentation.theme.WhatsAppAutoReplyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WhatsAppAutoReplyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WhatsAppAutoReplyApp()
                }
            }
        }
    }
}

@Composable
fun WhatsAppAutoReplyApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Check notification access permission
    var hasNotificationAccess by remember {
        mutableStateOf(
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )?.contains(context.packageName) == true
        )
    }

    if (!hasNotificationAccess) {
        NotificationPermissionScreen {
            hasNotificationAccess = true
        }
    } else {
        // Check battery optimization
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        var isIgnoringBatteryOptimizations by remember {
            mutableStateOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    powerManager.isIgnoringBatteryOptimizations(context.packageName)
                } else {
                    true
                }
            )
        }

        if (!isIgnoringBatteryOptimizations) {
            BatteryOptimizationScreen {
                isIgnoringBatteryOptimizations = true
            }
        } else {
            NavHost(
                navController = navController,
                startDestination = "chat_list"
            ) {
                composable("chat_list") {
                    ChatListScreen(
                        onChatClick = { chatId ->
                            navController.navigate("chat_detail/$chatId")
                        },
                        onSettingsClick = {
                            navController.navigate("settings")
                        },
                        onAnalyticsClick = {
                            navController.navigate("analytics")
                        }
                    )
                }
                composable("chat_detail/{chatId}") { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                    ChatDetailScreen(
                        chatId = chatId,
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        onBackClick = {
                            navController.popBackStack()
                        },
                        onDebugClick = {
                            navController.navigate("debug")
                        }
                    )
                }
                composable("analytics") {
                    AnalyticsScreen(
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }
                composable("debug") {
                    LLMDebugScreen(
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BatteryOptimizationScreen(onWhitelisted: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Battery Optimization",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "To ensure the auto-reply works 24/7 in the background, please disable battery optimization for this app.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Disable Optimization")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                        onWhitelisted()
                    }
                } else {
                    onWhitelisted()
                }
            }
        ) {
            Text("I've disabled it")
        }
    }
}

@Composable
fun NotificationPermissionScreen(onPermissionGranted: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Notification Access Required",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "This app needs notification access to monitor WhatsApp messages. Please grant the permission in settings.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Settings")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = {
                // Re-check permission
                val enabled = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                )?.contains(context.packageName) == true
                if (enabled) {
                    onPermissionGranted()
                }
            }
        ) {
            Text("I've granted the permission")
        }
    }
}

