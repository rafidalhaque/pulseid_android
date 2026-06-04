package lab.sabily.pulseid.worker

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import lab.sabily.pulseid.MainActivity
import lab.sabily.pulseid.PulseIDApplication
import lab.sabily.pulseid.R
import lab.sabily.pulseid.data.NtfyMessage
import lab.sabily.pulseid.data.SettingsManager
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

class NtfyForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Unlimited for SSE
        .build()

    private var currentCall: Call? = null

    companion object {
        private const val TAG = "NtfyService"
        private const val SERVICE_NOTIFICATION_ID = 8888
        private const val CHANNEL_ID_SERVICE = "pulseid_service_channel"

        fun start(context: Context) {
            android.util.Log.d(TAG, "Starting NtfyForegroundService...")
            val intent = Intent(context, NtfyForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            android.util.Log.d(TAG, "Stopping NtfyForegroundService...")
            val intent = Intent(context, NtfyForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d(TAG, "Service onCreate")
        createServiceNotificationChannel()

        try {
            val notification = createServiceNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(SERVICE_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start foreground service", e)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && 
                e is android.app.ForegroundServiceStartNotAllowedException) {
                android.util.Log.e(TAG, "ForegroundServiceStartNotAllowedException caught")
            }
            stopSelf()
            return
        }
        startSseConnection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startSseConnection() {
        serviceScope.launch {
            val settingsManager = SettingsManager(applicationContext)
            
            // Show test notification to verify channel
            showNotification(NtfyMessage(
                id = "test_${System.currentTimeMillis()}",
                time = System.currentTimeMillis() / 1000,
                event = "message",
                title = "Ntfy Service Started",
                message = "Listening for real-time updates..."
            ))

            while (isActive) {
                val topic = settingsManager.ntfyTopic.firstOrNull()
                val token = settingsManager.ntfyToken.firstOrNull()

                if (topic.isNullOrEmpty() || token.isNullOrEmpty()) {
                    android.util.Log.w(TAG, "Credentials missing: topic=${topic?.take(8)}, token=${token?.take(8)}, retrying in 10s...")
                    delay(10000)
                    continue
                }

                android.util.Log.d(TAG, "SSE service started for topic: $topic")
                android.util.Log.d(TAG, "Ntfy token: ${token.take(8)}...")

                val url = "https://notify.teamsabily.qzz.io/$topic/sse"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .build()

                try {
                    android.util.Log.d(TAG, "Connecting to SSE: $url")
                    currentCall = client.newCall(request)
                    currentCall?.execute()?.use { response ->
                        if (response.code == 401) {
                            android.util.Log.e(TAG, "401 Unauthorized, stopping service")
                            settingsManager.saveNtfyCredentials(null, null)
                            stopSelf()
                            return@launch
                        }
                        
                        if (!response.isSuccessful) {
                            android.util.Log.e(TAG, "SSE failed with code ${response.code}")
                            throw IOException("Unexpected code $response")
                        }

                        android.util.Log.d(TAG, "SSE connection opened")
                        val source = response.body?.source() ?: throw IOException("Empty body")
                        val currentData = StringBuilder()
                        
                        while (!source.exhausted() && isActive) {
                            val line = source.readUtf8Line()
                            if (line == null) break
                            android.util.Log.d(TAG, "SSE line: $line")
                            
                            when {
                                line.startsWith("data:") -> {
                                    currentData.append(line.removePrefix("data:").trim())
                                }
                                line.isBlank() -> {
                                    val data = currentData.toString()
                                    if (data.isNotBlank()) {
                                        processLine(data)
                                    }
                                    currentData.setLength(0)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        android.util.Log.e(TAG, "SSE connection error, reconnecting in 5s...", e)
                        delay(5000)
                    }
                }
            }
        }
    }

    private fun processLine(line: String) {
        try {
            android.util.Log.d(TAG, "Parsing SSE data: $line")
            val ntfyMsg = json.decodeFromString<NtfyMessage>(line)
            if (ntfyMsg.event == "message") {
                android.util.Log.d(TAG, "SSE message received: ${ntfyMsg.title}")
                showNotification(ntfyMsg)
            } else {
                android.util.Log.d(TAG, "SSE event received: ${ntfyMsg.event}")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to parse line: $line", e)
        }
    }

    private fun showNotification(msg: NtfyMessage) {
        android.util.Log.d(TAG, "Showing notification: ${msg.title}")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            data = msg.click?.let { Uri.parse(it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            msg.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, PulseIDApplication.CHANNEL_ID)
            .setSmallIcon(R.mipmap.pulseid)
            .setContentTitle(msg.title ?: "PulseID Notification")
            .setContentText(msg.message ?: "")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(msg.id.hashCode(), notification)
    }

    private fun createServiceNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setSmallIcon(R.mipmap.pulseid)
            .setContentTitle("PulseID Notification Service")
            .setContentText("Listening for real-time updates")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createServiceNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID_SERVICE,
                "Notification Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        currentCall?.cancel()
        super.onDestroy()
    }
}
