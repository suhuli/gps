#!/usr/bin/env python3
"""Check ARM64 ELF LOAD alignment and APK ZIP alignment. No native execution needed."""
import os
from pathlib import Path
import struct
import subprocess
import zipfile

variant = os.environ.get('BUILD_TYPE', 'release')
apks = list(Path(f'app/build/outputs/apk/{variant}').glob('*.apk'))
if len(apks) != 1:
    raise SystemExit('Expected one APK')
failed = []
checked = []
with zipfile.ZipFile(apks[0]) as archive:
    for name in archive.namelist():
        if not name.startswith('lib/arm64-v8a/') or not name.endswith('.so'):
            continue
        data = archive.read(name)
        if data[:5] != b'\x7fELF\x02' or data[5] != 1:
            failed.append(name + ': unexpected ELF format')
            continue
        phoff = struct.unpack_from('<Q', data, 32)[0]
        entsize, count = struct.unpack_from('<HH', data, 54)
        alignments = []
        for index in range(count):
            offset = phoff + index * entsize
            kind = struct.unpack_from('<I', data, offset)[0]
            if kind == 1:
                alignment = struct.unpack_from('<Q', data, offset + 48)[0]
                alignments.append(alignment)
                if alignment < 16384:
                    failed.append(name + ': LOAD alignment ' + str(alignment))
        if not alignments:
            failed.append(name + ': no LOAD segments')
        checked.append(name)
        print(name, alignments)
if not checked:
    raise SystemExit('No ARM64 native libraries found')
if failed:
    raise SystemExit('Native 16 KB alignment check failed:\n' + '\n'.join(failed))
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
subprocess.run([str(tools/'zipalign'), '-c', '-P', '16', '4', str(apks[0])], check=True)
print('PASS: ARM64 ELF and APK ZIP alignment; device execution is still required.')
