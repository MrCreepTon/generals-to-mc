"""Bounded native EMP and swept projectile checks; reuses an existing private lab."""
from pathlib import Path
import argparse, hashlib, json, uuid
import native_lockstep_bench as f

def main():
    p=argparse.ArgumentParser();p.add_argument('--exe',type=Path,required=True);p.add_argument('--lab',type=Path,required=True);a=p.parse_args()
    f.EXE=a.exe.resolve();f.LAB=a.lab.resolve();launch=f.subprocess.Popen
    def start(*args,**kw):
        kw['env']['ZHMC_TEST']='1';kw['env'].pop('ZHMC_SANDBOX',None);return launch(*args,**kw)
    f.subprocess.Popen=start
    node=None;report={'result':'FAIL','nativeSha256':hashlib.sha256(f.EXE.read_bytes()).hexdigest(),'checks':[]}
    try:
        node=f.Node(2,lockstep=False);report['session']=str(node.directory)
        state=f.wait([node],lambda ss:ss[0].get('maps'))[0]
        assert state.get('startRadii') and all(0<r<=24 for r in state['startRadii'].values())
        name=next(m for m in state['maps'] if 'tournament desert' in m.lower())
        node.send('START '+' '.join(json.dumps(x) for x in (name,'FactionGLA','FactionChina'))+' 12345 10000')
        f.wait([node],lambda ss:ss[0]['inGame'] and ss[0]['frame']>5)
        node.send('INIT_WORLD');node.send('SCENE 400 300 1000');revision=0
        for x in range(16,112,16):
            for y in range(16,80,16):
                revision+=1
                node.send(f'TERRAIN3 {revision} 256 '+' '.join(f'{x+i} {y+j} 0 0 256 0' for i in range(16) for j in range(16)))
        f.wait([node],lambda ss:ss[0]['terrainRevision']>=revision)
        node.send('TEST_SPAWN GLAVehicleRocketBuggy 180 240 0 2')
        state=f.wait([node],lambda ss:any(o['template']=='GLAVehicleRocketBuggy' for o in ss[0]['objects']))[0]
        buggy=next(o['id'] for o in state['objects'] if o['template']=='GLAVehicleRocketBuggy')
        keys=[str(uuid.uuid4()) for _ in range(3)]
        for i,key in enumerate(keys):node.send(f'MOB_SYNC {key} 290 {240+i*5} 0 20 20 5 14 0 0 "Splash fixture"')
        node.send(f'TEST_FIRE {buggy} 290 240 0');frame=state['frame']
        state=f.wait([node],lambda ss:ss[0]['frame']>frame+120,10)[0]
        hits=[sum(e['amount'] for e in state['combatEvents'] if e.get('target')==key) for key in keys]
        assert all(v>0 for v in hits),hits
        report['checks'].append('An original Rocket Buggy missile detonates and sends damage for all three nearby imported mobs')
        for key in keys:node.send('MOB_DROP '+key)
        revision+=1;node.send(f'TERRAIN3 {revision} 8 '+' '.join(f'42 {y} 0 6 0 1 0 256' for y in range(26,34)))
        before=max(e['sequence'] for e in state['combatEvents']);node.send(f'TEST_FIRE {buggy} 430 240 0')
        state=f.wait([node],lambda ss:any(e['sequence']>before and not e['target'] and e['damageType']==0 for e in ss[0]['combatEvents']),12)[0]
        impact=next(e for e in state['combatEvents'] if e['sequence']>before and not e['target'] and e['damageType']==0)
        assert 335<=impact['x']<=345,impact
        report['checks'].append('A Rocket Buggy missile collides with the imported wall and detonates there through its original projectile behavior')
        node.send('TEST_SPAWN AmericaTankCrusader 400 400 0 3')
        state=f.wait([node],lambda ss:any(o['template']=='AmericaTankCrusader' and o['owner']==3 for o in ss[0]['objects']))[0]
        tank=next(o['id'] for o in state['objects'] if o['template']=='AmericaTankCrusader' and o['owner']==3)
        node.send('TEST_SPAWN EMPMineEffectSpheroid 400 400 0 2')
        state=f.wait([node],lambda ss:any(o['id']==tank and o.get('disabled') for o in ss[0]['objects']),8)[0]
        assert any(e.get('disabledFrames',0)>0 for e in state['combatEvents'])
        frame=state['frame'];node.send(f'SELECT {tank}');node.send('MOVE 460 400 0')
        f.wait([node],lambda ss:ss[0]['frame']>frame+165,10)
        state=node.read();assert not next(o for o in state['objects'] if o['id']==tank)['disabled']
        report['checks'].append('A real EMP mine disables an enemy tank and emits the host impulse on a headless server, then expires without a crash')
        node.send('HOST_EMP_RULES 0');node.send('TEST_SPAWN EMPMineEffectSpheroid 400 400 0 2');frame=state['frame']
        state=f.wait([node],lambda ss:ss[0]['frame']>frame+25,5)[0]
        assert not next(o for o in state['objects'] if o['id']==tank)['disabled']
        report['checks'].append('Disabling the native EMP rule leaves vehicles operational')
        report['result']='PASS'
    except Exception as e:report['error']=repr(e)
    finally:
        if node:node.close()
        (Path(__file__).resolve().parents[1]/'evidence/native-emp-tests.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
        print(json.dumps(report))
    return 0 if report['result']=='PASS' else 1
if __name__=='__main__':raise SystemExit(main())
