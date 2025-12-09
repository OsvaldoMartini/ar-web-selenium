# Bundling Appium as a Single `appium.exe` (Windows, Node 18)

This guide explains how to package **Appium 2** and the **UiAutomator2 driver** into a **single executable** (`appium.exe`) using [`pkg`](https://github.com/vercel/pkg).

The result:

- No global Appium install required  
- No Node.js required on the target machine  
- Just run: `appium.exe --port 4723`

---

## 1. Prerequisites

- Windows
- Node.js **18.x** (recommended)
- `npm` available in `PATH`

Check versions:

```bat
node -v
npm -v
````

---

## 2. Create a Clean Appium Bundle Project

> Do this in a **new folder**, not inside any existing/broken Appium setup.

```bat
cd D:\Projects\ARWeb-Martini\ARWeb-Scanner

mkdir appium-bundle
cd appium-bundle

npm init -y
```

Edit `package.json` so the name is **not** `"appium"` and set basic scripts:

```json
{
  "name": "arweb-appium-bundle",
  "version": "1.0.0",
  "main": "index.js",
  "bin": "index.js",
  "scripts": {
    "start": "node index.js",
    "build:exe": "pkg . --targets node18-win-x64 --output appium.exe"
  }
}
```

> The `"bin"` property tells `pkg` which file is the executable entry.

---

## 3. Install Appium and UiAutomator2 Driver Locally

Install Appium 2 and the Android UiAutomator2 driver as **local dependencies**:

```bat
npm install --save appium@2.5.0
npm install --save appium-uiautomator2-driver@2.45.1 --legacy-peer-deps
```

Register the driver with Appium:

```bat
npx appium driver install uiautomator2
```

Verify:

```bat
npx appium driver list
```

You should see `uiautomator2` in the list.

---

## 4. Create the Appium Entry Script (`index.js`)

Create `index.js` in `appium-bundle` with this content:

```js
#!/usr/bin/env node

const { asyncify } = require('asyncbox');
const appium = require('appium');   // Use the module, not a relative ./build path

if (require.main === module) {
  // Start Appium with the normal CLI behavior (reads process.argv)
  asyncify(appium.main);
}

module.exports = appium;
```

Test it:

```bat
node index.js
```

You should see Appium start and log something like:

```text
[Appium] Appium REST http interface listener started on http://0.0.0.0:4723
```

If it **does not start**, fix any errors here before continuing.

---

## 5. Add `pkg` and Asset Configuration

Install `pkg` as a dev dependency:

```bat
npm install --save-dev pkg
```

Extend `package.json` with a `pkg` section to bundle Appium and driver files:

```json
{
  "name": "arweb-appium-bundle",
  "version": "1.0.0",
  "main": "index.js",
  "bin": "index.js",
  "scripts": {
    "start": "node index.js",
    "build:exe": "pkg . --targets node18-win-x64 --output appium.exe"
  },
  "pkg": {
    "assets": [
      "node_modules/appium/**",
      "node_modules/appium-uiautomator2-driver/**",
      ".appium/**"
    ]
  }
}
```

Notes:

* `assets` helps with dynamic `require()` calls and driver loading.
* If your Appium driver config lives in a `.appium` folder (e.g. in your user profile), you can copy it into this project so it gets bundled.

---

## 6. Build `appium.exe`

From inside `appium-bundle`:

```bat
npx pkg . --targets node18-win-x64 --output appium.exe
```

If you prefer to be explicit about the entry file, you can instead run:

```bat
npx pkg index.js --targets node18-win-x64 --output appium.exe
```

Either command should produce:

```text
appium-bundle/
  appium.exe
  index.js
  package.json
  node_modules/ ...
```

The `node_modules` folder is no longer needed on the **target** machine, only during the build.

> You may see:
>
> ```text
> npm warn Unknown global config "msvs_version".
> ```
>
> This is just an npm warning and can be ignored for this build.

---

## 7. Run the Single Executable

Copy `appium.exe` (and optionally `.appium`/config) to the machine where you want to run tests.

From the folder containing `appium.exe`:

```bat
appium.exe
```

Or with options:

```bat
appium.exe --port 4723 --log-level info
```

Your tests can then connect to:

```text
http://127.0.0.1:4723
```

with `automationName: "UiAutomator2"` in the desired capabilities.

---

## 8. Using a Custom `nodex.exe` Instead of `pkg` (Alternative)

If you already have a custom Node runtime launcher (e.g. `nodex.exe`) and **don’t** need a single file:

1. Keep the same project structure and `index.js`.
2. Place `nodex.exe`, `index.js`, `package.json`, and `node_modules` in the same folder.
3. Run:

   ```bat
   nodex.exe index.js
   ```

In this setup you **don’t** use `pkg` and you still rely on `node_modules` on the target machine.

---

## 9. Maintenance / Updates

* When you upgrade Appium or drivers, rebuild:

  ```bat
  npm update
  npm run build:exe
  ```

* If you install Appium plugins (`appium plugin install ...`), make sure they are installed **before** building and their folders are included under `"pkg.assets"`.

---

## 10. Troubleshooting

* **`ERR_REQUIRE_ESM` involving `uuid`**
  Make sure you’re using Appium 2.x as shown, and avoid adding your own top-level `uuid` dependency that might be ESM-only.

* **`Error! Property 'bin' does not exist in package.json` from `pkg`**
  Ensure `package.json` contains `"bin": "index.js"`, or call `pkg` with the entry file directly:

  ```bat
  npx pkg index.js --targets node18-win-x64 --output appium.exe
  ```

* **Appium doesn’t start from `appium.exe`**
  First verify `node index.js` works. If Node can’t run it, the packaged exe won’t either.

---

Happy testing 🚀

