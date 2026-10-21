"""One build entry point for Make, local terminals and release packaging."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def properties(path):
    return dict(line.strip().split('=', 1) for line in path.read_text(encoding='utf-8').splitlines()
                if '=' in line and not line.lstrip().startswith('#'))


def profiles():
    return sorted((p.stem for p in (ROOT / 'versions').glob('*.properties')),
                  key=lambda value: tuple(map(int, value.split('.'))))


def version():
    value = properties(ROOT / 'gradle.properties')['mod_version']
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.-]+)?', value):
        raise ValueError('Invalid mod_version in gradle.properties')
    return value


def run(args):
    print('> ' + subprocess.list2cmdline([str(a) for a in args]), flush=True)
    subprocess.run([str(a) for a in args], cwd=ROOT, check=True)


def gradle(task, target=None, native=None):
    command = [ROOT / 'gradlew.bat'] if os.name == 'nt' else ['bash', ROOT / 'gradlew']
    command += [task, '--console=plain']
    if target:
        command += ['-Pminecraft_target=' + target]
    if native:
        command += ['-PnativeBinary=' + str(native)]
    run(command)


def engine(path):
    if path:
        binary = Path(path).resolve()
        if not binary.is_file():
            raise ValueError('NATIVE_BIN does not exist: ' + str(binary))
        return binary
    if os.name != 'nt':
        raise ValueError('Building the engine requires Windows. Supply NATIVE_BIN=/path/to/source-built/generalszh.exe for a mod-only build.')
    shell = shutil.which('pwsh.exe') or shutil.which('powershell.exe')
    if not shell:
        raise ValueError('PowerShell is required to build the Windows engine')
    run([shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ROOT / 'Build-Native.ps1',
         '-NoStage', '-NativeRoot', ROOT / 'build/engine-cache'])
    return ROOT / 'build/native/windows-x86/generalszh.exe'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def bundle(source, output):
    current = version()
    packages = {}
    engines = set()
    sources = set()
    copies = []
    for audit_file in source.rglob('release-audit.json'):
        audit = json.loads(audit_file.read_text(encoding='utf-8'))
        target = audit.get('minecraft')
        if target not in profiles():
            continue
        expected = f'generals-minecraft-{current}+mc{target}.jar'
        if audit.get('jar') != expected:
            continue  # Retain older candidates; they cannot enter this release.
        if target in packages:
            raise ValueError('Duplicate Minecraft package: ' + target)
        if audit.get('result') != 'PASS':
            raise ValueError('Failed package: ' + str(audit_file))
        jar = audit_file.parent / expected
        if sha(jar) != audit['sha256']:
            raise ValueError('JAR checksum mismatch: ' + str(jar))
        with zipfile.ZipFile(jar) as archive:
            runtime = archive.read('generalsmc/runtime/windows-x86/generalszh.exe')
            if hashlib.sha256(runtime).hexdigest() != audit['nativeSha256']:
                raise ValueError('Bundled engine does not match its audit')
        source_zip = audit_file.parent / f'generals-minecraft-{current}+mc{target}-source.zip'
        with zipfile.ZipFile(source_zip) as archive:
            if ('mod_version=' + current) not in archive.read('gradle.properties').decode('utf-8').splitlines():
                raise ValueError('Corresponding source has a different mod version')
        sources.add(sha(source_zip))
        engines.add(audit['nativeSha256'])
        copies += [jar]
        packages[target] = {'jar': jar.name, 'sha256': sha(jar)}
    if set(packages) != set(profiles()):
        raise ValueError('Missing packages: ' + ', '.join(sorted(set(profiles()) - set(packages))))
    if len(engines) != 1 or len(sources) != 1:
        raise ValueError('Packages must share exactly one engine and corresponding source archive')
    output.mkdir(parents=True, exist_ok=True)
    if any((output / p.name).resolve() == p.resolve() for p in copies):
        raise ValueError('Release output must differ from the input packages')
    for item in copies:
        shutil.copy2(item, output / item.name)
    shutil.copy2(source_zip, output / f'generals-minecraft-{current}-source.zip')
    for name in ('README.md', 'README.ru.md', 'CHANGELOG.md','CHANGELOG.ru.md', 'LICENSE.md','LICENSE.ru.md', 'THIRD_PARTY_NOTICES.md','THIRD_PARTY_NOTICES.ru.md'):
        shutil.copy2(ROOT / name, output / name)
    docs = output / 'docs'
    docs.mkdir(exist_ok=True)
    for name in ('INSTALL.md','INSTALL.ru.md', 'CONTROLS.md','CONTROLS.ru.md', 'KNOWN_ISSUES.md','KNOWN_ISSUES.ru.md', 'DEVELOPMENT.md','DEVELOPMENT.ru.md'):
        shutil.copy2(ROOT / 'docs' / name, docs / name)
    shutil.copytree(ROOT / 'docs/media', docs / 'media', dirs_exist_ok=True)
    report = {'version': current, 'minecraft': packages, 'nativeSha256': engines.pop(),
              'sourceSha256': sources.pop(), 'checks': 'Build, logic checks and package audits passed. Runtime tests are separate.',
              'published': False}
    (output / 'BUILD.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    (output / 'SHA256SUMS.txt').write_text(''.join(sha(p) + '  ' + p.name + '\n' for p in sorted(output.iterdir())
                                               if p.suffix in ('.jar', '.zip')), encoding='utf-8')
    print('Release files: ' + str(output), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['all', 'build', 'native', 'release', 'package', 'source', 'versions', 'check', 'smoke', 'help', 'validate-tag'])
    parser.add_argument('--minecraft', default='')
    parser.add_argument('--native-bin', default='')
    parser.add_argument('--output', default='')
    parser.add_argument('--input', default='build/release/player')
    parser.add_argument('--tag', default='')
    args = parser.parse_args()
    selected = args.minecraft or properties(ROOT / 'gradle.properties')['minecraft_target']
    if args.action == 'help':
        print('make / make all       Build the engine once and every supported Minecraft JAR\n'
              'make build MC=26.3    Build one Minecraft version (default from gradle.properties)\n'
              'make release         Build all versions and collect dist/<mod-version>/\n'
              'make package         Collect already built and audited packages\n'
              'make source          Export the clean source ZIP\n'
              'make versions        List supported versions\n'
              'make check MC=26.3    Build and run asset-free checks/audit\n'
              'make smoke MC=26.3    Run the real-client local test (needs your own game)\n'
              'Optional: PYTHON=python, NATIVE_BIN=/path/to/source-built/exe, OUTPUT=/path/to/bundle')
        return
    if args.action == 'versions':
        print('\n'.join(v + (' (default)' if v == selected else '') for v in profiles()))
        return
    if args.action == 'validate-tag':
        if args.tag != 'v' + version():
            raise ValueError('Tag must match gradle.properties: v' + version())
        print('Validated ' + args.tag)
        return
    if selected not in profiles():
        raise ValueError('Unsupported Minecraft profile: ' + selected + '. Run make versions.')
    if args.action == 'source':
        gradle('releaseSources', selected)
        source_zip = ROOT / 'build/release' / f'generals-minecraft-{version()}+mc{selected}-source.zip'
        export = source_zip.with_name(f'generals-minecraft-{version()}-source.zip')
        shutil.copy2(source_zip, export)
        print('Source export: ' + str(export))
        return
    output = Path(args.output).resolve() if args.output else ROOT / 'dist' / version()
    if args.action == 'package':
        bundle((ROOT / args.input).resolve(), output)
        return
    native = engine(args.native_bin)
    if args.action == 'native':
        return
    targets = profiles() if args.action in ('all', 'release') else [selected]
    for target in targets:
        gradle('productionSmoke' if args.action == 'smoke' else 'stageRelease', target, native)
    if args.action == 'release':
        bundle(ROOT / 'build/release/player', output)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError, KeyError) as error:
        print('Build failed: ' + str(error), file=sys.stderr)
        sys.exit(1)
