# P3 Relationship Details and Read-Only Chips

Date: 2026-07-29
Status: complete
Roadmap: `ROADMAP_VARIABLE_CENTRIC_INSTRUCTION_GRAPH_2026_07_29.md`, Phase P3

## Outcome

P3 makes broken instruction relationships visible without hiding or collapsing instruction rows.
The Bot Job grid now derives relationship health from the P2 React/TypeScript classifier and
renders read-only repair indicators. P3 introduces no reconnect action, mutation request, or
database write.

Frontend source:

- repository: `abr-react-ts-grid`
- branch: `VERSION-4.6`
- commit: `7e46da4` (`feat: show instruction relationship health`)

## Frontend implementation

- `InstructionRelationshipDetails.tsx`
  - replaces the legacy local `renderOperations`;
  - preserves the existing colored details for valid rows;
  - always renders the details column, including missing, late, or cross-Block parents;
  - renders the P2 states as noninteractive spans;
  - keeps `CONNECTED` silent and supports `MEMORY_ONLY`, reconnect, repair, order, saving, and
    refusal presentation.
- `instructionRelationshipFacts.ts`
  - converts only one exact Bot Job owner and its rendered rows, Blocks, and variable links;
  - rejects malformed, duplicate, or cross-owner facts instead of inventing relationships;
  - performs no database, WebSocket, or persistence operation.
- `useInstructionGrid.ts`
  - builds one memoized relationship graph for the current synchronized snapshot;
  - projects instruction-source edges by instruction identity;
  - joins variable-owner edges through `variableId` without numeric entity-ID collisions.
- `useGridData.ts`
  - consumes additive variable types and the server capability;
  - activates P3 only for an exact correlated positive workspace epoch and graph revision;
  - keeps legacy and stale responses fail-closed.
- `index.tsx`
  - passes the authoritative detached Bot Job workspace epoch to the grid.

`GridItemComp` continues to share the canonical grid renderer, but relationship chips are disabled
for Components until P12. Existing valid Component details remain unchanged.

## Backend read contract

- Bot Job capabilities advertise `relationshipChipsV1=true`.
- Component capabilities advertise `relationshipChipsV1=false`.
- Read-only variable facts include nullable variable `type`.
- Only a successfully authorized request can echo the server-canonical `workspaceEpoch`.
- Authorization failures never promote a client-supplied epoch.
- No mutation or persistence service was added or changed.

## Verification

Focused frontend results:

- relationship policy, classifier, facts adapter, and renderer: 58 tests passed;
- capability/epoch/Components integration: 4 tests passed;
- existing Bot Job drag/Memory and Components parity regressions: 29 tests passed.

Total focused frontend tests: 91 passed.

Focused backend results:

- `CommandEditorServiceVariableLinksTest`
- `CommandEditorServiceWorkspaceCapabilitiesTest`
- `SimpleWebSocketServerCommandEditorCorrelationTest`

Total focused backend tests: 7 passed, with zero failures, errors, or skips.

`npm run build` completed successfully. Existing project-wide ESLint and Create React App warnings
remain; P3 introduced no build failure.

Deployment verification:

- frontend build copied to `ar-web-selenium/src/main/resources/build`;
- source files: 45;
- deployed files: 45;
- content mismatches: 0;
- deployed main JavaScript: `static/js/main.e244ecb1.js`;
- SHA-256: `CCAE9D9825AC411B0505720FD4D34F6E4773C649BDEA9EF1483D617AE730C9AD`.

## Rollback

- Disable `relationshipChipsV1` to stop the new Bot Job indicators.
- Revert the P3 frontend commit to restore the previous renderer.
- The additive response fields require no database or protocol rollback.

## P4 boundary

P4 adds execution preflight diagnostics and gating. It must reuse the P2 graph, bind validation to
the exact execution scope and authoritative revision, and begin in shadow/warn mode before any
mandatory execution refusal.
