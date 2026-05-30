package lab.sabily.pulseid.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import lab.sabily.pulseid.MainActivity
import lab.sabily.pulseid.PulseIDApplication
import lab.sabily.pulseid.R
import lab.sabily.pulseid.data.NtfyMessage
import lab.sabily.pulseid.data.SettingsManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class NtfyPollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result {
        val settingsManager = SettingsManager(applicationContext)
        val ntfyTopic = settingsManager.ntfyTopic.firstOrNull() ?: return Result.success()
        val ntfyToken = settingsManager.ntfyToken.firstOrNull() ?: return Result.success()

        val url = "https://notify.teamsabily.qzz.io/$ntfyTopic/json?poll=1&since=15m"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $ntfyToken")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    // Handle 401: Clear credentials as requested
                    settingsManager.saveNtfyCredentials(null, null)
                    return Result.failure()
                }
                if (!response.isSuccessful) return Result.retry()

                val body = response.body?.string() ?: return Result.success()
                val lines = body.split("\n").filter { it.isNotBlank() }

                for (line in lines) {
                    try {
                        val ntfyMsg = json.decodeFromString<NtfyMessage>(line)
                        if (ntfyMsg.event == "message") {
                            showNotification(ntfyMsg)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("NtfyPollWorker", "Failed to parse line: $line", e)
                    }
                }
                Result.success()
            }
        } catch (e: Exception) {
            android.util.Log.e("NtfyPollWorker", "Polling failed", e)
            Result.retry()
        }
    }

    private fun showNotification(msg: NtfyMessage) {
        // Only show if app is in background
        val isAppInForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (isAppInForeground) {
            android.util.Log.d("NtfyPollWorker", "App in foreground, skipping notification")
            return
        }

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
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

    companion object {
        const val WORK_NAME = "NtfyPollWorker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<NtfyPollWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
