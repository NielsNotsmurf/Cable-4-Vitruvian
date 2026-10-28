package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.OneRepMax
import nl.nielsnotsmurf.cleanui.model.OneRepMaxSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OneRepMaxMapTest {

    private val map = OneRepMaxMap(
        mapOf(
            "incline" to OneRepMaxMap.Entry("bench", 0.9f),
            "decline" to OneRepMaxMap.Entry("bench", 0.95f),
            "fly" to OneRepMaxMap.Entry("bench", 0.5f),
            "curl-sa" to OneRepMaxMap.Entry("curl", 1f)
        )
    )

    private fun tested(kg: Float) = OneRepMax(kg, OneRepMaxSource.BENCHMARK)

    @Test
    fun scalesFromTheAnchor() {
        assertEquals(22.5f, map.estimate("incline", mapOf("bench" to tested(25f))))
    }

    @Test
    fun anchorIsEstimatedBackFromARelatedLift() {
        assertEquals(25f, map.estimate("bench", mapOf("incline" to tested(22.5f))))
    }

    @Test
    fun prefersTheClosestRelatedLift() {
        // Decline (0.95) says bench ≈ 40; the fly (0.5) would say 60.
        val known = mapOf("decline" to tested(38f), "fly" to tested(30f))
        assertEquals(36f, map.estimate("incline", known))
    }

    @Test
    fun ignoresEstimatesAndOtherGroups() {
        assertNull(map.estimate("incline", mapOf("bench" to OneRepMax(25f, OneRepMaxSource.RELATED))))
        assertNull(map.estimate("incline", mapOf("curl" to tested(15f))))
        assertNull(map.estimate("unknown", mapOf("bench" to tested(25f))))
    }

    @Test
    fun shippedAssetParsesAndCoversTheLibrary() {
        val parsed = OneRepMaxMap.parse(File("src/androidMain/assets/one_rep_max_map.json").readText())
        // Incline Bench Press (handles) from Bench Press (handles).
        assertEquals(22.5f, parsed.estimate("aR4mXWcgsqNaxw4C", mapOf("ZZ92N8QsBdp6HCh3" to tested(25f))))
        val curlEstimate = parsed.estimate("fc5ca114-3cb6-462a-ab54-7ef1233b5fc2", mapOf("ZZ92N8QsBdp6HCh3" to tested(25f)))
        assertNull(curlEstimate)
        assertTrue(parsed.entryFor("lpnNPw86Vud67vWQ") != null)
    }
}
