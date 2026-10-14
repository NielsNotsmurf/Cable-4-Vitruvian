import Toybox.Application;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.WatchUi;

// Open this app before training (or the tablet opens it at the first set when the watch is
// paired to the tablet). It records heart rate and the sets the tablet sends, either through
// Garmin Connect on the paired phone or directly over Bluetooth (TabletLink).
class VitruvianApp extends Application.AppBase {
    var recorder;
    var link;

    function initialize() {
        AppBase.initialize();
        recorder = new Recorder();
        link = new TabletLink(recorder);
        recorder.link = link;
    }

    function onStart(state) {
        recorder.start();
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
        link.start();
        // Asks the tablet for anything it sent before the app was open.
        recorder.sendToPhone({"t" => "ready"});
    }

    function onStop(state) {
        // Never lose a training: leaving the app any other way saves it.
        recorder.save();
        link.stop();
    }

    function getInitialView() {
        return [new VitruvianView(recorder, link), new VitruvianDelegate(recorder)];
    }

    function onPhoneMessage(msg as Communications.PhoneAppMessage) as Void {
        if (msg.data instanceof Lang.Dictionary) {
            recorder.handle(msg.data);
        }
    }
}
