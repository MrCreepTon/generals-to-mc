"""Create or refresh a draft GitHub release after the tag build succeeds."""
import argparse
import json
from pathlib import Path
import subprocess
import sys

from build import ROOT, bundle, profiles, run, version


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--input', type=Path, default=ROOT / 'build/artifacts')
    parser.add_argument('--dry-run', action='store_true', help='Check assets and notes without calling GitHub')
    args = parser.parse_args()
    if args.tag != 'v' + version():
        raise ValueError('Release tag must match mod_version in gradle.properties')
    output = ROOT / 'dist' / version()
    bundle(args.input.resolve(), output)
    assets = sorted(output.glob('*.jar')) + sorted(output.glob('*-source.zip')) + [output / 'SHA256SUMS.txt', output / 'BUILD.json']
    if len(list(output.glob('*.jar'))) != len(profiles()):
        raise ValueError('The draft must include every supported Minecraft version')
    notes = output / 'RELEASE_NOTES.md'
    sections = []
    for language, filename, install in (
        ('English', 'CHANGELOG.md',
         'Choose the JAR for your exact Minecraft version. Windows and Fabric are required; '
         'Fabric API is bundled. Connect your own Generals + Zero Hour in the mod menu. '
         'Source and SHA256 are attached. CI checks builds and packages; game tests run locally.'),
        ('Русский', 'CHANGELOG.ru.md',
         'Выберите JAR для точной версии Minecraft. Нужны Windows и Fabric; Fabric API включён. '
         'Подключите собственную Generals + Zero Hour в меню мода. Исходники и SHA256 приложены. '
         'CI проверяет сборку и пакеты; игровые тесты выполняются локально.'),
    ):
        change = (ROOT / filename).read_text(encoding='utf-8').split('## ', 2)[1].strip()
        sections.append('<details>\n<summary>' + language + '</summary>\n\n## ' + change
                        + '\n\n' + install + '\n\n</details>')
    notes.write_text('\n\n'.join(sections) + '\n', encoding='utf-8')
    if args.dry_run:
        print(json.dumps({'tag': args.tag, 'draft': True, 'prerelease': '-' in version(),
                          'assets': [p.name for p in assets], 'notes': str(notes), 'githubCalled': False}, indent=2))
        return
    existing = subprocess.run(['gh', 'release', 'view', args.tag, '--json', 'isDraft'],
                              cwd=ROOT, capture_output=True, text=True)
    if existing.returncode == 0:
        if not json.loads(existing.stdout)['isDraft']:
            raise ValueError('The release is already public; refusing to change it')
        run(['gh', 'release', 'upload', args.tag, *assets, '--clobber'])
        run(['gh', 'release', 'edit', args.tag, '--title', 'Generals in Minecraft ' + args.tag,
             '--notes-file', notes])
    else:
        command = ['gh', 'release', 'create', args.tag, *assets, '--draft', '--verify-tag',
                   '--title', 'Generals in Minecraft ' + args.tag, '--notes-file', notes]
        if '-' in version():
            command += ['--prerelease']
        run(command)
    print('Draft ready. Review it on GitHub and publish it manually.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as error:
        print('Draft release failed: ' + str(error), file=sys.stderr)
        sys.exit(1)
