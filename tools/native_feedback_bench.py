"""Owned native regression fixtures for reported headless and world-adapter issues."""
from pathlib import Path
import argparse,hashlib,json,ctypes,uuid
import native_lockstep_bench as fixture

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--exe',type=Path);parser.add_argument('--report',default='native-feedback-tests.json');args=parser.parse_args()
    if args.exe:fixture.EXE=args.exe
    report={'result':'FAIL','checks':[],'nativeSha256':hashlib.sha256(fixture.EXE.read_bytes()).hexdigest()};node=None;other=None
    try:
        node=fixture.Node(2,lockstep=False);report['session']=str(node.directory)
        state=fixture.wait([node],lambda ss:ss[0].get('maps'))[0];name=next(m for m in state['maps'] if 'tournament desert' in m.lower())
        node.send('START '+' '.join(json.dumps(x) for x in (name,'FactionAmerica','FactionGLA'))+' 12345 10000')
        state=fixture.wait([node],lambda ss:ss[0]['inGame'] and ss[0]['frame']>5)[0]
        node.send('TEST_SPAWN SpySatellitePing 100 400 0');first=state['frame']
        fixture.wait([node],lambda ss:ss[0]['frame']>first+35,10)
        report['checks'].append('Original SpySatellitePing DynamicShroudClearingRangeUpdate remains active on the headless authority without dereferencing a graphical shadow manager')
        node.send('TEST_SPAWN AmericaInfantryRanger 100 400 40');state=fixture.wait([node],lambda ss:any(a['template']=='AmericaInfantryRanger' for a in ss[0]['objects']))[0];killer=next(a['id'] for a in state['objects'] if a['template']=='AmericaInfantryRanger');before=state['generalExperience'];rank=state['generalRank']
        # The pinned Zero Hour fast_float_ceil adds almost-one before truncating:
        # at float precision its unchanged Player.addSkillPoints(20) awards 21.
        key=str(uuid.uuid4());node.send(f'MOB_SYNC {key} 100 400 40 20 20 5 14 1 0 "Owned fixture"');ticket=node.send(f'MOB_KILLED {key} {killer} 20');state=fixture.wait([node],lambda ss:ss[0]['consumed']>=ticket)[0];assert state['generalExperience']==before+21,{'before':before,'after':state['generalExperience']}
        ticket=node.send(f'MOB_KILLED {key} {killer} 20');state=fixture.wait([node],lambda ss:ss[0]['consumed']>=ticket)[0];assert state['generalExperience']==before+21
        report['originalRoundedMobExperience']=21
        key=str(uuid.uuid4());node.send(f'MOB_SYNC {key} 100 400 40 20 20 5 14 1 0 "Owned fixture"');ticket=node.send(f'MOB_KILLED {key} {killer} 1000');state=fixture.wait([node],lambda ss:ss[0]['consumed']>=ticket)[0];assert state['generalRank']>rank
        report['checks'].append('Original Player.addSkillPoints awards one mob kill once, ignores duplicate kill acknowledgments and crosses the original general-rank threshold')
        node.close();assert node.process.returncode==0,{'headlessExit':node.process.returncode};node=fixture.Node(2,lockstep=False,graphics=True);report['graphicalSession']=str(node.directory)
        fixture.wait([node],lambda ss:ss[0].get('maps'))
        node.send('START '+' '.join(json.dumps(x) for x in (name,'FactionAmerica','FactionGLA'))+' 12345 10000')
        state=fixture.wait([node],lambda ss:ss[0]['inGame'] and ss[0]['frame']>20,20)[0]
        windows=[];user=ctypes.windll.user32
        @ctypes.WINFUNCTYPE(ctypes.c_bool,ctypes.c_void_p,ctypes.c_void_p)
        def window(hwnd,param):
            pid=ctypes.c_ulong();user.GetWindowThreadProcessId(ctypes.c_void_p(hwnd),ctypes.byref(pid))
            if pid.value==node.process.pid:
                namebuf=ctypes.create_unicode_buffer(256);user.GetClassNameW(ctypes.c_void_p(hwnd),namebuf,256)
                if namebuf.value=='Game Window':windows.append({'visible':bool(user.IsWindowVisible(ctypes.c_void_p(hwnd))),'extendedStyle':user.GetWindowLongW(ctypes.c_void_p(hwnd),-20)})
            return True
        user.EnumWindows(window,None);assert windows and all(not w['visible'] and w['extendedStyle']&0x80 for w in windows),windows
        report['windows']=windows
        report['checks'].append('The actual owned graphical engine window stays hidden with TOOLWINDOW/NOACTIVATE while its original logic advances, without OS focus or input automation')
        other=fixture.Node(2,lockstep=False,graphics=True);report['overlappingLocalSession']=str(other.directory)
        fixture.wait([other],lambda ss:ss[0].get('maps'))
        first=node.read()['frame'];other.send('START '+' '.join(json.dumps(x) for x in (name,'FactionAmerica','FactionGLA'))+' 12345 10000')
        fixture.wait([node,other],lambda ss:ss[0]['frame']>first+20 and ss[1]['inGame'] and ss[1]['frame']>20,20)
        other.close();assert other.process.returncode==0,{'overlappingExit':other.process.returncode};other=None
        node.close();assert node.process.returncode==0,{'graphicalExit':node.process.returncode};node=None
        report['checks'].append('Two actual non-lockstep graphical local sessions advance concurrently without the retail primary-instance conflict, then both shut down with exit code zero')
        report['result']='PASS'
    except BaseException as error:report['error']=repr(error)
    finally:
        if other:other.close()
        if node:node.close()
        (Path(__file__).resolve().parents[1]/'evidence'/args.report).write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
    return 0 if report['result']=='PASS' else 1

if __name__=='__main__':
    original=fixture.subprocess.Popen
    def launch(*args,**kwargs):
        kwargs['env']['ZHMC_TEST']='1';kwargs['env'].pop('ZHMC_SANDBOX',None);return original(*args,**kwargs)
    fixture.subprocess.Popen=launch
    raise SystemExit(main())
