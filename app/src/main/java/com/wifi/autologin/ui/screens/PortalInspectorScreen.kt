package com.wifi.autologin.ui.screens

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifi.autologin.data.model.FormDetectionResult
import com.wifi.autologin.data.model.WifiProfile
import com.wifi.autologin.ui.theme.*

@Composable
fun PortalInspectorScreen(
    currentPortalUrl: String,
    inspectorResult: FormDetectionResult?,
    isInspecting: Boolean,
    onInspectUrl: (String) -> Unit,
    onSaveAsProfile: (WifiProfile) -> Unit
) {
    var inputUrl by remember { mutableStateOf(currentPortalUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Column {
            Text(
                text = "Captive Portal Inspector",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark
            )
            Text(
                text = "Analyze any Wi-Fi login page to auto-detect input field names and hidden tokens",
                fontSize = 12.sp,
                color = TextSecondaryDark
            )
        }

        // URL Input Card
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
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = { Text("Portal URL / Gateway Address") },
                    placeholder = { Text("http://192.168.1.1 or leave blank to auto-detect", color = TextMutedDark) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TealLight)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealPrimary,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    )
                )

                Button(
                    onClick = { onInspectUrl(inputUrl) },
                    enabled = !isInspecting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    if (isInspecting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Inspecting Portal...")
                    } else {
                        Icon(imageVector = Icons.Default.Troubleshoot, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AUTO-DETECT FORM FIELDS")
                    }
                }
            }
        }

        // Inspector Results
        if (inspectorResult != null) {
            if (inspectorResult.isSuccessful) {
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
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = GreenSuccess)
                            Text(
                                text = "Form Successfully Detected!",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenSuccess
                            )
                        }

                        DetailRow("Form Action URL", inspectorResult.actionUrl)
                        DetailRow("HTTP Method", inspectorResult.httpMethod)
                        DetailRow("Username Field", inspectorResult.detectedUsernameField)
                        DetailRow("Password Field", inspectorResult.detectedPasswordField)

                        if (inspectorResult.hiddenFields.isNotEmpty()) {
                            Text(
                                text = "Extracted Hidden Tokens:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TealLight
                            )
                            inspectorResult.hiddenFields.forEach { (k, v) ->
                                Surface(
                                    color = DarkBackground,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "$k: $v",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextSecondaryDark,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Divider(color = DarkSurfaceVariant)

                        Text(
                            text = "Quick-Save As Wi-Fi Profile:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )

                        OutlinedTextField(
                            value = ssid,
                            onValueChange = { ssid = it },
                            label = { Text("Wi-Fi SSID") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealPrimary,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark
                            )
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Wi-Fi ID / Username") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealPrimary,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark
                            )
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Wi-Fi Password") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealPrimary,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark
                            )
                        )

                        Button(
                            onClick = {
                                val effectiveSsid = if (ssid.isNotBlank()) ssid else "Wi-Fi"
                                val profile = WifiProfile(
                                    name = if (ssid.isNotBlank()) ssid else "Wi-Fi Portal",
                                    ssid = effectiveSsid,
                                    portalUrl = inspectorResult.actionUrl,
                                    username = username,
                                    password = password,
                                    presetType = "GENERIC",
                                    httpMethod = inspectorResult.httpMethod,
                                    usernameField = inspectorResult.detectedUsernameField,
                                    passwordField = inspectorResult.detectedPasswordField,
                                    extraFields = inspectorResult.hiddenFields
                                )
                                onSaveAsProfile(profile)
                            },
                            enabled = username.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                        ) {
                            Text("SAVE THIS PROFILE & AUTO-LOGIN")
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = RedError)
                            Text(
                                text = "Detection Failed",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = RedError
                            )
                        }
                        Text(
                            text = inspectorResult.errorMessage ?: "Unknown error occurred.",
                            fontSize = 12.sp,
                            color = TextSecondaryDark
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 11.sp, color = TextSecondaryDark)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimaryDark,
            fontFamily = FontFamily.Monospace
        )
    }
}
