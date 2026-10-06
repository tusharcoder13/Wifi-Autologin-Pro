package com.wifi.autologin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.wifi.autologin.data.model.SpeedTestResult
import com.wifi.autologin.ui.theme.*

@Composable
fun SpeedTestDialog(
    result: SpeedTestResult,
    onDismiss: () -> Unit,
    onRetest: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Dialog(onDismissRequest = {
        if (!result.isRunning) onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(TealDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = TealLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Wi-Fi Speed & Diagnostic",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "Lightweight Data-Saver Test (~1.5 MB)",
                                fontSize = 11.sp,
                                color = TextSecondaryDark
                            )
                        }
                    }

                    if (!result.isRunning) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondaryDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.6f))

                // Hero Speed Display
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(if (result.isRunning) TealDark.copy(alpha = 0.25f) else DarkBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (result.downloadSpeedMbps > 0.0) {
                                String.format("%.1f", result.downloadSpeedMbps)
                            } else if (result.isRunning) {
                                "..."
                            } else {
                                "0.0"
                            },
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (result.downloadSpeedMbps >= 15.0) TealLight else TextPrimaryDark
                        )
                        Text(
                            text = "Mbps",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimary
                        )
                        Text(
                            text = "Download",
                            fontSize = 10.sp,
                            color = TextMutedDark
                        )
                    }
                }

                // Step status & progress bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = result.currentStep,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (result.isRunning) TealLight else TextPrimaryDark,
                        textAlign = TextAlign.Center
                    )

                    if (result.isRunning) {
                        LinearProgressIndicator(
                            progress = { result.progressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = TealPrimary,
                            trackColor = DarkSurfaceVariant
                        )
                    }
                }

                // Rating Chip (when finished)
                AnimatedVisibility(visible = !result.isRunning && result.rating.isNotBlank()) {
                    Surface(
                        color = if (result.downloadSpeedMbps >= 15.0) GreenSuccess.copy(alpha = 0.15f)
                        else if (result.downloadSpeedMbps >= 5.0) TealDark.copy(alpha = 0.3f)
                        else AmberWarning.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = result.rating,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (result.downloadSpeedMbps >= 15.0) GreenSuccess
                                else if (result.downloadSpeedMbps >= 5.0) TealLight
                                else AmberWarning,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Error message (if any)
                if (!result.isRunning && result.errorMessage != null) {
                    Text(
                        text = result.errorMessage,
                        fontSize = 11.sp,
                        color = RedError,
                        textAlign = TextAlign.Center
                    )
                }

                // Metrics Grid (Router Ping, Internet Ping, Download)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricItem(
                        icon = Icons.Default.Router,
                        title = "Router Ping",
                        value = if (result.gatewayLatencyMs > 0) "${result.gatewayLatencyMs} ms" else "---",
                        color = TealLight
                    )
                    MetricItem(
                        icon = Icons.Default.Public,
                        title = "Internet Ping",
                        value = if (result.internetLatencyMs > 0) "${result.internetLatencyMs} ms" else "---",
                        color = if (result.internetLatencyMs in 1..80) GreenSuccess else AmberWarning
                    )
                    MetricItem(
                        icon = Icons.Default.Download,
                        title = "Speed",
                        value = if (result.downloadSpeedMbps > 0.0) "${String.format("%.1f", result.downloadSpeedMbps)} M" else "---",
                        color = TealLight
                    )
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (result.isRunning) {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(disabledContainerColor = DarkSurfaceVariant)
                        ) {
                            Text("Testing Wi-Fi Performance...", fontSize = 13.sp, color = TextMutedDark)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondaryDark)
                        ) {
                            Text("Close", fontSize = 13.sp)
                        }

                        Button(
                            onClick = onRetest,
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Again", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    color: Color
) {
    Surface(
        color = DarkBackground,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.width(96.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark
            )
            Text(
                text = title,
                fontSize = 10.sp,
                color = TextSecondaryDark
            )
        }
    }
}
