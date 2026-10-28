package nl.nielsnotsmurf.webapp

import kotlinx.coroutines.await
import nl.nielsnotsmurf.cleanui.trainer.TrainerProtocol

// Web Bluetooth: Chrome and Edge (Android, desktop) and Bluefy on iOS. Safari has none.

fun bluetoothAvailable(): Boolean = js("(typeof navigator !== 'undefined' && !!navigator.bluetooth)")

/** Resolves with { device, rx, sample, reps } or null when the picker is cancelled. */
private fun jsConnectTrainer(service: String, rx: String, sample: String, reps: String, prefix: String): kotlin.js.Promise<JsAny?> = js("""
    navigator.bluetooth.requestDevice({ filters: [{ namePrefix: prefix }, { namePrefix: 'Vitruvian' }], optionalServices: [service] })
        .then(device => device.gatt.connect()
            .then(server => server.getPrimaryService(service))
            .then(svc => Promise.all([svc.getCharacteristic(rx), svc.getCharacteristic(sample), svc.getCharacteristic(reps)]))
            .then(c => ({ device: device, rx: c[0], sample: c[1], reps: c[2] })))
        .catch(e => (e && e.name === 'NotFoundError') ? null : Promise.reject(e))
""")

/** Resolves with { device, hr } or null when the picker is cancelled. */
private fun jsConnectHeartRate(service: String, char: String): kotlin.js.Promise<JsAny?> = js("""
    navigator.bluetooth.requestDevice({ filters: [{ services: [service] }] })
        .then(device => device.gatt.connect()
            .then(server => server.getPrimaryService(service))
            .then(svc => svc.getCharacteristic(char))
            .then(c => ({ device: device, hr: c })))
        .catch(e => (e && e.name === 'NotFoundError') ? null : Promise.reject(e))
""")

private fun jsDeviceName(link: JsAny): String = js("(link.device.name || 'Vitruvian')")

private fun jsOnDisconnect(link: JsAny, onGone: () -> Unit): Unit =
    js("link.device.addEventListener('gattserverdisconnected', () => onGone())")

private fun jsDisconnect(link: JsAny): Unit = js("{ try { link.device.gatt.disconnect(); } catch (e) {} }")

private fun jsNotify(link: JsAny, key: String, onValue: (String) -> Unit): kotlin.js.Promise<JsAny?> = js("""
    link[key].startNotifications().then(c => {
        c.addEventListener('characteristicvaluechanged', e => {
            const v = e.target.value;
            onValue(Array.from(new Uint8Array(v.buffer, v.byteOffset, v.byteLength), b => b.toString(16).padStart(2, '0')).join(''));
        });
        return null;
    })
""")

private fun jsRead(link: JsAny, key: String): kotlin.js.Promise<JsString?> = js("""
    link[key].readValue()
        .then(v => Array.from(new Uint8Array(v.buffer, v.byteOffset, v.byteLength), b => b.toString(16).padStart(2, '0')).join(''))
        .catch(() => null)
""")

private fun jsWrite(link: JsAny, hex: String): kotlin.js.Promise<JsAny?> = js("""
    (() => {
        const bytes = new Uint8Array(hex.match(/../g).map(h => parseInt(h, 16)));
        const c = link.rx;
        return (c.properties.write ? c.writeValueWithResponse(bytes) : c.writeValueWithoutResponse(bytes)).then(() => null);
    })()
""")

private fun ByteArray.toHex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

private fun String.hexToBytes() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

/** A connected trainer: commands go to the command characteristic, samples are polled. */
class TrainerLink private constructor(private val link: JsAny) {
    val name: String = jsDeviceName(link)

    suspend fun send(bytes: ByteArray) { jsWrite(link, bytes.toHex()).await<JsAny?>() }

    suspend fun readSample(): TrainerProtocol.Sample? =
        jsRead(link, "sample").await<JsString?>()?.toString()?.hexToBytes()?.let(TrainerProtocol::parseSample)

    fun disconnect() = jsDisconnect(link)

    companion object {
        /** Opens the browser's device picker; null when cancelled. */
        suspend fun connect(onReps: (TrainerProtocol.Reps) -> Unit, onGone: () -> Unit): TrainerLink? {
            val link = jsConnectTrainer(
                TrainerProtocol.SERVICE, TrainerProtocol.COMMAND_CHAR, TrainerProtocol.SAMPLE_CHAR,
                TrainerProtocol.REPS_CHAR, TrainerProtocol.DEVICE_NAME_PREFIX
            ).await<JsAny?>() ?: return null
            jsOnDisconnect(link, onGone)
            jsNotify(link, "reps") { hex -> TrainerProtocol.parseReps(hex.hexToBytes())?.let(onReps) }.await<JsAny?>()
            return TrainerLink(link)
        }
    }
}

/** A heart rate strap or watch broadcasting the standard heart rate service. */
class HeartRateLink private constructor(private val link: JsAny) {
    val name: String = jsDeviceName(link)

    fun disconnect() = jsDisconnect(link)

    companion object {
        suspend fun connect(onBpm: (Int) -> Unit, onGone: () -> Unit): HeartRateLink? {
            val link = jsConnectHeartRate(TrainerProtocol.HEART_RATE_SERVICE, TrainerProtocol.HEART_RATE_CHAR)
                .await<JsAny?>() ?: return null
            jsOnDisconnect(link, onGone)
            jsNotify(link, "hr") { hex -> TrainerProtocol.parseHeartRate(hex.hexToBytes())?.let(onBpm) }.await<JsAny?>()
            return HeartRateLink(link)
        }
    }
}
