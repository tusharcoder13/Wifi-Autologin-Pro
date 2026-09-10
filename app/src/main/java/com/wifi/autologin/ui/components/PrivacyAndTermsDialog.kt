package com.wifi.autologin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wifi.autologin.ui.theme.*

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp),
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
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = TealLight,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Privacy Policy",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "Last Updated: September 2026",
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

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PolicySection(
                        title = "1. Summary & Core Principle",
                        content = "WiFi AutoLogin Pro is designed as a privacy-first utility. All Wi-Fi credentials (usernames, roll numbers, passwords) and profile configurations are stored 100% locally on your own physical device. Zero data is ever collected, transmitted, or stored on external cloud servers."
                    )

                    PolicySection(
                        title = "2. Local Data Storage & Security",
                        content = "Your credentials are kept in your device's private, sandboxed local storage (Android SharedPreferences). No third party, including the app developer or external servers, has access to your stored passwords. You can wipe all data at any time by deleting profiles or clearing app storage in Android Settings."
                    )

                    PolicySection(
                        title = "3. Permissions & Why We Need Them",
                        content = "• Location (GPS): Required exclusively by Android OS security policy to read the local broadcast name (SSID: e.g. KU-ROOM_31). The app NEVER tracks, logs, or transmits your GPS coordinates.\n\n• Notifications: Used strictly to provide local status alerts (e.g. 5-second connection confirmations and keep-alive status).\n\n• Accessibility Service (Optional): Used solely to detect captive portal login pages in mobile browsers and insert your saved credentials into username/password inputs. It strictly ignores all banking, payment (UPI/Navi/PhonePe), messaging, and public websites (Google Forms, social media)."
                    )

                    PolicySection(
                        title = "4. Network Communication",
                        content = "The app only communicates directly over your local Wi-Fi interface with the local network gateway router (e.g. Cyberoam / Sophos captive portal on 172.24.x.x / 172.16.x.x) and Google's standard connectivity check (generate_204) to verify internet status. No external telemetry, tracking SDKs, or third-party ads are integrated."
                    )

                    PolicySection(
                        title = "5. Changes & Inquiries",
                        content = "Any future updates to this policy will be reflected in the app. For inquiries, you can connect directly with the developer via the LinkedIn profile linked in the Settings tab."
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("I Understand", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TermsOfServiceDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp),
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
                        Icon(
                            imageVector = Icons.Default.Gavel,
                            contentDescription = null,
                            tint = TealLight,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Terms of Service",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "Last Updated: September 2026",
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

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PolicySection(
                        title = "1. Acceptance of Terms",
                        content = "By installing and using WiFi AutoLogin Pro, you agree to these Terms of Service. If you do not agree with any part of these terms, please discontinue using the application."
                    )

                    PolicySection(
                        title = "2. Purpose & Authorized Use",
                        content = "WiFi AutoLogin Pro is an independent automation tool developed to simplify connecting to authorized campus, hostel, hotel, and office captive portal networks. You agree to use the application only with legitimate Wi-Fi accounts that you are personally authorized to use by your university or network administrator."
                    )

                    PolicySection(
                        title = "3. User Responsibility & Compliance",
                        content = "You are solely responsible for ensuring your use of campus Wi-Fi complies with your institution's Acceptable IT Usage Policy. You must not use the application to bypass network administrative restrictions, gain unauthorized access, or disrupt network infrastructure."
                    )

                    PolicySection(
                        title = "4. Third-Party Disclaimers",
                        content = "WiFi AutoLogin Pro is an independent software project developed by Tushar Sahu. It is not officially endorsed by, affiliated with, or sponsored by Cyberoam, Sophos, Fortinet, Kalinga University, or any third-party network hardware provider."
                    )

                    PolicySection(
                        title = "5. Disclaimer of Warranties",
                        content = "The application is provided on an 'AS IS' and 'AS AVAILABLE' basis without warranties of any kind. The developer shall not be held liable for any network disconnections, quota exhaustion, account lockouts, or data interruptions resulting from network policies or app usage."
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("Accept Terms", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PolicySection(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = TealLight
        )
        Text(
            text = content,
            fontSize = 12.sp,
            color = TextSecondaryDark,
            lineHeight = 18.sp
        )
    }
}
