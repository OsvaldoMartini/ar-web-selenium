# Plugin Encryption & Re-Key Flow

## Overview

Plugins are encrypted with AES-256-GCM. Three encryption modes are available:

| Mode | Script | plugins.key format | Who can decrypt |
|------|--------|--------------------|-----------------|
| **Owner** | `encrypt-plugins.js` | `PROTECTED:...` | Only owner with password + license |
| **Bank / Master Key** | `encrypt-bank.js` | Plain hex | Anyone with the key |
| **License Bound** | `encrypt-license.js` | `ACTIVATED:...` | Only the target machine + license |

---

## Files

| File | Purpose |
|------|---------|
| `encrypt-plugins.js` | Mode 1: Owner encryption (password + license) |
| `encrypt-bank.js` | Mode 2: Bank encryption (master key) |
| `encrypt-license.js` | Mode 3: License-bound encryption (machine + license) |
| `rekey-plugins.js` | Re-encrypt existing .enc files with new password |
| `setup-key.js` | Generate a PROTECTED key without encrypting |
| `plugins.key` | Wrapped encryption key (ship to production) |
| `.plugin-key-raw` | Raw hex key (owner backup, never ship) |

---

## Mode 1 — Owner Encryption (PROTECTED)

**Only the owner can decrypt.** Requires password + ARWeb.lic at runtime.

```bash
node encrypt-plugins.js --password --owner "osvaldo.martini@gmail.com"
```

Prompts for password (twice). Output:
- `plugins.key` → `PROTECTED:...` (password + license bound)
- `.zip` files with encrypted plugins

**Options:**
```bash
node encrypt-plugins.js --password "secret" --owner "osvaldo.martini@gmail.com"
node encrypt-plugins.js --password --license "D:\path\to\ARWeb.lic"
```

**Java runtime:** prompts user for password, derives key from password + license fingerprint.

---

## Mode 2 — Bank / Master Key

**For bank distribution.** Encrypt with a hex key. Bank needs the same key to decrypt.

```bash
# Generate a new master key
node encrypt-bank.js --client "UBS"

# Or use a specific key
node encrypt-bank.js --key <64-char-hex> --client "UBS"
```

Output:
- `plugins.key` → plain hex key
- `.zip` files with encrypted plugins

**Give the bank:**
- All `.zip` files
- `plugins.key`

**Java runtime:** uses the hex key directly, no password or license needed.

---

## Mode 3 — License Bound (ACTIVATED)

**Locked to a specific machine + ARWeb.lic.** No password needed at runtime.

```bash
node encrypt-license.js
node encrypt-license.js --license "D:\path\to\ARWeb.lic" --client "Credit Suisse"
```

Output:
- `plugins.key` → `ACTIVATED:...` (machine + license bound)
- `.zip` files with encrypted plugins

**Ship to production:**
- All `.zip` files
- `plugins.key`
- `ARWeb.lic` (must match the one used during encryption)

**Java runtime:** derives key from machine_id + license fingerprint automatically. Only works on the machine where encryption was run.

---

## Re-Key Existing Plugins

Use when plugins are already encrypted (.enc only, no .min.js sources) and you want to change the key/password.

**Requires:** `.plugin-key-raw` with the current raw hex key.

```bash
node rekey-plugins.js --owner "osvaldo.martini@gmail.com"
```

---

## Directory Structure

```
plugins/
  encrypt-plugins.js       # Mode 1: owner (password + license)
  encrypt-bank.js          # Mode 2: bank (master key)
  encrypt-license.js       # Mode 3: license bound (ACTIVATED)
  rekey-plugins.js         # re-key existing .enc files
  setup-key.js             # generate PROTECTED key only
  plugins.key              # wrapped key (ship to production)
  .plugin-key-raw          # raw hex key (owner backup, NEVER ship)
  manifest.json            # plugin metadata
  hoverPick/
    build/
      hoverPick.min.js     # source (from build)
      hoverPick.min.enc    # encrypted output
  hoverPick.zip            # zip containing the .enc
  ...
```

---

## Java Runtime Behavior

`EncryptedPluginLoader` reads `plugins.key` and detects the format:

| Format | Behavior |
|--------|----------|
| `PROTECTED:...` | Prompts for password, derives key from `password + license fingerprint` |
| `ACTIVATED:...` | Automatic, derives key from `machine_id + license fingerprint` |
| Plain hex (64 chars) | Uses the key directly |

---

## Production Checklist

1. Choose your encryption mode and run the appropriate script
2. Copy to production:
   - `plugins.key`
   - All `.zip` files
   - `ARWeb.lic` (for Mode 1 and Mode 3)
3. **DELETE** from production:
   - `.plugin-key-raw`
   - `.encrypt-meta.json`
   - Source `.min.js` files
   - Encryption scripts
