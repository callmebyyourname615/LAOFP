#!/usr/bin/env python3
import hashlib,json,os,subprocess,sys,time,urllib.error,urllib.request,uuid

base=os.environ['SYNTHETIC_BASE_URL'].rstrip('/')
db=os.environ['DB_URL']
probe=os.environ['SYNTHETIC_PROBE_CODE']
source=os.environ['SYNTHETIC_SOURCE_BANK']
destination=os.environ['SYNTHETIC_DESTINATION_BANK']
if not source.startswith('SYN') or not destination.startswith('SYN'):
    raise SystemExit('both synthetic participants must use the reserved SYN prefix')
mode=os.getenv('SYNTHETIC_MODE','INQUIRY').upper()
if mode not in {'INQUIRY','END_TO_END'}: raise SystemExit('SYNTHETIC_MODE must be INQUIRY or END_TO_END')
reference='SYN-'+uuid.uuid4().hex.upper()
execution=str(uuid.uuid4())
api_key=os.getenv('SYNTHETIC_API_KEY','')
headers={'Content-Type':'application/json','Accept':'application/json'}
if api_key: headers['X-API-Key']=api_key

def psql(sql,**variables):
    cmd=['psql',db,'-v','ON_ERROR_STOP=1']
    for k,v in variables.items(): cmd += ['-v',f'{k}={v}']
    cmd += ['-Atc',sql]
    return subprocess.check_output(cmd,text=True).strip()

def call(path,payload):
    body=json.dumps(payload,separators=(',',':')).encode()
    req=urllib.request.Request(base+path,body,headers,method='POST')
    with urllib.request.urlopen(req,timeout=30) as response:
        raw=response.read(1024*1024+1)
        if len(raw)>1024*1024: raise RuntimeError('synthetic response exceeds 1 MiB')
        if response.status not in {200,201,202}: raise RuntimeError(f'unexpected HTTP status {response.status}')
        return json.loads(raw)

psql("INSERT INTO synthetic_probe_execution(id,probe_code,synthetic_reference,started_at,status) VALUES (:'id'::uuid,:'probe',:'ref',now(),'RUNNING')",id=execution,probe=probe,ref=reference)
started=time.monotonic(); status='FAIL'; response_code=''; cleanup='NOT_REQUIRED'; error=''
try:
    inquiry=call('/api/inquiries',{
        'clientInquiryId':reference+'-INQ','idempotencyKey':reference+'-INQ','sourceBank':source,
        'destinationBank':destination,'debtorAccount':os.environ['SYNTHETIC_DEBTOR_ACCOUNT'],
        'creditorAccount':os.environ['SYNTHETIC_CREDITOR_ACCOUNT'],'amount':os.getenv('SYNTHETIC_AMOUNT','1.00'),
        'currency':os.getenv('SYNTHETIC_CURRENCY','LAK'),'reference':reference})
    inquiry_ref=inquiry.get('inquiryRef')
    if not inquiry_ref: raise RuntimeError('inquiry response missing inquiryRef')
    response_code='INQUIRY_OK'
    if mode=='END_TO_END':
        transfer=call('/api/transfers',{
            'sourceBank':source,'destinationBank':destination,'debtorAccount':os.environ['SYNTHETIC_DEBTOR_ACCOUNT'],
            'creditorAccount':os.environ['SYNTHETIC_CREDITOR_ACCOUNT'],'amount':os.getenv('SYNTHETIC_AMOUNT','1.00'),
            'currency':os.getenv('SYNTHETIC_CURRENCY','LAK'),'reference':reference,
            'idempotencyKey':reference+'-TRF','inquiryRef':inquiry_ref})
        if not (transfer.get('transferRef') or transfer.get('reference')): raise RuntimeError('transfer response missing reference')
        response_code='END_TO_END_OK'; cleanup='ISOLATED_SYNTHETIC_LEDGER'
    status='PASS'
except (Exception,urllib.error.HTTPError) as exc:
    error=str(exc)[:500]
latency=max(0,int((time.monotonic()-started)*1000))
evidence=hashlib.sha256(f'{reference}|{status}|{response_code}|{cleanup}|{latency}'.encode()).hexdigest()
psql("UPDATE synthetic_probe_execution SET completed_at=now(),status=:'status',latency_ms=:'latency'::bigint,response_code=:'code',cleanup_status=:'cleanup',evidence_hash=:'hash',error_summary=nullif(:'error','') WHERE id=:'id'::uuid AND status='RUNNING'",id=execution,status=status,latency=latency,code=response_code,cleanup=cleanup,hash=evidence,error=error)
if status!='PASS': raise SystemExit(error or 'synthetic probe failed')
print(reference)
