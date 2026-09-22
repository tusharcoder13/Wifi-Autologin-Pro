package com.wifi.autologin.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.*
import com.wifi.autologin.data.model.WifiProfile
import com.wifi.autologin.data.model.WifiState
import com.wifi.autologin.data.repository.LogRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WebViewLoginEngine {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun executeHeadlessLogin(
        context: Context,
        profile: WifiProfile,
        detector: CaptivePortalDetector,
        targetUrl: String
    ): AuthResult = withContext(Dispatchers.Main) {
        val resultDeferred = CompletableDeferred<AuthResult>()
        var webView: WebView? = null

        try {
            webView = WebView(context)
            webView.layoutParams = ViewGroup.LayoutParams(1080, 1920)
            webView.layout(0, 0, 1080, 1920)
            webView.onResume()
            webView.resumeTimers()

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                cacheMode = WebSettings.LOAD_NO_CACHE
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = true
            }

            try {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(webView, true)
            } catch (e: Exception) {
                // Ignore
            }

            var formSubmitted = false

            webView.webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    LogRepository.info("Portal Alert", "Popup: $message")
                    result?.confirm()
                    return true
                }
            }

            webView.webViewClient = object : WebViewClient() {
                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.proceed() // Bypass self-signed SSL on portals
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val currentUrl = url ?: ""
                    LogRepository.info("WebView Engine", "Loaded portal URL: $currentUrl")

                    val escapedUser = profile.username.replace("'", "\\'")
                    val escapedPass = profile.password.replace("'", "\\'")

                    // Continuous Polling script tailored for Cyberoam & Sophos Captive Portals
                    val jsContinuousInjection = """
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

                            var attempts = 0;
                            var timer = setInterval(function() {
                                attempts++;
                                var u = document.getElementById('username') || document.querySelector('input[name="username"]') || document.querySelector('input[type="text"]') || (document.forms.length > 0 ? document.forms[0].elements[0] : null);
                                var p = document.getElementById('password') || document.querySelector('input[name="password"]') || document.querySelector('input[type="password"]') || (document.forms.length > 0 ? document.forms[0].elements[1] : null);

                                if (u && p) {
                                    clearInterval(timer);
                                    fireAllEvents(u, '$escapedUser');
                                    fireAllEvents(p, '$escapedPass');

                                    setTimeout(function() {
                                        if (typeof window.submitform === 'function') {
                                            window.submitform();
                                        } else if (document.frmHTTPClient && typeof document.frmHTTPClient.submit === 'function') {
                                            document.frmHTTPClient.submit();
                                        } else {
                                            var btn = document.querySelector('input[name="btnSubmit"], input[type="button"], button, input[type="submit"], input[value*="Sign"], input[value*="sign"], input[value*="Login"]');
                                            if (btn) btn.click();
                                            else if (document.forms.length > 0) document.forms[0].submit();
                                        }
                                    }, 200);
                                }

                                if (attempts >= 20) {
                                    clearInterval(timer);
                                }
                            }, 200);
                        })();
                    """.trimIndent()

                    view?.evaluateJavascript(jsContinuousInjection, null)

                    if (!formSubmitted) {
                        formSubmitted = true
                        // Start background probe verification
                        Thread {
                            try {
                                var isOnline = false
                                for (attempt in 1..5) {
                                    try {
                                        Thread.sleep(1400)
                                    } catch (e: Exception) {}
                                    val probe = detector.probeConnectivity()
                                    if (probe.state == WifiState.CONNECTED_ONLINE && probe.httpCode == 204) {
                                        isOnline = true
                                        LogRepository.success("Auto-Login Success!", "Internet unblocked hands-free on attempt #$attempt!")
                                        resultDeferred.complete(
                                            AuthResult(
                                                isSuccess = true,
                                                httpCode = 200,
                                                message = "Hands-free auto-login successful!",
                                                portalTargetUrl = currentUrl
                                            )
                                        )
                                        break
                                    } else {
                                        LogRepository.info("Handshake Probe", "Probe #$attempt: State ${probe.state}")
                                    }
                                }

                                if (!isOnline) {
                                    resultDeferred.complete(
                                        AuthResult(
                                            isSuccess = false,
                                            httpCode = 502,
                                            message = "Form submitted, awaiting gateway handshake.",
                                            portalTargetUrl = currentUrl
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                resultDeferred.complete(
                                    AuthResult(
                                        isSuccess = false,
                                        httpCode = 0,
                                        message = "Verification error: ${e.localizedMessage}",
                                        portalTargetUrl = currentUrl
                                    )
                                )
                            }
                        }.start()
                    }
                }
            }

            LogRepository.info("WebView Engine", "Executing headless login on $targetUrl...")
            webView.loadUrl(targetUrl)

            resultDeferred.await()

        } catch (e: Exception) {
            AuthResult(
                isSuccess = false,
                httpCode = 0,
                message = "Headless engine error: ${e.localizedMessage}",
                portalTargetUrl = targetUrl
            )
        } finally {
            try {
                webView?.stopLoading()
                webView?.destroy()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
