package ambank.gtr.tapjacktester

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var overlayView: FrameLayout? = null
    private var overlayVisible = false

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, buildNotification())
        createOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        overlayView?.let {
            try { wm.removeView(it) } catch (_: Throwable) {}
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createOverlay() {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0x50FF0000) // transparent red
            visibility = FrameLayout.GONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or // key line
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        wm.addView(overlay, params)
        overlayView = overlay
    }

    private fun showOverlay() {
        overlayVisible = true
        overlayView?.visibility = FrameLayout.VISIBLE
    }

    private fun hideOverlay() {
        overlayVisible = false
        overlayView?.visibility = FrameLayout.GONE
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Tapjack Tester",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Tapjack Tester running")
            .setContentText("Overlay active")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_SHOW = "overlay_show"
        const val ACTION_HIDE = "overlay_hide"
        private const val CHANNEL_ID = "tapjacktester"
        private const val NOTIF_ID = 1001
    }
}
