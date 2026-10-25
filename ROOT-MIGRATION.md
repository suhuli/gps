# Root migration tracking — NOT a completed full Portal port

## Baselines

- Stable `main` remains 2.6.0-beta4 / 262, unchanged.
- `test`: 2.7.0-root-beta1 / 264; minimum API 26, existing package and signing identity.
- Portal pinned to e6457225f0cb057f66b231ad7a22ae94b5fccf03, Apache-2.0 attribution in third_party/portal.
- Source folders `portal-xposed`, `portal-system-api`, `portal-nmea` contain vendored code. Source presence does **not** mean a feature has been activated, UI-ported or device-validated.

## Milestone 1 implemented; signed CI passed, device validation outstanding

- Bundled LSPosed entry point, `android` scope, module metadata; Root status/settings screen.
- Portal control protocol adapter, handshake version 2 (rejects an old module until reboot), exact controller package + fixed certificate authentication on every command.
- Existing fixed-point, route/file-route, speed adjustment, floating controls and location adjustment connected to the new publisher.
- Removed legacy addTestProvider / setTestProviderLocation injection from GpsService; no silent fallback.
- Dedicated publisher thread and periodic callback command, independent of coordinate jitter interval. WGS84 and SI units at boundary.
- Pause holds a stationary sample; stop disables engine. Binder owner death and a 15-second awake-time heartbeat lease disable simulation.
- Fresh timestamps; no forced nonzero speed; automatic engine jitter disabled. Existing explicitly configured UI jitter remains supported.
- Existing two-map selection, search, reverse geocoding, route planning, local location history, input model formats, privacy flow, fixed certificate, icon and UI style retained.
- Main/test dispatch isolation and artifact naming; main workflow/source not changed by this branch.

## Full feature migration checklist

| Capability | Source imported | Connected/UI | Runtime verified |
|---|---|---|---|
| System location hooks | yes | first control adapter | no device |
| Fixed position, callback loop | yes | existing UI + Root settings | no device |
| Existing host route/file-route/speed/floating adjustment | host retained | new backend connected | no device |
| Portal rocker/auto movement variants | host motion implementation | direction/speed rocker + automatic direction lock; original floating panel includes continuous/lock modes | no device |
| Portal route editor/history/route management UI | host implementation | private route library, edit/order points, map picker, reverse, JSON import/export, existing route preview | no device |
| Altitude, accuracy, callback interval | yes | initial settings | no device |
| GNSS/NMEA/satellite panel | hooks + wire rewriter | lifecycle-bound observer, GNSS/NMEA options, checksum/coordinates fixes | no device |
| Vendor/fused paths | source imported and adapted | AOSP/Xiaomi/Oplus scope + read-only peer snapshots, class matching reports | no device |
| Native sensor module | safe typed NDK implementation | bundled ARM64/ARMv7, 16KB linking, guarded loader and native lease | no device |
| Wi-Fi/telephony integrations | safe response/callback wrappers | optional runtime flags, platform checks retained, read-only peers | no device |
| Portal settings and diagnostics | inventory completed | mapped settings and capability reports; deliberate behavior differences documented | no device |

No hiding of mock markers or automatic cellular downgrade is enabled. Arbitrary remote library loading is disabled. Unfinished/high-impact upstream options are not presented as working customer features.

## Follow-up source implementation (not a full-port delivery)

- Atomic app-private route storage, UUID-only filenames, explicit coordinate systems, finite coordinate/size/schema checks; reuse the existing dual-map picker and navigation preview. Original GPX/KML/CSV import remains unchanged.
- Direction rocker, touch-strength speed control, explicit automatic direction lock. No movement in route mode; pause/stop/service destruction clear motion. Coordinates advance by uptime deltas, capped after scheduling gaps.
- Route endpoint options: stationary hold, stop/recover, connected closed loop, or reverse/ping-pong. Navigation owns a private route copy so reversing does not mutate the map's shared path.
- GNSS/NMEA observation registers only while the page is visible, requests precise location, bounds in-memory sentence history, and unregisters on stop. No observation log is persisted.
- Typed session option transfer, bounded satellite counts, explicit callback-loop switch, and stop/death/watchdog option reset. API suppression gates also require an active simulation.
- NMEA GGA/GNS/RMC/GLL/VTG rewriting preserves talker IDs, uses absolute degrees/minutes plus hemispheres, converts SI speed, uses US numeric formatting and rebuilds checksums. Invalid checksums/fixes and unsupported sentences are not rewritten.
- Preserve normal GNSS/NMEA listener registration/removal, install satellite callback wrappers independently of the current toggle, and gate modifications at callback time.
- Local debug/release Kotlin compilation, 69 JVM tests (0 failures) and Release Lint Vital passed. Source guardrails and whitespace checks passed. The test source checkpoint has been pushed. Final signed CI evidence is recorded separately in the delivery report; no device runtime validation is claimed. The previously delivered alpha1 APK does not contain these follow-up changes.

Follow-up native and optional process integration has now been implemented and built locally. Standalone GNSS status uses its own foreground service without publishing a GPS coordinate. Additional peer policy and Portal route import tests were added. See `PORTAL-PARITY.md` for the complete mapping and deliberate deviations; in particular there is no SELinux-disabling shell feature, arbitrary library loader, hidden mock marker or unsafe subscription Parcel rewriting.

The next gate is the fixed-signature remote CI for the final source tree, followed by Root device acceptance. No generic Android 7–15 or vendor compatibility claim is made.

## Callback permission protections

The first registration hooks were tightened after review: track listeners only after successful platform registration, preserve normal platform registrations for stop/recovery, recheck current runtime permissions/AppOps on manual callbacks, and keep coarse-only recipients approximate. Sleeping time does not consume the heartbeat lease. These safeguards require device tests alongside the upstream hook behavior.

## Acceptance gates

1. CI unit tests including RootFrame boundaries; release lint; signed APK identity; module metadata; original SDK native alignment.
2. User test device: enable module for Android system framework and reboot after install/update. Confirm handshake, fixed point, callbacks, routes, pause/hold, stop and recovery.
3. Verify app death, service failure, screen off, permissions revoked, OS location state changes, target app callback behavior.
4. Complete remaining parity items in this list before describing the root edition as a complete migration.

Same applicationId means this APK upgrades/replaces the stable installation, not side-by-side. Back up routes first; normal version downgrade is not guaranteed. A separate side-by-side ID would require separately registered map keys. Root and LSPosed support must be verified on specific models; successful compilation is not runtime compatibility.

## Licensing

Portal's Apache-2.0 license does not supersede the original host project's existing distribution/commercial-use restrictions. Confirm authorization before customer distribution.
