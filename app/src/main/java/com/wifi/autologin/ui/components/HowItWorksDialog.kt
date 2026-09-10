package com.wifi.autologin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wifi.autologin.ui.theme.*

@Composable
fun HowItWorksDialog(
    onDismiss: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
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
                                .background(TealDark.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = TealLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "How It Works",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "Quick Guide for Campus Wi-Fi",
                                fontSize = 11.sp,
                                color = TextSecondaryDark
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondaryDark)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = DarkSurfaceVariant
                )

                // Scrollable Steps
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    GuideStepCard(
                        stepNumber = "1",
                        icon = Icons.Default.PersonAdd,
                        title = "Save Your Account Once",
                        description = "Go to the 'Profiles' tab and tap '+ Add Profile'. Enter your University Roll Number & Password. Your credentials stay 100% encrypted on your physical device."
                    )

                    GuideStepCard(
                        stepNumber = "2",
                        icon = Icons.Default.Bolt,
                        title = "100% Hands-Free Auto-Login",
                        description = "Whenever you join campus or hostel Wi-Fi, the app automatically detects the captive portal and authenticates in ~200ms in the background. You don't even need to open a browser!"
                    )

                    GuideStepCard(
                        stepNumber = "3",
                        icon = Icons.Default.Group,
                        title = "Smart Multi-Account Failover",
                        description = "Add your roommates' IDs as 'Backup Accounts'. If your primary ID runs out of daily data quota or reaches maximum devices, the app automatically switches to the backup ID so you stay online."
                    )

                    GuideStepCard(
                        stepNumber = "4",
                        icon = Icons.Default.AutoAwesome,
                        title = "How to Turn ON System Autofill",
                        description = "If you ever need manual 1-tap login inside Chrome or system captive portal popups:"
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            color = DarkBackground.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "1️⃣  Go to Settings tab in this app",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimaryDark
                                )

                                Text(
                                    text = "2️⃣  Tap 'Enable System Portal Autofill' → find WiFi AutoLogin Pro in Installed Apps → turn ON",
                                    fontSize = 11.sp,
                                    color = TextSecondaryDark,
                                    lineHeight = 15.sp
                                )

                                Text(
                                    text = "3️⃣  Tap 'Enable Android Autofill Provider' for Google Password Manager-style 1-tap autofill in Chrome",
                                    fontSize = 11.sp,
                                    color = TextSecondaryDark,
                                    lineHeight = 15.sp
                                )

                                // Android 13/14 Restricted Settings Tip
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurfaceVariant.copy(alpha = 0.7f))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(text = "💡", fontSize = 11.sp)
                                    Text(
                                        text = "If Android 13/14 says 'Restricted setting': Tap 'Open App Info' in Settings → 3 dots (⋮) top-right → 'Allow restricted settings'.",
                                        fontSize = 10.5.sp,
                                        color = TealLight,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }

                        if (onNavigateToSettings != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onNavigateToSettings()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TealLight),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TealDark)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Settings Tab ⚙️", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("Got It, Let's Go! 🚀", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun GuideStepCard(
    stepNumber: String,
    icon: ImageVector,
    title: String,
    description: String,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Surface(
        color = DarkBackground.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(TealDark.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealLight
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TealLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                }
                Text(
                    text = description,
                    fontSize = 11.5.sp,
                    color = TextSecondaryDark,
                    lineHeight = 17.sp
                )
                if (content != null) {
                    content()
                }
            }
        }
    }
}
