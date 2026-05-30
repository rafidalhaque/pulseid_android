package lab.sabily.pulseid.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import lab.sabily.pulseid.data.SettingsManager

class NtfyBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settingsManager = SettingsManager(context)
            CoroutineScope(Dispatchers.IO).launch {
                val topic = settingsManager.ntfyTopic.firstOrNull()
                val token = settingsManager.ntfyToken.firstOrNull()
                if (topic != null && token != null) {
                    NtfyForegroundService.start(context)
                }
            }
        }
    }
}
