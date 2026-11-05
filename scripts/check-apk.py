#!/usr/bin/env python3
"""Assert packaged rules and required engine ABIs, rather than source data alone."""
import gzip, hashlib, sys, zipfile
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
 assert not any(n.endswith('/libwg-go.so') for n in names), 'Unused second WireGuard backend packaged'
print('Verified packaged rules and both native engines for all three ABIs')
print('SHA-256:',hashlib.sha256(apk.read_bytes()).hexdigest())
