# Performance, Migration, and Shared Review Document Index

Last organized: 2026-08-05

## Purpose

This folder contains Markdown documents moved from `specifications/migrations`
because their filesystem modification date was within the date-based five-day
window 2026-07-31 through 2026-08-05. It also contains the active Claude/Codex
review bridge.

## Shared Claude/Codex review workflow

- [x] TASK — Move the qualifying recent Markdown files into this folder.
- [x] TASK — Split documents larger than 30 KB into ordered `_PART_N` files.
- [x] TASK — Verify every split by reconstructing its bytes and comparing its
  Git blob hash with the original committed document.
- [x] TASK — Preserve `COPY_LAST_RESPONSE.md` as the current two-way bridge.
- [x] TASK — Record that the external MultiTraderAI bridge is a structural
  example, not an AR Web Scanner source of truth.
- [ ] TASK — Claude reviews the latest Codex-authored change and fills the
  `CLAUDE → CODEX` section.
- [ ] TASK — Codex reviews the next Claude-authored change and fills the
  `CODEX → CLAUDE` section.
- [ ] TASK — Commit and push this documentation reorganization when requested.

## Governing handoff documents

| Document | Role |
|---|---|
| `COPY_LAST_RESPONSE.md` | Only the latest two-way Claude/Codex exchange |
| `GUIDANCES CLAUDE vs CODEX.md` | Original collaboration and build/deployment instructions |
| `CLAUDE_vs_CODEX_MIGRATION_CHECKS_2026_07_12_PART_1.md` through `_PART_6.md` | Detailed historical cross-review record |
| `ROADMAP_INSTRUCTION_GRAPH_AND_DRAG_DROP.md` | Protected drag-and-drop rules and recovery commits |
| `ACTIVE_BUGS_TO_FIX_2026_07_28.md` | Active defect inventory |

## Repository-level instruction and handoff sources

These files remain at the repository root because they govern more than the
performance/migration document collection:

| Document | Authority/use |
|---|---|
| `../../AGENTS.md` | Codex production execution contract; read before changes |
| `../../CLAUDE.md` | Claude repository/build/runtime contract; includes the standing prohibition against assistants running Maven |
| `../../SESSION_HANDOFF.md` | Older scanner-migration checkpoint; verify dates and live code before relying on it |

External comparison only:

| Document | Authority/use |
|---|---|
| `D:\Projects_DevOps\MultiTraderAI-Docker-Bots\specifications\performances\COPY_LAST_RESPONSE.md` | Template for the two-section review bridge; never an AR Web Scanner state source |

## Split-document map

The parts contain the original bytes in their original order; no task markers
were injected into historical content.

| Original document | Ordered replacement |
|---|---|
| `BOT_JOB_DETAILS_COMPONENT_DECOMPOSITION_2026_07_24.md` | `_PART_1.md`, `_PART_2.md` |
| `CLAUDE_vs_CODEX_MIGRATION_CHECKS_2026_07_12.md` | `_PART_1.md` through `_PART_6.md` |
| `ROADMAP_COMPLETE_VARIABLE_OPERATIONS_MIGRATION_2026_08_01.md` | `_PART_1.md`, `_PART_2.md` |
| `ROADMAP_DETACHED_PAGE_SCANNER_WORKSPACE_2026_07_20.md` | `_PART_1.md`, `_PART_2.md` |
| `ROADMAP_VARIABLE_CENTRIC_INSTRUCTION_GRAPH_2026_07_29.md` | `_PART_1.md` through `_PART_4.md` |

## Completion rule

Do not mark a shared task complete merely because one assistant implemented it.
Implementation, independent review, focused verification, broader verification,
commit, push, deployment, restart, and live behavior are separate gates.
