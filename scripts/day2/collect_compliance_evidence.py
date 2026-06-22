#!/usr/bin/env python3
import argparse,hashlib,json,pathlib,sys,yaml,datetime

def main():
 p=argparse.ArgumentParser();p.add_argument('--mapping',required=True);p.add_argument('--controls',required=True);p.add_argument('--root',default='.');p.add_argument('--release-reference',required=True);p.add_argument('--git-commit',required=True);p.add_argument('--image-digest',required=True);p.add_argument('--output',required=True);a=p.parse_args()
 base=pathlib.Path(a.root).resolve();mapping=yaml.safe_load(pathlib.Path(a.mapping).read_text());controls=yaml.safe_load(pathlib.Path(a.controls).read_text());art=[];present=set()
 for m in mapping['mappings']:
  path=(base/m['path']).resolve()
  if path!=base and base not in path.parents: raise SystemExit('unsafe evidence path')
  if not path.is_file(): continue
  art.append({'evidenceType':m['evidenceType'],'path':str(path.relative_to(base)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'sizeBytes':path.stat().st_size,'controlIds':m['controlIds']});present.add(m['evidenceType'])
 rows=[]
 for c in controls['controls']:
  required=set(c['requiredEvidence']);missing=sorted(required-present);rows.append({'id':c['id'],'status':'COVERED' if not missing else 'MISSING','missingEvidence':missing})
 status='PASS' if all(x['status']=='COVERED' for x in rows) else 'FAIL'
 doc={'schemaVersion':1,'generatedAt':datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace('+00:00','Z'),'release':{'reference':a.release_reference,'gitCommit':a.git_commit,'imageDigest':a.image_digest},'status':status,'artifacts':art,'controls':rows}
 pathlib.Path(a.output).write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n');return 0 if status=='PASS' else 1
if __name__=='__main__':sys.exit(main())
