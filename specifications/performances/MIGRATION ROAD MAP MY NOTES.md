 Current Roadmap Status

  ARCHIVED 2026-07-11: every item below has been implemented and verified in
  `MIGRATION_TRACKER_2026-07-11.md`. Keep this section as historical notes only;
  do not use it as the active remaining-work list.

 Remaining Work

  1. ELSEIF branch deletion
      - Currently denied safely.
      - Implement deletion of the selected ELSEIF boundary plus only its branch instructions.
      - Preserve surrounding IF/ELSE/ENDIF grammar.

  2. Loop-group deletion
      - LOOP/REFRESH_LOOP deletion is currently denied.
      - Add explicit deletion for the loop boundary, parent relationship, and affected span.
      - Show exact backend impact before confirmation.

  3. Database transactions
      - Split, IF-family deletion, row movement, and variable rewrites still use multiple database calls.
      - Wrap each logical mutation in one transaction with rollback on partial failure.

  4. Move confirmation behavior
      - Memory Apply waits for backend confirmation.
      - Normal drag and arrow moves still update React optimistically.
      - Make all moves wait for instructionEditor.rowMoveResponse or restore authoritative state on refusal.

  5. Delete impact detail
      - React now shows the actual cascade count.
      - Backend should also return affected row names/IDs before confirmation.
      - This is particularly important for IF-family and future loop/ELSEIF group deletion.

  6. Block deletion safety
      - Block deletion still needs:
          - Graph revision validation
          - Request-ID idempotency
          - Dependency impact analysis
          - Structured React result responses
          - Removal of any remaining Java error dialogs

  7. Variable transaction safety
      - Variable update and dependent command rewrites must be atomic.
      - A rewrite failure must roll back the variable change.

  8. Variable type compatibility
      - Java should define which commands accept String or Numeric variables.
      - React should list only compatible variables.
      - Crafted requests must be revalidated.

  9. Command codec completion
      - Add structured warnings for malformed historical operations.
      - Verify aliases and every command family round-trip correctly.

  10. JavaFX route audit

  - Confirm no active command, variable, delete, move, memory, or split path opens JavaFX.
  - Legacy routes may remain in source but must be unreachable from React.

  11. Automated tests

  - Conditional grammar tests.
  - Move and loop relationship tests.
  - Delete impact tests.
  - Revision and idempotency tests.
  - Command codec round-trip tests.
  - Playwright React-only workflow tests.

  The highest-priority next step is database transaction safety for deletion and split, because those operations can currently partially succeed if a later database call fails.


  The core safety work is substantially implemented: backend capabilities, stale revision rejection, request idempotency, conditional/loop validation, move/delete result handling, deletion impact counts,
  atomic graph deletion, and atomic block splitting.

  Remaining Work, In Priority Order

  1. Complete atomic move persistence
      - Confirm every row move and cross-block group move uses one transaction.
      - Verify instruction order normalization and parent relationships together.
      - Add rollback coverage for partial database failures.

  2. Finish split consolidation
      - Move all split legality into the graph service.
      - Add previewSplit capability and affected-row response.
      - Remove any remaining silent “last row becomes previous row” correction.
      - Add pure validator tests for nested IF and loop split boundaries.

  3. Shared React drag controller
      - Extract duplicated logic from GridItem.tsx and GridItemComp.tsx.
      - Create useInstructionDrag.ts.
      - Use backend capabilities for valid drop zones.
      - Preserve scroll and expanded blocks after rejection/refresh.

  4. Improved drag interface
      - Add dedicated drag handles.
      - Highlight valid and invalid destinations.
      - Display backend rejection reasons as tooltips.
      - Add group-move preview and keyboard movement.

  5. Complete command capability engine
      - Replace remaining static React command rules with Java metadata.
      - Finish typed schemas for SET, GET, checks, GOTO, loops, waits, and independent commands.
      - Default unknown command/row combinations to denied.

  6. Command codec parity
      - Port all serialization and parsing behavior from ARNewCommandPane.
      - Add round-trip fixtures using existing database command formats.
      - Ensure React does not construct legacy operation strings.

  7. Variable migration parity
      - Complete variable creation/editing/reference selection in React.
      - Implement usage lookup and dependent-operation rewrites.
      - Make rename and type changes transactional.
      - Ensure bot-job and component-grid parity.
	  
  8. Remove remaining JavaFX command routes
      - Find callers of INSERT_BEFORE, INSERT_AFTER, INSERT_NEW, and EDIT_OPERATION.
      - Route all normal actions through commandEditor.apply.
      - Remove “Insert empty step” paths that still open the legacy pane.

  9. Remove legacy JavaFX panes
      - Only after end-to-end parity:
      - Remove ARNewCommandPane and ARNewCommandScene.
      - Remove ARElementValuePane and ARElementValueScene.
      - Clean up JCEF-to-JavaFX forwarding references.

  10. End-to-end verification

  - SQLite tests using bot job 19, organization 2.
  - Create blocks and add scanned instructions from Memory List.
  - Test IF/ELSEIF/ELSE/ENDIF, loops, variables, deletion, movement, and splitting.
  - Verify refreshed database state, not only optimistic React state.

  The immediate next implementation should be split validator tests and previewSplit, followed by the shared React drag controller.
  
  
  
• Added focused validator unit tests.

  Coverage includes:

  - Valid nested IF families.
  - Multiple ELSEIF branches.
  - ELSEIF after ELSE rejection.
  - Wrong conditional parent rejection.
  - Missing ENDIF rejection.
  - Valid ordinary row swaps.
  - Conditional boundary movement rejection.
  - Loop-parent movement rejection.
  - Parent-linked command cross-block rejection.

  No Java or Maven tests were executed per your instruction. Static checks passed.
