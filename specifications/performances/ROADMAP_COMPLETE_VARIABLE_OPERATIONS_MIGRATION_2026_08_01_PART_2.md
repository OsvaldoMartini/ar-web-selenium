## 15. Verification strategy

Tests are implemented alongside each activated phase, not as one giant final rewrite.

Focused verification must cover:

- pure TypeScript audit/grouping and ambiguity rules;
- owner/epoch/revision rejection;
- SQLite/PostgreSQL/SQL Server/Access migrations where supported;
- exact raw-value preservation;
- VALUE("") versus VOID;
- multiple ordered GET producers;
- SET VALUE/empty/VOID behavior;
- locale-aware comparisons;
- colon-containing literal operands;
- migration transaction rollback injection;
- realtime stale-response rejection;
- Input-flow non-regression;
- Test Run/Launch non-blocking variable diagnostics;
- Bot Job 32 accepted migration fixture.

Manual acceptance uses a disposable Bot Job/database copy before the production Bot Job is
migrated.

## 16. Observability

Audit/migration logs include IDs and revisions, never runtime values or locator secrets:

```text
operation
request_id
home_banking_id
bot_job_id
workspace_epoch
base/committed revision
affected instruction IDs
created/remapped variable IDs
conflict/result codes
elapsed time
```

Runtime diagnostics may identify instruction and variable IDs but must redact user values by
default.

## 17. Definition of done

The migration is complete only when:

- [ ] Input and variable-command execution are demonstrably independent;
- [ ] GET, SET, ExcelWrite, CK, CSV CHECK, and PDF CHECK use typed V2 contracts;
- [ ] variable names and identities are no longer parsed from `instruction.operation`;
- [ ] SET consumes runtime memory and writes to a Web Element;
- [ ] exact runtime values survive stop, refresh, reconnect, and backend restart;
- [ ] multiple ordered producers behave deterministically;
- [ ] international numeric/currency/date checks use explicit policies;
- [ ] variable health never blocks Test Run or Launch;
- [ ] all migrated Bot Jobs pass post-migration audit;
- [ ] ambiguous Bot Jobs remain safely in LEGACY until reviewed;
- [ ] backup, restore, rollback, and realtime convergence are proven;
- [ ] the legacy operation-variable path is removed after the rollback window.

## 18. Immediate next step

Implement P0 and P1 only:

1. freeze a redacted Bot Job 32 audit fixture;
2. define the V2 TypeScript contracts;
3. implement the pure read-only conflict/candidate analyzer;
4. produce the expected ORDER_NUMBER/SALDO proposal;
5. stop for review before creating schema, persistence, or execution changes.
