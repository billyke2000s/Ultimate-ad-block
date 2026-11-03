"""Check the committed mobile bundle without downloading mutable upstream data."""
import gzip
import json
import re
from pathlib import Path
p=Path('systemblock/src/main/assets')
metadata=json.loads((p/'blocklist-sources.json').read_text())
domains=[s for s in gzip.decompress((p/'blocklist.txt.gz').read_bytes()).decode('utf-8').splitlines() if s and not s.startswith('#')]
assert len(domains)==metadata['domain_count'] and len(domains)>=400000, 'Unexpected bundle count'
assert domains==sorted(set(domains)), 'Rules must be sorted and unique'
assert all(re.fullmatch(r'[a-z0-9_.-]+',s) and 1<len(s)<=253 for s in domains), 'Invalid rule'
rules=set(domains)
def blocks(host):
 labels=host.split('.')
 return any('.'.join(labels[i:]) in rules for i in range(len(labels)))
assert blocks('pagead2.googlesyndication.com') and blocks('doubleclick.net'), 'Baseline ad rules missing'
for host in ['example.com','cloudflare-dns.com','github.com','google.com','microsoft.com','apple.com']:
 assert not blocks(host), 'Connectivity domain blocked: '+host
print(f'Validated {len(domains):,} unique domain rules and connectivity/ad sentinels')
