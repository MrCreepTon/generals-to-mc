"""Original signed-world navigation: no game/world copies or desktop input."""
from pathlib import Path
import argparse,json,math,hashlib
import native_lockstep_bench as f

def main():
 p=argparse.ArgumentParser();p.add_argument('--exe',type=Path,required=True);p.add_argument('--lab',type=Path,required=True);p.add_argument('--report',default='native-navigation-tests.json');a=p.parse_args();f.EXE=a.exe.resolve();f.LAB=a.lab.resolve()
 original=f.subprocess.Popen
 def launch(*args,**kwargs):
  kwargs['env']['ZHMC_TEST']='1';kwargs['env'].pop('ZHMC_SANDBOX',None);return original(*args,**kwargs)
 f.subprocess.Popen=launch
 n=None;r={'result':'FAIL','nativeSha256':hashlib.sha256(f.EXE.read_bytes()).hexdigest(),'checks':[]}
 try:
  n=f.Node(2,lockstep=False);r['session']=str(n.directory);s=f.wait([n],lambda ss:ss[0].get('maps'))[0];name=next(m for m in s['maps'] if 'tournament desert' in m.lower())
  n.send('START '+' '.join(json.dumps(x) for x in (name,'FactionGLADemolitionGeneral','FactionChina'))+' 12345 10000');f.wait([n],lambda ss:ss[0]['inGame'] and ss[0]['frame']>5);n.send('INIT_WORLD');n.send('SCENE -600 -64 1')
  rev=0
  for x in range(-96,-32,16):
   for y in range(-24,24,16):
    rev+=1;n.send(f'TERRAIN3 {rev} 256 '+' '.join(f'{x+i} {y+j} 0 0 256 0' for i in range(16) for j in range(16)))
  f.wait([n],lambda ss:ss[0]['terrainRevision']>=rev)
  n.send('TEST_SPAWN Demo_GLAVehicleTechnicalChassisOne -640 -64 0');s=f.wait([n],lambda ss:any(o['template']=='Demo_GLAVehicleTechnicalChassisOne' for o in ss[0]['objects']))[0];uid=next(o['id'] for o in s['objects'] if o['template']=='Demo_GLAVehicleTechnicalChassisOne');n.send(f'SELECT {uid}');n.send('MOVE -480 -64 0')
  s=f.wait([n],lambda ss:any(o['id']==uid and math.hypot(o['x']+480,o['y']+64)<16 for o in ss[0]['objects']),25)[0]
  ticket=n.send('TEST_GRID -174 -26 31 21');f.wait([n],lambda ss:ss[0]['consumed']>=ticket)
  cells=json.loads((n.directory/'grid-test.json').read_text());ghosts=[c for c in cells if c[2]==uid or c[3]==uid];r['departedCells']=ghosts[:24]
  assert not ghosts,'Vacated negative terrain retains position/goal reservations: '+str(ghosts[:5])
  r['checks'].append('A real moving Technical releases all position/goal reservations on vacated negative cells')
  # Repeated mixed groups detour around a real friendly command centre.
  for x in range(-112,0,16):
   for y in range(-24,24,16):
    rev+=1;n.send(f'TERRAIN3 {rev} 256 '+' '.join(f'{x+i} {y+j} 0 0 256 0' for i in range(16) for j in range(16)))
  f.wait([n],lambda ss:ss[0]['terrainRevision']>=rev)
  n.send('TEST_SPAWN Demo_GLACommandCenter -640 80 0 2');n.send('TEST_SPAWN Demo_GLAInfantryRebel -800 68 0 2');n.send('TEST_SPAWN Demo_GLAInfantryRebel -800 92 0 2')
  s=f.wait([n],lambda ss:sum(o['template']=='Demo_GLAInfantryRebel' for o in ss[0]['objects'])==2)[0]
  units=[o['id'] for o in s['objects'] if o['template']=='Demo_GLAInfantryRebel']+[uid]
  for gx in [-400,-800,-400]:
   n.send('SELECT '+' '.join(map(str,units)));n.send(f'MOVE {gx} 80 0')
   s=f.wait([n],lambda ss:all(any(o['id']==u and abs(o['x']-gx)<64 and abs(o['y']-80)<80 and math.hypot(o['x']-o.get('goalX',gx),o['y']-o.get('goalY',80))<8 and abs(o['z'])<1 for o in ss[0]['objects']) for u in units),35)[0]
  r['checks'].append('Mixed original infantry/vehicle group repeatedly detours around its own command centre, remains on the floor and accepts fresh moves')
  r['result']='PASS'
 except Exception as e:r['error']=repr(e)
 finally:
  if n:n.close()
  (Path(__file__).resolve().parents[1]/'evidence'/a.report).write_text(json.dumps(r,indent=2),encoding='utf-8');print(json.dumps(r))
 return 0 if r['result']=='PASS' else 1
if __name__=='__main__':raise SystemExit(main())

