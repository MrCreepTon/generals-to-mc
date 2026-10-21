"""Read native timing and local IPC backlog without changing a running match."""
from pathlib import Path
import argparse
import json
import os
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    default = Path(os.environ.get('LOCALAPPDATA', Path.home() / '.local/share')) / 'GeneralsMinecraft'
    parser.add_argument('--root', type=Path, default=default)
    parser.add_argument('--seconds', type=float, default=30)
    args = parser.parse_args()
    if args.seconds <= 0:
        parser.error('--seconds must be positive')
    previous = {}
    deadline = time.monotonic() + args.seconds
    print('Native target: 30 Hz. IPC pending is local delivery, not network ping.', flush=True)
    while time.monotonic() < deadline:
        now = time.time()
        active = 0
        for path in args.root.glob('profiles/*/lab/sessions/*/state.json'):
            try:
                age = now - path.stat().st_mtime
                if age > 5:
                    continue
                state = json.loads(path.read_text(encoding='utf-8'))
                if not state.get('inGame'):
                    continue
                active += 1
                frame = state['frame']
                stamp = time.monotonic()
                before = previous.get(path)
                hz = None if before is None else (frame - before[0]) / (stamp - before[1])
                previous[path] = frame, stamp
                sent = max((int(p.stem) for p in path.parent.glob('commands/*.cmd') if p.stem.isdigit()), default=state['consumed'])
                pending = max(0, sent - state['consumed'])
                speed = 'warming' if hz is None else f'{hz:.1f} Hz'
                error = state.get('lockstepError') or state.get('error') or '-'
                print(f'{path.parent.name[:8]} frame={frame} native={speed} '
                      f'snapshot_age={age * 1000:.0f}ms ipc_pending={pending} '
                      f'lockstep={state.get("lockstep", False)} error={error}', flush=True)
            except (OSError, ValueError, KeyError):
                continue  # Atomic snapshot replacement can briefly hide a file on Windows.
        if not active:
            print('No recent running match. Check --root or start an RTS match.', flush=True)
        time.sleep(min(1, max(0, deadline - time.monotonic())))


if __name__ == '__main__':
    main()
