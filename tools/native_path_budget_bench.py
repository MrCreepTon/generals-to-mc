"""Bounded sparse path search: unreachable island, long valid route and later logic ticks."""
from pathlib import Path
import hashlib,json,math,time
import native_lockstep_bench as bench

node=bench.Node(2,True);checks=[];report={'result':'FAIL','checks':checks,'session':str(node.directory),'nativeSha256':hashlib.sha256(bench.EXE.read_bytes()).hexdigest()}
try:
 state=bench.wait([node],lambda s:bool(s[0].get('maps')))[0]
 map_name=next(m for m in state['maps'] if 'tournament desert' in m.casefold())
 node.send('START '+json.dumps(map_name)+' "FactionAmerica" "FactionChina" 12345 10000')
 state=bench.wait([node],lambda s:s[0].get('inGame') and s[0].get('lockstep'))[0];frame=state['frame']
 world=['INIT_WORLD'];revision=0
 for x in range(64,256,32):
  for y in range(64,256,32):
   revision+=1
   def column(a,b):
    wall=170<=a<=176 and 170<=b<=176 and (a in (170,176) or b in (170,176))
    return f'{a} {b} {64 if wall else 0} {6 if wall else 0} 256 0'
   world.append(f'TERRAIN3 {revision} 1024 '+' '.join(column(a,b) for a in range(x,x+32) for b in range(y,y+32)))
 def commit(commands=()):
  global state,frame
  p=node.directory/'frames'/f'{frame}.tmp';p.write_text(f'FRAME {frame}\n'+''.join('W '+c+'\n' for c in commands));p.replace(p.with_suffix('.frame'))
  state=bench.wait([node],lambda s:s[0]['frame']>frame,10)[0];frame=state['frame']
 commit(world)
 before={a['id'] for a in state['objects']};node.send('TEST_SPAWN AmericaInfantryRanger 804 804 0 2')
 state=bench.wait([node],lambda s:any(a['id'] not in before and a['template']=='AmericaInfantryRanger' for a in s[0]['objects']))[0]
 unit=next(a['id'] for a in state['objects'] if a['id'] not in before and a['template']=='AmericaInfantryRanger')
 results=[]
 for name,x,y,reachable in [('sealed island',1388,1388,False),('116-block clear route',1732,804,True)]:
  before_limits=state.get('pathSearchLimits',0);start=time.monotonic();seq=node.send(f'TEST_PATH {unit} {x} {y} 0')
  state=bench.wait([node],lambda s:s[0]['consumed']>=seq,5)[0]
  elapsed=time.monotonic()-start;diagnostic=json.loads((node.directory/'path-diagnostic.json').read_text())
  assert diagnostic['found']==reachable,(name,diagnostic)
  assert elapsed<1,(name,elapsed)
  if not reachable:assert state['pathSearchLimits']>before_limits,'Fixture did not exercise the search budget'
  results.append({'case':name,'seconds':elapsed,'diagnostic':diagnostic,'limits':state['pathSearchLimits']})
  checks.append(f'{name}: correct reachability without blocking the engine for a second')
 start_frame=frame
 for _ in range(30):commit()
 assert frame>=start_frame+30
 checks.append('Original logic continues after a budget-limited unreachable path request')
 report.update(result='PASS',cases=results,frame=frame)
except BaseException as error:
 report['error']=repr(error);raise
finally:
 node.close();Path(__file__).resolve().parents[1].joinpath('evidence/native-path-budget-tests.json').write_text(json.dumps(report,indent=2));print(json.dumps(report))

