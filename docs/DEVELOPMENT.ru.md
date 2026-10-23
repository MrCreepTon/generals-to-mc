# Сборка и релиз

[English](DEVELOPMENT.md) · **Русский**

Выполняйте команды из корня репозитория, где находятся `Makefile`, `build.gradle` и `.github/`. Игроку нужен только готовый JAR.

## GitHub Actions

Откройте **Actions → Build mod → Run workflow**. Выберите `all` или профиль, например `26.3`. Скачайте `generals-minecraft-mc26.3` из **Artifacts**: внутри JAR, документация, контрольные суммы и соответствующие исходники.

CI собирает закреплённый движок на Windows, затем JAR на Linux. Установленные игры и секреты не нужны. Сборка на Linux не означает поддержку запуска на Linux. Workflow проверен локально; для удалённого запуска загрузите репозиторий на GitHub.

Нативный EXE кэшируется отдельно. Изменения в `native/`, `Build-Native.ps1`, `tools/prepare_engine.py` или `tools/patch*.py` запускают его сборку заново; изменения Java и документации используют готовый EXE. Компиляция пропускается только при точном совпадении ключа. В ключ включено семейство runner/компилятора; для принудительной пересборки измените суффикс `v1` или удалите запись в **Actions → Caches**. Проверки JAR и аудит выполняются как обычно.

## Локальная сборка

Нужны Windows, GNU Make, Python 3.10+, Git, CMake 3.28+, Visual Studio 2022 C++/Windows SDK с инструментами x86, JDK 25 для Gradle и JDK 21 для целей 1.21.x. При необходимости задайте `JAVA_HOME` и `JAVA21_HOME`.

```sh
make                        # Движок один раз + все версии Minecraft
make build MC=26.3          # Одна версия
make release               # Полная сборка в dist/<mod_version>/
make versions              # Список профилей
make source                # Чистый ZIP исходников
make help
```

Пакеты отдельных версий находятся в `build/release/player/<Minecraft>/`. По умолчанию выбрана 26.3. Остальные профили: 1.21.4, 1.21.11, 26.1, 26.1.1, 26.1.2 и 26.2.

`make release` собирает все JAR, исходники, документацию на двух языках и SHA256. `make package` собирает уже проверенные пакеты. Обе команды ничего не публикуют. Без Make используйте `python tools/build.py release`.

`Build-Native.ps1` собирает публичный движок ревизии `f746af7e626c96e693cb74a032da47a602c1a039` с патчами адаптера в `build/native/windows-x86/generalszh.exe`. Один движок используется всеми версиями Minecraft и профилями игры. Он читает установленную игру; это не её оригинальный EXE.

Чтобы не пересобирать C++, передайте `NATIVE_BIN=/path/to/source-built/generalszh.exe`. Так работает Linux-задание CI после Windows-сборки движка. Gradle также принимает `-PnativeBinary=...`. C++ runtime включён статически; отдельный Visual C++ Redistributable не нужен.

## Поддержка версий

Используется одна ветка с общим кодом и последовательными адаптерами. Другую версию собирайте через `MC=...`, для игры ставьте соответствующий JAR. Одновременно устанавливается один JAR Generals. Версия по умолчанию задана в `gradle.properties`.

| Где | Что находится |
| --- | --- |
| `versions/*.properties` | Точные версии Minecraft, Loader, Fabric API, Java и адаптеров |
| `src/main`, `src/client` | Общий код на базе 1.21.4 |
| `src/versions/modern` | Изменения для 1.21.11 |
| `src/versions/26.1`, `26.2`, `26.3` | Новые API; 26.1.1/26.1.2 используют слой 26.1 |

Для новой версии:

1. Создайте `versions/<версия>.properties` с точными значениями `minecraft`, `loader`, `fabric_api`, `java`, `unobfuscated` и `overlays`.
2. Используйте готовые адаптеры и добавьте только изменённые файлы в `src/versions/<слой>/{main,client}/{java,resources}`. Поздний слой заменяет ранний файл с тем же относительным путём.
3. Запустите `stageRelease` с новым профилем. `tools/ci_versions.py` сам включит его в CI. Номер без профиля отклоняется.
4. Проверьте готовый JAR: ввод, возврат в Minecraft, модели, туман, звук, маркеры, данные мира и сеть. Успешной компиляции для подтверждения совместимости недостаточно.

Для 1.21.x используется Java 21, для 26.x — Java 25. До 26.x применяется remap, в 26.x — Loom для необфусцированной Minecraft.

## Проверки

`stageRelease` запускает 38 проверок общей логики и аудит JAR: SHA256/импорты движка, отсутствие игровых данных и тестовых драйверов. Проверки настоящего клиента выполняются отдельно:

```powershell
.\gradlew.bat productionSmoke '-Pminecraft_target=1.21.11' -PnativeFeedbackRegression -PnativeTestLanguage=en_us
.\gradlew.bat productionSmoke '-Pminecraft_target=26.3' -PnativeFeedbackRegression -PnativeTestLanguage=en_us
python tools/native_pointer_bench.py
python tools/native_path_budget_bench.py
python tools/native_material_bench.py
python tools/native_lockstep_bench.py --graphics-peers --combat
python tools/verify_native_patch.py
```

`productionSmoke` запускает готовый JAR с отдельным приватным драйвером, мирами и профилями. Локальным сценариям нужны ваши игры в `C:/Games/Generals ZH` и `C:/Games/Generals`. Отчёты идут в `evidence/`, вне релиза.

Кадры для README: `productionSmoke -Pminecraft_target=1.21.4 -PnativeReleaseUx -PnativeReadmeCapture -PnativeTestLanguage=en_us`. Для русского языка укажите `ru_ru`, для съёмки только меню — `-PnativeReadmeCapture=ui`. Кадры сохраняются в `build/production-smoke/screenshots/readme-*.png`; драйвер съёмки не входит в JAR игрока.

Проверка F8/Peaceful на 1.21.4: `productionSmoke -Pminecraft_target=1.21.4 -PnativeResumeRegression -PnativeResumeSticky`. Проверяет потерянное отпускание Alt, смену сложности в меню паузы, выбор и движение мышью, пиксели штатной подсказки. Без `-PnativeResumeSticky` выполняет четыре обычных цикла F8.

Проверка двух клиентов 1.21.4: `runClient -Pminecraft_target=1.21.4 -PnativeNetworkBench -PnativeNetworkDualOnly -PnativeFakeLatency`. Она проверяет команды, темп симуляции и восстановление при 0/100/300/0 мс RTT с колебаниями.

Проверка стабильности 26.3: `productionSmoke -Pminecraft_target=26.3 -PnativeStabilityNetwork -PnativeStabilitySeconds=480`. Добавьте `-PnativeStabilitySolo` для одного клиента с общей симуляцией или `-PnativeStabilityJfr` для профилирования Java. Перезагрузка ресурсов: обычный `productionSmoke -PnativeReloadCycles=12`.

Короткий сценарий эффектов, транспорта, управления и маркеров: `productionSmoke -Pminecraft_target=<версия> -PnativeFeedbackRegression`. Он переиспользует тестовые профили и миры. Полный сценарий первой установки 1.21.4 (`-PnativeReleaseUx`) нужен при изменениях подключения игры; он создаёт отдельную чистую конфигурацию. Для изменений UI достаточно короткого целевого сценария. Боевые и сетевые проверки повторяйте при изменении симуляции, транспорта или исправлении регрессии. Они не заменяют долгие WAN-матчи и проверку больших армий.

Remap исходников зависимостей отключён для ускорения сборки. Для просмотра кода в IDE включите `-Dfabric.loom.ci=false`.


Проверка стартовых площадок и разрушений: `productionSmoke -Pminecraft_target=1.21.4 -PnativeResumeRegression -PnativeResumeSticky -PnativeWorldPolish`. Проверяет расчистку, отказ при наличии сундука без частичных изменений, обычную и принудительную атаку.

Проверка движения: `python tools/native_navigation_bench.py --exe build/native/windows-x86/generalszh.exe --lab <QA-lab>`. Нужен существующий тестовый профиль; установки и миры не копируются.

## Диагностика задержки

Во время матча выполните `python tools/watch_latency.py --seconds 30`. Для приватных проверок версий добавьте `--root build/qa/native`. Скрипт только читает снимки состояния и не создаёт файлов.

- Симуляция движка должна держаться около 30 Гц. Устойчивое падение указывает на работу движка или ожидание участника.
- Рост `ipc_pending` означает, что локальные команды ждут обработки движком. Это не сетевой пинг.
- Рост `snapshot_age` означает, что новые снимки перестали приходить. Проверьте журнал сессии и поле `error`.
- Если эти показатели нормальные, но растёт время кадра Minecraft, проверьте рендер через F3 и короткую запись F3+L.

Для отчёта выполните `python tools/watch_latency.py --seconds 30 --capture latency-report.json` и отдайте несколько приказов движения и атаки. Играйте до завершения записи. Скрипт создаёт небольшой запрос и результат в активной сессии, а также указанный отчёт. В нём — время кадров и кликов, версии Java/MC/мода, число потоков CPU и размер памяти Java. Имена игроков, миры и пути установок не записываются.

`frameIntervalMs` — интервалы кадров Minecraft; `sceneIngestMs` и `meshSubmitMs` — обработка и отправка сцены на отрисовку, без отложенной работы GPU. `clickHandlingMs` — выбор цели и отправка клика; `localIpcAckMs` — время до чтения ввода движком, до завершения поворота юнита или подтверждения сетевого хода. `nativeUpdateMs`, `nativeGuiMs`, `nativePublishMs` показывают работу движка, захват интерфейса и публикацию снимка; этапы могут пересекаться, складывать их нельзя. Сравните одиночный и сетевой матч на проблемном компьютере. На старых сборках в отчёте будут только native-замеры; клиентские доступны с beta.13. Если скачет время кадра, дополните отчёт записью Minecraft через F3+L.

Контрольная проверка двух клиентов 26.3: `productionSmoke -Pminecraft_target=26.3 -PnativeStabilityNetwork -PnativeStabilitySeconds=120`. В приватное соединение второго клиента добавляются 0/100/300/0 мс RTT с колебаниями, затем проверяется восстановление. `-PnativeStabilityJfr` включает ограниченные по размеру записи Java. Миры и профили тестов используют фиксированные каталоги; копировать проект или установку игры между запусками не нужно.


## Выпуск

1. Загрузите проект в свой GitHub-репозиторий с основной веткой `main`. Workflow должен лежать в `.github/workflows/build.yml`. При необходимости `make source` экспортирует чистую копию в `build/release/generals-minecraft-<версия>-source.zip`.
2. Запустите **Actions → Build mod → Run workflow → all**. CI собирает семь профилей и проверяет логику/пакеты; игровые тесты остаются локальными.
3. Задайте `mod_version` в `gradle.properties`, обновите оба файла истории изменений и сделайте коммит. Версия мода общая для всех профилей Minecraft.
4. Отправьте совпадающий тег, например:

```sh
git tag v0.2.0-beta.13
git push origin v0.2.0-beta.13
```

5. Откройте **Releases**, проверьте черновик и нажмите **Publish release**. Теги с `-beta` создают предварительные выпуски.

Несовпадающий тег отклоняется до сборки движка. Повторный запуск обновляет черновик, но не опубликованный релиз. Workflow использует `gh release create --draft`.

Не добавляйте в Git и релиз `build`, `run`, `private`, `evidence`, подключения, извлечённые игровые данные и декомпилированный код. `releaseSources` собирает файлы по явному списку; `stageRelease` прикладывает исходники к каждому пакету.

[README](../README.ru.md) · [Изменения](../CHANGELOG.ru.md)
