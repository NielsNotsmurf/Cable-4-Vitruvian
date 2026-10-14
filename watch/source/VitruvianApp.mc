import Toybox.Application;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.WatchUi;

// The tablet opens this app at the first set of a training (the watch asks once to
// allow it). From then on it records heart rate and the sets the tablet sends.
class VitruvianApp extends Application.AppBase {
    var recorder;

    function initialize() {
        AppBase.initialize();
        recorder = new Recorder();
    }

    function onStart(state) {
        recorder.start();
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
        // Asks the tablet for anything it sent before the app was open.
        recorder.sendToPhone({"t" => "ready"});
    }

    function onStop(state) {
        // Never lose a training: leaving the app any other way saves it.
        recorder.save();
    }

    function getInitialView() {
        return [new VitruvianView(recorder), new VitruvianDelegate(recorder)];
    }

    function onPhoneMessage(msg as Communications.PhoneAppMessage) as Void {
        if (msg.data instanceof Lang.Dictionary) {
            recorder.handle(msg.data);
        }
    }
}
