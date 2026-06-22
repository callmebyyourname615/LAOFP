#!/usr/bin/env python3
import argparse,json,pathlib,sys,yaml

def main():
 p=argparse.ArgumentParser();p.add_argument('--snapshot',required=True);p.add_argument('--budget',required=True);p.add_argument('--forecast-policy',required=True);p.add_argument('--output',required=True);p.add_argument('--forecast-output',required=True);a=p.parse_args();s=json.loads(pathlib.Path(a.snapshot).read_text());b=yaml.safe_load(pathlib.Path(a.budget).read_text());fp=yaml.safe_load(pathlib.Path(a.forecast_policy).read_text());checks=[]
 def add(i,ok,av,ev):checks.append({'id':i,'status':'PASS' if ok else 'FAIL','actual':av,'expected':ev})
 add('daily-cost',s['dailyCost']<=b['dailyBudget'],s['dailyCost'],b['dailyBudget']);inc=((s['dailyCost']/s['baselineDailyCost'])-1)*100 if s['baselineDailyCost'] else 999;add('daily-cost-increase',inc<=b['maximumDailyIncreasePercent'],round(inc,2),b['maximumDailyIncreasePercent'])
 resources=b['resources'];add('cpu-efficiency',s['applicationCpuUtilizationPercent']>=20,s['applicationCpuUtilizationPercent'],'>=20');add('memory-efficiency',s['applicationMemoryUtilizationPercent']>=30,s['applicationMemoryUtilizationPercent'],'>=30');add('log-ingestion',s['logIngestionGiBPerDay']<=resources['logIngestionWarningGiBPerDay'],s['logIngestionGiBPerDay'],resources['logIngestionWarningGiBPerDay'])
 minimum=fp['minimumForecastDays'];forecast=[]
 for x in s['storageAssets']:
  days=999999 if x['dailyGrowthGiB']<=0 else max(0,(x['capacityGiB']-x['usedGiB'])/x['dailyGrowthGiB']);forecast.append({'asset':x['asset'],'forecastDays':round(days,2)});add('storage-'+x['asset'],days>=minimum,round(days,2),minimum)
 status='PASS' if all(c['status']=='PASS' for c in checks) else 'FAIL';pathlib.Path(a.output).write_text(json.dumps({'schemaVersion':1,'status':status,'checks':checks},indent=2,sort_keys=True)+'\n');pathlib.Path(a.forecast_output).write_text(json.dumps({'schemaVersion':1,'assets':forecast},indent=2,sort_keys=True)+'\n');return 0 if status=='PASS' else 1
if __name__=='__main__':sys.exit(main())
