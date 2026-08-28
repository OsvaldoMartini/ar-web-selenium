# Synthetic Bot Job for the 24 guide screenshots

Owner: CLAUDE (bridge task "synthetic dataset"). Everything here is invented:
no real client, account, credential, license, or path may replace any value.
Codex (or Osvaldo) creates these records through the application UI — that is
also what produces valid database rows without touching the real dataset.

⚠ Use a SEPARATE database/profile, never the configured Banca Stato database.

## 1. Demo target website (serve before capturing)

File: `demo-bank.html` (this folder). Serve it locally:

    python -m http.server 8767 -d <this folder>
    URL for the Bot Job:  http://localhost:8767/demo-bank.html

Port 8767 (8000 dev, 8765/8766 presentation players — all taken).
The page is self-contained, labelled "AMBIENTE DIMOSTRATIVO", and has stable
element IDs so Page Scanner / Locator Generator / OCR captures look real.

## 2. Organization / Environment (screenshot 02)

| Field | Value |
| --- | --- |
| Organization | `Banca Demo SA` |
| Environment | `Demo Locale` |
| Base URL | `http://localhost:8767/demo-bank.html` |
| Description | `Ambiente dimostrativo con dati sintetici` |

## 3. Bot Job (screenshots 01, 03, 04, 08–12)

| Field | Value |
| --- | --- |
| Name | `DEMO-Saldo-Clienti` |
| Clone target (shot 04) | `DEMO-Saldo-Clienti-COPIA` |
| Description | `Legge i saldi dei clienti dimostrativi e li esporta in Excel` |

Blocks and commands (enough rows to fill the instruction grid):

    Block 1  ACCESSO
      1  OPEN_URL      http://localhost:8767/demo-bank.html
      2  SET_TEXT      #utente      ← variable ${utente_demo}
      3  SET_TEXT      #parola      ← variable ${parola_demo}   (SECRET)
      4  CLICK         #btn-accedi
    Block 2  LETTURA-SALDI
      5  LOOP          rows of INPUT column "Cliente"
      6  CLICK         row link "Dettagli" for ${cliente_corrente}
      7  READ_TEXT     #saldo-disponibile  → variable ${saldo_letto}
      8  ASSERT        ${saldo_letto} not empty
    Block 3  ESPORTAZIONE
      9  EXCEL_WRITE   output "Saldi-Demo.xlsx", column "Saldo" = ${saldo_letto}

(Exact command names follow the palette of the current build — use the closest
equivalents; the grid content matters more than command spelling.)

## 4. Runtime Variables (screenshot 22)

| Name | Initial value | Notes |
| --- | --- | --- |
| `utente_demo` | `demo.utente` | plain |
| `parola_demo` | VOID | marked SECRET — shows the VOID state |
| `cliente_corrente` | VOID | filled by the loop |
| `saldo_letto` | VOID | filled by READ_TEXT |
| `ambiente` | `Demo Locale` | plain |

## 5. Excel workbook (screenshots 19, 20, 21)

Import `dati-sintetici.csv` (this folder) into `Clienti-Demo.xlsx`,
sheet `Clienti`. Columns: Cliente, Conto, Divisa, Saldo. All values synthetic
(IBAN-like strings use the reserved-looking `CH00 DEMO ...` pattern; names are
"Cliente Demo Uno/Due/Tre..."). REAL memory shot 19 = after one execution with
this workbook; SYNTHETIC shot 20 = generate synthetic rows from the same
columns; shot 21 = ExcelWriter output `Saldi-Demo.xlsx` in a neutral folder
such as `C:\Demo\ARWeb\Output` (create it; never a private user path).

## 6. Capture checklist (before every screenshot)

- Window at the agreed fixed resolution (1440 × 900 recommended) — set once,
  never resized between captures.
- Demo server on 8767 running; app pointed at the demo database/profile.
- No real names, accounts, licenses, tokens, or private paths visible —
  including window titles and status bars.
- File names and states exactly as `../screenshots/README.md` defines.
- Save all 24 PNGs into `../screenshots/` (same folder as its README).
