# Сборка и релиз

[English](DEVELOPMENT.md) · **Русский**

Выполняйте команды из корня репозитория. Игроку нужен готовый JAR, инструменты сборки ему не нужны.

## Локальная сборка

Нужны Windows, GNU Make, Python 3.10+, Git, CMake 3.28+, Visual Studio 2022 C++ с инструментами x86 и Windows SDK, JDK 25 для Gradle и JDK 21 для Minecraft 1.21.x. Если Gradle не находит Java, задайте `JAVA_HOME` и `JAVA21_HOME`.

```sh
make                                      # Движок один раз, затем все версии
make build MC=1.21.4                       # Одна версия Minecraft
make release                              # Все пакеты в dist/<mod_version>/
make package                              # Собрать уже готовые проверенные пакеты
make source                               # Исходники вместе с Makefile
make versions                             # Доступные профили
make help
```

Без Make выполните `python tools/build.py release`. Пакеты отдельных версий находятся в `build/release/player/<Minecraft>/`. По умолчанию выбрана 26.3. `make release` также собирает обе версии документации, исходники и SHA256. Ничего не публикует.

`Build-Native.ps1` собирает движок ревизии `f746af7e626c96e693cb74a032da47a602c1a039` с адаптерами. Результат находится в `build/native/windows-x86/generalszh.exe`. Все профили Minecraft используют этот движок. Оригинальные ресурсы остаются в установленной игре пользователя.

Чтобы переиспользовать собранный движок, передайте `NATIVE_BIN=/path/to/generalszh.exe`. Так можно собирать JAR на Linux, но для игры клиентам и серверу по-прежнему нужен Windows. Gradle принимает тот же путь через `-PnativeBinary=...`. Среда C++ связана статически.

## Версии Minecraft

Общий код и последовательные адаптеры находятся в одной ветке. Выбирайте сборку через `MC=...` и устанавливайте только соответствующий JAR. Версия по умолчанию задана в `gradle.properties`.

| Путь | Назначение |
| --- | --- |
| `versions/*.properties` | Точные версии Minecraft, Fabric Loader, Fabric API, Java и адаптеров |
| `src/main`, `src/client` | Общий код на основе 1.21.4 |
| `src/versions/modern` | Изменения API для 1.21.11 |
| `src/versions/26.1`, `26.2`, `26.3` | Следующие изменения API; 26.1.1 и 26.1.2 используют 26.1 |

Для новой версии создайте профиль, адаптируйте изменившиеся API и выполните `stageRelease`. Поздний адаптер заменяет ранний файл с таким же относительным путём. `tools/ci_versions.py` подхватит профиль автоматически. Номер без профиля отклоняется. Проверьте ввод, F8, отрисовку, туман, звук, маркеры, сохранённые данные и сеть в готовом JAR. Одной компиляции недостаточно.

## GitHub Actions

Откройте **Actions → Build mod → Run workflow**, выберите `all` или точную версию. Скачайте пакет из **Artifacts**. CI собирает движок на Windows, затем JAR на Linux. Установленные игры и секреты не нужны.

Изменения только README и `docs/` не запускают сборку при push и pull request. Ручной запуск и теги релиза её запускают. Ключ кэша движка учитывает `native/`, `Build-Native.ps1`, `tools/prepare_engine.py` и `tools/patch*.py`. Изменения Java используют готовый движок при совпадении ключа. Для пересборки удалите запись в **Actions → Caches**. Проверки JAR выполняются как обычно.

## Проверки

`stageRelease` выполняет 43 проверки общей логики и аудит пакета. Аудит сверяет хеш движка и зависимости, отклоняет игровые ресурсы, Markdown и тестовые драйверы внутри JAR. Архив исходников и документация лежат рядом с JAR.

Игровые проверки используют отдельный приватный драйвер, постоянные профили и миры QA. Они переиспользуют игры в `C:/Games/Generals` и `C:/Games/Generals ZH`. Отчёты находятся в `evidence/`. Не копируйте установки и миры между прогонами.

```powershell
.\gradlew.bat productionSmoke '-Pminecraft_target=1.21.4' -PnativePolishRegression -PnativePolishSkipRoster -PnativeReportsRegression -PnativeLatestRegression -PnativeTestLanguage=en_us
.\gradlew.bat productionSmoke '-Pminecraft_target=1.21.11' -PnativeTestLanguage=en_us
.\gradlew.bat productionSmoke '-Pminecraft_target=26.3' -PnativeTestLanguage=en_us
python tools/verify_native_patch.py
```

Команда для 1.21.4 проверяет готовность, чат, восстановление склада, обычный клик и сдачу ресурсов, коллизии, посадку, пассажира в третьем лице, огонь, стрелы, отрисовку и повышения в неизведанной области. Проверки новых версий включают движение, посадку, отпускание огня, меню и пиксели тумана.

Для отдельных сценариев:

| Сценарий | Добавьте к `productionSmoke` |
| --- | --- |
| Меню без боя, 1.21.11 или 26.x | `-PnativeUiOnly` |
| Навигация меню на 1.21.4, прежний QA-мир | `-PnativeNavigationOnly` |
| Настоящий Modern UI на поддерживаемой версии | `-PnativeExtraMods=build/qa/addons/<версия>` |
| F8 и Peaceful, 1.21.4 | `-PnativeResumeRegression -PnativeResumeSticky` |
| Размещение, коллизии, ЭМИ/ECM, 1.21.4 | `-PnativePolishRegression` |
| Супероружие, высокие цели, обход кактусов, 1.21.4 | `-PnativePolishRegression -PnativeStrategicRegression` |
| Два клиента Minecraft с задержкой, 26.3 | `-PnativeStabilityNetwork -PnativeStabilitySeconds=120` |
| Перезагрузка ресурсов, 26.3 | `-PnativeReloadCycles=12` |
| Четыре кадра гайда, 26.3 | `-PnativeUiOnly -PnativeGuideCapture -PnativeTestLanguage=en_us` или `ru_ru` |

Нативные проверки принимают `--exe build/native/windows-x86/generalszh.exe --lab <QA-lab>`. `tools/native_supply_regression_bench.py` проверяет сбор обычным кликом у всех трёх сторон. `tools/native_latest_bench.py` проверяет поворот башни без огня, отпускание стрельбы, непрозрачность самолётов и захват нефтевышки NPC. `tools/native_navigation_bench.py` проверяет движение по местности Minecraft. Нужна существующая лаборатория QA.

Modern UI использует настоящие `Fragment`, `Button`, `EditText` и `RippleDrawable`. SDK нужен только для компиляции. Общие контроллеры Minecraft проверяют ввод и отправляют запросы серверу. Адаптеры поддерживают 1.21.4 и 26.1 по 26.1.2. Остальные профили используют встроенную тему. Дерево интерфейса описано на Java.

Скриншоты README сняты с английским интерфейсом. Для нового захвата добавьте `-PnativeReleaseUx -PnativeReadmeCapture -PnativeTestLanguage=en_us` к запуску 1.21.4. Значение `=ui` у `nativeReadmeCapture` пропускает бой. Кадры находятся в `build/production-smoke/screenshots/`. Полная проверка первого запуска создаёт новое приватное подключение. Для остальных правок используйте отдельные сценарии.

## Диагностика задержки ввода

Во время игры выполните `python tools/watch_latency.py --seconds 30 --capture latency-report.json` и отдайте приказы движения и атаки. Для приватных проверок добавьте `--root build/qa/native`.

| Показатель | Что измеряет |
| --- | --- |
| Native Hz | Частота симуляции, обычно около 30 Гц |
| `ipc_pending` | Локальная очередь команд движку, не сетевой пинг |
| `snapshot_age` | Время с последнего состояния движка |
| `frameIntervalMs` | Интервал кадров Minecraft |
| `clickHandlingMs` | Определение цели и отправка клика |
| `localIpcAckMs` | Время до обработки ввода нативным процессом |
| `sceneIngestMs`, `meshSubmitMs` | Обработка сцены и отправка геометрии на клиенте |

Поворот юнита происходит после доставки ввода. Сравните одиночный и сетевой отчёты на компьютере с проблемой. Если нативные показатели в норме, а Minecraft тормозит, запишите F3+L. В отчёте задержки есть тайминги и сведения о среде, без названий мира и игроков. `-PnativeStabilityJfr` добавляет ограниченную запись Java в проверку стабильности. Приватный сетевой тест вводит 0/100/300/0 мс RTT с джиттером и проверяет восстановление. Он не заменяет WAN-матч.

## Выпуск релиза

1. Задайте `mod_version` в `gradle.properties`, обновите оба файла истории изменений.
2. Соберите все профили и выполните нужные игровые проверки. Проверьте JAR, исходники и хеши.
3. Закоммитьте и отправьте проект. `.github/workflows/build.yml` должен лежать в корне репозитория.
4. Отправьте совпадающий тег:

```sh
git tag v0.2.0-beta.24
git push origin v0.2.0-beta.24
```

5. Откройте **Releases**, проверьте черновик и нажмите **Publish release**. Теги beta создают предварительные выпуски.

Несовпадающий тег отклоняется до сборки движка. Повторный запуск обновляет черновик, но не опубликованный релиз. Не добавляйте в Git `build`, `run`, `private`, `evidence`, установки и извлечённые или декомпилированные игровые данные. Архивы исходников собираются по явному списку файлов.

[README](../README.ru.md) · [Изменения](../CHANGELOG.ru.md)
