"""Reproduce group navigation outside the original dense map, using original orders."""
from pathlib import Path
import argparse,hashlib,json,math
import native_lockstep_bench as fixture

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--exe',type=Path);parser.add_argument('--report',default='native-group-tests.json');args=parser.parse_args()
    if args.exe:fixture.EXE=args.exe
    checks=[];report={'result':'FAIL','checks':checks,'nativeSha256':hashlib.sha256(fixture.EXE.read_bytes()).hexdigest()};node=None
    try:
        node=fixture.Node(2,lockstep=False);report['session']=str(node.directory)
        # Enable test spawning only in this child; keep its map and userdata private.
        state=fixture.wait([node],lambda s:s[0].get('maps'))[0]
        map_name=next(m for m in state['maps'] if 'tournament desert' in m.lower())
        node.send('START '+' '.join(json.dumps(x) for x in (map_name,'FactionAmerica','FactionChina'))+' 12345 10000')
        state=fixture.wait([node],lambda s:s[0]['inGame'] and s[0]['frame']>5 and len(s[0]['objects'])>2)[0];node.send('SCENE 0 0 1')
        revision=0
        for x,y in [(82,544),(-8000,8000),(17000,17000)]:
            for dx in range(-16,160,16):
                for dy in range(-16,32,16):
                    pts=[(math.floor(x/8)+dx+i,math.floor(y/8)+dy+j) for i in range(16) for j in range(16)]
                    revision+=1;node.send(f'TERRAIN2 {revision} {len(pts)} '+' '.join(f'{a} {b} 0 0 256' for a,b in pts))
            fixture.wait([node],lambda s:s[0]['terrainRevision']>=revision)
            template='AmericaVehicleHumvee' if x==82 else 'AmericaInfantryRanger';count=1 if x==82 else 3
            for i in range(count):node.send(f'TEST_SPAWN {template} {x} {y+i*12} 0')
            state=fixture.wait([node],lambda s:len([a for a in s[0]['objects'] if a['template']==template and abs(a['x']-x)<2])==count)[0]
            units=[a['id'] for a in state['objects'] if a['template']==template and abs(a['x']-x)<2]
            gx,gy=(x-112,y+120) if x==82 else (x+320,y+20)
            # User's dump: Humvee at82,544, LMB at-28,664, crossing the stock map's x=0 edge.
            node.send('SELECT '+' '.join(map(str,units)));ticket=node.send(f'MOVE {gx} {gy} 0')
            state=fixture.wait([node],lambda s:s[0]['consumed']>=ticket and all(any(a['id']==u and math.hypot(a['x']-gx,a['y']-gy)<32 for a in s[0]['objects']) for u in units),45)[0]
            checks.append(f'{count} original {template} move from {x},{y} to {gx},{gy}, including the real crash geometry across x=0')
        report['result']='PASS'
    except BaseException as error:report['error']=repr(error)
    finally:
        if node:node.close()
        target=Path(__file__).resolve().parents[1]/'evidence'/args.report;target.write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
    return 0 if report['result']=='PASS' else 1

if __name__=='__main__':
    # Private child only: keep TEST_SPAWN enabled while disabling lockstep frames.
    original=fixture.subprocess.Popen
    def launch(*args,**kwargs):
        kwargs['env']['ZHMC_TEST']='1';kwargs['env'].pop('ZHMC_SANDBOX',None)
        return original(*args,**kwargs)
    fixture.subprocess.Popen=launch
    raise SystemExit(main())
