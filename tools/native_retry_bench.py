"""Measure a retained original move after its previously unknown route becomes loaded."""
from pathlib import Path
import argparse,hashlib,json,math,time
import native_lockstep_bench as fixture

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--allow-slow',action='store_true');parser.add_argument('--report',default='native-retry-tests.json');args=parser.parse_args()
    node=None;checks=[];report={'result':'FAIL','checks':checks,'nativeSha256':hashlib.sha256(fixture.EXE.read_bytes()).hexdigest()}
    try:
        node=fixture.Node(2,lockstep=False,graphics=True);report['session']=str(node.directory)
        state=fixture.wait([node],lambda s:s[0].get('maps'))[0];name=next(m for m in state['maps'] if 'tournament desert' in m.lower())
        node.send('START '+' '.join(json.dumps(x) for x in (name,'FactionAmerica','FactionChina'))+' 12345 10000')
        fixture.wait([node],lambda s:s[0]['inGame'] and s[0]['frame']>5);node.send('SCENE 82 544 1')
        rev=0
        def terrain(x0,x1,y0,y1):
            nonlocal rev
            for x in range(x0,x1,16):
                for y in range(y0,y1,16):
                    rev+=1;node.send(f'TERRAIN2 {rev} 256 '+' '.join(f'{x+i} {y+j} 0 0 256' for i in range(16) for j in range(16)))
            return rev
        terrain(0,32,48,112);fixture.wait([node],lambda s:s[0]['terrainRevision']>=rev)
        node.send('TEST_SPAWN AmericaInfantryRanger 82 544 0');state=fixture.wait([node],lambda s:any(a['template']=='AmericaInfantryRanger' for a in s[0]['objects']))[0];unit=next(a['id'] for a in state['objects'] if a['template']=='AmericaInfantryRanger')
        node.send(f'SELECT {unit}');node.send('MOVE -30 664 0');first=state['frame']
        def actor(s):return next(a for a in s['objects'] if a['id']==unit)
        state=fixture.wait([node],lambda ss:ss[0]['frame']>first+45 and actor(ss[0])['x']<6 and actor(ss[0])['goalX']<0,15)[0]
        anchor=actor(state);frame=state['frame']
        state=fixture.wait([node],lambda ss:ss[0]['frame']>frame+20,5)[0]
        assert math.hypot(actor(state)['x']-anchor['x'],actor(state)['y']-anchor['y'])<2,'Unit must settle at the unloaded boundary before publication'
        before=actor(state);report['waitingActor']=before;report['waitingFrame']=state['frame']
        started=time.monotonic();terrain(-16,0,48,112)
        state=fixture.wait([node],lambda ss:ss[0]['terrainRevision']>=rev and math.hypot(actor(ss[0])['x']-before['x'],actor(ss[0])['y']-before['y'])>.1,10)[0]
        latency=round((time.monotonic()-started)*1000);report['terrainToMovementMillis']=latency;report['resumeFrame']=state['frame']
        if latency>750 and not args.allow_slow:raise AssertionError(f'Loaded route still waited {latency}ms before original movement')
        checks.append(f'Previously unknown route resumes the retained original Ranger movement {latency}ms after terrain publication, without sending another MOVE')
        node.send('KEY 31 1');ticket=node.send('KEY 31 0');state=fixture.wait([node],lambda ss:ss[0]['consumed']>=ticket and not actor(ss[0])['groundMovement'])[0];stopped=actor(state);frame=state['frame']
        terrain(-16,0,48,112);state=fixture.wait([node],lambda ss:ss[0]['frame']>frame+45)[0];after=actor(state)
        assert math.hypot(after['x']-stopped['x'],after['y']-stopped['y'])<2,(stopped,after)
        checks.append('The real original Stop key cancels the retained intent; later terrain updates do not restart movement')
        report['result']='REPRODUCED_DELAY' if args.allow_slow and latency>750 else 'PASS'
    except BaseException as error:report['error']=repr(error)
    finally:
        if node:node.close()
        (Path(__file__).resolve().parents[1]/'evidence'/args.report).write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
    return 0 if report['result']!='FAIL' else 1

if __name__=='__main__':
    original=fixture.subprocess.Popen
    def launch(*args,**kwargs):
        kwargs['env']['ZHMC_TEST']='1';kwargs['env'].pop('ZHMC_SANDBOX',None);return original(*args,**kwargs)
    fixture.subprocess.Popen=launch
    raise SystemExit(main())
