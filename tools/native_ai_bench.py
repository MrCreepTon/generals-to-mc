"""Owned headless source game: sustained NPC economy and excavation, no OS input."""
import sys, os, time, json, subprocess, hashlib
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import native_lockstep_bench as bench
n=bench.Node(2,True,sandbox=False);report={'result':'FAIL','session':str(n.directory),'nativeSha256':hashlib.sha256(bench.EXE.read_bytes()).hexdigest(),'samples':[]}
try:
    state=bench.wait([n],lambda s:bool(s[0].get('maps')))[0]
    map_name=next(m for m in state['maps'] if 'tournament desert' in m.lower())
    n.send('MATCH_SLOT 0 0 0 0 "FactionAmerica"');n.send('MATCH_SLOT 1 2 1 1 "FactionChina"')
    n.send('START '+json.dumps(map_name)+' "FactionAmerica" "FactionChina" 12345 10000')
    state=bench.wait([n],lambda s:s[0].get('inGame') and len(s[0].get('objects',[]))>5)[0]
    frame=state['frame'];world=['INIT_WORLD'];revision=0
    for x in range(64,288,32):
        for y in range(32,224,32):
            revision+=1;world.append('TERRAIN3 '+str(revision)+' 1024 '+' '.join(f'{a} {b} 16 0 256 0' for a in range(x,x+32) for b in range(y,y+32)))
    world+=['REBASE_PLAYER 2 800 800 16','REBASE_PLAYER 3 1600 800 16','RESOURCE block_fixture SupplyDock 1920 960 16 100000']
    def batch(count,commands=()):
        global frame,state
        goal=frame+count
        for f in range(frame,goal):
            p=n.directory/'frames'/f'{f}.tmp';p.write_text('FRAME '+str(f)+'\n'+('\n'.join('W '+c for c in commands)+'\n' if f==frame else ''),encoding='utf-8');p.replace(p.with_suffix('.frame'))
        state=bench.wait([n],lambda s:s[0]['frame']>=goal,35)[0];frame=state['frame']
    batch(1,world)
    for i in range(1,17):
        batch(300)
        n.send('TEST_AI');time.sleep(.08)
        npc=[a for a in state['objects'] if a['owner']==3]
        sample={'frame':frame,'templates':[{k:a[k] for k in ('id','template','z','constructionPercent','health')} for a in npc],'resources':state.get('resources'),'armies':state.get('armies')}
        sample['ai']=json.loads((n.directory/'ai-test.json').read_text())
        report['samples'].append(sample);print(json.dumps(sample),flush=True)
        built=[a for a in npc if a.get('structure') and (a.get('constructionPercent',0)<0 or a.get('constructionPercent',0)>=100)]
        if any('SupplyCenter' in a['template'] for a in built) and any('WarFactory' in a['template'] or 'Barracks' in a['template'] for a in built) and any('Tank' in a['template'] or 'Redguard' in a['template'] for a in npc):break
    assert len(built)>=4 and any('SupplyCenter' in a['template'] for a in built), 'NPC economy and production did not develop'
    assert state['resources'][0]['cash']<100050,'Original gatherers did not collect supplies'
    assert any('Tank' in a['template'] or 'Redguard' in a['template'] for a in npc),'NPC produced no combat unit'
    batch(2,['TEST_SPAWN SalvageCrate 900 1200 16 0','TEST_SPAWN AmericaVehicleHumvee 1000 1200 16 2'])
    vehicle=next(a for a in state['objects'] if a['template']=='AmericaVehicleHumvee')
    batch(60,[f'TEST_DAMAGE {vehicle["id"]} 1000000'])
    wreck=next(a for a in state['objects'] if a['template']=='AmericaVehicleHumveeDeadHull' and abs(a['x']-1000)<5)
    crate=next(a for a in state['objects'] if a['template']=='SalvageCrate')
    # Native position must follow a changed host floor, including an existing building.
    cc=next(a for a in state['objects'] if a['owner']==2 and a['structure']);bx=int(cc['x']//8);by=int(cc['y']//8)
    revision+=1;batch(2,[f'TERRAIN3 {revision} 3 {bx} {by} -24 0 256 0 {int(crate["x"]//8)} {int(crate["y"]//8)} -24 0 256 0 {int(wreck["x"]//8)} {int(wreck["y"]//8)} -24 0 256 0'])
    moved=next(a for a in state['objects'] if a['id']==cc['id']);assert abs(moved['z']-(cc['z']-40))<.01,(cc,moved)
    for old in (crate,wreck):
        moved=next(a for a in state['objects'] if a['id']==old['id']);assert moved['z']<=old['z']-39.9,(old,moved)
    report.update(result='PASS',checks=['Medium NPC completes economy and production buildings, gathers supplies and produces an original combat unit on imported elevated terrain','Existing original building settles when its host floor is excavated; native logical Z changes with rendering/collision','Original salvage crate and a real destroyed Humvee hulk settle with the excavated floor'])
except BaseException as e:
    report['error']=repr(e);raise
finally:
    n.close();Path('evidence/map-ai-native-tests.json').write_text(json.dumps(report,indent=2));print(json.dumps({k:v for k,v in report.items() if k!='samples'}),flush=True)
