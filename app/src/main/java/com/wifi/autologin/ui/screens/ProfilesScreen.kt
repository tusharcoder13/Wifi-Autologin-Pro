package com.wifi.autologin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifi.autologin.data.model.WifiProfile
import com.wifi.autologin.ui.theme.*

@Composable
fun ProfilesScreen(
    profiles: List<WifiProfile>,
    currentSsid: String,
    onSaveProfile: (WifiProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onLoginProfile: (WifiProfile) -> Unit,
    onSetPrimary: (String) -> Unit = {}
) {
    var editingProfile by remember { mutableStateOf<WifiProfile?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Saved Wi-Fi Profiles",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "${profiles.size} accounts/networks configured",
                        fontSize = 12.sp,
                        color = TextSecondaryDark
                    )
                }

                FilledTonalButton(
                    onClick = { isCreatingNew = true },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = TealDark, contentColor = TealLight)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Profile", fontSize = 13.sp)
                }
            }

            if (profiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = TextMutedDark,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No saved profiles yet.\nTap 'Add Profile' to configure an account.",
                            color = TextSecondaryDark,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                val sortedProfiles = remember(profiles) {
                    profiles.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.username } })
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    if (sortedProfiles.size == 1) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.6f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TealDark.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = TealLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "💡 Auto-Failover Tip",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TealLight
                                        )
                                        Text(
                                            text = "Add a secondary or friend's Wi-Fi ID as a Backup Profile. If your primary account reaches its device limit, the app will seamlessly log in using your backup account!",
                                            fontSize = 11.sp,
                                            color = TextSecondaryDark,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(
                        count = sortedProfiles.size,
                        key = { index -> "${sortedProfiles[index].id}_$index" }
                    ) { index ->
                        val profile = sortedProfiles[index]
                        val isCurrent = currentSsid.isNotBlank() && (
                            profile.ssid.equals(currentSsid, ignoreCase = true) ||
                            (profile.ssid.startsWith("KU") && currentSsid.startsWith("KU"))
                        )

                        ProfileCard(
                            profile = profile,
                            isCurrentWifi = isCurrent,
                            onEdit = { editingProfile = profile },
                            onDelete = { onDeleteProfile(profile.id) },
                            onLogin = { onLoginProfile(profile) },
                            onSetPrimary = { onSetPrimary(profile.id) },
                            onToggleAuto = { enabled ->
                                onSaveProfile(profile.copy(isAutoLoginEnabled = enabled))
                            }
                        )
                    }
                }
            }
        }

        // Add / Edit Dialog
        if (isCreatingNew || editingProfile != null) {
            EditProfileDialog(
                profile = editingProfile,
                currentConnectedSsid = currentSsid,
                onDismiss = {
                    isCreatingNew = false
                    editingProfile = null
                },
                onSave = { updated ->
                    onSaveProfile(updated)
                    isCreatingNew = false
                    editingProfile = null
                }
            )
        }
    }
}

@Composable
private fun ProfileCard(
    profile: WifiProfile,
    isCurrentWifi: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLogin: () -> Unit,
    onSetPrimary: () -> Unit,
    onToggleAuto: (Boolean) -> Unit
) {
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
                            .background(if (isCurrentWifi) TealPrimary else DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (isCurrentWifi) Color.White else TextSecondaryDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = profile.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            if (profile.isPrimary) {
                                Surface(
                                    color = TealPrimary.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(100.dp)
                                ) {
                                    Text(
                                        text = "👑 PRIMARY",
                                        color = TealLight,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    color = DarkBackground,
                                    shape = RoundedCornerShape(100.dp)
                                ) {
                                    Text(
                                        text = "🔄 BACKUP",
                                        color = TextSecondaryDark,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "SSID: ${profile.ssid} • ID: ${profile.username}",
                            fontSize = 12.sp,
                            color = TextSecondaryDark
                        )
                    }
                }

                Switch(
                    checked = profile.isAutoLoginEnabled,
                    onCheckedChange = onToggleAuto,
                    colors = SwitchDefaults.colors(checkedTrackColor = TealPrimary)
                )
            }

            Divider(color = DarkSurfaceVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!profile.isPrimary) {
                    TextButton(
                        onClick = onSetPrimary,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = TealLight, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Make Primary", fontSize = 12.sp, color = TealLight, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Surface(
                        color = DarkBackground,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Preset: ${profile.presetType}",
                            color = TealLight,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onLogin) {
                        Icon(imageVector = Icons.Default.Login, contentDescription = "Log In", tint = TealLight)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondaryDark)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = RedError.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}
