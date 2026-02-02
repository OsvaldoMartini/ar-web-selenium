# 🔧 TESER
Here are the practical ways to use Terser to make your injected JS much smaller, with options that work well for your “inject into browser” scenario.

---

## 1) Install + run from CLI (most common)

### Install

```bash
npm i -g terser
# or per project:
npm i -D terser
```

### Minify one file → one file

```bash
terser input.js -o output.min.js -c -m
```

* `-c` = compress
* `-m` = mangle (shorten variable names)

---

## 2) Recommended flags for your case (browser-injected tool)

Your script has lots of functions and variables. The best “safe default”:

```bash
terser input.js -o output.min.js -c -m --ecma 2020
```

If you want stronger compression:

```bash
terser input.js -o output.min.js \
  -c passes=2,drop_console=true \
  -m \
  --ecma 2020
```

### Notes

* `drop_console=true` removes `console.log(...)` and will shrink a lot.
* `passes=2` often gives better compression with minimal risk.

---

## 3) Keep some global names (IMPORTANT for `window.scannerToolRun`)

If you mangle without care, Terser can rename stuff you still need.

**You must keep your global entrypoints stable**, like:

* `scannerToolRun`
* `__scannerToolCleanup`
* `cleanupWebSocket`
* `revertSearchInjections`

Use:

```bash
terser input.js -o output.min.js \
  -c \
  -m reserved=['scannerToolRun','__scannerToolCleanup','cleanupWebSocket','revertSearchInjections'] \
  --ecma 2020
```

This prevents Terser from renaming those identifiers.

---

## 4) Minify and output as a single line (good for Java string injection)

Terser already outputs compact code, but to force a “single-line” style:

```bash
terser input.js -o output.min.js -c -m --format wrap_iife=false,semicolons=true

terser script-search-in-use.js -o script-search-in-use.min.js -c -m --format wrap_iife=false,semicolons=true

terser script-search-in-use-list.js -o script-search-in-use-list.min.js -c -m --format wrap_iife=false,semicolons=true

terser script-hover-pick-in-use.js -o script-hover-pick-in-use.min.js -c -m --format wrap_iife=false,semicolons=true
```

(Usually not needed; output is already minimal.)

---

## 5) Using Terser from Node (programmatically)

If your Java tool generates JS dynamically and you want minification in a build step:

```js
import { minify } from "terser";
import fs from "fs";

const code = fs.readFileSync("input.js", "utf8");

const result = await minify(code, {
  compress: { passes: 2, drop_console: true },
  mangle: { reserved: ["scannerToolRun", "__scannerToolCleanup", "cleanupWebSocket", "revertSearchInjections"] },
  ecma: 2020,
});

fs.writeFileSync("output.min.js", result.code);
```

Run:

```bash
node minify.js
```

---

## 6) Best workflow for your injection setup

Since CSP may block loading from `http://127.0.0.1/...`:

### ✅ Best pattern:

1. **Keep a readable source** `scannerTool.js`
2. Use Terser to create `scannerTool.min.js`
3. Inject **minified script** (or serve it locally if CSP allows)

---

## 7) Quick sanity checks after minifying

After you minify, verify in DevTools console:

```js
typeof window.scannerToolRun
typeof window.__scannerToolCleanup
```

Both should be `"function"` after you run your setup.

---

## TL;DR command for you

If you want one command that’s “best” and safe:

```bash
terser scannerTool.js -o scannerTool.min.js \
  -c passes=2,drop_console=true \
  -m reserved=['scannerToolRun','__scannerToolCleanup','cleanupWebSocket','revertSearchInjections'] \
  --ecma 2020
```

---
