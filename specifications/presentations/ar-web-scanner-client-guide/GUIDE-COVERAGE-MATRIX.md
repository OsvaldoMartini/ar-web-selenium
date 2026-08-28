# AR Web Scanner guide coverage matrix

This matrix is the audit record for `AR-Web-Scanner-Complete-Client-Guide.md`.
`SOURCE` means verified from the delivered React/Java source. `RUNTIME` means
independently exercised in the running application during this guide session.

| ID | Guide section | Screen/state | Entry path | Principal controls covered | Frontend evidence | Backend evidence | Verification | Screenshot |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| G01 | 3.1 | Main Dashboard | Application start | Organizations, New Bot Job, Clone Job, Config, Info, Launch, Open Job, Refresh, Find, selection/delete, Pages Open, Exit | `MainDashboard.tsx` | Main dashboard socket contracts | SOURCE | 01 pending |
| G02 | 3.2 | Organizations | Main → Organizations | Organization/Environment selectors, create/update/delete, advanced fields | `OrganizationManager.tsx` | Organization workspace handlers | SOURCE | 02 pending |
| G03 | 3.3 | New Bot Job | Main → New Bot Job | owner grids, name, description, app type, create/cancel | `NewBotJobPage.tsx` | New Bot Job workspace handlers | SOURCE | 03 pending |
| G04 | 3.4 | Clone Job | select job → Clone Job | source summary, target owner/URL/type, clone/cancel | `CloneJobPage.tsx` | Clone transaction | SOURCE | 04 pending |
| G05 | 3.5 | Configuration | Main → Config | Browser, DB Type, paths, reload, backup, restore, delete | `TemplateForm.tsx` | configuration/backup/restore handlers | SOURCE | 05 pending |
| G06 | 3.6 | About this Software | Main → Info | Refresh, license actions, Pages Open, Close | `InfoPage.tsx` | info/license status handler | SOURCE | 06 pending |
| G07 | 3.7 | License Request | About → License Manager | Request, Activate, Use existing, agreement, status refresh | `LicenseManager.tsx` | license services | SOURCE | 07 pending |
| G08 | 4.1 | Bot Job Details | Main → Open Job | surfaces, files, start block, ONE/ALL, Launch/Test run/CHECK/Stop, import/export, Variables, Excel Data | `GridItem.tsx`, `BotJobDetailsHeader.tsx`, `BotJobDataActions.tsx`, `BotJobFileActions.tsx` | Bot Job workspace/execution coordinators | SOURCE | 08 pending |
| G09 | 4.2 | Execution rules help | Bot Job → ? | names, priority, Excel authority, caller paths | `BotJobExecutionRulesHelp.tsx` | resolver contracts | SOURCE | 09 pending |
| G10 | 4.3–4.4 | Block/instruction grid | Bot Job grid | active, collapse, select, memory, move, rename, rollback, type, tests, edit, delete | `BlockHeader.tsx`, `InstructionRow.tsx`, `GridItem.tsx` | graph mutation/test handlers | SOURCE | 10 pending |
| G11 | 4.5 | Reconnect relationships | row relationship chip | parent/variable connect, disconnect, Add Variable, operator/second variable | `InstructionRelationshipDetails.tsx`, reconnect dialogs | relationship transactions | SOURCE | 11 pending |
| G12 | 4.6 | Command Editor | row → EDIT CMD / ADD | target Block, placement, command, editors, create/copy/update/cancel, ExcelWrite file | `CommandEditorPage.tsx`, `CommandEditorPageBody.tsx`, editor modules | command editor workspace/transactions | SOURCE | 12 pending |
| G13 | 4.7 | Locator Recovery | row → Review Locator or automatic recovery | origin, tests, scan, use once/save, bypass/stop/help | `SmokeTestLocatorRecoveryModal.tsx`, GridItem recovery hook | V1 recovery coordinator/scanner | SOURCE | 13 pending |
| G13A | 4.8 | Components Library | Bot Job → Components | save Component, refresh, organization scope, block/row memory, type, rename/rollback, apply to target Block | `ComponentsPage.tsx`, `GridItemComp.tsx`, Components workspace hooks, `MemoryList.tsx` | Component library persistence, canonical graph revision, Memory Apply transaction | SOURCE | 13A pending |
| G14 | 5.1 | Page Scanner toolbar | Bot Job → Pre Scan | Page Scanner, OCR Config, Refresh Web Page, Clear Grid, execution controls, Focus/Search | `GridItemScann.tsx`, scanner header/control modules | Page Scanner workspace/scanner services | SOURCE | 14 pending |
| G15 | 5.2 | Scanner results | Page Scanner after scan | keep/delete/clear, pagination, ID/ID-TEST/OCR, type, tests, rename/rollback, memory | `GridItemScann.tsx` | scanner row/test/memory handlers | SOURCE | 15 pending |
| G16 | 5.3 | Page Scanner profiles | scanner Focus settings | profile, terms, add/remove/refresh, new/save/delete | `PageScannerFocusProfileEditor.tsx` | profile persistence | SOURCE | 16 pending |
| G17 | 5.4 | Locator Generator | Page Scanner → Locator Gen | target, Control HTML, Generate, Apply XPath, Add ElementDTO(s), Clear | `LocatorGeneratorPanel.tsx` | locator generator action path | SOURCE | 17 pending |
| G18 | 5.5 | OCR Configuration | Page Scanner → OCR Config | profile fields/parameters, test, save, save as new, delete, cleanup | `OCRConfigPanel.tsx` | OCR configuration workspace | SOURCE | 18 pending |
| G19 | 6.1 | Excel Data REAL | Bot Job → Excel Data | mode, columns, rows, clean, save, reload, cell/delete/help | `ExcelDataPage.tsx`, Excel Data components | Excel Data workspace | SOURCE | 19 pending |
| G20 | 6.2 | Excel Data SYNTHETIC | Excel Data mode toggle | rows, context, generate, Save DB, isolated memory | `ExcelSyntheticControls.tsx`, `ExcelDataModeToggle.tsx` | synthetic dataset persistence | SOURCE | 20 pending |
| G21 | 6.3 | ExcelWriter Manager | execution with ExcelWrite | policy, file tabs, cells, Save Dirty Files, states | `ExcelWriterManagerPage.tsx`, `ExcelWriteManagerWorkspace.tsx`, domain modules | finalized artifact write service | SOURCE | 21 pending |
| G22 | 6.4 | Runtime Variables | Bot Job → Variables | refresh, add, auto, clear, delete all/one, edit/search/help | `RuntimeVariablesPage.tsx`, `RuntimeMemoryPanel.tsx` | runtime variable service | SOURCE | 22 pending |
| G23 | 6.5 | Memory List | Bot Job/Components/Page Scanner row or Block +, or Memory (x) | target Block, create, reorder, remove, clear, apply independent Component copies | `MemoryList.tsx`, Components memory bridge | Memory List workspace/apply transaction | SOURCE | 23 pending |
| G24 | 6.6 | Pages Open | Pages (x) / user menu | refresh, focus, close page, close application warning | `PagesOpen.tsx` | page registry/focus/close handlers | SOURCE | 24 pending |

## Deliberate exclusions

| Surface | Reason |
| --- | --- |
| Smoke Test page | Removed from the delivered client frontend; its Locator Recovery capability is documented at its delivered Bot Job location. |
| Page Mappings | Removed from the delivered client frontend; Page Scanner remains available. |
| Full Variables design page | Hidden from this delivery; client operations use Runtime Variables. |
| API/mobile/MultiTest developer pages | Imported legacy/optional surfaces are not part of the current delivered client navigation. |
| Real client screenshots | Prohibited for this guide. The running database contains client data and browser-control capture was unavailable. |

## Coverage totals

- 25 client-visible screens/states documented.
- 25 planned screenshot IDs reserved.
- 0 runtime screenshots captured; 25 remain pending a safe synthetic session.
- 4 intentionally excluded non-delivered/optional surfaces recorded above.
