"""Audit a release JAR: only one verifiable source-built EXE, no retail content or development fixtures."""
import argparse,hashlib,json,struct,sys,zipfile
from pathlib import Path

def imports(data):
    pe=struct.unpack_from('<I',data,0x3c)[0]
    assert data[pe:pe+4]==b'PE\0\0','Invalid PE image'
    machine,sections=struct.unpack_from('<HH',data,pe+4)
    size=struct.unpack_from('<H',data,pe+20)[0];optional=pe+24
    assert machine==0x14c and struct.unpack_from('<H',data,optional)[0]==0x10b,'Engine must be Windows x86'
    table=optional+size
    def offset(rva):
        for i in range(sections):
            virtual,address,raw_size,raw=struct.unpack_from('<IIII',data,table+40*i+8)
            if address<=rva<address+max(virtual,raw_size):return raw+rva-address
        raise AssertionError('Invalid PE directory RVA')
    directory=struct.unpack_from('<I',data,optional+104)[0]
    names=[];entry=offset(directory)
    while any(data[entry:entry+20]):
        name=offset(struct.unpack_from('<I',data,entry+12)[0])
        names.append(data[name:data.index(b'\0',name)].decode('ascii'))
        entry+=20
    return sorted(names)

p=argparse.ArgumentParser();p.add_argument('jar',type=Path);args=p.parse_args()
binary='generalsmc/runtime/windows-x86/generalszh.exe'
with zipfile.ZipFile(args.jar) as z:
    names=z.namelist();assert not [n for n in names if n.lower().endswith('.md')],'Markdown must stay outside player JARs'
    meta=json.loads(z.read('fabric.mod.json'))
    forbidden=[n for n in names if Path(n).suffix.lower() in {'.big','.w3d','.dds','.tga','.wav','.mp3','.bik','.dll','.exe'} and n!=binary]
    development=[n for n in names if ('Bench' in n and n.endswith('.class')) or '/sim/' in n or n.endswith(('/RtsScreen.class','/RtsWorldRenderer.class','/Messages.class'))]
    props=dict(line.split('=',1) for line in z.read('generalsmc/runtime/windows-x86/runtime.properties').decode().splitlines() if '=' in line)
    engine=z.read(binary);digest=hashlib.sha256(engine).hexdigest()
    assert engine[:2]==b'MZ' and digest==props['sha256'],'Invalid bundled runtime'
    dependencies=imports(engine)
    assert not any(n.lower().startswith(('msvcp','msvcr','vcruntime','ucrtbase','api-ms-win-crt')) for n in dependencies),'Engine requires a separate Visual C++ runtime'
    assert not forbidden and not development,(forbidden,development)
    assert meta['id']=='generalsmc' and meta['depends']['minecraft'] and meta.get('license')=='GPL-3.0-or-later'
    for resource in ['local/generalsmc/nativebridge/NativeRuntime.class','local/generalsmc/nativebridge/WorldRules.class','local/generalsmc/client/WorldSettingsScreen.class','assets/generalsmc/blockstates/supply_marker.json']:
        assert resource in names,resource
report={'result':'PASS','jar':args.jar.name,'sha256':hashlib.sha256(args.jar.read_bytes()).hexdigest(),'bytes':args.jar.stat().st_size,'entries':len(names),'nativeSha256':digest,'nativeDependencies':dependencies,'minecraft':meta['depends']['minecraft'],'developmentClasses':development,'retailFiles':forbidden}
out=Path('build/release');out.mkdir(parents=True,exist_ok=True);(out/'release-audit.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');(out/(args.jar.name+'.sha256')).write_text(report['sha256']+'  '+args.jar.name+'\n',encoding='utf-8')
print(json.dumps(report));sys.exit(0)
