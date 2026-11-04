# Controls

**English** · [Русский](CONTROLS.ru.md)

Open **Generals** from Minecraft’s main or pause menu. During a match, **F8** returns to walking and block interaction; the match keeps running. See Minecraft’s Controls settings for your current binding.

| Default input | Action |
| --- | --- |
| F8 | Switch Minecraft / RTS; only works after a match starts |
| F9 | Open Generals menu; keep the RTS camera |
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

Minecraft volume sliders control master, music, blocks for effects and voice for unit lines. While Generals is running with audio, Minecraft background music stays paused, including after F8 or opening a menu. It returns when the match or engine stops.

Open **Generals → World & markers → Rules**, or **Settings → Rules** in the lobby. The first screen shows destruction, resources, starting areas, and victory. **Advanced…** opens Combat, Mobs, World, and EMP. Only the world host changes them. On a dedicated server, an operator can configure rules before a room opens; its host must also be an operator. Terrain damage defaults to force attack and superweapons: ordinary shots hurt units without digging craters. **Starting areas → Prepare** levels and clears starts before the match; containers and protected blocks stop preparation. Experience counts actual mob kills by the army. With building victory enabled, losing all key buildings eliminates an army; the remaining alliance wins. **Free play** has no such victory condition.

**Rules → Advanced… → EMP** controls vehicles, mobs and redstone separately. Original EMP weapons keep their native vehicle behaviour. In Minecraft, a pulse temporarily stops mob AI and suppresses standard redstone signals; it does not replace blocks or save a permanent NoAI flag. Modded circuitry may use different signal APIs.

**Rules → Advanced… → Mobs** controls microwave stuns and ECM deflection of mob-fired arrows. Stuns expire without removing health. These options do not change weapon effects on Generals vehicles.

## Lobby and markers

The lobby uses a Generals-style roster and map preview. Click a cell to choose a general, color or team; click the NPC name to change its difficulty or remove it. On smaller screens the map opens through **Map**. The dark-and-gold theme is built in. With Modern UI installed, supported versions use its native buttons, text fields, and ripple effects.

**Ready** confirms your choices. The match starts when every player is ready; changes to settings, starts or players clear readiness. **Chat** or **T** opens room chat. Esc returns to the lobby.

**Debug** is in **F9 → World & markers → Debug menu**. It requires singleplayer Creative Free play and is disabled in skirmishes.

The host sets starting cash and the superweapon limit through the lobby’s **Settings** before launch. General, color, team and start choices are remembered for each connected game; occupied or missing choices fall back to available values.

Click a roster cell to choose a value. The list opens at the current selection. Up/Down moves between choices and pages; Enter chooses, Esc closes. Primary actions, keyboard focus and switches share the built-in theme, with or without Modern UI. The server resolves random choices at match start.

**Map → Starts** selects starts; **Map → Resources** shows placed supply and oil markers. Drag to pan, scroll to zoom, use **My position** to recenter or **All points** to fit markers.

Markers are in the **Generals** creative tab. Interact with an army-start marker to set its player/NPC, faction, team and difficulty. Oil derricks use the original Generals capture ability. **Generals → World & markers → Find markers** opens saved marker details, notes, map, deletion and permission-checked teleport, including unloaded areas. Back and Esc save edited names and notes after server acknowledgement. If saving fails, the draft stays open; Discard leaves without saving. Marker beams appear in ordinary Minecraft. Nearby loaded markers always show clearing outlines: blue for starts, green for supplies, gold for oil. Start size covers the largest starting building and its rotation.

Clicking a mob without an armed selection shows its icon. With armed units selected, it issues a normal attack; Ctrl is not required.

## Drive a vehicle

In Minecraft mode, interact with your vehicle to board it. The server checks army ownership.

| Default input | Action |
| --- | --- |
| WASD | Move relative to the camera |
| Mouse | Aim the turret, using its original turn speed |
| Hold left click | Fire with the original weapon cooldown |
| Release left click | Stop firing |
| Shift | Leave the vehicle |

Movement, attack, and sneak use your Minecraft key bindings. Third-person view moves over the shoulder so the rider does not cover the reticle. The aim marker is gold within weapon range and orange outside it. An unaimed or out-of-range weapon cannot hit the marker yet. Arrows stay attached to units where they hit.

To gather resources, build a supply center and left-click a supply dock with your Chinook, truck, or worker selected. Supply stock resets when a new match starts.

## Debug menu

In singleplayer Creative Free play, open **Generals → World & markers → Debug menu** for money, rank, all general powers, cooldowns, healing, fog and music. Debug is disabled in every skirmish, including matches against NPCs. The server also rejects direct Debug requests. Only the world host can change rules; on a dedicated server, the lobby host must also be an operator. `/zh-debug` exists only in development builds.

EMP and ECM can temporarily disable mobs. They turn blue and show the original lightning animation. **Rules → EMP → Stun mobs** controls EMP/ECM; **Rules → Mobs → Microwaves stun mobs** controls the microwave tank separately. Slimes remain neutral to the army until attacked.

The RTS camera keeps its height over craters. Scroll in further to descend manually.

Finish RTS through its menu. F8 only changes the view. RTS units are not saved between launches; Minecraft terrain and markers are saved.

[Installation](INSTALL.md) · [README](../README.md)
