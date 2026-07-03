# NEGATIVE IMPACTS — Selenium → Playwright Migration

> Authoritative inventory of every Selenium usage in the Scanner, classified by whether it
> can move to Playwright. Produced from a full read-only audit of the 47 files that import
> `org.openqa.selenium`. Use this as the migration roadmap: **MIGRATABLE** items can be ported
> mechanically; **PARTIAL** items port with semantic changes; **NO-EQUIVALENT** items are the
> "negative impacts" — they have no Playwright counterpart and require a design change (or must
> be dropped) before the corresponding Selenium code can be removed.

_Last updated: 2026-07-03. Config in use is Playwright-only (`use_playwright=true`,
`playwright_selenium_fallback=false`) — a single Playwright browser; Selenium is skipped._

---

## 0. The core reason a 1:1 migration is impossible

Selenium and Playwright have **fundamentally different element models**:

| | Selenium | Playwright |
|---|---|---|
| Element reference | `WebElement` — a **live handle** to a specific DOM node, reusable, comparable by identity, storable on a model | `Locator` — a **lazy selector** re-resolved on every action; not a handle, not storable, not identity-comparable |
| Frames | **stateful** `driver.switchTo().frame()/defaultContent()` mutates the driver's context | **stateless** `page.frameLocator(...)` is scoped per-locator; there is no "current frame" |
| Windows/tabs | opaque `getWindowHandles()` string handles + `switchTo().window()` | `context.pages()` returns `Page` objects; a different model |
| Waiting | explicit `WebDriverWait` + `ExpectedConditions` | implicit auto-wait for actionability on every action |
| Browser binary | external driver (`msedgedriver.exe`) + version handshake | Playwright bundles & manages the browser |

The existing Playwright wrapper is deliberately **selector/DTO-oriented** and **never returns an
element handle**: `PlaywrightActionExecutor` takes an `InstructionLoad` and acts;
`PlaywrightElementScanner.scan()` returns `List<ElementDTO>` (plain data). Therefore:

> **Any method that returns or accepts `org.openqa.selenium.WebElement` cannot be migrated 1:1.
> Its callers must be refactored to pass selectors/DTOs instead of receiving handles.**

This single fact accounts for the majority of the NO-EQUIVALENT items below.

---

## 1. Playwright target surface (what already exists)

- **`ARPlaywrightDriver`**: `open`, `navigate`, `goBack`, `setContent`, `evaluate(script, arg)`,
  `currentUrl`, `title`, `scanElements(searchTerms, includeHidden) → List<ElementDTO>`,
  `click(InstructionLoad)`, `fill(InstructionLoad, FieldData)`, `text(InstructionLoad)`,
  `isOpen`, `close`.
- **`PlaywrightActionExecutor`**: `click/fill/text(Page, InstructionLoad[, FieldData])`;
  internal `locate()` builds selectors (xpath→css→id→name→test-id), pierces open shadow DOM,
  handles iframes via `frameLocator`; click cascade normal→force→dispatch→mouse-coords.
- **`PlaywrightElementScanner.scan(Page, …) → List<ElementDTO>`**: one in-page JS survey,
  returns fully-populated DTOs (xpath, someText, attribId, coordinates, cssSelector, …).

### 1a. Wrapper GAPS to fill before some migrations are possible
These Playwright capabilities exist in the library but were **not exposed** on the wrapper.
✅ = added (commit 656758c6); ☐ = still to add.

| Needed method | Playwright API | Unblocks | Status |
|---|---|---|---|
| `screenshot(fullPage)` | `page.screenshot(...)` | OCR capture (`WebScreenshotCapture`, `PageOcrDumper`) | ✅ |
| `reload()` | `page.reload()` | `WindowAndFrameManager.refresh` | ✅ |
| `viewportSize()` | `page.viewportSize()` | coordinate math (`CoordinateActions`, `SupportCapture`) | ✅ |
| `content()` | `page.content()` | page-source dumps (`PageDiagnosticDumper`, DOM review) | ✅ |
| `evaluate(script)` / `evaluate(script, arg)` | `page.evaluate(...)` | JS surveys; multi-arg via a `List` arg → JS array | ✅ |
| `frames()` / frame content | `page.frames()`, `frame.content()` | iframe diagnostics without `switchTo` | ☐ |
| `pages()` / `bringToFront()` | `context.pages()` | tab navigation replacing `getWindowHandles` | ☐ |
| `hover` / `selectOption` / `press` (by selector) | `locator.*` | interaction helpers currently on `WebElement` | ☐ |

---

## 2. NO-EQUIVALENT — the negative impacts

These have **no reasonable Playwright counterpart** and block removal of the Selenium code until
the design around them changes. Grouped by theme.

### 2.1 Methods returning / accepting `WebElement` (the biggest block)
Playwright has no reusable element handle. Every one of these must be reworked to pass/return a
**selector string or `ElementDTO`**, and their callers updated.

- **`PerformActions`** (facade core) — `searchElement→WebElement` (L310), `locateElement→WebElement`
  (L679), `locateTargetElement→WebElement` (L675), `findElementByID/ByName/ByAttributeParams`
  (L277/281/285), `getElementAtCoordinates→WebElement` (L315), `getElementFromCoordinates→WebElement`
  (L1236), `findBySmartLocator→List<WebElement>` (L1227), `findWebElement(TargetElement)→WebElement`
  (L1400), `findElementByCssSelector→WebElement` (L1404/1408), `findShadowElementByCssSelector→WebElement`
  (L1383), `findElementByXPaths→WebElement` (L1375), plus all the interaction helpers that take a
  `WebElement` (`clickElement`, `insertInElement`, `insertDataInSelectElement`, `getValueInElement`,
  `scrollToElement`, `getOutPutElement`, `extractAttribute`, `isClickable`, `isElementVisible`).
- **`ElementLocator`** — every locate method returns/consumes `WebElement`/`WebDriver`
  (L63, 68, 74, 92, 102, 116, 193, 341, 380, 398, 410, 454, 482). `findBySmartLocator` builds a
  `Set<WebElement>` (dedup by handle) — Locators aren't hashable, so dedup must move to selector strings.
- **`ElementInteraction`** — all primitives take `WebElement` (L39, 55, 118, 204, 237, 261, 275, 310,
  319, 327, 337, 369, 437, 445). Focus-identity comparison (`safeActiveElement()` + `WebElement.equals`,
  L319-334) is doubly blocked: Locators have no DOM identity.
- **`ElementDtoMapper`** — reads `target.getElement()` (a cached `WebElement`) in `defineNameTitles`
  (L218/253) and `getRect()` (L623). Depends on the `TargetElement.element` handle field.
- **`ElementRecoveryService`** — **entire class returns handles**: `Recovery.element : WebElement`
  (L67/71/77), `findOrRecover(WebDriver,…)→Recovery` (L90), and `document.elementFromPoint` via
  `executeScript` expecting a `WebElement` back (L211-215/225-229). Self-healing recovery must be
  rewritten to return selectors/DTOs.
- **`TargetElementHelper`** — persists a live handle onto the model: `target.setElement(WebElement)`
  (L87-90, 206-280), reads it back in `defineNameTitles` (L401/433); `extractAttribute(WebElement,…)`
  (L552), `isClickable(WebElement,…)` (L556). **The `TargetElement.element` field is the core coupling.**
- **`ARWebDriver`** — `highlightElement/dehighlightElement/applyCssToElement/extractAttributes/
  runScript/elementExists` all take `WebElement` (L544-576).
- **`ARScannedElementScene`** — `findWebElement(target)` → `setElement(WebElement)` at L777 and L872.
- **`ARScannedElementPane`** — `getXPath(WebDriver,WebElement)` (L257), `getElementText(WebElement)`
  (L281), `findElementsWithoutIdOrName→Map<String,WebElement>` (L395), `getElementXPath` (L415),
  `findElementsWithXPath→Map<String,WebElement>` (L3147), `searchAllInputs→List<WebElement>` (L2852),
  `isClickable(WebElement)` (L3859), `immediateXPath→WebElement` (L7902), `findShadowElementByCssSelector`
  (L715), field `WebElement webElementFound` (L4003), `findBySmartLocator` result (L5249).

### 2.2 Stateful frame / window switching
Playwright is stateless — no "current frame", no window handles.

- `switchTo().frame()/defaultContent()`: `PerformActions` L341-343 (+ the whole save/restore driver
  dance L331-500 becomes dead), `ElementLocator` L201/234/414/431, `WindowAndFrameManager` L37/70/114,
  `IframeInputLocator` L54/94, `PageDiagnosticDumper.dumpIframes` L353/359, `ARScannedElementPane`
  L838/2380/2626/3741. → Replace with `page.frameLocator(...)` (per-locator) / `frame.content()`.
- `getWindowHandles()` + `switchTo().window()`: `ElementLocator` L202/415, `WindowAndFrameManager`
  L38/80, `ARScannedElementPane` `switchToLeftTab`/`switchToRightTab` (L2100-2165), L2467/2473/2900.
  → Structural rewrite to `context.pages()` + `page.bringToFront()`.

### 2.3 `getIframeElementsMap()` + entire `IframeInputLocator`
`WindowAndFrameManager.getIframeElementsMap()` (L92) builds a `Map<WebElement,List<WebElement>>` of
**live handles** by switching frames, handed to the singleton `IframeInputLocator` (whole class,
L20-271). No Playwright way to persist handles across frame contexts. Must be redesigned to scan
per-frame into DTOs.

### 2.4 Interactive PICK / hover-pick highlight (JS-injection over WebSocket)
- `PerformCloneLoad.dynamicPickOneCloneElementsDTO` (L101-128): `(JavascriptExecutor)driver
  .executeScript(hoverPick.min.js, 9 positional args)` — injects the pick plugin which opens a
  **WebSocket back to Java** and posts picked elements out-of-band (`return null` on success, L128).
  The WebSocket path is browser-side (driver-agnostic), so this is **PARTIAL, not impossible**: it can
  move to `page.evaluate(bundle, argObject)` — but the 9-positional-arg IIFE convention must be
  refactored to a single arg object, and no wrapper method exists yet.
- `highlightElement`/`dehighlightElement` (`BrowserJsUtils` L109, `PerformActions` L1379,
  `ARWebDriver` L544): tied to live hover-pick handles; re-express via `locator.evaluate` per element
  (loses the persistent-handle highlight model).

### 2.5 Selenium browser lifecycle & driver binaries (`ARWebDriver`)
No Playwright analogue — these are **deleted**, not ported, in Playwright-only mode:
- `getEdgeWebDriverVersion` (spawns `msedgedriver --version`, L125), `getEdgeBrowserVersion`
  (`RemoteWebDriver.getCapabilities().getBrowserVersion()`, L144) — Playwright manages the browser;
  there is no external driver binary or version handshake. **This is the very check whose error was
  being shown as the misleading "Invalid URL / WebDriver version" dialog.**
- `getDriverEdge/Chrome/FireFox` (L154/176/192), `buildOptionsEdge/Chrome` (`EdgeOptions`,
  `ChromeOptions`, `Proxy`, `LoggingPreferences`, `System.setProperty("webdriver.*")`, L425/487) →
  replaced by the single `BrowserType.launch()` inside `ARPlaywrightDriver`.
- `manage().window().maximize()` (L420) → launch arg / viewport option.
- Fields/state: `webDriverList`, `currentDriver`, `optionsEdge/Chrome/Firefox`, `initialize(List<WebDriver>)`,
  `addWebDriver` — become unused (currentDriver stays null).

### 2.6 Appium / mobile "Next" IME
`ElementInteraction.tryPressNext` (L275-303) — Android/iOS soft-keyboard "Next" handling. Playwright
is **web-only**; no soft-keyboard analogue. (The JS focus-next fallback part is migratable.)

### 2.7 Cross-origin iframe injection
`BrowserJsUtils.insertValueIFrameElement` (L43-107) — reaches into `iframe.contentDocument` +
`postMessage`. Only **same-origin** frames map cleanly to `frameLocator`; the cross-origin
`contentDocument` trick has no clean Locator equivalent (and is fragile in Selenium too).

---

## 3. PARTIAL — ports with semantic changes

- **Explicit waits** → auto-wait. `WebDriverWait`/`ExpectedConditions.visibilityOf/elementToBeClickable/
  presenceOfElementLocated` (`PerformActions` L692/716, `ElementLocator` L129-199, `ElementInteraction`
  L65/129/342, `IframeInputLocator` L204) collapse into a single `locator.click()/waitFor()`; the shared
  `Wait<WebDriver>` objects (`PerformActions` L82-83) disappear.
- **`executeAsyncScript` searchListAsync** (`PerformListElements` L416-426) → already superseded by
  `getPlaywrightDriver().scanElements(...)` (the alt branch at L396-401 is live). Legacy path retires.
- **`linkText`/`partialLinkText` locators** (`ElementLocator` L288-296) → `page.getByText(...)`
  (semantics differ; not an exact match).
- **Shadow DOM** (`ElementLocator` L398-408) → Playwright CSS locators pierce **open** shadow roots
  automatically; **closed** roots remain a gap.
- **`getPageSource`/`getCurrentUrl`/`getTitle`/window size** (`SupportCapture` L87-95, `ARScannedElementPane`
  L1650-1676) → `page.content()`/`currentUrl`/`title`/`viewportSize` (add wrappers).
- **`isBrowserClosed`** (`ARWebDriver` L589, `ARScannedElementPane` L491) → `page.isClosed()` /
  `ARPlaywrightDriver.isOpen()`.
- **Driver lifecycle `quit()`** scattered across scenes/panes (`ARMainScene` L258, `ARNewBotJobScene`
  L544, `ARViewBotJobScene` L130, `ARScannedElementScene` L556/571, `ARScannedElementPane` many) →
  route through `closeCurrentDriver()`; the `webDriverList` loops become no-ops in Playwright-only.

---

## 4. MIGRATABLE — clean mechanical ports

Mostly already implemented inside `PlaywrightActionExecutor`/`PlaywrightElementScanner`; the pattern
is: replace the `WebElement`/`JavascriptExecutor` call with a `Locator`/`page.evaluate` call.

- `executeScript(js[, args])` DOM manipulation → `page.evaluate(js[, arg])` — `CoordinateActions`
  (L99-372: scroll, set/clear value, click, marker overlay, sendInputJS), `PageDiagnosticDumper`
  (outerHTML/meta/shadow/overlay/rects surveys L322-405), `ARScannedElementPane` revert/inject helpers
  (`revertCloneInjections`, `revertPickInjections`, `revertHoverPickInjections`,
  `revertSearchTermsInjections`, `injectJumpTab`, `flashFoundElements` — all `WebDriver`-param only,
  logic ports directly).
- `element.clear()/sendKeys()/getText()/getAttribute()/getTagName()/isDisplayed()/isEnabled()/
  isSelected()` → `locator.fill()/press()/innerText()/getAttribute()/evaluate/isVisible()/isEnabled()/
  isChecked()` — `ElementInteraction`, `SupportCapture` (L201-405), `PerformActions` interaction rungs.
- `new Select(el).selectByVisibleText()` → `locator.selectOption(setLabel(...))` (`ElementInteraction` L379).
- `new Actions(driver).moveToElement()/sendKeys()` → `locator.hover()` / `page.keyboard().type()` /
  `page.mouse().click()` (`ElementInteraction` L437-451, `CoordinateActions` L115/175/181).
- `scrollIntoView` → `locator.scrollIntoViewIfNeeded()` (already used).
- `element.getRect()`/`getLocation()`/`getSize()` → `locator.boundingBox()` (`ElementDtoMapper` L623,
  `SupportCapture` L379/387).
- `manage().window().getSize()` → `page.viewportSize()` (`CoordinateActions` L42-130, `SupportCapture` L95).
- `navigate().refresh()/back()` → `page.reload()`/`page.goBack()` (`WindowAndFrameManager` L35/69).

Files with **zero real Selenium code** (labels/enums/commented only — nothing to do): `ARConfigurationPane`,
`ARNewHomeBankingPane`, `ARNewCommandPane`, and `ARMainPane` (its `executeScript` is the JavaFX
`WebEngine`, not Selenium). Files that only **pass a `WebDriver` reference** (already null-safe in
Playwright-only): `ARMainScene`, `ARNewBotJobScene`, `ARViewBotJobPane`, largely `ARViewBotJobScene`.

---

## 5. Already migrated / done

- `openDriver` branches to Playwright and short-circuits Selenium in Playwright-only mode (`ARWebDriver`
  L243-255).
- Page scan already routes to `getPlaywrightDriver().scanElements(...)` (`PerformListElements` L396-401).
- Action layer (`performWebActions` → `tryPlaywrightWebAction`) tries Playwright first
  (`PlaywrightActionExecutor` click/fill/text).
- TEST RUN runs the full engine in the single Playwright browser (`testRunBlockPlaywright`).
- All Selenium window/tab/frame derefs on the scanner-open and TEST-RUN paths are null-guarded so the
  app runs Playwright-only without NPEs.
- The misleading "Invalid URL / check your WebDriver version" dialog no longer fires for non-Selenium
  failures (`ARScannedElementScene.showModal` now logs the real cause).
- **Phase 1 (wrapper gaps)** — `screenshot`, `reload`, `viewportSize`, `content`, `evaluate` added to
  `ARPlaywrightDriver` (commit 656758c6).
- **Phase 2 (leaf ports, in progress)** — screenshot + DOM-rects + OCR pipeline now run via the single
  Playwright browser when there is no Selenium driver: `WebScreenshotCapture`, `PageDiagnosticDumper`
  (`dumpRects`/`dumpRectsFromElements`), `PageOcrDumper` (`runAndDump`), wired through
  `PerformListElements.processScanElements(ARWebDriver, …)`. This makes OCR-resolved `someText`/
  `definedName` available on scanned DTOs in Playwright-only mode — the input the self-healing
  re-resolution needs.

### Screenshot note
Screenshot capture is currently **disabled** (commented out) everywhere — there is **no live
`TakesScreenshot`/`getScreenshotAs` call**. `WebScreenshotCapture` still contains the Selenium
scroll-stitch implementation for OCR, but it is only reachable if OCR capture is re-enabled. When it
is, `page.screenshot({fullPage:true})` replaces the entire manual loop (after adding a `screenshot()`
wrapper — see §1a).

---

## 6. Recommended migration order (lowest risk first)

1. **Add the wrapper gaps** (§1a): `screenshot`, `reload`, `viewportSize`, `content`,
   `evaluate(script, Object[])`, `frames`, `pages`. Cheap, unblocks everything below.
2. **Mechanical §4 ports** in leaf helpers that only take a `WebDriver` (no `WebElement`):
   `CoordinateActions`, `PageDiagnosticDumper`, `WebScreenshotCapture`, `SupportCapture`, the
   `revert*Injections`/`flash`/`inject` helpers. Route them through `Page`/`ARPlaywrightDriver`.
3. **Retire `WebDriverWait`/`ExpectedConditions`** (§3) as each locate/interact path moves to Locators.
4. **The `WebElement` refactor** (§2.1) — the big one. Change the locate/interact contract from
   "return a `WebElement`" to "return a selector/`ElementDTO`", starting at `ElementLocator` /
   `PerformActions.searchElement`, then `TargetElement.element`, then `ElementRecoveryService`. This is
   what enables the self-healing re-resolution feature (re-scan + match by name/position/OCR).
5. **Frame/window rewrites** (§2.2/2.3) — `frameLocator` + `context.pages()`; delete `IframeInputLocator`.
6. **Delete dead Selenium lifecycle** (§2.5) once nothing calls it: driver builders, version detection,
   `webDriverList`, `currentDriver`.
7. **Accept the permanent gaps** (§2.6 Appium IME, §2.7 cross-origin `contentDocument`, closed shadow
   roots) — document them as unsupported in Playwright mode.

Until steps 4-6 land, the Selenium classes stay compiled but dormant (null-guarded) so the app runs
single-browser today while the migration proceeds incrementally.
