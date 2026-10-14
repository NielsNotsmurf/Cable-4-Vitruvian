package com.example.vitruvianredux.data.garmin

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.util.UUID

/**
 * Direct Bluetooth link to the vitruvian- watch app, for a watch paired to another phone.
 *
 * The tablet advertises [SERVICE] and runs a GATT server; the watch app (watch/source/TabletLink.mc)
 * connects, subscribes to [TO_WATCH] and writes to [TO_TABLET]. Messages are text lines
 * ("type|field|...\n") sent in 20-byte pieces, the default BLE payload.
 * Callbacks arrive on the main thread.
 */
@SuppressLint("MissingPermission") // every entry point checks the permissions first
class BleWatchServer(
    private val context: Context,
    /** A watch said hello: its own id and name. */
    private val onHello: (watchId: Long, name: String) -> Unit,
    /** A line from a watch, split into fields. */
    private val onLine: (watchId: Long, fields: List<String>) -> Unit,
    private val onDisconnected: (watchId: Long) -> Unit
) {
    companion object {
        val SERVICE: UUID = UUID.fromString("7a1e0001-5c4b-4d2e-9f3a-2b6c8d9e0f10")
        val TO_WATCH: UUID = UUID.fromString("7a1e0002-5c4b-4d2e-9f3a-2b6c8d9e0f10")
        val TO_TABLET: UUID = UUID.fromString("7a1e0003-5c4b-4d2e-9f3a-2b6c8d9e0f10")
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val CHUNK = 20
    }

    private val main = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private var server: BluetoothGattServer? = null
    private var advertising = false
    private lateinit var toWatch: BluetoothGattCharacteristic

    private val watchOf = mutableMapOf<String, Long>()          // device address -> watch id
    private val deviceOf = mutableMapOf<Long, BluetoothDevice>()
    private val subscribed = mutableSetOf<String>()
    private val inbox = mutableMapOf<String, StringBuilder>()
    private val outbox = mutableMapOf<String, ArrayDeque<ByteArray>>()
    private val sending = mutableSetOf<String>()

    fun hasPermissions(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || listOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        ).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Starts the server and advertising. Safe to call again, e.g. after permissions were granted. */
    fun start() {
        if (!hasPermissions()) return
        val adapter = manager?.adapter ?: return
        if (!adapter.isEnabled) return
        try {
            if (server == null) {
                val gatt = manager.openGattServer(context, callback) ?: return
                toWatch = BluetoothGattCharacteristic(
                    TO_WATCH,
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
                ).apply {
                    addDescriptor(BluetoothGattDescriptor(CCCD, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
                }
                val toTablet = BluetoothGattCharacteristic(
                    TO_TABLET,
                    BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
                )
                gatt.addService(BluetoothGattService(SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
                    addCharacteristic(toWatch)
                    addCharacteristic(toTablet)
                })
                server = gatt
            }
            if (!advertising) {
                val advertiser = adapter.bluetoothLeAdvertiser ?: return
                advertiser.startAdvertising(
                    AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                        .setConnectable(true)
                        .build(),
                    AdvertiseData.Builder().addServiceUuid(ParcelUuid(SERVICE)).build(),
                    advertiseCallback
                )
                advertising = true
            }
        } catch (e: Exception) {
            Timber.w(e, "Watch link: Bluetooth server failed to start")
        }
    }

    fun isConnected(watchId: Long): Boolean = deviceOf[watchId]?.address?.let { it in subscribed } == true

    /** Sends one line to [watchId]; false when it is not connected. */
    fun send(watchId: Long, fields: List<String>): Boolean {
        val device = deviceOf[watchId] ?: return false
        if (device.address !in subscribed) return false
        val line = fields.joinToString("|") { it.replace("|", "/").replace("\n", " ") } + "\n"
        val bytes = line.toByteArray(Charsets.UTF_8)
        val queue = outbox.getOrPut(device.address) { ArrayDeque() }
        for (i in bytes.indices step CHUNK) queue.add(bytes.copyOfRange(i, minOf(i + CHUNK, bytes.size)))
        sendNext(device)
        return true
    }

    // Android allows one notification in flight per device: the next goes in onNotificationSent.
    private fun sendNext(device: BluetoothDevice) {
        val address = device.address
        if (address in sending) return
        val piece = outbox[address]?.removeFirstOrNull() ?: return
        val gatt = server ?: return
        try {
            sending += address
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.notifyCharacteristicChanged(device, toWatch, false, piece) == BluetoothGatt.GATT_SUCCESS
            } else {
                legacyNotify(gatt, device, piece)
            }
            if (!ok) {
                sending -= address
                outbox[address]?.addFirst(piece)
                main.postDelayed({ sendNext(device) }, 50)
            }
        } catch (e: Exception) {
            sending -= address
            Timber.w(e, "Watch link: notify failed")
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyNotify(gatt: BluetoothGattServer, device: BluetoothDevice, piece: ByteArray): Boolean {
        toWatch.value = piece
        return gatt.notifyCharacteristicChanged(device, toWatch, false)
    }

    private fun receive(device: BluetoothDevice, value: ByteArray) {
        val buffer = inbox.getOrPut(device.address) { StringBuilder() }
        buffer.append(String(value, Charsets.UTF_8))
        while (true) {
            val end = buffer.indexOf("\n")
            if (end < 0) break
            val line = buffer.substring(0, end)
            buffer.delete(0, end + 1)
            val fields = line.split("|")
            if (fields.firstOrNull() == "hi") {
                val id = fields.getOrNull(1)?.toLongOrNull(16) ?: continue
                watchOf[device.address] = id
                deviceOf[id] = device
                onHello(id, fields.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "Garmin watch")
            } else {
                watchOf[device.address]?.let { onLine(it, fields) }
            }
        }
    }

    private fun forget(device: BluetoothDevice) {
        val address = device.address
        subscribed -= address
        sending -= address
        inbox.remove(address)
        outbox.remove(address)
        watchOf.remove(address)?.let { id ->
            if (deviceOf[id]?.address == address) deviceOf.remove(id)
            onDisconnected(id)
        }
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) main.post { forget(device) }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
            val on = value != null && value.isNotEmpty() && value[0].toInt() != 0
            main.post { if (on) subscribed += device.address else subscribed -= device.address }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            if (characteristic.uuid == TO_TABLET && value != null) main.post { receive(device, value) }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            main.post {
                sending -= device.address
                sendNext(device)
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            Timber.w("Watch link: advertising failed ($errorCode)")
            advertising = false
        }
    }
}
