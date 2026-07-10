# Config Page React Backend Migration Roadmap

Date: 2026-07-10

## Objective

Migrate the JavaFX **Configuration** page to React TypeScript using the same embedded React/JCEF pattern already used by **NEW ORGANIZATION**, **Main Dashboard**, and **New Bot Job**.

This page is high risk because it controls filesystem paths, database connection switching, full database backup/restore/delete operations, AI GEN FLOW settings, and Organization access. The React version must stay very close to the current behavior and must not simplify or reorder destructive operations.

Java remains the backend/bridge during migration. React owns the UI, validation display, confirmation states, and status messages. Java owns filesystem dialogs, database operations, license checks, scene cleanup, and all existing backup/restore/delete implementations.

## Current Java Owner

Primary JavaFX screen:

- `src/main/java/com/allinweb/ch/component/scene/ARConfigurationScene.java`
- `src/main/java/com/allinweb/ch/component/pane/ARConfigurationPane.java`

Current related services/utilities:

- `ARPropertyManager`
- `ARPropertyEnum`
- `PerformInitializer`
- `PerformDataBase`
- `PerformDBEngine`
- `PerformLists`
- `PerformBackup`
- `PerformMessage`
- `AROrganizationManagerScene`
- `ARWebDriver`

Current Java behavior:

- Shows a 800 x 700 modal Configuration window.
- Displays top action row:
  - Browser dropdown
  - DB Type dropdown
  - Reload Configs
  - Backup DB
  - Restore DB
  - Restore Date
  - Delete DB
  - Organizations
- Shows an **Operational Configuration** section by default:
  - License Path
  - Excel Path
  - Log Path
  - Database Path
  - Report Path
  - Priority Path
  - Engine Path
  - Web Driver Path
- Shows an **Advanced Configuration** section collapsed by default:
  - Appium Path
  - Plugins Path
  - URL Plugins
  - Database URL
  - Database User
  - Database Password
  - AI API Key
  - AI Endpoint
  - AI Model
  - AI Max Generated Blocks
  - Edit GEN FLOW Prompt
- Opens native directory/file choosers for path fields.
- Disables/enables DB fields based on selected database type:
  - `Access` and `TEXT`/SQLite use `PATH_DB`
  - Postgres uses `DB_URL`, `DB_USER`, `DB_PWD`
- Saves config properties, tests DB connection, changes DB connection, closes dependent scenes, clears/reloads lists, refreshes main page data, and shows success/error modals.
- Backs up the full database into a single SQL file and also copies Access/SQLite DB files where needed.
- Restores from either current single-file backup or legacy per-table backup files.
- Deletes all job details after confirmation.
- Opens React Organizations manager.
- Edits the `GEN_FLOW` prompt in a JavaFX text editor.
- Shows a small Organizations list at the bottom of the Config page.

## Migration Rule

Keep `ARConfigurationPane` available until the React version is verified end to end.

Do not move DB logic into React. Do not reimplement backup/restore/delete in TypeScript. The Java side should:

- open the React container
- bootstrap `window.receiveDataFromJava(...)`
- serve `config.*` WebSocket commands
- open native file/folder choosers
- execute existing save/backup/restore/delete/prompt operations
- close dependent scenes on DB reload/restore exactly as today
- refresh Main Dashboard after DB-affecting operations

Do not run Java/Maven as part of this roadmap execution unless explicitly requested.

## Actual Frontend Location And Packaging

Frontend repo:

- `/srv/projects/ar-react-ts-grid`

React source location:

- `/srv/projects/ar-react-ts-grid/src/components`

Suggested files:

- `src/components/ConfigManager.tsx`
- `src/components/ConfigManager.module.scss`

React bootstrap should be added to:

- `/srv/projects/ar-react-ts-grid/src/index.tsx`

Suggested session id:

- `configManager`

Build and bundle flow remains:

```bash
cd /srv/projects/ar-react-ts-grid
npm run build
cp -r build /srv/projects/ar-web-selenium/src/main/resources/
```

## Target UX

Make the React UI very similar to the current Configuration page so behavior remains familiar.

Suggested layout:

- Top blue header:
  - title: `Configuration`
  - status pill: connection/save/backup/restore/delete state
- Action toolbar:
  - Browser dropdown
  - DB Type dropdown
  - Reload Configs
  - Backup DB
  - Restore DB
  - Restore Date picker
  - Delete DB
  - Organizations
- Two collapsible sections:
  - `Operational Configuration`
  - `Advanced Configuration`
- Bottom Organization summary grid/list:
  - ID
  - Organization
  - Active Jobs
  - URL Baseline

Design constraints:

- Use the same dense operational style as `OrganizationManager`.
- No marketing layout.
- No nested cards.
- No row text wrapping.
- No right-side clipping when maximized.
- Use fixed internal scroll where needed.
- Keep destructive actions visually distinct and require confirmation.
- Use inline status/errors, but keep Java-side confirmation for destructive operations during the first migration phase if that reduces risk.

## Backend Contract

Use the same WebSocket bridge style as `organizationManager`, `mainDashboard`, and `newBotJob`.

React -> Java commands:

| Type | Payload | Result |
|---|---|---|
| `config.bootstrap` | `{}` | Returns current config, option lists, organization summary, DB status |
| `config.choosePath` | `{ field, mode: "file"|"directory" }` | Opens native chooser and returns selected path |
| `config.validate` | `{ config }` | Validates fields without saving |
| `config.save` | `{ config }` | Saves properties, tests DB connection, reloads data |
| `config.backup` | `{ destinationFolder? }` | Runs backup with confirmation/selected folder |
| `config.restore` | `{ date, sourceFolder? }` | Runs restore with confirmation/selected folder |
| `config.deleteAllJobs` | `{ databaseType }` | Deletes all job details after confirmation |
| `config.openOrganizations` | `{}` | Opens React Organizations manager |
| `config.loadGenFlowPrompt` | `{}` | Loads `GEN_FLOW` prompt |
| `config.saveGenFlowPrompt` | `{ content }` | Saves `GEN_FLOW` prompt |
| `config.cancel` | `{}` | Closes Config modal |

Java -> React events:

| Type | Payload |
|---|---|
| `config.bootstrapResponse` | `{ config, options, organizations, dbStatus, flags }` |
| `config.pathResponse` | `{ field, path, cancelled }` |
| `config.validateResponse` | `{ ok, message, errors }` |
| `config.saveResponse` | `{ ok, message, config, organizations, botJobs?, error? }` |
| `config.backupResponse` | `{ ok, message, fileName?, folder?, error? }` |
| `config.restoreResponse` | `{ ok, message, organizations, botJobs?, error? }` |
| `config.deleteResponse` | `{ ok, message, organizations, botJobs?, error? }` |
| `config.promptResponse` | `{ ok, content?, message, error? }` |
| `config.actionResponse` | `{ ok, message, organizations?, error? }` |
| `config.status` | `{ level: "info"|"success"|"warning"|"error", message }` |

DTO minimum:

```ts
type ConfigDTO = {
  browser: "Chrome" | "Edge" | "Firefox" | string;
  databaseType: "Access" | "Postgres" | "PostGres" | "TEXT" | string;
  pathLicense: string;
  pathExcel: string;
  pathLog: string;
  pathDb: string;
  pathReport: string;
  pathPriority: string;
  pathEngine: string;
  pathWebDriver: string;
  pathAppium: string;
  pathPlugins: string;
  urlPlugins: string;
  dbUrl: string;
  dbUser: string;
  dbPwd: string;
  aiApiKey: string;
  aiEndpoint: string;
  aiModel: string;
  aiMaxBlocks: string;
};

type ConfigOptionsDTO = {
  browsers: string[];
  databaseTypes: string[];
};

type ConfigOrganizationDTO = {
  id: number;
  name: string;
  activeJobs: number;
  url: string | null;
};
```

## Phase 1 - Backend Service Wrapper

Create a Java service around `ARConfigurationPane` behavior.

Suggested class:

- `ConfigService`

Responsibilities:

- read all current `ARPropertyEnum` values used by Config
- return browser/database options
- return organization summary from `PerformLists.getListHomeBanking()`
- validate required fields using the current rules
- test DB connection through `PerformInitializer.testConnection(...)`
- save properties through `ARPropertyManager.setProperty(...)`
- call `PerformDataBase.changeDbConnection()`
- close dependent scenes through a reusable cleanup method
- call `PerformLists.clearAllLists()`
- reload:
  - `PerformDBEngine.loadHomeBanking(null)`
  - `PerformDBEngine.loadHomeUrls(null)`
  - `PerformDataBase.loadQuickBotJobs()`
- return refreshed Organizations and Main Dashboard Bot Jobs
- push `mainDashboard.listResponse` after save/restore/delete operations

Acceptance:

- bootstrap returns the same values currently shown by JavaFX
- validation reports all missing fields currently checked by JavaFX
- save preserves property names exactly
- DB connection is tested before changing the saved DB properties
- successful save reloads organizations, environments, and quick Bot Jobs
- failure returns structured errors without half-refreshing React state

## Phase 2 - Native Path Picker Bridge

Keep file/folder picking in Java.

Implementation notes:

- React sends `config.choosePath` with a field id and mode.
- Java opens `DirectoryChooser` or `FileChooser` using current `PATH_DB` as the default start location.
- Java returns either `{ cancelled: true }` or `{ field, path }`.
- React only writes the returned path into the matching input.

Acceptance:

- License, Excel, Log, DB, Report, Priority, Web Driver, Appium, and Plugins fields can request a directory picker.
- Engine field can request a file picker, preserving current behavior.
- Cancelling a chooser does not clear the current value.
- The chooser opens owned by the Config scene so it does not hide behind the app.

## Phase 3 - Backup Flow

Move UI control to React but keep backup execution in Java.

Current Java behavior to preserve:

- selected database type must match saved database type
- `PerformDataBase.changeDbConnection()` runs before backup
- user selects destination folder
- backup filename is `backup_<dialect>_all_<yyyy_MM_dd>.sql`
- Access/SQLite also get a binary database copy through `PerformBackup.copyDbFileTo(...)`
- SQL dump uses `PerformBackup.dumpAllToSingleFile(...)`
- success/error is reported with folder and file name

Acceptance:

- mismatch between selected and saved DB type blocks backup
- cancelling destination folder cancels backup cleanly
- successful backup returns folder and filename
- backup does not change React config values

## Phase 4 - Restore Flow

Move UI control to React but keep restore execution in Java.

Current Java behavior to preserve:

- selected database type must match saved database type
- user selects restore date
- user selects source folder
- restore probes for:
  - `backup_<dialect>_all_<date>.sql`
  - `backup_all_<date>.sql`
  - legacy `backup_home_banking_<date>.sql`
- single-file restore uses `PerformBackup.restoreWithRemap(...)`
- legacy restore uses the existing 11-file restore chain
- dependent scenes are closed before reload
- all lists are cleared and reloaded after restore
- Main Dashboard and Organizations data refresh after success

Acceptance:

- missing date is rejected before any DB operation
- missing backup files return a clear error
- confirmation is required before restore
- successful restore refreshes organizations and main dashboard rows
- restore failure leaves a clear error and does not falsely update React as successful

## Phase 5 - Delete All Job Details Flow

Move UI control to React but keep delete execution in Java.

Current Java behavior to preserve:

- license check still applies when enabled
- selected database type must match saved database type
- confirmation text must clearly say it deletes all job details
- delete calls `PerformDataBase.deleteAllJobDetails(dataBaseType)`

Acceptance:

- mismatch between selected and saved DB type blocks delete
- confirmation is required
- successful delete refreshes Main Dashboard rows
- failure reports database-specific recommendation when available

## Phase 6 - GEN FLOW Prompt Editor

Replace the JavaFX text editor with React UI or a React modal inside Config.

Backend responsibilities:

- `config.loadGenFlowPrompt` calls `PerformDataBase.loadAiPrompt("GEN_FLOW")`
- `config.saveGenFlowPrompt` calls `PerformDataBase.updateAiPrompt("GEN_FLOW", content)`

Frontend responsibilities:

- keep monospace editor
- show required placeholder hint:
  - `{{BLOCK_NAME}}`
  - `{{ELEMENTS_JSON}}`
  - `{{MAX_BLOCKS}}`
  - `{{JSON_SCHEMA}}`
- warn if any required placeholder is missing before save

Acceptance:

- prompt loads from DB
- save writes to DB
- missing prompt returns clear error
- cancelling editor keeps previous prompt

## Phase 7 - React Config Manager

Create:

- `/srv/projects/ar-react-ts-grid/src/components/ConfigManager.tsx`
- `/srv/projects/ar-react-ts-grid/src/components/ConfigManager.module.scss`

Frontend responsibilities:

- bootstrap through `config.bootstrap`
- preserve current field labels and grouping
- keep operational section open by default
- keep advanced section collapsed by default
- disable DB URL/User/Password unless database type requires them
- disable path DB when database type requires remote DB URL instead
- show inline validation errors
- use native path chooser commands for path buttons
- show confirmation dialogs for backup/restore/delete
- open Organizations through `config.openOrganizations`
- show Organization summary list at the bottom

Acceptance:

- component renders inside current JCEF container
- maximized Config window expands React content cleanly
- no field text or button text wraps
- all path fields have matching chooser buttons
- destructive actions cannot be double-submitted
- status remains visible during long operations

## Phase 8 - Java React Container

Create Java container classes matching the existing React modal pattern.

Suggested classes:

- `ARConfigManagerScene`
- `ARConfigManagerPane`

Responsibilities:

- open React session `configManager`
- size initially the same as old Config scene or larger if needed
- own native file/folder choosers
- close from `config.cancel`
- expose scene cleanup used by save/restore/delete
- refresh Main Dashboard after DB-affecting operations

Acceptance:

- Main Dashboard Config action opens React Config manager
- old JavaFX Config pane is not shown from the React main dashboard
- closing Config does not close the application
- Config remains stable if Organizations window is opened and closed

## Phase 9 - Entry Point Swap

Modify current callers:

- `ARMainDashboardPane.openConfig()`
- any remaining `ARConfigurationScene.showModal()` call sites

Target behavior:

- Main Dashboard `Config` opens React Config manager
- legacy `ARConfigurationScene` remains available behind code until verification is complete
- save/restore/delete still refresh Main Dashboard data

Acceptance:

- Config button from Main Dashboard opens React
- Config page Organizations button opens React Organizations manager
- successful save sends fresh `mainDashboard.listResponse`
- restore/delete also update Main Dashboard

## Phase 10 - Test Plan

Do not use Maven/Java compilation for this migration unless explicitly requested.

Recommended tests:

- React build:
  - `npm run build` in `/srv/projects/ar-react-ts-grid`
- Bootstrap:
  - config values match current JavaFX values
  - browser/database option lists render correctly
  - organizations list matches `PerformLists.getListHomeBanking()`
- UI:
  - operational and advanced sections toggle correctly
  - text does not wrap or clip
  - maximized window expands React content
  - path chooser cancel preserves value
- Save:
  - blank required fields show validation errors
  - DB test failure blocks save
  - successful save reloads organizations and Bot Jobs
- Backup:
  - DB type mismatch blocks backup
  - cancel folder selection cancels operation
  - successful backup returns expected filename
- Restore:
  - missing date blocks restore
  - missing backup files return error
  - single-file restore path is detected
  - legacy per-table restore path is detected
  - successful restore refreshes Main Dashboard
- Delete:
  - confirmation is required
  - DB type mismatch blocks delete
  - successful delete refreshes Main Dashboard
- GEN FLOW Prompt:
  - prompt loads
  - missing placeholders warn before save
  - save persists prompt

## Phase 11 - Retirement

After the React Config flow is verified:

- mark `ARConfigurationPane` and `ARConfigurationScene` as legacy
- keep all DB/backup/restore logic centralized in `ConfigService`
- remove direct JavaFX Config entry points only after all callers use React
- keep current backup/restore filenames and legacy restore compatibility documented

## Open Questions Before Implementation

- Should the bottom Organizations list stay in Config, or should Config only show a compact summary plus the Organizations button?
- Should the GEN FLOW prompt editor be inside Config or open as its own React modal/session?
- Should hidden DB URL/User/Password fields remain hidden for Access/SQLite exactly as today, or be visible but disabled for clarity?
- Should `PATH_ENGINE` remain a file chooser while the other path fields use directory chooser?
- Should save continue validating fields that are currently hidden/disabled, or should validation become database-type aware?
