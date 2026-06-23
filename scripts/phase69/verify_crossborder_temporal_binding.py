#!/usr/bin/env python3
from __future__ import annotations
import argparse, re, sys, tempfile
from pathlib import Path

INSTANT_DECL = re.compile(r'\bInstant\s+([A-Za-z_$][\w$]*)')
CALL = re.compile(r'\.setObject\s*\((.*?)\)', re.S)
DANGEROUS_EXPR = re.compile(r'\bInstant\.now\s*\(|\.toInstant\s*\(')

def split_args(value: str) -> list[str]:
    args=[]; current=[]; depth=0
    for ch in value:
        if ch==',' and depth==0:
            args.append(''.join(current).strip()); current=[]; continue
        current.append(ch)
        if ch in '([{': depth+=1
        elif ch in ')]}' and depth: depth-=1
    args.append(''.join(current).strip())
    return args

def violations(text: str, source: str) -> list[str]:
    clean=re.sub(r'/\*.*?\*/|//[^\n]*', '', text, flags=re.S)
    instant_names=set(INSTANT_DECL.findall(clean))
    result=[]
    for match in CALL.finditer(clean):
        args=split_args(match.group(1))
        if len(args)<2: continue
        value=args[1]
        instant_value=bool(DANGEROUS_EXPR.search(value)) or any(re.search(rf'\b{re.escape(n)}\b', value) for n in instant_names)
        if instant_value and (len(args)<3 or 'Types.TIMESTAMP_WITH_TIMEZONE' not in args[2]):
            line=clean.count('\n',0,match.start())+1
            result.append(f'{source}:{line}: Instant must use setObject(index, value, Types.TIMESTAMP_WITH_TIMEZONE)')
    return result

def self_test() -> None:
    unsafe='''import java.time.Instant; class X { void x(java.sql.PreparedStatement s, Instant instant) throws Exception { s.setObject(1, instant); } }'''
    safe='''import java.time.Instant; import java.sql.Types; class X { void x(java.sql.PreparedStatement s, Instant instant) throws Exception { s.setObject(1, instant, Types.TIMESTAMP_WITH_TIMEZONE); } }'''
    direct='''class X { void x(java.sql.PreparedStatement s) throws Exception { s.setObject(1, java.time.Instant.now()); } }'''
    if not violations(unsafe,'unsafe.java'): raise AssertionError('unsafe sample was not rejected')
    if violations(safe,'safe.java'): raise AssertionError('safe sample was rejected')
    if not violations(direct,'direct.java'): raise AssertionError('Instant.now sample was not rejected')
    print('cross-border temporal binding self-test: PASS')

def main() -> int:
    p=argparse.ArgumentParser(); p.add_argument('--root',default='src/main/java/com/example/switching/crossborder'); p.add_argument('--self-test',action='store_true')
    a=p.parse_args()
    if a.self_test: self_test()
    root=Path(a.root); failures=[]
    if root.exists():
        for file in sorted(root.rglob('*.java')):
            failures.extend(violations(file.read_text(encoding='utf-8'), str(file)))
    if failures:
        print('cross-border temporal binding: FAIL')
        for failure in failures: print('  ERROR:',failure)
        return 1
    print('cross-border temporal binding: PASS')
    return 0
if __name__=='__main__': raise SystemExit(main())
