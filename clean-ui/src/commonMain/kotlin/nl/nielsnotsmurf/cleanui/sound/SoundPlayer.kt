package nl.nielsnotsmurf.cleanui.sound

/**
 * Plays mono 16-bit PCM at [CleanSounds.RATE]. [render] runs at most once per [sfx];
 * the platform keeps the result and replays it. Never blocks the caller.
 */
internal expect fun playPcm(sfx: Sfx, render: () -> ShortArray)

/**
 * Plays [sfx] after [delayMs], also when the app is in the background or the browser tab is hidden
 * (the web schedules it on the audio clock, which keeps running while timers are throttled).
 * A new call replaces the pending one.
 */
internal expect fun schedulePcm(sfx: Sfx, delayMs: Long, render: () -> ShortArray)

/** Drops the sound queued by [schedulePcm], if it hasn't played yet. */
internal expect fun cancelScheduledPcm()
