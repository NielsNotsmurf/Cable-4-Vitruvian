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

private val buffers = HashMap<Sfx, JsAny>()

internal actual fun playPcm(sfx: Sfx, render: () -> ShortArray) {
    runCatching {
        val buffer = buffers[sfx] ?: run {
            val pcm = render()
            val b = jsNewBuffer(pcm.size, CleanSounds.RATE) ?: return
            val channel = jsChannel(b)
            for (i in pcm.indices) jsSetSample(channel, i, pcm[i] / 32768.0)
            b.also { buffers[sfx] = it }
        }
        jsPlay(buffer)
    }
}
