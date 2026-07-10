# New Organization React Backend Migration Roadmap

Date: 2026-07-10

## Objective

Migrate the JavaFX **New Organization** / **Organizations & Environments** workflow to a React-managed screen backed by Java commands.

The first target is functional parity with `ARNewHomeBankingPane`: list organizations, create/update/delete organizations, list environments for an organization, and create/update/delete environment URLs. The Java database/backend remains the source of truth; React becomes the UI.

## Current Java Owner

Primary JavaFX screen:

- `src/main/java/com/allinweb/ch/component/scene/ARNewHomeBankingScene.java`
- `src/main/java/com/allinweb/ch/component/pane/ARNewHomeBankingPane.java`

Current backend/database methods:

- `PerformDataBase.createNewHomeBanking(DatabaseUserDTO user)`
- `PerformDataBase.getNewHomeBankId()`
- `PerformDataBase.createHomeUrlChild(int homeBankId, String newUrl)`
- `PerformDataBase.createNewHomeUrl(int homeBankId, String newUrl)`
- `PerformDataBase.updateHomeUrl(int homeUrlId, int homeBankId, String newUrl)`
- `PerformDataBase.deleteHomeUrl(int homeUrlId)`
- `PerformDataBase.countUsageOfHomeUrlId(int homeUrlId)`
- `PerformDataBase.updateUserData(String id, DatabaseUserDTO user)`
- `PerformDataBase.deleteUserData(String id)`
- `PerformDataBase.loadAllDataUsers()`
- `PerformDBEngine.loadHomeBanking(null)`
- `PerformDBEngine.loadHomeUrls(null)`
- `PerformLists.getListHomeBanking()`
- `PerformLists.getHomeUrlsByBankId(homeBankId)`

## Migration Rule

Keep the current JavaFX `ARNewHomeBankingPane` intact while the React version is introduced. Do not remove the JavaFX window until the React flow is verified end to end with existing database data and with Bot Job creation still working.

The React version must preserve the same business rules:

- Organization name and baseline URL are mandatory.
- Duplicate organization names are blocked.
- Creating an organization also creates its first `home_url` child.
- Organization delete is blocked when active bot jobs exist.
- Environment URL is mandatory for insert/update/delete.
- Duplicate environment URL is blocked per organization.
- Environment delete is blocked when referenced by bot jobs.
- The last remaining environment for an organization cannot be deleted from the UI.
- Template/default config behavior stays backend-compatible while the related fields are hidden from the React UI.

## Actual Frontend Location And Packaging

Frontend repo:

- `/srv/projects/ar-react-ts-grid`

React source location for new pages:

- `/srv/projects/ar-react-ts-grid/src/components`

Files for this screen:

- `src/components/OrganizationManager.tsx`
- `src/components/OrganizationManager.module.scss`

The Java desktop app uses the bundled React build through the existing browser container path. For migrated React pages, the frontend is built from `/srv/projects/ar-react-ts-grid`:

```bash
npm run build
```

Then the generated frontend bundle is copied into:

```text
/srv/projects/ar-web-selenium/src/main/resources/build
```

The app currently uses JCEF/browser embedding for React pages. The migration should avoid creating new JavaFX UI panes. Java remains as the backend/bridge during migration; React owns the screen.

## Target UX

Add a React **Organizations** dashboard that replaces the JavaFX modal/window behavior for the user-facing flow.

Suggested layout:

- Header: `Organizations`
- Left/top form: selected organization details
  - ID, Organization, URL Baseline, Active Jobs
  - Actions: Insert, Update, Delete
- Organization table:
  - ID, Active Jobs, Organization, URL Baseline
- Environment form for selected organization:
  - Environment ID, Environment URL
  - Actions: Insert URL, Update URL, Delete URL
- Environment table:
  - ID, Organization, URL Environment
- Status/toast area:
  - loading, saved, updated, deleted, validation errors, backend errors

Keep density similar to an operational admin screen. This is not a marketing page.

Design update note:

- `Priority`, `Scan Config`, and `WebDriver Options` are hidden from the React UI as of the dropdown redesign. They remain in the DTO/backend contract for compatibility, but they may be unused and should be reviewed for full removal after Bot Job creation/scanner behavior is verified.
- The Template action is also hidden from the visible UI while those fields are hidden, because it only populates those candidate-removal fields.

## Backend Contract

Use the existing WebSocket bridge pattern used by `preScannerGrid` and MultiTest. Add a dedicated React session, for example:

`organizationManager`

React -> Java commands:

| Type | Payload | Result |
|---|---|---|
| `organization.list` | `{}` | Returns organizations and all home URLs |
| `organization.create` | `{ name, url, priority, searchConfig, optionsConfig }` | Creates `home_banking` and first `home_url` |
| `organization.update` | `{ id, name, url, priority, searchConfig, optionsConfig }` | Updates selected organization |
| `organization.delete` | `{ id }` | Deletes selected organization if no active jobs |
| `organization.template` | `{}` | Returns default priority/search/options template text |
| `homeUrl.list` | `{ homeBankingId }` | Returns environments for one organization |
| `homeUrl.create` | `{ homeBankingId, url }` | Creates environment URL |
| `homeUrl.update` | `{ homeBankingId, homeUrlId, url }` | Updates environment URL |
| `homeUrl.delete` | `{ homeBankingId, homeUrlId }` | Deletes environment URL if allowed |

Java -> React events:

| Type | Payload |
|---|---|
| `organization.listResponse` | `{ organizations: [], homeUrls: [] }` |
| `organization.saveResponse` | `{ ok, message, organization?, organizations?, homeUrls?, error? }` |
| `organization.deleteResponse` | `{ ok, message, organizations?, homeUrls?, error? }` |
| `organization.templateResponse` | `{ priority, searchConfig, optionsConfig }` |
| `homeUrl.listResponse` | `{ homeBankingId, homeUrls: [] }` |
| `homeUrl.saveResponse` | `{ ok, message, homeUrls?, error? }` |
| `homeUrl.deleteResponse` | `{ ok, message, homeUrls?, error? }` |
| `organization.status` | `{ level: "info"|"success"|"warning"|"error", message }` |

## Phase 1 - Backend Service Wrapper

Create a small Java service around existing `PerformDataBase` / `PerformLists` behavior, for example:

- `OrganizationManagerService`
- `OrganizationManagerResponse`
- `OrganizationTemplateDefaults`

Responsibilities:

- Centralize validation currently embedded in `ARNewHomeBankingPane`.
- Return structured success/error responses instead of showing JavaFX modals directly.
- Reload `loadAllDataUsers`, `loadHomeBanking`, and `loadHomeUrls` after mutations.
- Return fresh organizations and home URLs after every successful mutation.
- Keep all DB writes using the existing `PerformDataBase` methods initially.

Acceptance:

- Service can list organizations and environments without UI.
- Create/update/delete paths return the same validation/business outcomes as JavaFX.
- No JavaFX classes are needed for service execution.

## Phase 2 - WebSocket Command Handlers

Extend `SimpleWebSocketServer` command routing with the new organization commands.

Implementation notes:

- Follow existing handler style such as `handleUseCaseList`, `handleFlowSave`, and `handleRequirementSave`.
- Route responses to session `organizationManager`.
- Use `WebSocketSessionManager.sendMessageJson(...)` for all responses.
- Keep errors structured; do not rely on modal HTML strings in React mode.

Acceptance:

- A test WebSocket message `organization.list` returns current `PerformLists.getListHomeBanking()` data.
- Create/update/delete commands work from a socket client and refresh the returned list.
- Existing JavaFX `Organizations` button still opens the old pane unchanged.

## Phase 3 - React Session And Component

In the React app (`/srv/projects/ar-react-ts-grid`), add a route/session for `organizationManager`.

Suggested component:

- `OrganizationManager.tsx`
- `OrganizationManager.module.scss`

Suggested hooks/utilities:

- `useOrganizationManagerSocket`
- `organizationManagerTypes.ts`

React behavior:

- On mount, send `organization.list`.
- Selecting an organization populates the organization form and filters environments.
- Selecting an environment populates the environment form.
- Insert clears IDs and creates new records.
- Update requires selected ID.
- Delete requires selected ID and asks for confirmation.
- Template/config defaults are not exposed in the current UI while the config fields are hidden.
- Show backend validation errors inline/toast.

Acceptance:

- React screen can fully create, update, and delete an organization/environments on a local DB.
- The organization list updates without closing/reopening the screen.
- Active Jobs count is visible and delete-disabled when greater than zero.

## Phase 4 - Java Entry Point Swap

Change the `Organizations` button entry point in `ARMainPane` to open the React manager instead of the JavaFX `ARNewHomeBankingScene`.

Implementation notes:

- Keep a fallback flag or method to open the JavaFX pane while React is under validation.
- Use the existing JCEF/browser embedding path for the React bundle.
- Do not create a new JavaFX form implementation. Java should only open the React container and provide backend socket/API handlers.
- The `Organizations` button should launch the React page with session id `organizationManager`.

Acceptance:

- Clicking `Organizations` opens React manager.
- Existing bot job list reload behavior still sees newly created organizations.
- New Bot Job flow still requires and consumes organizations exactly as before.

## Phase 5 - Parity And Regression Tests

Add focused tests before removing any JavaFX path:

Backend/service tests:

- List returns organizations and environments.
- Create organization creates one `home_banking` and one `home_url`.
- Duplicate organization name is rejected.
- Update organization keeps priority/search/options compatible until those fields are confirmed unused or removed.
- Delete organization with active jobs is rejected.
- Create duplicate environment URL is rejected.
- Delete last environment is rejected at service/UI level.
- Delete environment used by bot jobs is rejected.

Manual/E2E checks:

- Fresh DB: create first organization, then create Bot Job.
- Existing DB: edit organization config and verify Bot Job scanner still uses updated scan config.
- Existing DB: add second environment and switch a Bot Job environment.
- Delete blocked organization with jobs.
- Delete unused environment when at least two environments exist.

## Phase 6 - Documentation And Retirement

Update user documentation after the React flow is verified:

- `specifications/USER_GUIDE_PART1_SCANNER.md`
- screenshots for Organizations screen
- session handoff notes

Only after verification:

- Mark `ARNewHomeBankingPane` as legacy/fallback.
- Remove JavaFX-only validation duplication if the service fully owns the rules.
- Keep DTOs and database methods unless a later cleanup replaces them.

## Open Questions

- Should the React manager be a modal/window like the JavaFX scene, or embedded in the main AR Web screen?
- Should organization password/username/cookies/driver session fields remain hidden as today, or become advanced fields later?
- Should URL syntax validation stay as permissive as the current implementation, or be tightened during migration?
- Should hidden Template/default config fields be fully removed from the backend contract after scanner/Bot Job verification?

## Recommended First Slice

Build the backend service and socket read path first:

1. `organization.list`
2. `organization.template`
3. React read-only screen with selection and environment filtering

Then add mutations one at a time:

1. `organization.create`
2. `organization.update`
3. `homeUrl.create`
4. `homeUrl.update`
5. guarded deletes

This avoids replacing the JavaFX pane before the React screen can prove it sees the same data.
