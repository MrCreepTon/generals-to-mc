# Beta limitations

**English** · [Русский](KNOWN_ISSUES.ru.md)

- Windows and Fabric only. Use the JAR matching your exact supported Minecraft version. Linux/Wine is not supported.
- Minecraft 26.3 has a guard against a rare Windows JVM crash during native shader compilation. Guarded checks pass, but an unguarded control run also passed; the fix is not conclusive. Updating Java alone did not resolve the reproduced crash.
- Your own Generals and Zero Hour are required. Generals without Zero Hour is not ported. Vanilla and installed ROTR have been tested, without covering every ROTR unit or ability. Contra has not been tested. Mods changing EXE/DLL code need separate support.
- Some original modes, models, materials, shadows and abilities still differ or are missing.
- RTS saves, campaigns/Challenge, replays and joining/rejoining ongoing matches are not supported.
- Long routes through unknown chunks, large terrain regions, multilevel navigation and world boundaries need further work.
- NPC-only spectators, village defence and capture/hold objectives are not implemented.
- Two actual 26.3 clients passed eight minutes with an NPC and injected 0/100/300/0 ms RTT. A separate five-minute single-client shared simulation with an NPC passed. Earlier two-client checks cover 1.21.4. Remote dedicated servers, long WAN sessions, large armies and complete multiplayer checks on every profile remain unverified.
- Fire and destruction rules apply to Generals weapons. Minecraft fires and explosions follow Minecraft gamerules.
- Supply, oil and start markers persist with the world. A full building palette and natural oil generation are not available.

Checks cover specific scenarios, not every possible failure. If the engine stops, use the recovery screen and logs at `%LOCALAPPDATA%/GeneralsMinecraft`.

[Installation](INSTALL.md) · [Changelog](../CHANGELOG.md) · [README](../README.md)
