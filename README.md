# Generals in Minecraft

**English** · [Русский](README.ru.md)

![Tank battle in a Minecraft village](docs/media/battle.png)

Zero Hour armies, construction and battles inside your Minecraft world. Switch between the RTS camera and ordinary Minecraft while the match keeps running.

**Beta · Fabric · Windows · Minecraft 1.21.4 / 1.21.11 / 26.x**

## Install

1. Put the mod JAR in your Fabric instance's `mods` folder. Remove the previous Generals JAR when updating. Fabric API and the adapted engine are included.
2. Launch Minecraft → **Generals → Connect installed game**. Select your Zero Hour folder and confirm.

You need your own installed **Generals and Zero Hour**. Their files stay where they are. No installer, batch files or build tools are needed.

Open a world → pause menu → **Generals**. Choose **Free play** for a local army, or **Skirmish** for a shared match. **Settings & help → Quick start** replays the introduction.

**F8** switches Minecraft / RTS after a match starts. **F9** opens the menu. Change these keys in Minecraft’s Controls settings. Music, effects and voices follow Minecraft’s volume controls.

![Quick start](docs/media/quick-start.gif)

## Matches and maps

Each player has a separate army. In singleplayer, **Skirmish** opens your room directly. On a server, the host creates a room. **+ NPC** adds opponents. Each player presses **Ready**; the match starts when everyone is ready. Changing settings clears readiness. **Chat** opens room chat. Choose a faction, color, team and starting position. **+ NPC** adds a computer opponent. Click a field to change it; the NPC name opens difficulty and removal.

**Map** has **Starts** and **Resources** tabs. Choose army positions or inspect placed supplies and oil. Drag to pan and scroll to zoom. **Settings** contains starting cash and the superweapon limit; their current values stay visible in the lobby.

The **Generals** creative tab contains army-start, supply and oil markers. **Generals → World & markers → Find markers** lists saved markers, including unloaded areas. Open a marker to name it, add a note, edit its army settings, locate it or delete it. Owners/operators and Creative players can teleport safely beside it.

To gather supplies, build a supply center, select a Chinook, supply truck, or worker, then left-click the supply dock. Supplies refill for each new match. Oil derricks need the capture upgrade.

In Minecraft mode, interact with your vehicle to board it. Move with WASD, aim with the camera, hold left click to fire, and press Shift to leave. The aim marker turns orange outside weapon range. See [controls](docs/CONTROLS.md) for rebound keys.

![Marker tools](docs/media/markers.gif)

**Settings → Rules** in the lobby controls block destruction, fire, mob aggression, environmental damage, general experience, victory and resource generation. Rules are saved with the world. Only the match host can change rules. On a dedicated server, the host must also be an operator.

All Generals menus include a charcoal-and-gold theme. With Modern UI installed, menus use its native controls, text fields and ripple effects. Without it, Minecraft widgets keep the same layout. The optional integration supports official Modern UI builds for 1.21.4 and 26.1 through 26.1.2.

| Built-in theme | With Modern UI |
| --- | --- |
| ![Menu without Modern UI](docs/media/menu-standard.png) | ![Menu with Modern UI](docs/media/menu-modern.png) |

## Games and mods

Use **+** beside the selected game to connect another installation. Selecting a game switches to it; the pencil edits its connection. A moved/deleted folder offers repair or removal. Removing a connection leaves the installed game untouched.

Vanilla Zero Hour and the installed Rise of the Reds have been tested. INI/BIG mods can use separate profiles. Mods with custom EXE/DLL changes need engine work; arbitrary mod compatibility is not guaranteed.

All participants need matching game data, engine and mod versions. Vanilla versus Contra is rejected before the match. Folder paths and player display names may differ: army ownership uses Minecraft UUIDs.

## Compatibility

- **Minecraft:** 1.21.4, 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3. Use the JAR matching your exact version.
- **Runtime:** Java 21 for 1.21.x; Java 25 for 26.x. Fabric Loader 0.18.4+ for 1.21.4, 0.19.5+ for the other profiles. Fabric API is bundled.
- **Windows is required on clients and the server. Linux/Wine is not supported yet.**
- 26.3 includes protection for a rare Windows JVM crash during native shader compilation. It remains a beta; see [known issues](docs/KNOWN_ISSUES.md).
- RTS matches are not saved when Minecraft closes. Joining/rejoining an ongoing match, campaigns and NPC-only spectator scenarios are not ready.
- Tests cover specific scenarios. Long WAN sessions and large armies still need testing. See [test coverage and limitations](docs/KNOWN_ISSUES.md).

If the engine stops, Minecraft shows a recovery screen with its logs. Connections and logs live in `%LOCALAPPDATA%/GeneralsMinecraft`; world rules and markers live in the Minecraft save. To uninstall, close Minecraft and remove the JAR.

[Install and troubleshooting](docs/INSTALL.md) · [Controls](docs/CONTROLS.md) · [Known issues](docs/KNOWN_ISSUES.md) · [Changelog](CHANGELOG.md)

## Build

**GitHub → Actions → Build mod → Run workflow** builds every verified profile, or a selected version. Download its JAR from **Artifacts**. New Minecraft versions require a port and an in-game check; entering a version number alone cannot add support.

```sh
make                       # Engine + every supported Minecraft version
make build MC=26.3         # One version
make release               # All JARs + source + checksums in dist/<version>/
make versions              # Available profiles
```

Local full builds need Windows, GNU Make, Python, Git, CMake, Visual Studio 2022 C++/Windows SDK, and JDK 21/25. Players only need the mod JAR.

[Build, version adapters and release steps](docs/DEVELOPMENT.md). A matching `v<mod_version>` tag builds a draft GitHub release. Review and publish it on GitHub.

[GPL-3.0-or-later](LICENSE.md) · [Credits and third-party licenses](THIRD_PARTY_NOTICES.md). Developed with AI assistance from Codex. Original game assets are read from your installation and are not distributed with the mod.
