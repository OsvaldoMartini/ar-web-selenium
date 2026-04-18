# abr-web-selenium — Database Lifecycle Engineering Report

**Scope:** creation, migrations, backup, restore, ID preservation, and the refactoring gap between the current unified implementation and a modular target.
**Repo root:** `C:\Martini\abr-web-selenium`
**Files audited:** `ARControlPanel.java`, `facade/PerformDataBase.java` (7514 LOC), `facade/PerformBackup.java` (2966 LOC), `facade/PerformInitializer.java` (841 LOC), `facade/PerformDBScripts.java`, `component/pane/ARConfigurationPane.java` (1764 LOC), `util/ARPropertyManager.java`, `README-DATABASE.md`.

---

## A. Current Architecture — findings grounded in code

### A.1 First-run / database-existence bootstrap
Entry point: `ARControlPanel.databaseControl()` (L340). Flow:

1. `performDataBase.initialize(dataBaseType)` opens a connection based on the `DATABASE_TYPE` property (Postgres / Access / SQLite — SQLServer is half-wired).
2. Depending on flags `POSTGRES_DB | ACCESS_DB | SQLITE_DB`, the controller calls `performInitializer.doesNotInstructionTableExist{,Access,SQLITE}()` (L672/690/709 of `PerformInitializer`). Those three methods are identical: they query `conn.getMetaData().getTables(null, null, "instruction", null)` and return `!rs.next()`.
3. If the `instruction` table is missing, the controller calls the engine-specific DDL: `initializeMainDatabasePostgres()` L42, `initializeMainDatabaseAccess(dbFile)` L255, `initializeMainDatabaseSQLite(dbFile)` L482.
4. A second entry point exists: `performInitializer.initializeDBS()` L781 is called by `PerformDataBase.changeDbConnection()`, and does the same dispatch.

**Problems in this path:**

- The DB-existence check is a **single-table heuristic**. It only looks for `instruction`. If `instruction` exists but `component_reference` (or any other table) is missing — e.g. after a half-successful upgrade — the init path is skipped and the app crashes later during runtime with opaque JDBC errors.
- **No `CREATE DATABASE` call** anywhere in the Java startup code. For Postgres, the application assumes `ar_web` already exists. The only code that can create it is the standalone CLI `DatabaseConnectorPostgres.java` documented in `README-DATABASE.md`, which is not invoked from app startup. First-run experience for a new Postgres user: the app refuses to connect.
- `initializeMainDatabasePostgres()` starts with `DROP TABLE IF EXISTS ... CASCADE` for all 11 tables *before* `CREATE`. If the existence check ever incorrectly reports "instruction doesn't exist" on a populated DB (e.g. connection targets the wrong schema), a single call wipes everything.
- `testConnection()` (L727) branches on `SQLServer`, but `initializeDBS` and `databaseControl` have **no SQLServer branch** — choosing SQLServer silently falls through to nothing.
- DDL is three independent Java string blobs — Postgres uses `INTEGER GENERATED ALWAYS AS IDENTITY`; Access uses `AUTOINCREMENT` + separate `ALTER TABLE … ADD CONSTRAINT … FOREIGN KEY`; SQLite uses `INTEGER PRIMARY KEY AUTOINCREMENT` with inline FK. They have already drifted.
- **Confirmed SQLite bug** at `PerformInitializer.java:634`:

```sql
CREATE TABLE IF NOT EXISTS component_instruction (
    ...
    parent_block_id INTEGER,
    FOREIGN KEY(parent_block_id) REFERENCES block(id) ON DELETE CASCADE
    --                                        ^^^^^ should be component_block(id)
);
```

### A.2 Migration "system"
**There is no migration system.** There is no `/migration` folder anywhere in the repository. There are no `.sql` files under `src/main/resources/`. There is no `schema_migrations` / `flyway_schema_history` / `liquibase_changelog` table.

What exists is a scattered set of Java methods that do ad-hoc one-shot schema/data changes:

| Method | Location | What it does |
|---|---|---|
| `migrationScriptsv2_1f()` | `PerformDataBase.java:3347` | Backfills `bot_job_id` on `instruction`, `reference`, `complex_instruction`; sets `active = true` on `instruction` and `block`. |
| `updateDatabaseSchema(dbUrl, dbFile)` | `PerformDataBase.java:3813` | Adds `FK_NewHomeURL` on `bot_job.home_url_id` (Access-only, uses `DatabaseMetaData.getImportedKeys`). |
| `disableForeignKeyConstraints(dbUrl)` | `PerformDataBase.java:3887` | Iterates all tables and drops every FK it finds. |
| `updateColumns()` → `addColumnIfNotExists()` | `PerformDataBase.java:7333 / 7353` | Adds `parent_block_id INTEGER` to `instruction` and `component_instruction` if missing. |
| `dropPostGresSequences()` | `PerformDataBase.java:4427` | **Misnamed.** Actually runs `DELETE FROM home_url`, `DELETE FROM home_banking`, drops `home_url_id_seq` / `home_banking_id_seq`, recreates them. |

**Every single one of these is commented out** at the call site:

```java
// ARControlPanel.java
:371   //            performDataBase.dropPostGresSequences();
:401   //                    performDataBase.updateColumns();
:403   //                performDataBase.disableForeignKeyConstraints(dbUrl);
:405   //                                    performDataBase.updateDatabaseSchema(dbUrl, dbFile);
:445   //                performDataBase.disableForeignKeyConstraints(dbUrl);
:447   //                                    performDataBase.updateDatabaseSchema(dbUrl, dbFile);
```

Effectively: **schema evolution is manual.** A developer uncomments a line, ships a build, then re-comments it before the next release. There is no history, no idempotency, no rollback, no ordering, and no way to answer "what schema version is this install on?".

### A.3 Backup sequence
Invocation: `ARConfigurationPane.backupDBButton.setOnMouseClicked(e -> runBackupScripts())` (L675).
Sequence inside `runBackupScripts()` (L680-836), guarded by `if (errorMessage == null)`:

```
backupHomeBanking
→ backupHomeUrl
→ backupBotJob
→ backupBlock
→ backupInstruction
→ backupVariable
→ backupReference
→ backupComponentBlock
→ backupComponentInstruction
→ backupComponentVariable
→ backupComponentReference
```

Each `backupX` opens a `PrintWriter` on `backup_<entity>_<yyyyMMdd>.sql` with hard-coded `windows-1252` encoding, iterates the table with a `PreparedStatement`, and writes human-readable `INSERT INTO … VALUES (…);` lines, calling `escapeSql()` which replaces `'` with `''`.

The ordering is parent-first, which matters only for file readability (children reference parents). It does **not** matter for the restore path because restore wipes everything first (see A.4). There is no checksum, no format-version header, no schema-version marker in any backup file.

### A.4 Restore sequence and the pre-delete
Invocation: `ARConfigurationPane.restoreDBButton.setOnMouseClicked(e -> runRestoreScripts())` (L676).
Sequence inside `runRestoreScripts()` (L838-1028):

```
restoreHomeBanking   (← this also wipes the entire DB first)
→ restoreHomeUrl
→ restoreBotJob
→ restoreBlock
→ restoreInstruction
→ restoreVariable
→ restoreUpdateInstruction           (2nd pass: resolves parent_id / variable_id / parent_block_id)
→ restoreReference
→ restoreComponentBlock
→ restoreComponentInstruction
→ restoreComponentVariable
→ restoreComponentUpdateInstruction  (2nd pass for component_instruction)
→ restoreComponentReference
```

`restoreHomeBanking` begins at `PerformBackup.java:922-938`:

```java
conn.setAutoCommit(false);
stmt.executeUpdate("DELETE FROM component_reference");
stmt.executeUpdate("DELETE FROM component_variable");
stmt.executeUpdate("DELETE FROM component_instruction");
stmt.executeUpdate("DELETE FROM component_block");
stmt.executeUpdate("DELETE FROM reference");
stmt.executeUpdate("DELETE FROM variable");
stmt.executeUpdate("DELETE FROM instruction");
stmt.executeUpdate("DELETE FROM block");
stmt.executeUpdate("DELETE FROM bot_job");
stmt.executeUpdate("DELETE FROM home_url");
stmt.executeUpdate("DELETE FROM home_banking");
conn.commit();                                  // <-- committed BEFORE reading the backup file
```

**This is a data-loss bomb.** If the backup file is corrupt, missing, encoded differently, or if any subsequent `restoreX` throws, the database is already empty and the commit has already happened. There is no way back.

### A.5 ID preservation — how it actually works
Every `restoreX` that inserts auto-ID rows uses the same pattern:

```java
List<Integer> idsBefore = SELECT id FROM <table> ORDER BY id;
// … parse backup file, remember old IDs in insertedOldIds, execute inserts …
List<Integer> idsAfter  = SELECT id FROM <table> ORDER BY id;
idsAfter.removeAll(idsBefore);                  // "new" IDs
// zip old→new into a TreeMap<Integer,Integer>
for (int i = 0; i < insertedOldIds.size(); i++) {
    map.put(insertedOldIds.get(i), idsAfter.get(i));
}
```

Second-pass methods (`restoreUpdateInstruction` L1946, `restoreComponentUpdateInstruction` L2713) then translate the stored old FK values via the maps.

**Failure modes of this strategy:**

- Not safe under concurrent writes — any other session inserting during restore injects an ID into `idsAfter` that gets zipped against the wrong old ID.
- Not safe if the DBMS *reuses* gap IDs (Access does, some SQLite configs do) — `removeAll` can return rows that were never part of this restore.
- Fragile to `ORDER BY id` semantics across engines when a backup file generated on Postgres is restored into Access or vice-versa.
- It happens to work today only because restore runs single-threaded and nobody writes during it.

**Cleaner alternative:** use `PreparedStatement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)` + `getGeneratedKeys()` to read back the generated ID of each row as you insert it. JDBC delivers them 1:1 in insertion order, no cross-session interference. This is the only portable, thread-safe approach.

### A.6 Known defects in the restore code

**Defect 1 — shared mutable map reused between regular and component restores.** `PerformBackup.java:2218`:

```java
// restoreComponentBlock
blockMap.clear();                 // this is the SAME map used by block/instruction restore
```

The code reuses `blockMap` to hold component_block mappings. In the current call order no consumer of the "regular" `blockMap` runs after `restoreComponentBlock`, so the bug is latent — but any future refactor that reorders the sequence will silently produce wrong FK translations. This is the single scariest latent bug in the file.

**Defect 2 — inconsistent predicate in second-pass updates.** Compare:

```java
// restoreUpdateInstruction  (PerformBackup.java:1946)
SELECT id FROM instruction
 WHERE parent_id IS NULL OR variable_id IS NULL OR parent_block_id IS NULL

// restoreComponentUpdateInstruction  (PerformBackup.java:2713)
SELECT id FROM component_instruction
 WHERE parent_id IS NOT NULL OR variable_id IS NULL OR parent_block_id IS NOT NULL
```

The second predicate logically matches everything — `variable_id IS NULL` is true for every just-inserted row because the first pass stores NULL there. Coincidence covers the bug. Change the insertion defaults and the second pass silently stops updating rows.

**Defect 3 — no rollback on catch.** Every `restoreX` does `conn.setAutoCommit(false)` and ends with `conn.commit()` on success, but the catch block just returns an `ErrorMessage`:

```java
} catch (SQLException e) {
    logDB.error(…);
    return new ErrorMessage("Restore", "…", e.getMessage());
    // ^^ no conn.rollback()
}
```

When the try-with-resources closes the connection, the driver's implicit rollback fires for the current step only. Every **previous** step already committed. Net result: a partial restore commits half the state then aborts.

**Defect 4 — parser can't handle SQL-escaped quotes or multi-line values.** `extractValuesFromInsert` L1034 reads lines with `BufferedReader.readLine()` and toggles `insideQuotes` on every `'`. SQL's standard escape for a single quote inside a literal is `''` — two successive toggles resolve to "in, out" and the parser sees a field boundary that isn't there. Any value containing `'`, or a newline, corrupts the restore.

**Defect 5 — `windows-1252` hardcoded** for both backup (writer) and restore (reader). Anything outside Latin-1 (curly quotes in descriptions, emoji, non-Western names) round-trips as mojibake.

### A.7 Monolith inventory
The user's request mentions a "new folder/module structure". **That structure does not yet exist.** The current physical layout under `src/main/java/com/allinweb/ch/`:

```
ARControlPanel.java           (startup orchestrator — calls databaseControl())
builder/                      (unrelated)
component/                    (JavaFX panes, including ARConfigurationPane.java with runBackup/runRestore)
control/  driver/  executors/ (unrelated runtime)
facade/
  ├── PerformDataBase.java    ← 7514 LOC / 336 KB — the unified facade
  ├── PerformBackup.java      ← 2966 LOC / 138 KB — backup + restore
  ├── PerformInitializer.java ←  841 LOC — three DDL blocks + existence checks + dispatcher
  ├── PerformDBScripts.java   ←   69 LOC — one helper (deleteNullBlocksSQL)
  ├── PerformActions.java     (206 KB — unrelated runtime)
  ├── PerformLists.java, PerformMessage.java, …
license/  model/  readersAndWriters/  socket/  util/  vision/
```

There is no `migration/`, no `backup/`, no `restore/`, no `repository/`, no `db/` package. The methods are not "migrated" anywhere — they still live in the monolith.

---

## B. Problems — a consolidated list

1. **Destructive pre-delete before restore file validation.** `PerformBackup.java:922-938`.
2. **No rollback on catch in backup/restore steps.** All `restoreX` / `backupX` methods.
3. **No per-run atomic transaction around the 11 steps of restore.** Each step commits independently.
4. **No migration system; ad-hoc commented-out methods.** `ARControlPanel.java:371/401/403/405/445/447`.
5. **No `CREATE DATABASE` for Postgres in startup.** `ARControlPanel.databaseControl()` + `PerformInitializer.initializeMainDatabasePostgres`.
6. **Existence check uses a single-table heuristic.** `PerformInitializer.java:672/690/709`.
7. **SQLite `component_instruction.parent_block_id` references the wrong table** (`block` instead of `component_block`). `PerformInitializer.java:634`.
8. **DDL diverges between the three engines** — three independent Java-string blobs. `PerformInitializer.java:42/255/482`.
9. **SQLServer branch in `testConnection` but no matching DDL/init path.** Silent fall-through.
10. **ID mapping via `SELECT id BEFORE/AFTER`** is fragile under concurrency and gap-ID reuse.
11. **`blockMap.clear()` in `restoreComponentBlock` reuses the regular block map** for component mappings. Latent bug.
12. **Inconsistent SELECT predicate** in `restoreComponentUpdateInstruction` vs `restoreUpdateInstruction`.
13. **Hand-rolled SQL parser** in `extractValuesFromInsert` cannot handle `''` escapes or multi-line values.
14. **`windows-1252` hardcoded** everywhere backup/restore touches text.
15. **`dropPostGresSequences` is misnamed**: actually deletes `home_url` and `home_banking` data.
16. **Hardcoded credential fallback `db_pwd=martini`** in `ARPropertyManager`.
17. **Inconsistent default config**: `DATABASE_TYPE=Access` default but `db_url` default is a Postgres URL.
18. **`PerformDataBase.java` is a 7514-LOC monolith** mixing connection mgmt, CRUD, DDL, data migration, and compaction.
19. **`PerformDBScripts.java` ships one method; remaining scaffolding (singleton, unused constants) is dead code.**
20. **Controller layer contains orchestration logic** (`ARConfigurationPane.runBackupScripts` / `runRestoreScripts`) that belongs in a service.

---

## C. Method-migration audit — unified vs. modular

**Conclusion first:** the audit the user requested assumed a partial refactor. The reality is that **no methods have been migrated yet**. Everything still lives inside the original unified files. What follows is a target-state mapping so the refactor can start with a known destination.

| Concern | Currently in… | Should live in… |
|---|---|---|
| Connection open / dialect resolution | `PerformDataBase.getConnection()`, `PerformDataBase.changeDbConnection()`, `PerformDataBase.initialize(dataBaseType)` | `db/ConnectionManager.java`, `db/Dialect.java` |
| Raw SQL dialect quirks | scattered `if (POSTGRES_DB) { … } else { … }` branches across ~40 methods | `db/SqlDialectHelper.java` |
| Schema creation (three DDL blobs) | `PerformInitializer.initializeMainDatabase{Postgres,Access,SQLite}()` | `resources/db/migration/{postgres,access,sqlite}/V001__init_schema.sql` |
| DB existence check | `PerformInitializer.doesNotInstructionTableExist{,Access,SQLITE}` | `migration/MigrationRunner.ensureSchemaPresent()` (inspects `schema_migrations` instead of a single table) |
| Ad-hoc schema updates | `PerformDataBase.updateDatabaseSchema`, `updateColumns`, `addColumnIfNotExists`, `disableForeignKeyConstraints`, `dropPostGresSequences`, `migrationScriptsv2_1f` | `resources/db/migration/<dialect>/V002__*.sql`, `V003__*.sql`, … |
| First-run dispatcher | `ARControlPanel.databaseControl()` + `PerformInitializer.initializeDBS()` | `migration/MigrationRunner.run()` invoked once at startup |
| CRUD per entity | ~80 methods inside `PerformDataBase.java` | `repository/{HomeBanking,HomeUrl,BotJob,Block,Instruction,Variable,Reference,ComponentBlock,ComponentInstruction,ComponentVariable,ComponentReference}Repository.java` |
| Backup orchestration | `ARConfigurationPane.runBackupScripts()` | `backup/BackupService.run(Path target)` |
| Per-entity backup writers | `PerformBackup.backupHomeBanking … backupComponentReference` | `backup/dump/<Entity>Dumper.java`, shared `backup/BackupWriter` for file/header/checksum |
| Restore orchestration | `ARConfigurationPane.runRestoreScripts()` | `restore/RestoreService.run(Path source)` (one transaction, savepoints between loaders) |
| Per-entity restore loaders | `PerformBackup.restoreHomeBanking … restoreComponentReference` | `restore/loader/<Entity>Loader.java` |
| SQL file parsing | `PerformBackup.extractValuesFromInsert`, `setSafeParam`, `toSqlValue`, `escapeSql` | `restore/BackupReader.java` (real lexer, JSqlParser or hand-written state machine with `''` escape + multi-line) |
| ID mapping | shared mutable fields on `PerformDataBase` (`blockMap`, `instructionMap`, `homeBankMap`, …) | request-scoped `restore/IdMapper` (one map per table, passed explicitly) |
| Delete-before-restore | `PerformBackup.restoreHomeBanking` L922-938 | Deleted. Replace with `TRUNCATE … RESTART IDENTITY` inside the outer transaction — *after* file validation succeeds. |

---

## D. Root cause analysis

1. **Organic growth without module boundaries.** Every feature landed as "add a method to the existing big class." The facade became a dumping ground because there was no rule to say where a new method belonged.
2. **No dedicated migration runner from day one.** Every schema change shipped as a new Java method on the facade, plus a comment/uncomment ritual in the controller. Over ~7 years of work, this accreted into the current tangle.
3. **DDL is Java strings, one per engine.** There was never a single canonical schema. The three strings drifted (different FK rules, different naming, one known wrong FK target).
4. **Back-end error convention is `ErrorMessage` return, not exceptions.** The convention is sensible for the UI but it leaked into transaction code: catch blocks return an `ErrorMessage` instead of issuing a `rollback()`.
5. **ID mapping lives on the facade as mutable shared state.** That coupling prevents splitting backup and restore into services without rewriting the ID-mapping contract first — which is why the split has not happened.
6. **The controller layer does orchestration.** `ARConfigurationPane.runRestoreScripts` chains 13 facade calls with `if (errorMessage == null)` guards. A service layer would centralise transaction boundaries and rollback; placing the logic in a JavaFX pane guarantees it can't participate in a single JDBC transaction.

---

## E. Recommended target architecture

```
com.allinweb.ch/
├── db/
│   ├── ConnectionManager.java            // open, close, dialect-aware pool, testConnection
│   ├── Dialect.java                      // enum { POSTGRES, ACCESS, SQLITE, SQLSERVER }
│   └── SqlDialectHelper.java             // dialect-specific quoting, IF NOT EXISTS, LIMIT, RETURNING
├── migration/
│   ├── MigrationRunner.java              // loads V*.sql, executes in order, writes schema_migrations
│   ├── Migration.java                    // record(version, name, checksum, sql)
│   ├── SchemaMigrationsRepository.java   // CRUD on schema_migrations table
│   └── resources/db/migration/
│        ├── postgres/
│        │    ├── V001__init_schema.sql
│        │    ├── V002__backfill_bot_job_id.sql
│        │    ├── V003__add_parent_block_id.sql
│        │    └── V004__fk_new_home_url.sql
│        ├── sqlite/   V001__init_schema.sql, V002__…, V003__…
│        └── access/   V001__init_schema.sql, V002__…, V003__…
├── repository/
│   ├── HomeBankingRepository.java        (moved out of PerformDataBase)
│   ├── HomeUrlRepository.java
│   ├── BotJobRepository.java
│   ├── BlockRepository.java
│   ├── InstructionRepository.java
│   ├── VariableRepository.java
│   ├── ReferenceRepository.java
│   ├── ComponentBlockRepository.java
│   ├── ComponentInstructionRepository.java
│   ├── ComponentVariableRepository.java
│   └── ComponentReferenceRepository.java
├── backup/
│   ├── BackupService.java                // orchestrator: one output, 11 dumpers
│   ├── BackupWriter.java                 // UTF-8, version header, checksum, per-entity sections
│   └── dump/<Entity>Dumper.java
├── restore/
│   ├── RestoreService.java               // one transaction + savepoints, validates file first
│   ├── BackupReader.java                 // proper SQL lexer (JSqlParser or FSM)
│   ├── IdMapper.java                     // request-scoped Map<Table, Map<OldId,NewId>>
│   └── loader/<Entity>Loader.java
└── facade/
    └── PerformDataBase.java              // thin delegate, <500 LOC
```

---

## F. Concrete remediation plan

### F.1 Migration runner (replace all current commented-out methods)

**Naming convention:** `V<nnn>__<snake_name>.sql`, zero-padded to 3 digits, monotonically increasing per dialect.

**`schema_migrations` table (created by V001 or bootstrap code):**

```sql
CREATE TABLE IF NOT EXISTS schema_migrations (
    version     VARCHAR(20)  PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    checksum    CHAR(64)     NOT NULL,
    applied_at  TIMESTAMP    NOT NULL,
    success     BOOLEAN      NOT NULL
);
```

**`MigrationRunner` sketch:**

```java
public final class MigrationRunner {
    private final DataSource ds;
    private final Dialect dialect;
    private final Clock clock;

    public void run() throws SQLException, IOException {
        ensureDatabaseExists();              // Postgres: CREATE DATABASE IF NOT EXISTS ar_web
                                             // Access/SQLite: create file if missing
        try (Connection c = ds.getConnection()) {
            ensureSchemaMigrationsTable(c);
            Set<String> applied = loadAppliedVersions(c);
            List<Migration> all = discoverMigrationsForDialect(dialect);   // classpath resource scan
            all.sort(Comparator.comparing(Migration::version));
            for (Migration m : all) {
                if (applied.contains(m.version())) {
                    verifyChecksum(c, m);                                  // fails loudly on drift
                    continue;
                }
                apply(c, m);                                               // own tx, rollback on fail
            }
        }
    }

    private void apply(Connection c, Migration m) throws SQLException {
        c.setAutoCommit(false);
        try (Statement s = c.createStatement()) {
            for (String stmt : splitStatements(m.sql())) s.execute(stmt);
            recordApplied(c, m, true);
            c.commit();
            log.info("Applied {} ({})", m.version(), m.name());
        } catch (SQLException e) {
            c.rollback();
            recordApplied(c, m, false);     // best-effort, separate tx
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }
}
```

**Wiring change.** In `ARControlPanel.databaseControl()`, replace the current existence-check + `initializeMainDatabase*` dispatch with:

```java
new MigrationRunner(dataSource, dialect, Clock.systemUTC()).run();
```

Delete (or mark `@Deprecated` no-op) the following methods in `PerformDataBase` and the commented lines in `ARControlPanel`:

- `migrationScriptsv2_1f()` → replaced by `V002__backfill_bot_job_id.sql`
- `addColumnIfNotExists` / `updateColumns` → replaced by `V003__add_parent_block_id.sql`
- `updateDatabaseSchema` → replaced by `V004__fk_new_home_url.sql` (Access-specific file only)
- `dropPostGresSequences` → **do not migrate as-is**; it deletes data. Split into `V005__reset_home_url_sequences.sql` (sequences only) and a separate admin/maintenance CLI for the data delete.
- `disableForeignKeyConstraints` → drop. If ever needed, it's a maintenance CLI, not app startup.

### F.2 Backup / Restore remediation

**Backup file format v2 header** (first line):

```
# ABR-BACKUP v=2 dialect=postgres schema=V004 generated=2026-04-17T12:34:56Z sha256=<digest-of-body>
```

**Restore algorithm (replaces `runRestoreScripts`):**

```
1. Read and validate header (version, dialect compatibility, checksum).
2. Parse the entire file into an in-memory plan (count of INSERTs per table).
3. BEGIN TRANSACTION (single connection, autoCommit=false).
4. SAVEPOINT sp_wipe
     TRUNCATE … RESTART IDENTITY CASCADE (Postgres) or DELETE FROM … (Access/SQLite)
5. For each entity loader in dependency order:
     SAVEPOINT sp_<entity>
     insert rows, capture generated keys via Statement.RETURN_GENERATED_KEYS
     record old→new in IdMapper
     On failure: ROLLBACK TO sp_<entity>, return ErrorMessage, do NOT commit.
6. Second pass: update instruction/component_instruction FKs using IdMapper.
7. COMMIT.
Any uncaught exception before step 7: conn.rollback().
```

**ID mapping rewrite:** replace `SELECT id BEFORE/AFTER` with:

```java
try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
    ps.setInt(1, …); ps.setString(2, …);
    ps.executeUpdate();
    try (ResultSet gk = ps.getGeneratedKeys()) {
        if (gk.next()) idMapper.put("block", oldId, gk.getInt(1));
    }
}
```

This is portable across all three engines (Postgres, SQLite, Ucanaccess all support `getGeneratedKeys()`), thread-safe, and removes the gap-ID fragility.

**Parser rewrite:** use JSqlParser (`com.github.jsqlparser:jsqlparser`) — its `Insert` AST handles quoted strings, `''` escapes, numeric / boolean / null / binary literals, and multi-line values correctly. Feed the whole file to `CCJSqlParserUtil.parseStatements(new FileReader(…, UTF_8))`. Delete `extractValuesFromInsert`, `toSqlValue`, `setSafeParam`, `escapeSql`.

**Encoding:** switch writer and reader to `StandardCharsets.UTF_8` and record it in the v2 header. Keep a one-shot compatibility path: if header is absent, fall back to `windows-1252` for legacy files.

### F.3 Bootstrap path for a fresh Postgres install

Add to `MigrationRunner.ensureDatabaseExists()`:

```java
if (dialect == Dialect.POSTGRES) {
    String adminUrl = baseUrl.replaceFirst("/ar_web$", "/postgres");
    try (Connection admin = DriverManager.getConnection(adminUrl, user, pwd);
         Statement s = admin.createStatement()) {
        try { s.execute("CREATE DATABASE ar_web"); }
        catch (SQLException e) {
            if (!"42P04".equals(e.getSQLState())) throw e;   // 42P04 = duplicate_database
        }
    }
}
```

### F.4 Fix-list for the known latent bugs (to apply before the refactor)

These are each small, self-contained, and can be shipped before the migration runner lands:

1. `PerformInitializer.java:634` — change SQLite `component_instruction.parent_block_id` FK to `REFERENCES component_block(id)`.
2. `PerformBackup.java:2218` — introduce `componentBlockMap` as a distinct field; stop calling `blockMap.clear()` in `restoreComponentBlock`.
3. `PerformBackup.java:2713` — align the `restoreComponentUpdateInstruction` predicate with L1946: `parent_id IS NULL OR variable_id IS NULL OR parent_block_id IS NULL`.
4. Every `restoreX` / `backupX` catch block — add `try { conn.rollback(); } catch (SQLException ignore) {}` before returning `ErrorMessage`.
5. `PerformBackup.restoreHomeBanking` L922-938 — move the bulk delete **after** the file has been fully parsed and validated; wrap both in the same transaction.
6. Remove the `db_pwd=martini` fallback in `ARPropertyManager`; require explicit config or fail fast.

---

## G. Risk ranking

| Rank | # | Risk | Location |
|------|---|------|----------|
| CRITICAL | 1 | Pre-delete of entire DB before restore file is parsed/validated | `PerformBackup.java:922-938` |
| CRITICAL | 2 | No `rollback()` on catch in any restore/backup step; partial restores commit partial state | `PerformBackup.java` (all `restoreX` / `backupX`) |
| CRITICAL | 3 | No migration system; schema changes require uncommenting ad-hoc Java methods | `ARControlPanel.java:371,401,403,405,445,447` + `PerformDataBase.java:3347,3813,3887,4427,7333` |
| CRITICAL | 4 | No `CREATE DATABASE` for Postgres in app startup; first-run Postgres users are stuck | `ARControlPanel.java:357-379` + `PerformInitializer.java:42` |
| HIGH | 5 | SQLite `component_instruction.parent_block_id` FK points to `block` instead of `component_block` | `PerformInitializer.java:634` |
| HIGH | 6 | Existence check uses a single-table heuristic; partial schemas go undetected | `PerformInitializer.java:672, 690, 709` |
| HIGH | 7 | `blockMap.clear()` in `restoreComponentBlock` reuses the regular block map for component restores | `PerformBackup.java:2218` |
| HIGH | 8 | Inconsistent SELECT predicate in `restoreComponentUpdateInstruction` | `PerformBackup.java:2713` vs `:1946` |
| HIGH | 9 | DDL diverges between Postgres/Access/SQLite (three independent Java blobs) | `PerformInitializer.java:42, 255, 482` |
| MEDIUM | 10 | ID mapping via `SELECT id BEFORE/AFTER` is unsafe under concurrency / gap-ID reuse | `PerformBackup.java` (all `restoreX`) |
| MEDIUM | 11 | Hand-rolled SQL parser cannot handle `''` escapes or multi-line values | `PerformBackup.java:1034` |
| MEDIUM | 12 | No atomic "restore is one transaction" contract; each step commits independently | `PerformBackup.java` (all `restoreX`) |
| MEDIUM | 13 | `windows-1252` hardcoded; non-Latin1 characters corrupt backup files | `PerformBackup.java` |
| MEDIUM | 14 | SQLServer branch in `testConnection` but no matching DDL path — silent fall-through | `PerformInitializer.java:743` |
| MEDIUM | 15 | `dropPostGresSequences` deletes `home_url` and `home_banking` rows (misnamed) | `PerformDataBase.java:4427` |
| LOW | 16 | Hardcoded `db_pwd=martini` fallback | `ARPropertyManager.java` |
| LOW | 17 | Default `DATABASE_TYPE=Access` but default `db_url=jdbc:postgresql://…` — inconsistent defaults | `ARPropertyManager.java` |
| LOW | 18 | `PerformDataBase.java` 7514 LOC monolith — maintenance risk, not an immediate defect | `PerformDataBase.java` |
| LOW | 19 | `PerformDBScripts.java` ships a single method; remaining scaffolding is dead code | `PerformDBScripts.java` |
| LOW | 20 | Orchestration lives in the JavaFX pane, preventing service-level transaction control | `ARConfigurationPane.java:680, 838` |

---

## Verification notes

- Every line reference above was read directly from the source tree at `C:\Martini\abr-web-selenium\src\main\java\com\allinweb\ch` during this investigation.
- `find /migration` and `find *.sql` both return empty — confirmed there is no existing migration asset to preserve.
- `grep` for `migrationScriptsv2_1f | updateDatabaseSchema | updateColumns | dropPostGresSequences | disableForeignKeyConstraints` confirmed all five methods are referenced only from commented-out call sites in `ARControlPanel.java`.
- The restore pre-delete at `PerformBackup.java:922-938` was read verbatim and its commit happens before any backup-file line is consumed.
- The SQLite FK defect was confirmed by reading `PerformInitializer.java:482-670`.
