# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project identity

**AR Web Scanner** — JavaFX desktop application that records user actions on web pages (via Selenium + an embedded Chromium through JCEF) so a separate runtime called **AR Web Engine** can replay them. The Scanner is the authoring tool; the Engine (lives in a different repo, artifact `Engine.jar` / `AR_Web_Engine.jar`) is the executor. The two share DTO models and database schemas — changes to anything under `com.allinweb.ch.model` must be verified against the Engine, because classes are historically copy-duplicated between the two projects (see README "Premise").

There is also a sibling repo `ar-web-mobile` at `D:\Projects\AllinWeb\ar-web-mobile` that carries the Appium side and an `com.allinweb.ch.vision.ocr` package (Tess4J + OpenCV). When OCR/vision capabilities are needed here, prefer reusing that package over reimplementing.

## Build, run, test

```bash
# Run the scanner UI
mvn javafx:run

# Full build (produces the shaded fat jar in target/)
mvn clean package

# Run tests (JUnit 5 via Surefire)
mvn test
mvn -Dtest=ClassName#methodName test   # single test

# Formatting is enforced by Spotless (palantir-java-format) on the `validate` phase —
# `mvn validate` runs both `check` and `apply`, so your code gets auto-formatted every build.
mvn spotless:apply
```

Runtime expects a config file. Pass it with `-c`. The working config for this dev machine is:
```
-c "D:\Projects\ARWeb-Martini\Config-4.2\ARWeb.config"
```
(There are multiple config folders at the repo root — `config/`, `Config-4.7/` — but the one actively in use is `Config-4.2` in the sibling workspace above. Don't edit the in-repo ones expecting the running app to pick them up.)

JavaFX VM options (needed when running from IDE):
```
--module-path "<path>\javaFX\lib" --add-modules javafx.controls,javafx.web,javafx.fxml
```
See `README-DEBUG.md` for the canonical IDE launch arguments.

### Runtime JS plugins (live injection payloads)
The scanner does **not** inject a single bundle. It loads separate minified plugins from an **external runtime directory** pointed at by `PATH_PLUGINS`. On this dev machine the live files are:

```
D:\Projects\ARWeb-Martini\ARWeb\plugins\hoverPick\build\hoverPick.min.js
D:\Projects\ARWeb-Martini\ARWeb\plugins\actionExecutor\build\actionExecutor.min.js
D:\Projects\ARWeb-Martini\ARWeb\plugins\searchListAsync\build\searchListAsync.min.js
```

`src/main/resources/plugins/pageScanner/` ships a bundled `build/scanner.min.js` but the running app loads the per-plugin minified files above. When you edit plugin source, rebuild the specific plugin's `build/<name>.min.js` under its own folder — editing the in-resources copy will not affect a running instance that points `PATH_PLUGINS` at the external directory. Check the active `configuration.properties` for the `path_plugins` entry before rebuilding.

## Architecture you need in your head before editing

### Entry point and bootstrap
`com.allinweb.ch.ARControlPanel` (JavaFX `Application`) wires singletons in a static block — `PerformDataBase`, `PerformInitializer`, `PerformMessage`, `ARPropertyManager`, three scenes (`ARLicenseScene`, `ARConfigurationScene`, `ARMainScene`), and two WebSocket servers (`ARWebSocketServer`, `ARWebSocketServerIP`). Ports are chosen at startup and exported via `System.setProperty("ARWebChosenPort*")`. License validation runs before the UI is shown.

### Package layout (what each one owns)
- **`facade`** — `Perform*` singletons are the service layer. `PerformActions` drives Selenium, `PerformCloneLoad` runs the element-picking flow, `PerformDataBase` owns persistence, `PerformMessage` is the serialization + user-feedback hub (note: `outputJsonElementDTO` is the canonical DTO-to-disk writer used by pick events).
- **`socket`** — Jetty-based WebSocket layer. `SimpleWebSocketServer` has a big switch on a verb enum (`SEARCH_TOOL`, `addPickOne`, `mobile-return-server`, `scannerGrid`, `mobileScannerGrid`, etc.) that routes UI ↔ browser-injected-JS ↔ mobile-companion messages. `WebSocketSessionManager` is the singleton session registry.
- **`driver`** — `ARWebDriver` wraps the Selenium driver lifecycle. Default target is Chrome; `WEBDRIVER.md` documents the Edge/proxy variants.
- **`component/scene` + `component/pane`** — UI. Scenes = windows, Panes = content. Every UI class extends `ARPane` (see README "UI Structure").
- **`model`** — DTOs. The central one is `ElementDTO` (fields include `xPath`, `customXPath`, `someText`, `definedName`, `attribId`, `attribName`, `coordinates`, `cssSelector`, `shadowHost`, `attributeData[]`, plus mobile extension `androidData[]`). `SplitDTO` is the per-instruction payload that flows over the WebSocket. Lombok `@Data` is pervasive.
- **`db`, `migration`** — schema migrations are numbered Java classes in `db/migrations/M<YYYYMMDD>_*.java` executed by `MigrationRunner`. Add new migrations by dropping a new dated class there; do not edit an applied migration. Three engines are linked in (`postgresql`, `sqlite-jdbc`, `ucanaccess` for MS Access `.accdb`) — which one is active depends on the loaded `configuration.properties`.
- **`util`** — cross-cutting. `ARPropertyManager` is the singleton config reader; every runtime path (DB data dir, engine jar, webdriver, plugins) comes through it via `ARPropertyEnum` keys (`PATH_DB`, `PATH_ENGINE`, `PATH_WEBDRIVER`, `PATH_PLUGINS`, `PATH_LICENSE`). `ARConstants` / `ARConstantsEngine` hold the baked-in defaults. `ARPriorities` + `Priority.java` + `PriorityTypeEnum` implement the per-site element-matching priority table (see `Priorities Properties README.md` + `xPath Auto.md`).
- **`vision`** — `VisionElement` + `UiElementType` + `VisionElementMapper`. Stubs for the upcoming OCR integration; real OCR engine sits in the sibling `ar-web-mobile` module under `com.allinweb.ch.vision.ocr`.
- **`license`** — `LicenseManager` + fingerprinting (`util/LicenseFingerprint`). Licence enforcement is on by default (`isEnabledLicence = true` in `ARControlPanel`).

### The element-matching contract
The scanner writes DTOs; the Engine resolves them at run time. The fallback ladder is **deterministic and first-match-wins** (see `xPath Auto.md`):
1. `xPath` / `customXPath`
2. `definedName` (case-insensitive exact)
3. `someText` (case-insensitive exact)
4. `someText` attribute (exact)
5. `someText` attribute (contains)
6. Priorities table per site (`[site-section]` blocks in the config file) can insert attribute-based lookups (`test-id`, custom attributes) at higher priority
7. Coordinates (last resort)

When you add a field to `ElementDTO` that is meant to act as a locator, you must also extend this ladder — otherwise the Engine silently ignores it. Do not add stealth fallbacks that are not in this table.

### Runtime data layout
At startup `ARPropertyManager` derives paths relative to `user.dir` if they aren't set in the config:
- `PATH_DB` → typically `<parent>/ARWeb` — holds pick DTOs, logs, reports, screenshots, plus the database files for SQLite/Access modes.
- `PATH_PLUGINS` → `<user.dir>/plugins` — loadable plugin jars.
- `PATH_WEBDRIVER` → `<user.dir>/driver` — bundled msedgedriver/chromedriver.

Features that persist artifacts on pick events always write them under `PATH_DB` (e.g. `elementDTO-HP.json`, `AI-ElementDTO-HP.json`, and anything generated by the roadmaps in `specifications/`).

## Conventions worth knowing

- **Singletons via `getInstance()`** — every `Perform*` class, `ARPropertyManager`, and `WebSocketSessionManager` follow this pattern. Do not replace with DI unless you're doing it consistently.
- **Lombok** — `@Data`, `@Slf4j`, `@AllArgsConstructor`, `@NoArgsConstructor` are standard. Keep it that way; hand-written boilerplate should be removed when you touch a file.
- **Spotless auto-fix on validate** — you'll never see import-order or formatting review comments; the build applies them. Don't fight it.
- **Dependency exclusions matter** — `selenium-java` and `ucanaccess` both explicitly exclude `slf4j-api`, `log4j-core`, `commons-logging` to force the Logback + `log4j-to-slf4j` bridge. Adding a new transitive dep that reintroduces log4j 2 core or a second slf4j binding will break logging.
- **Configuration format** — `configuration.properties` uses `[section]` blocks for per-site overrides and priority tables; the `#numero priorità, categoria, identificativo` header comment is a canonical marker, not noise.
- **Roadmaps and specs** — new feature plans live in `specifications/` (e.g. `AVALOQ_SEARCH.md`). Engineering investigation write-ups use the `ENGINEERING_REPORT_*.md` / `INVESTIGATION_REPORT.md` naming at the repo root.

## Things that will bite you

- The Engine is a separate artifact assumed to sit at `Engine.jar` / `AR_Web_Engine.jar` in the project root (or wherever `PATH_ENGINE` points). Missing it means `PerformDBEngine` and anything that spawns the Engine CLI will fail at runtime, not at build time.
- `ARControlPanel` assumes Windows paths in several default derivations (`parentPath + "\\ARWeb"`, etc.). Cross-platform work requires touching `ARPropertyManager.setDefaults*`.
- JCEF (`me.friwi:jcefmaven`) bundles native binaries under `jcef-bundle/`. The first run extracts them; on Linux/mac the platform classifier differs. Don't commit the extracted bundle.
- `.ARWebScanner.lock` is the single-instance guard (see `util/SingleInstance`). Stale locks after a crash block restart — delete the file.
- The `target/` directory is committed-adjacent in repo listings (large artifacts), but `ar-web-selenium.zip` at the root is a distributable snapshot, not sources. Do not edit inside the zip.
