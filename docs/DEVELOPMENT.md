# Build and release

**English** · [Русский](DEVELOPMENT.ru.md)

Run commands from the repository root containing `Makefile`, `build.gradle` and `.github/`. Players only need the finished JAR.

## GitHub Actions

Open **Actions → Build mod → Run workflow**. Select `all` or a profile such as `26.3`. Download `generals-minecraft-mc26.3` from **Artifacts**: it contains the JAR, documentation, checksums and corresponding source.

CI builds the pinned engine on Windows, then JARs on Linux. It does not need installed game data or secrets. Linux builds do not imply Linux runtime support. The workflow has been checked locally; running it remotely requires uploading this repository to GitHub.

The native EXE is cached separately. Changes to `native/`, `Build-Native.ps1`, `tools/prepare_engine.py` or `tools/patch*.py` rebuild it; Java and documentation changes reuse it. Only an exact key skips compilation. The runner/toolchain family is part of the key; change its `v1` suffix or delete the entry in **Actions → Caches** to force a fresh build. JAR checks and audits still run.

## Local build

You need Windows, GNU Make, Python 3.10+, Git, CMake 3.28+, Visual Studio 2022 C++/Windows SDK with x86 tools, JDK 25 for Gradle and JDK 21 for 1.21.x targets. Set `JAVA_HOME` and `JAVA21_HOME` if needed.

```sh
make                        # Engine once + all Minecraft versions
make build MC=26.3          # One version
make release               # Full build into dist/<mod_version>/
make versions              # Available profiles
make source                # Clean source ZIP
make help
```

Single-version packages go to `build/release/player/<Minecraft>/`. Default: 26.3. Other profiles: 1.21.4, 1.21.11, 26.1, 26.1.1, 26.1.2 and 26.2.

`make release` collects all JARs, source, both documentation languages and SHA256 checksums. `make package` collects already built, audited packages. Neither publishes anything. Without Make, use `python tools/build.py release`.

`Build-Native.ps1` builds public engine revision `f746af7e626c96e693cb74a032da47a602c1a039` with adapter patches into `build/native/windows-x86/generalszh.exe`. One engine serves all Minecraft and game-data profiles. It reads the user’s installation; it is not the retail EXE.

Pass `NATIVE_BIN=/path/to/source-built/generalszh.exe` to skip rebuilding C++. Linux CI uses this after the Windows engine job. Gradle also accepts `-PnativeBinary=...`. The engine uses a static C++ runtime; no separate Visual C++ Redistributable is required.

## Version support

Use one branch with shared code and ordered adapters. Build another supported version with `MC=...`; play it by installing its matching JAR. Install one Generals JAR at a time. The default target is in `gradle.properties`.

| Location | Purpose |
| --- | --- |
| `versions/*.properties` | Exact Minecraft, Loader, Fabric API, Java and adapter versions |
| `src/main`, `src/client` | Shared code, based on 1.21.4 |
| `src/versions/modern` | 1.21.11 changes |
| `src/versions/26.1`, `26.2`, `26.3` | Later API changes; 26.1.1/26.1.2 reuse 26.1 |

To add a version:

1. Create `versions/<version>.properties` with exact `minecraft`, `loader`, `fabric_api`, `java`, `unobfuscated` and `overlays` values.
2. Reuse adapters and add only changed files under `src/versions/<layer>/{main,client}/{java,resources}`. Later layers replace earlier files at the same relative path.
3. Run `stageRelease` with the new profile. `tools/ci_versions.py` includes it in CI automatically. A number without a profile is rejected.
4. Test the finished JAR: input, return to Minecraft, models, fog, audio, markers, world data and multiplayer. Compilation alone does not establish compatibility.

Java 21 is used for 1.21.x and Java 25 for 26.x. Pre-26 targets use remapping; 26.x uses Loom for unobfuscated Minecraft.

## Checks

`stageRelease` runs 38 shared-logic checks and audits the JAR. It checks the engine SHA256/imports and excludes game assets and test drivers. Real-client tests run separately:

```powershell
.\gradlew.bat productionSmoke '-Pminecraft_target=1.21.11' -PnativeFeedbackRegression -PnativeTestLanguage=en_us
.\gradlew.bat productionSmoke '-Pminecraft_target=26.3' -PnativeFeedbackRegression -PnativeTestLanguage=en_us
python tools/native_pointer_bench.py
python tools/native_path_budget_bench.py
python tools/native_material_bench.py
python tools/native_lockstep_bench.py --graphics-peers --combat
python tools/verify_native_patch.py
```

`productionSmoke` launches the packaged JAR with a separate private driver, worlds and profiles. Local scenarios expect your own games at `C:/Games/Generals ZH` and `C:/Games/Generals`. Reports go to `evidence/`, outside releases.

README screenshots: run `productionSmoke -Pminecraft_target=1.21.4 -PnativeReleaseUx -PnativeReadmeCapture -PnativeTestLanguage=en_us`. Use `ru_ru` for Russian or `-PnativeReadmeCapture=ui` to skip the battle. Frames are saved to `build/production-smoke/screenshots/readme-*.png`; the capture driver is excluded from player JARs.

F8/Peaceful regression on 1.21.4: `productionSmoke -Pminecraft_target=1.21.4 -PnativeResumeRegression -PnativeResumeSticky`. It tests a lost Alt release, paused difficulty settings, fresh mouse selection/movement and native tooltip pixels. Omit `-PnativeResumeSticky` to repeat four ordinary F8 cycles.

Two-client 1.21.4 check: `runClient -Pminecraft_target=1.21.4 -PnativeNetworkBench -PnativeNetworkDualOnly -PnativeFakeLatency`. It tests commands, simulation rate and recovery under 0/100/300/0 ms RTT with jitter.

26.3 stability check: `productionSmoke -Pminecraft_target=26.3 -PnativeStabilityNetwork -PnativeStabilitySeconds=480`. Add `-PnativeStabilitySolo` for a single shared-simulation client or `-PnativeStabilityJfr` for Java profiling. Use ordinary `productionSmoke -PnativeReloadCycles=12` for resource reloads.

The short effects, transport, input and marker check is `productionSmoke -Pminecraft_target=<version> -PnativeFeedbackRegression`. It reuses QA profiles and worlds. The full 1.21.4 first-install check (`-PnativeReleaseUx`) creates a fresh isolated setup; run it when changing game connection. Run a short targeted check for UI changes. Repeat combat/network checks when simulation, transport or a regression requires them. These checks do not replace long WAN or large-army testing.

Dependency source remapping is disabled to speed up normal builds. Enable `-Dfabric.loom.ci=false` for IDE source browsing.


Start preparation and terrain damage: `productionSmoke -Pminecraft_target=1.21.4 -PnativeResumeRegression -PnativeResumeSticky -PnativeWorldPolish`. Checks preparation, container rejection without partial edits, and normal/forced attacks.

Movement: `python tools/native_navigation_bench.py --exe build/native/windows-x86/generalszh.exe --lab <QA-lab>`. Requires an existing QA profile; installations and worlds are reused.

## Latency diagnostics

During a match, run `python tools/watch_latency.py --seconds 30`. Use `--root build/qa/native` for the private version tests. The script only reads snapshots; it writes no files.

- Native simulation should stay near 30 Hz. A sustained drop points to engine work or waiting for a participant.
- Growing `ipc_pending` means local commands are waiting for the native process. It is not network ping.
- Growing `snapshot_age` means snapshots have stopped arriving. Check the session log and `error`.
- If these stay normal but Minecraft frame time spikes, inspect client rendering with F3 and a short F3+L recording.

To capture a shareable report, run `python tools/watch_latency.py --seconds 30 --capture latency-report.json` and issue a few move/attack orders. Keep playing until it finishes. This writes a small request and result in the active session, plus the report you named. It contains frame and click timings, Java/MC/mod versions and CPU/heap counts; no world, installation or player names.

`frameIntervalMs` shows Minecraft pacing; `sceneIngestMs` and `meshSubmitMs` show scene processing and submission (deferred GPU work is excluded). `clickHandlingMs` measures picking and sending; `localIpcAckMs` ends when the native process consumes input, before a unit finishes turning or a network turn is committed. `nativeUpdateMs`, `nativeGuiMs` and `nativePublishMs` separate the engine, GUI capture and snapshot publication; these stages can overlap, so do not add them. Compare solo and multiplayer reports on the affected machine. On older builds, the report still includes native samples; client timings require beta.13 or later. F3+L supplies a separate Minecraft recording if frame times spike.

For a controlled two-client check on 26.3: `productionSmoke -Pminecraft_target=26.3 -PnativeStabilityNetwork -PnativeStabilitySeconds=120`. It injects 0/100/300/0 ms RTT with jitter in the private peer connection, then checks recovery. `-PnativeStabilityJfr` adds bounded Java recordings. Test worlds/profiles use fixed directories; do not copy the project or game installation between runs.


## Release

1. Upload the project to your GitHub repository with `main` as its primary branch. Keep `.github/workflows/build.yml` at the root. `make source` exports a clean copy to `build/release/generals-minecraft-<version>-source.zip` if needed.
2. Run **Actions → Build mod → Run workflow → all**. CI builds all seven profiles and checks logic/packages; real game tests remain local.
3. Set `mod_version` in `gradle.properties`, update both changelogs and commit. The mod version applies to all Minecraft profiles.
4. Push a matching tag, for example:

```sh
git tag v0.2.0-beta.13
git push origin v0.2.0-beta.13
```

5. Open **Releases**, review the generated draft and select **Publish release**. Tags containing `-beta` produce prereleases.

A mismatched tag is rejected before the engine build. Reruns update drafts, never published releases. The workflow uses `gh release create --draft`.

Keep `build`, `run`, `private`, `evidence`, game connections, extracted game data and decompiled code out of Git and releases. `releaseSources` uses an explicit file allowlist; `stageRelease` attaches the source archive to each package.

[README](../README.md) · [Changelog](../CHANGELOG.md)
