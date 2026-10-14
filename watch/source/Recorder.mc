import Toybox.Activity;
import Toybox.ActivityRecording;
import Toybox.Communications;
import Toybox.FitContributor;
import Toybox.Lang;
import Toybox.Sensor;
import Toybox.System;
import Toybox.WatchUi;

// Records one Vitruvian training as a strength activity. The tablet sends:
//   {"t"=>"go"}                         a set starts on the trainer
//   {"t"=>"set", "id", "ex", "cat", "reps", "kg"}  a set is done
//   {"t"=>"end"}                        "Finish training" on the tablet
// Every message also carries "tr" (training id) and "who" (profile name).
// Connect IQ cannot write Garmin's own set records, so each set is a lap with
// its exercise, reps and weight in developer fields; rest between sets is a lap too.
class Recorder {
    // Save on our own after this long without a set (the tablet may be gone).
    const IDLE_SAVE_MS = 1800000;

    var session = null;
    var repsField = null;
    var weightField = null;
    var categoryField = null;
    var exerciseField = null;
    var totalRepsField = null;
    var volumeField = null;

    var training = null;
    var seen = {};
    var inSet = false;
    var setCount = 0;
    var totalReps = 0;
    var volumeKg = 0.0;
    var lifter = "";
    var lastSet = "";
    var status = "Waiting for the first set";
    var done = false;
    var lastActivityMs = 0;
    // Direct link to the tablet (TabletLink), next to the phone route.
    var link = null;

    function initialize() {
    }

    function start() {
        Sensor.setEnabledSensors([Sensor.SENSOR_HEARTRATE]);
        session = ActivityRecording.createSession({
            :name => "Vitruvian",
            :sport => Activity.SPORT_TRAINING,
            :subSport => Activity.SUB_SPORT_STRENGTH_TRAINING
        });
        repsField = session.createField("reps", 0, FitContributor.DATA_TYPE_UINT16,
            {:mesgType => FitContributor.MESG_TYPE_LAP, :units => "reps"});
        weightField = session.createField("weight", 1, FitContributor.DATA_TYPE_FLOAT,
            {:mesgType => FitContributor.MESG_TYPE_LAP, :units => "kg"});
        categoryField = session.createField("exercise_category", 2, FitContributor.DATA_TYPE_UINT16,
            {:mesgType => FitContributor.MESG_TYPE_LAP});
        exerciseField = session.createField("exercise", 3, FitContributor.DATA_TYPE_STRING,
            {:mesgType => FitContributor.MESG_TYPE_LAP, :count => 32});
        totalRepsField = session.createField("total_reps", 4, FitContributor.DATA_TYPE_UINT32,
            {:mesgType => FitContributor.MESG_TYPE_SESSION, :units => "reps"});
        volumeField = session.createField("volume", 5, FitContributor.DATA_TYPE_FLOAT,
            {:mesgType => FitContributor.MESG_TYPE_SESSION, :units => "kg"});
        clearLapFields();
        session.start();
        lastActivityMs = System.getTimer();
    }

    function isRecording() {
        return session != null && !done;
    }

    function handle(data) {
        var type = data["t"];
        if (type == null) {
            return;
        }
        var tr = data["tr"];
        if (tr != null && training != null && !tr.equals(training)) {
            // A new training while the last one never got its "end": keep both apart.
            if (type.equals("end")) {
                return;
            }
            save();
            restart();
        }
        if (!isRecording()) {
            return;
        }
        if (tr != null) {
            training = tr;
        }
        if (data["who"] != null) {
            lifter = data["who"];
        }
        lastActivityMs = System.getTimer();
        if (type.equals("go")) {
            startSet();
        } else if (type.equals("set")) {
            finishSet(data);
        } else if (type.equals("end")) {
            save();
        }
        WatchUi.requestUpdate();
    }

    function restart() {
        seen = {};
        inSet = false;
        setCount = 0;
        totalReps = 0;
        volumeKg = 0.0;
        lastSet = "";
        done = false;
        status = "Waiting for the first set";
        start();
    }

    // Closes the rest lap, so the next lap is the set itself.
    function startSet() {
        if (inSet) {
            return;
        }
        clearLapFields();
        session.addLap();
        inSet = true;
        status = "Lifting";
    }

    function finishSet(data) {
        var key = data["id"];
        if (key != null) {
            if (seen.hasKey(key)) {
                return;
            }
            seen.put(key, true);
        }
        var reps = numberOr(data["reps"], 0);
        var kg = floatOr(data["kg"], 0.0);
        var name = data["ex"] != null ? data["ex"].toString() : "Vitruvian";
        if (name.length() > 31) {
            name = name.substring(0, 31);
        }
        repsField.setData(reps);
        weightField.setData(kg);
        categoryField.setData(numberOr(data["cat"], 65534));
        exerciseField.setData(name);
        session.addLap();
        clearLapFields();
        inSet = false;
        setCount += 1;
        totalReps += reps;
        volumeKg += reps * kg;
        lastSet = name + "\n" + reps + " x " + kg.format("%.1f") + " kg";
        status = "Set " + setCount;
    }

    function clearLapFields() {
        repsField.setData(0);
        weightField.setData(0.0);
        categoryField.setData(65534);
        exerciseField.setData("Rest");
    }

    // Called every few seconds by the view.
    function tick() {
        if (isRecording() && System.getTimer() - lastActivityMs > IDLE_SAVE_MS) {
            save();
        }
    }

    function save() {
        if (!isRecording()) {
            return;
        }
        totalRepsField.setData(totalReps);
        volumeField.setData(volumeKg);
        if (session.isRecording()) {
            session.stop();
        }
        session.save();
        done = true;
        status = "Saved to Garmin Connect";
        sendToPhone({"t" => "saved", "tr" => training});
    }

    function discard() {
        if (!isRecording()) {
            return;
        }
        if (session.isRecording()) {
            session.stop();
        }
        session.discard();
        done = true;
        status = "Discarded";
        sendToPhone({"t" => "saved", "tr" => training});
    }

    function sendToPhone(data) {
        Communications.transmit(data, null, new PhoneListener());
        if (link != null) {
            link.send(data["t"] + "|" + (data["tr"] != null ? data["tr"].toString() : ""));
        }
    }

    function numberOr(value, fallback) {
        return value != null ? value.toNumber() : fallback;
    }

    function floatOr(value, fallback) {
        return value != null ? value.toFloat() : fallback;
    }
}

class PhoneListener extends Communications.ConnectionListener {
    function initialize() {
        ConnectionListener.initialize();
    }

    function onComplete() {
    }

    function onError() {
    }
}
