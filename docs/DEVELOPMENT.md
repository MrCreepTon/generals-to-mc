# Build and release

**English** · [Русский](DEVELOPMENT.ru.md)

Run commands from the repository root. Players need the finished JAR, not the build tools.

## Build locally

Use Windows, GNU Make, Python 3.10+, Git, CMake 3.28+, Visual Studio 2022 C++ with x86 tools and Windows SDK, JDK 25 for Gradle, and JDK 21 for Minecraft 1.21.x. Set `JAVA_HOME` and `JAVA21_HOME` if Gradle cannot find them.

```sh
make                                      # Engine once, then every supported version
make build MC=1.21.4                       # One Minecraft version
make release                              # All packages in dist/<mod_version>/
make package                              # Collect packages already built and audited
make source                               # Source ZIP, including the Makefile
make versions                             # Available profiles
make help
```

Without Make, run `python tools/build.py release`. Single-version packages go to `build/release/player/<Minecraft>/`. The default target is 26.3. `make release` also collects both documentation languages, source, and SHA256 checksums. It does not publish anything.

`Build-Native.ps1` builds engine revision `f746af7e626c96e693cb74a032da47a602c1a039` with the adapter patches. The output is `build/native/windows-x86/generalszh.exe`. All Minecraft profiles use this engine. Original assets stay in the player's installed Generals and Zero Hour.

To reuse a source-built engine, pass `NATIVE_BIN=/path/to/generalszh.exe`. Linux can build JARs this way; playing still requires Windows on clients and the server. Gradle accepts the same path through `-PnativeBinary=...`. The engine links the C++ runtime statically.

## Minecraft versions

Keep shared code and ordered adapters in one branch. Select a build with `MC=...`, then install only its matching JAR. The default is in `gradle.properties`.

| Location | Purpose |
| --- | --- |
| `versions/*.properties` | Exact Minecraft, Fabric Loader, Fabric API, Java, and adapter versions |
| `src/main`, `src/client` | Shared code based on 1.21.4 |
| `src/versions/modern` | Minecraft 1.21.11 API changes |
| `src/versions/26.1`, `26.2`, `26.3` | Later API changes; 26.1.1 and 26.1.2 reuse 26.1 |

To add a version, create its profile, adapt changed APIs, and run `stageRelease`. Later adapters replace earlier files with the same relative path. `tools/ci_versions.py` discovers profiles automatically. A version number without a profile is rejected. Test input, F8, rendering, fog, audio, markers, saved data, and multiplayer in the finished JAR. Compilation alone does not establish support.

## GitHub Actions

Open **Actions → Build mod → Run workflow** and select `all` or an exact profile. Download its package from **Artifacts**. CI builds the engine on Windows and JARs on Linux without installed game assets or secrets.

README and `docs/` edits alone skip push and pull-request builds. Manual runs and release tags still build. The native cache key includes `native/`, `Build-Native.ps1`, `tools/prepare_engine.py`, and `tools/patch*.py`. Java changes reuse the engine when the key matches. Delete its entry in **Actions → Caches** to rebuild it. JAR checks still run.

## Checks

`stageRelease` runs 43 shared-logic checks and audits the package. The audit verifies the engine hash and dependencies and rejects game assets, Markdown, and private test drivers inside JARs. Source ZIPs and documentation ship beside the JAR.

Real-client checks use a separate private driver with fixed QA profiles and worlds. They reuse installed games at `C:/Games/Generals` and `C:/Games/Generals ZH`. Reports go to `evidence/`. Do not copy installations or worlds between runs.

```powershell
.\gradlew.bat productionSmoke '-Pminecraft_target=1.21.4' -PnativePolishRegression -PnativePolishSkipRoster -PnativeReportsRegression -PnativeLatestRegression -PnativeTestLanguage=en_us
.\gradlew.bat productionSmoke '-Pminecraft_target=1.21.11' -PnativeTestLanguage=en_us
.\gradlew.bat productionSmoke '-Pminecraft_target=26.3' -PnativeTestLanguage=en_us
python tools/verify_native_patch.py
```

The 1.21.4 command checks Ready, chat, supply reset, a real supply click and deposit, collisions, riding, third-person passenger alignment, firing, arrows, rendering, and powers in unexplored fog. The newer-version checks include movement, riding, fire release, menus, and shroud pixels.

For targeted checks:

| Scenario | Add to `productionSmoke` |
| --- | --- |
| Menus without a battle, 1.21.11 or 26.x | `-PnativeUiOnly` |
| Menu navigation on 1.21.4, reused QA world | `-PnativeNavigationOnly` |
| Actual Modern UI, supported version | `-PnativeExtraMods=build/qa/addons/<version>` |
| F8 and Peaceful, 1.21.4 | `-PnativeResumeRegression -PnativeResumeSticky` |
| Placement, collisions, EMP/ECM, 1.21.4 | `-PnativePolishRegression` |
| Superweapons, elevated targets, cactus routes, 1.21.4 | `-PnativePolishRegression -PnativeStrategicRegression` |
| Two Minecraft clients with latency, 26.3 | `-PnativeStabilityNetwork -PnativeStabilitySeconds=120` |
| Resource reloads, 26.3 | `-PnativeReloadCycles=12` |
| Four guide frames, 26.3 | `-PnativeUiOnly -PnativeGuideCapture -PnativeTestLanguage=en_us` or `ru_ru` |

Native-only checks accept `--exe build/native/windows-x86/generalszh.exe --lab <QA-lab>`. `tools/native_supply_regression_bench.py` exercises ordinary resource clicks for all three factions. `tools/native_latest_bench.py` checks turret aim without firing, fire release, aircraft opacity, and NPC oil capture. `tools/native_navigation_bench.py` checks movement on imported terrain. They require an existing QA lab.

Modern UI uses a native `Fragment`, `Button`, `EditText`, and `RippleDrawable`. Its SDK is compile-only. Shared Minecraft controllers validate input and send server requests. The adapters support 1.21.4 and 26.1 through 26.1.2. Other profiles use the built-in theme. The view tree is Java.

README media use English UI. For a new capture, add `-PnativeReleaseUx -PnativeReadmeCapture -PnativeTestLanguage=en_us` to the 1.21.4 run. Add `=ui` to `nativeReadmeCapture` to skip the battle. Frames go to `build/production-smoke/screenshots/`. The full first-install check creates a new private connection setup; use targeted checks for later changes.

## Diagnose input delay

While playing, run `python tools/watch_latency.py --seconds 30 --capture latency-report.json` and issue move and attack orders. For private tests, add `--root build/qa/native`.

| Reading | What it measures |
| --- | --- |
| Native Hz | Simulation rate, normally near 30 Hz |
| `ipc_pending` | Local commands waiting for the engine, not network ping |
| `snapshot_age` | Time since the last native snapshot |
| `frameIntervalMs` | Minecraft frame pacing |
| `clickHandlingMs` | Picking and sending the click |
| `localIpcAckMs` | Time until the native process consumes input |
| `sceneIngestMs`, `meshSubmitMs` | Client scene processing and mesh submission |

A unit's turn time comes after input delivery. Compare solo and multiplayer reports on the affected computer. If native timings stay normal while Minecraft stalls, collect an F3+L recording. The latency report contains timings and runtime information, without world or player names. `-PnativeStabilityJfr` adds bounded Java recordings to stability checks. The private network test injects 0/100/300/0 ms RTT with jitter and checks recovery; it does not replace WAN testing.

## Publish a release

1. Set `mod_version` in `gradle.properties` and update both changelogs.
2. Build every profile and run the relevant real-client checks. Review the JARs, source, and hashes.
3. Commit and push the project with `.github/workflows/build.yml` at the repository root.
4. Push a matching tag:

```sh
git tag v0.2.0-beta.24
git push origin v0.2.0-beta.24
```

5. Open **Releases**, review the generated draft, and select **Publish release**. Beta tags create prereleases.

A mismatched tag is rejected before the engine build. Reruns update drafts, never published releases. Keep `build`, `run`, `private`, `evidence`, installations, and extracted or decompiled game data out of Git. Source archives use an explicit file allowlist.

[README](../README.md) · [Changelog](../CHANGELOG.md)
