# AR Web Factory — "Match rules" field (Avaloq & custom attribute search)

---

## 🔨 Build the plugin

After editing any source under `plugins/searchListAsync/` (`index.js`,
`scanner/*.js`, `classifier/*.js`) re-minify so the dev bundle picks it up:

```
cd D:/Projects/ARWeb-Martini/ARWeb/plugins/searchListAsync
npx esbuild index.js --bundle --minify --outfile=build/searchListAsync.min.js

  build\searchListAsync.min.js  18.4kb
  Done in 7ms
```

Dev mode (Tier 2 of `EncryptedPluginLoader`) loads the `.min.js` directly —
no encryption needed while you're iterating.

---

## 🔐 Encrypt for production

Production runs Tier 3: `EncryptedPluginLoader` reads
`{plugins}/{pluginId}.zip`, extracts `{pluginId}.min.enc` into memory, and
decrypts with AES-256-GCM using the **org key at position `[4]` in
`ARWeb.lic`** (managed by `PluginKeyManager`).

### File format expected by the loader
```
[IV (12 bytes)] [Auth Tag (16 bytes)] [Ciphertext]
```

### One-time encrypt helper (Node.js)

Save as `plugins/encryptPlugin.js`:

```js
#!/usr/bin/env node
// Usage: node encryptPlugin.js <input.min.js> <output.min.enc> <orgKeyHex>
//   <orgKeyHex> = 64 hex chars (AES-256 key from position [4] in ARWeb.lic)
const crypto = require('crypto');
const fs = require('fs');

const [, , inFile, outFile, keyHex] = process.argv;
if (!inFile || !outFile || !keyHex) {
  console.error('Usage: node encryptPlugin.js <input.min.js> <output.min.enc> <orgKeyHex>');
  process.exit(1);
}
const key = Buffer.from(keyHex, 'hex');
if (key.length !== 32) { console.error('orgKeyHex must be 64 hex chars (AES-256, 32 bytes)'); process.exit(2); }

const plaintext = fs.readFileSync(inFile);
const iv = crypto.randomBytes(12);
const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
const ciphertext = Buffer.concat([cipher.update(plaintext), cipher.final()]);
const tag = cipher.getAuthTag(); // 16 bytes

// Loader expects [IV][Tag][Ciphertext] — Node's createCipheriv emits [Ciphertext]+tag separately,
// so we splice in the order the Java side reads.
fs.writeFileSync(outFile, Buffer.concat([iv, tag, ciphertext]));
console.log(`Wrote ${outFile}  ${iv.length + tag.length + ciphertext.length} bytes  (IV=12, Tag=16, Cipher=${ciphertext.length})`);
```

### Run it
```
cd D:/Projects/ARWeb-Martini/ARWeb/plugins/searchListAsync
set ORG_KEY=<paste your 64-char hex key from ARWeb.lic>
node ../encryptPlugin.js build/searchListAsync.min.js build/searchListAsync.min.enc %ORG_KEY%
```
(or `$env:ORG_KEY="…"` in PowerShell, `export ORG_KEY=…` in bash, then `$ORG_KEY`.)

### Package for shipping
```
cd D:/Projects/ARWeb-Martini/ARWeb/plugins
powershell -Command "Compress-Archive -Path searchListAsync -DestinationPath searchListAsync.zip -Force"
```
Loader Tier 3 will read `searchListAsync.zip!/searchListAsync/build/searchListAsync.min.enc` and decrypt in memory.

### Fast-check your encryption
Drop the newly-built `.min.enc` as a loose file at
`{plugins}/searchListAsync/searchListAsync.min.enc` and restart the app.
The dev-mode modal will say **ORG KEY VALID ✅** or **ORG KEY INVALID ❌**
so you know immediately whether the key matched.

---

## 🔁 New classifier rules (ARIA / CDK / Material / Avaloq)

Added in `classifier/tagClassifier.js`. Triggered during `getElementIdentity`
(the source was rewired to pass the live `element`, not just tag + xpath).
Order of the pass — first match wins:

| Pass | What it checks | Mapped to |
| ---- | -------------- | --------- |
| 1. ARIA role        | `role="button"|"treeitem"|"menuitem"` | `button` |
|                     | `role="checkbox"|"radio"`             | `input`  |
|                     | `role="option"|"tab"`                 | `select` |
| 2. CDK attributes   | `cdktreemenuitem` / `cdk-menu-item` / `cdk-menu-trigger` | `button` |
| 3. Material tags    | `mat-tree-node` / `mat-menu-item` / `mat-option` / `mat-expansion-panel-header` | `button` |
|                     | `mat-radio-button` / `mat-checkbox` / `mat-slide-toggle` / `mat-button-toggle` | `input` |
| 4. Avaloq tags      | `avq-wp-tree-leaf` / `avq-wp-actions-menu` / `avq-icon-button` / `avq-checkbox-button` / `avq-submit-button` | `button` |
|                     | `avq-checkbox` / `avq-radio-button` / `avq-slide-toggle` / `avq-input` / `avq-textarea` | `input` |
|                     | `avq-select` / `avq-option` / `avq-dropdown` | `select` |
|                     | **`avq-text-button` WITH descendant `<a>`** | `a` |
|                     | **`avq-text-button` WITHOUT `<a>`** | `label` (Java treats as **output**) |
|                     | `avq-text-bold` / `avq-text-italic` / `avq-text-small` / `avq-text-subtle` / `avq-text-emphasis` / `avq-text` / `avq-label` | `label` |
| —                   | `<mark>` | `label` |
| 5. Legacy XPath     | unchanged | — |

### Why it matters

The final sort in `index.js` keeps only `['input','textarea','button','a','select','label','span','div']`.
Before this change, `<avq-text-button>` / `<avq-wp-tree-leaf>` / `<mat-tree-node>` all hit the sort as
their native tag and got **silently dropped** — even when the Match rule found them. The classifier
now maps them onto one of the 8 accepted tag names so they reach the Java side.

### Signature change
```js
// Before
classifyTag(tagName, xpath)
// After
classifyTag(tagName, xpath, element)   // element is optional; ARIA / CDK /
                                       // avq-text-button anchor-check passes
                                       // skip when element is absent
```
One call site updated: `scanner/elementIdentity.js` (line 158).

### Verification
After `npx esbuild …` rebuild, grep on the output confirms the new strings:
```
avq-text-bold
avq-text-button
avq-wp-tree
cdktreemenuitem
mat-tree-node
treeitem
```
All present in `build/searchListAsync.min.js`.

---

## 📋 Match-rules field overview

The pane at `ARScannedElementPane.java` has two scanner inputs:

| Field          | Purpose                                        | Script arg     |
| -------------- | ---------------------------------------------- | -------------- |
| `Search by :`  | existing — CSS tag names / legacy "with X"     | `arguments[0]` |
| `Match rules :`| **new** — prefix / suffix / attribute matching | `arguments[8]` |

Both fields are independent. Running them together unions the matches; dedup
happens downstream so overlap is free.

---

## Rule grammar (Match rules field)

Comma-separated list of rules. Each rule is `<kind>:<value>`. Matching is
**case-insensitive** (tag names and attribute names).

| Rule                       | Matches                                             |
| -------------------------- | --------------------------------------------------- |
| `tagPrefix:<p>`            | element whose tag name **starts with** `<p>`        |
| `tagSuffix:<s>`            | element whose tag name **ends with** `<s>`          |
| `attr:<name>`              | element that has attribute `<name>` (any value)     |
| `attr:<name>=<value>`      | element where attribute `<name>` equals `<value>`   |
| `attrPrefix:<p>`           | element that has **any attribute** whose name starts with `<p>` |

Anything else (`foo:bar`, typos, missing colon) is silently ignored — that
specific rule contributes zero matches, the rest of the scan continues.

---

## Examples — Avaloq

### Find every `avq-*` custom element
```
tagPrefix:avq
```
Matches every Avaloq web component — `<avq-instrument-table>`,
`<avq-wp-tree-leaf>`, `<avq-trades-table>`, `<avq-wp-actions-menu>`,
`<avq-text-bold>`, `<avq-text-button>`, `<avq-icon-button>`, etc.

### The `avq-text-*` family (variable behaviour)

These tags look visually similar but behave differently depending on what's
inside them. The scanner captures all of them via `tagPrefix:avq` and the
**Java side** classifies each one based on its actual structure:

| Avaloq tag              | Typical content                       | Java classification  |
| ----------------------- | ------------------------------------- | -------------------- |
| `<avq-text-bold>`       | plain text only                       | **label** (display-only) |
| `<avq-text-button>`     | wraps an `<a>` / anchor / link        | **a** (clickable)        |
| `<avq-text-button>`     | no anchor inside                      | **label** → **output** (for Excel export) |
| `<avq-icon-button>`     | icon + click handler                  | **button**           |
| `<avq-wp-tree-leaf>`    | clickable tree node                   | **button**           |
| `<avq-wp-actions-menu>` | menu trigger                          | **button**           |

So one rule (`tagPrefix:avq`) gives you the union — the reclassification
pass in `pushElement` / `domWalker.js` and the final mapping inside
`ARScannedElementPane` decide whether each hit becomes a button, a link,
an input, a label, or an output field for Excel export.

**Rule of thumb:** if an `avq-text-button` has no anchor child, Java tags
it as a **label**, which in the bot-job editor is usually saved as an
**output** (read for Excel) rather than a click target.

### Narrow to a specific Avaloq family
```
tagPrefix:avq-text
```
Catches just the `avq-text-*` variations (bold / button / italic / …) —
useful when you only want the textual widgets, not the tables or menus.

```
tagPrefix:avq-wp
```
Catches the workspace-panel family (`avq-wp-tree-leaf`,
`avq-wp-actions-menu`, `avq-wp-*`).

### Find any element Avaloq tagged with `test-id` ⭐ high-yield rule
```
attr:test-id
```
Avaloq sprays `test-id="…"` on almost every interactive element —
checkboxes, buttons, menu items, cells. This single rule typically
**multiplies the visible results** because it picks up elements the
default tag-name pass (`input, button, a, select, label`) would filter
out. Combine with `tagPrefix:avq` for full coverage:

```
tagPrefix:avq, attr:test-id
```

### Find a specific Avaloq test-id
```
attr:test-id=positions-grid
```

### Match custom React-style attributes (any `data-*`)
```
attrPrefix:data-
```

### Combine rules (comma-separated)
```
tagPrefix:avq, attr:test-id, attr:data-test-id, attr:role=menuitem
```
Scanner walks the DOM once and adds every element matching *any* rule.
Recommended starting combo for an Avaloq page.

---

## Examples — generic (Material / ARIA / custom widgets)

| Goal                                    | Rule                              |
| --------------------------------------- | --------------------------------- |
| Every `<mat-*>` element                 | `tagPrefix:mat-`                  |
| Every element with a `data-testid`      | `attr:data-testid`                |
| Every element with `role="button"`      | `attr:role=button`                |
| Every element carrying any `data-*`     | `attrPrefix:data-`                |
| Every element carrying any `aria-*`     | `attrPrefix:aria-`                |
| Every `<*-button>` custom tag           | `tagSuffix:-button`               |
| Every `<*-input>` custom tag            | `tagSuffix:-input`                |

---

## Using both fields together

| Search by          | Match rules                              | Result                                     |
| ------------------ | ---------------------------------------- | ------------------------------------------ |
| `input, button`    | *(empty)*                                | legacy behaviour — same as before the change |
| *(empty)*          | `tagPrefix:avq`                          | only Avaloq custom tags                    |
| *(empty)*          | `attr:test-id`                           | ⭐ every element Avaloq tagged with `test-id` — usually the biggest jump in result count |
| `input, button, a` | `tagPrefix:avq, attr:test-id`            | inputs + buttons + anchors + every Avaloq element + every element with `test-id` |
| *(empty)*          | `tagPrefix:avq-text, attr:test-id`       | all `avq-text-*` widgets plus every `test-id`-tagged element |
| *(empty)*          | `attr:role=button, attr:role=menuitem`   | all role-based button-like elements |

### Why `attr:test-id` is the biggest lever

The default `Search by :` pass keeps only elements in this list:
`input, textarea, button, a, select, label, span, div`. Avaloq's custom
widgets (`avq-checkbox`, `avq-text-button`, `avq-icon-button`, etc.)
don't match any of those tag names on their own, so they fall off the
classifier. But they almost all carry `test-id="…"`, so a single
`attr:test-id` rule recovers them wholesale and lets the Java-side
classifier decide each one's type (button / input / label-output).

---

## How it works under the hood

1. Java → JS arg wiring (9 positional args via `executeAsyncScript`):
   - `[0]` searchTerms (from "Search by :")
   - `[1]` hiddenFields
   - `[2]` port  `[3]` sessionId  `[4]` destination
   - `[5]` operationId  `[6]` homeBankingId  `[7]` botJobId
   - **`[8]` extendedRules** (from "Match rules :" — new)
2. `searchListAsync/index.js` stores `[8]` on `window.__slAsync_extendedRules`.
3. `searchListAsync/scanner/domWalker.js → collectElements` runs the legacy
   `searchTerms` branch first, then iterates each rule and pushes matches
   into the same `collectionFound` array.
4. The existing dedup / classifier / dedup-by-coordinates pipeline runs
   unchanged — new rule matches ride the same rails as tag-name matches.

Native CSS selector (`querySelectorAll('[attrName]')` / `[attrName="value"]`)
is used where possible for speed; `tagPrefix` / `tagSuffix` / `attrPrefix`
fall back to a single `getElementsByTagName('*')` iteration.

---

## Field placement

`ARScannedElementPane.java` lays both fields out in the top grid (`gridPaneTop`):

```
[Scanner] [Plugin Update] [Update Plugins] [Search by :] [<field>] [Search] [On/Off] [←] [→]
                                           [Match rules :] [<field>]
```

The Match rules field spans the same grid columns as the Search by field,
one row below, so nothing in the first row shifts.
