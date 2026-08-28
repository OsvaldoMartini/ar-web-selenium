# CODEX ↔ CLAUDE BRIDGE — shared task exchange

One file, two agents. Codex and Claude both read this at session start and
update it before session end. Osvaldo arbitrates.

Rules:
- Each agent edits ONLY its own section, the task board, and appends to the
  Status log. Never rewrite the other agent's text — reply under it.
- A task moves TASK QUEUE → ONGOING (claim it with your name + date) →
  REVIEW (the OTHER agent or Osvaldo reviews) → the Status log when closed.
- Contracts marked FROZEN change only with Osvaldo's approval.

Current effort: **AR Web guides & video presentations**
Prompts: `presentations/PROMPT-1-GUIDE-MANUAL.md` (Codex) ·
`presentations/PROMPT-2-VIDEO-PRESENTATION.md` (Claude)

---

## TASK QUEUE (unclaimed work, priority order)

| # | Task | Owner-to-be | Depends on |
|---|---|---|---|
| 2 | Verify handoff contract on Codex's output (pre-flight) | CLAUDE | task 1 |
| 3 | Build narrated video presentations per PROMPT-2 | CLAUDE | task 2 |
| 4 | Delivery package (zip + Istruzioni.pdf) in docs/presentations | CLAUDE | task 3 |

## ONGOING (claimed — name, date, expected outcome)

- **Task 1 — CODEX — 27.08.2026:** Guide content and all delivery formats are complete and verified. Safe synthetic-data screenshot capture remains pending because the in-app browser was unavailable and the configured database contains real client data.

## REVIEW (finished work awaiting the other agent's / Osvaldo's check)

- **Synthetic Bot Job package — CLAUDE — 28.08.2026** (unblocks Codex's
  screenshot gate): `presentations/ar-web-scanner-client-guide/synthetic/`
  - `SYNTHETIC-BOTJOB-SPEC.md` — Organization "Banca Demo SA", Environment
    "Demo Locale", Bot Job "DEMO-Saldo-Clienti" (3 Blocks / 9 commands),
    runtime variables incl. SECRET/VOID states, workbook plan, per-shot
    capture checklist mapped to the 24 slots.
  - `demo-bank.html` — self-contained synthetic target site (login,
    client table, account detail, payment form; stable element IDs).
    Serve with: `python -m http.server 8767 -d <synthetic folder>`.
  - `dati-sintetici.csv` — workbook rows for `Clienti-Demo.xlsx`.
  CODEX: create the records through the app UI against a demo database
  (never the Banca Stato one), capture the 24 PNGs at ONE resolution
  (1440×900 recommended) into `presentations/ar-web-scanner-client-guide/
  screenshots/`, then move your Phase 1 status to DONE.

---

## HANDOFF CONTRACT — Phase 1 → Phase 2 (FROZEN)

Codex's guide output must satisfy ALL of this before Claude starts:

1. Screenshots in `docs/guide/screenshots/` named `NN-kebab-name.png`,
   numbered in guide order, **all at ONE identical resolution** (report it).
2. Guide document(s) with H1 chapters and **one numbered H2 section per
   screenshot**; every screenshot embedded in exactly one H2 section.
3. UI control labels verbatim from the real UI — never translated or
   paraphrased (the narration quotes them literally).
4. Per-section caution/limit notes where relevant (they become narration).
5. No real customer data, credentials, or keys visible in any screenshot.
6. Files written UTF-8. Markdown source is the single source of truth.

Phase 2 output: `docs/guida/<tour>/steps.json` + `docs/guida/index.html`
player + delivery zip in `docs/presentations/`, per PROMPT-2.
Serving port: **8766** (8765 = ARWeb-Api-Tester presentation, 8000 = dev
servers — both taken).

---

## SEZIONE CODEX (Phase 1 owner — guides)

> Keep current: what you produced, where, deviations from the contract.

- Status: **GUIDE READY FOR REVIEW — SAFE SCREENSHOTS PENDING**
- Guides produced: `specifications/presentations/ar-web-scanner-client-guide/AR-Web-Scanner-Complete-Client-Guide.md`, navigable `index.html` plus five part pages, DOCX, PDF, coverage matrix, and handoff.
- Screenshot count + resolution: 0 captured; 24 synthetic-data capture slots and filenames are defined in `screenshots/README.md`.
- Deviations from contract: output is under `specifications/presentations/ar-web-scanner-client-guide/` rather than `docs/guide/`; screenshots are intentionally absent because the in-app browser was unavailable and the configured database contains real Banca Stato data. No customer data was copied into the guide.
- Notes for Claude: guide checkpoint `412c3f96`. Source inventory is based on delivered frontend `ar-web-allinweb-fe` branch `allinweb-delivered` at `8e87d2c` and backend/deployment project `ar-web-allinweb` at `a52227a`. HTML validation passed for 6/6 files; DOCX 16/16 pages and PDF 19/19 pages were rendered and visually inspected. The guide explicitly covers Locator Recovery / Review Locator, Excel Data REAL versus SYNTHETIC execution memory, ExcelWriter output memory and save boundaries, Runtime Variables, Page Scanner, and Bot Job operations. Do not begin final screenshot-dependent video assembly until an approved synthetic Bot Job is available.

## SEZIONE CLAUDE (Phase 2 owner — presentations)

> Do not start before SEZIONE CODEX says DONE and the pre-flight passes.

- Status: **PARALLEL SUPPORT ACTIVE — presentation still waiting on Phase 1
  screenshots**
- Conflict boundary (28.08.2026): Claude touches ONLY
  `specifications/presentations/**` and this bridge — no application code,
  no `src/`, no frontend. Codex has free rein on the Components-page coding;
  zero overlap by construction.
- Synthetic dataset: delivered to REVIEW (see above) — answers Codex's
  "approved synthetic Bot Job" prerequisite. Osvaldo approved its creation
  on 28.08.2026.
- Pre-flight check results: —
- Tours produced: —
- Voice: browser speechSynthesis by default; HeyGen clone only on Osvaldo's
  explicit go (key via gitignored .env; voice_id chosen by Osvaldo).
- Delivery package: —
- Notes for Codex: reference implementation lives in
  `D:\Projects_DevOps\ARWeb-Api-Tester` → `docs/guida/` (player),
  `tools/guide_to_steps.py`, `tools/generate_audio.py` — reuse, don't reinvent.

---

## OPEN QUESTIONS (either agent asks; Osvaldo answers)

- (none)

## STATUS LOG (append one line per session, newest first)

| Date | Agent | What changed |
|---|---|---|
| 28.08.2026 | CLAUDE | Synthetic Bot Job package delivered to REVIEW (spec + demo-bank.html on port 8767 + CSV) — unblocks the 24-screenshot gate; declared conflict boundary: Claude edits only specifications/presentations/** and this bridge while Codex works on the Components page |
| 27.08.2026 | CODEX | Checkpoint `412c3f96`: completed the source-verified AR Web Scanner client guide in Markdown, navigable HTML (five parts), DOCX, and PDF; documented 24 safe screenshot slots; validated 6/6 HTML files and visually inspected all 16 DOCX and 19 PDF pages. Safe synthetic screenshots remain pending. |
| 27.08.2026 | CLAUDE | Bridge created in specifications/; prompts 1+2 in specifications/presentations/; docs/ output folders scaffolded; waiting on Codex Phase 1 |
