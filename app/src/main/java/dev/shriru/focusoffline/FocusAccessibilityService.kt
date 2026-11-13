package dev.shriru.focusoffline

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event?.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val prefs = getSharedPreferences("focus_offline", MODE_PRIVATE)
        val endAt = prefs.getLong("session_end", 0L)
        if (endAt <= System.currentTimeMillis()) return
        val blocked = prefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
        if (pkg in blocked) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            val intent = Intent(this, BlockedActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
    }
    override fun onInterrupt() = Unit
}
