# Авторы и исходники

[English](THIRD_PARTY_NOTICES.md) · **Русский**

Generals in Minecraft соединяет Minecraft Java с движком Generals — Zero Hour, собранным из исходников. Адаптер разработан с помощью ИИ (Codex). Новая игровая графика и звук не генерировались; оригинальные игровые данные не включены.

- **Electronic Arts:** исходный движок Generals / Zero Hour, GPL-3.0-or-later с дополнительными условиями из [LICENSE.md](LICENSE.md). [Исходники EA](https://github.com/electronicarts/CnC_Generals_Zero_Hour).
- **TheSuperHackers:** обновлённый движок, зависимости и сборка. Закреплена ревизия `f746af7e626c96e693cb74a032da47a602c1a039` из [GeneralsGameCode](https://github.com/TheSuperHackers/GeneralsGameCode).
- **Fabric Loader / Fabric API:** [fabricmc.net](https://fabricmc.net/), Apache-2.0. Fabric API включён как вложенная зависимость со своей лицензией. Loader устанавливается отдельно в профиль Minecraft.
- **Fabric Loom:** инструмент сборки, в мод не включён. [MIT](https://github.com/FabricMC/fabric-loom/blob/dev/1.18/LICENSE).
- **Minecraft:** игра и её данные принадлежат Mojang / Microsoft и не включены.
- **universal-modder:** навыки и справочные материалы использовались при разработке и проверке. [Репозиторий](https://github.com/rehan-remade/universal-modder).

JAR содержит адаптированный Windows x86 движок, собранный из исходников, а не оригинальный EXE игры. Закрытые архивы, текстуры, звуки и компоненты читаются из установки пользователя и не распространяются проектом.

К сборке прилагается архив соответствующих исходников адаптера, патчей, закреплённой ревизии и инструкций воспроизводимой сборки. `Build-Native.ps1` применяет патчи к отдельной копии публичных исходников. При распространении сохраняйте `LICENSE.md` и эти уведомления. Проект неофициальный и не связан с EA, Mojang или Microsoft.

[Лицензия](LICENSE.ru.md) · [README](README.ru.md)
