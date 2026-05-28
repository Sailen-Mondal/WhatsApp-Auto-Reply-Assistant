package com.whatsappautoreply.presentation.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatsappautoreply.data.database.dao.DirectionStat
import com.whatsappautoreply.data.database.dao.ToneStat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBackClick: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val totalMessages by viewModel.totalMessages.collectAsState()
    val totalAutoReplies by viewModel.totalAutoReplies.collectAsState()
    val directionStats by viewModel.messageDirectionStats.collectAsState()
    val toneStats by viewModel.toneDistribution.collectAsState()

    val replyRate = if (totalMessages > 0)
        (totalAutoReplies.toFloat() / totalMessages * 100).toInt()
    else 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Analytics",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ─── KPI Row ─────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Forum,
                        label = "Total Messages",
                        value = totalMessages.toString(),
                        iconColor = MaterialTheme.colorScheme.primary
                    )
                    KpiCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.SmartToy,
                        label = "Auto-Replies",
                        value = totalAutoReplies.toString(),
                        iconColor = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AutoAwesome,
                        label = "Reply Rate",
                        value = "$replyRate%",
                        iconColor = MaterialTheme.colorScheme.tertiary
                    )
                    KpiCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.ThumbUp,
                        label = "Tones Used",
                        value = toneStats.size.toString(),
                        iconColor = MaterialTheme.colorScheme.error
                    )
                }
            }

            // ─── Reply Rate Bar ──────────────────────────────────────────
            if (totalMessages > 0) {
                item {
                    SectionHeader("Auto-Reply Rate")
                    ReplyRateBar(replyRate)
                }
            }

            // ─── Tone Distribution ───────────────────────────────────────
            if (toneStats.isNotEmpty()) {
                item { SectionHeader("Tone Distribution") }
                items(toneStats) { stat ->
                    AnimatedStatBar(
                        label = stat.tone.replaceFirstChar { it.uppercaseChar() },
                        value = stat.count,
                        total = totalAutoReplies,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ─── Message Direction Breakdown ─────────────────────────────
            if (directionStats.isNotEmpty()) {
                item { SectionHeader("Message Breakdown") }
                items(directionStats) { stat ->
                    val label = when (stat.direction.name) {
                        "INCOMING" -> "Received"
                        "OUTGOING" -> "Sent by you"
                        "BOT_OUTGOING" -> "Auto-replied"
                        else -> stat.direction.name
                    }
                    val color = when (stat.direction.name) {
                        "INCOMING" -> MaterialTheme.colorScheme.primary
                        "OUTGOING" -> MaterialTheme.colorScheme.secondary
                        "BOT_OUTGOING" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    AnimatedStatBar(
                        label = label,
                        value = stat.count,
                        total = totalMessages,
                        color = color
                    )
                }
            }

            // ─── Empty state ─────────────────────────────────────────────
            if (totalMessages == 0) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                            Text(
                                "No data yet.\nStart chatting to see analytics here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun KpiCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    iconColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReplyRateBar(replyRate: Int) {
    val animatedFraction by animateFloatAsState(
        targetValue = replyRate / 100f,
        animationSpec = tween(1000),
        label = "replyRate"
    )
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Auto-reply rate", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "$replyRate%",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = animatedFraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun AnimatedStatBar(
    label: String,
    value: Int,
    total: Int,
    color: Color
) {
    val fraction = if (total > 0) value.toFloat() / total else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(800),
        label = "stat_$label"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "$value  (${(fraction * 100).toInt()}%)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
            )
        }
    }
}
