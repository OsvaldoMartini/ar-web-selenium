# V2 parity full test matrix - 2026-08-14

Scope: complete Java, isolated Node V2, React unit, and React browser-E2E verification after
commits `32cd748b` and `630e1a0f`. No production code or test expectation was changed during this
matrix.

## Results

| Suite | Passed | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Node V2 (`npm test`) | 42 | 0 | 0 | 0 | PASS |
| Java (`mvn test`) | 1,471 | 1 | 0 | 2 | FAIL |
| React unit, reproducible rerun | 870 | 54 | 0 | 0 | FAIL |
| React browser E2E after pinned Chromium install | 1 | 3 | 0 | 0 | FAIL |

The first React run was 869 passed / 55 failed across 19 failing suites. The rerun was 870 passed /
54 failed across 18 failing suites. `excelWriteManager.test.ts` exceeded its five-second timeout only
in the first run and passed in the rerun, so it is a separate timing/flakiness issue.

## Java failure

`AutomationTestCatalogServiceTest.catalogContainsEveryBackendJunitDeclarationInTheCurrentCheckout`
reports 1,479 source declarations versus 1,471 packaged catalog entries. The generated
`automation-tests.json` catalog is eight declarations behind current source. This is a catalog
freshness failure, not a V2 browser/runtime assertion failure. The two intentional skips remain
`PerformDBEngineAccessTest` and `PreScanDumpComparisonTest`.

## Stable React unit failures

| Failing suite | Failed tests |
| --- | ---: |
| `App.test.tsx` | 1 |
| `ActivationRequired.test.tsx` | 1 |
| `BlockHeader.test.tsx` | 1 |
| `executionRelationshipPreflight.test.ts` | 2 |
| `instructionMove.test.ts` | 1 |
| `instructionRelationshipGraph.test.ts` | 2 |
| `instructionRelationshipPolicy.test.ts` | 3 |
| `InstructionRelationshipDetails.test.tsx` | 6 |
| `CommandEditorPage.test.tsx` | 1 |
| `GridItem.relationshipChips.test.tsx` | 4 |
| `GridItemComp.memoryParity.test.tsx` | 8 |
| `ReconnectWebElement.test.tsx` | 3 |
| `AddVariableModal.test.tsx` | 1 |
| `VariableExecutionLane.test.tsx` | 1 |
| `VariablesCommandBoard.test.tsx` | 8 |
| `VariablesConnectionsModal.test.tsx` | 4 |
| `VariablesPage.test.tsx` | 2 |
| `variablesWorkspace.contract.test.ts` | 5 |

The failures cluster in obsolete starter/design assertions, removed block-level Excel UI, stale
WebSocket/capability fixtures, and old relationship/variable semantics. In particular, tests still
treat SET as a runtime-value writer although the current execution contract consumes a variable to
write a Web Element; they also expect obsolete PDF/CSV element-target behavior. These suites require
an authoritative contract-by-contract review. Do not bulk-update assertions without proving the
current execution behavior.

## Browser E2E

The first E2E attempt was blocked before assertions because Playwright 1.61 Chromium v1228 was not
installed. `npm run test:e2e:install` installed the exact repository-pinned browser. The rerun then
produced one pass and three functional failures:

1. Page Scanner has two regions named `Page Scanner`; the test requires exactly one.
2. OCR Config header is `rgb(29, 79, 145)` while the test expects obsolete `rgb(11, 83, 148)`.
3. Main Dashboard no longer has the obsolete heading `AR Web` expected by the test.

## V2 parity coverage assessment

Covered:

- Java serializes bounded Bot Job browser arguments and rejects malformed arguments.
- Node start admission preserves approved arguments and rejects malformed/unbounded arguments.
- Isolated-run retention rebinds the browser handle to the replacement run ID.
- Safe logger emits only allowlisted diagnostic fields and a log-sink failure cannot change runtime
  behavior.
- Full Node action, cancellation, replay, isolation, retention, and close-browser tests pass.

Missing automated coverage:

1. No test intercepts the concrete Playwright `chromium.launch()` call and asserts the final
   de-duplicated `--start-maximized` argument list.
2. No test intercepts concrete `browser.newContext()` and asserts `viewport: null`,
   `serviceWorkers: 'allow'`, and `acceptDownloads: false` together.
3. No headed integration test proves actual `window.innerWidth/innerHeight` tracks the maximized
   native screen for V2.
4. No concrete browser-factory test proves launch/navigation/action/refresh/interrupt/close and
   unexpected-close log ordering, failure codes, and cleanup events.
5. No Java log-capture test asserts the complete reserve/start/action/recovery/Stop trace sequence.
6. The five-browser synthetic acceptance has not been rerun after this parity change, and no live
   same-Bot-Job V1-versus-V2 geometry/candidate comparison has been performed.

Recommended next focused test seam: inject a narrow Playwright launcher adapter into
`PlaywrightBrowserFactory`. A fake adapter can capture exact launch/context options and lifecycle
events without opening a bank page or exposing secrets. Retain one headed local acceptance for
native maximization because a unit mock cannot prove operating-system window geometry.

## Completion gates

- [x] Full Node suite run.
- [x] Full Java suite run.
- [x] Full React unit suite run twice; machine-readable rerun saved under `target`.
- [x] Browser E2E dependency installed and suite rerun.
- [ ] Java generated catalog refreshed and reverified.
- [ ] Stable React failures repaired or deliberately rebaselined after contract review.
- [ ] Three browser E2E failures repaired/rebaselined.
- [ ] Concrete Playwright launch/context/logging tests added.
- [ ] Five-browser parity acceptance rerun.
- [ ] Live V1/V2 same-owner comparison completed after restart.
