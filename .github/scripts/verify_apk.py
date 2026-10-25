#!/usr/bin/env python3
import os
from pathlib import Path
import re
import subprocess

variant = os.environ['BUILD_TYPE']
apks = list(Path(f'app/build/outputs/apk/{variant}').glob('*.apk'))
if len(apks) != 1:
    raise SystemExit(f'Expected one installable APK, found {len(apks)}')
apk = apks[0]
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/30.0.3'
signing = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
match = re.search(r"^package: name='([^']+)'", badging, re.M)
if not match or match.group(1) != os.environ['APPLICATION_ID']:
    raise SystemExit('Built APK package does not match application_id input')
sha1 = re.search(r'Signer #1 certificate SHA-1 digest:\s*([0-9a-fA-F]+)', signing)
expected = os.environ['EXPECTED_SIGNING_SHA1'].replace(':', '').lower()
if not sha1 or sha1.group(1).lower() != expected:
    raise SystemExit('APK signing certificate does not match the fixed project certificate')

out = Path('artifacts')
out.mkdir(exist_ok=True)
name = f"gps-{os.environ['APPLICATION_ID']}-{os.environ['SOURCE_BRANCH']}-{variant}.apk"
(out / name).write_bytes(apk.read_bytes())
(out / 'signing-certificate.txt').write_text(signing)
info = '\n'.join([
    'Package: ' + os.environ['APPLICATION_ID'],
    'Source branch: ' + os.environ['SOURCE_BRANCH'],
    'Build type: ' + variant,
    'Signing SHA-1: ' + os.environ['EXPECTED_SIGNING_SHA1'],
    next(line for line in badging.splitlines() if line.startswith('package:')),
    'Map/search/routing require a valid Baidu Key registered for this package and certificate.',
]) + '\n'
(out / 'BUILD-INFO.txt').write_text(info)
print(info)
print(signing)
with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
    summary.write('## APK build result\n\n```text\n' + info + '```\n')
    summary.write('\nDownload the APK and public certificate fingerprints from Artifacts. No private key or signing password is included.\n')
