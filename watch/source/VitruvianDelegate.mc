import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

// Back asks what to do with the training instead of quietly dropping it.
class VitruvianDelegate extends WatchUi.BehaviorDelegate {
    var recorder;

    function initialize(rec) {
        BehaviorDelegate.initialize();
        recorder = rec;
    }

    function onBack() {
        if (!recorder.isRecording()) {
            return false;
        }
        var menu = new WatchUi.Menu2({:title => "Training"});
        menu.addItem(new WatchUi.MenuItem("Save", "To Garmin Connect", :save, null));
        menu.addItem(new WatchUi.MenuItem("Resume", null, :resume, null));
        menu.addItem(new WatchUi.MenuItem("Discard", null, :discard, null));
        WatchUi.pushView(menu, new FinishMenuDelegate(recorder), WatchUi.SLIDE_UP);
        return true;
    }
}

class FinishMenuDelegate extends WatchUi.Menu2InputDelegate {
    var recorder;

    function initialize(rec) {
        Menu2InputDelegate.initialize();
        recorder = rec;
    }

    function onSelect(item) {
        var id = item.getId();
        if (id == :save) {
            recorder.save();
        } else if (id == :discard) {
            recorder.discard();
        }
        WatchUi.popView(WatchUi.SLIDE_DOWN);
    }
}
