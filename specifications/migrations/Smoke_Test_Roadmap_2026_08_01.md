# Variable Flow Smoke Test Roadmap

Date: 2026-08-01  
Status: planned; investigation complete; implementation not started  
Primary surface: `REVIEW CONNECTIONS` page  
Initial pilot: Home Banking 2, Bot Job 32

## 1. Purpose

Add a right-side panel named **SMOKE TESTS** to the Review Connections page. The simulator will
execute the currently visible Bot Job instruction flow without opening or controlling a real Web
page. Its purpose is to validate command order, relationships, control flow, and durable runtime
variable behavior before a Playwright execution.

This is a flow simulator, not a browser test. It must never click, type into, or read a real page.

## 2. What the current Variable Flow means

The existing flow is:

```text
Declaration Web Field -> GET/SET producers -> Variable -> Readers/checks
```

For the observed variable `#3 asdaadasd`:

- **Owner missing** means the variable definition has no valid declaration Web Element owner.
- The long producer list contains every GET/SET currently bound to variable ID 3.
- Repeated labels such as `#3` or `#6` are instruction order numbers from different Blocks; they
  are not duplicate instruction IDs.
- **Configured VALUE: EMPTY** means the configured raw value is a legitimate empty string.
- **Runtime VOID: NO_PRODUCER_YET** means no successful runtime or manual write has produced a
  value yet.
- Readers/checks are commands such as ExcelWrite, CheckValue, and CSV CHECK that consume variable
  ID 3.

When several GET commands write the same variable, the latest successful producer that executes
before a reader supplies the value observed by that reader. The flow therefore exposes a real
data-quality issue: unrelated labels such as `$ORDER NUMBER` and `$SALDO` may currently point to
the same variable ID.

## 3. User interface

Create a fixed right-side `SMOKE TESTS` panel containing:

- **RUN SMOKE TEST**;
- **STOP**;
- the frozen execution scope;
- current instruction and Block;
- a vertical, timestamped step log;
- runtime-variable changes;
- final counts for passed, bypassed, warning, and failed steps.

The run scope follows the synchronized Block filter:

- empty Block filter: all visible Blocks and instructions;
- selected Block: only instructions visible in that Block;
- changing filters after the run starts does not mutate the running scope.

The panel must remain usable with a vertical scrollbar and must not resize or hide the existing
review graph.

## 4. Execution model

At start, create an immutable run plan containing:

- Home Banking and Bot Job IDs;
- graph revision;
- selected Block scope;
- ordered visible instructions;
- relationship facts;
- variable definitions and committed runtime values;
- configured LOOP counts, conditional operands, and GOTO targets.

Execute one instruction at a time and append one result to the log. If the authoritative graph
revision changes during a run, stop with a stale-plan diagnostic instead of continuing against a
different graph.

## 5. Simulated Web Element memory

Maintain a run-local map keyed by Web Element instruction ID. It represents page state without a
browser.

```text
Click       records a successful simulated click
Input       writes deterministic text such as TEST_1 to its simulated Web Element
GET         reads exact raw text from its connected simulated Web Element
SET         writes the connected variable's exact raw text to its simulated Web Element
```

The deterministic Input sequence makes runs repeatable: `TEST_1`, `TEST_2`, and so on. A future
extension may allow the client to edit these values before running.

## 6. Runtime-variable rules

Runtime variables are real durable Bot Job runtime values, even though Web Elements are simulated.

- GET persists the exact simulated Web Element text into its connected variable.
- SET reads the current connected variable and writes it to simulated page memory.
- ExcelWrite reads only its connected variable and logs the simulated Excel/CSV destination write.
- Check commands evaluate the current variable against their typed operand.
- `VALUE("")` is a legitimate empty value.
- `VOID` means no usable producer/value exists.
- VOID bypasses only the dependent operation and never stops the complete smoke run.
- Successful writes persist through the normal durable runtime-value transaction and realtime
  publication path.
- The runtime update source is `SMOKE_TEST`.

Starting a smoke test defaults to **KEEP** existing values. Resetting values must be an explicit
client action; starting or stopping a run must not reset them implicitly.

## 7. Command behavior

### 7.1 Basic actions

- Click, Output, Refresh, Pause, and Wait produce deterministic log results.
- Wait may report the configured duration without imposing a long real delay.
- Input writes a deterministic value into simulated Web Element memory.

### 7.2 GET and SET

- GET requires a connected Web Element and variable.
- GET performs `simulated Web Element -> durable runtime variable`.
- SET requires a connected variable and writable Web Element.
- SET performs `durable runtime variable -> simulated Web Element`.

### 7.3 ExcelWrite and external outputs

- ExcelWrite requires a variable, not a Web Element parent.
- It reads the exact runtime value and logs the configured output key/column.
- It does not write a real file in the first smoke-test implementation.

### 7.4 Checks

- CheckValue, CSV CHECK, and PDF CHECK use typed operators and operands.
- The engine must preserve exact raw text and apply conversion only in an explicit comparison
  adapter.
- A failed check is logged and the run continues unless a future explicit stop-on-failure policy is
  selected.

### 7.5 LOOP and conditional flow

- LOOP uses its real configured count and anchor relationship.
- The engine maintains an explicit loop counter and program counter.
- IF/ELSE/ELSEIF/ENDIF evaluate typed operands and choose the correct branch.
- A safety ceiling limits maximum executed steps and loop iterations.

### 7.6 GOTO

- GOTO and EXCEL GOTO use their connected Block target.
- GOTO to the containing Block remains invalid.
- Cycle detection and a maximum-step ceiling prevent infinite simulations.

## 8. Diagnostics and continuation policy

The simulator reports, but normally does not block on:

- missing parent;
- missing variable;
- VOID runtime value;
- missing output configuration;
- failed check;
- disconnected reader;
- ambiguous legacy operation label.

It stops only when continuing would make the simulator structurally unsafe, such as an invalid
program counter, an unrecoverable conditional graph, a stale graph revision, explicit STOP, or the
safety ceiling.

## 9. Separation of concerns

React/TypeScript owns:

- scope selection and frozen run-plan construction;
- the pure simulation state machine;
- simulated Web Element memory;
- command dispatch and control-flow decisions;
- logs, counters, UI state, and client confirmation.

Java owns only:

- authenticated owner/session validation;
- atomic durable runtime-variable persistence;
- compare-and-set revision handling;
- realtime publication of committed values.

The smoke simulator must not add a second Java execution engine.

## 10. Proposed components

```text
VariablesSmokeTestPanel.tsx
VariablesSmokeTestPanel.module.scss
VariablesSmokeTestLog.tsx
VariablesSmokeTestLog.module.scss

domain/variablesSmokeTestTypes.ts
domain/variablesSmokeTestPlan.ts
domain/variablesSmokeTestEngine.ts
domain/variablesSmokeTestCommands.ts
domain/variablesSmokeTestControlFlow.ts
domain/variablesSmokeTestRuntime.ts
```

Each command handler returns a result and the next program counter. The engine contains no JSX,
WebSocket code, or database logic.

## 11. Small implementation phases

### S1 - Read-only panel and frozen scope

- Add the panel, RUN, STOP, scope summary, and scrollable log.
- Build the immutable ordered plan from the visible Review Connections graph.
- Do not write runtime values yet.

### S2 - Pure deterministic engine

- Add the program counter, status, safety ceiling, and step results.
- Support Click, Input, Output, Wait, Pause, and Refresh.

### S3 - Simulated Web Element memory

- Store deterministic Input values by Web Element instruction ID.
- Expose read/write operations to command handlers.

### S4 - Durable GET/SET runtime flow

- GET writes the real Bot Job runtime variable through the existing persistence service.
- SET reads the committed runtime value and updates simulated Web Element memory.
- Realtime snapshots update every subscribed page.

### S5 - Variable consumers

- Add ExcelWrite, CheckValue, CSV CHECK, and PDF CHECK simulation.
- Preserve EMPTY versus VOID semantics.

### S6 - LOOP and conditional execution

- Add configured loop counts, branch selection, counters, and safety limits.

### S7 - GOTO and complete Block flow

- Add cross-Block program-counter transitions and cycle diagnostics.

### S8 - Flow presentation improvements

- Render Block ID/name, instruction order/ID, producer order, and latest producer.
- Make repeated order numbers from different Blocks unambiguous.

### S9 - Migration acceptance

- Compare smoke results before and after typed variable-operation migration.
- Require no silent variable assignment, no parent fallback for ExcelWrite, and stable runtime
  revision behavior.

## 12. Recommended first deliverable

Implement S1 through S4 for Click, Input, GET, SET, and ExcelWrite. This is the smallest useful
vertical slice: it proves filter scope, instruction order, simulated page state, real variable
persistence, realtime synchronization, and exact raw-value handling before complex control flow is
added.

## 13. Non-goals

The first implementation will not:

- open Playwright or interact with a real page;
- mutate Bot Job instructions, relationships, Blocks, or order;
- write physical Excel/CSV/PDF files;
- replace production `executeJob()`;
- infer an ambiguous variable or Web Element connection.

