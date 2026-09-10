package com.wifi.autologin.ui.components

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wifi.autologin.data.model.WifiProfile
import com.wifi.autologin.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivePortalDialog(
    portalUrl: String,
    profile: WifiProfile?,
    availableProfiles: List<WifiProfile> = emptyList(),
    onDismiss: () -> Unit
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("Loading Portal...") }
    var currentUrl by remember { mutableStateOf(portalUrl) }

    val effectiveUrl = if (portalUrl.isNotBlank()) portalUrl else (profile?.portalUrl ?: "")
    val profilesToDisplay = if (availableProfiles.isNotEmpty()) availableProfiles else listOfNotNull(profile)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Live Login Portal",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = effectiveUrl,
                                fontSize = 11.sp,
                                color = TealLight
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondaryDark)
                        }
                    },
                    actions = {
                        IconButton(onClick = { webViewInstance?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = TealLight)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
                )
            },
            bottomBar = {
                Surface(
                    color = DarkSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        profilesToDisplay.forEachIndexed { idx, p ->
                            Button(
                                onClick = {
                                    val user = p.username
                                    val pass = p.password
                                    val escapedUser = user.replace("'", "\\'")
                                    val escapedPass = pass.replace("'", "\\'")
                                    val js = """
                                        (function() {
                                            function fireAllEvents(el, val) {
                                                if (!el) return;
                                                el.focus();
                                                el.value = val;
                                                ['focus', 'keydown', 'keypress', 'input', 'keyup', 'change', 'blur'].forEach(function(ev) {
                                                    try {
                                                        var event = new Event(ev, { bubbles: true, cancelable: true });
                                                        el.dispatchEvent(event);
                                                    } catch(e) {}
                                                });
                                            }

                                            var u = document.querySelector('input[type="text"], input[name*="user"], input[name*="id"], input[name*="uname"]') || (document.forms.length > 0 ? document.forms[0].elements[0] : null);
                                            var p = document.querySelector('input[type="password"], input[name*="pass"], input[name*="pwd"]') || (document.forms.length > 0 ? document.forms[0].elements[1] : null);

                                            if (u) fireAllEvents(u, '$escapedUser');
                                            if (p) fireAllEvents(p, '$escapedPass');

                                            setTimeout(function() {
                                                var btn = document.querySelector('button[type="submit"], input[type="submit"], button, .btn, input[value*="Sign"], input[value*="sign"], input[value*="Login"], input[value*="login"]');
                                                if (btn) {
                                                    btn.click();
                                                }
                                                if (typeof window.submitform === 'function') window.submitform();
                                                else if (typeof window.doLogin === 'function') window.doLogin();
                                                else if (typeof window.login === 'function') window.login();
                                                else if (typeof window.check === 'function') window.check();
                                                else if (document.forms.length > 0) {
                                                    document.forms[0].submit();
                                                }
                                            }, 150);
                                        })();
                                    """.trimIndent()
                                    webViewInstance?.evaluateJavascript(js, null)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (p.isPrimary) TealPrimary else DarkSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (p.isPrimary) "👑 Fill ${p.username.ifBlank { "Account 1" }}" else "🔄 Fill ${p.username.ifBlank { "Account ${idx + 1}" }}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.White)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            }
                            webViewClient = object : WebViewClient() {
                                @SuppressLint("WebViewClientOnReceivedSslError")
                                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                    handler?.proceed()
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    currentUrl = url ?: ""
                                    pageTitle = view?.title ?: "Portal"
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    if (!title.isNullOrBlank()) pageTitle = title
                                }
                            }
                            loadUrl(effectiveUrl)
                            webViewInstance = this
                        }
                    },
                    onRelease = { webView ->
                        try {
                            webView.destroy()
                        } catch (e: Exception) {}
                    }
                )
            }
        }
    }
}
