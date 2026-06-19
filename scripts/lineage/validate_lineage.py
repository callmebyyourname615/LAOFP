#!/usr/bin/env python3
import json,pathlib,re,sys
SHA=re.compile(r'^[0-9a-f]{64}$')
def main(path):
    doc=json.loads(pathlib.Path(path).read_text(encoding='utf-8')); assets=doc.get('assets') or [];edges=doc.get('edges') or []
    if not assets: raise SystemExit('assets are required')
    codes=set()
    for i,a in enumerate(assets):
        for key in ('code','type','reference','owner','classification'):
            if not a.get(key): raise SystemExit(f'assets[{i}].{key} is required')
        if a['code'] in codes: raise SystemExit(f'duplicate asset {a["code"]}')
        codes.add(a['code'])
        if a['classification'] not in {'PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED'}: raise SystemExit('invalid classification')
    graph={c:set() for c in codes}
    for i,e in enumerate(edges):
        if e.get('source') not in codes or e.get('target') not in codes: raise SystemExit(f'edges[{i}] references unknown asset')
        if e['source']==e['target']: raise SystemExit('self lineage edge is not allowed')
        if e.get('field_mapping_sha256') and not SHA.fullmatch(e['field_mapping_sha256']): raise SystemExit('invalid field mapping hash')
        graph[e['source']].add(e['target'])
    visiting=set();visited=set()
    def visit(node):
        if node in visiting: raise SystemExit('lineage cycle detected')
        if node in visited:return
        visiting.add(node)
        for child in graph[node]:visit(child)
        visiting.remove(node);visited.add(node)
    for code in codes:visit(code)
    print(f'validated {len(codes)} assets and {len(edges)} acyclic lineage edges')
if __name__=='__main__':
    if len(sys.argv)!=2:raise SystemExit('usage: validate_lineage.py LINEAGE.json')
    main(sys.argv[1])
