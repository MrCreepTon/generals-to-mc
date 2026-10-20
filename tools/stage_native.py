"""Private native lab using original BIG archives, with ZH-before-base mount order.
The engine opens archives for reading; hard links avoid duplicating gigabytes.
Hard links themselves do NOT enforce write protection.
Never places a loader, binary, INI or converted asset into either user's install.
"""
from pathlib import Path
import argparse
import json
import shutil
import filecmp

STOCK = {'audio', 'audioenglish', 'english', 'ini', 'maps', 'music', 'patch',
         'shaders', 'speech', 'speechenglish', 'terrain', 'textures', 'w3d',
         'w3denglish', 'window', 'gensec'}

def copy_changed(source, destination):
    # A repeat staging operation must not rewrite already loaded identical DLLs.
    if not Path(destination).exists() or not filecmp.cmp(source, destination, shallow=False):
        shutil.copy2(source, destination)
    return destination

def stage(source: Path, target: Path, suffix: str):
    target.mkdir(parents=True, exist_ok=True)
    mounted = []
    for file in source.glob('*.big'):
        stem = file.stem.casefold()
        if suffix and stem.endswith(suffix):
            stem = stem[:-len(suffix)]
        if stem not in STOCK:
            continue
        destination = target / file.name
        if not destination.exists():
            destination.hardlink_to(file)
        mounted.append({'archive': str(file), 'mount': str(destination), 'bytes': file.stat().st_size})
    for folder in ('Cursors', 'Scripts', 'WaterPlane', 'English'):
        src = source / 'Data' / folder
        if src.exists():
            shutil.copytree(src, target / 'Data' / folder, dirs_exist_ok=True, copy_function=copy_changed)
    # Miles loads its original 3D providers, MP3/voice codecs and DSP from MSS\.
    if (source / 'MSS').is_dir():
        shutil.copytree(source / 'MSS', target / 'MSS', dirs_exist_ok=True, copy_function=copy_changed)
    for name in ('langdata.dat', 'BINKW32.DLL', 'mss32.dll'):
        file = source / name
        if file.exists():
            copy_changed(file, target / name)
    return mounted

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--zh', type=Path, default=Path('C:/Games/Generals ZH'))
    parser.add_argument('--base', type=Path, default=Path('C:/Games/Generals'))
    parser.add_argument('--lab', type=Path, default=Path.home() / 'AppData/Local/GeneralsMinecraft/native-lab')
    args = parser.parse_args()
    for directory, name in ((args.zh, 'INIZH.big'), (args.zh, 'W3DZH.big'), (args.base, 'INI.big'), (args.base, 'W3D.big')):
        if not (directory / name).is_file():
            raise FileNotFoundError(f'Required original archive is absent: {directory / name}')
    lab = args.lab.resolve()
    for original in (args.zh.resolve(), args.base.resolve()):
        if lab == original or lab.is_relative_to(original):
            raise ValueError('Lab must be outside both original installations')
    records = stage(args.zh, lab / 'zh', 'zh') + stage(args.base, lab / 'base', '')
    (lab / 'userdata').mkdir(exist_ok=True)
    (lab / 'mounts.json').write_text(json.dumps(records, indent=2), encoding='utf-8')
    print(f'{len(records)} original BIG archives mounted in private lab {lab}')

if __name__ == '__main__':
    main()
