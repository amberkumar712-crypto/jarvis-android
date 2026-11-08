package com.jarvis.ai

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

// Only acts inside WhatsApp, only for ~20s after you confirm a message in JARVIS.
class JarvisService : AccessibilityService() {
    companion object {
        @Volatile var armed = false
        @Volatile var armedAt = 0L
        @Volatile var running = false
        fun arm() { armed = true; armedAt = System.currentTimeMillis() }
    }
    override fun onServiceConnected() { running = true }
    override fun onUnbind(intent: Intent?): Boolean { running = false; return super.onUnbind(intent) }
    override fun onInterrupt() {}
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (!armed) return
        if (System.currentTimeMillis() - armedAt > 20000) { armed = false; return }
        if (e?.packageName?.toString() != "com.whatsapp") return
        val root = rootInActiveWindow ?: return
        val n = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send").firstOrNull()
            ?: root.findAccessibilityNodeInfosByText("Send").firstOrNull { it.contentDescription?.toString() == "Send" }
        if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) armed = false
    }
}
