# Controls

**English** · [Русский](CONTROLS.ru.md)

Open **Generals** from Minecraft’s main or pause menu. During a match, **F8** returns to walking and block interaction; the match keeps running. See Minecraft’s Controls settings for your current binding.

| Default input | Action |
| --- | --- |
| F8 | Switch Minecraft / RTS; only works after a match starts |
| F9 | Open Generals menu |
| Left click | Select or issue a context command: move, attack, repair, enter transport |
| Left-drag / double-click | Group selection / matching units, following Generals rules |
| Short right click | Cancel or deselect |
| Hold right button | Scroll the camera |
| Arrow keys / screen edge | Move the camera |
| Mouse wheel | Zoom |
| Middle button + move | Rotate the camera |
| Ctrl + left click | Force fire |
| S | Stop selected units |
| Hold left button while placing | Rotate the building preview |
| Ctrl + number / number | Assign / select a control group |

Change mode, menu and mouse bindings in **Minecraft Options → Controls → Generals**. RTS keyboard commands come from the selected game’s CommandMap and action shortcuts, including mod changes. They only capture input in RTS mode; ordinary Minecraft movement and interaction work after switching back.

## Sound and rules

Minecraft volume sliders control master, music, blocks (effects) and voice (unit lines). While Generals is running with audio, Minecraft background music stays paused, including after F8 or opening a menu. It returns when the match or engine stops.

**Rules** groups settings into Combat, Mobs, World and EMP. Only the world host can change them; on a dedicated server, an operator can configure rules before a lobby opens, then its host controls them. Terrain damage defaults to force attack only: ordinary shots hurt units without digging craters. **World → Starting areas → Prepare** levels and clears starts before the match; containers and protected blocks stop preparation. Experience counts actual mob kills by the army. With building victory enabled, losing all key buildings eliminates an army; the remaining alliance wins. **Start RTS here** has no such victory condition.

**Rules → EMP** controls vehicles, mobs and redstone separately. Original EMP weapons keep their native vehicle behaviour. In Minecraft, a pulse temporarily stops mob AI and suppresses standard redstone signals; it does not replace blocks or save a permanent NoAI flag. Modded circuitry may use different signal APIs.

## Lobby and markers

With Modern UI installed, the lobby uses a Generals-style roster and map preview. Click a cell to choose a general, colour or team; click the NPC name to change its difficulty or remove it. On smaller screens the map opens through **Positions**. Without Modern UI, the standard Minecraft layout remains available.

In the standard Minecraft layout, left click cycles team/difficulty forward and right click cycles backward. The server resolves random choices at match start.

**Positions** selects starts; **Resources** shows placed supply and oil markers. Drag to pan, scroll to zoom, use **My position** to recenter or **All points** to fit markers.

Markers are in the **Generals** creative tab. Interact with an army-start marker to set its player/NPC, faction, team and difficulty. Oil derricks use the original Generals capture ability. **Find markers** opens saved marker details, notes, map, deletion and permission-checked teleport, including unloaded areas. Marker beams appear in ordinary Minecraft. Aim at a start marker, or hold its item, to see a clearing outline sized for the largest starting building and its rotation.

Clicking a mob without an armed selection shows its icon. With armed units selected, it issues a normal attack; Ctrl is not required.

## Local creative tools

In a local Creative world, open **Generals → Creative tools** for money, superweapon readiness, healing, fog and music. These tools are unavailable in a network room. `/zh-debug` exists only in development builds.

Finish RTS through its menu. F8 only changes the view. RTS units are not saved between launches; Minecraft terrain and markers are saved.

[Installation](INSTALL.md) · [README](../README.md)
