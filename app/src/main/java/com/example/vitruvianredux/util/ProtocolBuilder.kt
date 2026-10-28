package com.example.vitruvianredux.util

import com.example.vitruvianredux.domain.model.EchoLevel
import com.example.vitruvianredux.domain.model.ProgramMode
import com.example.vitruvianredux.domain.model.WorkoutParameters
import com.example.vitruvianredux.domain.model.WorkoutType
import nl.nielsnotsmurf.cleanui.trainer.TrainerProtocol

/**
 * Protocol Builder - Builds binary protocol frames for Vitruvian device communication
 * Ported from protocol.js and modes.js in the reference web application.
 * The bytes come from the shared TrainerProtocol, so the web app sends exactly the same.
 */
object ProtocolBuilder {

    /** The initial 4-byte command sent before INIT */
    fun buildInitCommand(): ByteArray = TrainerProtocol.init()

    /** INIT preset frame with coefficient table (34 bytes) */
    fun buildInitPreset(): ByteArray = TrainerProtocol.initPreset()

    /**
     * Program parameters frame (96 bytes). Just Lift and Echo run the Old School profile.
     * Weight is per cable; the machine applies it to each cable.
     */
    fun buildProgramParams(params: WorkoutParameters): ByteArray {
        val mode = when (val type = params.workoutType) {
            is WorkoutType.Program -> if (params.isJustLift) ProgramMode.OldSchool else type.mode
            is WorkoutType.Echo -> ProgramMode.OldSchool
        }
        timber.log.Timber.d("Program frame: ${params.workoutType}, profile $mode, ${params.weightPerCableKg} kg per cable, progression ${params.progressionRegressionKg} kg")
        return TrainerProtocol.programParams(
            reps = params.reps,
            warmupReps = params.warmupReps,
            unlimited = params.isJustLift || params.isAMRAP,
            profile = mode.toProfile(),
            weightPerCableKg = params.weightPerCableKg,
            progressionKg = params.progressionRegressionKg
        )
    }

    /** Echo mode control frame (32 bytes) */
    fun buildEchoControl(
        level: EchoLevel,
        warmupReps: Int = 3,
        targetReps: Int = 2,
        isJustLift: Boolean = false,
        isAMRAP: Boolean = false,
        eccentricPct: Int = 75
    ): ByteArray {
        timber.log.Timber.d("Echo frame: ${level.displayName}, eccentric $eccentricPct%, warmup $warmupReps, target $targetReps, justLift $isJustLift")
        return TrainerProtocol.echoControl(
            level = TrainerProtocol.EchoLevel.valueOf(level.name),
            warmupReps = warmupReps,
            targetReps = targetReps,
            unlimited = isJustLift || isAMRAP,
            eccentricPct = eccentricPct
        )
    }

    /** Color scheme command (34 bytes) */
    fun buildColorScheme(brightness: Float, colors: List<RGBColor>): ByteArray =
        TrainerProtocol.colorScheme(brightness, colors.map { (it.r shl 16) or (it.g shl 8) or it.b })

    private fun ProgramMode.toProfile(): TrainerProtocol.Profile = when (this) {
        is ProgramMode.OldSchool -> TrainerProtocol.Profile.OLD_SCHOOL
        is ProgramMode.Pump -> TrainerProtocol.Profile.PUMP
        is ProgramMode.TUT -> TrainerProtocol.Profile.TUT
        is ProgramMode.TUTBeast -> TrainerProtocol.Profile.TUT_BEAST
        is ProgramMode.EccentricOnly -> TrainerProtocol.Profile.ECCENTRIC_ONLY
    }

    /** START command (4 bytes) */
    fun buildStartCommand(): ByteArray = TrainerProtocol.start()

    /** STOP command (4 bytes) */
    fun buildStopCommand(): ByteArray = TrainerProtocol.stop()

    /** Official app stop packet (0x50): halts the set, the machine keeps reporting */
    fun buildOfficialStopPacket(): ByteArray = TrainerProtocol.officialStop()

    /** RESET/INIT command (0x0A), what the web app sends to stop */
    fun buildResetCommand(): ByteArray = TrainerProtocol.reset()

    /** Color scheme command for a predefined scheme */
    fun buildColorSchemeCommand(schemeIndex: Int): ByteArray {
        val schemes = ColorSchemes.ALL
        val scheme = schemes.getOrElse(schemeIndex) { schemes[0] }
        return buildColorScheme(scheme.brightness, scheme.colors)
    }
}
