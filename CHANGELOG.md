# Changelog

**English** · [Русский](CHANGELOG.ru.md)

## 0.2.0-beta.13 — Minecraft effects and local timing

- Create original explosion and attached effects in Minecraft even outside RTS visibility; keep RTS fog rules.
- Draw the starting-area outline above the surface rather than inside the ground.
- Reuse per-frame unit lighting and GUI scratch storage; calculate scene bounds per mesh batch.
- Add an opt-in latency report with Minecraft frame, click, local IPC and native timing measurements.

## 0.2.0-beta.12 — setup, effects and EMP

- Find both Generals installations from their parent folder; handle an empty folder picker safely.
- Show a conservative clearing outline around start markers. Rename the random choice to Random general.
- Refresh the rendered scene between Minecraft ticks; stop interpolating reused effect IDs. Sample structure lighting above the ground and display buildings outside RTS fog in Minecraft mode.
- Detonate original missiles against imported Minecraft walls. Preserve native splash damage.
- Restore live music volume after F8 and map initialization.
- Adapt original EMP pulses to mobs and redstone, with separate host settings for vehicles, mobs and circuits. Restore AI and circuits when the pulse expires.
- Add an optional Modern UI lobby: a compact roster, in-place selectors, map preview and a clear match action. Keep vanilla widgets without Modern UI.

## 0.2.0-beta.11 — terrain and navigation

- Release signed-world position/goal reservations and old infantry footprints. Keep moving ground units on imported ground.
- Default terrain damage to force attack only; preserve original unit damage and projectile intent.
- Group match rules into Combat, Mobs and World. Validate host permissions on the server.
- Add optional starting-area preparation with protected-block preflight and bounded edits.
- Face the nearest opponent at match start and show the original general icon in the command-centre portrait.

## 0.2.0-beta.10 — returning from Minecraft

- Suspend and restore original Generals input through F8, clearing lost modifier releases, waypoint mode and mouse capture. Keep the simulation running.
- Replace the Minecraft owner overlay with the original Generals tooltip: unit name, player name, original font and army colour.
- Keep successfully rebuilt native objects after a failed compilation; stop idle Gradle daemons sooner.

## 0.2.0-beta.9 — effects and targeting

- Render original particle colours with dedicated unlit shaders on modern Minecraft. Restore default particle depth testing on 26.2/26.3.
- Recognize hostile mobs such as slimes through Minecraft's enemy interface, allowing Humvee passengers to target them automatically. Keep neutral mobs neutral until provoked.
- Show the army owner on hover; remove the permanent F8 banner.
- Reuse test profiles and worlds, skip test-world backups and discard completed QA sessions. Add a read-only latency monitor.

## 0.2.0-beta.8 — stability and release tools

- Bound sparse terrain path searches by deterministic work, preserving the original nearest-reachable fallback. Remove repeated full goal-ray searches that stalled NPC matches and made other clients wait.
- Prepare Windows worker stack pages before Minecraft 26.3 enters native shader compilation, protecting its Java include callback.
- Restore outward triangle winding after converting native coordinates, fixing dark back-face lighting in modern Minecraft.
- Add English/Russian README switching, a complete Make build and tag-triggered draft releases for all profiles.
- Always launch a fresh game for production smoke checks; cached results no longer count as a new test.
- Add sustained production-client NPC/latency checks and an unreachable-path regression fixture. Test drivers stay outside player JARs.

## 0.2.0-beta.7 — version ports

- Add Fabric profiles for Minecraft 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3. Keep 1.21.4; use 26.3 by default.
- Adapt mouse/keyboard events, key contexts, camera, models, mipmaps, depth shroud, entities, chunk tickets and world codecs to the new APIs. Verify actual movement and UI events inside each version.
- Import older world markers, notes, rules and resource stock when the namespaced SavedData layout changes. Retain legacy files and prefer current data on later loads.
- Preserve the ground/object snapshot of a pressed native mouse command through the input frame, including ground target ID zero.
- Build all supported profiles from shared code and ordered version adapters. Package each version separately; keep the native engine shared.
- Skip dependency source remapping during normal builds; allow opting back in for IDE source browsing.

## 0.2.0-beta.6 — release candidate

- Marker details combine naming, notes, army settings, map, teleport and confirmed deletion. Distant teleports prepare unloaded chunks asynchronously.
- Game selection switches directly; refresh/edit are separate. Missing folders offer repair or removal.
- NPC colour swatches, clearer Positions/Resources buttons and lobby Rules. Shorter menu text and a replayable first-run guide.
- Preserve original command-centre logo coverage from the second WW3D texture stage.
- Separate the bounded network delivery window from the authority execution queue. Two real-client checks inject 0/100/300 ms RTT and jitter, then verify recovery.
- Restore the Unix Gradle wrapper required by Linux CI builds. Historical development notes moved to the private archive.
- Minecraft 1.21.11/26.x ports remain in progress; this candidate targets 1.21.4.

## 0.2.0-beta.5 — NPC progression and map tools

- Fixed NPC prerequisite selection and supply-centre placement; preserved ground-relative AI plans on elevated terrain. Resource discovery includes NPC starts.
- Buildings, wrecks and salvage follow changes in imported ground height.
- Fixed tall marker model UVs sampling unrelated atlas textures.
- Resource overview shows placed markers only; added map drag, zoom, player heading and recenter/fit controls.
- Added a marker finder with distance sorting, map location and server-validated safe teleport for Creative/world owners/operators.
- F8 switches existing matches only. Unified Random/Automatic labels and roster panels.
- Added profile refresh and legacy ROTR faction migration through the normal Minecraft UI.

## 0.2.0-beta.4 — Minecraft 1.21.4

- Bound confirmed-frame delivery by the slowest participant; separate CRC checkpoints, compress terrain and skip unchanged minimaps.
- Use one server engine for a single-player network match with NPCs; retain shared command ordering for multiple players. Ignore late packets from finished matches.
- Reduce UI buffer and model-vertex allocations.
- Fix TNT damage against large buildings and rotated rectangular collision. Show destroyed-vehicle particles in Minecraft using its camera.
- Align player/NPC fields, separate position/resource maps and add marker icons, flags and beams.
- Recognize launcher-disabled ROTR archives in private profiles and read faction names from game data.
- Use the selected game’s RTS keyboard commands; keep mode/menu/mouse bindings in Minecraft. Restore ordinary Minecraft input after switching modes.
- Prevent Minecraft music overlapping Generals; restore it after RTS ends.
- Fix the server engine crash during satellite reconnaissance; keep the engine window hidden.
- Improve escape paths from pits and prevent false damage on ordinary steps.
- Stop stale builder routes interrupting construction/repair. Include buildings in mob pathfinding.
- Prioritize newly selected mob targets, retain pending clicks and tolerate small double-click pointer movement.
- Add optional mob-kill general experience and key-building victory rules.
- Show creative-tool confirmations in menus and export original segmented laser beams.

## 0.2.0-beta.3 — Minecraft 1.21.4

- Fix the reproduced Humvee/group pathfinding crash at the original map edge.
- Wait for the engine map to load before applying Minecraft terrain changes.
- Retain movement goals beyond loaded terrain, retry as routes load and cancel on Stop.
- Restore Minecraft input and open recovery when the engine stops. Add a visible return-to-Minecraft button.
- Prevent invisible building bodies intercepting block targeting from inside a base, pushing players or applying mob cramming.
- Add game connection selection, creation, editing and deletion; failed validation preserves the working profile.
- Hide oversized Minecraft flames around invisible bodies while retaining fire damage.
- Isolate test directories and handle missing release-excluded test drivers.
- Allow manual Actions builds of one supported profile or all profiles.

## 0.2.0-beta.2 — Minecraft 1.21.4

- Add the game connection + button and faction/colour selection with random choices at launch.
- Share the player/NPC roster, enforce unique colours and add random teams, NPC difficulty and start-marker maps. Right click cycles team/difficulty backward.
- Use Minecraft’s Russian/English language, translate server messages on clients and display errors above menus.
- Move RTS bindings into Minecraft controls and display current bindings in help (keyboard handling changed in beta.4).
- Add a Generals creative tab with markers and a local creative-tools menu.
- Show selected mobs as icons without name/health text; allow normal attacks on neutral mobs without Ctrl.
- Account for Minecraft heights in weapon trajectories and add block damage radius/penetration settings.
- Improve mob collision with wide buildings; prevent destroyed oil derricks respawning from markers.

## 0.2.0-beta.1 — Minecraft 1.21.4

- Install one Fabric JAR with the engine and Fabric API included; remove the separate installer and player-side build requirement.
- Connect installed Zero Hour and mods through Minecraft menus.
- Add world rules for weapon block damage, ignition, hostile attacks, neutral retaliation and environmental damage.
- Support seeded resources or manual markers; persist supply stock and remove supplies with their marker.
- Exclude the former Java simulation, test drivers, installer and retail game assets from player JARs.
- Add Minecraft version profiles, GitHub Actions builds, package audits, SHA256 and corresponding source archives.
- Test finished JARs separately from development runs; keep QA drivers local.

This is a beta. See [known issues](docs/KNOWN_ISSUES.md) for current limitations.
