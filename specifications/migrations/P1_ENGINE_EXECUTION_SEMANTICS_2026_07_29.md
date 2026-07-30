# P1 Engine and Execution-Path Semantics

Date: 2026-07-29
Status: complete characterization; no runtime behavior changed
Roadmap: `ROADMAP_VARIABLE_CENTRIC_INSTRUCTION_GRAPH_2026_07_29.md`

## 1. Scope and evidence boundary

P1 freezes what the application actually executes before React relationship classification,
reconnect, free movement, or non-blocking relationship diagnostics change behavior.

The audit covers:

- persisted Bot Job fields loaded by `PerformDBEngine`;
- the in-process Playwright path ending in `ScannerRuntimeBackend.executeJob()`;
- every current Test Run, Launch, and Pre-Launch entry point;
- the configured external Engine JAR and its matching local source repository;
- current command metadata, operation encoding, and focused characterization tests.

P1 does **not**:

- change command behavior;
- repair production rows;
- block execution because variable relationships are incomplete;
- change React rendering or persistence contracts;
- rebuild, replace, or repair the installed external Engine.

### Installed Engine identity

`D:\Projects\ARWebBancaStato\Config-4.2\ARWeb.config` points to:

`D:\Projects\ARWebBancaStato\ARWeb-Scanner\AR_Web_Engine-4.2.jar`

The configured JAR exists:

| Fact | Value |
|---|---|
| SHA-256 | `902ce883933042b23214d740b56aaf5fedf3ab27e6b64fc304df3aff7aa30b85` |
| Size | `71,340,765` bytes |
| Matching build artifact | `D:\Projects\AllinWeb\ar-web-engine\target\AR_Web_Engine-4.2.jar` |

The configured JAR and matching build artifact are byte-for-byte identical. Therefore the
`ar-web-engine` checkout below is the authoritative source baseline for this installed binary.

The two inspected external Engine source baselines are:

| Repository | Branch | Commit |
|---|---|---|
| `D:\Projects\AllinWeb\ar-web-engine` | `VERSION-4-2-NEW` | `f890e833d9aad9a8abbfb455e789e3d74c7817a5` |
| `D:\Projects\AllinWeb\ar-web-engine-4.2` | `VERSION-4-2-BANCA_STATO_16-09-25` | `cc4ceac8768094af3841ae9a8c805376ad7c11e5` |

The inspected runtime files are byte-identical between those two repositories:

| File | SHA-256 |
|---|---|
| `runner/EngineRunner.java` | `58144ec9bc62b53c96b4cdb0a9144c13d34c53e9a436fcd2d5dc199d49101158` |
| `facade/PerformActions.java` | `010515def4a162e4b80e539f54ea6784f5ce6156b9c6bf025641f3e84c318ebf` |
| `facade/PerformDBEngine.java` | `55f7f94b3420294a76b9eb02aa521d890ac3ca5459011f2765a73976a83e099e` |

These hashes prove source equivalence between the inspected repositories. The whole-JAR equality
above additionally identifies the installed production-configured Engine with the
`ar-web-engine` build artifact.

## 2. Persisted relationship facts

`PerformDBEngine.loadCompleteJobs` loads, among other instruction fields:

- action and operation;
- `parent_id`;
- `parent_block_id`;
- `variable_id`;
- Block and instruction order and Active flags;
- locator and input-related fields;
- instruction references.

`loadAllVariables` loads:

- variable ID and owner `instruction_id`;
- type, name, configured value, local format, and delimiter.

The runtime therefore receives all three instruction relationship identifiers plus variable owner
metadata. Receiving a field is not the same as using it during execution; the action matrix below
records actual use.

### Legacy EXCEL GOTO normalization

There are two silent compatibility repairs:

1. the general job loader may project a missing EXCEL GOTO `parentBlockId` to its own Block in
   memory;
2. `ScannerPreLaunchPreparation.loadAndFixExcelGoto` can write a missing destination back to the
   database before execution.

Execution diagnostics must report this as an invalid relationship. They must not mutate the graph
while evaluating readiness, and variable health must never block Test Run or Launch.

## 3. Audited command capability matrix

The capability schema must keep authoring, persistence, and runtime behavior separate. A generic
“parent” or generic “consumer” relationship is not precise enough.

| Action | Canonical operation | `parentId` at runtime | `variableId` at runtime | Variable owner `instruction_id` at runtime | `parentBlockId` at runtime | Actual runtime behavior |
|---|---|---|---|---|---|---|
| GET | `webField:typedVariable` | Resolves same-Block target name/action/XPath | Selects runtime map key | Not read | Not used | Reads the target element and writes the value to runtime memory |
| SET | `webField:configuredLiteral` | Resolves same-Block writable target | Selects runtime map key | Not read | Not used | Writes the configured literal to the element and overwrites the same runtime-memory key |
| E | `typedVariable` | Required for the persisted target/label path | Reads runtime map key | Not read | Not used | Exports the value previously written by GET **or SET** |
| CK | `typedVariable:operator:expected` | Resolves target/label context | Reads runtime map key | Not read | Not used | Compares the runtime-memory value |
| PDF CHECK | `typedVariable:operator:expected` | Resolves command context | Resolved but not used as the actual value source | Not read | Not used | Reads `fieldsToValidate`; actual values come from OUTPUT-ID-derived runtime keys |
| CSV CHECK | `typedVariable:operator:expected` | Resolves command context | Resolved but not used as the actual value source | Not read | Not used | Same source model as PDF CHECK |
| LOOP | `interval:count` | Resolves the same-Block loop anchor | Not used | Not read | Not used | Repeats from its anchor using the bounded count |
| REFRESH_LOOP | `interval:count` | Resolves the same-Block loop anchor | Not used | Not read | Not used | Repeats from its anchor and refreshes/waits between repeats |
| GOTO | `count` | Not used | Not used | Not read | Resolves destination Block | Forward target is a branch; backward target is a bounded repeat |
| EXCEL GOTO | `1` | Not used | Not used | Not read | Selects the initial Excel destination Block | The marker row itself is skipped during normal instruction iteration |

### Locked P1 consequences

1. Variable owner `instruction_id` is an authoring/integrity relationship today. The local
   executor does not consult it to run GET, SET, E, or CK.
2. GET is a runtime-memory producer.
3. SET is a literal DOM writer **and** a runtime-memory writer. It does not consume a previous GET,
   and a later CK/E can consume the SET value.
4. E reads runtime memory and still requires its persisted target context.
5. PDF/CSV CHECK must not be classified as ordinary GET-memory consumers until their execution
   contract is redesigned. They use `fieldsToValidate` plus OUTPUT-derived keys.
6. LOOP/REFRESH_LOOP use an element anchor, not variable ownership.
7. GOTO/EXCEL GOTO use Block targets, not instruction parents.

## 4. Operator-domain defect

The currently authorable and executable operator sets differ:

| Surface/runtime | CK | PDF CHECK / CSV CHECK |
|---|---|---|
| React Command Editor | `=`, `!=`, `>`, `<`, `>=`, `<=` | `=`, `!=`, `>`, `<`, `>=`, `<=` |
| Operation codec | Accepts any submitted string | Accepts any submitted string |
| Local runtime | `=`, `!=`, `>`, `<`, `contains` | `=`, `!=`, `>`, `<` |

Consequences:

- `>=` and `<=` are currently authorable and persist successfully but cannot execute
  successfully;
- `contains` executes for CK but is not offered by the current React editor;
- PDF/CSV do not implement `contains`.

P2 must use action-specific operator capability facts. It must not treat successful operation
encoding as proof of runtime support. Runtime expansion or UI restriction is a separately reviewed
behavior change.

## 5. Current execution entry points

| User entry | React/transport | Java path | Executes now? | Relationship-gate coverage today |
|---|---|---|---:|---|
| Bot Job Details TEST RUN | `botJobDetails.toolbar.action` | `BotJobDetailsWorkspaceHost` -> `BotJobTestRunCoordinator` -> `ScannerTestRunPreparationFlow` | Yes | None |
| Bot Job Details LAUNCH | Same toolbar transport | Same in-process coordinator/path | Yes | None |
| Pre-scan TEST RUN/LAUNCH | Shared Bot Job header/controller | Same coordinator/path | Yes | None |
| Detached Page Scanner TEST RUN | Shared toolbar action bound to scanner owner | Same coordinator/path | Yes | None |
| Classic Scanner PRE_LAUNCH | `scanner.action` | `ScannerPreLaunchStarter` | Yes | None |
| Main Dashboard Launch | `mainDashboard.launchBotJob` | `MainDashboardPresentationAdapter` | No; modal only | Not applicable until wired |
| Mobile `LAUNCH_BOT_JOB_TEST` | Mobile legacy transport | Forwarded to mobile receiver | Not locally | Receiver-owned |
| External Engine BAT/manual process | `execute/j ...` | External `EngineRunner` | Yes when separately invoked | Bypasses local preparation |
| `TestRunLauncher` | No current production caller | Direct legacy Playwright runner | No current caller | Would bypass both preparation flows |

All active in-process Playwright starts eventually converge on
`ScannerPreLaunchExecutionCoordinator` and `ScannerRuntimeBackend.executeJob()`, but they reach the
coordinator through two separate preparation flows.

### Important lifecycle drift

- Current Bot Job Details LAUNCH is in-process; comments that describe it as an external Engine
  process are stale.
- Main Dashboard Launch sends only `botJobId` and currently shows a placeholder modal.
- Main Dashboard and the mobile legacy request do not have the same correlated request/version
  contract as the Bot Job toolbar.
- `ScannerPreLaunchExecutionGate` is a concurrency guard. It is **not** a relationship-readiness
  validator.
- Classic Pre-Launch currently reports a definition-load error and can continue. Structural or
  stale-owner failures may still refuse startup, but variable metadata/health remains warning-only.

## 6. Exact future diagnostic insertion points

P4 must use the same non-mutating diagnostic evaluator in all active execution paths. Variable
issues produce warnings/VOID and never refuse execution:

1. `ScannerTestRunPreparationFlow.prepare`: after definitions are loaded and reported READY, before
   Bot Job/Excel/browser preparation.
2. `ScannerPreLaunchStarter.start`: after definitions and the current Bot Job are loaded, before
   Excel load and execution recall. A failed definition load must return immediately.
3. `ScannerPreLaunchExecutionCoordinator.recallJobExecutionId`: final defense-in-depth diagnostic
   refresh immediately before the concurrency gate reserves/submits the execution.
4. External Engine: validate after its own authoritative database load and before WebDriver
   initialization. A manual BAT cannot be protected only by the AR Web UI process.
5. Mobile receiver: enforce the same model at the receiving execution boundary.
6. Main Dashboard: when real Launch wiring is added, delegate to the unified execution service;
   do not introduce a direct `ProcessBuilder` bypass.

## 7. External Engine contract

The inspected Engine source accepts:

`execute/j {homeBankingId} {botJobId} {blockIndex} {excelPath} -c {configPath}`

Before initializing WebDriver it loads:

- Home Banking and URL data;
- the complete Bot Job;
- variables;
- EXCEL GOTO data;
- actions.

It then initializes WebDriver and calls its own `executeJob()`. A local UI preflight cannot make a
separate process safe by itself because the graph may change after the UI check. The external
contract needs an expected database graph version/execution revision, and the Engine must compare
it against the graph it loaded before opening WebDriver.

### Verified Scanner/Engine differences

The configured Engine is not command-semantics-equivalent to the in-process Scanner:

1. **GET/SET bypass Playwright in both runtimes.** Their special operator path resolves the parent
   XPath and operates through Selenium. It does not use the Playwright-first ordinary Web action
   path. Therefore the product requirement “one Playwright attempt” is not currently true for GET
   or SET.
2. **Configured Engine PDF/CSV CHECK has an uninitialized validation context.** `splitDTO` is read
   but never assigned in the installed class. Bytecode inspection confirms reads and no field
   write. Executing this branch can throw a null dereference. The Scanner constructs a `SplitDTO`,
   but with no fields it silently marks the check `IGNORED`; neither path proves a functioning
   integrated PDF/CSV validation.
3. **Forward GOTO differs.** Scanner contains explicit forward-target branch routing. The installed
   Engine retains only the older repeat-map logic, so the same persisted forward GOTO can behave
   differently.
4. **Null handling differs.** Scanner preserves nullable relationship IDs. External Engine loading
   converts SQL nulls to `0` through JDBC `getInt`, losing the distinction between missing and
   sentinel values.
5. **EXCEL GOTO repair differs and remains unsafe.** External loading includes name-based
   compatibility repair; a null instruction name can also cause a loader null dereference.
6. **Locator recovery does not cover GET/SET parents.** Ordinary Web Elements can receive
   in-memory locator repair; the GET/SET special path does not use that recovery.

The external Engine repositories currently have no direct automated test source tree. P4 cannot
claim universal execution gating until the external boundary is versioned and tested.

## 8. Revision-bound execution-start contract

The robust contract is one atomic **execution-start** request containing preflight expectations,
not a reusable “approved” token.

### Request

```json
{
  "contractVersion": 1,
  "requestId": "unique",
  "action": "TEST_RUN",
  "entryPoint": "BOT_JOB_DETAILS",
  "ownerAssertion": {
    "workspaceKind": "BOT_JOB",
    "homeBankingId": 2,
    "botJobId": 5
  },
  "expectedGraphVersion": 42,
  "graphRevision": "content-hash",
  "workspaceEpoch": 12,
  "runScope": {
    "kind": "ONE",
    "selectedBlockId": 1
  }
}
```

`workspaceEpoch` is required for detached-workspace callers, not invented for callers that do not
own an epoch.

### Response

```json
{
  "requestId": "unique",
  "action": "TEST_RUN",
  "status": "READY_STARTED",
  "enforcement": "SHADOW",
  "authoritativeOwner": {
    "workspaceKind": "BOT_JOB",
    "homeBankingId": 2,
    "botJobId": 5
  },
  "validatedGraphVersion": 42,
  "validatedGraphRevision": "content-hash",
  "normalizedRunScope": {
    "kind": "ONE",
    "selectedBlockId": 1
  },
  "scopeFingerprint": "reachable-plan-hash",
  "reachableBlockIds": [1],
  "reachableInstructionIds": [716],
  "issues": [],
  "executionId": 1001,
  "resyncRequired": false
}
```

Allowed terminal statuses are:

- `READY_STARTED`;
- `WARN_STARTED`;
- `BLOCKED` (structural ownership/version failures only; never variable health);
- `STALE`.

Each issue carries a stable code, severity, relationship kind, Block/instruction IDs, message, and
focus target.

### Revision limitation

The current content hash includes core instruction relationships and variable ownership, but it is
not a complete immutable execution snapshot. It omits execution-relevant facts such as Active
flags, Block state/order, locators, references, and several input/default fields. P4 therefore
needs:

- a database-owned graph version/CAS;
- a complete execution-content revision or immutable validated snapshot;
- an atomic recheck immediately before execution reservation/start.

## 9. Reachability rules

Validation applies to the requested **reachable active plan**, not blindly to every historical row:

- `ALL` validates the active full plan;
- `ONE` validates the actual selected scope and any runtime-reachable control-flow targets;
- future `FROM_SELECTED` validates the plan reachable from that starting point.

`ONE` must be characterized before it is described as “one physical Block”: current in-Block GOTO
handling can transfer control before the usual single-Block stop.

Inactive rows remain visible. Reachable variable issues are diagnostics only and never block
execution. Validation must not rewrite the user's Active flags.

## 10. P2 classifier locks

The pure React classifier must:

- distinguish `ELEMENT_TARGET`, `VARIABLE_BINDING`, `VARIABLE_OWNER`, `VARIABLE_ORDER`,
  `LOOP_ANCHOR`, `CONDITIONAL_ROOT`, `BLOCK_TARGET`, and `POSITIONAL_SCOPE`;
- derive roles from canonical actions, never instruction labels;
- use current rendered facts only, with no WebSocket, database, or Java semantic query;
- accept compound owner scope and nullable historical relationships;
- classify SET as a literal/runtime-memory writer, not a GET consumer;
- classify PDF/CSV source behavior separately from CK;
- avoid guessed scanned/authored provenance because no reliable `source_kind` exists;
- preserve broken rows and the last valid grid;
- make no UI or persistence change in P2.

## 11. Focused P1 verification

`ExecutionCommandSemanticsCharacterizationTest` freezes:

- operation encoding for GET, SET, E, CK, PDF CHECK, CSV CHECK;
- producer/consumer policy as it exists before P2;
- LOOP/REFRESH_LOOP/GOTO/EXCEL GOTO operation encoding and required fields;
- preservation of explicit `parentId`, `parentBlockId`, and `variableId` during decode;
- known operator-domain differences where a pure evaluator can characterize them.

P1 intentionally does not run a browser, external Engine, or broad suite. Runtime integration gaps
remain explicit future tests rather than being represented as proven.

Focused Maven verification:

| Test scope | Result |
|---|---|
| 10 execution metadata, codec, control-flow, prelaunch, native-operation, and evaluator classes | 57 passed |
| Failures / errors / skipped | 0 / 0 / 0 |
| Broad suite | Intentionally not run |
| React build/deployment | Not applicable; P1 changes no React source |

## 12. P1 acceptance and remaining defects

P1 acceptance is met when the focused tests pass and the roadmap checklist references this
document.

The audit exposes these separate behavior defects:

1. authorable `>=`/`<=` are unsupported at runtime;
2. PDF/CSV variable policy does not match their OUTPUT/`fieldsToValidate` runtime source;
3. SET runtime-memory writes are not represented by the old GET-only producer policy;
4. configured Engine PDF/CSV can null-dereference its uninitialized validation context;
5. GET/SET bypass the mandatory Playwright-first path in Scanner and Engine;
6. Scanner and Engine implement forward GOTO differently;
7. classic Pre-Launch can continue after definition-load failure;
8. current execution requests are not bound to a complete authoritative execution revision;
9. Main Dashboard Launch remains a placeholder;
10. manual/external and mobile execution need their own boundary validation.

These findings constrain P2 and P4. P1 does not silently fix them.
