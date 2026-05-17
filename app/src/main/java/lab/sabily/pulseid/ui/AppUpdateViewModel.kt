package lab.sabily.pulseid.ui

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lab.sabily.pulseid.data.AppUpdateService
import lab.sabily.pulseid.data.VersionInfo
import java.io.File

sealed class AppUpdateState {
    object Idle : AppUpdateState()
    object Checking : AppUpdateState()
    data class UpdateAvailable(val versionInfo: VersionInfo) : AppUpdateState()
    object UpToDate : AppUpdateState()
    data class Downloading(val progress: Float) : AppUpdateState()
    data class Error(val message: String) : AppUpdateState()
}

class AppUpdateViewModel(
    application: Application,
    private val updateService: AppUpdateService
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val downloadManager = application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1

    fun checkForUpdates(silent: Boolean = false) {
        if (!silent) _state.value = AppUpdateState.Checking
        
        viewModelScope.launch {
            try {
                val remoteVersion = updateService.getLatestVersion()
                val localVersionCode = getLocalVersionCode()
                
                if (remoteVersion.versionCode > localVersionCode) {
                    _state.value = AppUpdateState.UpdateAvailable(remoteVersion)
                } else if (!silent) {
                    _state.value = AppUpdateState.UpToDate
                }
            } catch (e: Exception) {
                if (!silent) _state.value = AppUpdateState.Error(e.message ?: "Failed to check for updates")
            }
        }
    }

    private fun getLocalVersionCode(): Long {
        val context = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
        }
    }

    fun startDownload(versionInfo: VersionInfo) {
        val context = getApplication<Application>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                _state.value = AppUpdateState.Error("Please allow unknown app installs and try again")
                return
            }
        }

        val request = DownloadManager.Request(Uri.parse(versionInfo.downloadUrl))
            .setTitle("PulseID Update")
            .setDescription("Downloading version ${versionInfo.versionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "pulseid.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        // Delete existing file if any
        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "pulseid.apk")
        if (destinationFile.exists()) destinationFile.delete()

        downloadId = downloadManager.enqueue(request)
        _state.value = AppUpdateState.Downloading(0f)

        monitorDownloadProgress()
        registerDownloadReceiver()
    }

    private fun monitorDownloadProgress() {
        viewModelScope.launch {
            while (_state.value is AppUpdateState.Downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_SUCCESSFUL) {
                        val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        if (total > 0) {
                            val progress = downloaded.toFloat() / total
                            _state.value = AppUpdateState.Downloading(progress)
                        }
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        _state.value = AppUpdateState.Error("Download failed")
                    }
                }
                cursor.close()
                delay(500)
            }
        }
    }

    private var downloadReceiver: BroadcastReceiver? = null

    private fun registerDownloadReceiver() {
        val context = getApplication<Application>()
        if (downloadReceiver != null) return

        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(context)
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(context: Context) {
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "pulseid.apk")
        if (!apkFile.exists()) {
            _state.value = AppUpdateState.Error("APK file not found after download")
            return
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        
        // Clean up
        unregisterDownloadReceiver()
        _state.value = AppUpdateState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        unregisterDownloadReceiver()
    }

    private fun unregisterDownloadReceiver() {
        val context = getApplication<Application>()
        downloadReceiver?.let {
            context.unregisterReceiver(it)
            downloadReceiver = null
        }
    }

    class Factory(private val application: Application, private val updateService: AppUpdateService) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppUpdateViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AppUpdateViewModel(application, updateService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}