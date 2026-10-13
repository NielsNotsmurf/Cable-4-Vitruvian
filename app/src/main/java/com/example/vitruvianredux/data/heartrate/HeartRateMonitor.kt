package com.example.vitruvianredux.data.heartrate

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads live heart rate from standard Bluetooth heart rate sensors: a Garmin watch with
 * "Broadcast Heart Rate" switched on, or any chest strap (Garmin HRM, Polar, Wahoo).
 *
 * Runs next to the Vitruvian connection and never touches it. Each profile can pair one
 * sensor; the view model tells this class which addresses to keep connected.
 * Android re-connects on its own (autoConnect) when a watch starts broadcasting again.
 */
@Singleton
class HeartRateMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** A reading older than this is treated as "no heart rate". */
        const val STALE_AFTER_MS = 5_000L

        private const val SCAN_DURATION_MS = 20_000L

        // Names of common devices that broadcast heart rate but don't always advertise the HR service
        private val KNOWN_HR_NAMES = Regex(
            "(?i)garmin|venu|forerunner|fenix|f[eē]nix|epix|instinct|vivoactive|hrm|polar|wahoo|tickr|coospo|heart"
        )

        /**
         * Parses a Heart Rate Measurement (0x2A37) value. Bit 0 of the flags byte says whether
         * the heart rate is a uint8 or a little-endian uint16.
         */
        fun parseHeartRateMeasurement(value: ByteArray): Int? {
            if (value.isEmpty()) return null
            val flags = value[0].toInt() and 0xFF
            val bpm = if (flags and 0x01 == 0) {
                if (value.size < 2) return null
                value[1].toInt() and 0xFF
            } else {
                if (value.size < 3) return null
                (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
            }
            return bpm.takeIf { it in 1..250 }
        }
    }

    data class Reading(val bpm: Int, val timestampMs: Long)

    data class FoundSensor(val address: String, val name: String, val advertisesHeartRate: Boolean)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _readings = MutableStateFlow<Map<String, Reading>>(emptyMap())
    /** Latest reading per sensor address. */
    val readings: StateFlow<Map<String, Reading>> = _readings.asStateFlow()

    private val _connectedAddresses = MutableStateFlow<Set<String>>(emptySet())
    val connectedAddresses: StateFlow<Set<String>> = _connectedAddresses.asStateFlow()

    private val _scanResults = MutableStateFlow<List<FoundSensor>>(emptyList())
    val scanResults: StateFlow<List<FoundSensor>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val gatts = ConcurrentHashMap<String, BluetoothGatt>()
    @Volatile private var targets: Set<String> = emptySet()
    private var scanStopJob: Job? = null

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Keep exactly these sensors connected. Called whenever profile pairings change.
     * An empty set disconnects everything, so the app works the same without a watch.
     */
    @SuppressLint("MissingPermission")
    fun setTargets(addresses: Set<String>) {
        targets = addresses
        gatts.keys.filter { it !in addresses }.forEach { address ->
            gatts.remove(address)?.let { gatt ->
                try {
                    gatt.disconnect()
                    gatt.close()
                } catch (e: SecurityException) {
                    Timber.w(e, "HR: no permission to close $address")
                }
            }
            markDisconnected(address)
        }
        if (!hasConnectPermission()) {
            Timber.w("HR: BLUETOOTH_CONNECT not granted, not connecting to heart rate sensors")
            return
        }
        val bluetooth = adapter ?: return
        addresses.filter { !gatts.containsKey(it) }.forEach { address ->
            if (!BluetoothAdapter.checkBluetoothAddress(address)) return@forEach
            try {
                val device = bluetooth.getRemoteDevice(address)
                // autoConnect = true: Android connects whenever the sensor shows up, so a watch
                // that starts broadcasting mid-workout is picked up without any user action.
                val gatt = device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
                if (gatt != null) gatts[address] = gatt
                Timber.d("HR: waiting for sensor $address")
            } catch (e: SecurityException) {
                Timber.w(e, "HR: no permission to connect to $address")
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "HR: invalid sensor address $address")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return
        if (!hasScanPermission()) {
            Timber.w("HR: scan permission not granted")
            return
        }
        val scanner = adapter?.bluetoothLeScanner ?: return
        _scanResults.value = emptyList()
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            // No service filter: some watches only put the HR service in the scan response,
            // so filter by service or by name in the callback instead.
            scanner.startScan(null, settings, scanCallback)
            _isScanning.value = true
            scanStopJob?.cancel()
            scanStopJob = scope.launch {
                delay(SCAN_DURATION_MS)
                stopScan()
            }
        } catch (e: SecurityException) {
            Timber.w(e, "HR: scan failed")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanStopJob?.cancel()
        scanStopJob = null
        if (!_isScanning.value) return
        _isScanning.value = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Timber.w(e, "HR: stop scan failed")
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address ?: return
            val name = result.scanRecord?.deviceName
                ?: try { result.device.name } catch (e: SecurityException) { null }
            val advertisesHr = result.scanRecord?.serviceUuids
                ?.contains(ParcelUuid(HEART_RATE_SERVICE)) == true
            if (!advertisesHr && (name == null || !KNOWN_HR_NAMES.containsMatchIn(name))) return
            val sensor = FoundSensor(address, name ?: "Heart rate sensor", advertisesHr)
            _scanResults.update { current ->
                if (current.any { it.address == address }) current
                else (current + sensor).sortedByDescending { it.advertisesHeartRate }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.w("HR: scan failed with code $errorCode")
            _isScanning.value = false
        }
    }

    private fun markDisconnected(address: String) {
        _connectedAddresses.update { it - address }
        _readings.update { it - address }
    }

    private fun handleMeasurement(address: String, value: ByteArray) {
        val bpm = parseHeartRateMeasurement(value) ?: return
        _readings.update { it + (address to Reading(bpm, System.currentTimeMillis())) }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            try {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Timber.d("HR: connected to $address")
                    _connectedAddresses.update { it + address }
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Timber.d("HR: disconnected from $address (status $status)")
                    markDisconnected(address)
                    // Re-arm auto-connect so the watch is picked up again when it comes back
                    if (address in targets && gatts[address] === gatt) gatt.connect()
                }
            } catch (e: SecurityException) {
                Timber.w(e, "HR: permission lost for $address")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val characteristic = gatt.getService(HEART_RATE_SERVICE)
                ?.getCharacteristic(HEART_RATE_MEASUREMENT)
            if (characteristic == null) {
                Timber.w("HR: ${gatt.device.address} has no heart rate service")
                return
            }
            try {
                gatt.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR) ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
            } catch (e: SecurityException) {
                Timber.w(e, "HR: could not enable notifications")
            }
        }

        // Android 13+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT) handleMeasurement(gatt.device.address, value)
        }

        // Android 12 and lower
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            if (characteristic.uuid == HEART_RATE_MEASUREMENT) handleMeasurement(gatt.device.address, value)
        }
    }
}
