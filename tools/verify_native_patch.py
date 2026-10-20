"""Verify fresh pinned source integration, idempotence and active checkout equality.

The temporary original source stays in the private LOCALAPPDATA lab.
"""
from pathlib import Path
import io,json,os,subprocess,tarfile,uuid,hashlib,sys

root=Path(os.environ['LOCALAPPDATA'])/'GeneralsMinecraft'
source=root/'native-src/GeneralsGameCode'
fixture=root/'native-src'/('patch-oracle-'+str(uuid.uuid4()))
fixture.mkdir()
data=subprocess.check_output(['git','-C',str(source),'archive','HEAD'])
with tarfile.open(fileobj=io.BytesIO(data)) as archive:
    for member in archive:
        if not member.isfile():continue
        target=(fixture/member.name).resolve()
        if not target.is_relative_to(fixture.resolve()):raise RuntimeError('Archive escaped fixture')
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(archive.extractfile(member).read())
# prepare_engine only queries rev-parse; no git mutation runs in this fixture.
(fixture/'.git').write_text('gitdir: '+str(source/'.git'),encoding='utf-8')
patcher=Path(__file__).with_name('prepare_engine.py')
paths=subprocess.check_output(['git','-c','core.autocrlf=false','-C',str(source),'diff','--name-only'],text=True).splitlines()
def hashes(directory):
    return {p:hashlib.sha256((directory/p).read_bytes()).hexdigest() for p in paths}
subprocess.run([sys.executable,'-X','utf8',str(patcher),str(fixture)],check=True)
first=hashes(fixture)
subprocess.run([sys.executable,'-X','utf8',str(patcher),str(fixture)],check=True)
second=hashes(fixture);active=hashes(source)
report={'result':'PASS' if first==second==active else 'FAIL','files':len(paths),
        'fixture':str(fixture),'idempotent':first==second,
        'activeDifferences':[p for p in paths if active[p]!=first[p]]}
Path(__file__).resolve().parents[1].joinpath('evidence/native-new-issues-patch-tests.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report));sys.exit(0 if report['result']=='PASS' else 1)
