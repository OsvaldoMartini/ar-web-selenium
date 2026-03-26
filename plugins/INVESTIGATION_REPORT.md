# Deep Investigation Report — JS Injection System Refactor

## Summary

The migration from hardcoded JS injection to classpath-loaded plugin system is **already 80% complete** in the codebase. This report documents the remaining gaps, a confirmed bug fix, and all output files.

---

## File Inventory

| File | Destination | What changed |
|------|-------------|-------------|
| `PerformPreLoad.java` | `src/main/java/com/allinweb/ch/facade/` | Fixed `jsExecutor` race condition (static → local). Added logging on first load. Made `loadScript()` package-private for reuse. |
| `pluginTest_index.js` | `src/main/resources/plugins/pluginTest/index.js` | Added `console.log("plugin test")` as first statement in the IIFE. |
| `pluginTest.min.js` | `src/main/resources/plugins/pluginTest/build/` | Rebuilt from corrected source via `npx esbuild index.js --bundle --minify`. |
| `scanner.min.js` | `src/main/resources/plugins/pageScanner/build/` | **NEW** — built from the 7 modular source files. This was missing entirely (gitignored, never committed, no Maven step wired). |

---

## Bug Fix: Static `jsExecutor` Race Condition

### Problem

Both the old `PerformPreLoad` and `ARScannedElementPane` declare:

```java
private static JavascriptExecutor jsExecutor;
```

Then assign it before every use:

```java
jsExecutor = (JavascriptExecutor) driver;
jsExecutor.executeScript(...);
```

If two threads call `dynamicLoadElementsDTO()` concurrently with different `WebDriver` instances (multi-tab scanning, parallel bot jobs), Thread A's driver can be overwritten by Thread B between the assignment and the `executeScript` call. This would inject the scanner into the wrong browser session.

### Fix

```java
// Local variable — no shared state, no race
JavascriptExecutor executor = (JavascriptExecutor) driver;
executor.executeScript(getJsScanner(), ...);
```

Applied in the output `PerformPreLoad.java`. The same pattern should be applied to the 12+ occurrences of `jsExecutor = (JavascriptExecutor) driver;` in `ARScannedElementPane.java`.

---

## Plugin Test Validation

### Button behavior (already implemented in `ARScannedElementPane.java`)

- **Green state** (`⬤ Plugin Test`): `pluginTest.min.js` found on classpath. Click injects the script.
- **Orange state** (`⚠ Plugin Test`): resource missing. Button disabled. Tooltip shows build command.
- **Positioned at**: grid cell `(1, 0)`, immediately right of the `Page Scanner` button.

### Script behavior (corrected in this delivery)

1. `console.log("plugin test")` — written to browser console
2. Green floating card in top-right corner with text "Plugin loaded ✓ (pluginTest)"
3. Auto-dismisses after 4 seconds, or click to close immediately
4. Previous overlay cleaned up before re-injection (idempotent)

### Failure handling

- If `pluginTest.min.js` is missing from classpath → button renders orange/disabled (Java-side check)
- If no WebDriver session is open → JavaFX Alert: "Open a browser session first"
- If executeScript throws → JavaFX Alert with the exception message

---

## Architecture Assessment

### What works well

1. **Double-checked locking on `jsScanner`** — correct volatile + synchronized pattern, lazy load avoids startup crash if artifact is missing.
2. **Classpath loading via `getResourceAsStream`** — works regardless of JAR packaging, Maven resource filtering, or IDE run configurations.
3. **Module decomposition** — the 7-file structure cleanly separates concerns. Each module has JSDoc, imports are explicit, and the `index.js` IIFE preserves the exact `window.*` API surface that Java callers depend on.
4. **esbuild as bundler** — produces a single IIFE from ES module imports, minifies to 12 KB (vs ~7.5 KB for the hand-minified monolith — the slight size increase comes from module wrapper overhead, negligible).

### Remaining risks

1. **No Maven build step wired** — the `frontend-maven-plugin` referenced in the migration report is not yet configured in `pom.xml`. Until it is, developers must manually run `npx esbuild` after editing any pageScanner source file. The `scanner.min.js` I've built and included will work immediately, but will go stale if sources are edited.

2. **`Thread.sleep(2000)` on FX thread** — `searchTermsBtn()` at line 2881 blocks the JavaFX Application Thread. This freezes the entire UI for 2 seconds on every scan. Should be moved to a background thread with a `Platform.runLater()` callback.

3. **Duplicate utility methods** — `loadScriptFromResource()` in `ARScannedElementPane` (line 451) and `loadScript()` in `PerformPreLoad` do the same thing. The corrected `PerformPreLoad.loadScript()` is now package-private so both classes can use it.

---

## Deployment Checklist

```
[ ] Copy PerformPreLoad.java → src/main/java/com/allinweb/ch/facade/
[ ] Copy pluginTest_index.js → src/main/resources/plugins/pluginTest/index.js
[ ] Copy pluginTest.min.js   → src/main/resources/plugins/pluginTest/build/
[ ] Copy scanner.min.js      → src/main/resources/plugins/pageScanner/build/
[ ] Run full build: mvn clean package
[ ] Test: open browser session → click "Plugin Test" → verify "plugin test" in console
[ ] Test: click "Page Scanner" → verify elements scanned → verify WebSocket chunks sent
[ ] Verify pluginTest button shows GREEN when scanner.min.js is on classpath
[ ] Remove pluginTest.min.js from classpath → verify button turns ORANGE
```
