### Private drag-controller boundary

Visual parity does not authorize shared mutable drag state. Maintain three independent controllers:

1. Bot Job Details instruction/block drag;
2. Components instruction/block drag;
3. detached Memory List item/dependency-group drag.

They may reuse pure stateless ordering/graph helpers and DTO contracts only. Each controller owns its
sensor lifecycle, active IDs, indices, preview correlation, confirmation, optimistic snapshot, and
rollback. A page refresh/reconnect cannot cancel or complete another page's drag.

### Current delivery order and truth

- [x] Bot Job Details Memory Apply manually accepted by the user.
- [x] Component-generated relationships now normalize a legacy null parent-block on the clone only
  and scope the generated update to the destination Bot Job.
- [x] Complete focused automated `GridItemComp` parity, including connected parent/child moves,
  own-family no-op handling, block `+`, row `+`, and Component-only WebSocket routing.
- [ ] User runtime acceptance of the completed `GridItemComp` continuation remains pending.
- [x] Memory List backend grouped reorder/removal policy exists and rejects split dependency groups.
- [x] Memory List frontend drag is restored through private `useMemoryListDrag`; it uses stable
  item keys and moves a connected group as one contiguous unit.
- [x] Whole-block staging keeps independent dependency families separate and merges only
  overlapping parent/child families.
- [ ] User runtime acceptance of Memory List drag remains pending.
- [ ] After Components and Memory List are accepted, return to Bot Job Details for the deferred
  connected-group destination-index fix. Compute the destination index after removing the complete
  moving group so a same-block drop neither duplicates nor offsets the group.

## 2026-07-28 - Memory dependency selection migrated to React

The Memory List selection boundary is now WYSIWYG: `GridItem` and `GridItemComp` calculate the
complete group from the rows currently loaded in their shared React grid. Clicking row/block `+`
does not ask Java or SQL to choose additional instructions.

### React-owned pure graph rules

- [x] Smallest containing `IF ... ENDIF` span.
- [x] Smallest containing `LOOP` / `REFRESH_LOOP` anchor span.
- [x] Web-field root and dependent-child family.
- [x] Recursive ordinary `parentId` and child closure.
- [x] Variable declaration owner and every instruction using that variable.
- [x] `COMPONENT_COPY` recursively stages complete destination blocks referenced by either `GOTO`
  or `EXCEL GOTO`, preserving `parentId` and `parentBlockId`.
- [x] `BOT_JOB_COPY` does not pull external navigation destination blocks into Memory.
- [x] Selecting a destination row does not pull incoming cross-block `GOTO` / `EXCEL GOTO` callers.
- [x] Deterministic block/order/row ordering and structured all-or-nothing errors.
- [x] Whole Component block `+` uses the same indexed resolver with every visible row as a seed.
- [x] `GOTO` and `EXCEL GOTO` use `parentId`, `parentBlockId`, source `blockId`, and recursively
  discovered child/variable relationships; same-block navigation remains an ordinary family.
- [x] Bot Job `EXCEL GOTO` groups are resolved locally but cannot be staged for an illegal
  same-job duplicate; Components can still stage them for a destination job.
- [x] A capability response whose authoritative instruction IDs do not exactly match the rendered
  IDs fails Memory additions closed while keeping the last valid grid visible.

### Backend boundary

- [x] `instructionEditor.memoryCapabilitiesResponse` no longer emits Java-selected
  `canAddToMemory`, `memoryGroupKey`, `memoryGroupRows`, or `memoryGroupBlocks`.
- [x] The response exposes only raw variable ownership facts as
  `variableLinks: [{id, instructionId}]`, including null owners.
- [x] Move/delete permissions and `graphRevision` remain backend-authorized.
- [x] `graphRevision` includes deterministic `variable.id -> variable.instruction_id` ownership,
  so variable-topology changes invalidate stale Memory, move, delete, rollback, split, and Command
  Editor mutations.
- [x] Apply remains transactional persistence validation: Java verifies the exact React-submitted
  set is complete/current, then clones fresh instruction/variable/reference IDs and remaps parent,
  variable, `GOTO`, and `EXCEL GOTO` links. It does not silently add omitted Memory rows.

### Focused evidence

- [x] Pure TypeScript dependency suite: 22/22.
- [x] Shared GridItem/GridItemComp, Memory staging, and button feedback suites: 73/73.
- [x] Backend revision, raw-variable-link, transactional apply, and rollback suites: 47/47.
- [ ] User runtime acceptance with real Component block `#20 Check payment`, including Variables,
  `GOTO`, and `EXCEL GOTO`.
