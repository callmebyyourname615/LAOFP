#!/usr/bin/env python3
import json,pathlib,re,sys
for filename in sys.argv[1:]:
    p=pathlib.Path(filename); data=json.loads(p.read_text(encoding='utf-8'))
    body=data.get('bodyTemplate','')
    if not body.strip(): raise SystemExit(f'{p}: bodyTemplate required')
    forbidden=[r'(?i)password',r'(?i)private[_ -]?key',r'(?i)full[_ -]?card',r'(?i)cvv']
    if any(re.search(x,body) for x in forbidden): raise SystemExit(f'{p}: forbidden sensitive placeholder/text')
    placeholders=set(re.findall(r'\{\{([A-Za-z0-9_.-]+)\}\}',body))
    allowed=set(data.get('allowedPlaceholders',[]))
    if not placeholders<=allowed: raise SystemExit(f'{p}: undeclared placeholders {sorted(placeholders-allowed)}')
print('notification template lint PASS')
