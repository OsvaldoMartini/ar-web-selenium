# License, About, and Activation React Migration Roadmap

Date: 2026-07-11

## Objective

Replace the JavaFX Licenses, About, and Activation Software Required panes with React/TypeScript surfaces using the same architecture as Organization Manager, Main Dashboard, New Bot Job, Clone Job, and Config Manager.

The migration must remove the duplicated activation implementations currently found in `ARLicensePane` and `LicenseActivationApp`. React owns presentation, form state, confirmation, validation feedback, and navigation. Java temporarily owns license cryptography, machine fingerprinting, protected file operations, configuration persistence, and license verification.

## Existing Java Ownership

| User surface | Current implementation | Notes |
|---|---|---|
| About | `ARInfoPane` / `ARInfoScene` | Displays version, build, expiration, copyright, and opens License |
| Licenses | `ARLicensePane` / `ARLicenseScene` | Full request, activation, existing-license, agreement, and folder workflow |
| Activation Software Required | `LicenseActivationApp` and `ARLicenseScene` | Duplicates agreement, request, activation, validation, and status behavior |

## Target React Structure

Create these files under `/srv/projects/ar-react-ts-grid/src/components`:

- `LicenseManager.tsx`
- `LicenseManager.module.scss`
- `AboutPanel.tsx`
- `AboutPanel.module.scss`
- `ActivationRequired.tsx`
- `ActivationRequired.module.scss`
- `licenseTypes.ts`
- `useLicenseApi.ts`

`LicenseManager` is the single reusable workflow. `ActivationRequired` uses it in a blocking startup state. `AboutPanel` shows product information and opens the same License Manager component. Do not duplicate agreement text, form rules, status mapping, or activation controls.

## Target Backend Boundary

Add a pane-free Java service and WebSocket routes:

| Operation | Purpose |
|---|---|
| `license.bootstrap` | Return status, enabled mode, configured path, organization, owner, agreement, and supported actions |
| `license.status` | Recheck the current license and return a typed status |
| `license.request` | Validate fields and generate a request file |
| `license.activate` | Import and verify a response file |
| `license.useExisting` | Select, verify, and persist an existing license location |
| `license.chooseDirectory` | Open the temporary native directory chooser while JCEF remains |
| `license.chooseFile` | Open the temporary native file chooser while JCEF remains |
| `about.bootstrap` | Return product name, version, build, expiration, licensing mode, and copyright |

All responses must use structured fields such as `ok`, `statusCode`, `title`, `message`, `path`, `requestId`, and `capabilities`. React must not parse HTML-formatted Java messages.

## Phase 1 - Consolidate License Logic

- [x] Inventory the legacy license branches and remove the superseded pane/application implementations.
- [x] Extract the pane-free `LicenseService` around protected cryptographic and fingerprint logic.
- [x] Define and test typed status mapping across every `LicenceVal` value.
- [x] Move the recovered seven-section agreement to the single versioned React resource `licenseAgreement.ts` (`d28a8d6`).
- [x] Centralize email, organization, owner, agreement, path, and response-file validation.
- [x] Keep private keys, machine identity, fingerprint generation, and protected file parsing out of React.
- [x] Add request IDs and serialize activation mutations to prevent duplicate imports or request files.

## Phase 2 - Backend API

- [x] Implement read-only `LicenseService` bootstrap/status/About contracts without JavaFX imports.
- [x] Add the license status/bootstrap and About bootstrap WebSocket routes to `SimpleWebSocketServer`.
- [x] Return capabilities for request, activate, use-existing, choose-file, and choose-directory.
- [x] Return safe error details without exposing license secrets or raw encrypted content.
- [x] Persist selected license path, organization, and owner only after successful operations.
- [x] Recheck the imported or selected license before returning success.
- [x] Publish `license.statusChanged` to every open session and consume it in Main Dashboard and both instruction grids (`0500f35`).

## Phase 3 - React License Manager

- [x] Build a restrained reusable License Manager panel with separated SCSS (`90bb13a`).
- [x] Use a segmented control for Request, Activate Response, and Use Existing (`90bb13a`).
- [x] Capability-gate request transport modes: Directory is enabled and Online remains unavailable, so no false Online control is rendered.
- [x] Show license status, licensed organization/owner, configured path, and last verification result (`90bb13a`).
- [x] Show organization, owner, and email fields only for Request (`b292327`).
- [x] Show file/path controls only for Activate or Use Existing (`b292327`).
- [x] Require explicit agreement acceptance before Request or Activate (`b292327`).
- [x] Keep agreement content in a bounded scrollable region for Request and Activate without nesting the page in a card (`d28a8d6`).
- [x] Use React agreement confirmation and inline structured errors for shared license workflows; backend cryptographic parsing is pane-free.
- [x] Prevent repeated submission while a request is pending (`b292327`).
- [x] Refresh status immediately after request generation, activation, or existing-license selection (`b292327`).

## Phase 4 - React About Panel

- [x] Build `AboutPanel` with product name as the primary heading and separated SCSS (`90bb13a`).
- [x] Show version, build, expiration/status, copyright, and licensing mode from `about.bootstrap` (`90bb13a`).
- [x] Normalize supported persisted expiration formats to strict ISO `yyyy-MM-dd` in `about.bootstrap`.
- [x] Open the shared `LicenseManager` from the License action (`90bb13a`).
- [x] Remove the direct `ARInfoPane -> ARLicenseScene` dependency and route its License action to React.
- [x] Keep product metadata backend-owned; React uses neutral unavailable states instead of duplicated product constants (`c70d669`).

## Phase 5 - Activation Required Startup State

- [x] Replace the active startup `ARLicenseScene` route with the reusable `ActivationRequired` React surface.
- [x] Make `license.startup` return allowed/activation-required state and the target React session.
- [x] Prevent protected WebSocket routes and job mutations while activation is required.
- [x] Permit only license recovery/status, About, and basic connection operations while restricted.
- [x] Re-bootstrap the React dashboard after successful activation without requiring a JavaFX window restart (`60edf97`).
- [x] Preserve offline request generation and response/license import through the configured license directory.
- [x] Define headless behavior as server-validated typed paths; native chooser capabilities remain false until real endpoints exist.

## Phase 6 - Remove Redundancy and Legacy Routes

- [x] Route normal-navigation License buttons to the shared React component; startup activation is tracked separately.
- [x] Route the active React Main Dashboard About/Info action to `AboutPanel`.
- [x] Route active legacy license guards through `LicenseService.isActive()` instead of duplicated verification/dialog calls.
- [x] Remove startup and cryptographic JavaFX HTML message composition from license verification paths.
- [x] Remove the unreferenced `ARLicensePane`, `ARLicenseScene`, `ARInfoPane`, and `ARInfoScene` after React parity coverage.
- [x] Delete the unreferenced `LicenseActivationApp`; runtime startup verification remains an external Java-machine gate.
- [x] Keep protected `LicenseManager` cryptographic primitives backend-only pending the separate post-Java Node migration.
- [x] Run the Java/test zero-reference audit before deleting the legacy About/License scenes, panes, and launcher.

## Security Requirements

- Never send license secrets, signing keys, raw fingerprints, or decrypted payloads to React.
- Validate all paths server-side and restrict filesystem access to configured license/request locations.
- Authenticate license API calls and log activation outcomes without logging sensitive material.
- Verify uploaded/imported files by content and signature, not filename or extension alone.
- Rate-limit online status and request endpoints.
- Use explicit maximum upload sizes and reject symbolic-link/path traversal escapes.

## Test Gates

- [x] Add tests covering every `LicenceVal` mapping and the complete headless-safe capability contract.
- [x] Add deterministic request validation tests for missing agreement/request ID, invalid organization, owner, and email.
- [x] Add encrypted request-file generation coverage using a configured temporary directory.
- [x] Add deterministic valid, expired, wrong-machine/domain/user, malformed, missing, and unreadable-path license fixtures.
- [x] Add idempotency-cache tests for duplicate successful mutations, retryable failures, defensive copies, and bounded eviction.
- [x] React tests for Request, Activate, and Use Existing payloads plus agreement gating (`af861e9`).
- [x] React tests for capability visibility, pending state, structured errors, and successful status refresh (`13e6c93`).
- [x] About tests for version/build/expiration, dashboard navigation, malformed-response fallback, and the shared License Manager (`04665b3`).
- [x] React startup activation tests for blocking state, successful dashboard handoff, and Exit (`fc63b2e`).
- [x] Add deterministic startup policy tests proving inactive sessions can use only recovery/About operations and cannot reach protected routes.
- [x] Test the Linux/headless typed-path capability contract; native Windows chooser is explicitly unsupported rather than falsely advertised.
- [x] Add service-level encrypted response import, successful activation, persisted `ARWeb.lic`, and `mainDashboard` startup transition coverage.

## Remaining JavaFX Page Inventory

This inventory is based on current pane/scene classes and existing React components. A pane may remain as a JCEF compatibility host even when its user-facing content has migrated.

### Migrated or actively being completed

| Area | React replacement/status |
|---|---|
| Main Dashboard / job list | `MainDashboard.tsx`; migrated, parity work remains |
| Organization and environments | `OrganizationManager.tsx`; migrated |
| New Bot Job / job manager | `NewBotJobManager.tsx`; migrated |
| Clone Job | Integrated React manager flow; migrated |
| Config Manager | `ConfigManager.tsx`; migrated |
| Bot Job and component instruction grids | `GridItem.tsx` / `GridItemComp.tsx`; active migration tracker |
| New Command and Variables | `InstructionCommandPanel.tsx`; active parity and end-to-end work |
| Create Block | `CreateNewBlock.tsx`; migrated, end-to-end persistence test remains |
| OCR interaction | `OCRPanel.tsx`; React surface exists, legacy ownership still requires audit |

### Still requiring migration or explicit retirement audit

| JavaFX area | Required decision/work |
|---|---|
| About (`ARInfoPane`) | Migrate through this roadmap |
| License (`ARLicensePane`) | Migrate through this roadmap |
| Activation required (`LicenseActivationApp`) | Consolidate and migrate through this roadmap |
| Excel file workflow (`ARExcelFilePane`) | Create dedicated migration/parity roadmap |
| Save Component (`ARSaveComponentPane`) | Migrate component-save form and persistence feedback |
| Scanner/pre-scan host (`ARScannedElementPane`, `ARScannedElementScene`) | React UI is partial; audit scanner/device/WebDriver ownership before removal |
| OCR configuration/results (`AROcrConfigPane`, `AROcrTestResultsPane`) | Verify whether `OCRPanel` has complete configuration and results parity |
| Alert/dialog infrastructure (`ARAlertPane`, `PerformMessage`) | Replace remaining Java dialogs with shared React confirmation/error surfaces |
| Bot Job detail host (`ARViewBotJobPane`) | React grids exist; remove residual Java controls only after current tracker and runtime audit |
| Main/config/new-job/clone Java panes and scenes | Perform zero-reference audit and remove compatibility wrappers after JCEF routing no longer needs them |
| Legacy organization/new-home-banking panes | Confirm no routes remain, then remove |

## Completion Criteria

- About, License, and activation-required experiences are fully React-owned.
- One shared License Manager implements every activation mode.
- No normal license workflow opens JavaFX or a second activation application.
- Java license code is pane-free, structured, testable, and limited to protected backend operations.
- Startup restriction and post-activation re-bootstrap work without restarting a JavaFX scene.
- All remaining JavaFX pages have either a migration roadmap or an explicit, evidenced retirement decision.
