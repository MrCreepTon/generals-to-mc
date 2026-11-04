# Installation

**English** · [Русский](INSTALL.ru.md)

You need Windows, Minecraft Java with Fabric, and your own installed Generals and Zero Hour. Choose the JAR for your exact Minecraft version.

| Minecraft | Java | Fabric Loader |
| --- | --- | --- |
| 1.21.4 | 21 | 0.18.4+ |
| 1.21.11 | 21 | 0.19.5+ |
| 26.1, 26.1.1, 26.1.2, 26.2, 26.3 | 25 | 0.19.5+ |

Fabric API and the adapted engine are bundled. Windows is required on both clients and the server; Linux/Wine is not supported.

## First launch

1. Close Minecraft. Put one matching mod JAR in your Fabric instance's `mods` folder. Remove the old Generals JAR when updating.
2. Launch Minecraft → **Generals → Connect installed game**. Select the Zero Hour folder and confirm. Add the base Generals folder if requested.
3. Enter a world → pause menu → **Generals → Free play**. Choose your faction and color. Leave a flat, clear area for the base.

No installer or build tools are needed. The mod extracts its engine automatically and leaves your installed games unchanged. **Settings & help → Quick start** replays the introduction.

## Games and mods

Open **Generals → Settings & help**. **+** beside the selected game adds another installation. **My games** lets you select, edit, refresh or remove a connection. Removing it leaves the game files intact. A missing folder offers repair or removal. Finish the current RTS match before switching games.

Use **Game and mod options…** for a separate base Generals folder or mod folder. A mod installed directly in Zero Hour does not need to be selected twice. Advanced options can load all BIG archives. EXE/DLL modifications require separate engine support.

After updating an installed game or mod, use **Generals → Settings & help → Game → ↻** to refresh its factions. Older ROTR profiles may request **Refresh game data** before starting.

## Multiplayer and NPCs

Join the same Minecraft world. Each player and the server must connect matching game data and engine versions. Open **Generals → Skirmish**. The world owner/operator creates a room; players join it and choose faction, color, team and start. Joining Minecraft alone does not join the RTS match.

The host adds NPCs with **+ NPC** and sets their difficulty. Each player presses **Ready**. The match starts when everyone is ready; changing settings clears readiness. There are up to eight armies. Matching teams are allies; colors are unique. Random choices are resolved once by the server. Ownership uses Minecraft UUIDs, so duplicate display names do not share control. Vanilla and Contra cannot play in the same match.

Use **Map → Starts** to choose a start marker. Automatic placement needs a suitable loaded area. Keep starts clear and at least 40 blocks apart. Joining or rejoining an ongoing match is not supported yet.

## Markers and rules

The **Generals** creative tab contains army-start, supply and oil markers. Leave enough flat space for the actual building. For manual resource placement, select **Markers only** before starting. Supplies refill and marked oil derricks return when a new match starts. Within a match, supply stock is finite and destroyed derricks stay destroyed. Removing a supply marker removes its dock.

**Generals → World & markers → Find markers** lists saved points, including unloaded areas. Open one to rename it, add a note, edit army settings, locate it or confirm deletion. Owners/operators and Creative players can teleport safely beside it. Maps support dragging, wheel zoom and recentering on your position. **Map → Resources** shows only placed resource markers.

Use **Settings → Rules** in the lobby or **Generals → World & markers → Rules** to set destruction, fire, mob behaviour, environmental damage, general experience, victory and resources. Rules and markers are saved with the world. Only the match host changes rules. On a dedicated server, the host must also be an operator.

Back up your world before upgrading Minecraft. The mod imports older markers, notes, rules and resource stock when moving to 26.x and keeps the old files. Do not reopen an upgraded world in an older Minecraft version.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| No Generals button | Launch Fabric; check the JAR version and the instance's `mods` folder. |
| Game not found | Check both game folders in connection options. The menu reports missing files. |
| No supply dock or oil derrick | Clear a flat area around the marker and let the terrain load. |
| Supply unit ignores a dock | Build a supply center, select a Chinook, truck, or worker, then left-click the dock. A depleted dock refills in the next match. |
| Controls do not fit | Scroll the menu with the mouse wheel. |
| Engine stopped | Use the recovery screen and logs. Check that ordinary Zero Hour starts, then reconnect it. |
| No sound | Check Minecraft’s master, music, blocks and voice volumes. |

Connections and logs: `%LOCALAPPDATA%/GeneralsMinecraft`. Rules and markers: Minecraft world save. To uninstall, close Minecraft and remove the mod JAR. RTS matches are not saved between launches.

[Controls](CONTROLS.md) · [Known issues](KNOWN_ISSUES.md) · [README](../README.md)
