"""Three owned source processes: authority and two independently controlled human armies.

This is the native determinism/command gate oracle, not a Minecraft multiplayer playthrough.
"""
from pathlib import Path
import hashlib,json,math,os,shutil,subprocess,time,uuid,sys

ROOT=Path(os.environ['LOCALAPPDATA'])/'GeneralsMinecraft'
EXE=ROOT/'native-src/GeneralsGameCode/build/minecraft-bridge/GeneralsMD/Release/generalszh.exe'
LAB=ROOT/'native-lab'
EVIDENCE=Path(__file__).resolve().parents[1]/'evidence'

class Node:
    def __init__(self,player,authority=False,lockstep=True,graphics=False,sandbox=True):
        self.directory=LAB/'sessions'/str(uuid.uuid4());(self.directory/'commands').mkdir(parents=True);(self.directory/'frames').mkdir();(self.directory/'userdata').mkdir()
        for p in (LAB/'userdata').glob('*.ini'):shutil.copy2(p,self.directory/'userdata'/p.name)
        self.player=player;self.sequence=self.outgoing=0;self.state=None
        env=dict(os.environ,ZHMC_BRIDGE_DIR=str(self.directory),ZHMC_BASE_DATA=str(LAB/'base'),ZHMC_USER_DATA=str(self.directory/'userdata'),ZHMC_PARENT_PID=str(os.getpid()),ZHMC_PAGED='1',ZHMC_WORLD_SCALE='8',ZHMC_SANDBOX='1',ZHMC_TEST='1',ZHMC_LOCKSTEP='1',ZHMC_LOCAL_PLAYER=str(player))
        if authority:env['ZHMC_AUTHORITY']='1'
        if not sandbox:env.pop('ZHMC_SANDBOX')
        if not lockstep:env.pop('ZHMC_LOCKSTEP');env.pop('ZHMC_TEST')
        if graphics:env['ZHMC_CAPTURE']='1'
        self.log=(self.directory/'process.log').open('w')
        mode=['-win','-xres','800','-yres','600'] if graphics else ['-headless']
        self.process=subprocess.Popen([str(EXE),*mode,'-noaudio','-nologo','-noshellmap','-setCwd',str(LAB/'zh')],env=env,cwd=LAB/'zh',stdout=self.log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    def send(self,text):
        self.sequence+=1;file=self.directory/'commands'/f'{self.sequence}.tmp';file.write_text(text,encoding='utf-8');file.replace(file.with_suffix('.cmd'));return self.sequence
    def read(self):
        if self.process.poll() is not None:raise RuntimeError(f'Owned native process {self.process.pid} exited {self.process.returncode}: {self.directory}')
        try:self.state=json.loads((self.directory/'state.json').read_text(encoding='utf-8'))
        except (FileNotFoundError,PermissionError,json.JSONDecodeError):pass
        if self.state and (self.state.get('error') or self.state.get('lockstepError')):raise RuntimeError(self.state.get('error') or self.state.get('lockstepError'))
        return self.state
    def commands(self):
        commands=[]
        while (self.directory/'outbox'/f'{self.outgoing+1}.msg').is_file():
            self.outgoing+=1;commands.append(f'C {self.player} '+(self.directory/'outbox'/f'{self.outgoing}.msg').read_text().strip())
        return commands
    def close(self):
        if self.process.poll() is None:
            self.send('QUIT')
            try:self.process.wait(timeout=8)
            except subprocess.TimeoutExpired:self.process.terminate();self.process.wait(timeout=5)
        self.log.close()

def wait(nodes,predicate,timeout=45):
    until=time.monotonic()+timeout
    while time.monotonic()<until:
        states=[n.read() for n in nodes]
        if all(states) and predicate(states):return states
        time.sleep(.015)
    raise TimeoutError('Native lockstep predicate; last frames '+str([s.get('frame') if s else None for s in states]))

def main():
    nodes=[];checks=[];report={'result':'FAIL','checks':checks,'nativeSha256':hashlib.sha256(EXE.read_bytes()).hexdigest(),'scope':'Native-only source lockstep, private file relay used as a server oracle. Does not claim Minecraft packets, lobby, two Minecraft clients or NPC skirmishes are complete.'}
    try:
        for player,authority in ((2,True),(2,False),(3,False)):nodes.append(Node(player,authority,graphics=not authority and '--graphics-peers' in sys.argv))
        report['sessions']=[str(n.directory) for n in nodes]
        states=wait(nodes,lambda s:all(x.get('maps') for x in s));map_name=next(m for m in states[0]['maps'] if 'tournament desert' in m.lower())
        for n in nodes:n.send('START '+' '.join(json.dumps(x) for x in (map_name,'FactionGLA' if '--gla' in sys.argv else 'FactionAmerica','FactionChina'))+' 12345 10000')
        states=wait(nodes,lambda s:all(x.get('inGame') and x.get('lockstep') and len(x.get('objects',[]))>5 for x in s))
        time.sleep(.2);states=[n.read() for n in nodes];frame=states[0]['frame'];assert all(s['frame']==frame for s in states),[s['frame'] for s in states]
        for n in nodes:n.send('SCENE 0 0 1')
        checks.append('Three concurrent original processes start the same two-human match and wait at the same native frame')
        def commit(world=()):
            nonlocal frame,states
            records=['W '+x for x in world]
            for n in nodes[1:]:records+=n.commands()
            text='FRAME '+str(frame)+'\n'+'\n'.join(records)+'\n'
            for n in nodes:
                p=n.directory/'frames'/f'{frame}.tmp';p.write_text(text,encoding='utf-8');p.replace(p.with_suffix('.frame'))
            states=wait(nodes,lambda s:all(x['frame']>frame for x in s),10)
            assert len({s['frame'] for s in states})==1,[s['frame'] for s in states]
            frame=states[0]['frame']
            # Record any mismatch for inspection rather than disguising a drift as a working network mode.
            assert len({s['lockstepCrc'] for s in states})==1,{'frame':frame,'crc':[s['lockstepCrc'] for s in states]}
        world=['INIT_WORLD'];rev=0
        for a in states[0]['objects']:
            if a['owner'] in (2,3) and a['template'].endswith('CommandCenter'):
                bx=int(a['x']//8)-16;by=int(a['y']//8)-16;rev+=1
                world.append('TERRAIN3 '+str(rev)+' 1024 '+' '.join(f'{x} {y} 0 0 256 0' for x in range(bx,bx+32) for y in range(by,by+32)))
        commit(world)
        checks.append('Identical server world initialization and imported terrain preserve original simulation CRC across all participants')
        wait(nodes,lambda s:s[1]['localPlayerIndex']==2 and s[2]['localPlayerIndex']==3)
        units=[next(a for a in states[0]['objects'] if a['owner']==owner and ('Dozer' in a['template'] or 'Worker' in a['template'])) for owner in (2,3)]
        goals=[]
        for n,a in zip(nodes[1:],units):
            n.send(f'SELECT {a["id"]}');goal=(a['x']+48,a['y']+24,0);goals.append(goal);n.send(f'MOVE {goal[0]} {goal[1]} 0')
        wait(nodes,lambda s:all((n.directory/'outbox/1.msg').exists() for n in nodes[1:]),5)
        for _ in range(160):commit()
        for a,goal in zip(units,goals):
            moved=next(o for o in states[0]['objects'] if o['id']==a['id'])
            assert math.hypot(moved['x']-goal[0],moved['y']-goal[1])<12,(moved,goal)
        checks.append('Two human players select their own original dozers and move independently through relayed native commands')
        checks.append('Periodic original simulation CRC checkpoints agree on authority and both participants across160 committed frames')
        if '--combat' in sys.argv:
            cc=next(a for a in states[0]['objects'] if a['owner']==2 and a['template'].endswith('CommandCenter'))
            x,y=cc['x'],cc['y']-80
            for node in nodes[1:]:
                own=next(a for a in states[0]['objects'] if a['owner']==node.player and a['template'].endswith('CommandCenter'));node.send(f'SCENE {own["x"]} {own["y"]} 450')
            commit([f'TEST_SPAWN AmericaInfantryRanger {x} {y} 0 2',f'TEST_SPAWN ChinaInfantryRedguard {x+64} {y} 0 3'])
            victims={a['id']:a['health'] for a in states[0]['objects'] if a['template'] in ('AmericaInfantryRanger','ChinaInfantryRedguard')}
            for _ in range(240):commit()
            assert any(a['id'] not in {o['id'] for o in states[0]['objects']} or next(o['health'] for o in states[0]['objects'] if o['id']==a['id'])<hp for a_id,hp in victims.items() for a in [{'id':a_id}]),victims
            checks.append('Original infantry weapons, launch bones, damage and death retain matching periodic CRC checkpoints across240 combat frames with different participant cameras')
        report['graphicsPeers']='--graphics-peers' in sys.argv
        report['glaWorker']='--gla' in sys.argv
        report.update(result='PASS',frame=frame,crcs=[s['lockstepCrc'] for s in states],outgoing=[n.outgoing for n in nodes])
    except BaseException as e:
        report['error']=repr(e)
        for i,n in enumerate(nodes):
            if n.state:(EVIDENCE/f'native-lockstep-failure-{i}.json').write_text(json.dumps(n.state,indent=2),encoding='utf-8')
        raise
    finally:
        for n in nodes:n.close()
        (EVIDENCE/'native-lockstep-tests.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))

if __name__=='__main__':main()
