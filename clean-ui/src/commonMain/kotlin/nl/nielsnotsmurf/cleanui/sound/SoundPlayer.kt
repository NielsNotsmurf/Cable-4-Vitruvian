package nl.nielsnotsmurf.cleanui.sound

/**
 * Plays mono 16-bit PCM at [CleanSounds.RATE]. [render] runs at most once per [sfx];
 * the platform keeps the result and replays it. Never blocks the caller.
 */
internal expect fun playPcm(sfx: Sfx, render: () -> ShortArray)
