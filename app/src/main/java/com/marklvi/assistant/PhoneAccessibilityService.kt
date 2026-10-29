package com.marklvi.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class PhoneAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onServiceConnected() { instance = this }
    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun doGlobalAction(action: Int): Boolean = performGlobalAction(action)

    companion object {
        @Volatile var instance: PhoneAccessibilityService? = null
            private set
    }
}
