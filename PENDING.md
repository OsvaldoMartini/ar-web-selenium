# Pending test-failure investigation

The failures are not all obsolete tests. The reruns show three categories:

- Real product defects that require code fixes.
- Valid new behavior with obsolete tests/fixtures.
- Shared infrastructure/test-environment failures masking functionality.

No code was modified during this investigation.

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
