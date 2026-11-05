#!/usr/bin/env python3
"""Assert packaged rules and required engine ABIs, rather than source data alone."""
import gzip, hashlib, sys, zipfile, subprocess, tempfile
from pathlib import Path
apk=Path(sys.argv[1] if len(sys.argv)>1 else 'systemblock/build/outputs/apk/debug/systemblock-debug.apk')
with zipfile.ZipFile(apk) as z:
 names=set(z.namelist())
 bundled=z.read('assets/blocklist.txt') if 'assets/blocklist.txt' in names else gzip.decompress(z.read('assets/blocklist.txt.gz'))
 expected=gzip.decompress(Path('systemblock/src/main/assets/blocklist.txt.gz').read_bytes())
 assert bundled==expected, 'Packaged domain rules differ from the committed bundle'
 for abi in ['arm64-v8a','armeabi-v7a','x86_64']:
  for lib in ['libubgo.so','libubvpn.so']:
   assert f'lib/{abi}/{lib}' in names, (abi,lib)
 # ELF dependencies must refer to packaged names, never the build machine path.
 with tempfile.TemporaryDirectory() as tmp:
  for abi in ['arm64-v8a','armeabi-v7a','x86_64']:
   for lib in ['libubgo.so','libubvpn.so']:
    path=Path(tmp)/lib;path.write_bytes(z.read(f'lib/{abi}/{lib}'))
    dynamic=subprocess.check_output(['readelf','-d',str(path)],text=True)
    for line in dynamic.splitlines():
     if '(NEEDED)' in line: assert '/' not in line.split('[')[-1], line
    assert '(SONAME)' in dynamic, f'Missing SONAME: {abi}/{lib}'
 assert not any(n.endswith('/libwg-go.so') for n in names), 'Unused second WireGuard backend packaged'
print('Verified packaged rules and both native engines for all three ABIs')
print('SHA-256:',hashlib.sha256(apk.read_bytes()).hexdigest())
