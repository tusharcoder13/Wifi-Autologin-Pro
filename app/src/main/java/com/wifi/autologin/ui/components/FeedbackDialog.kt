package com.wifi.autologin.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.wifi.autologin.data.model.FeedbackPayload
import com.wifi.autologin.ui.theme.*

@Composable
fun FeedbackDialog(
    onDismiss: () -> Unit,
    onSubmit: (FeedbackPayload) -> Unit
) {
    val scrollState = rememberScrollState()
    val reactionScroll = rememberScrollState()
    val categoryScroll = rememberScrollState()

    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var selectedReaction by remember { mutableStateOf("😍 Awesome") }
    var selectedCategory by remember { mutableStateOf("⚡ Speed") }
    var message by remember { mutableStateOf("") }
    var isSubmitted by remember { mutableStateOf(false) }
    var nameTouched by remember { mutableStateOf(false) }
    var contactTouched by remember { mutableStateOf(false) }
    var messageTouched by remember { mutableStateOf(false) }

    val reactions = listOf(
        "😍 Awesome",
        "😊 Good",
        "😐 Okay",
        "😕 Needs Work",
        "😞 Not Working"
    )

    val categories = listOf(
        "⚡ Speed",
        "💡 Suggestion",
        "🐛 Bug Report",
        "❤️ Compliment"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            if (isSubmitted) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(GreenSuccess.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = GreenSuccess,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = "Thank You!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )

                    Text(
                        text = "Your feedback has been received. Thanks for helping make WiFi AutoLogin Pro better for everyone! ❤️",
                        fontSize = 13.sp,
                        color = TextSecondaryDark,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(TealDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = TealLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Share Your Experience",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "Feedback goes directly to Tushar",
                                fontSize = 11.sp,
                                color = TealLight,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f))

                    // Reaction Selector
                    Text(
                        text = "HOW IS YOUR EXPERIENCE?",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondaryDark
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(reactionScroll),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        reactions.forEach { reaction ->
                            val isSelected = selectedReaction == reaction
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedReaction = reaction },
                                color = if (isSelected) TealPrimary.copy(alpha = 0.3f) else DarkBackground,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) TealPrimary else DarkSurfaceVariant
                                )
                            ) {
                                Text(
                                    text = reaction,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) TealLight else TextPrimaryDark,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    // Category Selector
                    Text(
                        text = "CATEGORY:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondaryDark
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(categoryScroll),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedCategory = cat },
                                color = if (isSelected) TealDark else DarkBackground,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) TealLight else TextSecondaryDark,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    // Helper note
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "* All sections are compulsory",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TealLight
                        )
                    }

                    val isNameValid = name.trim().isNotBlank()
                    val isContactValid = contact.trim().length == 10
                    val isMessageValid = message.trim().isNotBlank()
                    val isFormValid = isNameValid && isContactValid && isMessageValid

                    val isNameError = nameTouched && !isNameValid
                    val isContactError = contactTouched && !isContactValid
                    val isMessageError = messageTouched && !isMessageValid

                    // Name field (Compulsory)
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameTouched = true
                        },
                        label = { Text("Your Name *", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Rahul Sharma", fontSize = 12.sp, color = TextMutedDark) },
                        singleLine = true,
                        isError = isNameError,
                        supportingText = if (isNameError) {
                            { Text("Name is required", color = Color(0xFFFF6B6B), fontSize = 11.sp) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            unfocusedBorderColor = DarkSurfaceVariant,
                            errorBorderColor = Color(0xFFFF6B6B),
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        )
                    )

                    // Contact Number field (Compulsory 10 digits)
                    OutlinedTextField(
                        value = contact,
                        onValueChange = { input ->
                            // Only digits allowed, maximum 10 digits
                            contact = input.filter { it.isDigit() }.take(10)
                            contactTouched = true
                        },
                        label = { Text("Contact Number * (10 digits)", fontSize = 12.sp) },
                        placeholder = { Text("e.g. 9876543210", fontSize = 12.sp, color = TextMutedDark) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = isContactError,
                        supportingText = if (isContactError) {
                            {
                                val msg = if (contact.isEmpty()) "Contact number is required"
                                else "Must be exactly 10 digits (${contact.length}/10)"
                                Text(msg, color = Color(0xFFFF6B6B), fontSize = 11.sp)
                            }
                        } else if (contact.isNotEmpty()) {
                            { Text("${contact.length}/10 digits", color = if (isContactValid) TealLight else TextMutedDark, fontSize = 11.sp) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            unfocusedBorderColor = DarkSurfaceVariant,
                            errorBorderColor = Color(0xFFFF6B6B),
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        )
                    )

                    // Message field (Compulsory)
                    OutlinedTextField(
                        value = message,
                        onValueChange = {
                            message = it
                            messageTouched = true
                        },
                        label = { Text("Your Feedback or Suggestion *", fontSize = 12.sp) },
                        placeholder = { Text("Tell us what you like or what we should fix...", fontSize = 12.sp, color = TextMutedDark) },
                        minLines = 3,
                        maxLines = 5,
                        isError = isMessageError,
                        supportingText = if (isMessageError) {
                            { Text("Feedback message is required", color = Color(0xFFFF6B6B), fontSize = 11.sp) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            unfocusedBorderColor = DarkSurfaceVariant,
                            errorBorderColor = Color(0xFFFF6B6B),
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        )
                    )

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondaryDark)
                        ) {
                            Text("Cancel", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                nameTouched = true
                                contactTouched = true
                                messageTouched = true
                                if (!isFormValid) return@Button

                                val payload = FeedbackPayload(
                                    name = name.trim(),
                                    contact = contact.trim(),
                                    reaction = selectedReaction,
                                    category = selectedCategory,
                                    message = message.trim(),
                                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                                    androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                                    appVersion = "3.0.0"
                                )
                                onSubmit(payload)
                                isSubmitted = true
                            },
                            enabled = isFormValid,
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TealPrimary,
                                disabledContainerColor = DarkSurfaceVariant.copy(alpha = 0.5f),
                                disabledContentColor = TextMutedDark
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (isFormValid) Color.White else TextMutedDark
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Submit",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFormValid) Color.White else TextMutedDark
                            )
                        }
                    }
                }
            }
        }
    }
}
