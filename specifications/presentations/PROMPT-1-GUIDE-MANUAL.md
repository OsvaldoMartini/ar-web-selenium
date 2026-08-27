# TEMPLATE PROMPT 1 — Client guide manual with screenshots

Fill every {{PLACEHOLDER}}, then paste the whole prompt to the agent (Codex/Claude).
This produces the guide that PROMPT-2 later turns into a narrated video presentation,
so the output structure below is a CONTRACT — do not let the agent improvise it.

---

Project: {{PROJECT_ROOT e.g. D:\Projects\AllinWeb\ar-web-selenium}}
Branch: {{BRANCH}} (check it out first; do not work on main)

GOAL
Produce a complete client guide for {{APP_NAME}} as a written manual with
numbered screenshots of the real running application:
  {{APP_NAME}}-Complete-Client-Guide  (.md source + .docx + .pdf)
All material goes under {{DOCS_DIR e.g. docs\guide}}.

STEP 0 — INSPECT BEFORE PLANNING. Do not assume anything.
  - git checkout {{BRANCH}}, confirm the working tree is clean
  - launch the application ({{HOW_TO_RUN e.g. npm run dev / the packaged exe}})
    and list every screen/page reachable from the main navigation
  - report: screen list, how each is reached, which have empty/filled states
Then STOP and show me: the screen inventory and your proposed chapter layout.
Wait for my approval before writing anything.

DELIVERABLES (after I approve the plan)
  {{DOCS_DIR}}\screenshots\NN-name.png       one per screen/state, numbered 01..
  {{DOCS_DIR}}\{{APP_NAME}}-Manual.md        the Markdown source of the guide
  docs\{{APP_NAME}}-Complete-Client-Guide.docx  (and .pdf) built from the source

SCREENSHOT RULES — the downstream presentation depends on these
  - ALL screenshots share ONE resolution (e.g. 1296x839). Fix the window size
    before the first capture and never resize it. Report the resolution.
  - Name them NN-kebab-name.png in the order screens appear in the guide
    (01-home.png, 02-import.png, ...). Detail states get their own number
    (e.g. 16-environment-form.png for the same page with a form open).
  - Capture real UI with realistic demo data — never mockups, never edited
    images. Empty states and filled states are separate screenshots.
  - No real customer data, credentials, or production keys visible anywhere.

GUIDE STRUCTURE — a CONTRACT, exactly this shape
  - H1 chapters (numbered: "1. Introduction", "2. ...").
  - One chapter "Screen-by-screen guide" holding one H2 section PER SCREENSHOT,
    numbered (e.g. "6.4 API Catalog"). Every screenshot is embedded in exactly
    one H2 section, referenced as "Figure N. <caption>".
  - Each H2 section: 2-5 sentences of what the screen does and how to use it,
    plus one "note box" line for warnings/limits when relevant
    (release limits, evidence rules, security cautions).
  - UI control names verbatim as they appear on screen — NEVER translated,
    never paraphrased ("press Submit", not "press the send button").
  - An "Interface fundamentals" chapter describing shared visual states
    (primary buttons, disabled states, badges, error colours, chevrons).

TECHNICAL CONSTRAINTS
  - Write files UTF-8. On Windows never rely on the default codepage.
  - The .md is the single source of truth; docx/pdf are generated from it
    (pandoc or python-docx — ask me before installing anything).
  - Do not commit or push without asking me first.
  - Do not modify anything outside {{DOCS_DIR}}, docs\ and tools\.

WORK ORDER
  1. Step 0 inspection, then stop for approval.
  2. Capture all screenshots. Show me the file list + resolutions. Stop.
  3. Write the guide chapter by chapter; show me the first two chapters
     for tone feedback before doing the rest.
  4. Generate docx/pdf, report any conversion warnings.
