package com.wifi.autologin.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.wifi.autologin.data.model.WifiProfile
import com.wifi.autologin.data.repository.LogRepository
import com.wifi.autologin.data.repository.ProfileRepository
import com.wifi.autologin.network.CaptivePortalDetector

class WifiAccessibilityAutofillService : AccessibilityService() {

    private var dropdownView: View? = null
    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isDropdownShowing = false
    private var lastDismissTimestamp = 0L
    private var lastShowTimestamp = 0L
    private var activeAnchorNode: AccessibilityNodeInfo? = null
    private lateinit var detector: CaptivePortalDetector
    private var profileRepo: ProfileRepository? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        detector = CaptivePortalDetector(applicationContext)
        profileRepo = ProfileRepository(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""
        val eventType = event.eventType

        // Ignore our own app, keyboard, input methods, and system UI / status bar
        if (packageName == "com.wifi.autologin" ||
            packageName == applicationContext.packageName ||
            packageName.contains("inputmethod", ignoreCase = true) ||
            packageName.contains("keyboard", ignoreCase = true) ||
            packageName.contains("systemui", ignoreCase = true)) {
            return
        }

        // Dismiss only if user goes back to the home launcher screen
        val isLauncher = packageName.contains("launcher", ignoreCase = true) ||
                packageName.contains("home", ignoreCase = true)

        if (isLauncher && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            removeDropdown()
            return
        }

        val isPaymentOrBankingApp = packageName.contains("navi", ignoreCase = true) ||
                packageName.contains("phonepe", ignoreCase = true) ||
                packageName.contains("paytm", ignoreCase = true) ||
                packageName.contains("gpay", ignoreCase = true) ||
                packageName.contains("paisa", ignoreCase = true) ||
                packageName.contains("bhim", ignoreCase = true) ||
                packageName.contains("cred", ignoreCase = true) ||
                packageName.contains("bank", ignoreCase = true) ||
                packageName.contains("upi", ignoreCase = true) ||
                packageName.contains("sbi", ignoreCase = true) ||
                packageName.contains("hdfc", ignoreCase = true) ||
                packageName.contains("icici", ignoreCase = true) ||
                packageName.contains("axis", ignoreCase = true) ||
                packageName.contains("kotak", ignoreCase = true)

        if (isPaymentOrBankingApp) {
            return
        }

        val isCaptivePortalApp = packageName.contains("captiveportal", ignoreCase = true)
        val isBrowser = packageName.contains("browser", ignoreCase = true) ||
                packageName.contains("chrome", ignoreCase = true) ||
                packageName.contains("firefox", ignoreCase = true) ||
                packageName.contains("opera", ignoreCase = true) ||
                packageName.contains("edge", ignoreCase = true) ||
                packageName.contains("webview", ignoreCase = true)

        if (!isCaptivePortalApp && !isBrowser) {
            return
        }

        // When dropdown is already open: Dismiss immediately if user clicks outside the input box
        if (isDropdownShowing && dropdownView != null) {
            if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val source = event.source
                val isInput = source != null && (source.isEditable || source.isPassword || (source.className?.toString() ?: "").contains("EditText", ignoreCase = true))
                if (!isInput) {
                    lastDismissTimestamp = System.currentTimeMillis()
                    removeDropdown()
                    return
                }
            }
            return
        }

        // Trigger ONLY on explicit user focus or tap (drop text selection to avoid typing lag)
        val isCandidateEvent = eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED

        if (!isCandidateEvent) return

        val now = System.currentTimeMillis()
        if (now - lastDismissTimestamp < 350) return

        val rootNode = rootInActiveWindow ?: return

        try {
            val repo = profileRepo ?: ProfileRepository(applicationContext).also { profileRepo = it }
            val activeProfiles = repo.profiles.value.filter { it.isAutoLoginEnabled && it.username.isNotBlank() }
                .sortedByDescending { it.isPrimary }

            if (activeProfiles.isEmpty()) return

            // URL & Domain matching: Only show on portal/captive screens
            if (!isCaptivePortalApp && !isCurrentScreenMatchingPortal(rootNode, activeProfiles)) {
                return
            }

            var anchorNode: AccessibilityNodeInfo? = null

            // 1. Try finding explicitly focused input in the active window
            val inputFocus = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (inputFocus != null && (inputFocus.isEditable || inputFocus.isPassword || (inputFocus.className?.toString() ?: "").contains("EditText", ignoreCase = true))) {
                anchorNode = inputFocus
            }

            // 2. Check event.source
            val source = event.source
            if (anchorNode == null && source != null && (source.isEditable || source.isPassword || (source.className?.toString() ?: "").contains("EditText", ignoreCase = true))) {
                anchorNode = source
            }

            // 3. Scan tree for focused or editable node
            if (anchorNode == null) {
                var firstEditable: AccessibilityNodeInfo? = null
                fun scan(node: AccessibilityNodeInfo?) {
                    if (node == null || anchorNode != null) return
                    val className = node.className?.toString() ?: ""
                    val isInput = node.isEditable || node.isPassword || className.contains("EditText", ignoreCase = true)
                    if (isInput) {
                        if (node.isFocused) {
                            anchorNode = node
                            return
                        }
                        if (firstEditable == null) {
                            firstEditable = node
                        }
                    }
                    for (i in 0 until node.childCount) {
                        scan(node.getChild(i))
                    }
                }
                scan(rootNode)
                if (anchorNode == null) {
                    anchorNode = firstEditable
                }
            }

            if (anchorNode != null) {
                activeAnchorNode = anchorNode
                val bounds = Rect()
                anchorNode.getBoundsInScreen(bounds)

                if (bounds.width() > 0 && bounds.height() > 0) {
                    showAnchoredDropdown(activeProfiles, bounds)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Checks whether the current screen in the browser matches the saved portal URL,
     * network gateway IP (e.g. 172.24.16.1, 172.24.8.1, 172.24.64.1), or captive portal endpoints.
     * Strictly ignores public internet websites (Google Forms, social media, shopping, etc.).
     */
    private fun isCurrentScreenMatchingPortal(
        rootNode: AccessibilityNodeInfo,
        profiles: List<WifiProfile>
    ): Boolean {
        val publicDomainsBlacklist = listOf(
            "google.", "docs.google.", "forms.google.", "forms.gle", "youtube.",
            "facebook.", "instagram.", "twitter.", "x.com", "linkedin.", "github.",
            "amazon.", "flipkart.", "netflix.", "spotify.", "reddit.", "wikipedia.",
            "apple.", "microsoft.", "yahoo.", "bing.", "medium.", "quora.",
            "stackoverflow.", "canva.", "notion.so", "whatsapp.", "telegram."
        )

        val specificPortalKeywords = mutableSetOf<String>()

        try {
            val gatewayIp = detector.getGatewayIpAddress()
            if (gatewayIp.isNotBlank() && gatewayIp != "0.0.0.0" && gatewayIp != "127.0.0.1") {
                specificPortalKeywords.add(gatewayIp.lowercase())
                val parts = gatewayIp.split(".")
                if (parts.size >= 2) {
                    specificPortalKeywords.add("${parts[0]}.${parts[1]}.".lowercase())
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        specificPortalKeywords.add("172.24.")
        specificPortalKeywords.add("172.16.")
        specificPortalKeywords.add(":8090")
        specificPortalKeywords.add("8090/httpclient")
        specificPortalKeywords.add("httpclient.html")
        specificPortalKeywords.add("httpclient")
        specificPortalKeywords.add("login.xml")
        specificPortalKeywords.add("captiveportal")
        specificPortalKeywords.add("connectivitycheck.gstatic.com")
        specificPortalKeywords.add("generate_204")

        for (p in profiles) {
            if (p.portalUrl.isNotBlank()) {
                val cleanUrl = p.portalUrl.lowercase()
                    .removePrefix("http://")
                    .removePrefix("https://")
                    .split("/").firstOrNull() ?: ""
                val host = cleanUrl.split(":").firstOrNull() ?: ""
                if (host.isNotBlank()) {
                    specificPortalKeywords.add(host)
                    val hostParts = host.split(".")
                    if (hostParts.size >= 2) {
                        specificPortalKeywords.add("${hostParts[0]}.${hostParts[1]}.".lowercase())
                    }
                }
            }
        }

        var foundBrowserUrl = ""
        var hasSpecificPortalMatch = false
        var isPublicWebsite = false
        var scannedNodes = 0

        fun scan(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || isPublicWebsite || scannedNodes > 60 || depth > 12) return
            scannedNodes++

            val viewId = (node.viewIdResourceName ?: "").lowercase()
            val text = (node.text?.toString() ?: "").lowercase()
            val contentDesc = (node.contentDescription?.toString() ?: "").lowercase()

            val isUrlBar = viewId.contains("url_bar") ||
                    viewId.contains("search_box") ||
                    viewId.contains("location_bar") ||
                    viewId.contains("toolbar_url") ||
                    viewId.contains("omnibox")

            if (isUrlBar && text.isNotBlank()) {
                foundBrowserUrl = text
            }

            // Check if page/URL is a public website
            for (blacklisted in publicDomainsBlacklist) {
                if (text.contains(blacklisted) || foundBrowserUrl.contains(blacklisted)) {
                    isPublicWebsite = true
                    return
                }
            }

            for (kw in specificPortalKeywords) {
                if (kw.isNotBlank() && (text.contains(kw) || contentDesc.contains(kw) || viewId.contains(kw) || foundBrowserUrl.contains(kw))) {
                    hasSpecificPortalMatch = true
                }
            }

            for (i in 0 until node.childCount) {
                scan(node.getChild(i), depth + 1)
            }
        }

        scan(rootNode, 0)

        if (isPublicWebsite) {
            return false
        }

        if (hasSpecificPortalMatch) {
            return true
        }

        if (foundBrowserUrl.isNotBlank()) {
            for (kw in specificPortalKeywords) {
                if (kw.isNotBlank() && foundBrowserUrl.contains(kw)) {
                    return true
                }
            }
        }

        return false
    }

    private fun fillAndSubmit(profile: WifiProfile) {
        lastDismissTimestamp = System.currentTimeMillis()

        try {
            val rootNode = rootInActiveWindow ?: activeAnchorNode?.window?.root ?: return
            val textFields = mutableListOf<AccessibilityNodeInfo>()
            var passwordField: AccessibilityNodeInfo? = null
            var submitButton: AccessibilityNodeInfo? = null

            fun scan(node: AccessibilityNodeInfo?) {
                if (node == null) return
                val className = node.className?.toString() ?: ""
                val text = (node.text?.toString() ?: "").lowercase()
                val contentDesc = (node.contentDescription?.toString() ?: "").lowercase()
                val viewId = (node.viewIdResourceName ?: "").lowercase()

                if (node.isPassword || className.contains("password", ignoreCase = true) || viewId.contains("pass") || viewId.contains("pwd")) {
                    passwordField = node
                } else if (node.isEditable || className.contains("EditText", ignoreCase = true)) {
                    textFields.add(node)
                }

                val isButton = node.isClickable && (
                        className.contains("Button", ignoreCase = true) ||
                        text.contains("sign in") || text.contains("login") || text.contains("submit") ||
                        contentDesc.contains("sign in") || contentDesc.contains("login")
                )

                if (isButton && submitButton == null) {
                    submitButton = node
                }

                for (i in 0 until node.childCount) {
                    scan(node.getChild(i))
                }
            }

            scan(rootNode)

            val usernameField = textFields.firstOrNull { it != passwordField } ?: activeAnchorNode

            if (usernameField != null) {
                // Focus & Fill Username
                usernameField.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val userArgs = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, profile.username)
                }
                usernameField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, userArgs)

                // Focus & Fill Password
                if (passwordField != null) {
                    passwordField?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    val passArgs = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, profile.password)
                    }
                    passwordField?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, passArgs)
                }

                LogRepository.success(
                    "Dropdown Autofill",
                    "Autofilled credentials for ${profile.username}!"
                )

                // Auto-click submit button with small delay
                mainHandler.postDelayed({
                    submitButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }, 150)
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            // Smoothly remove dropdown after fill
            mainHandler.postDelayed({
                removeDropdown()
            }, 80)
        }
    }

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    private fun showAnchoredDropdown(profiles: List<WifiProfile>, anchorBounds: Rect) {
        mainHandler.post {
            if (isDropdownShowing && dropdownView != null) {
                return@post
            }

            // Synchronously clean up previous view
            try {
                if (dropdownView != null && windowManager != null) {
                    windowManager?.removeView(dropdownView)
                }
            } catch (e: Exception) {}
            dropdownView = null
            isDropdownShowing = true
            lastShowTimestamp = System.currentTimeMillis()

            try {
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels

                // Google Password Manager Style Pill Card
                val layout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(10, 8, 10, 8)
                    val bg = GradientDrawable().apply {
                        setColor(Color.parseColor("#0F172A"))
                        cornerRadius = 28f
                        setStroke(2, Color.parseColor("#0D9488"))
                    }
                    background = bg
                    elevation = 32f

                    // Touch outside detection to dismiss immediately
                    setOnTouchListener { _, motionEvent ->
                        if (motionEvent.action == MotionEvent.ACTION_OUTSIDE) {
                            lastDismissTimestamp = System.currentTimeMillis()
                            removeDropdown()
                            true
                        } else {
                            false
                        }
                    }
                }

                // Header with Google Style branding & Close button
                val headerRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(12, 6, 12, 6)
                }

                val title = TextView(this).apply {
                    text = "🔑 USE SAVED ACCOUNT"
                    setTextColor(Color.parseColor("#2DD4BF"))
                    textSize = 10.5f
                    typeface = Typeface.DEFAULT_BOLD
                    letterSpacing = 0.05f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                headerRow.addView(title)

                val closeBtn = TextView(this).apply {
                    text = "✕"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(12, 0, 4, 0)
                    setOnClickListener {
                        lastDismissTimestamp = System.currentTimeMillis()
                        removeDropdown()
                    }
                }
                headerRow.addView(closeBtn)

                layout.addView(headerRow)

                for ((idx, p) in profiles.withIndex()) {
                    val item = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        isClickable = true
                        isFocusable = true
                        setPadding(14, 11, 14, 11)

                        val itemColor = if (p.isPrimary) Color.parseColor("#134E4A") else Color.parseColor("#1E293B")
                        val itemBg = GradientDrawable().apply {
                            setColor(itemColor)
                            cornerRadius = 20f
                            setStroke(1, Color.parseColor("#0D9488"))
                        }
                        val rippleColor = ColorStateList.valueOf(Color.parseColor("#2DD4BF"))
                        background = RippleDrawable(rippleColor, itemBg, null)

                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 3, 0, 3)
                        }
                        layoutParams = params

                        val icon = ImageView(context).apply {
                            setImageResource(android.R.drawable.ic_lock_lock)
                            setColorFilter(Color.parseColor("#2DD4BF"))
                            val iconParams = LinearLayout.LayoutParams(34, 34).apply {
                                setMargins(0, 0, 12, 0)
                            }
                            layoutParams = iconParams
                        }
                        addView(icon)

                        val textLayout = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        val userText = TextView(context).apply {
                            text = if (p.isPrimary) "👑 ${p.username}" else "🔄 ${p.username}"
                            setTextColor(Color.WHITE)
                            textSize = 13.5f
                            typeface = Typeface.DEFAULT_BOLD
                        }
                        textLayout.addView(userText)

                        val passSub = TextView(context).apply {
                            text = "•••••••••••• • ${p.name}"
                            setTextColor(Color.parseColor("#94A3B8"))
                            textSize = 10.5f
                        }
                        textLayout.addView(passSub)

                        addView(textLayout)

                        val fillBadge = TextView(context).apply {
                            text = "Autofill"
                            textSize = 11f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(Color.parseColor("#2DD4BF"))
                            setPadding(8, 0, 4, 0)
                        }
                        addView(fillBadge)

                        setOnClickListener {
                            fillAndSubmit(p)
                        }
                    }

                    layout.addView(item)
                }

                val dropdownWidth = (anchorBounds.width() + 40).coerceIn(620, screenWidth - 32)
                val targetX = (anchorBounds.left - 20).coerceIn(16, screenWidth - dropdownWidth - 16)

                // Intelligent Above/Below positioning: if input is low on screen, anchor ABOVE it
                val estimatedHeight = (profiles.size * 64 + 48).coerceAtMost(320)
                val spaceBelow = screenHeight - anchorBounds.bottom
                val targetY = if (spaceBelow < estimatedHeight + 120 && anchorBounds.top > estimatedHeight + 40) {
                    (anchorBounds.top - estimatedHeight - 12).coerceAtLeast(16)
                } else {
                    (anchorBounds.bottom + 8).coerceAtMost(screenHeight - estimatedHeight - 16)
                }

                val params = WindowManager.LayoutParams(
                    dropdownWidth,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = targetX
                    y = targetY
                }

                // Smooth Entrance Animation (Google Style Pop-in)
                layout.alpha = 0f
                layout.scaleX = 0.94f
                layout.scaleY = 0.94f

                windowManager?.addView(layout, params)
                dropdownView = layout

                layout.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .start()

            } catch (e: Exception) {
                isDropdownShowing = false
            }
        }
    }

    private fun removeDropdown() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            val view = dropdownView
            if (view != null && windowManager != null) {
                view.animate()
                    .alpha(0f)
                    .scaleX(0.94f)
                    .scaleY(0.94f)
                    .setDuration(100)
                    .withEndAction {
                        try {
                            windowManager?.removeView(view)
                        } catch (e: Exception) {}
                    }
                    .start()
            }
            dropdownView = null
            isDropdownShowing = false
        } else {
            mainHandler.post {
                removeDropdown()
            }
        }
    }

    override fun onInterrupt() {
        removeDropdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeDropdown()
    }
}
