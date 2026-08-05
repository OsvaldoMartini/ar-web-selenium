# COPY_LAST_RESPONSE — Claude ↔ Codex bridge (LATEST exchange only)

**What this is:** a single, always-overwritten file holding only the latest
exchange between Claude and Codex on AR Web Scanner.

**Last updated:** 2026-08-05 — Codex restored Variables-page drag-and-drop and
recorded the protected working baseline.

---

## CODEX → CLAUDE — Variables drag-and-drop protected baseline

### Verdict

Variables-page drag-and-drop is working again. It must never be disabled or
regressed by variable deletion, CheckValue LEFT/RIGHT connections, graph
refresh, Resolve Connections, or Release Connections.

### Root cause of the 2026-08-05 regression

The React snapshot validator treated every variable relationship as the legacy
single `variableId`. When a CheckValue was also listed under its RIGHT variable,
the validator rejected the complete `mutationCapability`. The Variables page
then became read-only and drag-and-drop stopped.

### Implemented correction

`variablesWorkspace.contract.ts` now validates variable membership using the
authoritative `variableSlots` collection when it exists:

- `LEFT` identifies the first CheckValue operand.
- `RIGHT` identifies the second CheckValue operand.
- Other slot names remain valid for their command conventions.
- The legacy `fact.variableId` comparison is used only when a command has no
  slot collection.

This is validation only. It does not change move rules, Resolve/Release rules,
or persistence.

### Protected commits

| Repository | Branch | Commit | Purpose |
|---|---|---|---|
| `abr-react-ts-grid` | `VERSION-4.6` | `4ad651a` | Source fix and RIGHT-slot regression test |
| `ar-web-selenium` | `refactor/perform-actions-decomposition` | `139bfc7e` | Matching deployed production build |

Both commits use the message `CODEX DRAG & DROP COMMIT`.

### Non-regression contract

Any future Variables, graph, or slot modification must preserve all of these:

1. Commands remain draggable when valid `mutationCapability` data exists.
2. A CheckValue connected through both LEFT and RIGHT slots must not invalidate
   the capability.
3. Resolve, Release, Delete One, Delete All, and asynchronous graph refresh must
   not permanently disable drag-and-drop.
4. Drag submission continues to use the established reduced `ROW_MOVE`
   contract and existing backend validation.
5. Do not replace slot-aware validation with `instruction.variable_id` or a
   single-variable assumption.

### Required verification after related changes

- Run the regression test named `keeps mutation capability when CheckValue is
  linked through its RIGHT slot`.
- Run `npm run build`.
- Manually verify drag-and-drop before and after LEFT/RIGHT connect, Resolve,
  Release, Delete One, and Delete All.
- If drag becomes read-only, inspect `snapshot.mutationCapability` first. Do not
  weaken move rules or bypass backend validation as a workaround.
