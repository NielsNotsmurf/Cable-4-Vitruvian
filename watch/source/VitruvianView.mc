import Toybox.Activity;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Timer;
import Toybox.WatchUi;

class VitruvianView extends WatchUi.View {
    var recorder;
    var timer;
    var savedTicks = 0;

    function initialize(rec) {
        View.initialize();
        recorder = rec;
    }

    function onShow() {
        timer = new Timer.Timer();
        timer.start(method(:onTimer), 1000, true);
    }

    function onHide() {
        if (timer != null) {
            timer.stop();
        }
    }

    function onTimer() as Void {
        recorder.tick();
        if (!recorder.isRecording()) {
            // Show "Saved" for a moment, then close.
            savedTicks += 1;
            if (savedTicks >= 4) {
                System.exit();
            }
        }
        WatchUi.requestUpdate();
    }

    function onUpdate(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        var info = Activity.getActivityInfo();
        var hr = (info != null && info.currentHeartRate != null) ? info.currentHeartRate.toString() : "--";
        var seconds = (info != null && info.timerTime != null) ? info.timerTime / 1000 : 0;
        var elapsed = (seconds / 60).format("%d") + ":" + (seconds % 60).format("%02d");

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.10, Graphics.FONT_XTINY, "vitruvian-" + (recorder.lifter.length() > 0 ? " - " + recorder.lifter : ""), Graphics.TEXT_JUSTIFY_CENTER);
        dc.setColor(Graphics.COLOR_RED, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.20, Graphics.FONT_NUMBER_MEDIUM, hr, Graphics.TEXT_JUSTIFY_CENTER);
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.42, Graphics.FONT_XTINY, "bpm  -  " + elapsed, Graphics.TEXT_JUSTIFY_CENTER);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.52, Graphics.FONT_SMALL, recorder.status, Graphics.TEXT_JUSTIFY_CENTER);
        if (recorder.lastSet.length() > 0) {
            dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, h * 0.64, Graphics.FONT_XTINY, recorder.lastSet, Graphics.TEXT_JUSTIFY_CENTER);
        }
    }
}
