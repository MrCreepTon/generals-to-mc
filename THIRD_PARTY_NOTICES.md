# Credits and source

**English** · [Русский](THIRD_PARTY_NOTICES.ru.md)

Generals in Minecraft integrates Minecraft Java with a source-built Command & Conquer: Generals — Zero Hour engine. The adapter is developed with AI assistance (Codex). No new game art or audio is generated, and no retail game assets are included.

- Electronic Arts: original Generals / Zero Hour engine, GPL-3.0-or-later with the additional terms reproduced in LICENSE.md. Original source: https://github.com/electronicarts/CnC_Generals_Zero_Hour
- TheSuperHackers: modernized engine and dependency/build maintenance. Pinned revision: f746af7e626c96e693cb74a032da47a602c1a039 of https://github.com/TheSuperHackers/GeneralsGameCode
- Fabric Loader / Fabric API: https://fabricmc.net/ — Apache-2.0. Fabric API is included as a nested dependency with its own license; Loader is installed separately in the Minecraft instance.
- Fabric Loom (build tool, not bundled): MIT, https://github.com/FabricMC/fabric-loom/blob/dev/1.18/LICENSE.
- Minecraft and its assets belong to Mojang / Microsoft and are not included.
- The universal-modder skills and references informed the development and verification workflow: https://github.com/rehan-remade/universal-modder

The JAR contains the adapted source-built Windows x86 engine, not the original retail executable. Proprietary archives, textures, sounds and runtime providers are read from the user's installed games. They are not distributed by this project.

The corresponding adapter sources, integration patches, pinned upstream revision and reproducible build instructions accompany the build as a source archive. Build-Native.ps1 applies the patches to a separate public-source checkout. Keep LICENSE.md and these notices with redistributions. This project is unofficial and is not affiliated with EA, Mojang or Microsoft.
