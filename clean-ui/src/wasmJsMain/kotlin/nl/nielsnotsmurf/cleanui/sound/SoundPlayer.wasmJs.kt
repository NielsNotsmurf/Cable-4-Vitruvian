package nl.nielsnotsmurf.cleanui.sound

// Web Audio: each sound is rendered once into an AudioBuffer and replayed from there.

private fun jsNewBuffer(length: Int, rate: Int): JsAny? = js(
    """{
        const C = window.AudioContext || window.webkitAudioContext;
        if (!C) return null;
        window.__cleanUiAudio = window.__cleanUiAudio || new C();
        return window.__cleanUiAudio.createBuffer(1, length, rate);
    }"""
)

private fun jsChannel(buffer: JsAny): JsAny = js("buffer.getChannelData(0)")

private fun jsSetSample(channel: JsAny, index: Int, value: Double): Unit = js("{ channel[index] = value; }")

private fun jsPlay(buffer: JsAny): Unit = js(
    """{
        const c = window.__cleanUiAudio;
        if (c.state === 'suspended') c.resume();
        const s = c.createBufferSource();
        s.buffer = buffer;
        s.connect(c.destination);
        s.start();
    }"""
)

// Queued on the audio clock: it plays on time even when a hidden tab's timers are throttled.
// Until it has played, an inaudible hum keeps the page "playing audio", so the phone doesn't
// suspend the audio (and with it the beep) while another app is in front.
private fun jsPlayAt(buffer: JsAny, delaySeconds: Double): Unit = js(
    """{
        const c = window.__cleanUiAudio;
        if (c.state === 'suspended') c.resume();
        if (window.__cleanUiQueued) { try { window.__cleanUiQueued.stop(); } catch (e) {} }
        if (window.__cleanUiHum) { try { window.__cleanUiHum.stop(); } catch (e) {} }
        const hum = c.createBufferSource();
        const hb = c.createBuffer(1, c.sampleRate, c.sampleRate);
        const d = hb.getChannelData(0);
        for (let i = 0; i < d.length; i++) d[i] = (Math.random() * 2 - 1) * 0.0005;
        hum.buffer = hb;
        hum.loop = true;
        hum.connect(c.destination);
        hum.start();
        window.__cleanUiHum = hum;
        const s = c.createBufferSource();
        s.buffer = buffer;
        s.connect(c.destination);
        s.onended = () => { if (window.__cleanUiHum === hum) { try { hum.stop(); } catch (e) {} window.__cleanUiHum = null; } };
        s.start(c.currentTime + Math.max(0, delaySeconds));
        window.__cleanUiQueued = s;
    }"""
)

private fun jsCancelQueued(): Unit = js(
    """{
        if (window.__cleanUiQueued) { try { window.__cleanUiQueued.stop(); } catch (e) {} }
        if (window.__cleanUiHum) { try { window.__cleanUiHum.stop(); } catch (e) {} }
        window.__cleanUiQueued = null;
        window.__cleanUiHum = null;
    }"""
)

private val buffers = HashMap<Sfx, JsAny>()

private fun bufferFor(sfx: Sfx, render: () -> ShortArray): JsAny? = buffers[sfx] ?: run {
    val pcm = render()
    val b = jsNewBuffer(pcm.size, CleanSounds.RATE) ?: return null
    val channel = jsChannel(b)
    for (i in pcm.indices) jsSetSample(channel, i, pcm[i] / 32768.0)
    b.also { buffers[sfx] = it }
}

internal actual fun playPcm(sfx: Sfx, render: () -> ShortArray) {
    runCatching { bufferFor(sfx, render)?.let { jsPlay(it) } }
}

internal actual fun schedulePcm(sfx: Sfx, delayMs: Long, render: () -> ShortArray) {
    runCatching { bufferFor(sfx, render)?.let { jsPlayAt(it, delayMs / 1000.0) } }
}

internal actual fun cancelScheduledPcm() {
    runCatching { jsCancelQueued() }
}
