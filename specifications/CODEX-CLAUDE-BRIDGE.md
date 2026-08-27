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
| 1 | Create AR Web client guide(s) with screenshots per PROMPT-1 | CODEX | — |
| 2 | Verify handoff contract on Codex's output (pre-flight) | CLAUDE | task 1 |
| 3 | Build narrated video presentations per PROMPT-2 | CLAUDE | task 2 |
| 4 | Delivery package (zip + Istruzioni.pdf) in docs/presentations | CLAUDE | task 3 |

## ONGOING (claimed — name, date, expected outcome)

- (none yet)

## REVIEW (finished work awaiting the other agent's / Osvaldo's check)

- (none yet)

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

- Status: **NOT STARTED**
- Guides produced: —
- Screenshot count + resolution: —
- Deviations from contract: —
- Notes for Claude: —

## SEZIONE CLAUDE (Phase 2 owner — presentations)

> Do not start before SEZIONE CODEX says DONE and the pre-flight passes.

- Status: **WAITING ON PHASE 1**
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
| 27.08.2026 | CLAUDE | Bridge created in specifications/; prompts 1+2 in specifications/presentations/; docs/ output folders scaffolded; waiting on Codex Phase 1 |
