package com.wifi.autologin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.wifi.autologin.data.model.WifiState
import com.wifi.autologin.ui.theme.*

@Composable
fun StatusBadge(state: WifiState) {
    val (bgColor, textColor, text, icon) = when (state) {
        WifiState.CONNECTED_ONLINE -> Quadruple(GreenSuccess.copy(alpha = 0.15f), GreenSuccess, "Online (Authenticated)", Icons.Default.CheckCircle)
        WifiState.CAPTIVE_PORTAL_DETECTED -> Quadruple(AmberWarning.copy(alpha = 0.15f), AmberWarning, "Captive Portal Required", Icons.Default.Lock)
        WifiState.CONNECTED_NO_INTERNET -> Quadruple(RedError.copy(alpha = 0.15f), RedError, "No Internet / Unreachable", Icons.Default.Warning)
        WifiState.CONNECTING -> Quadruple(BlueAccent.copy(alpha = 0.15f), BlueAccent, "Connecting...", Icons.Default.Sync)
        WifiState.DISCONNECTED -> Quadruple(DarkSurfaceVariant, TextSecondaryDark, "Wi-Fi Disconnected", Icons.Default.WifiOff)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(100.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

enum class ScreenTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    PROFILES("Profiles", Icons.Default.Wifi),
    INSPECTOR("Inspector", Icons.Default.Search),
    LOGS("Logs", Icons.Default.Terminal),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun AppBottomNav(
    selectedTab: ScreenTab,
    onTabSelected: (ScreenTab) -> Unit
) {
    NavigationBar(
        containerColor = DarkSurface,
        contentColor = TextPrimaryDark,
        tonalElevation = 8.dp
    ) {
        ScreenTab.values().forEach { tab ->
            val isSelected = tab == selectedTab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = tab.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TealPrimary,
                    selectedTextColor = TealLight,
                    indicatorColor = TealDark.copy(alpha = 0.4f),
                    unselectedIconColor = TextSecondaryDark,
                    unselectedTextColor = TextSecondaryDark
                )
            )
        }
    }
}
