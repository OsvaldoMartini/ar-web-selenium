# AR Web Runtime V1 versus V2

**Client and product architecture guide**  
**Current product position:** AR Web can be delivered with V1 and V2 together. A separately packaged
V1-only edition is planned so customers that do not need isolated parallel execution can operate
without the V2 Node runtime.

## Executive summary

The largest architectural difference is simple:

> **V1 shares one Java-owned Playwright browser. V2 gives each execution an isolated
> Node/TypeScript Playwright browser.**

V1 provides the established, legacy-compatible execution path and is well suited to one interactive
Bot Job at a time. V2 is the isolation and scalability path: it supports multiple concurrent Bot Job
executions while keeping their browser contexts, cookies, storage, pages, and failures separated.

Both runtimes use the same AR Web Bot Jobs, instructions, REAL/SYNTHETIC data, Runtime Variables,
ExcelWriter state, scanned-element registry, OCR mapping, owner authorization, and immutable Page
Scanner capture model.

## Complete comparison

| Area | Java V1 Shared | TypeScript V2 Isolated |
|---|---|---|
| Browser owner | Java `ARPlaywrightDriver` | Node/TypeScript Playwright runtime |
| Browser model | One process-wide shared browser/page | Separate Browser/Context/Page per execution |
| Concurrency | One shared browser execution at a time | Up to five isolated executions in the current pool |
| SERVER control | Not required | Must be ON and READY |
| Bot Job isolation | Logical owner checks around a shared browser | Physical browser/context isolation plus owner checks |
| Cookies and browser storage | Shared browser state can continue between operations | Private to each isolated browser context |
| Parallel Bot Jobs | Serialized; not intended for parallel browser ownership | Designed for parallel Bot Job execution |
| STOP | Stops execution and preserves the shared browser | Stops execution and retains that exact isolated browser |
| KILL INSTANCE | Closes the shared V1 browser | Closes only the selected V2 browser |
| CONTINUE PAGE | Reuses the currently open shared page | Reuses the retained browser for the exact owner |
| Page Scanner | Direct Java browser access | Temporarily leases the selected owner's retained V2 browser |
| Scanner processing | Java scanner, OCR, fingerprint, registry, and snapshot | The same Java pipeline through bounded authenticated Node RPC |
| Runtime Instances | Displays active executions; a stopped retained browser is not currently listed | Same; retained V2 browsers are not currently listed |
| Physical Click/Input | Java Playwright action implementation | Node/TypeScript Playwright action implementation |
| Locator wait | Java bounded locator discovery | Node bounded and interruptible render wait |
| Locator Recovery | Supported | Supported |
| Recovery modal | Pauses the instruction for an explicit user decision | Pauses the instruction for an explicit user decision |
| Stop during locator wait | Interrupts the Java execution | Interrupts the exact Node action and run |
| Crash isolation | Shared-browser failure can affect V1 browser work | One V2 browser failure should not affect sibling runs |
| Browser options | Applied during Java browser startup | Propagated to the isolated Node browser launch |
| Viewport | Shared maximized browser | Isolated maximized browser with native viewport sizing |
| Operational logs | Java Smoke Test and Page Scanner logs | Java trace plus detailed Node `ar_web_execution_v2.log` |
| Architecture position | Stable legacy-compatible runtime | Preferred foundation for isolation and parallel execution |

## Capabilities shared by V1 and V2

| Capability | Common behavior |
|---|---|
| REAL and SYNTHETIC Excel Data | Uses the same React-owned frozen execution data |
| Runtime Variables | Uses the same owner-scoped runtime state |
| ExcelWriter Manager | Uses the same React execution memory and flush rules |
| Active/inactive instructions | Uses the same Bot Job instruction authority |
| Page Scanner result model | Produces the same Element DTO, OCR, registry, fingerprint, and snapshot data |
| Owner authorization | Requires the exact Home Banking, Bot Job, workspace, and binding generation |
| STOP semantics | Execution stops while the browser stays available for reuse |
| Runtime selection | Page Scanner follows the owner-specific V1/V2 selection from Smoke Test |
| Locator recovery decisions | Supports explicit Bypass, Use Once, Use and Save, and execution control |
| Traceability | Records bounded operational events without exposing credentials or input values |

## Which runtime should a customer use?

| Customer scenario | Recommended runtime |
|---|---|
| One interactive Bot Job with maximum legacy compatibility | V1 |
| Multiple Bot Jobs running concurrently | V2 |
| Strong cookie, session, and storage isolation | V2 |
| Manual testing against one already-open shared page | V1 |
| Independent failure containment between executions | V2 |
| Existing V1 automation requiring a gradual migration | V1, then validate the same flow on V2 |
| New scalable deployments | V2 |
| Migration and parity verification | Deliver both and compare each runtime against the same Bot Job |

## Current combined edition: V1 and V2 together

The complete edition contains both runtimes and exposes one owner-scoped runtime selector.

1. The customer can keep existing V1 automation operational.
2. The same Bot Job can be validated on V2 without replacing its database definition.
3. STOP preserves the selected runtime's browser for Continue Page and Page Scanner.
4. Page Scanner follows the selected runtime and exact Bot Job owner.
5. V1 and V2 logs remain distinguishable for support and diagnosis.
6. V2 requires its supervised SERVER process; V1 does not.
7. KILL INSTANCE closes the selected browser according to the runtime boundary.

This combined edition is the safest migration product because it permits controlled parity testing
before a customer adopts isolated V2 execution as the default.

## Planned V1-only edition

The V1-only edition should be a real packaging boundary, not only a hidden V2 button.

| Separation boundary | V1-only requirement |
|---|---|
| Product configuration | Build/package profile explicitly selects `V1_ONLY` |
| User interface | Remove V2 selector, SERVER control, isolated-runtime status, and V2-only messages |
| Java runtime | Keep the V1 shared-browser coordinator and common Bot Job services |
| Node sidecar | Exclude the V2 Node server, compiled runtime, Node executable dependency, and V2 launch scripts |
| Contracts | Keep shared domain contracts separate from V2 transport implementations |
| Page Scanner | Bind directly to V1 while preserving the same DTO/OCR/snapshot result contract |
| Runtime Instances | Show only V1 execution/browser state appropriate to the V1 product |
| Logs | Retain Java operational logs; omit V2 Node log configuration |
| Installer/package | Produce a separate artifact and manifest proving no V2 sidecar is included |
| Tests | Run a V1-only startup, execution, STOP/KILL, Page Scanner, recovery, data, and ExcelWriter matrix |

### Engineering separation strategy

1. Define a small runtime interface for start, action, stop, kill, retained-browser access, Page
   Scanner access, status, and shutdown.
2. Keep V1 and V2 implementations in separate modules behind that interface.
3. Keep shared React domain state and shared Java authorization/contracts outside either runtime.
4. Select available runtimes through an immutable build/product profile, not a mutable client flag.
5. Build two distributable products:
   - **AR Web Complete:** V1 + V2.
   - **AR Web V1:** V1 only, without the Node V2 runtime payload.
6. Give each artifact an explicit product name, version, manifest, dependency inventory, and automated
   acceptance report.
7. Prevent a V1-only package from accepting V2 WebSocket or HTTP operations, even if a caller crafts
   them manually.

## Important operational behavior

| Operation | V1 | V2 |
|---|---|---|
| Run | Uses the shared Java browser | Creates or reuses the exact owner's isolated browser |
| Stop | Ends execution; browser remains open | Ends execution; exact browser moves to retained/idle state |
| Page Scanner after Stop | Uses the preserved shared browser | Leases the exact retained isolated browser and returns it afterward |
| Kill | Ends execution and closes the shared browser | Ends execution and closes only the selected isolated browser |
| Change Bot Job | Retargets shared-browser authority to the new active owner | Selects only the new owner's isolated/retained browser |

## Current user-interface limitation and planned improvement

Runtime Instances currently lists active executions. After STOP, the execution row disappears even
though its browser remains open and reusable. This is expected in the current implementation.

A useful future enhancement is a separate **Retained Browsers** view or `RETAINED / IDLE` rows with:

- runtime type;
- exact Home Banking and Bot Job owner;
- browser/context identity;
- retained timestamp and idle age;
- Page Scanner availability;
- explicit **Close Browser** action.

This would make the distinction between an active execution and a reusable stopped browser visible
to operators and clients.

## Product message

AR Web V1 protects established automation investments. AR Web V2 adds isolated parallel browser
execution without changing the customer's Bot Job, variable, data, ExcelWriter, scanned-element,
OCR, or Page Mapping ownership model. The combined edition supports gradual adoption; the planned
V1-only edition provides a smaller deployment for customers who deliberately choose the established
shared-browser architecture.
