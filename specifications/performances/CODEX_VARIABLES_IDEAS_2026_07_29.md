Your idea is good: drag/drop should move exactly one row, while relationships become independently repairable. The current + Memory List behavior can remain untouched.

  The key safety rule: do not silently assign the row above as the new parent or variable. Instead, offer it explicitly when compatible.

  ## Proposed behavior

  Drag one row
  → preserve links that remain valid
  → identify broken relationships
  → offer Move Only or Move + Reconnect
  → persist layout and explicit relationship changes atomically
  → broadcast grid and variable updates in real time

  ### Relationship display

  Replace the current details only when a link is broken:

  - Valid: Jump To Parent (917) Pagina iniziale
  - Missing/invalid parent: RECONNECT PARENT
  - LOOP anchor problem: RECONNECT LOOP
  - Variable problem: RECONNECT VARIABLE
  - Consumer before GET: MOVE AFTER GET
  - IF-family problem: REPAIR CONDITIONAL
  - Missing GOTO target: RECONNECT BLOCK
  - While saving: SAVING…

  Currently, D:/Projects/AllinWeb/abr-react-ts-grid/src/components/GridItem.tsx:227 returns null for several invalid relationships, which makes the information disappear. It should eventually delegate this
  display to a separate relationship component.

  ### Intelligent drag targets

  I recommend two distinct drop behaviors:

  - Drop on the gap between rows: move only that instruction.
  - Drop on a compatible row’s Attach area: move and propose reconnection to that row.

  For example:

  > Move LOOP #918 after #917 and reconnect it to (917) Pagina iniziale?

  Buttons:

  - MOVE + RECONNECT
  - MOVE ONLY
  - CANCEL

  This is safer than automatically selecting whichever row happens to be above the drop position.

  ## Relationship rules

   Instruction                                 Relationship
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   GET, SET, E, CK, PDF/CSV CHECK              Web Field parent plus variable
  ──────────────────────────────────────────  ────────────────────────────────────────────────────────────────────
   LOOP, REFRESH_LOOP                          Web Field anchor; rows between anchor and LOOP are positional body
  ──────────────────────────────────────────  ────────────────────────────────────────────────────────────────────
   ELSEIF, ELSE, ENDIF                         IF root
  ──────────────────────────────────────────  ────────────────────────────────────────────────────────────────────
   GOTO, EXCEL GOTO                            Destination Block
  ──────────────────────────────────────────  ────────────────────────────────────────────────────────────────────
   Wait, Pause, Screenshot, ordinary fields    Independently movable
  ──────────────────────────────────────────  ────────────────────────────────────────────────────────────────────
   Variable consumer                           Must execute after a compatible GET producer

  Important: SET is currently treated as a literal assignment, not a GET-style producer. This should not silently change during the drag refactor.

  All candidates must be restricted to the exact owner:

  - same homeBankingId;
  - same botJobId;
  - same Component owner when operating in Components;
  - never resolve relationships by numeric ID alone.

  ## Delete behavior

  The delete modal should offer:

  1. DELETE SELECTED ONLY
  2. DELETE + DIRECT ATTACHMENTS
  3. Advanced: DELETE FULL LINKED GRAPH
  4. CANCEL

  Rules:

  - Never include instructions merely because they are positioned between IF/ENDIF or parent/LOOP.
  - “Direct attachments” means explicit parentId, variable, conditional-root, or loop-anchor links.
  - “Full linked graph” means transitive explicit relationships only.
  - Show exactly which surviving rows will become RECONNECT.

  Examples:

  - Delete LOOP only: parent and positional body remain.
  - Delete LOOP parent only: LOOP remains with RECONNECT LOOP.
  - Delete GET only: consumers remain but show MISSING PRODUCER.
  - Delete IF only: ELSE/ENDIF remain but show REPAIR CONDITIONAL.
  - Delete GOTO: destination Block is never deleted.

  Current deletion has only one cascade policy in D:/Projects/AllinWeb/abr-react-ts-grid/src/components/bot-job-details/grid/domain/instructionDelete.ts:66.

  ## Variables require special care

  Variable semantics are already calculated in React by D:/Projects/AllinWeb/abr-react-ts-grid/src/components/variablesGraph.ts:181. That is a strong foundation for real-time repair.

  When moving GET under another Web Field, ask whether to:

  - keep the existing variable and reconnect only the parent;
  - select a variable owned by the new Web Field;
  - transfer variable ownership, after showing every affected consumer;
  - create a new variable.

  When deleting a variable-owning Web Field, the current backend deletes its variable rows. For selected-only deletion, the transaction should instead detach the variable owner first—set
  variable.instruction_id = NULL—then delete the instruction. The variable and consumers survive and the Variables page displays RECONNECT OWNER.

  ## Required persistence contract

  The existing version-2 move cannot support this. src/main/java/com/allinweb/ch/facade/InstructionMoveTransaction.java:20 refuses any submitted parentId different from the stored value.

  A version-3 command should contain:

  {
    type: "ROW_MOVE",
    rowMoveLayoutVersion: 3,
    graphRevision,
    owner: { workspaceKind, homeBankingId, botJobId },
    updatedRows: [...],
    relationshipPatches: [
      {
        instructionId,
        relationKind,
        expectedTargetId,
        newTargetId
      }
    ],
    variableOwnerPatches: [
      {
        variableId,
        expectedInstructionId,
        newInstructionId
      }
    ]
  }

  React decides exactly what moves and which links change. Java must only:

  - validate owner scope, revision, IDs and expected old values;
  - persist the exact submitted layout and patches in one transaction;
  - reload and verify the committed graph;
  - broadcast instructions, variables and the new revision in real time.

  Java should not infer connected groups.

  ## Critical execution protection

  Free editing means the database may temporarily contain unresolved relationships. Therefore, Test Run and Launch must refuse execution when an active instruction needs reconnection.

  I would preserve the user’s Active flag—not silently deactivate rows—but display:

  > Cannot execute: 3 active instructions require reconnection.

  ## Recommended implementation order

  1. Add the React relationship classifier and RECONNECT chips without changing drag behavior.
  2. Implement free same-Block single-row drag with the version-3 transaction.
  3. Add the searchable reconnect dialog and real-time variable refresh.
  4. Add the three delete modes.
  5. Add cross-Block free movement.
  6. Apply the same behavior to Components using its own independent persistence pipeline.
  7. Keep the existing + Memory List logic frozen.

  No files were modified, and no tests or builds were run during this investigation.