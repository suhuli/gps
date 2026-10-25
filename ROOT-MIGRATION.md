# Root migration tracking — NOT a completed full Portal port

## Baselines

- Stable `main` remains 2.6.0-beta4 / 262, unchanged.
- `test`: 2.7.0-root-alpha1 / 263; minimum API 26, existing package and signing identity.
- Portal pinned to e6457225f0cb057f66b231ad7a22ae94b5fccf03, Apache-2.0 attribution in third_party/portal.
- Source folders `portal-xposed`, `portal-system-api`, `portal-nmea` contain vendored code. Source presence does **not** mean a feature has been activated, UI-ported or device-validated.

## Milestone 1 implemented, awaiting CI and device validation

- Bundled LSPosed entry point, `android` scope, module metadata; Root status/settings screen.
- Portal control protocol adapter, handshake version 1, exact controller package + fixed certificate authentication on every command.
- Existing fixed-point, route/file-route, speed adjustment, floating controls and location adjustment connected to the new publisher.
- Removed legacy addTestProvider / setTestProviderLocation injection from GpsService; no silent fallback.
- Dedicated publisher thread and periodic callback command, independent of coordinate jitter interval. WGS84 and SI units at boundary.
- Pause holds a stationary sample; stop disables engine. Binder owner death and a 15-second heartbeat lease disable simulation.
- Fresh timestamps; no forced nonzero speed; automatic engine jitter disabled. Existing explicitly configured UI jitter remains supported.
- Existing two-map selection, search, reverse geocoding, route planning, local location history, input model formats, privacy flow, fixed certificate, icon and UI style retained.
- Main/test dispatch isolation and artifact naming; main workflow/source not changed by this branch.

## Full feature migration checklist

| Capability | Source imported | Connected/UI | Runtime verified |
|---|---|---|---|
| System location hooks | yes | first control adapter | no device |
| Fixed position, callback loop | yes | existing UI + Root settings | no device |
| Existing host route/file-route/speed/floating adjustment | host retained | new backend connected | no device |
| Portal rocker/auto movement variants | yes in upstream review, not host UI | pending parity review | no |
| Portal route editor/history/route management UI | upstream review only | pending | no |
| Altitude, accuracy, callback interval | yes | initial settings | no device |
| GNSS/NMEA/satellite panel | hook/parser source imported | parameter/UI migration pending | no |
| Vendor/fused paths | source imported | only first system integration | no |
| Native sensor module | source retained, not built or loaded | pending separate milestone | no |
| Wi-Fi/telephony integrations | source retained, not initialized | pending scope/compatibility review | no |
| All Portal settings and diagnostics | inventory started | partial | no |

No hiding of mock markers or automatic cellular downgrade is enabled. Arbitrary remote library loading is disabled. Unfinished/high-impact upstream options are not presented as working customer features.

## Acceptance gates

1. CI unit tests including RootFrame boundaries; release lint; signed APK identity; module metadata; original SDK native alignment.
2. User test device: enable module for Android system framework and reboot after install/update. Confirm handshake, fixed point, callbacks, routes, pause/hold, stop and recovery.
3. Verify app death, service failure, screen off, permissions revoked, OS location state changes, target app callback behavior.
4. Complete remaining parity items in this list before describing the root edition as a complete migration.

Same applicationId means this APK upgrades/replaces the stable installation, not side-by-side. Back up routes first; normal version downgrade is not guaranteed. A separate side-by-side ID would require separately registered map keys. Root and LSPosed support must be verified on specific models; successful compilation is not runtime compatibility.

## Licensing

Portal's Apache-2.0 license does not supersede the original host project's existing distribution/commercial-use restrictions. Confirm authorization before customer distribution.
