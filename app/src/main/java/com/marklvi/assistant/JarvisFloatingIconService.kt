package com.marklvi.assistant

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

/** Small draggable JARVIS indicator. Visible only while display is on and listener is active. */
class JarvisFloatingIconService : Service() {
    private lateinit var wm: WindowManager
    private var icon: ImageView? = null
    private var screenOn = true
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; removeIcon() }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> { screenOn = true; showIconIfAllowed() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val f = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, f, RECEIVER_NOT_EXPORTED) else @Suppress("DEPRECATION") registerReceiver(screenReceiver, f)
        showIconIfAllowed()
    }

    private fun showIconIfAllowed() {
        if (!screenOn || icon != null || !Settings.canDrawOverlays(this)) return
        val v = ImageView(this).apply {
            setImageResource(R.drawable.jarvis_icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(5, 18, 48))
                setStroke(dp(2), Color.rgb(70, 180, 255))
            }
            clipToOutline = true
            elevation = dp(8).toFloat()
            contentDescription = "MARK LVI JARVIS is listening"
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val p = WindowManager.LayoutParams(dp(54), dp(54), type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.END; x = dp(12); y = dp(180)
        }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = p.x; oy = p.y; true }
                MotionEvent.ACTION_MOVE -> { p.x = (ox + sx - e.rawX).toInt().coerceAtLeast(0); p.y = (oy + e.rawY - sy).toInt().coerceAtLeast(0); try { wm.updateViewLayout(v, p) } catch (_: Exception) {}; true }
                MotionEvent.ACTION_UP -> { if (kotlin.math.abs(e.rawX - sx) < dp(8) && kotlin.math.abs(e.rawY - sy) < dp(8)) { val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP); startActivity(i) }; true }
                else -> false
            }
        }
        try { wm.addView(v, p); icon = v } catch (_: Exception) { icon = null }
    }

    private fun removeIcon() { try { icon?.let { wm.removeView(it) } } catch (_: Exception) {}; icon = null }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroy() { removeIcon(); try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
