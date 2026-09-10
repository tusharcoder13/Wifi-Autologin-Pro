package com.wifi.autologin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifi.autologin.data.model.LogEntry
import com.wifi.autologin.data.model.LogLevel
import com.wifi.autologin.ui.theme.*

@Composable
fun LogsScreen(
    logs: List<LogEntry>,
    onClearLogs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    text = "Live Activity & Network Logs",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
                Text(
                    text = "Real-time background authentication events",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )
            }

            IconButton(
                onClick = onClearLogs,
                colors = IconButtonDefaults.iconButtonColors(containerColor = DarkSurface)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear", tint = TextSecondaryDark)
            }
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = TextMutedDark,
                        modifier = Modifier.size(48.dp)
                    )
                    Text("No logs recorded yet.", color = TextSecondaryDark, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(
                    count = logs.size,
                    key = { index -> "${logs[index].id}_$index" }
                ) { index ->
                    LogCard(log = logs[index])
                }
            }
        }
    }
}

@Composable
private fun LogCard(log: LogEntry) {
    val (badgeBg, badgeColor, levelLabel) = when (log.level) {
        LogLevel.SUCCESS -> Triple(GreenSuccess.copy(alpha = 0.15f), GreenSuccess, "SUCCESS")
        LogLevel.ERROR -> Triple(RedError.copy(alpha = 0.15f), RedError, "ERROR")
        LogLevel.WARNING -> Triple(AmberWarning.copy(alpha = 0.15f), AmberWarning, "WARN")
        LogLevel.INFO -> Triple(TealPrimary.copy(alpha = 0.15f), TealLight, "INFO")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = levelLabel,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = log.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                }

                Text(
                    text = log.formattedTime(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMutedDark
                )
            }

            Text(
                text = log.message,
                fontSize = 12.sp,
                color = TextSecondaryDark
            )

            if (!log.rawPayload.isNullOrBlank()) {
                Surface(
                    color = DarkBackground,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = log.rawPayload,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMutedDark,
                        modifier = Modifier.padding(8.dp),
                        maxLines = 4
                    )
                }
            }
        }
    }
}
