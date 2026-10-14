package com.example.vitruvianredux.data.garmin

import android.content.Context
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.nielsnotsmurf.cleanui.logic.GarminWatches
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Live link to the vitruvian- Connect IQ app on a Garmin watch (source in /watch), through
 * Garmin Connect Mobile on this tablet.
 *
 * At a profile's first set the watch is asked to open the app, which records heart rate and
 * each set this class sends (exercise, reps, weight). "Finish training", or 30 minutes without
 * a set, makes the watch save it as a strength activity that syncs to Garmin Connect.
 * Only the profile a watch belongs to sends to it (see [GarminWatches.watchFor]).
 * Without Garmin Connect Mobile or a watch nothing happens and workouts run as before.
 */
@Singleton
class GarminWatchLink @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        /** Must match the application id in watch/manifest.xml. */
        const val WATCH_APP_ID = "9ff78d152cd74db0ab2362fb6f1c5352"
        const val IDLE_FINISH_MS = 30 * 60_000L
        private const val PREFS = "garmin_watch_links"
    }

    data class Watch(val id: Long, val name: String, val connected: Boolean, val problem: String? = null)

    /** A profile as the link needs it: who they are and which heart rate sensor they paired. */
    data class Profile(val id: String, val name: String, val heartRateSensorName: String?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val app = IQApp(WATCH_APP_ID)
    private var ciq: ConnectIQ? = null
    private var ready = false
    private val devices = mutableMapOf<Long, IQDevice>()

    private val _watches = MutableStateFlow<List<Watch>>(emptyList())
    /** Garmin watches known to Garmin Connect Mobile on this tablet. */
    val watches: StateFlow<List<Watch>> = _watches.asStateFlow()

    private val _chosen = MutableStateFlow(loadChosen())
    /** Watch picked per profile id ([GarminWatches.NO_WATCH] = none); others are matched automatically. */
    val chosen: StateFlow<Map<String, Long>> = _chosen.asStateFlow()

    private val _recording = MutableStateFlow<Set<Long>>(emptySet())
    /** Watches recording the current training. */
    val recording: StateFlow<Set<Long>> = _recording.asStateFlow()

    private var profiles: List<Profile> = emptyList()
    private var training = newTrainingId()
    private var nextSetId = 1
    private var idleJob: Job? = null
    /** Messages not yet delivered, per watch; resent when the watch app reports it is open. */
    private val pending = mutableMapOf<Long, MutableList<Map<String, Any>>>()

    /** Connects to Garmin Connect Mobile. Safe to call more than once. */
    fun start() {
        if (ciq != null) return
        try {
            val sdk = ConnectIQ.getInstance(context, ConnectIQ.IQConnectType.WIRELESS)
            ciq = sdk
            sdk.initialize(context, false, object : ConnectIQ.ConnectIQListener {
                override fun onSdkReady() {
                    ready = true
                    refreshWatches()
                }

                override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
                    // Usually Garmin Connect Mobile isn't installed: no watches, nothing to do.
                    Timber.i("Garmin Connect IQ unavailable: $status")
                }

                override fun onSdkShutDown() {
                    ready = false
                }
            })
        } catch (e: Exception) {
            Timber.w(e, "Garmin Connect IQ failed to start")
        }
    }

    fun updateProfiles(list: List<Profile>) {
        profiles = list
    }

    /** The watch that records [profileId]'s sets, or null. */
    fun watchFor(profileId: String): Watch? {
        val all = _watches.value
        val match = GarminWatches.watchFor(
            profileId,
            all.map { GarminWatches.Watch(it.id, it.name) },
            _chosen.value,
            profiles.associate { it.id to it.heartRateSensorName }
        ) ?: return null
        return all.firstOrNull { it.id == match.id }
    }

    /** Picks [watchId] for [profileId], or [GarminWatches.NO_WATCH]. A watch belongs to one profile. */
    fun choose(profileId: String, watchId: Long) {
        _chosen.update { current ->
            val others = if (watchId == GarminWatches.NO_WATCH) current else current.filterValues { it != watchId }
            others + (profileId to watchId)
        }
        val editor = prefs.edit().clear()
        _chosen.value.forEach { (profile, watch) -> editor.putLong(profile, watch) }
        editor.apply()
    }

    /** A set starts on the trainer for [profileId]: opens the app on their watch the first time. */
    fun setStarted(profileId: String) {
        // Callers may be on any thread; the SDK and this state live on the main thread.
        scope.launch {
            val watch = watchFor(profileId) ?: return@launch
            val device = devices[watch.id] ?: return@launch
            touch()
            if (watch.id !in _recording.value) beginOn(device)
            send(device, message("go", profileId), queue = false)
        }
    }

    /** A set by [profileId] is done. [kg] is the total load (both cables). */
    fun setCompleted(profileId: String, exercise: String, reps: Int, kg: Float) {
        // Callers may be on any thread; the SDK and this state live on the main thread.
        scope.launch {
            val watch = watchFor(profileId) ?: return@launch
            val device = devices[watch.id] ?: return@launch
            touch()
            // The set started before the watch was linked or reachable.
            if (watch.id !in _recording.value) beginOn(device)
            send(
                device,
                message("set", profileId) + mapOf(
                    "id" to nextSetId++,
                    "ex" to exercise,
                    "cat" to GarminWatches.categoryFor(exercise),
                    "reps" to reps,
                    "kg" to kg
                ),
                queue = true
            )
        }
    }

    /** "Finish training": every recording watch saves its activity. */
    fun finishTraining() {
        idleJob?.cancel()
        _recording.value.forEach { id ->
            devices[id]?.let { send(it, mapOf("t" to "end", "tr" to training), queue = true) }
        }
        _recording.value = emptySet()
        training = newTrainingId()
        nextSetId = 1
    }

    private fun message(type: String, profileId: String): Map<String, Any> = mapOf(
        "t" to type,
        "tr" to training,
        "who" to (profiles.firstOrNull { it.id == profileId }?.name ?: "")
    )

    /** Ends the training on its own after [IDLE_FINISH_MS] without a set (the watch does the same). */
    private fun touch() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_FINISH_MS)
            finishTraining()
        }
    }

    /** Starts this training on [device]; anything left from an earlier training is dropped. */
    private fun beginOn(device: IQDevice) {
        pending.remove(device.deviceIdentifier)
        _recording.update { it + device.deviceIdentifier }
        openApp(device)
    }

    private fun openApp(device: IQDevice) {
        val sdk = ciq?.takeIf { ready } ?: return
        try {
            sdk.openApplication(device, app) { _, _, status ->
                Timber.d("Garmin open app on ${device.friendlyName}: $status")
                val problem = when (status) {
                    ConnectIQ.IQOpenApplicationStatus.APP_IS_NOT_INSTALLED -> "vitruvian- app is not on this watch"
                    else -> null
                }
                if (problem != null) _recording.update { it - device.deviceIdentifier }
                _watches.update { list -> list.map { if (it.id == device.deviceIdentifier) it.copy(problem = problem) else it } }
            }
        } catch (e: Exception) {
            Timber.w(e, "Garmin open app failed")
        }
    }

    private fun send(device: IQDevice, payload: Map<String, Any>, queue: Boolean) {
        val sdk = ciq?.takeIf { ready } ?: return
        val id = device.deviceIdentifier
        if (queue) pending.getOrPut(id) { mutableListOf() }.add(payload)
        try {
            sdk.sendMessage(device, app, payload) { _, _, status ->
                if (status == ConnectIQ.IQMessageStatus.SUCCESS) {
                    pending[id]?.remove(payload)
                } else {
                    Timber.d("Garmin message ${payload["t"]} not delivered yet: $status")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Garmin send failed")
        }
    }

    private fun onWatchMessage(device: IQDevice, data: List<Any>) {
        val msg = data.firstOrNull() as? Map<*, *> ?: return
        when (msg["t"]) {
            // The app just opened: deliver what it missed.
            "ready" -> pending[device.deviceIdentifier]?.toList()?.forEach { send(device, it, queue = false) }
            // Saved or discarded on the watch: the next set starts a new activity.
            "saved" -> if (msg["tr"] == null || msg["tr"] == training) {
                pending.remove(device.deviceIdentifier)
                _recording.update { it - device.deviceIdentifier }
            }
        }
    }

    /** Looks for watches paired in Garmin Connect Mobile since the app started. */
    fun refreshWatches() {
        val sdk = ciq?.takeIf { ready } ?: return
        try {
            val known = sdk.knownDevices.orEmpty()
            known.forEach { device ->
                if (devices.put(device.deviceIdentifier, device) != null) return@forEach
                sdk.registerForDeviceEvents(device) { d, _ -> updateWatch(d) }
                sdk.registerForAppEvents(device, app) { d, _, data, status ->
                    if (status == ConnectIQ.IQMessageStatus.SUCCESS) onWatchMessage(d, data)
                }
            }
            _watches.value = known.map { d ->
                val old = _watches.value.firstOrNull { it.id == d.deviceIdentifier }
                Watch(d.deviceIdentifier, d.friendlyName ?: "Garmin", statusOf(d) == IQDevice.IQDeviceStatus.CONNECTED, old?.problem)
            }
        } catch (e: Exception) {
            Timber.w(e, "Garmin device list failed")
        }
    }

    private fun updateWatch(device: IQDevice) {
        val connected = statusOf(device) == IQDevice.IQDeviceStatus.CONNECTED
        _watches.update { list -> list.map { if (it.id == device.deviceIdentifier) it.copy(connected = connected) else it } }
        if (connected) pending[device.deviceIdentifier]?.toList()?.forEach { send(device, it, queue = false) }
    }

    private fun statusOf(device: IQDevice): IQDevice.IQDeviceStatus? =
        try { ciq?.getDeviceStatus(device) } catch (e: Exception) { null }

    private fun loadChosen(): Map<String, Long> =
        prefs.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    private fun newTrainingId() = Random.nextInt(1, Int.MAX_VALUE)
}
