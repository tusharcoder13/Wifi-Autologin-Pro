package com.wifi.autologin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.wifi.autologin.data.model.PortalPreset
import com.wifi.autologin.data.model.WifiProfile
import com.wifi.autologin.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    profile: WifiProfile?,
    currentConnectedSsid: String = "",
    onDismiss: () -> Unit,
    onSave: (WifiProfile) -> Unit
) {
    var name by remember(profile?.id) { mutableStateOf(profile?.name ?: if (currentConnectedSsid.isNotBlank()) currentConnectedSsid else "My Wi-Fi") }
    var ssid by remember(profile?.id) { mutableStateOf(profile?.ssid ?: if (currentConnectedSsid.isNotBlank()) currentConnectedSsid else "") }
    var portalUrl by remember(profile?.id) { mutableStateOf(profile?.portalUrl ?: "") }
    var username by remember(profile?.id) { mutableStateOf(profile?.username ?: "") }
    var password by remember(profile?.id) { mutableStateOf(profile?.password ?: "") }
    var selectedPreset by remember(profile?.id) { mutableStateOf(profile?.presetType ?: PortalPreset.GENERIC.name) }
    var usernameField by remember(profile?.id) { mutableStateOf(profile?.usernameField ?: "username") }
    var passwordField by remember(profile?.id) { mutableStateOf(profile?.passwordField ?: "password") }
    var isAutoLoginEnabled by remember(profile?.id) { mutableStateOf(profile?.isAutoLoginEnabled ?: true) }
    var isKeepAliveEnabled by remember(profile?.id) { mutableStateOf(profile?.isKeepAliveEnabled ?: true) }
    var isPrimary by remember(profile?.id) { mutableStateOf(profile?.isPrimary ?: false) }
    var isPasswordVisible by remember(profile?.id) { mutableStateOf(false) }
    var showAdvanced by remember(profile?.id) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (profile == null) "New Wi-Fi Profile" else "Edit Wi-Fi Profile",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondaryDark)
                    }
                }

                // Preset Selector
                Text(
                    text = "PORTAL PRESET TEMPLATE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealLight
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PortalPreset.values().forEach { preset ->
                        val isSelected = preset.name == selectedPreset
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPreset = preset.name
                                    usernameField = preset.defaultUsernameField
                                    passwordField = preset.defaultPasswordField
                                    if (portalUrl.isBlank() && preset.defaultUrlPattern.isNotBlank()) {
                                        portalUrl = preset.defaultUrlPattern
                                    }
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) TealDark.copy(alpha = 0.4f) else DarkBackground,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, TealPrimary) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = TealPrimary)
                                )
                                Column {
                                    Text(
                                        text = preset.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimaryDark
                                    )
                                    Text(
                                        text = preset.description,
                                        fontSize = 11.sp,
                                        color = TextSecondaryDark
                                    )
                                }
                            }
                        }
                    }
                }

                // Profile Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name (e.g. Hostel Wi-Fi)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealPrimary,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    )
                )

                // Wi-Fi SSID
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("Wi-Fi SSID (Network Name)") },
                    trailingIcon = {
                        if (currentConnectedSsid.isNotBlank() && ssid != currentConnectedSsid) {
                            TextButton(onClick = { ssid = currentConnectedSsid }) {
                                Text("Current", fontSize = 11.sp, color = TealLight)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealPrimary,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    )
                )

                // Wi-Fi ID / Username
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Wi-Fi ID / Roll No / Username") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = TealLight)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealPrimary,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    )
                )

                // Wi-Fi Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Wi-Fi Password") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = TealLight)
                    },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Password Visibility",
                                tint = TextSecondaryDark
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealPrimary,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    )
                )

                // Portal URL (Optional)
                OutlinedTextField(
                    value = portalUrl,
                    onValueChange = { portalUrl = it },
                    label = { Text("Portal URL (Leave blank to auto-detect)") },
                    placeholder = { Text("e.g. http://192.168.1.1:1000/login", color = TextMutedDark) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealPrimary,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    )
                )

                // Advanced Settings Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Advanced Field Names",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TealLight
                    )
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TealLight
                    )
                }

                if (showAdvanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = usernameField,
                            onValueChange = { usernameField = it },
                            label = { Text("HTML Username Field Name") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealPrimary,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark
                            )
                        )
                        OutlinedTextField(
                            value = passwordField,
                            onValueChange = { passwordField = it },
                            label = { Text("HTML Password Field Name") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealPrimary,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark
                            )
                        )
                    }
                }

                // Primary Account, Auto-Login & Keep-Alive Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Set as Primary Account", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        Text("Used first during auto-login", fontSize = 11.sp, color = TextSecondaryDark)
                    }
                    Switch(
                        checked = isPrimary,
                        onCheckedChange = { isPrimary = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = TealPrimary)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-login when connected", fontSize = 13.sp, color = TextPrimaryDark)
                    Switch(
                        checked = isAutoLoginEnabled,
                        onCheckedChange = { isAutoLoginEnabled = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = TealPrimary)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Keep-Alive heartbeat", fontSize = 13.sp, color = TextPrimaryDark)
                    Switch(
                        checked = isKeepAliveEnabled,
                        onCheckedChange = { isKeepAliveEnabled = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = TealPrimary)
                    )
                }

                // Save / Cancel Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondaryDark)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val updated = (profile ?: WifiProfile(name = name, ssid = ssid)).copy(
                                name = name.ifBlank { ssid },
                                ssid = ssid,
                                portalUrl = portalUrl.trim(),
                                username = username.trim(),
                                password = password.trim(),
                                presetType = selectedPreset,
                                usernameField = usernameField.ifBlank { "username" },
                                passwordField = passwordField.ifBlank { "password" },
                                isAutoLoginEnabled = isAutoLoginEnabled,
                                isKeepAliveEnabled = isKeepAliveEnabled,
                                isPrimary = isPrimary
                            )
                            onSave(updated)
                        },
                        enabled = ssid.isNotBlank() && username.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                    ) {
                        Text("Save Profile")
                    }
                }
            }
        }
    }
}
