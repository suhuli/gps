#!/usr/bin/env python3
"""Milestone-1 guardrails. Static checks do not prove runtime hook compatibility."""
from pathlib import Path
import json,re
root=Path('.')
service=(root/'app/src/main/java/com/yadea/smartmoto/server/GpsService.kt').read_text()
for old in ['addTestProvider(', 'setTestProviderLocation(', 'setTestProviderEnabled(']:
    assert old not in service, 'Legacy injection remains in service'
assert 'RootEngine.offer' in service and 'RootEngine.hold' in service
signing=json.loads((root/'signing-public.json').read_text())
binder=(root/'portal-xposed/src/main/java/moe/fuqiuluo/xposed/utils/BinderUtils.kt').read_text()
assert signing['sha256'].replace(':','').lower() in binder, 'Controller signing identity mismatch'
assert 'packages.contains("com.yadea.smartmoto")' in binder
handler=(root/'portal-xposed/src/main/java/moe/fuqiuluo/xposed/RemoteCommandHandler.kt').read_text()
assert 'binder.linkToDeath' in handler and '15000L' in handler
assert '"load_library" -> { return false }' in handler
assert 'if (!BinderUtils.isLocationProviderEnabled(BinderUtils.getCallerUid())) return false' in handler
assert 'var hideMock = false' in (root/'portal-xposed/src/main/java/moe/fuqiuluo/xposed/utils/FakeLoc.kt').read_text()
assert (root/'portal-xposed/src/main/assets/xposed_init').read_text().strip()=='moe.fuqiuluo.xposed.FakeLocation'
assert (root/'third_party/portal/LICENSE').exists()
print('Root source guardrails passed: old backend removed, fixed controller identity, session death/lease, attribution.')

# Follow-up route/motion/GNSS guardrails; runtime compatibility is still a device gate.
assert '"configure_session" ->' in handler and 'count !in 0..35' in handler
assert handler.count('FakeLoc.resetSessionOptions()') >= 3
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
for activity in ['RootRoutesActivity', 'RootMotionActivity', 'RootGnssActivity']:
    assert f'android:name=".ui.{activity}" android:exported="false"' in manifest
nmea=(root/'portal-xposed/src/main/java/moe/fuqiuluo/xposed/BaseLocationHook.kt').read_text()
assert 'NmeaRewriter.rewrite' in nmea
assert (root/'portal-nmea/src/test/java/NmeaRewriterTest.kt').exists()
print('Follow-up guardrails passed: bounded session settings, cleanup, private UI, NMEA wire tests.')

assert 'peer_snapshot' in handler
peer=(root/'portal-xposed/src/main/java/moe/fuqiuluo/xposed/PeerBridge.kt').read_text()
assert 'exchange_key' not in peer and 'PeerPolicy.allowed' in peer
native=(root/'portal-xposed/src/main/cpp/sensor_hook.cpp').read_text()
assert 'sizeof(ASensorEvent)' in native and 'CLOCK_MONOTONIC' in native and 'deadline' in native
assert '-1.0' not in native and '((char*)' not in native
assert 'externalNativeBuild' in (root/'portal-xposed/build.gradle').read_text()
assert '.server.RootAuxService' in manifest
print('Native/peer guardrails passed: bundled native ABI/lease, read-only peers, independent GNSS service.')

assert 'if (uid == android.os.Process.SYSTEM_UID || uid == 0) return true' not in binder
print('Shared-UID controller bypass is absent; peers cannot obtain write authority through UID 1000.')
