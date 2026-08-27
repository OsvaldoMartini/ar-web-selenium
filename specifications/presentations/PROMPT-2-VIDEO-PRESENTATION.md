# TEMPLATE PROMPT 2 — Narrated video presentation from the guides

Prerequisite: the guide(s) from PROMPT-1 exist, with uniform-resolution
numbered screenshots. Fill every {{PLACEHOLDER}}, paste the whole prompt.
This is the exact prompt (plus lessons learned) that produced the ARWeb
"Visita guidata" — keep the constraints, they were all earned the hard way.

---

Project: {{PROJECT_ROOT}}
Branch: {{BRANCH}} (check it out first; do not work on main)

GOAL
Turn the existing written guide(s) into narrated step-by-step screen
walkthroughs with progressive Italian subtitles — one tour per guide:
  {{LIST THE GUIDES, e.g. 1. ARWeb-Complete-Client-Guide}}
Source material, including all screenshots, is in {{GUIDE_DIR e.g. docs\guide}}.

STEP 0 — INSPECT BEFORE PLANNING. Do not assume anything.
  - git checkout {{BRANCH}}, confirm the working tree is clean
  - list {{GUIDE_DIR}} recursively: find the guide files (extension unknown),
    count the images, and report their resolutions
  - report how many level-2 sections each guide has
  - check whether {{OUT_DIR e.g. docs\guida}}\index.html exists (the player)
Then STOP and show me: file names found, image count, resolutions, section
counts per guide, and your proposed step breakdown. Wait for my approval.

If the player does not exist, say so and I will decide whether you build it
(see PLAYER below) or I supply one.

DELIVERABLES (after I approve the plan)
  CLAUDE.md / AGENTS.md               repo root, holding the rules below
  tools\guide_to_steps.py             guide -> steps.json scaffolder
  {{OUT_DIR}}\<tour>\steps.json       one manifest per tour
  {{OUT_DIR}}\index.html              the player (reads steps.json)
  tools\generate_audio.py             OPTIONAL cloned-voice audio (see VOICE)
  docs\presentations\{{PACKAGE_NAME}}.zip  self-contained delivery package

COST CONSTRAINT
Zero paid services by default. Browser speechSynthesis for playback.
Voice cloning (HeyGen) ONLY if I explicitly provide the API key — see VOICE.
Never call ElevenLabs, OpenAI or any metered API. Ask before installing anything.

LANGUAGE — audience is Italian-speaking Switzerland (Ticino), not Italy
  - Register: impersonal infinitive. YES "inserire le proprie credenziali",
    NO "inserite le vostre credenziali".
  - Swiss vocabulary: formulario (not modulo), annunciarsi (not accedere/login),
    linguetta (not scheda, for UI tabs), scaricamento (not download).
    Avoid anglicisms more aggressively than Italy-Italian would.
  - Dates 27.08.2026, thousands 1'000, currency CHF.
  - NEVER translate UI control names. If the button says "Submit", the
    narration says "premere Submit". Extract the real labels from the guides
    and the source code — do not invent them.
  - Each step: 2-4 sentences, 25-55 words. Longer and the subtitle band
    scrolls past what a viewer can read.
  - Include the guide's caution notes (synthetic data, evidence limits,
    credential handling) in the narration — they are not optional filler.

TECHNICAL CONSTRAINTS — already established, do not rediscover them
  - utterance.lang must stay "it-IT". There is no it-CH voice on any platform;
    requesting it falls back to the system default, often German or English.
    Document tag stays it-CH.
  - Hotspot coordinates are fractions of the IMAGE, not the viewport.
  - All screenshots in one tour must share a resolution or hotspots drift.
    Report mismatches in step 0; do not silently rescale.
  - Write JSON with ensure_ascii=False and explicit encoding="utf-8".
    Windows cp1252 mangles accented characters on both read and write.
    NEVER edit UTF-8 files with PowerShell Get-Content/-replace/Set-Content
    without explicit encodings — it double-encodes the accents.
  - When stripping Markdown code fences, preserve string length (replace
    non-space chars with spaces) or heading offsets shift.
  - Re-running the scaffolder must never overwrite hand-written "testo",
    "titolo" or "hotspot" — merge over the existing steps.json.
  - Map docx-embedded images to screenshot files BY CONTENT HASH — the
    embedding order does not follow the filename numbering. Steps follow
    document order (the guide's narrative), not filename order.
  - The player fetches steps.json, so file:// is blocked by CORS.
    Serve with: python -m http.server {{PORT}}
  - PORT: never use 8000 or the app's own dev port. Use {{PORT e.g. 8765}}
    everywhere (launcher, readme, PDF).

STEPS.JSON CONTRACT (per tour)
  { "titolo", "lingua": "it-CH", "linguaVoce": "it-IT",
    "immagini": {"larghezza", "altezza"},
    "steps": [ { "id", "ordine", "immagine" (relative to {{OUT_DIR}}),
                 "capitolo", "sezione", "titolo", "testo",
                 "hotspot": {"x","y","w","h"} | null } ] }

PLAYER — single self-contained index.html, no dependencies
  - Menu page listing the tours; deep link ?tour=<name>.
  - Per step: screenshot, hotspot highlight (border + huge box-shadow dimming
    the rest), title, subtitle with per-word spans revealed progressively.
  - Audio priority: recorded narration <tour>/audio/<id>.mp3 + <id>.json
    (word timestamps) when present, else speechSynthesis fallback with an
    Italian-voice picker.
  - LESSONS (do not regress):
    * Play recorded audio via the Web Audio API (AudioContext +
      decodeAudioData + BufferSource), NOT the <audio> element — <audio>
      stalls against minimal servers like python http.server.
    * Reveal subtitles with setInterval (~120ms), NOT requestAnimationFrame —
      rAF stops completely in background tabs.
    * Word-timestamp lists may contain sentinel tokens like "<start>" —
      filter them before aligning with the text spans.
    * Compare image URLs as absolute (new URL(rel, location.href).href ===
      img.src) — a relative endsWith check breaks replay on the same step.
    * First audio needs one real user click (autoplay policy) — after that,
      auto-advance chains the whole tour.
  - Controls: play/pause (space), prev/next (arrows), restart, auto-advance
    checkbox, progress bar.

VOICE (optional, only if I say so and provide the key)
  - Store the key in gitignored .env as HEYGEN_API_KEY. Never echo it.
  - GET https://api.heygen.com/v3/voices?type=private to list my clones;
    ASK ME which voice_id to use, and whether audio may ship inside the
    committed zip (raw MP3s always stay gitignored).
  - POST https://api.heygen.com/v3/voices/speech {text, voice_id,
    language:"it"} -> {audio_url, duration, word_timestamps}. Save
    <id>.mp3 + <id>.json (keys: durata, parole). Skip existing files so
    re-runs are resumable. Generate ONE test clip first, then batch.

DELIVERY PACKAGE ({{PACKAGE_NAME}}.zip in docs\presentations)
  - Contents: guida/ (player + manifests + audio), guide/screenshots
    (paths preserved so "../" references resolve), AVVIARE.bat, LEGGIMI.txt,
    Istruzioni.pdf.
  - AVVIARE.bat: cd to its own folder, try python then py, start the browser
    on http://localhost:{{PORT}}/guida/index.html, run http.server {{PORT}};
    friendly message if Python is missing.
  - Istruzioni.pdf: one A4 page, Italian, sections Requisiti / Installazione /
    Avvio / Comandi / In caso di problemi. Generate with pymupdf if available
    in the repo, else ask.
  - gitignore: raw audio dirs and any locally extracted package folder;
    an explicit !docs/presentations/*.zip exception if zips are ignored.

DO NOT
  - commit or push without asking me first
  - modify anything outside {{OUT_DIR}}, tools\, docs\presentations and the
    rules file
  - commit raw generated audio or video files (the zip is the only carrier)
  - rewrite the existing guides

WORK ORDER
  1. Step 0 inspection, then stop for approval.
  2. Rules file + scaffolder. Run it on all guides. Show one steps.json. Stop.
  3. Italian narration for tour 1 only. Show the first 5 steps and wait for
     my tone feedback before doing the rest.
  4. Remaining tours once tour 1 is signed off.
  5. Build/verify the player, serve locally, VERIFY IN A REAL BROWSER
     (playback progresses, words light in sync, auto-advance chains).
  6. Optional voice pass (see VOICE), re-verify.
  7. Build the delivery package, verify the zip contents (count the MP3s,
     check the port in AVVIARE.bat, check the player code is the final one).
