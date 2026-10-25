#!/usr/bin/env python3
import os
from pathlib import Path
import re
import subprocess
import zipfile

variant = os.environ['BUILD_TYPE']
apks = list(Path(f'app/build/outputs/apk/{variant}').glob('*.apk'))
if len(apks) != 1:
    raise SystemExit(f'Expected one installable APK, found {len(apks)}')
apk = apks[0]
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
signing = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
match = re.search(r"^package: name='([^']+)'", badging, re.M)
if not match or match.group(1) != os.environ['APPLICATION_ID']:
    raise SystemExit('Built APK package does not match application_id input')
label = re.search(r"^application-label:'([^']*)'", badging, re.M)
if not label or label.group(1) != os.environ.get('APP_NAME', '雅迪智行'):
    raise SystemExit('Built APK display name does not match 雅迪智行')
sha1 = re.search(r'Signer #1 certificate SHA-1 digest:\s*([0-9a-fA-F]+)', signing)
expected = os.environ['EXPECTED_SIGNING_SHA1'].replace(':', '').lower()
if not sha1 or sha1.group(1).lower() != expected:
    raise SystemExit('APK signing certificate does not match the fixed project certificate')

# Validate the compiled result, not just Gradle's applicationId setting.
manifest = subprocess.check_output([str(tools / 'aapt'), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'], text=True)
legacy = '.'.join(('com', 'huolala', 'mockgps'))
if legacy in manifest:
    raise SystemExit('Legacy application component namespace found in compiled Manifest')
for key_name, env_name in [('com.baidu.lbsapi.API_KEY', 'BAIDU_MAP_KEY'), ('com.amap.api.v2.apikey', 'AMAP_MAP_KEY')]:
    expected_key = os.environ.get(env_name)
    config_name = 'map_key' if env_name == 'BAIDU_MAP_KEY' else 'amap_key'
    configured = re.search(r"(?m)^\s*" + config_name + r"\s*:\s*'([^']*)'", Path('config.gradle').read_text())
    if configured:
        expected_key = configured.group(1)
    blocks = re.split(r'\n\s*E: ', manifest)
    matching = [block for block in blocks if key_name in block]
    if not expected_key or not matching or (expected_key and not any('"' + expected_key + '"' in block for block in matching)):
        raise SystemExit('Compiled map credential does not match the requested build configuration: ' + env_name)
with zipfile.ZipFile(apk) as archive:
    if os.environ['SOURCE_BRANCH'] == 'test':
        if 'xposedmodule' not in manifest or 'moe.fuqiuluo.xposed.FakeLocation' not in archive.read('assets/xposed_init').decode():
            raise SystemExit('Root module metadata/entry point missing from built APK')
        for abi in ['armeabi-v7a', 'arm64-v8a']:
            if f'lib/{abi}/libportal.so' not in archive.namelist():
                raise SystemExit('Bundled Portal native library missing: '+abi)

    forbidden = [legacy.encode(), legacy.replace('.', '/').encode(), b'www.' + b'pgyer.com', b'/apiv2/app/' + b'check']
    for entry in archive.namelist():
        if re.fullmatch(r'classes\d*\.dex', entry):
            contents = archive.read(entry)
            if any(token in contents for token in forbidden):
                raise SystemExit('Retired updater or legacy application namespace found in compiled DEX')
print('Compiled Manifest, application namespace, retired-channel scan and both map credential checks passed.')

out = Path('artifacts')
out.mkdir(exist_ok=True)
source_slug = os.environ['SOURCE_BRANCH'].replace('/', '-')
name = f"yadea-smartmoto-{os.environ['APPLICATION_ID']}-{source_slug}-{variant}.apk"
if os.environ.get("COPY_APK_ARTIFACT", "true").lower() == "true":
    (out / name).write_bytes(apk.read_bytes())
(out / 'signing-certificate.txt').write_text(signing)
info = '\n'.join([
    'App name: ' + os.environ.get('APP_NAME', '雅迪智行'),
    'Package: ' + os.environ['APPLICATION_ID'],
    'Source branch: ' + os.environ['SOURCE_BRANCH'],
    'Build type: ' + variant,
    'Signing SHA-1: ' + os.environ['EXPECTED_SIGNING_SHA1'],
    next(line for line in badging.splitlines() if line.startswith('package:')),
    'Baidu and AMap each require an Android Key registered for this package and certificate. Native SDKs: Baidu Map/Search 8.2.0.2, Location 9.7.0; AMap Map 11.3.100 / Location 11.3.101 / Search 9.8.1.',
]) + '\n'
(out / 'BUILD-INFO.txt').write_text(info)
print(info)
print(signing)
with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
    summary.write('## APK build result\n\n```text\n' + info + '```\n')
    summary.write('\nDownload the APK and public certificate fingerprints from Artifacts. No private key or signing password is included.\n')
