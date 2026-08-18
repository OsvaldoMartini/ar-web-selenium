# Pending test-failure investigation

The failures are not all obsolete tests. The reruns show three categories:

- Real product defects that require code fixes.
- Valid new behavior with obsolete tests/fixtures.
- Shared infrastructure/test-environment failures masking functionality.

This document began as a read-only investigation. Later checkpoints record the repairs that were
implemented and verified.

## Java suite complete — 2026-08-14 after `b6019e62`

This section supersedes the earlier Java totals and remaining-Java tables below. React and browser
E2E totals remain separate work and are not changed by this checkpoint.

- Full Java command: `mvn test`.
- Result: **1,466 total; 1,464 passed; 0 failed; 0 errors; 2 skipped** (`BUILD SUCCESS`, 5:08).
- `BotJobDetailsToolbarPlaywrightTest` now follows the current UI contract: retired block-level
  `excelExport.*` coverage is removed, deployed assets are checked for instruction-level
  `excelWrite.chooseDirectory`, exact Job Files controls are selected, Runtime Variable Values are
  confirmed with `KEEP`, selected blocks begin in `ONE` mode, and duplicate status surfaces are
  handled without weakening their content assertion.
- `MainDashboardAutoTestPlaywrightTest` now expects the approved runtime title `Main Bot Jobs`;
  its separate static `AR Web` manifest/title coverage remains intact.
- `VariablesIfFamilyDeletePlaywrightTest` now targets the exact `Variables` heading and the current
  `Bot Job ID 1` subtitle while retaining the complete IF-family deletion assertions.
- The automation catalog was regenerated to 2,409 rows / 2,373 code cases; its focused 2/2 check
  and all three repaired browser suites pass.
- The two skips are intentional environment assumptions, not regressions:
  `PerformDBEngineAccessTest` requires an available Access integration database, and
  `PreScanDumpComparisonTest` requires a Page Scanner diagnostic fixture generated externally.
- Test-generated `Config-4.2/TESTS.config` changes were restored exactly after verification.
- Code/test checkpoint `b6019e62` (`CODEX- align Java browser regressions with current UI`) is
  pushed to `origin/refactor/perform-actions-decomposition`.

## Current authoritative rerun — 2026-08-13 after `785a482c` / frontend `b783ad3`

This section supersedes all earlier aggregate totals. The complete Java, React and browser E2E
suites were rerun sequentially from the pushed commits.

### Current totals

| Layer | Result | Change from previous baseline |
|---|---:|---|
| Full Java (`mvn test`) | 1,466 total; 1,461 passed; 2 failed; 1 error; 2 skipped | Unsuccessful methods reduced from 33 to 3. All ACL, Page Mappings and five repaired Java groups passed in the complete run. |
| Full React/Jest | 172 suites: 154 passed, 18 failed; 920 tests: 866 passed, 54 failed | One suite and two tests were repaired by the ambiguous-parent change. |
| Browser E2E | 4 total; 1 passed; 3 failed | Same established accessibility and obsolete-style/title expectations. |

### Remaining Java-hosted browser checks

| Suite | Remaining | Current evidence and action |
|---|---:|---|
| `BotJobDetailsToolbarPlaywrightTest` | 1 failure | Obsolete architecture marker. The current source and production bundle use instruction-level `excelWrite.chooseDirectory`; the test still requires retired block-level `excelExport.chooseDirectory`. Update the test and its mocked operation to the current ExcelWrite contract. |
| `MainDashboardAutoTestPlaywrightTest` | 1 failure | Obsolete title assertion: approved UI sets `Main Bot Jobs`; the test expects `AR Web`. Update the assertion while retaining manifest/document-title coverage. |
| `VariablesIfFamilyDeletePlaywrightTest` | 1 error | Test locator uses a non-exact heading name `Variables`, which now also matches `Memory variables`. Use the exact level-1 `Variables` heading and retain the deletion behavior assertions. |

### Remaining React suites

The related `variablesBatchConnections.test.ts` suite is now green 6/6. The other 18 suite
groups and their prior classifications remain reproducible, with unchanged per-suite counts:

- Obsolete UI/payload expectations: `App.test.tsx`, `ActivationRequired.test.tsx`,
  `BlockHeader.test.tsx`, `AddVariableModal.test.tsx`, `VariablesPage.test.tsx`.
- Variables/runtime relationship semantics requiring one authoritative decision or production
  correction before assertions change: `executionRelationshipPreflight.test.ts`,
  `instructionMove.test.ts`, `instructionRelationshipGraph.test.ts`,
  `instructionRelationshipPolicy.test.ts`, `VariableExecutionLane.test.tsx`,
  `variablesWorkspace.contract.test.ts`.
- Current fixture/capability drift: `InstructionRelationshipDetails.test.tsx`,
  `GridItem.relationshipChips.test.tsx`, `GridItemComp.memoryParity.test.tsx`,
  `VariablesCommandBoard.test.tsx`, `VariablesConnectionsModal.test.tsx`.
- Lifecycle/safety review still required: `CommandEditorPage.test.tsx`,
  `ReconnectWebElement.test.tsx`.

### Remaining E2E checks

1. Duplicate `Page Scanner` regions remain a real accessibility defect (two regions, expected one).
2. OCR Config header test expects obsolete `rgb(11, 83, 148)`; approved UI is
   `rgb(29, 79, 145)`.
3. Main Dashboard test expects obsolete `AR Web`; approved heading is `Main Bot Jobs`.

### Verification notes

- The initial Java invocation used a two-minute command limit and was discarded when the command
  timed out. The authoritative rerun used a ten-minute limit and completed in 3:26.
- The clean sequential Java run passed `PageMappingsWorkspaceServiceTest` 22/22 and did not
  reproduce the prior managed-execution Windows ACL error 5.
- No production code was changed by this rerun. This checkpoint records test evidence only.

## Resolved checkpoint — five Java groups plus related React fix — 2026-08-13

- `VariableRelationshipService` now loads legacy `instruction.variable_id` slots when the
  directional slot table is absent, and treats the optional typed-command configuration table as
  empty on an older schema instead of failing the complete raw-facts response.
- Runtime-variable deletion removes authoritative slot rows and clears the still-supported legacy
  instruction field in the same transaction. Its fixture now installs graph/runtime/slot schema.
- Variables instruction copy runs against the current graph/runtime/slot schema and allocates
  owner-unique variable names (`Name Copy`, `Name Copy 2`, and so on) instead of violating the
  registered unique-name index.
- Page Mappings retention fixtures activate the exact authoritative Bot Job owner. Isolated
  Page Mappings service constructors receive an explicit owner-validator seam; the production
  singleton still uses the global active-owner registry.
- Related React connection planning no longer selects the first ambiguous parent. It returns
  `REVIEW_REQUIRED`, blocks its dependent variable review, and never invents a variable binding.
- Focused Java functional results: variable relationships 4/4, runtime variables 4/4,
  instruction copy 5/5, retention 5/5, Page Mappings service 22/22.
- Adjacent WebSocket lifecycle: 30/30. Automation catalog: 2/2 and regenerated to 2,409 rows /
  2,373 code cases. Related React planner: 6/6.
- Production React build completed with pre-existing warnings and was mirrored exactly into Java
  resources: 61 files, zero hash mismatches, zero count delta.
- A later combined Java invocation reproduced four `PageMappingsWorkspaceServiceTest` fixture
  setup errors from Windows `SetNamedSecurityInfo` error 5. They occur while creating private test
  artifacts, before the service assertions; the same suite passed 22/22 earlier in this checkpoint.
  Production ACL enforcement was not weakened, and the shared ACL execution-environment item
  remains separately open.

## Current authoritative rerun — 2026-08-13 after `4b7b412a`

This section supersedes every older aggregate count in this document. The older tables remain useful root-cause history, but the numbers below are the current reproducible baseline.

### Current totals

| Layer | Result | Current status |
|---|---:|---|
| Full Java (`mvn test`) | 1,466 total; 1,431 passed; 11 failed; 22 errors; 2 skipped | 33 unsuccessful methods across 9 suites |
| Focused deterministic Java remainder | 43 total; 14 passed; 8 failed; 21 errors | 29 persistent methods across 5 suites; `PageScannerTaskGateTest` passed 3/3 in isolation |
| Focused Java-hosted Playwright remainder | 3 total; 2 failed; 1 error | All 3 reproduce independently |
| Full React/Jest | 172 suites: 153 passed, 19 failed; 920 tests: 864 passed, 56 failed | Counts unchanged from the prior baseline |
| Browser E2E | 4 total; 1 passed; 3 failed | Same three established failures reproduced |

### Current Java remainder

| Suite | Unsuccessful | Current classification | Next authoritative action |
|---|---:|---|---|
| `PageScannerTaskGateTest` | 1 in full suite; 0 focused | Timing/order-sensitive test failure; the focused suite passes 3/3. | Audit latch scheduling and suite isolation before changing production serialization. |
| `VariableRelationshipServiceTest` | 3 | Persistent compatibility/contract defect: legacy fixtures do not produce the required raw facts/revision. | Trace and implement the missing owner-safe legacy relationship fallback, then retain the tests. |
| `BotJobRuntimeVariableServiceTest` | 4 | Current service contract, obsolete partial fixture: `instruction_graph_state` is missing. | Install the registered graph-state schema in the fixture. |
| `VariablesInstructionCopyTransactionTest` | 5 | Current service contract, obsolete partial fixture: `instruction_variable_slot` is missing. | Upgrade the fixture to the current slot schema and verify copy/rollback semantics. |
| `PageMappingsWorkspaceRetentionTest` | 5 | Fixture authorization drift: responses are rejected before retention fields exist because the authoritative Bot Job registry owner is not activated. | Activate the exact owner/epoch in the fixture; do not weaken production authorization. |
| `PageMappingsWorkspaceServiceTest` | 12 | Mixed fixture authorization drift: 9 owner/registry response failures plus 3 Memory lifecycle assertions. | Repair exact registry/epoch setup first, then reassess only the remaining Memory lifecycle assertions. |
| `BotJobDetailsToolbarPlaywrightTest` | 1 | Deployed frontend resources are stale and lack `excelExport.chooseDirectory`. | Build/mirror the current frontend assets, then rerun; no assertion change yet. |
| `MainDashboardAutoTestPlaywrightTest` | 1 | Obsolete expectation: test expects `AR Web`; approved UI renders `Main Bot Jobs`. | Update the assertion after asset synchronization. |
| `VariablesIfFamilyDeletePlaywrightTest` | 1 | Reproducible browser-fixture timeout waiting for `Bot Job 1`. | Synchronize assets, then trace fixture bootstrap/navigation if it still times out. |

The earlier Windows ACL failures are absent from this full rerun. Production ACL enforcement remains unchanged.

### Current frontend and E2E remainder

- React remains at 56 failures in 19 suites. The detailed React table below is still current; the highest-priority product defect remains ambiguous Variables parent selection guessing the first candidate instead of requiring review.
- Browser E2E reproduces:
  1. Duplicate `Page Scanner` regions — real accessibility defect.
  2. OCR header expected old blue `rgb(11, 83, 148)` but approved design is `rgb(29, 79, 145)` — obsolete test.
  3. Main Dashboard expects the obsolete `AR Web` heading — obsolete test.

### Recommended next repair batch

1. Fix the Variables relationship legacy fallback and ambiguous-parent fail-open behavior as one cross-layer semantic batch.
2. Upgrade runtime-variable and instruction-copy Java fixtures to registered schemas.
3. Repair Page Mappings owner/epoch fixtures, then isolate any genuine Memory lifecycle failures.
4. Build and mirror current frontend assets; rerun the three Java-hosted Playwright tests.
5. Fix the duplicate Page Scanner region and update the two obsolete E2E expectations.
6. Refresh obsolete React fixtures/labels after the shared variable semantics are authoritative.
7. Rerun full Java, React, and E2E suites and regenerate the automation catalog if test declarations change.

## Java failures

| Failing suite | Failed | Why it fails | Required action |
|---|---:|---|---|
| `ScannedPageIdentityTest` | 0 | Resolved: RFC 3986 dot-segment removal now preserves repeated slash segments. | Focused suite passes 11/11. |
| `PlaywrightBridgeTest` | 0 | Resolved: fixtures now exercise the current owner/page-scoped healing preparation and runtime action APIs. | Focused suite passes 5/5. |
| `AllJobDetailsDeleteTransactionTest` | 0 | Resolved: fixture now isolates snapshot health/PATH_DB and installs the authoritative snapshot schema. | Focused suite passes 3/3. |
| `AutomationTestCatalogServiceTest` | 0 | Resolved: `automation-tests.json` was regenerated from the current Java and React test trees. | Focused suite passes 2/2. |
| `BotJobDeleteTransactionTest` | 0 | Resolved as the same fixture root: snapshot health/PATH_DB and schema are isolated and current. | Broader affected suite passes 5/5. |
| `ComponentMemoryApplyServiceTest` | 0 | Resolved: snapshot test state is isolated and generated artifacts receive the same private ACL hardening required by capture verification. | Focused suite passes 36/36. |
| `ExecutionPreflightSnapshotRepositoryTest` | 0 | Resolved: current `instruction_variable_slot` remains authoritative; an old schema with only `instruction.variable_id` receives the promised compatibility fallback. | Focused suite passes 4/4, including current- and legacy-schema paths. |
| `ExecutionCommandSemanticsCharacterizationTest` | 0 | Resolved: characterization now matches the current contract—CK is variable-based and ExcelWrite is instruction/file-based, not Web Element based. | Focused suite passes 6/6. |
| `InstructionMoveTransactionTest` | 0 | Resolved: fixture uses `instruction_variable_slot` and `bot_job_variable_definition`; stale ownership assertions target the current producer relationship. | Focused suite passes 14/14. |
| `LocatorGeneratorServiceTest` | 0 | Resolved: exact attribute identity no longer depends on Jsoup CSS parsing; incompatible quoted selectors use a safe positional CSS fallback while retaining exact XPath. | Focused suite passes 14/14. |
| `PageMappingApplyResolverTest` | 10 | Windows private ACL returns error 5. | ACL root correction. |
| `PageScanSnapshotArtifactLifecycleTest` | 3 | Same ACL error 5. | ACL root correction. |
| `PageScanSnapshotFileSecurityLongPathTest` | 1 | Direct focused rerun reproduces `SetNamedSecurityInfo` error 5 on the temporary long path. | Diagnose test-directory ownership/ACL application; never disable production ACL enforcement. |
| `PageScanSnapshotLifecycleHardeningTest` | 4 | Same ACL root. | ACL root correction. |
| `PageScanSnapshotRetentionServiceTest` | 1 | Same ACL root. | ACL root correction. |
| `PageScanSnapshotStoreTest` | 5 | Three ACL errors; two assertions execute through the resulting failure path. | Fix ACL first, then reclassify the remaining two if they persist. |
| `VariableRelationshipServiceTest` | 3 | Source claims legacy `instruction.variable_id` fallback, but implementation only reads `instruction_variable_slot`. | Implement the missing backward-compatible fallback. Keep tests. |
| `BotJobRuntimeVariableServiceTest` | 4 | Fixture omits current `instruction_graph_state`. | Upgrade fixture/migration setup. |
| `VariablesInstructionCopyTransactionTest` | 5 | Fixture omits `instruction_variable_slot`. | Upgrade fixture/migration setup. |
| `PageMappingsWorkspaceRetentionTest` | 5 | Tests create a Page Mappings binding but do not activate the new authoritative global Bot Job registry owner. | Update fixtures; do not weaken production authorization. |
| `PageMappingsWorkspaceServiceTest` | 12 | Mixed: four ACL failures and several fixtures missing the current active Bot Job registry/epoch. | Correct ACL and activate the exact owner in fixtures; rerun before any product change. |

The Windows ACL problem accounts for a large part of the 51 Java errors. Live BancaStato capture ACL validation previously passed, so this currently points toward the automated temporary-directory environment or its ACL setup—not evidence that production security should be relaxed.

## React failures

| Failing suite | Failed | Classification | Required action |
|---|---:|---|---|
| `App.test.tsx` | 1 | Obsolete test | Replace untouched CRA “learn react” assertion with the current application route/shell. |
| `ActivationRequired.test.tsx` | 1 | Obsolete wording/flow | Test expects “Exit application”; current action is “Back to main dashboard.” Update test if current navigation is intended. |
| `BlockHeader.test.tsx` | 1 | Obsolete architecture | Test expects the old block-level Excel icon. ExcelWrite configuration was intentionally moved to the instruction level. |
| `executionRelationshipPreflight.test.ts` | 2 | Semantic inconsistency | Align SET/CK producer-consumer rules across Java and React, then update tests to that one contract. |
| `instructionMove.test.ts` | 1 | Semantic inconsistency | Test and current graph disagree about moving a consumer before GET. Resolve runtime-variable ordering authority first. |
| `instructionRelationshipGraph.test.ts` | 2 | Likely product defects | Owner/parent mismatch can become connected; SET/GET writer selection is inconsistent. Fix graph rules, then update expected projections. |
| `instructionRelationshipPolicy.test.ts` | 3 | Mixed | ExcelWrite/PDF/CSV expectations are outdated, but SET write/read behavior is inconsistent across layers. |
| `InstructionRelationshipDetails.test.tsx` | 6 | Fixture/graph drift | Current fixtures produce no authoritative relationship edges, leaving details blank. Refresh fixtures after graph semantics are corrected. |
| `CommandEditorPage.test.tsx` | 1 | Lifecycle contract needs review | A refusal arriving before backend binding no longer reaches the expected loaded editor state. Verify whether the refusal must be displayed before updating the test. |
| `GridItem.relationshipChips.test.tsx` | 4 | Obsolete capability fixtures | Tests use old `relationshipChipsV1` response shapes/epochs. Update to the current raw-facts/graph contract. |
| `GridItemComp.memoryParity.test.tsx` | 8 | Mixed fixture/contract drift | Old Memory snapshot payloads and delete/rollback expectations no longer match correlated revisions and current component routing. Update fixtures, then assess any remaining delete/rollback defects. |
| `ReconnectWebElement.test.tsx` | 3 | Safety behavior needs review | Current UI enables actions/auto-selection where tests require explicit selection and pending locking. Preserve explicit choice for ambiguous targets. |
| `AddVariableModal.test.tsx` | 1 | Obsolete payload | Test expects `{name}`; current API submits batch `{variables:[{name}]}`. |
| `variablesBatchConnections.test.ts` | 2 | Real fail-open defect | With multiple parent candidates, implementation automatically selects the first. It must return `REVIEW_REQUIRED`; variable selection must remain blocked until parent review. Unique-parent variable projection also needs correction. |
| `VariableExecutionLane.test.tsx` | 1 | Graph projection drift | Disconnected CK renders `INVALID LINK` instead of the expected reconnect-parent state. Resolve graph semantics first. |
| `VariablesCommandBoard.test.tsx` | 8 | Mixed | Several fixtures no longer yield rows/edges; bulk button names also changed from “ALL CONNECTIONS” to “CONNECTIONS.” Fix graph fixtures and update wording assertions. |
| `VariablesConnectionsModal.test.tsx` | 4 | Mixed | Frozen-scope fixtures produce zero connections; tests also expect the former default resolution mode. Rebuild fixtures from current graph and confirm the intended Same/Distinct default. |
| `VariablesPage.test.tsx` | 2 | Obsolete accessible names | Current buttons are “RESOLVE CONNECTIONS” and “RELEASE CONNECTIONS,” not the old “ALL” labels. |
| `variablesWorkspace.contract.test.ts` | 5 | Mixed | Capability fixtures were not updated when SET/raw facts were added; one remaining SET classification conflict is a real cross-layer semantic issue. |

The strongest React product defect is the ambiguous Variables connection behavior: it presently guesses the first candidate. That test is correct and production must be fixed.

## Browser E2E failures

| Failure | Actual functionality | Decision |
|---|---|---|
| Duplicate Page Scanner region | Both `FloatingWorkspaceFrame` and `DetachedPageShell` expose `aria-label="Page Scanner"`, producing two identically named regions. | Real accessibility defect. Keep one authoritative region and make the nested wrapper non-region. |
| OCR header color | Test expects old `rgb(11,83,148)`; current approved header is `rgb(29,79,145)`. | Update obsolete E2E expectation. |
| Main Dashboard heading | Test expects old “AR Web”; current heading is `Main Bot Jobs - Automation Test`. | Update obsolete E2E expectation. |

## Rerun evidence

Rerun coverage:

- The complete Java suite and every failing Surefire class were analyzed.
- Focused Java groups covered ACL, schema, Page Mappings authorization, Playwright bridge, command semantics, page identity, locator generation and variable relationships.
- The complete React suite was run twice.
- Focused React relationship/domain suites were rerun.
- Focused Variables batch/workspace suites were rerun.
- Seven Grid/Variables UI suites: 59 tests, 29 failures reproduced.
- Remaining App/Activation/Command Editor suites: 11 tests, 3 failures reproduced.
- Grid header/details suites: 14 tests, 7 failures reproduced.
- Browser E2E was run twice; the same three failures reproduced.

## Recommended repair order

1. Fix the Windows ACL test execution root; it masks roughly half the Java failures.
2. Fix the real fail-open Variables ambiguous-parent selection.
3. Align GET/SET/CK runtime-variable semantics across Java and React.
4. Implement legacy `variable_id` fallback and decide old-schema preflight compatibility.
5. Fix locator CSS escaping and page-identity normalization.
6. Correct Page Mappings/Playwright/schema fixtures.
7. Update obsolete UI and E2E expectations.
8. Regenerate the automation catalog.
9. Rerun focused suites, then full Java, React and E2E suites.

## Completion gates

- [x] Failures reproduced
- [x] Root groups identified
- [x] Product defects separated from obsolete tests
- [ ] Production fixes implemented
- [ ] Tests updated
- [ ] Full suites green
- [ ] Committed
- [ ] Pushed
- [ ] Deployed

## Resolved checkpoint — 2026-08-13

- Page identity preserves server-significant repeated slash segments while still removing RFC 3986 dot segments and one trailing slash.
- Playwright Bridge coverage now supplies the required Bot Job owner, active page and prepared runtime-healing contract.
- All-job and single-job deletion fixtures use isolated process snapshot state and the registered snapshot schema instead of partial legacy tables.
- Windows long-path private ACL verification passes under normal process permissions; the earlier error 5 was caused by the managed filesystem sandbox, so production ACL enforcement was not weakened.
- The generated automation catalog is synchronized with the current source trees.
- Exact requested suites: 21/21 passed.
- Directly affected broader suites: 18/18 passed.
- Windows extended-length ACL suite: 1/1 passed.

## Resolved checkpoint — next five Java groups — 2026-08-13

- Component Memory capture fixtures now isolate snapshot health/root state and apply production-equivalent private ACLs before verification.
- Execution preflight supports both the authoritative variable-slot schema and the documented legacy `instruction.variable_id` schema without mixing owners.
- Command semantics characterize CK and ExcelWrite according to the current variable/file architecture.
- Instruction movement fixtures and assertions use current variable-slot and producer-definition persistence.
- Locator generation preserves exact XPath literals for quote/backslash values and uses a safe positional CSS selector only when the CSS parser cannot represent the exact attribute selector.
- Requested five-suite batch: 74/74 passed.
- Adjacent preflight, graph-revision, command-codec and command-editor contract suites: 29/29 passed.
- Automation catalog consistency: 2/2 passed; catalog regenerated to 2,409 rows / 2,373 code cases.
