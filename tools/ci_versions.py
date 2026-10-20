"""Generate the Actions build matrix from verified version profiles."""
from pathlib import Path
import json,re,argparse

parser=argparse.ArgumentParser()
parser.add_argument('--target',default='all',help='all, or one existing verified Minecraft profile')
args=parser.parse_args()

root=Path(__file__).resolve().parents[1]
profiles=sorted((root/'versions').glob('*.properties'))
assert profiles,'No supported Minecraft profiles'
versions=[]
for file in profiles:
    assert re.fullmatch(r'[0-9]+(?:\.[0-9]+){1,2}',file.stem),file
    values=dict(line.strip().split('=',1) for line in file.read_text().splitlines() if '=' in line and not line.lstrip().startswith('#'))
    assert values['minecraft']==file.stem and all(values.get(key) for key in ['loader','fabric_api','java']),file
    versions.append({'minecraft':file.stem})
if args.target!='all':
    versions=[v for v in versions if v['minecraft']==args.target]
    if not versions:parser.error('Unsupported profile; choose all or an existing file in versions/')
print('matrix='+json.dumps({'include':versions},separators=(',',':')))
