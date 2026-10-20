"""Real engine mouse press snapshot and Tank dozer movement regression."""
from pathlib import Path
import hashlib, json, math, time
import native_lockstep_bench as fixture

original = fixture.subprocess.Popen
def launch(*args, **kwargs):
    kwargs['env']['ZHMC_TEST'] = '1'
    return original(*args, **kwargs)
fixture.subprocess.Popen = launch

n = fixture.Node(2, graphics=True, lockstep=False)
report = {'result': 'FAIL', 'checks': [], 'session': str(n.directory),
          'nativeSha256': hashlib.sha256(fixture.EXE.read_bytes()).hexdigest()}
try:
    state = fixture.wait([n], lambda s: bool(s[0].get('maps')))[0]
    name = next(m for m in state['maps'] if 'tournament desert' in m.lower())
    n.send('START ' + json.dumps(name) + ' "FactionChinaTankGeneral" "FactionGLA" 12345 10000')
    state = fixture.wait([n], lambda s: s[0]['inGame'] and s[0]['frame'] > 40)[0]
    cc = next(a for a in state['objects'] if a['owner'] == 2 and a['structure'])
    worker = next(a for a in state['objects'] if a['owner'] == 2 and 'Dozer' in a['template'])
    n.send(f'SCENE {cc["x"]} {cc["y"]} 500')
    n.send('INIT_WORLD')
    bx, by = int(worker['x']//8)-16, int(worker['y']//8)-16
    n.send('TERRAIN3 1 1024 ' + ' '.join(f'{x} {y} 0 0 256 0' for x in range(bx,bx+32) for y in range(by,by+32)))
    time.sleep(.3)
    def sample(clicked, clicked_id, hover, hover_id):
        output = n.directory/'project-test.json'
        output.unlink(missing_ok=True)
        p = ' '.join(map(str, clicked)); h = ' '.join(map(str, hover))
        n.send(f'WORLD_MOUSE 513 {p} {clicked_id} 1 400 200')
        n.send(f'WORLD_MOUSE 514 {p} {clicked_id} 0 400 200')
        n.send(f'WORLD_MOUSE 512 {h} {hover_id} 0 400 200')
        n.send('TEST_PROJECT 400 200')
        fixture.wait([n], lambda _: output.is_file())
        result = json.loads(output.read_text())
        assert result['valid'] and result['picked'] == clicked_id, result
        assert all(abs(result[k]-v)<.01 for k,v in zip(('x','y','z'),clicked)), result
        return result
    ground = (worker['x']-48, worker['y']-48, 0)
    building = (cc['x'],cc['y'],cc['z'])
    sample(ground, 0, building, cc['id'])
    report['checks'].append('Ground click keeps its coordinates and zero target after a later building hover at the same screen pixel')
    time.sleep(.3)
    sample(building, cc['id'], ground, 0)
    report['checks'].append('Building click keeps its object and coordinates after a later ground hover')
    time.sleep(.3)
    output = n.directory/'project-test.json'; output.unlink()
    n.send('TEST_PROJECT 400 200')
    fixture.wait([n], lambda _: output.is_file())
    result = json.loads(output.read_text())
    assert result['picked'] == 0 and abs(result['x']-ground[0])<.01, result
    report['checks'].append('Released press expires after the bounded simulation window and live hover resumes')
    # Derive a fresh outward destination after the click/selection cases. A goal
    # inside the command-centre footprint is legitimately moved by the original AI.
    worker = next(a for a in n.read()['objects'] if a['id']==worker['id'])
    dx,dy=worker['x']-cc['x'],worker['y']-cc['y'];length=max(.01,math.hypot(dx,dy))
    ground=(worker['x']+dx/length*48,worker['y']+dy/length*48,0)
    report['destination']=ground
    n.send(f'TEST_PATH {worker["id"]} {ground[0]} {ground[1]} 0')
    fixture.wait([n],lambda _: (n.directory/'path-diagnostic.json').is_file())
    report['path'] = json.loads((n.directory/'path-diagnostic.json').read_text())
    n.send(f'SELECT {worker["id"]}')
    n.send(f'MOVE {ground[0]} {ground[1]} 0')
    state = fixture.wait([n], lambda s: any(a['id']==worker['id'] and math.hypot(a['x']-ground[0],a['y']-ground[1])<10 for a in s[0]['objects']), 15)[0]
    report['checks'].append('Actual Tank general dozer reaches a ground destination on imported Minecraft terrain')
    report['result'] = 'PASS'
except BaseException as error:
    report['error'] = repr(error)
    report['lastWorker'] = next((a for a in n.state.get('objects',[]) if a['id']==worker['id']), None) if n.state else None
finally:
    n.close()
    target = Path(__file__).resolve().parents[1]/'evidence/native-pointer-tests.json'
    target.parent.mkdir(exist_ok=True)
    target.write_text(json.dumps(report,indent=2))
    print(json.dumps(report))
if report['result'] != 'PASS':
    raise SystemExit(1)
