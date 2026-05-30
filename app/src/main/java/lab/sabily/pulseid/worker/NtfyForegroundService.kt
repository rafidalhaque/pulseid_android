package lab.sabily.pulseid.worker

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
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
        .readTimeout(0, TimeUnit.MILLISECONDS) // Unlimited for SSE
        .build()

    private var currentCall: Call? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID_SERVICE = "pulseid_service_channel"

        fun start(context: Context) {
            val intent = Intent(context, NtfyForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NtfyForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createServiceNotificationChannel()
        startForeground(NOTIFICATION_ID, createServiceNotification())
        startSseConnection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startSseConnection() {
        serviceScope.launch {
            val settingsManager = SettingsManager(applicationContext)
            while (isActive) {
                val topic = settingsManager.ntfyTopic.firstOrNull()
                val token = settingsManager.ntfyToken.firstOrNull()

                if (topic == null || token == null) {
                    android.util.Log.w("NtfyService", "Credentials missing, retrying in 10s...")
                    delay(10000)
                    continue
                }

                val url = "https://notify.teamsabily.qzz.io/$topic/json"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .build()

                try {
                    android.util.Log.d("NtfyService", "Connecting to SSE: $url")
                    currentCall = client.newCall(request)
                    currentCall?.execute()?.use { response ->
                        if (response.code == 401) {
                            android.util.Log.e("NtfyService", "401 Unauthorized, stopping service")
                            settingsManager.saveNtfyCredentials(null, null)
                            stopSelf()
                            return@launch
                        }
                        
                        if (!response.isSuccessful) {
                            android.util.Log.e("NtfyService", "SSE failed with code ${response.code}")
                            throw IOException("Unexpected code $response")
                        }

                        val source = response.body?.source() ?: throw IOException("Empty body")
                        while (!source.exhausted() && isActive) {
                            val line = source.readUtf8Line()
                            if (!line.isNullOrBlank()) {
                                processLine(line)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        android.util.Log.e("NtfyService", "SSE connection error, reconnecting in 5s...", e)
                        delay(5000)
                    }
                }
            }
        }
    }

    private fun processLine(line: String) {
        try {
            val ntfyMsg = json.decodeFromString<NtfyMessage>(line)
            if (ntfyMsg.event == "message") {
                showNotification(ntfyMsg)
            }
        } catch (e: Exception) {
            android.util.Log.e("NtfyService", "Failed to parse line: $line", e)
        }
    }

    private fun showNotification(msg: NtfyMessage) {
        val isAppInForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (isAppInForeground) {
            android.util.Log.d("NtfyService", "App in foreground, skipping notification")
            return
        }

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
