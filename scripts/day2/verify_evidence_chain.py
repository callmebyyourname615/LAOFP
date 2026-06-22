#!/usr/bin/env python3
import argparse,hashlib,json,pathlib,sys

def main():
 p=argparse.ArgumentParser();p.add_argument('--manifest',required=True);p.add_argument('--root',default='.');a=p.parse_args();base=pathlib.Path(a.root).resolve();d=json.loads(pathlib.Path(a.manifest).read_text());bad=[]
 for x in d.get('artifacts',[]):
  f=(base/x['path']).resolve()
  if base not in f.parents or not f.is_file() or hashlib.sha256(f.read_bytes()).hexdigest()!=x['sha256']:bad.append(x['path'])
 if d.get('status')!='PASS':bad.append('manifest-status')
 if bad: print(json.dumps({'status':'FAIL','invalid':bad}));return 1
 print(json.dumps({'status':'PASS','verifiedArtifacts':len(d.get('artifacts',[]))}));return 0
if __name__=='__main__':sys.exit(main())
