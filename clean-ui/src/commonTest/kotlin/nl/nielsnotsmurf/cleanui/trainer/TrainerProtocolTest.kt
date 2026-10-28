package nl.nielsnotsmurf.cleanui.trainer

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden bytes captured from the app's ProtocolBuilder before it moved here, so the app and
 * the web app keep sending exactly what the app always sent.
 */
class TrainerProtocolTest {
    private fun hex(b: ByteArray) = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun programFramesMatchTheApp() {
        // Just Lift always runs the Old School profile, as in the app.

        val cases = listOf(
            Triple(TrainerProtocol.programParams(10, 3, false || false, TrainerProtocol.Profile.OLD_SCHOOL, 20.0f, 0.0f), "OLD_SCHOOL 10", "040000000d0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa00500000001400000040404b00580200004842ecfa50fb0000c842fcfe92ff00000000000000000000f0410000a04100000000"),
            Triple(TrainerProtocol.programParams(8, 3, false || false, TrainerProtocol.Profile.OLD_SCHOOL, 12.5f, 1.5f), "OLD_SCHOOL 8", "040000000b0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa00500000001400000040404b00580200004842ecfa50fb0000c842fcfe92ff00000000000000000000a841000030410000c03f"),
            Triple(TrainerProtocol.programParams(5, 3, true || false, TrainerProtocol.Profile.OLD_SCHOOL, 33.3f, -2.0f), "OLD_SCHOOL 5", "04000000ff0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa00500000001400000040404b00580200004842ecfa50fb0000c842fcfe92ff00000000000000003333354233330d42000000c0"),
            Triple(TrainerProtocol.programParams(12, 3, false || true, TrainerProtocol.Profile.OLD_SCHOOL, 7.0f, 0.0f), "OLD_SCHOOL 12", "04000000ff0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa00500000001400000040404b00580200004842ecfa50fb0000c842fcfe92ff0000000000000000000088410000e04000000000"),
            Triple(TrainerProtocol.programParams(10, 3, false || false, TrainerProtocol.Profile.PUMP, 20.0f, 0.0f), "PUMP 10", "040000000d0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa0050003200c20100002041f40158020000484244fddafd0000803f9cffceff0000803f000000000000f0410000a04100000000"),
            Triple(TrainerProtocol.programParams(8, 3, false || false, TrainerProtocol.Profile.PUMP, 12.5f, 1.5f), "PUMP 8", "040000000b0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa0050003200c20100002041f40158020000484244fddafd0000803f9cffceff0000803f000000000000a841000030410000c03f"),
            Triple(TrainerProtocol.programParams(5, 3, true || false, TrainerProtocol.Profile.OLD_SCHOOL, 33.3f, -2.0f), "PUMP 5", "04000000ff0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa00500000001400000040404b00580200004842ecfa50fb0000c842fcfe92ff00000000000000003333354233330d42000000c0"),
            Triple(TrainerProtocol.programParams(12, 3, false || true, TrainerProtocol.Profile.PUMP, 7.0f, 0.0f), "PUMP 12", "04000000ff0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa0050003200c20100002041f40158020000484244fddafd0000803f9cffceff0000803f00000000000088410000e04000000000"),
            Triple(TrainerProtocol.programParams(10, 3, false || false, TrainerProtocol.Profile.TUT, 20.0f, 0.0f), "TUT 10", "040000000d0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa005000fa005e010000e040c2015802000048427cfc44fd00008c429cffceff00006041000000000000f0410000a04100000000"),
            Triple(TrainerProtocol.programParams(8, 3, false || false, TrainerProtocol.Profile.TUT, 12.5f, 1.5f), "TUT 8", "040000000b0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa005000fa005e010000e040c2015802000048427cfc44fd00008c429cffceff00006041000000000000a841000030410000c03f"),
            Triple(TrainerProtocol.programParams(5, 3, true || false, TrainerProtocol.Profile.OLD_SCHOOL, 33.3f, -2.0f), "TUT 5", "04000000ff0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa00500000001400000040404b00580200004842ecfa50fb0000c842fcfe92ff00000000000000003333354233330d42000000c0"),
            Triple(TrainerProtocol.programParams(12, 3, false || true, TrainerProtocol.Profile.TUT, 7.0f, 0.0f), "TUT 12", "04000000ff0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa005000fa005e010000e040c2015802000048427cfc44fd00008c429cffceff0000604100000000000088410000e04000000000"),
            Triple(TrainerProtocol.programParams(10, 3, false || false, TrainerProtocol.Profile.TUT_BEAST, 20.0f, 0.0f), "TUT_BEAST 10", "040000000d0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa0050009600fa000000e0405e01c201000048427cfc44fd00008c429cffceff0000e041000000000000f0410000a04100000000"),
            Triple(TrainerProtocol.programParams(8, 3, false || false, TrainerProtocol.Profile.TUT_BEAST, 12.5f, 1.5f), "TUT_BEAST 8", "040000000b0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa0050009600fa000000e0405e01c201000048427cfc44fd00008c429cffceff0000e041000000000000a841000030410000c03f"),
            Triple(TrainerProtocol.programParams(5, 3, true || false, TrainerProtocol.Profile.OLD_SCHOOL, 33.3f, -2.0f), "TUT_BEAST 5", "04000000ff0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa00500000001400000040404b00580200004842ecfa50fb0000c842fcfe92ff00000000000000003333354233330d42000000c0"),
            Triple(TrainerProtocol.programParams(12, 3, false || true, TrainerProtocol.Profile.TUT_BEAST, 7.0f, 0.0f), "TUT_BEAST 12", "04000000ff0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa0050009600fa000000e0405e01c201000048427cfc44fd00008c429cffceff0000e04100000000000088410000e04000000000"),
            Triple(TrainerProtocol.programParams(10, 3, false || false, TrainerProtocol.Profile.ECCENTRIC_ONLY, 20.0f, 0.0f), "ECCENTRIC_ONLY 10", "040000000d0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa00500032002602000048428a02ee02000020417cfc44fd00008c429cffceff0000a041000000000000f0410000a04100000000"),
            Triple(TrainerProtocol.programParams(8, 3, false || false, TrainerProtocol.Profile.ECCENTRIC_ONLY, 12.5f, 1.5f), "ECCENTRIC_ONLY 8", "040000000b0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa00500032002602000048428a02ee02000020417cfc44fd00008c429cffceff0000a041000000000000a841000030410000c03f"),
            Triple(TrainerProtocol.programParams(5, 3, true || false, TrainerProtocol.Profile.OLD_SCHOOL, 33.3f, -2.0f), "ECCENTRIC_ONLY 5", "04000000ff0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa00500000001400000040404b00580200004842ecfa50fb0000c842fcfe92ff00000000000000003333354233330d42000000c0"),
            Triple(TrainerProtocol.programParams(12, 3, false || true, TrainerProtocol.Profile.ECCENTRIC_ONLY, 7.0f, 0.0f), "ECCENTRIC_ONLY 12", "04000000ff0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa00500032002602000048428a02ee02000020417cfc44fd00008c429cffceff0000a04100000000000088410000e04000000000"),
            Triple(TrainerProtocol.programParams(10, 3, false, TrainerProtocol.Profile.OLD_SCHOOL, 20f, 0f), "echo workout frame", "040000000d0303000000a0400000a04000000000fa00fa00c8001e000000a04000000000fa00fa00c8001e00fa00500000001400000040404b00580200004842ecfa50fb0000c842fcfe92ff00000000000000000000f0410000a04100000000")
        )
        cases.forEach { (bytes, name, expected) -> assertEquals(expected, hex(bytes), name) }
    }

    @Test
    fun echoFramesMatchTheApp() {
        val cases = listOf(
            Triple(TrainerProtocol.echoControl(TrainerProtocol.EchoLevel.HARD, 3, 10, false || false, 75), "HARD 3/10/75", "4e000000030a00004b003200cdcccc3d0000803f00004842000000000000c8c2"),
            Triple(TrainerProtocol.echoControl(TrainerProtocol.EchoLevel.HARD, 2, 6, true || false, 150), "HARD 2/6/150", "4e00000002ff000096003200cdcccc3d0000803f00004842000000000000c8c2"),
            Triple(TrainerProtocol.echoControl(TrainerProtocol.EchoLevel.HARD, 3, 8, false || true, 0), "HARD 3/8/0", "4e00000003ff000000003200cdcccc3d0000803f00004842000000000000c8c2"),
            Triple(TrainerProtocol.echoControl(TrainerProtocol.EchoLevel.HARDER, 3, 10, false || false, 75), "HARDER 3/10/75", "4e000000030a00004b003200cdcccc3d0000a03f00002042000000000000c8c2"),
            Triple(TrainerProtocol.echoControl(TrainerProtocol.EchoLevel.HARDER, 2, 6, true || false, 150), "HARDER 2/6/150", "4e00000002ff000096003200cdcccc3d0000a03f00002042000000000000c8c2"),
            Triple(TrainerProtocol.echoControl(TrainerProtocol.EchoLevel.HARDER, 3, 8, false || true, 0), "HARDER 3/8/0", "4e00000003ff000000003200cdcccc3d0000a03f00002042000000000000c8c2"),
            Triple(TrainerProtocol.echoControl(TrainerProtocol.EchoLevel.HARDEST, 3, 10, false || false, 75), "HARDEST 3/10/75", "4e000000030a00004b003200cdcccc3d4260d53f0000f041000000000000c8c2"),
            Triple(TrainerProtocol.echoControl(TrainerProtocol.EchoLevel.HARDEST, 2, 6, true || false, 150), "HARDEST 2/6/150", "4e00000002ff000096003200cdcccc3d4260d53f0000f041000000000000c8c2"),
            Triple(TrainerProtocol.echoControl(TrainerProtocol.EchoLevel.HARDEST, 3, 8, false || true, 0), "HARDEST 3/8/0", "4e00000003ff000000003200cdcccc3d4260d53f0000f041000000000000c8c2"),
            Triple(TrainerProtocol.echoControl(TrainerProtocol.EchoLevel.EPIC, 3, 10, false || false, 75), "EPIC 3/10/75", "4e000000030a00004b003200cdcccc3ddf4f554000007041000000000000c8c2"),
            Triple(TrainerProtocol.echoControl(TrainerProtocol.EchoLevel.EPIC, 2, 6, true || false, 150), "EPIC 2/6/150", "4e00000002ff000096003200cdcccc3ddf4f554000007041000000000000c8c2"),
            Triple(TrainerProtocol.echoControl(TrainerProtocol.EchoLevel.EPIC, 3, 8, false || true, 0), "EPIC 3/8/0", "4e00000003ff000000003200cdcccc3ddf4f554000007041000000000000c8c2")
        )
        cases.forEach { (bytes, name, expected) -> assertEquals(expected, hex(bytes), name) }
    }

    @Test
    fun smallCommandsMatchTheApp() {
        assertEquals("110000000000000000000000cdcccc3e00a8dd00cffc5ddffc00a8dd00cffc5ddffc", hex(TrainerProtocol.colorScheme(0.4f, listOf(0x00A8DD, 0x00CFFC, 0x5DDFFC))))
        assertEquals("0a000000", hex(TrainerProtocol.init()))
        assertEquals("110000000000000000000000cdcccc3eff004cff238cff8c8cff004cff238cff8c8c", hex(TrainerProtocol.initPreset()))
        assertEquals("03000000", hex(TrainerProtocol.start()))
        assertEquals("05000000", hex(TrainerProtocol.stop()))
        assertEquals("5000", hex(TrainerProtocol.officialStop()))
        assertEquals("0a000000", hex(TrainerProtocol.reset()))
    }

    @Test
    fun parsesSamplesAndReps() {
        // ticks 70000, posA -12.3 mm, loadA 25.5 kg, posB 456.7 mm, loadB 0.5 kg, status 0x8000
        val sample = byteArrayOf(0x70, 0x11, 0x01, 0x00, 0x85.toByte(), 0xFF.toByte(), 0, 0, 0xF6.toByte(), 0x09,
            0xD7.toByte(), 0x11, 0, 0, 0x32, 0x00, 0x00, 0x80.toByte())
        assertEquals(TrainerProtocol.Sample(70000, -12.3f, 25.5f, 456.7f, 0.5f, 0x8000), TrainerProtocol.parseSample(sample))
        val reps = ByteArray(24).also { it[0] = 5; it[4] = 4; it[16] = 3; it[20] = 2; it[11] = 0x3F; it[10] = 0x80.toByte() }
        assertEquals(TrainerProtocol.Reps(5, 4, 1.0f, 0f, 3, 2, false), TrainerProtocol.parseReps(reps))
        assertEquals(TrainerProtocol.Reps(7, 6, 0f, 0f, 0, 0, true), TrainerProtocol.parseReps(byteArrayOf(7, 0, 0, 0, 6, 0)))
        assertEquals(72, TrainerProtocol.parseHeartRate(byteArrayOf(0x00, 72)))
        assertEquals(300, TrainerProtocol.parseHeartRate(byteArrayOf(0x01, 0x2C, 0x01)))
    }
}
