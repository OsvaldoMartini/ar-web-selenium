# Floating Independent Workspaces Migration Roadmap

Date: 2026-07-22

## Objective

Standardize every major top-level workflow into its own independent floating workspace, using the same React + Java backend session model already used by Open Job.

The required target pages are:

- New Bot Job
- Clone Job
- Config
- Info
- Bot Job Details
- Page Scanner
- OCR Config
- OCR Results

Each page must:

- open as its own isolated workspace instance
- remain independent from the main dashboard
- keep realtime communication with the backend
- support drag and drop / floating layout where applicable
- close only itself when the page-level `X` is clicked
- never call `window.close()` as an app-level shutdown path

## Core Rule

The application must separate:

1. workspace lifetime
2. browser/application lifetime
3. backend session lifetime

Closing a floating page means:

- release that workspace session
- notify backend if needed
- return focus to the main dashboard or keep the parent shell alive
- do not kill the application process

## Current Pattern To Reuse

The Open Job / Bot Job Details flow is the reference implementation:

- Main Dashboard emits an open request
- Backend resolves the target session
- React receives `react.session.open`
- `index.tsx` retargets the current shell to the new workspace
- The page is rendered in a dedicated floating shell
- The workspace close action returns to the main shell rather than exiting the app

This pattern should be generalized, not duplicated ad hoc.

## Workspaces To Normalize

### 1. Bot Job Details

Current state:

- already has an open/retarget flow
- already uses a dedicated session model

Needed rule:

- same open semantics as every other detached page
- close button must only close that workspace

### 2. New Bot Job

Needed:

- open from the dashboard as an independent page
- keep its own WebSocket session
- use a dedicated top-right close button
- close returns to main dashboard

### 3. Clone Job

Needed:

- open as a dedicated independent page
- no browser-level close behavior
- close button only closes the Clone Job page
- preserve clone-specific realtime messages

### 4. Config

Needed:

- separate floating page
- independent from dashboard DOM
- close returns to main dashboard
- preserve backend config save/test/reload communication

### 5. Info

Needed:

- same isolated page pattern
- no application shutdown on close
- if the page is informational only, still preserve the same shell behavior for consistency

### 6. Page Scanner

Needed:

- open as a detached workspace
- remain visible even when Bot Job Details is open
- support the same close semantics as other detached pages
- keep live scanner actions and element selection synchronization

### 7. OCR Config and OCR Results

Needed:

- each opens as its own floating independent page
- no nesting inside Bot Job Details or Page Scanner DOM
- close action only closes the workspace
- results window should not depend on application shutdown

## React Architecture Changes

### Shell Routing

Keep `src/index.tsx` as the top-level workspace router, but make it the only place that decides which detached workspace is active.

The router should:

- read the current session
- decide which page component to mount
- provide a common close callback that returns to the main dashboard
- preserve the current websocket port and session identity rules

### Shared Workspace Shell

Keep using the shared floating shell template:

- `DesktopWorkspaceShell`
- `FloatingWorkspaceFrame`

The shell should remain a layout wrapper only. It should not own business logic.

### Shared Close Contract

Introduce a single close contract for detached pages:

- `onDetachedClose`
- or a page-specific equivalent that always resolves to “return to main dashboard”

Avoid direct use of `window.close()` in page-level components unless it is intentionally a native app shutdown path and not a workspace close action.

## Backend Contract Changes

The Java backend must continue to own:

- opening the workspace session
- maintaining one logical session per detached page type
- retargeting instead of creating duplicate pages
- broadcasting realtime updates to the correct session
- cleaning up the correct session when a workspace closes

The backend should distinguish between:

- open new workspace request
- focus existing workspace request
- retarget existing workspace to another Bot Job
- close only this workspace request

## Session Behavior Required

### Bot Job and Open Job

- clicking the same Bot Job twice should focus or retarget the same workspace
- it must not spawn duplicate floating pages for the same logical workspace

### Page Scanner and OCR

- opening Page Scanner, OCR Config, or OCR Results must preserve the current main dashboard
- each workspace should be independently draggable and usable on separate monitors
- if the user opens the same workspace type again, the backend should reuse or retarget the existing session, not duplicate it

### Close Behavior

For every detached workspace:

- `X` closes only that page
- the main dashboard survives
- the backend remains alive
- other floating pages remain alive unless explicitly targeted by their own close logic

## Implementation Milestones

### Phase 1: Normalize shared routing

- centralize detached workspace route decisions in `index.tsx`
- pass a single close callback into all detached workspaces
- remove any remaining workspace-close `window.close()` calls

### Phase 2: Make detached pages page-local

- New Bot Job
- Clone Job
- Config
- Info
- Page Scanner
- OCR Config
- OCR Results

Each page gets its own isolated close and lifecycle contract.

### Phase 3: Backend session coordination

- preserve realtime websocket routing
- ensure retarget messages carry the right workspace identity
- ensure backend cleanup only affects the closed workspace

### Phase 4: UI consistency

- same floating frame template for all pages
- same close button placement
- same top-right user/action area pattern where applicable
- same no-browser-address-bar behavior for detached workspaces

### Phase 5: Verification

- open main dashboard
- open Bot Job Details
- open Clone Job
- open Config
- open Info
- open Page Scanner
- open OCR Config
- open OCR Results
- close each one independently
- confirm main dashboard remains alive
- confirm other detached pages remain alive

## Acceptance Criteria

- No detached workspace close action shuts down the app.
- Open Job still works with the same call pattern and realtime target selection.
- Each requested page can exist as an independent floating workspace.
- Page Scanner and OCR remain realtime connected to the backend.
- Same logical workspace opened twice reuses/focuses/retargets instead of duplicating blindly.
- Main dashboard remains available as the stable parent shell.

## Risks

- Existing code contains direct `window.close()` calls in several workspace components.
- Some backend pathways may currently interpret close as application shutdown.
- Multiple page types already reuse shared websocket/session helpers, so the close contract must be changed consistently.
- Realtime retargeting must stay deterministic or the same workspace could display stale data.

## Notes

This roadmap is intentionally broader than a single page migration because the requested behavior is now a workspace model, not a one-off screen.

The right end state is a uniform rule:

> Every major top-level page is a floating independent workspace, and closing it only closes itself.

