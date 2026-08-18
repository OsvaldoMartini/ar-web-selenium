# License Validation Sync — AR Web Mobile

**Date:** 2026-06-22
**Source project:** `D:\Projects\AllinWeb\ar-web-selenium`
**Target project:** `D:\Projects\AllinWeb\ar-web-mobile`

---

## Background

AR Web Scanner (ar-web-selenium) has a complete, up-to-date license management system.
AR Web Mobile (ar-web-mobile) has the same license package but it is **outdated** — the `.lic`
file format diverged and several methods/classes are missing entirely.

The shared `.lic` format is:

```
pcName | domainName | userName | expiryDate | orgKey | email | version
```
(7 pipe-separated parts, AES-128-ECB encrypted, key `"0123456789abcdef"`)

ar-web-mobile currently expects **4 parts** — this is the breaking mismatch.

---

## Files already in sync — no action required

| File | Status |
|---|---|
| `license/LicenceVal.java` | Identical to ar-web-selenium |
| `license/SystemDetails.java` | Identical to ar-web-selenium |
| `ARControlMobile.java` → `licenseControl()` | Wired correctly at startup |

---

## Priority 1 — CRITICAL (app will reject valid licenses without these)

### 1. `src/main/java/com/allinweb/ch/license/LicenseManager.java`

The mobile version expects 4 parts; ar-web-selenium now produces 7.

**Changes required:**

#### `validateLicense()` (line ~150)
```java
// BEFORE (broken):
if (parts.length != 4) return LicenceVal.MISSING;

// AFTER (correct):
if (parts.length < 7) return LicenceVal.MISSING;
// then extract:
String orgKey   = parts[4];   // 64-char hex
String email    = parts[5];
String version  = parts[6];
// store via ARPropertyManager / ARPropertyEnum
```

#### `genereteResponseFile()` (lines ~179-184)
- Array index offsets are wrong — copy implementation from ar-web-selenium.

#### `generateRequestFile()`
- Missing parameters: `organization`, `owner`, `email`.
- Must produce the 7-part format to match what `validateLicense()` now reads.
- Add constant: `APP_VERSION = "4.7"` (or the correct mobile version string).

#### Missing methods to add (trivial copies from ar-web-selenium)
- `isEmail(String input)` — RFC 5322 simplified validation
- `sendRequestOnline(String org, String owner, String email)` — returns `"DISABLED"`
- `pingApi()` — returns `false`
- `getDecryptedResponseFile(String requestFile)` — decrypt + validate `.request` file

---

### 2. `src/main/java/com/allinweb/ch/util/ARPropertyEnum.java`

Five enum constants are missing that `validateLicense()` needs to store the extracted fields:

```java
LICENSE_ORG_KEY("license.orgKey"),    // parts[4] — 64-char hex AES key
LICENSE_ORG_NAME("license.orgName"),
LICENSE_OWNER("license.owner"),
LICENSE_EMAIL("license.email"),        // parts[5]
LICENSE_VERSION("license.version"),    // parts[6]
```

Add these after the existing `EXPIRATION` entry.

---

## Priority 2 — Should create (completeness / parity)

### 3. `src/main/java/com/allinweb/ch/util/LicenseFingerprint.java` *(new file)*

Does not exist in ar-web-mobile. Direct 46-line copy from ar-web-selenium.

**Purpose:**
- Computes `sha256:<hex>` of the `.lic` file bytes (one-way hash, not the decrypted content)
- Used as bearer credential for support uploads to the MultiPlugins portal
- `compute(String licPath)` → `String` or `null` if file missing

---

## Priority 3 — Optional (license UI, only if mobile needs standalone activation)

### 4. `src/main/java/com/allinweb/ch/license/LicenseActivationApp.java` *(new file)*

298-line JavaFX Application. Provides the user-facing license request / response-import UI.
Copy from ar-web-selenium. Only needed if mobile users manage their own license independently
of the Scanner.

### 5. `src/main/java/com/allinweb/ch/license/LicenceResponseManagerApp.java` *(new file)*

184-line JavaFX Application. Admin tool — decrypts `.request` files, generates `.response`
files with a configurable expiry day count, writes output to Desktop.
Copy from ar-web-selenium.

---

## Summary table

| File | Action | Priority |
|---|---|---|
| `license/LicenseManager.java` | Update — wrong format + missing methods | **CRITICAL** |
| `util/ARPropertyEnum.java` | Update — add 5 license enum values | **CRITICAL** |
| `util/LicenseFingerprint.java` | Create — SHA-256 fingerprint utility | Should do |
| `license/LicenseActivationApp.java` | Create — user license UI | Optional |
| `license/LicenceResponseManagerApp.java` | Create — admin response generator | Optional |
| `license/LicenceVal.java` | No change | Done |
| `license/SystemDetails.java` | No change | Done |
| `ARControlMobile.java` | No change | Done |

---

## Validation checklist (after applying changes)

- [ ] 7-part `.lic` file decrypts and validates correctly (pcName, domain, user, expiry, orgKey, email, version)
- [ ] `LICENSE_ORG_KEY`, `LICENSE_EMAIL`, `LICENSE_VERSION` populated from parts[4-6]
- [ ] `LicenseFingerprint.compute()` returns `sha256:<hex>` matching ar-web-selenium output for same file
- [ ] `isEmail()` validates RFC 5322 addresses
- [ ] `generateRequestFile()` produces format compatible with `validateLicense()`
- [ ] Admin response file generation uses correct array offsets
- [ ] `sendRequestOnline()` returns `"DISABLED"` — no network calls
- [ ] Startup license check does not block on missing API
