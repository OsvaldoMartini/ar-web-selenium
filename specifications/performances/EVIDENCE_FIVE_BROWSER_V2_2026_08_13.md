# Five-browser isolated V2 acceptance evidence - 2026-08-13

Scope: local mock acceptance only. The runner used the production `PlaywrightWorkerPool` and
`PlaywrightBrowserFactory`, opened no banking endpoint, performed no banking action, and released
all five sessions after the bounded display interval.

Command:

```text
npm run demo:five-browsers
```

Result: exit code 0. Every session reached `READY`, revision 3, with a distinct runtime identity.

| Browser | Mock Bot Job | Run ID | Browser instance | Context instance | Page instance |
|---:|---:|---|---|---|---|
| 1 | 10001 | `aab10c4c-...` | `341c04f2-...` | `574296f0-...` | `597eb735-...` |
| 2 | 10002 | `0e4a93fa-...` | `d92410eb-...` | `19250869-...` | `9cf6e587-...` |
| 3 | 10003 | `9ada0c18-...` | `c54d7526-...` | `26449cb7-...` | `1ac911bc-...` |
| 4 | 10004 | `281d0a65-...` | `4ef92a5f-...` | `5a1a7a50-...` | `5cd892ee-...` |
| 5 | 10005 | `0e71682a-...` | `390f45e8-...` | `38940897-...` | `37b19e0b-...` |

This proves five isolated V2 browser lifecycles can coexist. It does not prove five complete Bot Job
instruction graphs, datasets, Runtime Variables, or ExcelWriter flows; that React control-plane phase
remains open.
