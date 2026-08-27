# AR Web Scanner client guide handoff

| Gate | Owner | Status | Evidence/artifacts | Commit | Remaining work |
| --- | --- | --- | --- | --- | --- |
| Governing instructions and repositories | Codex | APPROVED | `AGENTS.md`, shared bridge, prompts, repo status/HEAD recorded | `412c3f96` | none |
| Current UI/workflow inventory | Codex | READY FOR REVIEW | `GUIDE-COVERAGE-MATRIX.md` | `412c3f96` | client/Claude review |
| Markdown source | Codex | READY FOR REVIEW | `AR-Web-Scanner-Complete-Client-Guide.md` | `412c3f96` | client wording review |
| Navigable HTML | Codex | READY FOR REVIEW | `index.html`, five part pages, `guide-steps.json`; 6/6 HTML files passed local-link validation | `412c3f96` | client/Claude review |
| DOCX | Codex | READY FOR REVIEW | `AR-Web-Scanner-Complete-Client-Guide.docx`; 16/16 rendered pages visually inspected | `412c3f96` | client/Claude review |
| PDF | Codex | READY FOR REVIEW | `AR-Web-Scanner-Complete-Client-Guide.pdf`; 19/19 rendered pages visually inspected | `412c3f96` | client/Claude review |
| Screenshots | Codex | BLOCKED | `screenshots/README.md` | `412c3f96` | capture 24 synthetic-data screens at one resolution |
| Claude presentation pre-flight | Claude | NOT STARTED | shared bridge task 2 | — | wait for this guide checkpoint |

## Source repositories

| Repository | Branch | Verified HEAD | Dirty state preserved |
| --- | --- | --- | --- |
| `D:\Projects\AllinWeb\ar-web-selenium` | `final-allinweb` | `e0b956da` at task start | Existing `.claude`, worktree, image, and migration patch changes untouched |
| `D:\Projects\AllinWeb\ar-web-allinweb-fe` | `allinweb-delivered` | `8e87d2c` | clean at task start |
| `D:\Projects\AllinWeb\ar-web-allinweb` | `allinweb-delivered` | `a52227a` | existing untracked `config/` untouched |

## Runtime evidence and limitation

- The authorized local application was running from the delivered assets.
- Frontend assets reported during the session: `main.ebac8f70.js` and `main.c703a3f5.css`.
- The in-app browser connection was unavailable (`agent.browsers.list()` returned no browser), so no compliant screenshot automation could be performed.
- The configured database contains real client data. No screenshot was taken from it and no client data was copied into this package.
- Source verification is therefore marked `SOURCE`; screenshot/runtime walkthrough remains pending.

## Completed verification

- HTML: 6 files validated with zero missing local links or anchors.
- DOCX: exported through Microsoft Word, rendered to 16 pages, and every page visually inspected for clipping, blank pages, table breaks, and numbering.
- PDF: rendered to 19 pages, and every page visually inspected for clipping, blank pages, table breaks, and numbering.
- Content: Main Dashboard, Bot Job, Page Scanner, Locator Recovery, Excel Data REAL/SYNTHETIC memory, ExcelWriter Manager output memory, Runtime Variables, Memory List, Pages Open, setup, licensing, and troubleshooting are covered.
- Generated QA render images remain local build evidence and are excluded from version control by `work/.gitignore`.

## Resume instructions

1. Read `AGENTS.md`, `specifications/CODEX-CLAUDE-BRIDGE.md`, this handoff, and the coverage matrix.
2. Do not recapture from the Banca Stato database. Prepare an approved synthetic Bot Job and dataset.
3. Use one fixed resolution for all 24 PNGs and keep the assigned filenames.
4. Replace each Markdown figure placeholder with exactly one screenshot reference.
5. Regenerate HTML, DOCX, and PDF through `work/build_guide.py`.
6. Re-run HTML link checks, DOCX render checks, PDF render checks, privacy search, and `git diff --check`.
