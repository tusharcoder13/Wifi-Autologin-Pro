package com.wifi.autologin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifi.autologin.data.model.ConnectionStatus
import com.wifi.autologin.data.model.LogEntry
import com.wifi.autologin.data.model.WifiState
import com.wifi.autologin.data.repository.AppSettings
import com.wifi.autologin.ui.components.HowItWorksDialog
import com.wifi.autologin.ui.components.StatusBadge
import com.wifi.autologin.ui.theme.*

@Composable
fun DashboardScreen(
    status: ConnectionStatus,
    settings: AppSettings,
    recentLogs: List<LogEntry>,
    isLoggingIn: Boolean,
    onLoginClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onToggleAutoLogin: (Boolean) -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onSwitchProfile: (String) -> Unit = {},
    onFeedbackClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var showGuideDialog by remember { mutableStateOf(false) }

    if (showGuideDialog) {
        HowItWorksDialog(
            onDismiss = { showGuideDialog = false },
            onNavigateToSettings = onNavigateToSettings
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TealDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = TealLight,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "WiFi AutoLogin Pro",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        maxLines = 1
                    )
                    Text(
                        text = "Captive Portal Bypass Daemon",
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // In-App Feedback Button
                IconButton(
                    onClick = onFeedbackClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Share Feedback",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Help Guide Button
                IconButton(
                    onClick = { showGuideDialog = true },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "How It Works",
                        tint = TealLight,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Refresh Status Button
                IconButton(
                    onClick = onRefreshClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Status",
                        tint = TealLight,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Multi-Account Switcher Card (Shows when 2 or more profiles exist for current Wi-Fi)
        androidx.compose.animation.AnimatedVisibility(
            visible = status.matchingProfiles.size > 1,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE ACCOUNT (${status.matchingProfiles.size} Available)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealLight
                        )
                        Text(
                            text = "1-Tap Switch",
                            fontSize = 11.sp,
                            color = TextMutedDark
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        status.matchingProfiles.forEachIndexed { idx, p ->
                            val isCurrent = p.isPrimary || (status.matchedProfile?.id == p.id)
                            val chipBg by androidx.compose.animation.animateColorAsState(
                                targetValue = if (isCurrent) TealDark.copy(alpha = 0.5f) else DarkBackground,
                                animationSpec = androidx.compose.animation.core.tween(200),
                                label = "ChipBg"
                            )
                            val chipBorder = if (isCurrent) androidx.compose.foundation.BorderStroke(1.5.dp, TealPrimary) else null

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSwitchProfile(p.id) },
                                shape = RoundedCornerShape(10.dp),
                                color = chipBg,
                                border = chipBorder
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (isCurrent) "👑 Account ${idx + 1}" else "🔄 Account ${idx + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) TealLight else TextSecondaryDark
                                    )
                                    Text(
                                        text = p.username.ifBlank { p.name },
                                        fontSize = 11.sp,
                                        color = TextPrimaryDark,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live Wi-Fi Connection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CONNECTED WI-FI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealLight,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (status.ssid.isNotBlank()) {
                                status.ssid
                            } else if (status.ipAddress.isNotBlank()) {
                                "Connected Wi-Fi (${status.ipAddress})"
                            } else {
                                "No Wi-Fi Connected"
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                    }
                    StatusBadge(state = status.state)
                }

                Divider(color = DarkSurfaceVariant.copy(alpha = 0.6f))

                // Network Details Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DetailItem(
                        title = "IP Address",
                        value = if (status.ipAddress.isNotBlank()) status.ipAddress else "0.0.0.0"
                    )
                    DetailItem(
                        title = "Gateway",
                        value = if (status.gatewayIp.isNotBlank()) status.gatewayIp else "---"
                    )
                    DetailItem(
                        title = "Latency",
                        value = if (status.pingLatencyMs > 0) "${status.pingLatencyMs} ms" else "---"
                    )
                }

                // Matched Profile Info
                if (status.matchedProfile != null) {
                    Surface(
                        color = TealDark.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = TealLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Matched Profile: ${status.matchedProfile.name} (${status.matchedProfile.username})",
                                fontSize = 12.sp,
                                color = TextPrimaryDark,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else if (status.state != WifiState.DISCONNECTED && status.ssid != "Wi-Fi Disconnected") {
                    Surface(
                        color = AmberWarning.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = AmberWarning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (status.ssid.isNotBlank() && status.ssid != "<unknown ssid>" && status.ssid != "Campus Wi-Fi") {
                                        "No profile for '${status.ssid}'"
                                    } else {
                                        "No Wi-Fi profile created yet"
                                    },
                                    fontSize = 12.sp,
                                    color = AmberWarning
                                )
                            }
                            TextButton(onClick = onNavigateToProfiles) {
                                Text("Add Profile", color = TealLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Auto-Login Master Control Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (settings.isMasterAutoLoginEnabled) TealDark else DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (settings.isMasterAutoLoginEnabled) TealLight else TextSecondaryDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Automatic Background Login",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = if (settings.isMasterAutoLoginEnabled) "Auto-detects & submits login form" else "Disabled (Manual only)",
                            fontSize = 11.sp,
                            color = TextSecondaryDark
                        )
                    }
                }

                Switch(
                    checked = settings.isMasterAutoLoginEnabled,
                    onCheckedChange = onToggleAutoLogin,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = TealPrimary,
                        uncheckedThumbColor = TextSecondaryDark,
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }
        }

        // Detailed Authentication Failure Alert Banner
        if (status.lastLoginMessage.isNotBlank() && status.state != WifiState.CONNECTED_ONLINE) {
            val isError = status.lastLoginMessage.contains("Failed", ignoreCase = true) ||
                    status.lastLoginMessage.contains("wrong", ignoreCase = true) ||
                    status.lastLoginMessage.contains("incorrect", ignoreCase = true) ||
                    status.lastLoginMessage.contains("limit", ignoreCase = true)

            if (isError) {
                Surface(
                    color = RedError.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RedError.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = RedError,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = status.lastLoginMessage,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = RedError
                            )
                        }
                        TextButton(
                            onClick = onNavigateToProfiles,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Edit Profile", color = TealLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Main Action Button (Log In Now)
        Button(
            onClick = onLoginClick,
            enabled = !isLoggingIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TealPrimary,
                disabledContainerColor = TealDark.copy(alpha = 0.5f)
            )
        ) {
            if (isLoggingIn) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Authenticating...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Login,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AUTHENTICATE / LOG IN NOW",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Keep-Alive & Session Guard Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = if (settings.isKeepAliveEnabled) RedError else TextSecondaryDark,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Session Keep-Alive Heartbeat",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = if (settings.isKeepAliveEnabled) "Sending heartbeat ping every ${settings.keepAliveIntervalMinutes}m to prevent disconnects" else "Disabled in Settings",
                        fontSize = 11.sp,
                        color = TextSecondaryDark
                    )
                }
            }
        }

        // Recent Activity Preview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Activity",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    TextButton(onClick = onNavigateToLogs) {
                        Text("View All", fontSize = 12.sp, color = TealLight)
                    }
                }

                if (recentLogs.isEmpty()) {
                    Text(
                        text = "No recent events recorded.",
                        fontSize = 12.sp,
                        color = TextSecondaryDark
                    )
                } else {
                    recentLogs.take(3).forEach { log ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = log.formattedTime(),
                                fontSize = 11.sp,
                                color = TextMutedDark
                            )
                            Text(
                                text = "•",
                                color = when (log.level) {
                                    com.wifi.autologin.data.model.LogLevel.SUCCESS -> GreenSuccess
                                    com.wifi.autologin.data.model.LogLevel.ERROR -> RedError
                                    com.wifi.autologin.data.model.LogLevel.WARNING -> AmberWarning
                                    else -> TealLight
                                },
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = log.title + ": " + log.message,
                                fontSize = 12.sp,
                                color = TextSecondaryDark,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DetailItem(title: String, value: String) {
    Column {
        Text(text = title, fontSize = 11.sp, color = TextSecondaryDark)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
    }
}
