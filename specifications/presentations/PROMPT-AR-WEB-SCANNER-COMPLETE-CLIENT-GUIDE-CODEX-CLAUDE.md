# Execution Prompt: AR Web Scanner Complete Client Guide

Copy this entire prompt into Codex or Claude. It is ready for this project and
must be followed as an execution contract. The objective is to produce a final,
client-ready manual with verified text, clickable navigation, and screenshots
of every relevant screen and state of the real AR Web Scanner application.

## 1. Project and output locations

Documentation repository and output owner:

```text
D:\Projects\AllinWeb\ar-web-selenium
```

Current source-of-truth repositories identified by the latest project handoff:

```text
Backend and deployment assets: D:\Projects\AllinWeb\ar-web-allinweb
React frontend:               D:\Projects\AllinWeb\ar-web-allinweb-fe
```

Do not assume those locations or branches are still current. Re-read the
governing files and verify the repositories before beginning.

Save the complete guide package under:

```text
D:\Projects\AllinWeb\ar-web-selenium\specifications\presentations\ar-web-scanner-client-guide\
```

Required final files:

```text
AR-Web-Scanner-Complete-Client-Guide.md
AR-Web-Scanner-Complete-Client-Guide.docx
AR-Web-Scanner-Complete-Client-Guide.pdf
GUIDE-COVERAGE-MATRIX.md
GUIDE-HANDOFF.md
screenshots\01-*.png ... NN-*.png
work\                         temporary generation material only
```

The Markdown file is the content source of truth. The DOCX and PDF are final
client deliverables generated from that source and visually verified page by
page.

## 2. Goal and quality bar

Create the final AR Web Scanner client manual at the same quality level as the
completed ARAPI and AR Conversational guides. The manual must allow a new client
user to install or start the authorized application, understand the interface,
create and configure automation, scan pages, manage elements and variables,
execute Bot Jobs, read results, and troubleshoot normal problems without
needing access to the source code.

The manual must:

- use only behavior verified in the current source or real running application;
- cover every client-visible page, dialog, menu, tab, toolbar, button, field,
  selector, status, and meaningful empty/error/loading state;
- explain every visible command and its result, prerequisites, and important
  side effects;
- use numbered screenshots captured one by one from the real application;
- use realistic synthetic demonstration data, never production customer data;
- preserve UI labels exactly as displayed, including capitalization;
- clearly distinguish confirmed behavior, administrator-only operations,
  optional modules, known limitations, and unavailable features;
- contain a clickable table of contents, internal bookmarks, cross-references,
  figure links, and working external links where appropriate;
- be polished enough to send directly to clients.

Do not invent missing behavior, buttons, workflows, configuration values,
screenshots, results, support contacts, or product claims.

## 3. Governing instructions and safety

Before modifying or launching anything:

1. Read every applicable `AGENTS.md` and `CLAUDE.md` completely in the
   documentation, backend, and frontend repositories.
2. Read the latest `SESSION_HANDOFF.md`, current roadmap, `IN_PROGRESS.md`, and
   relevant status/checkpoint documents.
3. Record the current branch, HEAD commit, upstream, and `git status --short` in
   all three repositories.
4. Preserve every pre-existing modification and untracked file. Never stage,
   remove, restore, or overwrite work that does not belong to this guide.
5. Confirm which repositories are authoritative. The `ar-web-selenium` checkout
   may be documentation-only even though it contains historical source.
6. Never expose or copy license files, passwords, API keys, database credentials,
   authentication tokens, customer identifiers, real account data, private
   endpoints, or internal infrastructure details into screenshots or documents.
7. Do not start, stop, restart, migrate, package, or modify a production/shared
   installation without explicit user approval.
8. Use an approved local demonstration configuration and synthetic data. If no
   safe environment is available, stop and request one.
9. Documentation work does not authorize application code, database, runtime,
   deployment, or configuration changes.
10. Follow the repository's commit policy. Stage only exact guide paths. Never
    include unrelated dirty files in a guide commit.

## 4. Codex-specific execution section

When Codex executes this prompt:

1. Treat the applicable `AGENTS.md` files as mandatory repository policy.
2. Inspect files with repository search before relying on historical manuals.
3. Use the browser-control capability for live navigation and screenshots when
   available. If the application cannot be controlled by that browser, use an
   approved local browser automation method and report the limitation.
4. Use the documents capability for DOCX creation/editing when available. Follow
   its complete render-and-verify procedure, including page rendering to PNG and
   visual inspection after every material layout change.
5. Use the PDF capability for PDF creation or inspection when available, and
   render every final PDF page for visual QA.
6. Use repository-native scripts and existing templates before creating new
   generators. Use `apply_patch` for text-file edits.
7. Keep concise progress updates at every approval gate and report exact files,
   commands, results, and unresolved limitations.
8. For Codex-authored Git checkpoints, obey the repository's required `CODEX-`
   commit prefix and push only the current intended branch/upstream.

Codex must not claim that a screen was verified merely because a component with
that name exists in source. It must verify reachability, state, and labels in the
running application whenever safe runtime access is available.

## 5. Claude-specific execution section

When Claude executes this prompt:

1. Read both `CLAUDE.md` and every applicable `AGENTS.md`; neither replaces the
   user's current task.
2. Use repository search and inspect the authoritative frontend/backend sources,
   not only the historical Markdown manuals or generated frontend bundle.
3. Use Playwright or another approved real-browser workflow to capture the live
   application at a fixed viewport. Do not substitute generated mockups.
4. Use the repository's existing document generation tooling when present. If a
   converter or package must be installed, stop and request approval first.
5. Render and inspect the DOCX and PDF page by page. A successful conversion is
   not proof that the document is visually correct.
6. Preserve UTF-8 explicitly. Do not use Windows text operations that silently
   convert UTF-8 content to a legacy code page.
7. Maintain `GUIDE-HANDOFF.md` with completed gates, artifact paths, screenshot
   inventory, source commits, verification evidence, and remaining work so
   Codex can continue without repeating or overwriting work.
8. Do not imitate a Codex commit signature. Follow the repository's actual
   commit naming policy and ask the user if the policy is ambiguous for Claude.

Claude must follow the same evidence, privacy, screenshot, layout, and approval
gates as Codex. Tool differences do not lower the acceptance criteria.

## 6. Codex and Claude collaboration contract

If both agents participate:

- Only one agent is the active owner of a file at a time.
- Never edit the same Markdown, DOCX, PDF, screenshot, generator, or handoff file
  concurrently.
- Before starting, read `GUIDE-HANDOFF.md` and verify Git status and HEAD.
- After each gate, update the handoff with: agent, timestamp, commit hashes,
  completed work, exact output files, commands run, verification results, open
  risks, and the next safe action.
- A receiving agent must verify existing artifacts before continuing; it must
  not regenerate accepted screenshots or rewrite approved prose without a
  stated reason.
- The agent producing the final package owns the final cross-format comparison
  between Markdown, DOCX, PDF, screenshot directory, and coverage matrix.
- Neither agent may claim another agent's unverified work as complete.

Use this handoff table:

```text
| Gate | Owner | Status | Evidence/artifacts | Commit | Remaining work |
| --- | --- | --- | --- | --- | --- |
```

Allowed statuses are `NOT STARTED`, `IN PROGRESS`, `READY FOR REVIEW`,
`APPROVED`, and `BLOCKED`.

## 7. Phase 0 - establish the baseline

Perform read-only discovery first.

1. Verify all repository paths, branches, commits, upstreams, and dirty files.
2. Identify the actual build and launch process from current configuration and
   code. Known leads include Java 17/Maven, the AR Web Scanner backend, and the
   React frontend, but these are not substitutes for verification.
3. Determine whether an already running authorized application can be used.
4. Identify its local URL/ports, configuration file, database type, browser
   dependencies, license requirements, and safe demonstration data source.
5. Confirm application/product name and version as displayed to the user.
6. Inspect existing documentation, especially:
   - `specifications\USER_GUIDE_PART1_SCANNER.md`
   - `specifications\USER_GUIDE_PART2_MULTITEST.md`
   - `specifications\USER_GUIDE_PART3_MULTITEST.md`
   - `specifications\screenshots\`
   These are historical coverage leads, not authoritative behavior.
7. Inspect the current React entrypoint, navigation/session-open contracts,
   top-level components, dialogs, detached workspaces, localization resources,
   and tests.
8. Inspect the corresponding Java handlers, WebSocket contracts, persistence,
   execution, browser automation, report/export, configuration, and license
   paths for every documented user action.
9. Identify conditional features, role/license restrictions, incomplete pages,
   unavailable actions, and differences between local demonstration and client
   deployment.
10. Create the first version of `GUIDE-COVERAGE-MATRIX.md`.

The baseline report must include:

- repository/branch/commit table;
- safe runtime plan and exact proposed launch command;
- proposed guide language and client audience;
- list of available historical guide/image assets;
- risks, missing prerequisites, and decisions needed from the user.

### Approval gate 0

Stop. Present the baseline report and request approval before launching the
application or changing documentation.

## 8. Phase 1 - build the authoritative UI and workflow inventory

After approval, map the product before writing prose.

For every top-level page and detached workspace, record:

- exact visible title;
- how the user reaches it;
- prerequisite organization, Bot Job, URL, element, variable, or configuration;
- source component and backend contract;
- navigation and close/back behavior;
- all buttons, menus, links, tabs, fields, toggles, tables, filters, pagination,
  context menus, dialogs, confirmations, badges, notifications, and keyboard
  shortcuts;
- enabled/disabled rules;
- loading, empty, populated, validation, warning, error, disconnected, stale,
  pending, success, and read-only states where meaningful;
- effect of each action and whether it changes persistent data;
- screenshots required to explain it.

Use the following as a provisional coverage checklist only. Confirm, rename,
add, or remove entries according to the current application:

1. Activation/licensing and startup states.
2. Main Dashboard and its primary actions.
3. Organizations / Home Banking / environments.
4. Configuration and connection/database/browser settings.
5. Bot Job list, search, selection, status, create, edit, clone, import/export,
   delete, and launch actions.
6. Bot Job Details, toolbar, URL/environment context, Blocks, instructions,
   relationships, context menus, and confirmations.
7. Command Editor, command types, waits/pauses, navigation, LOOP/GOTO/IF logic,
   variable binding, and ExcelWrite configuration.
8. Variables management and runtime variable behavior.
9. Page Scanner / AR Web Factory, search/match rules, scan controls, progress,
   and scan results.
10. Scanner Grid, element cards/rows, selection, rename, delete, save, locator,
    component, and page actions.
11. Page Mappings, capture history, Use Existing, Rescan, retention/pinning,
    screenshots/overlays, OCR review/apply, and failure/reload behavior.
12. OCR Configuration profiles, tabs, parameters, import/export if present,
    test/review, and approval actions.
13. Pages Open and browser/session management.
14. Excel Data and ExcelWriter Manager.
15. Pre-Launch and execution controls.
16. Execution progress, result details, evidence, reports, logs, and exports.
17. Backup/restore and maintenance actions that are truly client-visible.
18. Help, About, release/version information, and support paths.
19. Optional mobile/Appium, MultiTest, API-testing, or other licensed modules -
    only if they are present and reachable in the current client product.

Coverage matrix columns:

```text
ID | Chapter | Screen/state | Entry path | Exact controls | Preconditions |
Frontend evidence | Backend evidence | Runtime verified | Screenshot ID |
Documented | Cross-format verified | Notes/limitations
```

No visible interactive control may remain unexplained. If a control is
intentionally excluded, the matrix must record why.

### Approval gate 1

Stop and show the complete inventory, proposed chapter structure, coverage
matrix, and screenshot shot list. Wait for approval before capturing images.

## 9. Phase 2 - prepare a safe demonstration session

1. Use only the approved local/test installation and configuration.
2. Back up any local demonstration data that documentation actions might
   change, or use a disposable copy when feasible.
3. Create a coherent synthetic scenario used throughout the guide. Suggested
   neutral names: `Demo Banking`, `Client Portal Demo`, `Daily Balance Check`,
   `Account Balance`, and `DemoEnvironment`.
4. Never use real client names, account numbers, URLs, credentials, files, or
   proprietary data.
5. Set one fixed browser viewport and one fixed OS scaling level before the
   first capture. Preferred viewport: 1440 x 900 unless the real application
   requires another size. Record the actual pixel dimensions.
6. Use the same theme, language, zoom, window position, and data set throughout.
7. Disable distracting notifications and hide unrelated desktop content.
8. Confirm that no password, token, license, private endpoint, or personal data
   is visible anywhere in the capture area.
9. Do not edit application state directly in the database merely to fabricate a
   screenshot. Use supported UI workflows unless the user explicitly approves a
   documented fixture procedure.
10. Record the exact runtime build/commit and capture environment in the matrix.

## 10. Phase 3 - capture screenshots one by one

Capture real PNG screenshots in the order they will appear in the guide.

File naming:

```text
screenshots\01-startup.png
screenshots\02-main-dashboard.png
screenshots\03-main-dashboard-actions.png
...
screenshots\NN-final-screen-state.png
```

Rules:

- Use two-digit numbering until 99; use three digits if the guide exceeds 99
  images. Never renumber accepted screenshots without updating every reference.
- Capture the full application window for page context, then an additional
  focused state when a dense dialog/menu requires it.
- Every screenshot must show exactly the state described by its caption.
- Capture menus, tabs, forms, context menus, confirmation dialogs, populated
  tables, validation errors, disabled controls, progress, and results separately
  when each state teaches a different action.
- Avoid duplicate images that add no instructional value.
- Do not crop away page titles, navigation, or state indicators needed for
  orientation.
- Do not add fake controls or reconstruct the UI in an image editor.
- Prefer recapturing a safe screen over redacting a secret after capture.
- If a harmless annotation is essential, keep an untouched original and use a
  consistent numbered callout style. Record the transformation in the matrix.
- Verify every image is readable at normal document width.
- Record filename, dimensions, state, chapter, source commit, capture date, and
  privacy review result in the coverage matrix.

After every 10 screenshots:

1. verify dimensions programmatically;
2. inspect each image visually;
3. confirm numbering has no gaps or duplicates;
4. confirm the UI labels match the inventory;
5. update the matrix and handoff.

### Approval gate 2

After capturing the first representative set (cover/main page, one complex
workspace, one form/dialog, and one results/error state), stop and show the
images and metadata for style approval. After approval, capture the remaining
shot list and stop again with the complete inventory before writing the guide.

## 11. Phase 4 - write the guide

Write `AR-Web-Scanner-Complete-Client-Guide.md` in clear professional English
unless the user approves another language. Do not translate labels shown in the
application.

Required structure:

1. Cover page.
2. Document control: product, version/build, guide version, date, audience,
   owner, confidentiality classification, and source commits.
3. Clickable table of contents.
4. Introduction, purpose, audience, scope, and verified limitations.
5. Security and demonstration-data notice.
6. Prerequisites, supported environment, installation/startup, and first launch.
7. Interface fundamentals: navigation, common buttons, field conventions,
   tables, badges, loading/disabled/error states, notifications, and dialogs.
8. Quick-start workflow showing a safe end-to-end client scenario.
9. Screen-by-screen reference following the approved inventory.
10. Bot Job authoring and command reference.
11. Variables, data files, Excel Data, and ExcelWriter workflows.
12. Page scanning, elements, locators, Page Mappings, and OCR workflows.
13. Execution, monitoring, evidence, reports, and exports.
14. Administration/configuration features that clients are expected to use.
15. Troubleshooting and recovery, including symptoms, likely causes, safe
    checks, and escalation boundaries.
16. Known limitations and optional/licensed modules.
17. Glossary.
18. Support/escalation information only if supplied or verified.
19. Figure index and optional control/command index.

Every screen/state section must contain:

1. Purpose.
2. How to open it, including the exact navigation path.
3. Preconditions.
4. Numbered screenshot and caption: `Figure N. Exact caption`.
5. A table describing every visible control:

```text
| Control label | Type | Purpose | How to use | Enabled when | Result/side effect |
```

6. Numbered procedure for the normal workflow.
7. Expected result and visible confirmation.
8. Warnings, permissions, persistence, or limitations.
9. Related sections as clickable internal links.

Writing rules:

- Address the client professionally and use concise imperative steps.
- Keep each step atomic: one user action and its expected visible result.
- Bold exact UI labels; use code formatting only for commands, paths, ports,
  filenames, keys, and literal values.
- Explain what happens after an action, not only where to click.
- Distinguish `Save`, `Apply`, `Run`, `Launch`, `Delete`, `Close`, and `Cancel`
  precisely according to verified behavior.
- State when an action is destructive, persistent, role-restricted, unavailable
  during execution, or dependent on another service.
- Do not expose implementation detail unless it helps a client operate or
  troubleshoot the product.
- Do not advertise roadmap items as released functionality.
- Never claim PDF/Excel/export/report formats that the running version does not
  demonstrably provide.
- Link each figure reference to its figure/bookmark where the format supports
  it. Ensure TOC entries and cross-references are clickable in DOCX and PDF.

### Approval gate 3

Write the cover, document-control section, introduction, interface fundamentals,
quick start, and two representative screen sections first. Stop for tone,
terminology, screenshot scale, and table-format approval before writing the
remaining chapters.

## 12. Phase 5 - generate DOCX and PDF

1. Reuse an existing approved document template if the user provides one.
2. Generate DOCX and PDF reproducibly from the Markdown source or a documented
   generator. Do not maintain three unrelated copies of the prose.
3. Use A4 pages, professional margins, readable body text, consistent heading
   hierarchy, page numbers, headers/footers, branded but restrained colors, and
   a clean cover.
4. Keep screenshots legible and proportional. Never stretch images.
5. Keep captions with their images and avoid headings orphaned at page bottoms.
6. Repeat table headers across pages and prevent control-table rows from being
   clipped.
7. Include alt text for screenshots where tooling supports it.
8. Make the TOC, internal chapter links, figure references, and external links
   clickable in both DOCX and PDF where technically possible.
9. Ensure the document metadata contains the correct title, subject, author/
   organization if approved, and no temporary paths or private usernames.
10. Do not install Pandoc, LibreOffice, Python packages, fonts, or other tools
    without approval when they are not already available.

## 13. Phase 6 - mandatory visual and content QA

Render the complete DOCX and PDF to page images and inspect every page.

Verify:

- cover alignment and correct product name;
- no blank, duplicate, truncated, or unexpectedly rotated pages;
- no clipped text, tables, screenshots, captions, headers, or footers;
- readable screenshots at 100 percent zoom;
- consistent fonts, spacing, colors, figure numbering, and page numbering;
- no headings stranded at the bottom of a page;
- TOC page numbers and links are correct;
- all internal/external hyperlinks work;
- all screenshot files referenced by Markdown exist;
- every screenshot is used exactly once unless reuse is explicitly justified;
- every coverage-matrix row is either documented or has an approved exclusion;
- Markdown, DOCX, and PDF contain the same current content;
- search for placeholders such as `TODO`, `TBD`, `FIXME`, `{{...}}`, fake URLs,
  sample secrets, and unsupported claims returns zero unresolved matches;
- no client/customer data, credentials, keys, license content, machine-specific
  private paths, or internal-only notes remain;
- spelling and terminology are consistent with the UI;
- file properties and document metadata are suitable for client delivery.

If a visual defect exists, fix the source/generator, regenerate both formats,
and re-render the affected pages plus neighboring pages. Continue until the
layout is client-ready.

## 14. Phase 7 - functional verification of documented procedures

Using the approved demonstration environment:

1. Replay every quick-start step in order.
2. Replay at least one complete Bot Job creation/edit/execution workflow.
3. Verify every navigation path and named button in the guide.
4. Verify one expected validation/error state and its recovery.
5. Confirm steps that mutate or delete data are clearly marked and behave as
   documented.
6. Confirm optional or unavailable features are labeled accurately.
7. Restore or remove synthetic demonstration changes when safe and approved.
8. Record exact evidence and any unverified item in the coverage matrix and
   handoff.

Do not call the manual complete if any client-visible page or control remains
unverified. Mark genuinely inaccessible items as limitations and request user
acceptance.

## 15. Phase 8 - final package and Git checkpoint

Before staging anything:

1. List all final artifacts and file sizes.
2. Confirm temporary files are contained under `work\` and excluded from the
   client package.
3. Run `git diff --check` for text changes.
4. Review the complete diff and `git status --short`.
5. Prove no unrelated file is staged.
6. Present the completion checklist and request final acceptance where required.
7. Follow the applicable repository instructions for a narrowly scoped commit
   and push. For Codex, use the required `CODEX-` prefix.

Final report format:

```text
Outcome:
Guide language/audience:
Source repositories and commits:
Runtime/build verified:
Screens/pages/controls covered:
Screenshots captured and resolution:
Markdown verification:
DOCX render verification:
PDF render verification:
Link/bookmark verification:
Privacy/security review:
Files delivered:
Commit:
Push/upstream:
Deployment/runtime changes:
Known limitations:
Next client-review step:
```

## 16. Completion gates

Report every item independently:

```text
[ ] Governing instructions read
[ ] Authoritative repositories and commits confirmed
[ ] Existing dirty work preserved
[ ] Safe demonstration environment approved
[ ] UI/workflow inventory approved
[ ] Coverage matrix complete
[ ] Screenshot plan approved
[ ] Representative screenshots approved
[ ] All screenshots captured and visually verified
[ ] Sample chapters approved
[ ] Complete Markdown manual reviewed
[ ] DOCX generated and every page rendered/checked
[ ] PDF generated and every page rendered/checked
[ ] TOC, bookmarks, cross-references, and links tested
[ ] Procedures replayed against the running application
[ ] Privacy/security review passed
[ ] Final artifacts and source are consistent
[ ] Handoff updated
[ ] Only intended files committed
[ ] Commit pushed to the intended upstream
[ ] Client review remains / is complete
```

## 17. Stop conditions

Stop and ask the user instead of guessing if:

- the authoritative source repository or branch is unclear;
- the available runtime may be production or contain real client data;
- application startup requires an unknown license, credential, or migration;
- a requested screen cannot be reached safely;
- historical documentation conflicts with current source/runtime behavior;
- a feature is visibly incomplete or produces errors;
- document-generation software must be installed;
- the guide language, brand/template, confidentiality label, or support contact
  is not defined;
- a commit/push would include unrelated files;
- Codex and Claude ownership overlaps or the handoff is stale.

The final standard is evidence, not volume: every included instruction must be
true, every relevant control must be accounted for, every screenshot must come
from the real approved application, and every final page must be visually
inspected before delivery to the client.
