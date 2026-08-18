# BotJob vs Bank/Organization (Component) context — the map that governs both grids

The scanner has **two distinct authoring contexts**, surfaced by two React grids that look alike but
bind different tables, keys, and scopes. Confused, they corrupt data. Keep this map in mind for EVERY
frontend + backend change touching either grid.

## The two contexts

| Aspect | **BotJob context** | **Bank/Organization (Component) context** |
|---|---|---|
| Grid component | `GridItem.tsx` | `GridItemComp.tsx` |
| WS session id | `botJobTasks` | `componentTasks` |
| Instruction table | `instruction` | `component_instruction` |
| Block table | `block` | `component_block` |
| Variable table | `variable` | `component_variable` |
| **Scoping key (SQL WHERE / `whereId`)** | **`bot_job_id`** | **`home_banking_id`** |
| Refresh op (backend→FE) | `UPDATE_INSTRUCTIONS` / `updateInstructions` | `COMPONENTS_UPDATE` / `componentsUpdate` |
| FE DTO | `BlockLoopInstructionLoadDTO` (prop `data`) | `ComponentsInstructionsDTO` (prop `dataComp`) |
| `botJobId` | real BotJob id | **synthetic / placeholder** — carried in payloads but NEVER the WHERE key |
| Scope | one BotJob | a whole bank/company; **BotJob-agnostic**, reusable by any BotJob of the same company |

### What "BotJob-agnostic org scope" means
A component authored/scanned for, say, all of *Banca Stato*'s pages lives under `home_banking_id`, not any
one `bot_job_id`. Any BotJob of that company can view/inject those components. That is why GridItemComp
keeps a **synthetic BotJobId** for the shared UI/context — the natural key is the bank, not a job.

## Where the switch happens (backend — authoritative)

`SimpleWebSocketServer` derives table set + `whereId` purely from the session
(`isBotJobInstructionWorkspaceSession` vs `isComponentInstructionWorkspaceSession`, ~line 4348):

```
botJobTasks   → instruction / block / variable,            whereId = splitDTO.getBotJobId(),      refresh UPDATE_INSTRUCTIONS
componentTasks→ component_instruction / component_block /   whereId = splitDTO.getHomeBankingId(), refresh COMPONENTS_UPDATE
                component_variable
```

`PerformDataBase.updateSwiftBlockOrderNumber(tableName, whereId, listBlocks)` (~line 848) mirrors it:
`WHERE ... AND (bot_job_id | home_banking_id) = ?` — `block`→`bot_job_id`, `component_block`→`home_banking_id`,
and per-block `homeBankingId` is injected by `mapToBlockLoad(homeBankingId, updatedBlocks)` from the
message-level id (the FE `updatedBlocks` entries do NOT need to carry it).

**Implication:** the FE only has to send the correct **`sessionId`** (+ the usual `botJobId`/`botJobName`/
`homeBankingId` in the message). The backend picks the table and the WHERE key. So parameterizing a shared
FE hook by a single `targetSessionId` is sufficient for BLOCK_MOVE / ROW_MOVE / status / delete / etc.

## Cross-context edge: `COMPONENT_INJECT`
GridItemComp's `handleComponentInjection` sends `COMPONENT_INJECT` with `sessionId: 'botJobTasks'` — it
deliberately pushes a component's block **into the currently open BotJob** (org → botjob). This is the one
place the emitting grid addresses the OTHER session; it must stay a distinct, explicitly-addressed target,
never rewritten by a blanket `targetSessionId`.

## Rules for changes (both FE and BE)
1. Never assume `bot_job_id`. In the Component context the scope is `home_banking_id`.
2. A shared FE hook must route by `targetSessionId` (`'botJobTasks' | 'componentTasks'`); the backend does
   the rest. Don't hardcode `'botJobTasks'` in anything GridItemComp will reuse (e.g. `useBlockReorder`
   currently hardcodes it — parameterize before sharing).
3. Refresh verbs differ (`updateInstructions` vs `componentsUpdate`) — same purpose, never conflate.
4. Command editor differs by model (`commandEditor.workspaceOpen` vs `commandEditor.apply`/`insertElseIf`).
5. `botJobId` in the Component context is synthetic — safe to send, never trust as a scope key.

## FUTURE roadmap (deferred — do NOT start without an explicit plan)
Because a component scanned for an entire bank produces **many `scanned_element` rows**, a global Component
view over a company will accumulate large numbers of components + elements. The planned next big effort:
**new tables that read the component tables and synchronize with `scanned_element`** (component ↔
scanned_element sync, dedup/reuse across BotJobs of the same company). This is a separate, large roadmap —
capture requirements first. For now: keep the FE/BE correct and always honor the BotJob vs Bank/Org split.
