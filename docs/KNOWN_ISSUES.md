# Beta limitations

**English** · [Русский](KNOWN_ISSUES.ru.md)

- Clients and the server require Windows and Fabric. Linux/Wine is not supported. Use the JAR for your exact Minecraft version.
- Bring your own Generals and Zero Hour. Generals without Zero Hour is not ported. Ordinary Zero Hour and installed Rise of the Reds have been tested; full ROTR unit coverage and Contra remain unverified. Mods that change EXE or DLL code need separate support.
- RTS saves, campaigns, Challenge, replays, and joining an ongoing match are not supported. Markers, notes, and world rules persist, but armies do not survive closing Minecraft.
- Long routes through unknown chunks, multilevel navigation, world boundaries, and large armies need more testing.
- Minecraft 26.3 has a guard for an intermittent JVM crash during native shader compilation. Short guarded runs pass, but the fix is not conclusive. Report repeat crashes with the recovery logs.
- Network tests cover controlled local clients, NPCs, injected latency, and recovery. Remote dedicated servers and long WAN matches remain unverified. A passing scenario does not establish every multiplayer case.
- NPC-only spectator games, village defense, capture-and-hold objectives, a full building palette, and natural oil generation are not implemented.
- Generals destruction and fire rules apply to Generals weapons. Minecraft explosions and fires follow Minecraft gamerules. Modded redstone may bypass the vanilla signal hooks used by EMP.

If the engine stops, use the recovery screen. Connections and logs are in `%LOCALAPPDATA%/GeneralsMinecraft`.

[Installation](INSTALL.md) · [Changelog](../CHANGELOG.md) · [README](../README.md)
