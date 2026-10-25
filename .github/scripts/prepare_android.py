#!/usr/bin/env python3
"""Prepare a checked-out upstream source tree for a fixed-signature CI build."""
import base64
import json
import os
from pathlib import Path
import re

application_id = os.environ['APPLICATION_ID'].strip()
if os.environ.get('SOURCE_BRANCH') not in ('main', 'dev'):
    raise SystemExit('Unsupported source branch')
if len(application_id) > 200 or not re.fullmatch(r'[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+', application_id):
    raise SystemExit('application_id must be a lowercase dotted Android package name, for example com.yadea.smartmoto')
if os.environ['BUILD_TYPE'] not in ('debug', 'release'):
    raise SystemExit('Unsupported build type')
required = ('ANDROID_KEYSTORE_BASE64', 'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD')
if any(not os.environ.get(name) for name in required):
    raise SystemExit('Missing fixed-signing repository Secrets. No temporary signing fallback is allowed.')

key_path = Path(os.environ['RUNNER_TEMP']) / 'gps-signing.p12'
key_path.write_bytes(base64.b64decode(os.environ['ANDROID_KEYSTORE_BASE64'], validate=True))
key_path.chmod(0o600)
with open(os.environ['GITHUB_ENV'], 'a') as output:
    output.write(f'GPS_KEYSTORE_PATH={key_path}\n')

config = Path('config.gradle')
text, count = re.subn(r"(applicationId\s*:\s*)'[^']*'", lambda m: m.group(1) + repr(application_id), config.read_text())
if count != 1:
    raise SystemExit('Expected applicationId configuration was not found')
# Read the manual input from the event file instead of exposing it in step env output.
event = {}
if os.environ.get('GITHUB_EVENT_PATH'):
    event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
key = str(event.get('inputs', {}).get('baidu_map_key', '') or '').strip() or os.environ.get('BAIDU_MAP_KEY', '').strip()
if key:
    if not re.fullmatch(r'[A-Za-z0-9_-]+', key):
        raise SystemExit('Unexpected Baidu map key format')
    print('::add-mask::' + key, flush=True)
    text, count = re.subn(r"(map_key\s*:\s*)'[^']*'", lambda m: m.group(1) + repr(key), text)
    if count != 1:
        raise SystemExit('Expected Baidu map key configuration was not found')
else:
    print('WARNING: No map key input or BAIDU_MAP_KEY Secret. The upstream map key may not work with this package and certificate.')
config.write_text(text)

app_name = os.environ.get('APP_NAME', '雅迪智行')
if app_name != '雅迪智行':
    raise SystemExit('Unexpected app display name')
strings = Path('app/src/main/res/values/strings.xml')
labels, count = re.subn(r'(<string\s+name="app_name">)[^<]*(</string>)', lambda m: m.group(1) + app_name + m.group(2), strings.read_text())
if count != 1:
    raise SystemExit('Expected app_name resource was not found')
labels = re.sub(r'(<string\s+name="go_main">)[^<]*(</string>)', lambda m: m.group(1) + '进入' + app_name + m.group(2), labels)
strings.write_text(labels)

app = Path('app/build.gradle')
app.write_text(app.read_text() + '''
// CI-only override: keep the source namespace but use our own persistent signing identity.
android {
    signingConfigs {
        gpsCi {
            storeFile file(System.getenv("GPS_KEYSTORE_PATH"))
            storeType "PKCS12"
            storePassword System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias System.getenv("ANDROID_KEY_ALIAS")
            keyPassword System.getenv("ANDROID_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { signingConfig signingConfigs.gpsCi }
        release { signingConfig signingConfigs.gpsCi }
    }
}
''')

# This is only an on-screen path hint; runtime storage already uses Context paths.
expand = Path('app/src/main/java/com/huolala/mockgps/ui/ExpandActivity.kt')
if expand.exists():
    expand.write_text(expand.read_text().replace(
        '/Android/data/com.huolala.mockgps/files/nav_path',
        '/Android/data/${com.huolala.mockgps.BuildConfig.APPLICATION_ID}/files/nav_path'))
Path('gradlew').chmod(Path('gradlew').stat().st_mode | 0o111)
print('App display name:', app_name)
print('Prepared package:', application_id)
print('Build type:', os.environ['BUILD_TYPE'])
print('Signing: fixed repository identity; no original-author or ephemeral debug key fallback')
