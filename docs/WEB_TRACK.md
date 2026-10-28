# Web track: vitruvian- as an installable web app (beta)

Goal: the real app (not the demo) running in the browser, installable to the home
screen on iPhone, iPad and Android, looking and behaving exactly like the Android
app. One codebase, so every change lands in both at the same time.

Branch: `web-beta` (long-lived). Each phase is one PR into `web-beta`, merged when
CI is green. `web-beta` goes to `master` only after the beta checklist (phase 8)
passes and Niels says go.

## Status

- [x] Plan (this file)
- [x] Decision: Apple + Bluetooth: option 1, full + Safari fallback (Niels, 2026-10-28)
- [x] Phase 1: clean-ui is multiplatform; all screens, logic, models and the gateway are shared and tested on Android + web (app unchanged)
- [x] Phase 2: the real app in the browser (profiles, history, settings, exercise library),
  the trainer and heart rate straps over Web Bluetooth with the app's own protocol code, and a
  simulated trainer where the browser has no Bluetooth. Still to check: one workout on the real
  trainer in Chrome on Android (Niels).
- [ ] Phase 3: PWA install (home-screen intro, offline, lag disclaimer)
- [ ] Phase 4: responsive layout (phone, tablet, iPad; also in the app)
- [ ] Phase 5: anonymous usage statistics + stats view
- [ ] Phase 6: security hardening
- [ ] Phase 7: beta hosting on GitHub Pages
- [ ] Phase 8: beta test round on real devices, then merge to master

## Feasibility first: Bluetooth on Apple

The trainer (and BLE heart rate) connect over Bluetooth. Browsers do that with
**Web Bluetooth**, which works in Chrome/Edge on Android, Windows, macOS and
ChromeOS, but **not in Safari, and not in any browser on iPhone/iPad** (Apple forces
all iOS browsers onto WebKit, which has no Web Bluetooth). A web app added to the
iOS home screen runs on that same WebKit, so it cannot reach the trainer either.

The one exception on iOS is **Bluefy**, a free App Store browser that adds Web
Bluetooth on top of WebKit. It can run the web app with the trainer connected, but
the app then lives inside Bluefy, not as its own home-screen icon.

Options:

1. **Chosen (2026-10-28): full web where the browser allows it, clear fallback elsewhere.**
   Chrome/Edge (Android, desktop) and Bluefy (iOS) get everything. Safari / iOS
   home screen gets the whole app (profiles, plans, 1RMs, history, analytics,
   settings) plus a simulated trainer, with a clear notice: "To train with your
   trainer on iPhone/iPad, open this page in Bluefy." The Android app does not
   change, because the feature *is* possible on the web, just not in Safari.
2. **Simulated trainer only on all web.** Simpler, but the web is then a tester's
   preview, not an alternative to the app.
3. **Native iOS app later.** Phase 1 makes an iOS target possible from the same
   code, but distribution needs an Apple developer account (99 USD/year) and
   TestFlight. Can be added after the beta; not part of this track.

Phases 1, 3, 4, 5 and 6 do not depend on this choice. Phase 2 does.

## Architecture: one source of truth

Today `:clean-ui` is an Android library (about 8,900 lines of Kotlin/Compose) that
only talks to the trainer through `TrainerGateway`; `:app` implements that gateway
with the upstream Android BLE code. That split is what makes the web possible.

Target layout:

```
:clean-ui   Kotlin Multiplatform + Compose Multiplatform
  commonMain   all screens, ViewModel, logic, models, TrainerGateway  <- shared
  androidMain  Android bits (DataStore, ExoPlayer, sounds)
  wasmJsMain   web bits (localStorage/IndexedDB, <video>, Web Audio)
:app        Android app, unchanged role: implements TrainerGateway with BLE
:web-app    wasmJs executable: implements TrainerGateway with Web Bluetooth,
            hosts CleanApp(), PWA manifest + service worker
```

Swaps needed for phase 1 (each replaces an Android-only dependency with its
multiplatform equivalent, so the Android app keeps working the same):

| Today (Android only) | Multiplatform replacement |
| --- | --- |
| androidx Compose | JetBrains Compose Multiplatform (same API) |
| navigation-compose, lifecycle-viewmodel | JetBrains multiplatform builds of the same libraries |
| DataStore preferences (`CleanUiStore`) | `expect`/`actual` key-value store: DataStore on Android, localStorage on web |
| Coil 2 | Coil 3 (multiplatform) |
| media3 ExoPlayer (`DemoVideo`) | `expect`/`actual` player: ExoPlayer on Android, HTML `<video>` on web |
| `android.*` imports (5 files) | `expect`/`actual` or moved to androidMain |
| `org.json` in tests | kotlinx-serialization |

Trainer protocol for the web: the packet building and parsing the Android BLE code
uses will be ported into common Kotlin so Android and web speak to the trainer with
the same code. Only port from this repository's MIT code; never copy from
Phoenix-MP (proprietary licence).

Parity rules (apply from phase 1 onward, for every thread):

- New UI or logic goes in `clean-ui/commonMain`. Platform code only behind
  `expect`/`actual` or `TrainerGateway`.
- Anything that cannot work on the web is either hidden behind a capability flag
  with the same fallback in the app, or changed in both. No feature exists on one
  platform only, except install/browser notices that only make sense on the web.
- `clean-ui/FEATURES.md` gets a "Web" column next to "Android".
- CI builds and tests both targets on every PR; a PR that breaks either is red.

## Phases

Each phase is sized for one thread and one PR, with its own test list.

**Phase 1: shared code.** Convert `:clean-ui` to KMP with `androidTarget` and
`wasmJs`, move code to `commonMain`, do the swaps above. Done when the APK behaves
exactly as before (same screens, same stored data, existing unit tests pass in
`commonTest` on JVM and wasm) and CI runs `:clean-ui:allTests` plus a wasm compile.

**Phase 2: web app + trainer.** New `:web-app` module rendering `CleanApp()` in a
full-page canvas. `WebTrainerGateway` over Web Bluetooth (connect needs a tap, as
browsers require), BLE heart rate the same way, and the simulated trainer for
browsers without Web Bluetooth (per the decision above). Done when a workout runs
end to end in Chrome on Android against the real trainer.

**Phase 3: PWA install.** Web app manifest, icons, service worker for offline use
(app shell and data cached; it is an offline app, no server). First-launch intro on
the web only: what the web version is, that it can lag compared to the app, and
step-by-step "add to home screen" for iPhone/iPad (Share > Add to Home Screen) and
Android (Chrome menu > Install app / native prompt). The install guide must make
the Apple split unmistakable, both in the intro and in the README/project guide:
"Want to train with your trainer on iPhone/iPad? Install Bluefy (free) and open the
link there. Safari / home screen = everything except the trainer and heart-rate
connection (simulated trainer)." The intro detects the browser and shows only the
steps that apply, with a button that opens the same link in Bluefy. Data backup: export/import of
the local data as a file, since browsers can clear storage.

**Phase 4: responsive layout.** Window size classes in common code: phone (bottom
tab bar as today), tablet/iPad (navigation rail, two-pane where it helps, e.g.
exercise list + set setup), landscape, and a max content width on desktop. Because
it is shared code, Android tablets get the same layout. Done when screenshots at
360, 768, 1024 and 1366 px wide look right in both orientations.

**Phase 5: anonymous statistics.** Cookieless, no personal data, no IDs, no IP
storage. Proposed: GoatCounter (open source, free, no cookies, can be self-hosted)
on the web app only, with a few events: app opened, opened as installed app or in a
browser, device class (phone/tablet/desktop), browser family, workout finished,
trainer connect success/fail. The existing `docs/stats` page gets a "Web" section
with these counts next to the APK download counts. The Android app keeps download
counts only unless Niels wants the same events there. Note: the demo page stays
untracked, as decided on 2026-10-26.

**Phase 6: security.**
- HTTPS only (GitHub Pages enforces it); Web Bluetooth also requires it.
- Content-Security-Policy via `<meta>` (Pages cannot set headers): `default-src
  'self'`, `wasm-unsafe-eval` for Wasm, `connect-src` only for the stats endpoint,
  no inline scripts, no third-party scripts.
- No secrets or tokens in the client; nothing to sign in to.
- Dependabot (Gradle + GitHub Actions), dependency-review on PRs, CodeQL for
  Kotlin and Actions, secret scanning with push protection.
- Service worker limited to the app's own path; a versioned cache so updates roll
  out cleanly.
- Data stays on the device (localStorage/IndexedDB); the privacy note says so.

**Phase 7: beta hosting.** GitHub Pages switches from "deploy from /docs" to a
Pages workflow that publishes `docs/` from master as today plus the web build at
`/Cable-4-Vitruvian/app/` (beta label, built from `web-beta`). Niels changes one
Pages setting (Source: GitHub Actions) when this phase lands; the demo and stats
links keep working.

**Phase 8: beta round.** Real-device checklist: iPhone Safari + home screen, iPad,
Bluefy, Android Chrome + installed, Android tablet, desktop Chrome. Bugs get
fixed in shared code. When it passes, `web-beta` merges into `master`, and from
then on every master release builds the APK and the web app together.

## Tests (mirroring the app)

- Logic: existing unit tests move to `commonTest` and run on JVM and wasm.
- UI: Compose Multiplatform UI tests (`runComposeUiTest`) for the main flows
  (onboarding, set setup, live workout with a fake gateway, 1RM test), on both
  targets.
- Web smoke test: Playwright against the built web app with the simulated trainer
  (load, onboarding, start and finish a set, reload keeps data, offline reload).
- CI on every PR into `web-beta`: Android build + tests, wasm build + tests,
  Playwright smoke. One combined required check.

## Working on this track cheaply

- One phase per thread, one PR per phase into `web-beta` (phases are not split further);
  tick the box above when it merges.
- Start a phase by saying "start web phase N" in the project chat.
- Master keeps getting normal app releases meanwhile; `web-beta` merges master in
  at the start of each phase.
