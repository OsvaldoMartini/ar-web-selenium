# Plugin Encryption & Re-Key Flow

## Overview

Plugins are encrypted with AES-256-GCM. The encryption key is stored in `plugins.key` in one of three formats:

| Format | Description | Auth |
|--------|-------------|------|
| `PROTECTED:...` | Password + license bound | Prompts for password at runtime |
| `ACTIVATED:...` | Machine + license bound | Automatic (no password needed) |
| Plain hex | Raw key (dev only) | No auth |

---

## Files

| File | Purpose |
|------|---------|
| `plugins.key` | Wrapped encryption key (ship to production) |
| `.plugin-key-raw` | Raw hex key (keep secret, delete from production) |
| `encrypt-plugins.js` | Encrypt `.min.js` sources + optional password protection |
| `setup-key.js` | Generate a new PROTECTED key from scratch |
| `rekey-plugins.js` | Decrypt existing `.enc` files and re-encrypt with new password |

---

## Encrypt Plugins with Password (recommended)

Use this when you have the `.min.js` source files in `{plugin}/build/`.

```bash
cd D:\Projects\AllinWeb\ar-web-selenium\plugins

node encrypt-plugins.js --password --owner "osvaldo.martini@gmail.com"
```

You will be prompted to type a password and confirm it (press Enter after each).

The script will:

1. Use the existing raw key from `.plugin-key-raw` (or generate a new one)
2. Encrypt all `{plugin}/build/{name}.min.js` -> `{name}.min.enc`
3. Create a `.zip` for each plugin
4. Wrap the key as `PROTECTED:` (password + license bound)
5. Save `plugins.key`

**Options:**

```bash
# Inline password (no prompt)
node encrypt-plugins.js --password "mysecret" --owner "you@email.com"

# Custom license path
node encrypt-plugins.js --password --license "D:\path\to\ARWeb.lic"

# Plain hex key, no password (dev only)
node encrypt-plugins.js

# Reuse a specific key
node encrypt-plugins.js --key <64-char-hex> --password
```

---

## Re-Key Existing Plugins

Use this when plugins are already encrypted (only `.enc` files, no `.min.js` sources)
and you want to change the key or add password protection.

**Prerequisites:** `.plugin-key-raw` must contain the current raw hex key.

```bash
cd D:\Projects\ARWeb-Martini\ARWeb\plugins

node rekey-plugins.js --owner "osvaldo.martini@gmail.com"
```

The script will:

1. Decrypt all `.enc` files with the old key from `.plugin-key-raw`
2. Generate a new AES-256 key
3. Re-encrypt all plugins with the new key
4. Wrap the key as `PROTECTED:` (password + license bound)
5. Save `plugins.key` and re-zip each plugin

---

## Setup Key Only (no encryption)

Use this to generate a new password-protected key without encrypting plugins.
Useful when you want to run `encrypt-plugins.js --key <hex>` separately.

```bash
node setup-key.js --license "D:\Projects\ARWeb-Martini\ARWeb-Scanner\ARWeb.lic"
```

---

## Java Runtime Behavior

When the Java app starts, `EncryptedPluginLoader` reads `plugins.key`:

- **PROTECTED:** prompts the user for the password, derives the unwrap key from `password + license fingerprint`, decrypts the plugin key
- **ACTIVATED:** derives the unwrap key from `machine_id + license fingerprint` automatically
- **Plain hex:** uses the key directly (dev only)

The unwrapped key is then used to decrypt each `.min.enc` file at runtime.

---

## Directory Structure

```
plugins/
  encrypt-plugins.js       # main encryption script
  rekey-plugins.js         # re-key existing .enc files
  setup-key.js             # generate PROTECTED key only
  plugins.key              # wrapped key (ship to production)
  .plugin-key-raw          # raw hex key (DO NOT ship)
  manifest.json            # plugin metadata
  hoverPick/
    build/
      hoverPick.min.js     # source (from build)
      hoverPick.min.enc    # encrypted output
  hoverPick.zip            # zip containing the .enc
  pageScanner/
    build/
      scanner.min.js
      scanner.min.enc
  pageScanner.zip
  ...
```

---

## Production Checklist

1. Run `encrypt-plugins.js --password --owner "you@email.com"`
2. Copy to production plugins folder:
   - `plugins.key`
   - All `.zip` files
3. **Delete** from production:
   - `.plugin-key-raw`
   - `.encrypt-meta.json` / `.rekey-meta.json`
   - Source `.min.js` files
4. The end user only needs the password to unlock plugins
