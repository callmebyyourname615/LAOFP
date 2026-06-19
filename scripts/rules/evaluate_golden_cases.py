#!/usr/bin/env python3
import argparse,json,pathlib

def main(cases_path,results_path):
    cases=json.loads(pathlib.Path(cases_path).read_text(encoding='utf-8')).get('cases',[])
    results=json.loads(pathlib.Path(results_path).read_text(encoding='utf-8')).get('results',[])
    by_id={r['id']:r for r in results};fail=[]
    for case in cases:
        actual=by_id.get(case['id'])
        if not actual:fail.append(case['id']+': missing result');continue
        if actual.get('decision')!=case.get('expected_decision'):fail.append(case['id']+': decision mismatch')
    print(json.dumps({'total':len(cases),'failed':len(fail),'failures':fail},sort_keys=True));return 2 if fail else 0
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('cases');p.add_argument('results');a=p.parse_args();raise SystemExit(main(a.cases,a.results))
