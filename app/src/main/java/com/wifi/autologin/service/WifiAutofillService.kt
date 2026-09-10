package com.wifi.autologin.service

import android.app.assist.AssistStructure
import android.app.assist.AssistStructure.ViewNode
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.*
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.wifi.autologin.R
import com.wifi.autologin.data.repository.LogRepository
import com.wifi.autologin.data.repository.ProfileRepository
import com.wifi.autologin.network.CaptivePortalDetector

@RequiresApi(Build.VERSION_CODES.O)
class WifiAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure: AssistStructure = request.fillContexts.lastOrNull()?.structure ?: run {
            callback.onSuccess(null)
            return
        }

        val targetPkg = structure.activityComponent.packageName.lowercase()

        // 1. NEVER show in Android Settings, System UI, or non-browser/non-portal apps
        val isSystemSettingsOrApp = targetPkg.contains("setting") ||
                targetPkg.contains("systemui") ||
                targetPkg.contains("keyguard") ||
                targetPkg.contains("launcher") ||
                targetPkg.contains("system") ||
                targetPkg.contains("android.wifi")

        if (isSystemSettingsOrApp) {
            callback.onSuccess(null)
            return
        }

        val isCaptivePortal = targetPkg.contains("captiveportal")
        val isBrowser = targetPkg.contains("chrome") ||
                targetPkg.contains("browser") ||
                targetPkg.contains("firefox") ||
                targetPkg.contains("opera") ||
                targetPkg.contains("edge") ||
                targetPkg.contains("webview")

        if (!isCaptivePortal && !isBrowser) {
            callback.onSuccess(null)
            return
        }

        val profileRepo = ProfileRepository(applicationContext)
        val profiles = profileRepo.profiles.value

        if (profiles.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        // Find username and password fields in the current screen / webview / chrome
        val fieldFinder = FieldFinder()
        for (i in 0 until structure.windowNodeCount) {
            val rootNode = structure.getWindowNodeAt(i).rootViewNode
            fieldFinder.traverse(rootNode)
        }

        val usernameId = fieldFinder.usernameId
        val passwordId = fieldFinder.passwordId

        if (usernameId == null && passwordId == null) {
            callback.onSuccess(null)
            return
        }

        // 2. Strict Domain & Gateway IP check
        val isPublicDomain = fieldFinder.webDomains.any { domain ->
            domain.contains("google.") || domain.contains("youtube.") || domain.contains("facebook.") ||
                    domain.contains("instagram.") || domain.contains("twitter.") || domain.contains("x.com") ||
                    domain.contains("linkedin.") || domain.contains("github.") || domain.contains("amazon.") ||
                    domain.contains("flipkart.") || domain.contains("microsoft.") || domain.contains("apple.")
        }
        if (isPublicDomain) {
            callback.onSuccess(null)
            return
        }

        val detector = CaptivePortalDetector(applicationContext)
        val gatewayIp = detector.getGatewayIpAddress().lowercase()

        val hasMatchingDomain = fieldFinder.webDomains.any { domain ->
            (gatewayIp.isNotBlank() && domain.contains(gatewayIp)) ||
                    domain.contains("172.24.") ||
                    domain.contains("172.16.") ||
                    domain.contains("httpclient") ||
                    profiles.any { p ->
                        val portalHost = p.portalUrl.lowercase()
                            .removePrefix("http://")
                            .removePrefix("https://")
                            .split("/").firstOrNull()?.split(":")?.firstOrNull() ?: ""
                        (portalHost.isNotBlank() && domain.contains(portalHost)) ||
                                (p.ssid.isNotBlank() && domain.contains(p.ssid.lowercase().removePrefix("ku-").removePrefix("ku_")))
                    }
        }

        // If in a browser and domain does NOT match any saved portal or gateway, suppress!
        if (isBrowser && fieldFinder.webDomains.isNotEmpty() && !hasMatchingDomain) {
            callback.onSuccess(null)
            return
        }

        val responseBuilder = FillResponse.Builder()

        // Create autofill dataset entries for EACH saved account (Google Password Manager style)
        for ((index, profile) in profiles.withIndex()) {
            if (profile.username.isBlank()) continue

            val presentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                val label = if (profile.isPrimary) {
                    "👑 ${profile.username} (${profile.name})"
                } else {
                    "🔄 ${profile.username} (Account ${index + 1})"
                }
                setTextViewText(R.id.autofill_username, label)
                setTextViewText(R.id.autofill_subtext, "•••••••••••• • WiFi AutoLogin Pro")
            }

            val datasetBuilder = Dataset.Builder(presentation)

            if (usernameId != null) {
                datasetBuilder.setValue(usernameId, AutofillValue.forText(profile.username), presentation)
            }
            if (passwordId != null && profile.password.isNotBlank()) {
                datasetBuilder.setValue(passwordId, AutofillValue.forText(profile.password), presentation)
            }

            try {
                responseBuilder.addDataset(datasetBuilder.build())
            } catch (e: Exception) {
                // Ignore
            }
        }

        try {
            callback.onSuccess(responseBuilder.build())
        } catch (e: Exception) {
            callback.onSuccess(null)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private class FieldFinder {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        val webDomains = mutableSetOf<String>()

        fun traverse(node: ViewNode) {
            node.webDomain?.let { if (it.isNotBlank()) webDomains.add(it.lowercase()) }

            val hints = node.autofillHints?.toList() ?: emptyList()
            val idEntry = (node.idEntry ?: "").lowercase()
            val hint = (node.hint ?: "").lowercase()
            val className = node.className ?: ""

            // HTML Web Form attribute inspection (Chrome, Samsung Internet, WebViews)
            var isHtmlPassword = false
            var isHtmlUsername = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val htmlInfo = node.htmlInfo
                if (htmlInfo != null) {
                    val attributes = htmlInfo.attributes ?: emptyList()
                    for (attr in attributes) {
                        val name = attr.first?.lowercase() ?: ""
                        val value = attr.second?.lowercase() ?: ""

                        if ((name == "type" && value == "password") ||
                            (name == "name" && (value.contains("pass") || value.contains("pwd"))) ||
                            (name == "id" && (value.contains("pass") || value.contains("pwd")))) {
                            isHtmlPassword = true
                        }

                        if ((name == "type" && (value == "text" || value == "email")) ||
                            (name == "name" && (value.contains("user") || value.contains("uname") || value.contains("id") || value.contains("roll"))) ||
                            (name == "id" && (value.contains("user") || value.contains("uname") || value.contains("id") || value.contains("roll")))) {
                            isHtmlUsername = true
                        }
                    }
                }
            }

            val isPasswordField = isHtmlPassword ||
                    node.isPasswordInputType() ||
                    hints.contains(View.AUTOFILL_HINT_PASSWORD) ||
                    idEntry.contains("pass") || idEntry.contains("pwd") ||
                    hint.contains("pass") || hint.contains("pwd")

            val isUsernameField = isHtmlUsername ||
                    hints.contains(View.AUTOFILL_HINT_USERNAME) ||
                    hints.contains(View.AUTOFILL_HINT_EMAIL_ADDRESS) ||
                    idEntry.contains("user") || idEntry.contains("uname") || idEntry.contains("id") || idEntry.contains("roll") ||
                    hint.contains("user") || hint.contains("id") || hint.contains("roll")

            if (isPasswordField && passwordId == null) {
                passwordId = node.autofillId
            } else if (isUsernameField && usernameId == null && !isPasswordField) {
                usernameId = node.autofillId
            } else if (className.contains("EditText", ignoreCase = true) && usernameId == null && !isPasswordField) {
                usernameId = node.autofillId
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChildAt(i))
            }
        }

        private fun ViewNode.isPasswordInputType(): Boolean {
            val inputType = this.inputType
            return (inputType and 0x80) != 0 || (inputType and 0x10) != 0 || (inputType and 0x90) != 0
        }
    }
}
