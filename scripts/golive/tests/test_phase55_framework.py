#!/usr/bin/env python3
from __future__ import annotations
import datetime as dt, hashlib, importlib.util, json, os, pathlib, subprocess, sys, tempfile, unittest
from unittest import mock

ROOT = pathlib.Path(__file__).resolve().parents[3]
PYTHON = os.environ.get("PYTHON", "python3")


def run(*args: str, ok: bool = True) -> subprocess.CompletedProcess:
    result = subprocess.run([PYTHON, *args], cwd=ROOT, text=True, capture_output=True)
    if ok and result.returncode != 0:
        raise AssertionError(result.stdout + result.stderr)
    if not ok and result.returncode == 0:
        raise AssertionError("command unexpectedly passed")
    return result


def sha(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class Phase55FrameworkTest(unittest.TestCase):
    def test_release_candidate_tamper_detection(self):
        with tempfile.TemporaryDirectory() as td:
            tmp = pathlib.Path(td); out = tmp / "rc"
            commit = "a" * 40; app = "sha256:" + "b" * 64; migration = "sha256:" + "c" * 64
            phase54 = tmp / "phase54.json"
            phase54.write_text(json.dumps({"releaseCandidateReady": True, "release": {"reference": "CHG-123", "gitCommit": commit, "imageDigest": app}, "phases": []}))
            artifacts = []
            for rel in ["sbom/application.spdx.json", "sbom/migration.spdx.json", "signatures/application-verification.txt", "signatures/migration-verification.txt", "provenance/application.intoto.jsonl", "provenance/migration.intoto.jsonl", "manifests/deployment.yaml", "manifests/migration-job.yaml"]:
                src = tmp / rel.replace("/", "-"); src.write_text("{}\n" if rel.endswith((".json", ".jsonl")) else "verified\n")
                artifacts += ["--artifact", f"{src}={rel}"]
            run("scripts/golive/assemble_release_candidate.py", "--output", str(out), "--reference", "CHG-123", "--rc-id", "switching-1.0.0", "--git-commit", commit, "--application-image-repository", "registry/app", "--application-image-digest", app, "--migration-image-repository", "registry/migration", "--migration-image-digest", migration, "--phase54-manifest", str(phase54), *artifacts)
            run("scripts/golive/verify_release_candidate.py", "--root", str(out), "--expected-rc-id", "switching-1.0.0", "--expected-git-commit", commit, "--expected-application-digest", app, "--expected-migration-digest", migration)
            (out / "manifests/deployment.yaml").write_text("tampered\n")
            run("scripts/golive/verify_release_candidate.py", "--root", str(out), ok=False)

    def test_decision_binds_evidence(self):
        with tempfile.TemporaryDirectory() as td:
            tmp=pathlib.Path(td); evidence=tmp/"evidence.json"; evidence.write_text("{}\n")
            decision=tmp/"decision.json"; now=dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00","Z")
            doc={"stage":"25","decision":"PROMOTE","releaseReference":"CHG-123","releaseCandidateId":"switching-1.0.0","gitCommit":"a"*40,"applicationImageDigest":"sha256:"+"b"*64,"issuedAt":now,"approvedBy":["one","two"],"evidenceSha256":sha(evidence)}
            decision.write_text(json.dumps(doc))
            base=["scripts/golive/verify_decision.py","--decision",str(decision),"--stage","25","--reference","CHG-123","--rc-id","switching-1.0.0","--git-commit","a"*40,"--application-digest","sha256:"+"b"*64,"--evidence",str(evidence)]
            run(*base); evidence.write_text("changed\n"); run(*base,ok=False)

    def test_reconciliation_invariants(self):
        with tempfile.TemporaryDirectory() as td:
            tmp=pathlib.Path(td); before=tmp/"before.json"; after=tmp/"after.json"; out=tmp/"out.json"
            base={"queryDefinitionSha256":"x","label":"before","results":{"transactions":{"mode":"aggregate-nondecreasing","rows":[{"count":10,"amount":100}]},"ledger":{"mode":"balanced","rows":[{"debit":100,"credit":100}]},"duplicates":{"mode":"zero","rows":[{"count":0}]},"outbox":{"mode":"monotonic","rows":[{"count":5}]}}}
            current={"queryDefinitionSha256":"x","label":"after","results":{"transactions":{"mode":"aggregate-nondecreasing","rows":[{"count":11,"amount":110}]},"ledger":{"mode":"balanced","rows":[{"debit":110,"credit":110}]},"duplicates":{"mode":"zero","rows":[{"count":0}]},"outbox":{"mode":"monotonic","rows":[{"count":6}]}}}
            before.write_text(json.dumps(base)); after.write_text(json.dumps(current))
            run("scripts/golive/compare_reconciliation.py","--baseline",str(before),"--current",str(after),"--output",str(out))
            current["results"]["ledger"]["rows"][0]["credit"]=109; after.write_text(json.dumps(current))
            run("scripts/golive/compare_reconciliation.py","--baseline",str(before),"--current",str(after),"--output",str(out),ok=False)

    def test_reconciliation_capture_uses_single_repeatable_read_snapshot(self):
        module_path = ROOT / 'scripts/golive/capture_reconciliation.py'
        spec = importlib.util.spec_from_file_location('phase55_capture_test_module', module_path)
        module = importlib.util.module_from_spec(spec); assert spec.loader; spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as td:
            tmp=pathlib.Path(td); queries=tmp/'queries.yaml'; out=tmp/'out.json'
            queries.write_text(__import__('yaml').safe_dump({'schemaVersion':1,'queries':{'one':{'mode':'zero','sql':'SELECT 0 AS count'},'two':{'mode':'monotonic','sql':'SELECT 1 AS count'}}}))
            completed = subprocess.CompletedProcess(args=[], returncode=0, stdout='{"name":"one","rows":[{"count":0}]}\n{"name":"two","rows":[{"count":1}]}\n', stderr='')
            argv=['capture_reconciliation.py','--queries',str(queries),'--output',str(out),'--label','test','--release-reference','CHG-123','--git-commit','a'*40]
            env={'DB_URL':'jdbc:postgresql://db.prod:5432/switching_db?sslmode=verify-full','DB_USERNAME':'app','DB_PASSWORD':'secret'}
            with mock.patch.dict(os.environ,env,clear=False), mock.patch.object(module.subprocess,'run',return_value=completed) as called, mock.patch.object(sys,'argv',argv):
                self.assertEqual(module.main(),0)
            self.assertEqual(called.call_count,1)
            sql=called.call_args.kwargs['input']
            self.assertIn('REPEATABLE READ READ ONLY',sql); self.assertIn('SET LOCAL statement_timeout',sql)
            doc=json.loads(out.read_text()); self.assertTrue(doc['readOnly']); self.assertEqual(doc['isolationLevel'],'REPEATABLE READ')

    def test_hypercare_minimum_duration(self):
        with tempfile.TemporaryDirectory() as td:
            tmp=pathlib.Path(td); policy=json.loads(json.dumps({}))
            required = __import__('yaml').safe_load((ROOT/'config/phase55-hypercare-policy.yaml').read_text())['requiredSignals']
            now=dt.datetime.now(dt.timezone.utc); obs={"startedAt":(now-dt.timedelta(hours=25)).isoformat().replace('+00:00','Z'),"endedAt":now.isoformat().replace('+00:00','Z'),"signals":{k:{"status":"PASS"} for k in required}}
            files={"observations":obs,"incidents":{"criticalOpen":0,"highOpen":0},"reconciliation":{"status":"PASS"},"alerts":{"criticalFiring":0,"deliveryVerified":True}}
            paths={}
            for name,data in files.items(): paths[name]=tmp/(name+'.json'); paths[name].write_text(json.dumps(data))
            run("scripts/golive/build_hypercare_report.py","--observations",str(paths['observations']),"--incidents",str(paths['incidents']),"--reconciliation",str(paths['reconciliation']),"--alerts",str(paths['alerts']),"--output",str(tmp/'out.json'))

    def test_stage_metrics_fail_closed_and_pass(self):
        module_path = ROOT / 'scripts/golive/verify_stage_metrics.py'
        spec = importlib.util.spec_from_file_location('phase55_stage_metrics_test_module', module_path)
        module = importlib.util.module_from_spec(spec); assert spec.loader; spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as td:
            tmp=pathlib.Path(td); config=tmp/'metrics.yaml'; out=tmp/'out.json'
            base=['verify_stage_metrics.py','--prometheus-url','http://prometheus.invalid','--track','canary','--stage','5','--config',str(config),'--output',str(out)]
            config.write_text(__import__('yaml').safe_dump({'schemaVersion':1,'window':'1m','queryTimeoutSeconds':2,'metrics':[{'id':'zero','description':'test','expression':'vector(0)','comparison':'maximum','threshold':0}]}))
            with mock.patch.object(module, 'query', return_value=0.0), mock.patch.object(sys, 'argv', base):
                self.assertEqual(module.main(), 0)
            self.assertEqual(json.loads(out.read_text())['status'],'PASS')
            config.write_text(__import__('yaml').safe_dump({'schemaVersion':1,'metrics':[{'id':'must-one','expression':'vector(0)','comparison':'minimum','threshold':1}]}))
            with mock.patch.object(module, 'query', return_value=0.0), mock.patch.object(sys, 'argv', base):
                self.assertEqual(module.main(), 1)
            self.assertEqual(json.loads(out.read_text())['status'],'FAIL')

    def test_operational_acceptance_manifest_requires_pass(self):
        with tempfile.TemporaryDirectory() as td:
            tmp=pathlib.Path(td); root=tmp/'evidence'; phase=root/'phases'/'55J'; phase.mkdir(parents=True)
            reference='CHG-123'; rc_id='switching-1.0.0'; commit='a'*40; app='sha256:'+'b'*64; migration='sha256:'+'c'*64
            release={'reference':reference,'releaseCandidateId':rc_id,'gitCommit':commit,'applicationImageDigest':app,'migrationImageDigest':migration,'environment':'hypercare'}
            (phase/'result.json').write_text(json.dumps({'phase':'55J','status':'PASS','release':release}))
            (phase/'operational-acceptance.json').write_text('{}'); (phase/'known-issues-register.json').write_text('{}'); (phase/'post-implementation-review.md').write_text('# PIR\n')
            plan=tmp/'plan.yaml'; plan.write_text(__import__('yaml').safe_dump({'phases':[{'id':'55J','name':'closure','requiredForOperationalAcceptance':True,'evidence':['phases/55J/result.json','phases/55J/operational-acceptance.json','phases/55J/known-issues-register.json','phases/55J/post-implementation-review.md','operational-acceptance/manifest.json','operational-acceptance/checksums.sha256']}]}))
            out=root/'operational-acceptance'/'manifest.json'
            run('scripts/golive/build_operational_acceptance.py','--root',str(root),'--plan',str(plan),'--reference',reference,'--rc-id',rc_id,'--git-commit',commit,'--application-digest',app,'--migration-digest',migration,'--output',str(out))
            self.assertTrue(json.loads(out.read_text())['operationallyAccepted'])
            data=json.loads((phase/'result.json').read_text()); data['status']='FAIL'; (phase/'result.json').write_text(json.dumps(data))
            run('scripts/golive/build_operational_acceptance.py','--root',str(root),'--plan',str(plan),'--reference',reference,'--rc-id',rc_id,'--git-commit',commit,'--application-digest',app,'--migration-digest',migration,'--output',str(out),ok=False)

if __name__ == "__main__":
    unittest.main()
