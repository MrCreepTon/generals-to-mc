"""Check clean pinned-source integration and idempotence in memory. No checkout copies."""
from pathlib import Path
from unittest.mock import patch
import argparse
import contextlib
import io
import json
import subprocess
import sys
import prepare_engine


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, default=Path(__file__).resolve().parents[1] / 'build/engine-cache/native-src/GeneralsGameCode')
    args = parser.parse_args()
    source = args.source.resolve()
    original_read = Path.read_text
    memory = {}
    originals = {}

    def relative(path):
        return path.resolve().relative_to(source).as_posix()

    def read(path, *args, **kwargs):
        try:
            name = relative(path)
        except ValueError:
            return original_read(path, *args, **kwargs)
        if name not in memory:
            blob = subprocess.check_output(['git', '-C', str(source), 'show', prepare_engine.PIN + ':' + name])
            memory[name] = blob.decode('utf-8-sig').replace('\r\n', '\n')
            originals[name] = memory[name]
        return memory[name]

    def write(path, value, *args, **kwargs):
        # Refuse writes outside the emulated pinned checkout.
        name = relative(path)
        if name not in memory:
            read(path)
        memory[name] = value
        return len(value)

    with patch.object(Path, 'read_text', read), patch.object(Path, 'write_text', write), patch.object(sys, 'argv', ['prepare_engine.py', str(source)]), contextlib.redirect_stdout(io.StringIO()):
        prepare_engine.main()
        first = dict(memory)
        prepare_engine.main()
        second = dict(memory)
    changed = {name: value for name, value in first.items() if value != originals[name]}
    differences = [name for name, value in changed.items() if original_read(source / name, encoding='utf-8-sig') != value]
    report = {'result': 'PASS' if first == second and not differences else 'FAIL',
              'pin': prepare_engine.PIN, 'files': len(changed), 'idempotent': first == second,
              'activeDifferences': differences, 'scope': 'Pinned Git blobs patched in memory; no installation, build or world copied.'}
    destination = Path(__file__).resolve().parents[1] / 'evidence/native-patch-tests.json'
    destination.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report))
    return 0 if report['result'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
