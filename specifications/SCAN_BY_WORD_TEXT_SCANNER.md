# NEW SCANNER — "Scan by Word/Text" FOCUS mode — CRITICAL (deferred)

**Priority: CRITICAL.** **Status: NOT STARTED — do AFTER the current decomposition/refactor work.**
Do NOT modify existing scanners; this is an ADDITIVE new FOCUS option. Captured 2026-07-26 from user.

## Goal
Add a new option to the **FOCUS:** dropdown on the **PAGE SCANNER** page: **"Scan by Word/Text"**.

When the user picks this mode and supplies a match string (e.g. `"Log in"`, `"Go Getters"`), the scanner
finds **web elements whose text matches/contains that string** — matching against, at least:
- the element's **label text** (associated `<label>`, `aria-label`, visible text),
- the element's **placeholder** value,
- (and other reasonable text sources — button/anchor inner text, `title`, `value`, `name`).

The match should be **contains / case-insensitive** (not exact), so `"Log in"` finds "Log In", "User Login", etc.

Crucially, it must still **classify the element type exactly as the current scanners do** — OUTPUT / INPUT /
CLICK / LINK / etc. — i.e. reuse the existing element-identification/classification pipeline; only the
*selection criterion* (find-by-text-match) is new.

## Where it plugs in (to be confirmed at build time)
- FE FOCUS options + scanner contract: `abr-react-ts-grid/src/components/scanner/PageScanner.contract.ts`
  (+ `.test.ts`); scanner UI wiring near `GridItemScann.tsx`.
- Actual DOM scan is done by the external runtime plugins (see CLAUDE.md `PATH_PLUGINS`):
  `hoverPick.min.js` / `actionExecutor.min.js` / `searchListAsync.min.js` under
  `D:\Projects\ARWeb-Martini\ARWeb\plugins\*`. The new mode needs a plugin (or plugin branch) that
  queries the DOM by text/label/placeholder match, then runs each hit through the SAME element
  classification (OUTPUT/INPUT/…) + DTO build the current pick flow uses.
- Results should flow into the grid the same way current scans do (and the Memory List append that
  already works from Page Scanner).

## Acceptance (draft — refine at build time)
- New "Scan by Word/Text" entry in the FOCUS dropdown; selecting it reveals a text-to-match input.
- Given a match string, the scan returns all matching elements with correct type classification.
- Matches on label text OR placeholder (OR the other text sources above), contains + case-insensitive.
- Existing FOCUS modes/scanners unchanged.

## Related (also pending — see backlog)
- Memory List as central maintenance hub (blue-arrow reinsert via Memory List modal; multi-source add).
- "Move Instruction Refused: Web Field dependencies must remain in their parent block" — root-caused to
  `InstructionMoveValidator.validateParentRelationships` (intended integrity rule; fix = move the whole
  web-field family together, likely via the Memory List apply). See backlog doc.
