## 13. Claude/Codex reconciliation record

Accepted from both:

- single-row movement as the default;
- explicit reconnect UX;
- React semantic planning;
- Java atomic persistence;
- strict realtime correlation;
- non-blocking variable diagnostics and typed runtime VOID;
- frozen Memory List `+`;
- durable variables.

Accepted with modification:

- variable-centric ownership applies to variable-capable commands, not every command;
- `parentId` is a compatibility projection only where the Engine still needs it;
- same-Block freedom comes before cross-Block freedom;
- conditional boundaries remain constrained in the first release;
- delete is selected/direct/full explicit graph, never positional span deletion.

Deferred pending audit:

- SET as a variable-source consumer;
- E/CK/PDF/CSV running without a Web Element target;
- variable-anchored LOOP;
- universal variable assignment to every command;
- live runtime value editing.

Rejected:

- silently choosing the row above as a new relation;
- persisting dangling IDs;
- deleting variables automatically with their owner;
- moving/deleting innocent positional body rows;
- hiding broken-state authoring without diagnostics/reconnect;
- sharing live drag controllers between Bot Job, Component, and Memory List pages.

## 14. Definition of done

The redesign is complete only when:

- [ ] one Bot Job instruction can move independently within and across Blocks;
- [ ] every broken required relation is visible and repairable;
- [ ] IF/LOOP positional bodies are never treated as delete ownership;
- [ ] variables survive owner deletion as legitimate memory;
- [ ] new Web Elements receive one variable atomically;
- [ ] delete selected/direct/full modes persist exactly what React confirmed;
- [ ] Test Run and Launch never refuse because of variable health on any entry path;
- [ ] missing producers become typed VOID, `VALUE("")` remains valid empty data, and VOID consumers
  bypass safely with visible diagnostics;
- [ ] Java v3 performs structural validation and exact persistence without semantic group
  expansion;
- [ ] Components have independent parity;
- [ ] Variables page edits use the same contracts and revisions;
- [ ] Memory List `+`, apply, fresh-ID copy, and reorder behavior are unchanged;
- [ ] database audits, constraints, backup, restore, and rollback are proven;
- [ ] focused and full regression suites pass;
- [ ] manual runtime acceptance passes on disposable Bot Job and Component copies;
- [ ] final source commits and deployed frontend bundle hashes are documented.

## 15. Immediate next task

Finish P4 typed VOID/diagnostic behavior and then the P5 shared-version boundary; do not advertise
or consume the prepared v3 capability yet:

1. preserve the current exactly-once, warning-only Test Run/Launch dispatch on every audited entry
   point; variable health must never be consulted as a permission gate;
2. replace the shared `"Not Variable defined"` runtime key with typed `VOID | VALUE` state and
   verify `VALUE("")` remains valid data;
3. make GET/SET producer failure leave VOID and make CK/PDF/CSV/Excel/COPY consumers bypass only
   their dependent operation with a bounded non-modal diagnostic;
4. split variable diagnostics from true structural start failures in Java/TypeScript result
   terminology and UX while retaining correlated transport compatibility;
5. inventory every remaining v2/legacy Bot Job graph writer and make each increment the same
   `instruction_graph_state` version in its own transaction, or retire it behind the v3 route;
6. add mixed-writer, rollback-failure, wrong-owner/epoch, idempotency, response/broadcast-order,
   and production-schema/dialect tests;
7. add the correlated server-owned v3 WebSocket route and authoritative post-commit publication;
8. advertise `rowMoveContractV3` only after the preceding checks pass, then activate P6
   same-Block valid-only movement;
9. stop for review before wiring P7 reconnect or P11 cross-Block draft movement.

The approved activation sequence is P4 diagnostics/VOID -> P5 v3 -> P6 same-Block single-row
movement -> P7 chip-to-modal reconnect and explicit disconnect/reconnect -> P11 exact cross-Block
single-row movement. Variable health remains warning-only throughout this sequence. Prepared P5/P7
modules stay inert until their prerequisite phase is accepted. Animated relationship arrows remain
deferred.
