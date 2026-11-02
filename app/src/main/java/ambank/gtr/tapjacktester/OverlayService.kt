package ambank.gtr.tapjacktester

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
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
        // 1. Establish precise button size parameters (Instead of full screen MATCH_PARENT)
        val elementWidth = 500   // Targeted width footprint in pixels
        val elementHeight = 180  // Targeted height footprint in pixels
        val offsetX = 0          // Horizontal layout alignment adjustments
        val offsetY = 400        // Vertical layout alignment adjustments relative to gravity center

        // 2. Set up layout container wrapper
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT) // Wrapper is clear so unmasked areas remain accessible
            visibility = FrameLayout.GONE
        }

        // 3. Construct the localized visual indicator element
        val visualMaskElement = Button(this).apply {
            text = "Verify Connection"
            setBackgroundColor(Color.parseColor("#E6C23350")) // Semi-transparent test mask color
            setTextColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        visualMaskElement.setOnTouchListener(object : OnTouchListener {
            private var localTapCount = 0

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    localTapCount++

                    // Generate a local system UI alert box (Strictly local tracking mechanism)
                    Toast.makeText(
                        applicationContext,
                        "PoC Target Hit! Tap #$localTapCount\nCoords: X=${event.rawX.toInt()} Y=${event.rawY.toInt()}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Record transaction telemetry data directly to local device system streams
                    Log.w(
                        "OfflineTapValidation",
                        "Target Intercept -> Tap detected at X: ${event.rawX}, Y: ${event.rawY}"
                    )

                    // Dynamic visible layout feedback adjustment
                    visualMaskElement.text = "TAP #$localTapCount VERIFIED"
                    visualMaskElement.setBackgroundColor(Color.parseColor("#FF3300"))
                }

                // Return false to allow the physical gesture event packet to simultaneously cascade down
                // to the target layout application element resting beneath the test boundary box.
                return false
            }
        })

        overlay.addView(visualMaskElement)

        // 4. Shrink WindowManager parameters configuration to strictly target the overlay element boundary box
        val params = WindowManager.LayoutParams(
            elementWidth,
            elementHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_NOT_TOUCH_MODAL instructs the operating system that actions outside this small layout window
            // bounds bypass our service process completely, preventing a total user interface lock.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = offsetX
            y = offsetY
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
            .setContentText("Targeted overlay active")
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
