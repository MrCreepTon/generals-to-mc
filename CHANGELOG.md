# Changelog

**English** · [Русский](CHANGELOG.ru.md)

## 0.2.0-beta.24

- Reduce the main menu to play modes, world tools, settings, and Back. Move game connections and help into Settings & help.
- Open a solo skirmish room directly. Ready still starts the match only after every player confirms.
- Group cash, the superweapon limit, rules, and room closure in Settings. Keep current match values visible in the roster.
- Combine start selection and resources in Map tabs, including worlds without start markers.
- Show four common rules first. Keep Combat, Mobs, World, and EMP under Advanced.
- Keep dropdown arrows visible in native Modern UI controls, with space reserved beside long labels.
- Preserve Back/Esc navigation, disabled-action explanations, and host permissions across the built-in theme and Modern UI.

## 0.2.0-beta.23

- Restore the guide backdrop on 26.x and keep dividers clear of its progress label and navigation controls.
- Correct the first-run guide to describe manual Ready and automatic launch once all players confirm.
- Center the main menu in a compact card. Keep its primary action visible after resizing a scrolled window or changing GUI scale.
- Preserve selected tabs and toggles in real Modern UI controls. Update action styles when their role changes, and distinguish focus, hover, disabled, and quiet actions.
- Keep Debug visible with an explanation when unavailable on 26.2 and 26.3.
- Show an ellipsis for long marker labels and keep the full text in their tooltip. Shorten the English terrain-destruction value.

## 0.2.0-beta.22

- Finish imported supply docks internally so normal clicks can order gathering. Check Chinooks, supply trucks, and GLA workers through the original context command, not a direct AI order.
- Restore mobile-unit collision with Minecraft players and mobs. Riders do not collide with their own vehicle.
- Render passengers, the camera, unit icons, and vehicle meshes from the same interpolated frame.
- Aim turrets with the camera at Minecraft tick rate. Release direct control when switching back to RTS so it cannot cancel commander orders. Stop mounted fire when the attack key is released or the rider dismounts. Add a range-colored reticle and a compact vehicle health bar.
- Keep the next chat draft when the previous message receives its server acknowledgement.
- Write native commands and simulation frames through an ordered background queue so disk stalls cannot block rendering or server ticks. Preserve every command and report write failures.
- Correct supply lifecycle, Ready, Modern UI, driving, and build instructions in both documentation languages. Include the Makefile in source releases.

## 0.2.0-beta.21 — supplies, vehicles and Modern UI

- Reset supply stock for each match; allow selecting visible resource sites through fog.
- Use real Modern UI fragments and native controls where its official Fabric build is available. Keep the built-in theme elsewhere.
- Board your own vehicles in Minecraft; drive, fire and leave through normal controls. Validate ownership on the server.
- Attach arrows to original model triangles. Keep overhead icons and riding cameras in sync with interpolated models.
- Use live drawable opacity for aircraft; retain intentional stealth. Show oriented collision outlines in F3+B.
- Let original general powers target unexplored areas. Teach AI armies to research capture and secure imported oil derricks.

## 0.2.0-beta.20 — lobby and camera fixes

- Add manual Ready and room chat. Settings and player changes clear readiness; the match starts when everyone is ready.
- Show clearing outlines for oil and supply markers. Keep Debug visible with an explanation when unavailable.
- Register imported supply docks as pathfinding obstacles; supply trucks can still harvest them.
- Anchor original group numerals to the world each rendered frame. Expand scene streaming with camera zoom and window size to keep edge buildings visible.
- Preserve original face culling for construction models instead of drawing overlapping backsides.

## 0.2.0-beta.19 — effects, roofs and sound

- Age original particles by committed frames, including network catch-up. Clear the targeting cursor when switching to Minecraft.
- Keep the clicked height for construction and strikes. Support broad roofs, export tall block collisions and detect superweapon impacts above ground.
- Apply host starting cash even when a faction INI defines its own balance. Free play also reads cash and the superweapon limit from the world's settings.
- Match stock music to the local army. Apply Minecraft's music volume to the actual soundtrack stream, including mute.
- Remove marker light under command centers and refresh lighting around existing markers.
- Forward Minecraft water currents to ground units; update terrain when water flows.

## 0.2.0-beta.18 — gameplay and menu fixes

- Keep the empty lobby inside its panel; explain locked settings and show the current fog state.
- Keep the RTS camera when F9 opens the menu. Block Debug in every skirmish, including direct server requests; add an all-powers action to Creative Free play.
- Restrict red building bibs to the active placement preview. Export unselectable aircraft such as carpet bombers and allow general powers over loaded unexplored terrain.
- Adapt ECM disabling beams to mobs, sync a blue tint and use the original lightning animation. Keep microwave and EMP mob rules separate; make slimes neutral to the army.
- Anchor health bars and original status icons to the rendered unit. Project ground-aligned effects onto block levels and keep the camera above craters.

## 0.2.0-beta.17 — a shared theme for every menu

- Include the dark-and-gold theme without Modern UI. The optional mod improves fonts; no separate UI dependency is needed.
- Give connection forms, marker and army editors, maps, rules, the guide, creative tools, recovery and results the same panels and action hierarchy.
- Use themed native text fields, clear choice/stepper controls and drawn icons that also work with Minecraft's default font.
- Replace mod confirmations with compact themed dialogs. Esc cancels; repeated clicks submit once. Keep normal Minecraft menus unchanged.
- Return to the menu after closing a lobby; refresh locked actions when the server acknowledges the closure.
- Check all menu controls at compact and wide sizes; preserve paths when switching connection views.

## 0.2.0-beta.16 — buttons and menu navigation

- Give the built-in menus a shared charcoal-and-gold theme: a clear primary action, quiet navigation, distinct destructive actions, selected tabs and switches.
- Add visible keyboard focus and short hover/press feedback without moving controls. Cache fitted captions instead of measuring them every frame.
- Open dropdowns at the current selection. Use Up/Down across pages, Enter to choose and Esc to return.
- Give colour names more room. Avoid rebuilding menus when scrolling past an edge.
- Keep empty choice lists valid on small screens. Clear a dismissed marker-save error from unrelated menus.

## 0.2.0-beta.15 — first launch and usability

- Use the compact roster without Modern UI too. Small screens show both player and AI; the add-AI action stays visible.
- Separate Skirmish from Free play. Explain the map before launching from the guide; disable the dead-end Join action in an empty singleplayer world.
- Pan cached map terrain immediately with bounded resampling. Stop sorting the marker list every render frame.
- Save marker text with Back or Esc. Preserve edits made during an in-flight save; keep the editor open on server errors.
- Reset an assigned start to Auto when its marker is deleted or reassigned. Expose AI colour in the start-marker editor.
- Offer repair for incomplete game metadata and replace unavailable saved colours with Random.
- Wrap long rule labels and show a scrollbar. Suppress background tooltips while a choice list is open.
- Avoid repeated whole-army scans when refreshing remembered buildings under fog.

## 0.2.0-beta.14 — input, menus and weapons

- Move the building preview every Minecraft frame. Generals still validates final placement and rotation.
- Block background controls under dropdowns, select starts on the map and clarify exit actions. Remember army choices for each connected game.
- Put starting cash and the superweapon limit in the lobby, with host-only changes before launch. Show start outlines without hovering.
- Rotate structure collision footprints; leave empty bounding-box corners walkable and buildable. Let players escape a structure built around them.
- Stop treating Avenger designator durations as damage. Add host settings for microwave mob stuns and ECM deflection of mob-fired arrows.
- Export the particle cannon's main damage pulses to Minecraft. Avoid duplicate block explosions from its residual trail; preserve original entity damage.
- Allow superweapons to target loaded unexplored terrain. Strategic strikes retain terrain damage under the Ctrl and superweapons rule.
- Add general promotion to creative tools. Mark hazardous terrain for avoidance with clearance for unit width.
- Check native patches in memory, without copying the source checkout.
- Prioritize changed terrain near armies and the camera instead of waiting for the background sweep.

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
- Add a Generals-style lobby layout when Modern UI is detected: a compact roster, in-place selectors, map preview and a clear match action. Keep vanilla widgets without Modern UI.

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
