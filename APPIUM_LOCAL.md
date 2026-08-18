# 🔧 Troubleshooting Local Appium (Portable appium.exe)
1. The Problem

Your .cmd launcher sometimes starts global Appium located in:

C:\Users\<user>\AppData\Roaming\npm\node_modules\appium


This happens even when your intention is to use the local bundled appium.exe.

This causes errors such as:

Error [ERR_REQUIRE_ESM]: require() of ES Module uuid/...


because the global Appium installation is outdated and incompatible.

---

# 📘 Running Appium as a Local Portable Bundle (No Global Appium)

This guide explains how to ensure Appium is always launched from a **local project folder**, using your own packaged `appium.exe`, and never from a **global npm installation**.

This avoids issues like:

```
Error [ERR_REQUIRE_ESM]:
C:\Users\<user>\AppData\Roaming\npm\node_modules\appium\node_modules\uuid\...
```

which happen when the system accidentally launches **global Appium** instead of your bundled one.

---

## ❗ Why the Error Happens

If you see this path in an error:

```
C:\Users\<user>\AppData\Roaming\npm\node_modules\appium\
```

it means:

### 🚨 The `.cmd` script is launching **global Appium (`npm install -g appium`)**

instead of your **local portable Appium bundle**.

This happens because Windows searches for executables in this order:

1. **Full path**
2. **Current folder**
3. **PATH**
4. **Global npm folder** (e.g. `%APPDATA%\npm`) ← the problem
5. System folders

If your `.cmd` script simply calls:

```
appium.exe
```

Windows may resolve it as:

```
C:\Users\<user>\AppData\Roaming\npm\appium.cmd
```

instead of your packaged Appium.

---

## 🔥 Fix: Force `.cmd` to Always Use the Local appium.exe

Update your launcher script so it always uses the **full BASE_DIR path**.

### ❌ Wrong

```bat
appium.exe %*
```

This allows Windows to fall back to global Appium.

### ✅ Correct (forces local exe)

```bat
"%BASE_DIR%appium.exe" %*
```

Now it is **impossible** for Windows to pick global Appium.

---

## ✅ Final `appium-1.0.cmd` (Portable, Local, Safe)

```bat
@echo off

REM --------------------------------------------------------------
REM Base directory (folder where this script lives)
REM --------------------------------------------------------------
SET "BASE_DIR=%~dp0"

REM --------------------------------------------------------------
REM Android Build-Tools inside local Appium bundle
REM --------------------------------------------------------------
SET "ANDROID_BUILD_TOOLS=%BASE_DIR%build-tools\36.0.0"

REM --------------------------------------------------------------
REM Expose Build-Tools (apksigner.jar, zipalign.exe) to Appium
REM --------------------------------------------------------------
SET "PATH=%ANDROID_BUILD_TOOLS%;%PATH%"

REM --------------------------------------------------------------
REM Ensure this script runs ONLY the local bundled appium.exe
REM --------------------------------------------------------------
cd /d "%BASE_DIR%"

REM Forward all command line arguments to appium.exe
"%BASE_DIR%appium.exe" %*
```

---

## 🧪 Testing

Run:

```cmd
appium-1.0.cmd --port 8205 --allow-cors
```

If the server starts and displays:

```
Using Appium at: D:\Projects\ARWeb-Martini\ARWeb-Scanner\appium
```

you are successfully using the **local bundled Appium**.

---

## 🚫 Optional but Recommended: Remove Global Appium

To prevent accidental global usage:

```cmd
npm uninstall -g appium
```

Your local bundle is fully self-contained and does not require a global install.

---

## 📁 Folder Structure Example

```
appium/
  appium.exe
  appium-1.0.cmd
  build-tools/
     36.0.0/
       apksigner.bat
       zipalign.exe
  node_modules/
  package.json
  index.js
```

Everything needed is inside this folder.

---

## 🎉 Summary

* Your local `appium.exe` works correctly.
* The `.cmd` wrapper now forces use of **local**, not global Appium.
* No Node.js or npm installation is required on the target machine.
* No global Appium interference.

---
