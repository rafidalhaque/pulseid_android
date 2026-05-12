package lab.sabily.pulseid

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BleAdvertiser(private val context: Context) {
    private val TAG = "BleAdvertiser"
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _status = MutableStateFlow("Stopped")
    val status: StateFlow<String> = _status.asStateFlow()

    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "BLE Advertising started successfully")
            _status.value = "Started"
            Toast.makeText(context, "BLE Advertising started!", Toast.LENGTH_SHORT).show()
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            val errorMsg = when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                else -> "Unknown error: $errorCode"
            }
            Log.e(TAG, "BLE Advertising failed: $errorMsg")
            _status.value = "Error: $errorMsg"
            Toast.makeText(context, "Advertising failed: $errorMsg", Toast.LENGTH_LONG).show()
        }
    }

    fun startAdvertising(uuidString: String) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _status.value = "Error: Bluetooth not supported"
            return
        }

        if (!adapter.isEnabled) {
            _status.value = "Error: Bluetooth is OFF"
            return
        }

        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            val isMultiAdv = adapter.isMultipleAdvertisementSupported
            val isLe2M = adapter.isLe2MPhySupported
            val isExtAdv = adapter.isLeExtendedAdvertisingSupported

            Log.e(TAG, "BLE Advertising not supported. MultiAdv: $isMultiAdv, Le2M: $isLe2M, ExtAdv: $isExtAdv")

            _status.value = "Error: Bluetooth LE Advertising not supported"
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // Avoid "Data too large" errors
            .setIncludeTxPowerLevel(true)
            .addServiceUuid(ParcelUuid(UUID.fromString(uuidString)))
            .build()

        try {
            _status.value = "Starting..."
            advertiser.startAdvertising(settings, data, callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Missing permissions to advertise", e)
            _status.value = "Error: Missing Permissions"
        } catch (e: Exception) {
            Log.e(TAG, "Error starting advertising", e)
            _status.value = "Error: ${e.message}"
        }
    }

    fun stopAdvertising() {
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            _status.value = "Stopped"
            return
        }

        try {
            advertiser.stopAdvertising(callback)
            _status.value = "Stopped"
            Log.d(TAG, "BLE Advertising stopped")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Missing permissions to stop advertising", e)
            _status.value = "Error: Missing Permissions"
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising", e)
            _status.value = "Error: ${e.message}"
        }
    }
}