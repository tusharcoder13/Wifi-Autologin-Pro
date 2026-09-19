package com.wifi.autologin.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifi.autologin.data.repository.AppSettings
import com.wifi.autologin.ui.theme.*

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    isCheckingUpdates: Boolean = false,
    updateCheckMessage: String? = null,
    onCheckForUpdates: () -> Unit = {},
    onClearUpdateMessage: () -> Unit = {},
    onOpenFeedback: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var probeUrl by remember { mutableStateOf(settings.customProbeUrl) }
    var interval by remember { mutableStateOf(settings.keepAliveIntervalMinutes.toFloat()) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showTermsOfService by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header
        Column {
            Text(
                text = "Daemon Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark
            )
            Text(
                text = "Configure auto-login behavior, intervals, and battery rules",
                fontSize = 12.sp,
                color = TextSecondaryDark
            )
        }

        // Section: Automation
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "AUTOMATION & DETECTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealLight
                )

                SettingSwitchRow(
                    title = "Master Auto-Login",
                    subtitle = "Automatically submit credentials upon connecting to Wi-Fi",
                    checked = settings.isMasterAutoLoginEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(isMasterAutoLoginEnabled = it)) }
                )

                SettingSwitchRow(
                    title = "Success Notifications",
                    subtitle = "Show temporary 6-second alert when login succeeds",
                    checked = settings.showNotifications,
                    onCheckedChange = { onUpdateSettings(settings.copy(showNotifications = it)) }
                )

                SettingSwitchRow(
                    title = "Bypass SSL / Certificate Errors",
                    subtitle = "Required for local campus/hotel portals with self-signed SSL",
                    checked = settings.bypassSslErrors,
                    onCheckedChange = { onUpdateSettings(settings.copy(bypassSslErrors = it)) }
                )

                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f))

                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                putExtra(Settings.EXTRA_CHANNEL_ID, com.wifi.autologin.service.WifiMonitorService.CHANNEL_SILENT_DAEMON)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(fallback)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                ) {
                    Icon(imageVector = Icons.Default.NotificationsOff, contentDescription = null, tint = RedError, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🚫 Hide 'Monitoring in background' Notification", color = TextPrimaryDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Section: Keep-Alive Heartbeat
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "SESSION KEEP-ALIVE (HEARTBEAT)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealLight
                )

                SettingSwitchRow(
                    title = "Keep-Alive Daemon",
                    subtitle = "Send periodic pings to keep your session logged in",
                    checked = settings.isKeepAliveEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(isKeepAliveEnabled = it)) }
                )

                if (settings.isKeepAliveEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Heartbeat Interval", fontSize = 13.sp, color = TextPrimaryDark)
                            Text("${interval.toInt()} minutes", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealLight)
                        }
                        Slider(
                            value = interval,
                            onValueChange = { interval = it },
                            onValueChangeFinished = {
                                onUpdateSettings(settings.copy(keepAliveIntervalMinutes = interval.toInt()))
                            },
                            valueRange = 1f..15f,
                            steps = 13,
                            colors = SliderDefaults.colors(
                                thumbColor = TealPrimary,
                                activeTrackColor = TealPrimary,
                                inactiveTrackColor = DarkSurfaceVariant
                            )
                        )
                    }
                }
            }
        }

        // Section: Battery Optimization
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
                Text(
                    text = "BACKGROUND RELIABILITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealLight
                )

                Text(
                    text = "To ensure Android does not kill the background Wi-Fi detector when your screen is locked, disable battery optimization for this app.",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )

                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(fallback)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                ) {
                    Icon(imageVector = Icons.Default.BatteryChargingFull, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disable Battery Optimization", color = TextPrimaryDark, fontSize = 13.sp)
                }
            }
        }

        // Section: System Autofill Provider (Google Password Manager style)
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
                Text(
                    text = "GOOGLE-STYLE MANUAL AUTOFILL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealLight
                )

                Text(
                    text = "Enable WiFi AutoLogin Pro as an Android Autofill Service so when you tap on login fields in any browser or popup, a 1-tap autofill popup appears above the keyboard.",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )

                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val fallback = Intent(Settings.ACTION_SETTINGS)
                                context.startActivity(fallback)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                ) {
                    Icon(imageVector = Icons.Default.Password, contentDescription = null, tint = TealLight, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enable Android Autofill Provider", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Section: Software Updates & Version Checker
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
                        text = "APP UPDATES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealLight
                    )
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Installed: v1.0.0",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealLight,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "WiFi AutoLogin Pro automatically checks for updates online. You can also manually check for the latest releases.",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )

                if (updateCheckMessage != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = DarkBackground
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "✅ $updateCheckMessage",
                                fontSize = 11.sp,
                                color = GreenSuccess,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = onClearUpdateMessage, modifier = Modifier.size(16.dp)) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = TextMutedDark)
                            }
                        }
                    }
                }

                Button(
                    onClick = onCheckForUpdates,
                    enabled = !isCheckingUpdates,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                ) {
                    if (isCheckingUpdates) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TealLight, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking for updates...", color = TextSecondaryDark, fontSize = 12.sp)
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = TealLight, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check for Updates Now", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Section: Share Feedback & Suggestions
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "USER FEEDBACK & REACTION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealLight
                        )
                    }
                    Surface(
                        color = TealDark.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Direct to Dev",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealLight,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "Help make WiFi AutoLogin Pro better! Rate your experience, report an issue, or drop a quick suggestion.",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )

                Button(
                    onClick = onOpenFeedback,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Share Feedback & Reaction",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Legal & Privacy Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = TealLight,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Legal & Privacy",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showPrivacyPolicy = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TealLight),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TealDark.copy(alpha = 0.5f))
                    ) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Privacy Policy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { showTermsOfService = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TealLight),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TealDark.copy(alpha = 0.5f))
                    ) {
                        Icon(imageVector = Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Terms of Use", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (showPrivacyPolicy) {
            com.wifi.autologin.ui.components.PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
        }

        if (showTermsOfService) {
            com.wifi.autologin.ui.components.TermsOfServiceDialog(onDismiss = { showTermsOfService = false })
        }

        // App Information & Developer Signature
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("WiFi AutoLogin Pro v1.0.0", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMutedDark)
            Text("Built for Campus, Hostel, Hotel & Office Captive Portals", fontSize = 11.sp, color = TextMutedDark)

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/in/tusharsahu13"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Ignore
                    }
                },
                color = DarkSurfaceVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(100.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TealDark.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "⚡ Engineered by Tushar Sahu",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TealLight
                    )
                    Text(
                        text = "•",
                        fontSize = 12.sp,
                        color = TextMutedDark
                    )
                    Text(
                        text = "LinkedIn ↗",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0A66C2)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
            Text(text = subtitle, fontSize = 11.sp, color = TextSecondaryDark)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TealPrimary,
                uncheckedThumbColor = TextSecondaryDark,
                uncheckedTrackColor = DarkSurfaceVariant
            )
        )
    }
}
