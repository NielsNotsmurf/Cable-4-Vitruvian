package nl.nielsnotsmurf.cleanui.ui.components

private val muxId = Regex("""^https://stream\.mux\.com/([A-Za-z0-9]+)(?:\.m3u8|/[a-z]+\.mp4)""")

/** Mux links are either ID.m3u8 or ID/high.mp4; the HLS stream lets the player pick a small rendition. */
internal fun previewStreamUrl(url: String): String =
    muxId.find(url)?.let { "https://stream.mux.com/${it.groupValues[1]}.m3u8" } ?: url

internal fun muxThumbnail(url: String): String? =
    muxId.find(url)?.let { "https://image.mux.com/${it.groupValues[1]}/thumbnail.jpg?width=640" }
