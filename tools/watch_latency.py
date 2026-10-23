"""Read native timing and local IPC backlog without changing a running match."""
from pathlib import Path
import argparse
import json
import os
import time
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    default = Path(os.environ.get('LOCALAPPDATA', Path.home() / '.local/share')) / 'GeneralsMinecraft'
    parser.add_argument('--root', type=Path, default=default)
    parser.add_argument('--seconds', type=float, default=30)
    parser.add_argument('--capture', type=Path, help='Save native and Minecraft frame/click timings to one JSON report (5–60 seconds).')
    args = parser.parse_args()
    if args.seconds <= 0:
        parser.error('--seconds must be positive')
    if args.capture and not 5 <= args.seconds <= 60:
        parser.error('--capture requires --seconds between 5 and 60')
    previous = {}
    requests, samples = {}, []
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
                timings = {key: state.get(key) for key in ('engineUpdateMs', 'guiCaptureMs', 'publishMs')}
                if args.capture:
                    if path not in requests and (state.get('guiCaptureMs', 0) > 0):
                        request = str(uuid.uuid4())
                        temporary = path.parent / 'latency-request.tmp'
                        temporary.write_text(json.dumps({'id': request, 'seconds': args.seconds}), encoding='utf-8')
                        temporary.replace(path.parent / 'latency-request.json')
                        requests[path] = request
                    samples.append({'session': path.parent.name, 'time': now, 'frame': frame, 'hz': hz,
                                    'snapshotAgeMs': age * 1000, 'ipcPending': pending,
                                    'lockstep': state.get('lockstep', False), **timings})
                speed = 'warming' if hz is None else f'{hz:.1f} Hz'
                error = state.get('lockstepError') or state.get('error') or '-'
                print(f'{path.parent.name[:8]} frame={frame} native={speed} '
                      f'snapshot_age={age * 1000:.0f}ms ipc_pending={pending} '
                      f'update={timings["engineUpdateMs"]}ms gui={timings["guiCaptureMs"]}ms publish={timings["publishMs"]}ms '
                      f'lockstep={state.get("lockstep", False)} error={error}', flush=True)
            except (OSError, ValueError, KeyError):
                continue  # Atomic snapshot replacement can briefly hide a file on Windows.
        if not active:
            print('No recent running match. Check --root or start an RTS match.', flush=True)
        time.sleep(min(1, max(0, deadline - time.monotonic())))
    if args.capture:
        # The client checks requests once per second and writes off the render thread.
        client_reports = {}
        until = time.monotonic() + 3
        while len(client_reports) < len(requests) and time.monotonic() < until:
            for path, request in requests.items():
                try:
                    report = json.loads((path.parent / 'latency-client.json').read_text(encoding='utf-8'))
                    if report.get('id') == request:
                        client_reports[path.parent.name] = report
                except (OSError, ValueError):
                    pass
            if len(client_reports) < len(requests):
                time.sleep(.25)
        report = {'schema': 1, 'native': samples, 'clients': client_reports,
                  'missingClientReports': [p.parent.name for p in requests if p.parent.name not in client_reports]}
        args.capture.parent.mkdir(parents=True, exist_ok=True)
        args.capture.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        print(f'Saved {args.capture}: {len(samples)} native samples, {len(client_reports)} client reports.', flush=True)


if __name__ == '__main__':
    main()
