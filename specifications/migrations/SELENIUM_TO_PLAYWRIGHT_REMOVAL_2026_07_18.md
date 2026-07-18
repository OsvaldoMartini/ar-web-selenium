# Selenium to Playwright Removal — 2026-07-18

Status: In progress. Playwright is the only browser launcher in AR Web Scanner; the remaining
Selenium dependency is retained temporarily for legacy Java types and dormant fallback bodies.

## Scope and boundary

- This roadmap covers `ar-web-selenium` (AR Web Scanner).
- The separate external `ar-web-engine` launch still receives `path_web_driver`. That compatibility
  setting remains until the Engine is migrated independently.
- The React frontend and its Playwright navigation tests remain unchanged.

## Verified starting point

- No production source constructs `ChromeDriver`, `EdgeDriver`, `FirefoxDriver`, or
  `RemoteWebDriver`.
- Browser startup calls `ARPlaywrightDriver.openOrNavigate(...)`.
- Before this pass, 46 production files and 12 test files imported Selenium.
- After the first removal slice, 37 production files, 6 JUnit files, and 1 packaged plugin source
  still imported or referenced Selenium.
- After the continuation slice, the direct footprint was 16 production files, 1 JUnit file, and no
  packaged plugin source. The remaining files are concentrated in `TargetElement`, `PerformActions`,
  `ScannerRuntimeBackend`, and the legacy action helper package.
- The current working-tree checkpoint removes the dead `BrowserJsUtils` bridge and ports
  `EngineDialogs` browser messaging/QUIT to Playwright. The footprint is now 14 production files,
  1 JUnit file, and no packaged plugin source. This checkpoint passed the complete safe backend
  sweep described below.

## Completed first slice

- [x] Removed the always-empty global `List<WebDriver>` registry and startup plumbing.
- [x] Removed unused Selenium option/version/path state and dead WebElement helpers from
  `ARWebDriver`.
- [x] Deleted dead `WebElementScriptBuilder`, `WebElementScriptFactory`, `PerformCloseBrowser`,
  `DomIntrospectionUtil`, and `IframeInputLocator` chains.
- [x] Deleted six non-JUnit socket-injection programs that immediately aborted because no Selenium
  driver existed.
- [x] Replaced the scanner launcher contract with `openBrowser(...)`, which always opens or reuses
  Playwright and no longer validates a Scanner WebDriver executable.
- [x] Removed `use_playwright` and `playwright_selenium_fallback`; Playwright is no longer optional.
- [x] Removed `path_web_driver` from mandatory Scanner startup properties while preserving it for
  the external Engine launch.
- [x] Routed TEST RUN stop, QUIT, execution-tail close, Scanner close, and application shutdown
  through the Playwright-aware browser lifecycle.
- [x] Separated browser close from process shutdown so repeated closes reuse the Playwright executor
  and application exit terminates it.
- [x] Fixed Scanner shutdown so a null Selenium driver cannot prevent executor cleanup.
- [x] Added Guava as an explicit Maven dependency instead of relying on Selenium's transitive copy.

## Completed continuation slice

- [x] Replaced `ScannerSeleniumBrowserAdapter` with a browser-neutral Playwright adapter for URL,
  title, page source, viewport, support captures, and review files.
- [x] Added Playwright DOM element snapshots for support-file enrichment while preserving the
  existing JSON envelope and clicked-element HTML.
- [x] Ported Scanner workspace browser state and previous/next-tab controls to Playwright pages.
- [x] Converted `PerformListElements`, OCR screenshots, and DOM diagnostic rectangles to
  Playwright-only execution.
- [x] Removed the dead `ScannerPageScanService`, Selenium scan overloads, search-list script cache,
  actionExecutor Selenium injector, and always-null insert/update WebElement enrichment.
- [x] Retired the uncompiled duplicate `src/main/resources/plugins/PerformPreLoad.java` source.
- [x] Removed dead Selenium logger suppression, WebDriver report screenshots, legacy support
  adapters, unused locator helpers, and unreachable element-recovery fallback code.
- [x] Removed the uncalled Selenium iframe/highlight JavaScript bridge and ported in-page engine
  messages plus QUIT cleanup to Playwright.

## Current verification

- 415 production Java sources compiled successfully.
- 203 test Java sources compiled successfully.
- 66 focused migration, scanner, action, support, and browser-port tests passed; 5 browser-launch
  cases skipped because browser download is disabled in this environment.
- The complete safe backend sweep passed 711 tests with 0 failures/errors and 19 fixture/environment
  skips when `PerformDBEngineAccessTest` was excluded.
- The unfiltered default sweep ran 708 tests and had one unrelated failure: the live Access database
  path required by `PerformDBEngineAccessTest` does not exist in this environment.
- The regenerated inventory contains 894 displayed rows, 860 code cases, and 19,452 generated API
  requests.

## Remaining executable slices

### 1. Browser information and support ports

- [x] Add a Playwright implementation for current URL, title, page source, tabs, support capture,
  screenshots, and review files.
- [x] Replace `ScannerSeleniumBrowserAdapter` and remove WebDriver pass-through types from scanner
  services.
- [x] Convert the corresponding adapter/service tests to a browser-neutral fake.

### 2. Element model and scanning

- [ ] Replace `WebElement` storage in `TargetElement` with an immutable locator/snapshot contract.
- [ ] Move element lookup, iframe traversal, attributes, text, coordinates, and recovery to
  `ARPlaywrightDriver`/Playwright locators.
- [x] Convert `PerformListElements`, OCR/diagnostic dumpers, and scanner preparation services.
- [ ] Finish converting `ElementLocator`, `ElementDtoMapper`, and the remaining TargetElement helper
  paths.

### 3. Action execution

- [ ] Delete unreachable Selenium fallbacks from `PerformActions` and the `facade/actions` package.
- [ ] Remove `currentDriver` from `ARWebDriver`, `PerformActions`, `ActionContext`, and scanner
  runtime APIs.
- [x] Remove the unreachable Selenium actionExecutor injector and cache.
- [ ] Decide whether the remaining browser-side actionExecutor protocol has any Playwright-only use;
  otherwise retire its client/socket protocol in a later cleanup.

### 4. Tests and packaged plugin source

- [ ] Remove the final Selenium type from `PerformActionsPureMethodsTest` when locator criteria become
  browser-neutral.
- [x] Retire `src/main/resources/plugins/PerformPreLoad.java`.
- [x] Regenerate `automation-tests.json` after source/test removals.

### 5. Dependency removal gate

- [ ] Require zero matches for Selenium imports in production, tests, and packaged plugin sources.
- [ ] Remove `selenium-java` from `pom.xml` and verify no Selenium transitive artifact remains.
- [x] Remove obsolete Selenium logging suppression and WebDriver-specific Scanner messages.
- [ ] Run the clean full backend suite and the React Playwright navigation suite.

## Final acceptance commands

```powershell
rg -n "org\.openqa\.selenium" src/main/java src/test/java src/main/resources/plugins
mvn dependency:tree "-Dincludes=org.seleniumhq.selenium"
mvn clean test
```

The first command must return no source matches before the Maven Selenium dependency is deleted.
