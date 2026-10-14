import Toybox.Application;
import Toybox.BluetoothLowEnergy;
import Toybox.Lang;
import Toybox.Math;
import Toybox.StringUtil;
import Toybox.WatchUi;

// Direct Bluetooth link to the vitruvian- tablet, so the watch can stay paired to a phone.
// The tablet advertises SERVICE; the watch connects, subscribes to TO_WATCH and writes to
// TO_TABLET. Messages are text lines ("type|field|...\n") split in 20-byte pieces.
// Must match BleWatchServer in :app.
class TabletLink extends BluetoothLowEnergy.BleDelegate {
    var SERVICE = BluetoothLowEnergy.stringToUuid("7a1e0001-5c4b-4d2e-9f3a-2b6c8d9e0f10");
    var TO_WATCH = BluetoothLowEnergy.stringToUuid("7a1e0002-5c4b-4d2e-9f3a-2b6c8d9e0f10");
    var TO_TABLET = BluetoothLowEnergy.stringToUuid("7a1e0003-5c4b-4d2e-9f3a-2b6c8d9e0f10");
    const CHUNK = 20;

    var recorder;
    var device = null;
    var connected = false;
    var inbox = []b;
    var outbox = [];
    var writing = false;

    function initialize(rec) {
        BleDelegate.initialize();
        recorder = rec;
    }

    function start() {
        BluetoothLowEnergy.setDelegate(self);
        BluetoothLowEnergy.registerProfile({
            :uuid => SERVICE,
            :characteristics => [
                {:uuid => TO_WATCH, :descriptors => [BluetoothLowEnergy.cccdUuid()]},
                {:uuid => TO_TABLET}
            ]
        });
    }

    function stop() {
        BluetoothLowEnergy.setScanState(BluetoothLowEnergy.SCAN_STATE_OFF);
        if (device != null) {
            BluetoothLowEnergy.unpairDevice(device);
            device = null;
        }
        connected = false;
    }

    function onProfileRegister(uuid, status) as Void {
        BluetoothLowEnergy.setScanState(BluetoothLowEnergy.SCAN_STATE_SCANNING);
    }

    function onScanResults(results) as Void {
        if (device != null) {
            return;
        }
        for (var next = results.next(); next != null; next = results.next()) {
            var r = next as BluetoothLowEnergy.ScanResult;
            var uuids = r.getServiceUuids();
            for (var u = uuids.next(); u != null; u = uuids.next()) {
                if (u.equals(SERVICE)) {
                    BluetoothLowEnergy.setScanState(BluetoothLowEnergy.SCAN_STATE_OFF);
                    device = BluetoothLowEnergy.pairDevice(r);
                    return;
                }
            }
        }
    }

    function onConnectedStateChanged(dev, state) as Void {
        if (state == BluetoothLowEnergy.CONNECTION_STATE_CONNECTED) {
            var ch = characteristic(TO_WATCH);
            if (ch != null) {
                ch.getDescriptor(BluetoothLowEnergy.cccdUuid()).requestWrite([0x01, 0x00]b);
            }
        } else {
            // Gone (out of range, tablet app closed): look for the tablet again.
            connected = false;
            writing = false;
            inbox = []b;
            if (device != null) {
                BluetoothLowEnergy.unpairDevice(device);
                device = null;
            }
            BluetoothLowEnergy.setScanState(BluetoothLowEnergy.SCAN_STATE_SCANNING);
        }
        WatchUi.requestUpdate();
    }

    function onDescriptorWrite(descriptor, status) as Void {
        connected = true;
        outbox = [];
        writing = false;
        // Tells the tablet who this is; it then sends what it has for us.
        send("hi|" + watchId() + "|Venu 4");
        WatchUi.requestUpdate();
    }

    function onCharacteristicChanged(ch, value) as Void {
        for (var i = 0; i < value.size(); i++) {
            if (value[i] == 0x0A) {
                var line = StringUtil.convertEncodedString(inbox, {
                    :fromRepresentation => StringUtil.REPRESENTATION_BYTE_ARRAY,
                    :toRepresentation => StringUtil.REPRESENTATION_STRING_PLAIN_TEXT
                });
                inbox = []b;
                recorder.handle(parse(line));
            } else {
                inbox.add(value[i]);
            }
        }
    }

    function onCharacteristicWrite(ch, status) as Void {
        writing = false;
        writeNext();
    }

    // Sends one line to the tablet, if connected.
    function send(line) {
        if (!connected) {
            return;
        }
        var bytes = StringUtil.convertEncodedString(line + "\n", {
            :fromRepresentation => StringUtil.REPRESENTATION_STRING_PLAIN_TEXT,
            :toRepresentation => StringUtil.REPRESENTATION_BYTE_ARRAY
        });
        for (var i = 0; i < bytes.size(); i += CHUNK) {
            var end = i + CHUNK < bytes.size() ? i + CHUNK : bytes.size();
            outbox.add(bytes.slice(i, end));
        }
        writeNext();
    }

    function writeNext() {
        if (writing || outbox.size() == 0) {
            return;
        }
        var ch = characteristic(TO_TABLET);
        if (ch == null) {
            outbox = [];
            return;
        }
        var piece = outbox[0];
        outbox = outbox.slice(1, null);
        writing = true;
        ch.requestWrite(piece, {:writeType => BluetoothLowEnergy.WRITE_TYPE_WITH_RESPONSE});
    }

    function characteristic(uuid) {
        if (device == null) {
            return null;
        }
        var service = device.getService(SERVICE);
        return service != null ? service.getCharacteristic(uuid) : null;
    }

    // "set|tr|who|id|cat|reps|kg|exercise" -> the dictionary Recorder.handle expects.
    function parse(line) {
        var f = split(line);
        var data = {"t" => f[0]};
        var names = ["tr", "who", "id", "cat", "reps", "kg", "ex"];
        for (var i = 0; i < names.size() && i + 1 < f.size(); i++) {
            if (f[i + 1].length() > 0) {
                data.put(names[i], f[i + 1]);
            }
        }
        return data;
    }

    function split(line) {
        var parts = [];
        var rest = line;
        var at = rest.find("|");
        while (at != null) {
            parts.add(rest.substring(0, at));
            rest = rest.substring(at + 1, rest.length());
            at = rest.find("|");
        }
        parts.add(rest);
        return parts;
    }

    // A random id kept on the watch, so the tablet remembers which lifter it belongs to.
    function watchId() {
        var id = Application.Storage.getValue("watchId");
        if (id == null) {
            id = (Math.rand() & 0x7FFFFFFF).format("%08x");
            Application.Storage.setValue("watchId", id);
        }
        return id;
    }
}
