package com.wifi.autologin.network

import com.wifi.autologin.data.model.FormDetectionResult
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

object HtmlFormParser {

    /**
     * Parses the HTML of a captive portal login page and intelligently detects:
     * 1. Form submit action URL (resolving relative URLs against baseUrl)
     * 2. HTTP method (POST/GET)
     * 3. Username / ID field name
     * 4. Password field name
     * 5. Hidden fields (CSRF tokens, magic tokens, session IDs)
     */
    fun parseLoginForm(html: String, baseUrl: String): FormDetectionResult {
        if (html.isBlank()) {
            return FormDetectionResult(errorMessage = "Empty HTML received from portal.")
        }

        try {
            val doc: Document = Jsoup.parse(html, baseUrl)

            // 1. Find the login form (prefer forms containing a password input)
            var targetForm: Element? = doc.select("form:has(input[type=password])").first()
            if (targetForm == null) {
                targetForm = doc.select("form").first()
            }

            if (targetForm == null) {
                return FormDetectionResult(
                    isSuccessful = false,
                    rawHtml = html.take(2000),
                    errorMessage = "No <form> element found in the HTML page."
                )
            }

            // 2. Resolve Action URL
            val rawAction = targetForm.attr("action").trim()
            val actionUrl = if (rawAction.isBlank()) {
                baseUrl
            } else {
                try {
                    URI(baseUrl).resolve(rawAction).toString()
                } catch (e: Exception) {
                    if (rawAction.startsWith("http://") || rawAction.startsWith("https://")) {
                        rawAction
                    } else {
                        baseUrl
                    }
                }
            }

            // 3. HTTP Method
            val method = targetForm.attr("method").ifBlank { "POST" }.uppercase()

            // 4. Detect Password Field
            val passwordInput = targetForm.select("input[type=password]").first()
                ?: targetForm.select("input[name*='pass'], input[name*='pwd']").first()
            val passwordFieldName = passwordInput?.attr("name")?.ifBlank { "password" } ?: "password"

            // 5. Detect Username / ID Field
            val usernameKeywords = listOf("user", "login", "id", "uname", "email", "auth", "account", "roll", "member")
            var usernameInput: Element? = null

            val textInputs = targetForm.select("input[type=text], input[type=email], input[type=tel], input:not([type])")
            for (input in textInputs) {
                val name = input.attr("name").lowercase()
                val id = input.id().lowercase()
                if (usernameKeywords.any { name.contains(it) || id.contains(it) }) {
                    usernameInput = input
                    break
                }
            }
            if (usernameInput == null && textInputs.isNotEmpty()) {
                usernameInput = textInputs.first()
            }
            val usernameFieldName = usernameInput?.attr("name")?.ifBlank { "username" } ?: "username"

            // 6. Extract Hidden and Extra Inputs (e.g. CSRF tokens, session IDs, magic codes)
            val hiddenFields = mutableMapOf<String, String>()
            val hiddenInputs = targetForm.select("input[type=hidden]")
            for (hidden in hiddenInputs) {
                val name = hidden.attr("name")
                val value = hidden.attr("value")
                if (name.isNotBlank()) {
                    hiddenFields[name] = value
                }
            }

            return FormDetectionResult(
                isSuccessful = true,
                actionUrl = actionUrl,
                httpMethod = method,
                detectedUsernameField = usernameFieldName,
                detectedPasswordField = passwordFieldName,
                hiddenFields = hiddenFields,
                rawHtml = targetForm.outerHtml()
            )

        } catch (e: Exception) {
            return FormDetectionResult(
                isSuccessful = false,
                rawHtml = html.take(1000),
                errorMessage = "Failed to parse HTML: ${e.localizedMessage}"
            )
        }
    }
}
