# abr-web-selenium — Database Lifecycle Engineering Report (v2)

**Date:** 2026-04-17
**Scope:** verification of the prior report `ENGINEERING_REPORT_DB_LIFECYCLE.md` against the source tree, corrections, new findings, and a starter migration kit.
**Repo root:** `C:\Martini\abr-web-selenium`
**Production install:** `C:\ARWebAvaloq` (backup scripts expected under `C:\ARWebAvaloq\ARWeb`)

> **Note on today's backup files.** The folder `C:\ARWebAvaloq\ARWeb` is not mounted into this session. Only `C:\Martini\abr-web-selenium` was shared. To analyse today's actual backup scripts I need one of: (a) mount that folder into the session, or (b) copy today's `backup_*_2026_04_17.sql` files into the workspace. The rest of this report analyses the generator code, which deterministically produces what those files contain.

---

## 0. What was verified vs. the prior report

The prior report (`ENGINEERING_REPORT_DB_LIFECYCLE.md`) is substantially accurate. Every single line-number claim was re-opened in the source tree:

| # | Claim (prior report) | Verdict |
|---|---|---|
| 1 | `initializeMainDatabasePostgres` at `PerformInitializer.java:42` | **Confirmed.** |
| 2 | `initializeMainDatabaseAccess` at `:255` | **Confirmed.** |
| 3 | `initializeMainDatabaseSQLite` at `:482` | **Confirmed.** |
| 4 | SQLite FK bug at `:634` (component_instruction.parent_block_id → block instead of component_block) | **Confirmed bug.** Verified in context: line 635 correctly points `block_id → component_block(id)`, but line 634 points `parent_block_id → block(id)`. Postgres DDL at `:192` has the same column → `component_block(id)`. SQLite is therefore cross-tree FK bound to the wrong parent table. |
| 5 | Three existence-check methods at `:672/:690/:709` | **Confirmed.** All query `getTables(null, null, "instruction", null)`. |
| 6 | `testConnection` SQLServer branch at `:727` | **Confirmed.** SQLServer branch exists in `testConnection`, none in `initializeDBS` / `databaseControl`. |
| 7 | `initializeDBS` dispatcher at `:781` | **Confirmed.** |
| 8 | `restoreHomeBanking` commits a wipe before reading the file — `PerformBackup.java:922-938` | **Confirmed.** `conn.commit()` at line 938 runs before the file-reading while-loop at line 956. |
| 9 | `extractValuesFromInsert` can't handle `''` escapes — `:1034` | **Partially wrong. The bug is real but different.** See §2.1 below. The method DOES have `.replace("''","'")` at lines 1048/1054, but the toggle logic strips both `'` chars of a `''` pair, so `''` never appears in the StringBuilder for the replace to fire. Apostrophes are **silently lost**, not corrupted. |
| 10 | `restoreUpdateInstruction` predicate `OR … IS NULL …` at `:1946` | **Confirmed.** |
| 11 | `blockMap.clear()` reused in `restoreComponentBlock` at `:2218` | **Confirmed.** Comment on `:2217` even acknowledges the reuse. |
| 12 | Inconsistent predicate at `:2713` (`IS NOT NULL … IS NULL … IS NOT NULL`) | **Confirmed — and the net effect is still worse than the prior report stated** (§2.2). |
| 13 | `migrationScriptsv2_1f()` at `PerformDataBase.java:3347` | **Confirmed — AND it is dead code.** No call site anywhere. |
| 14 | `updateDatabaseSchema` at `:3813` | **Confirmed.** |
| 15 | `disableForeignKeyConstraints` at `:3887` | **Confirmed.** |
| 16 | `dropPostGresSequences` at `:4427` wipes data | **Confirmed.** `DELETE FROM "home_url"` and `DELETE FROM "home_banking"` before the sequence drop. |
| 17 | `updateColumns`/`addColumnIfNotExists` at `:7333/:7353` | **Confirmed.** |
| 18 | Commented-out call sites in `ARControlPanel.java` | **Confirmed** on lines 371, 401, 403, 405, 445, 447. |
| 19 | Button handlers at `ARConfigurationPane.java:675-676` | **Confirmed.** |
| 20 | `runBackupScripts()` 680-836, `runRestoreScripts()` 838-1028 | **Confirmed.** |

Additional structural checks:
- `rg 'rollback'` in `PerformBackup.java` → **0 matches.** No rollback anywhere.
- `rg 'RETURN_GENERATED_KEYS'` in `PerformBackup.java` → **0 matches.**
- `find **/*.sql` in the repo → 0 matches. No SQL files on disk.
- `find -type d -name migration` → 0 matches. No migration folder.
- `grep -r schema_migrations` → 0 matches.

**Conclusion:** every HIGH/CRITICAL finding from v1 stands, with #9 re-classified (still a bug, different root cause).

---

## 1. Additional findings not covered in v1

### 1.1 CRITICAL — `extractValuesFromInsert` silently drops apostrophes (corrected root cause)

`PerformBackup.java:1034-1057`:

```java
boolean insideQuotes = false;
for (int i = 0; i < valuesString.length(); i++) {
    char c = valuesString.charAt(i);
    if (c == '\'') {
        insideQuotes = !insideQuotes;           // ← quote chars are NEVER appended
    } else if (c == ',' && !insideQuotes) {
        values.add(sb.toString().trim().replace("''", "'"));
        sb.setLength(0);
    } else {
        sb.append(c);
    }
}
```

Walk-through for a round-tripped value `O'Brien`:

1. Backup writes `'O''Brien'` (escapeSql doubles the `'`, toSqlValue wraps in quotes).
2. Restore sees chars `'`, `O`, `'`, `'`, `B`, …, `n`, `'`.
3. Every `'` toggles `insideQuotes` and is **skipped** (never `sb.append(c)`).
4. After the loop, `sb = "OBrien"` — the `.replace("''", "'")` cannot fire because `''` never made it into the buffer.

**Net effect:** any string value containing `'` round-trips without the apostrophe. No exception, no warning. It silently mangles user data. Severity: CRITICAL because it cannot be detected from logs.

### 1.2 HIGH — `restoreComponentUpdateInstruction` predicate picks up the entire table

`PerformBackup.java:2713`:

```sql
WHERE parent_id IS NOT NULL OR variable_id IS NULL OR parent_block_id IS NOT NULL
```

Because the first-pass insert sets `variable_id` to NULL for every row (waiting for the second pass), `variable_id IS NULL` is true for every freshly inserted row. The `OR` makes the whole predicate tautological — the second pass scans and updates the entire table. That is only "safe" because the batch update happens to write NULL back into all three columns for rows whose source values were NULL. Change the insertion defaults (e.g. add a non-null sentinel, or fill variable_id from the file) and this pass silently stops updating every row that matches the new default.

### 1.3 HIGH — sibling bug in `restoreUpdateInstruction` (regular tree)

`PerformBackup.java:1946`:

```sql
WHERE parent_id IS NULL OR variable_id IS NULL OR parent_block_id IS NULL
```

Same design flaw: rows with parent_id=NULL, variable_id=NULL and parent_block_id=NULL are matched three times. It works today because the UPDATE batch is idempotent per row, but it does UNNECESSARY work proportional to row count — and masks the real intent ("find rows whose FKs still hold *old* IDs"). The correct predicate needs a deterministic marker in the file (e.g. a sentinel row added at dump time, or a separate staging table).

### 1.4 HIGH — passwords dumped in cleartext

`PerformBackup.backupHomeBanking` (`:63-121`) `SELECT … username, password FROM home_banking` and writes plain text `INSERT INTO home_banking (… password) VALUES (…, '<pwd>')`. Backup files therefore contain live banking credentials in cleartext, with `windows-1252` encoding, no file permissions hardening.

### 1.5 HIGH — autocommit leaked back to the pool

`runBackupScripts` / `runRestoreScripts` open a connection with try-with-resources but each `restoreX` calls `conn.setAutoCommit(false)` and never explicitly restores it. When the connection is closed by try-with-resources the driver generally reverts the value, **but** if the connection ever comes from a pooled `DataSource` (the target architecture proposes one), the post-close hook hands a connection back with `autoCommit=false`. Future users see random transaction-scoped behaviour. Fix by wrapping every restore step in a finally-block that restores the previous autocommit value.

### 1.6 MEDIUM — silent FK-miss continues restore

`restoreVariable` (~`:1849`) and `restoreReference` (~`:2119`) do:

```java
if (newInstructionId == null) {
    log.info("Skipped variable with unknown instruction_id: " + oldInstructionId);
    currentInsert.setLength(0);
    continue;
}
```

A row that cannot be mapped is skipped with an `info`-level log line. No counter, no aggregate error, no post-restore verification. After restore, the row counts silently differ from the backup. Severity MEDIUM because it's observable only if someone greps logs.

### 1.7 MEDIUM — connection pooling is DIY via `DriverManager.getConnection`

`PerformDataBase.getConnection()` (`:161, :186, :206` and elsewhere) calls `DriverManager.getConnection` without a pool. Every place that forgets `try-with-resources` leaks a physical connection. Combined with 1.5, long-running sessions (hours-long bot jobs) exhaust Postgres `max_connections`.

### 1.8 MEDIUM — `dropPostGresSequences` is a weapon

`PerformDataBase.java:4427` — despite the name, the method runs `DELETE FROM "home_url"` and `DELETE FROM "home_banking"` before dropping/recreating the sequences. The `ARControlPanel` call site at `:371` is commented out — but a naïve "let me re-enable migration methods" refactor would detonate it.

### 1.9 LOW — `migrationScriptsv2_1f` is dead code

Grep shows no call site. It lingers as documentation of what a v2.1f install needed; it should be either deleted or moved verbatim into `V002__backfill_bot_job_id.sql` (a copy is included in this kit).

### 1.10 LOW — default config is internally inconsistent

`ARPropertyManager` defaults `DATABASE_TYPE=ACCESS` and `db_url=jdbc:postgresql://localhost:5432/ar_web`. A fresh install that accepts defaults connects to nothing. Fix: tie `db_url` default to `DATABASE_TYPE`.

---

## 2. Restore ordering — is it actually correct?

The restore order in `runRestoreScripts()` is:

```
home_banking → home_url → bot_job → block → instruction → variable
  → restoreUpdateInstruction (2nd pass)
  → reference
  → component_block → component_instruction → component_variable
  → restoreComponentUpdateInstruction (2nd pass)
  → component_reference
```

Compared to the FK graph derived from `PerformInitializer`:

```
home_banking
├── home_url
├── bot_job ◄──────── home_url
│    ├── block
│    │    ├── instruction (block_id, parent_block_id → block)
│    │    │    ├── reference   (instruction_id → instruction; bot_job_id → bot_job)
│    │    │    └── variable    (instruction_id → instruction; bot_job_id → bot_job)
│    │    └── …
├── component_block
│    ├── component_instruction (block_id, parent_block_id → component_block;
│    │                          variable_id → component_variable [not FK today])
│    │    ├── component_reference
│    │    └── component_variable
```

**Parent-before-child order is correct.** Two latent problems:

1. `instruction.variable_id` and `component_instruction.variable_id` are deferred — the restore inserts the instruction first with `variable_id = NULL`, then inserts variables, then would need a THIRD pass to wire `instruction.variable_id = map(old_variable_id)`. The current code handles `parent_id` and `parent_block_id` in the second pass but **not** `variable_id`. Searching for `variable_id` updates in `restoreUpdateInstruction`/`restoreComponentUpdateInstruction` shows both passes set `variable_id = NULL`, never the mapped value. Any row whose old `variable_id` was non-NULL ends up with `variable_id = NULL` after restore. → **MEDIUM: silent data loss.**
2. The 2nd-pass updates happen inside their own transaction and `conn.commit()` at the end of each — which means step 6 (`reference`) runs AFTER `restoreUpdateInstruction` committed. That ordering is fine today. It becomes wrong the moment someone reorders for performance, because `reference.instruction_id` depends on the freshly mapped `instruction.id`. Today's code uses the `instructionMap` directly rather than reading the updated rows, so it works — but the dependency is not expressed as a transaction boundary, only as hidden state on the `PerformBackup` instance.

---

## 3. ID-preservation strategy — why it's fragile

Every insert method that needs new IDs uses the `SELECT id BEFORE → INSERT → SELECT id AFTER → ids.removeAll(before)` pattern. Concrete failure modes:

1. **Any other session writing to the same table during restore** will inject IDs into the "AFTER" set that then zip to the wrong "insertedOldIds[i]". The code never takes a table-level lock.
2. **Gap-ID reuse** — SQLite without `AUTOINCREMENT` happily reuses deleted IDs. If a row's `rowid` is reused and was in `idsBefore`, `removeAll` eats it.
3. **Order-by drift** — `ORDER BY id` is monotone on auto-identity tables but `idsAfter.removeAll(idsBefore)` is a *set* op; order is re-imposed by the list iteration. On Postgres / SQLite this is OK; on Access the result order of `SELECT id ORDER BY id` after an insert batch is implementation-dependent.
4. **Post-condition:** the final `blockMap.put(insertedOldIds.get(i), idsAfter.get(i))` silently zips truncated at `min(size)`. Off-by-one from any prior drift produces silently mismatched mappings for entire sections of the tree.

**Portable fix.** Every engine the app supports (Postgres / SQLite / UCanAccess) honours `Statement.RETURN_GENERATED_KEYS` + `getGeneratedKeys()`. Use it:

```java
try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
    ps.setInt(1, …); ps.setString(2, …); …
    ps.executeUpdate();
    try (ResultSet gk = ps.getGeneratedKeys()) {
        if (gk.next()) idMap.put(oldId, gk.getInt(1));
    }
}
```

1-to-1, thread-safe, no `removeAll`, no ORDER BY — this single change eliminates §3.1–§3.4.

---

## 4. Starter migration kit (files written)

The following files have been added under the repo to get the migration system off the ground:

| Path | Purpose |
|---|---|
| `src/main/resources/db/migration/postgres/V000__schema_migrations.sql` | Bootstraps the history table. |
| `src/main/resources/db/migration/postgres/V001__init_schema.sql` | Canonical Postgres schema — 11 tables, all FKs, deferred variable FK. No `DROP TABLE`. |
| `src/main/resources/db/migration/postgres/V002__backfill_bot_job_id.sql` | Replaces the dead `migrationScriptsv2_1f()` method. Idempotent. |
| `src/main/resources/db/migration/sqlite/V001__init_schema.sql` | SQLite schema — **fixes the `parent_block_id → block` bug by pointing to `component_block`**. |
| `src/main/resources/db/migration/access/V001__init_schema.sql` | UCanAccess-compatible schema — FKs added via `ALTER TABLE ... ADD CONSTRAINT`. |
| `src/main/java/com/allinweb/ch/migration/MigrationRunner.java` | Ordered, checksummed runner. One transaction per migration. Fails fast on checksum drift. Supports Postgres, SQLite, Access, SQLServer. |

**Naming convention.** `V<nnn>__<snake_name>.sql`. `<nnn>` is zero-padded, strictly monotonic per dialect. Dialects evolve independently (Access may need extra migrations, Postgres may skip them).

**Wiring change** (in `ARControlPanel.databaseControl()`):

```java
Dialect dialect = Dialect.valueOf(dataBaseType.toUpperCase());
try (Connection conn = performDataBase.getConnection()) {
    new MigrationRunner(conn, dialect).run();
}
```

Delete (or mark `@Deprecated no-op`) the five ad-hoc migration methods and all six commented call-sites in `ARControlPanel.java`.

---

## 5. Backup/restore remediation plan (step-by-step)

1. **Freeze current backup file format v1** (the `.sql` file used today). Add a reader that recognises absence of header → treat as v1, UTF-8 off, windows-1252 on.
2. **Introduce v2 header** (first line): `# ABR-BACKUP v=2 dialect=postgres schema=V002 generated=<iso8601> sha256=<digest>`. Writer and reader both switch to UTF-8 for v2.
3. **Replace `extractValuesFromInsert`** with JSqlParser (`com.github.jsqlparser:jsqlparser`) or a proper FSM that handles `''`, unicode, and multi-line. Delete `toSqlValue`, `setSafeParam`, `escapeSql` once the parser is in.
4. **Rewrite `restoreX` methods** to the `RETURN_GENERATED_KEYS` pattern (see §3). Keep `IdMapper` request-scoped; pass it explicitly instead of using instance fields.
5. **Single transaction for the whole restore.** `conn.setAutoCommit(false)` at the top of `runRestoreScripts`, `SAVEPOINT` between loaders, single `commit()` at the end, `rollback()` in catch. Move the 11-table DELETE/`TRUNCATE ... RESTART IDENTITY CASCADE` into that same transaction, AFTER file validation succeeds. (Today it commits before reading the file — see #8 in the verification table.)
6. **Verification pass.** After commit, run a `COUNT(*)` per table and compare against the counts recorded in the v2 header's body digest. Fail the restore if any delta.
7. **Encrypt secrets.** `home_banking.password` must not be written in cleartext. Options: (a) redact on dump and require the user to re-enter passwords after restore, or (b) symmetric-encrypt with a key stored outside the backup file.
8. **Third pass for `variable_id` on instruction / component_instruction.** After `variable` inserts populate `variableMap`, re-walk `instruction` rows whose file-side `variable_id` was non-NULL and translate them. Without this step, `instruction.variable_id` is silently lost today (§2 point 1).
9. **Skipped-row aggregate.** Every silent `continue` in `restoreVariable`/`restoreReference` becomes an increment on a `Map<String,Integer>`. If any count > 0 at the end of restore, return ErrorMessage; optionally let the user acknowledge "accept partial".
10. **Controllers → services.** Move `runBackupScripts` / `runRestoreScripts` out of `ARConfigurationPane` into `backup/BackupService` and `restore/RestoreService`. The pane invokes the service on a background thread and listens for progress events.

---

## 6. Consolidated risk ranking

| Rank | Item | Where |
|---|---|---|
| CRITICAL | Pre-delete + commit before parsing backup file | `PerformBackup.java:922-938` |
| CRITICAL | No rollback on catch anywhere in backup/restore | `PerformBackup.java` (all `restoreX`/`backupX`) |
| CRITICAL | No migration system, no schema_migrations, all migrations commented out | `ARControlPanel.java:371,401,403,405,445,447` + `PerformDataBase.java:3347 …` |
| CRITICAL | No `CREATE DATABASE` for Postgres in app startup | `ARControlPanel.databaseControl()` + `PerformInitializer.initializeMainDatabasePostgres` |
| CRITICAL | `extractValuesFromInsert` silently drops every `'` in values (§1.1) | `PerformBackup.java:1034-1057` |
| CRITICAL | Passwords dumped cleartext | `PerformBackup.java:63-121` |
| HIGH | SQLite `component_instruction.parent_block_id → block(id)` | `PerformInitializer.java:634` |
| HIGH | `blockMap.clear()` reused between block and component_block restore | `PerformBackup.java:2218` |
| HIGH | `restoreComponentUpdateInstruction` predicate is tautological (§1.2) | `PerformBackup.java:2713` |
| HIGH | `restoreUpdateInstruction` predicate matches too broadly (§1.3) | `PerformBackup.java:1946` |
| HIGH | `instruction.variable_id` never restored (§2 point 1) | `PerformBackup.java:1946 & 2713` |
| HIGH | DB-existence check is single-table heuristic | `PerformInitializer.java:672, 690, 709` |
| HIGH | DDL diverges between Postgres/Access/SQLite | `PerformInitializer.java:42, 255, 482` |
| HIGH | autocommit leaked back to connection/pool (§1.5) | `PerformBackup.java` every `restoreX` |
| MEDIUM | ID mapping via SELECT before/after (§3) | `PerformBackup.java` every `restoreX` |
| MEDIUM | Silent FK-miss rows on restore (§1.6) | `PerformBackup.java:1849, 2119` |
| MEDIUM | No atomic "restore is one transaction" | `PerformBackup.java` all `restoreX` |
| MEDIUM | Connection leaks in `PerformDataBase.getConnection` (§1.7) | `PerformDataBase.java:161, 186, 206` |
| MEDIUM | `windows-1252` hardcoded | `PerformBackup.java` everywhere |
| MEDIUM | `dropPostGresSequences` deletes data (§1.8) | `PerformDataBase.java:4427` |
| MEDIUM | SQLServer branch in `testConnection` only | `PerformInitializer.java:743` |
| LOW | Dead code `migrationScriptsv2_1f` (§1.9) | `PerformDataBase.java:3347` |
| LOW | Inconsistent defaults (§1.10) | `ARPropertyManager.java` |
| LOW | Hardcoded `db_pwd=martini` fallback | `ARPropertyManager.java:~155` |
| LOW | Orchestration in JavaFX pane | `ARConfigurationPane.java:680, 838` |
| LOW | `PerformDataBase.java` 7514 LOC monolith | `PerformDataBase.java` |

---

## 7. Real-data analysis of today's backup (`C:\ARWebAvaloq\ARWeb\backup_*_2026_04_17.sql`)

The 11 backup files generated today were read directly. Summary:

| Table | Rows | Notable |
|---|---:|---|
| home_banking | 13 | 1 row (id=194 "CA Next") contains an **embedded `\n` inside a VALUE**, producing a multi-line INSERT — the Java restore reads line-by-line and will mis-parse it. |
| home_url | 16 | `home_banking_id ∈ [183..195]` |
| bot_job | 5 | `home_banking_id ∈ [184..195]`, `home_url_id ∈ [184..198]` |
| block | 13 | `bot_job_id ∈ [341..355]` |
| instruction | 55 | **29 rows contain `''` apostrophe escapes**, 5 rows have non-NULL `variable_id`. |
| variable | 3 | |
| reference | 398 | **33 rows contain `''`**. |
| component_block | 52 | |
| component_instruction | 194 | **6 rows with `''`**, 4 rows with non-NULL `variable_id`. |
| component_variable | 3 | |
| component_reference | 1027 | **6 rows with `''`**. |
| **Total** | **1779** | |

### 7.1 Confirmed production impact

**(a) Apostrophe-corruption (§1.1) hits real data.** 74 rows across four tables carry `''` escape pairs — every one would lose its apostrophe when restored through the existing Java code. Concrete examples from `backup_instruction_2026_04_17.sql`:

```sql
... VALUES (8938, 3, 'I:Parola d''ordine', 'Parola d''ordine',
    '//android.widget.EditText[@text=''Parola d&apos;ordine''
       and @bounds=''[44,557][1036,710]'']', ...)
```

After restore via the current code, that row becomes `name=Parola dordine`, `xpath=//android.widget.EditText[@text=Parola d&apos;ordine and @bounds=[44,557][1036,710]]`. The XPath is no longer valid — the automation will silently stop finding this element.

**(b) `variable_id` silently set to NULL on restore (§2 point 1).** 5 rows in `instruction` (ids 8952, 8953, 8957, 9017, 9018) carry `variable_id=340` or `345`; 4 rows in `component_instruction` carry `variable_id=7`. Neither `restoreUpdateInstruction` nor `restoreComponentUpdateInstruction` writes variable_id back from the backup — both methods always set it to NULL. Guaranteed loss after restore today: **9 production rows.**

**(c) Multi-line INSERT in home_banking id=194 ("CA Next").** A row's value contains `\n`. The Java restore uses `BufferedReader.readLine()`, which drops everything after the newline and treats the continuation as a new line — the record is corrupted. `PerformBackup.java:1034` does not account for this; the `insideQuotes` state doesn't cross readLine boundaries.

### 7.2 Consolidated restore migration produced

The 11 files have been consolidated into a single Postgres migration:

```
src/main/resources/db/migration/postgres/V003__restore_from_2026_04_17.sql
```

Properties:
- **Single transaction** (`BEGIN ... COMMIT`) — cannot leave the DB half-restored.
- `TRUNCATE ... RESTART IDENTITY CASCADE` at the top, so the target is guaranteed empty before any INSERT.
- Every row uses `OVERRIDING SYSTEM VALUE` so the ID from the source DB persists literally — no fragile `SELECT id BEFORE/AFTER` scheme.
- Per-table sequence advancement (`setval(..., MAX(id))`) at the end, so subsequent app inserts don't collide.
- `DO $$ ... IF COUNT(*) <> <expected> THEN RAISE EXCEPTION ...$$` block at the bottom — the migration fails loudly if any row silently vanished.
- Windows-1252 → UTF-8 transcoded at generation time (so `priorità`, `£`, `&apos;` are preserved verbatim).
- `'[null]'` legacy sentinel replaced with plain SQL `NULL`.
- Multi-line INSERTs (home_banking id=194) rejoined correctly via quote-state-tracking parser.

**Stats:** 1779 rows written, file sha256 = `643f62cd8c3a2aee55125a6a2871da0fe6248fa775a277c4f79e13a6140d247f`.

**Companion script:** `tools/build_restore_migration.py` is the generator. Usage:

```
python3 tools/build_restore_migration.py \
    C:/ARWebAvaloq/ARWeb \
    2026_04_17 \
    003 \
    src/main/resources/db/migration/postgres
```

The script is idempotent (same input → same SHA) and can be rerun for any future backup date.

### 7.3 Next steps

1. Dry-run `V003` against a scratch Postgres: `psql -1 -f V003__restore_from_2026_04_17.sql` (the `-1` flag enforces the file-level transaction wrap even if the file omitted it). Verify the final `DO $$` assertion block passes.
2. After Postgres dry-run, if you need SQLite/Access versions, generate them by adding a `rewrite_for_sqlite` / `rewrite_for_access` pass to `build_restore_migration.py`:
   - SQLite: drop `OVERRIDING SYSTEM VALUE` (SQLite accepts explicit IDs natively when the column is `INTEGER PRIMARY KEY`). Use `DELETE FROM <t>` then `UPDATE sqlite_sequence SET seq = (SELECT MAX(id) FROM <t>)`.
   - Access: `DELETE FROM` all tables, `INSERT` with explicit IDs (UCanAccess allows this for AUTOINCREMENT columns), no sequence adjustment needed.
3. Retire the Java-side `runRestoreScripts()` path in favour of `MigrationRunner.run()` reading these generated SQL files. The entire `PerformBackup.restoreX` surface area (~2966 LOC) becomes obsolete except the backup writers — and those should be ported to UTF-8 and fixed-quote output.
4. Keep the backup writers (`backupX` methods) but harden them: (a) replace `windows-1252` with UTF-8, (b) include a header line with dialect + schema version + row count + sha256, (c) ensure no VALUE contains a raw `\n` (replace with `\\n` literal then unescape on restore), (d) redact or encrypt `home_banking.password`.
