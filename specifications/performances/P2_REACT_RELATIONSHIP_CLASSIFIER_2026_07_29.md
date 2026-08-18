# P2 React Relationship Classifier

Date: 2026-07-29  
Status: complete  
Roadmap: `ROADMAP_VARIABLE_CENTRIC_INSTRUCTION_GRAPH_2026_07_29.md`, Phase P2

## Outcome

P2 introduces a deterministic, persistence-free React/TypeScript domain model for instruction,
Block, and variable relationships. It changes no rendering, drag/drop, delete, Memory List,
WebSocket, Java, or database behavior.

Frontend source commit:

- repository: `abr-react-ts-grid`
- branch: `VERSION-4.6`
- commit: `78785e6` (`feat: add variable relationship classifier`)

## Production modules

- `instructionRelationshipPolicy.ts`
  - canonical action aliases;
  - explicit relationship roles;
  - GET/SET/E/CK/PDF/CSV variable semantics;
  - IF/ELSEIF/ELSE/ENDIF, LOOP/REFRESH_LOOP, and GOTO/EXCEL GOTO requirements.
- `instructionRelationshipGraph.ts`
  - strict normalized nullable relationship facts;
  - exact compound-owner filtering;
  - typed relationship edges and repair states;
  - deterministic candidate and issue ordering;
  - no input mutation or external access.

`instructionDependency.ts` now delegates only canonical action normalization to the policy module.
Its existing dependency, Memory List, drag, and delete behavior remains unchanged.

## Locked semantics

- GET produces and writes a runtime variable value.
- SET performs literal assignment and also writes the runtime variable value.
- E and CK require a preceding active GET or SET for the same variable.
- PDF CHECK and CSV CHECK are output-validation commands; P2 does not invent a GET-order
  dependency for them.
- Owned GET/SET variable bindings require the variable owner and command element parent to agree.
  Ownerless compatible variables remain valid durable memory.
- A missing element/LOOP target uses reconnect state.
- An existing but late element/LOOP target uses `FIX_ORDER`.
- LOOP positional scope and conditional positional scope are informational edges only; they do not
  become ownership or deletion cascades.
- Bot Job ownership is `{workspaceKind, homeBankingId, botJobId}`.
- Component ownership is `{workspaceKind, homeBankingId}` under the current Component table model.
- The classifier infers no scanned/authored provenance.

## Verification

Focused frontend results:

- new policy and classifier: 44 tests passed;
- existing instruction dependency and Variables graph: 34 tests passed;
- existing Memory List groups and Components parity: 28 tests passed;
- existing instruction-memory hook: 16 tests passed.

Total focused tests: 122 passed.

`npm run build` completed successfully. Existing project-wide ESLint warnings remain and are outside
P2; the new P2 files introduced no build failure.

Deployment verification:

- frontend build copied to `ar-web-selenium/src/main/resources/build`;
- source files: 45;
- deployed files: 45;
- content mismatches: 0;
- deployed main JavaScript:
  `static/js/main.e76b365a.js`;
- SHA-256:
  `C96C1C23F4AF19C8B21381932F3A4E5687470DD3355BDB218918CDF9183A059A`.

## P3 boundary

P3 must migrate visible relationship diagnostics to this classifier. In particular, the legacy
`variablesGraph.ts` still represents the pre-P1 producer/consumer view for the existing UI; it is
deliberately unchanged in P2 because this phase permits no rendering change.

P3 must preserve all rows and the last valid grid when a relationship is broken. Repair chips are
read-only in P3 and introduce no persistence request.
