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
