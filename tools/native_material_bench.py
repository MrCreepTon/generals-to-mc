"""Real original WW3D two-stage command-centre coverage; no extracted assets in the release."""
from pathlib import Path
import json,struct,time,hashlib
import native_lockstep_bench as fixture
original=fixture.subprocess.Popen
def launch(*args,**kwargs):
 kwargs['env']['ZHMC_TEST']='1';return original(*args,**kwargs)
fixture.subprocess.Popen=launch
n=fixture.Node(2,graphics=True,lockstep=False);report={'result':'FAIL','checks':[],'session':str(n.directory),'nativeSha256':hashlib.sha256(fixture.EXE.read_bytes()).hexdigest()}
try:
 state=fixture.wait([n],lambda s:bool(s[0].get('maps')))[0];name=next(m for m in state['maps'] if 'tournament desert' in m.lower());n.send('START '+json.dumps(name)+' "FactionChinaTankGeneral" "FactionGLA" 12345 10000')
 state=fixture.wait([n],lambda s:s[0]['inGame'] and s[0]['frame']>40)[0];cc=next(a for a in state['objects'] if a['owner']==2 and a['structure']);n.send(f'SCENE {cc["x"]} {cc["y"]} 500');time.sleep(1)
 records=(n.directory/'texture-names.txt').read_text().splitlines();masks=[r.split() for r in records if 'alpha-stage' in r];assert masks,'Original Tank command-centre alpha stage not exported'
 def pixels(ident):
  data=(n.directory/'textures'/f'{ident}.rgba').read_bytes();return struct.unpack_from('<II',data),data[8:]
 ids=[]
 for composite,_,base,mask,operation in masks:
  dimensions,rgba=pixels(composite);dw,dh=dimensions;bd,b=pixels(base);md,m=pixels(mask);assert dimensions==bd==md and operation=='1',(dimensions,bd,md,operation)
  assert rgba[3::4]==m[3::4] and min(rgba[3::4])==0 and max(rgba[3::4])==255,'Coverage was lost or guessed'
  assert all(rgba[i]==b[i] for i in range(len(rgba)) if i%4!=3),'House colours were changed';ids.append(int(composite))
 scene=(n.directory/'scene.bin').read_bytes();_,frame,batches=struct.unpack_from('<III',scene);offset=12;used=False
 for _ in range(batches):
  actor,texture,count,part,mode=struct.unpack_from('<IIIQI',scene,offset);offset+=24+count*36
  if actor==cc['id'] and texture in ids:used=True;assert mode&15==2 and (mode>>4)&15==5
 assert used,'Correct coverage not referenced by original command-centre triangles'
 report.update(result='PASS',checks=['Real Tank command-centre exports its source second-stage alpha into a cached shared-UV material','Every coverage byte matches the original mask; RGB and house colours are unchanged','Actual command-centre scene triangles reference the masked texture with original alpha blend factors'])
except BaseException as error:report['error']=repr(error)
finally:
 n.close();Path('evidence/native-material-tests.json').write_text(json.dumps(report,indent=2));print(json.dumps(report))
if report['result']!='PASS':raise SystemExit(1)
