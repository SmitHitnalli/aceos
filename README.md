# AceOS

AceOS (Asset, Compliance & Equipment Operations System) is a local JavaFX application for institutional assets, complaints, repairs, preventive schedules and inspections. It is an independent OOP and RDBMS college project inspired by campus operations.

## Current college build

The college feature scope is closed. Accounts and sessions, role workspaces, connected management screens, comments/review reasons, technician workload, complaint withdrawal/reopening and repair return/correction are implemented. Smit confirmed the role walkthroughs, remaining keyboard/screen-reader/reset/invalidation/feedback checks and the five-step repair-return walkthrough. Human confirmations are separate from automated verification.

The selected college freeze is **v2.0.0**, identified by its local annotated tag on `codex/role-based-foundation`. Maven now reports 2.0.0. The published [v1.0.0 baseline](https://github.com/SmitHitnalli/aceos/releases/tag/v1.0.0) remains preserved. Local freezing does not publish a GitHub release; publication is separate. See [freeze and package notes](docs/release-v2.0.0.md).

Java 24, JavaFX 24.0.2, JDBC/MySQL 8 and Maven remain the stack. Models express lifecycle rules; services authorize and coordinate transactions; repositories contain SQL; JavaFX runs database operations in the background.

## Implemented behaviour

| Role | Main actions |
|---|---|
| Requester | Report as self; view own complaints/progress; comment on open complaints; withdraw own submitted complaints; reopen own withdrawn complaints |
| Technician | View assigned repairs/inspections and authorized technical history; start/complete repairs; correct and resubmit returned work; complete inspections |
| Administrator | Manage accounts/assets; review complaints with reasons; assign/reassign unstarted work; return or verify completed repairs; manage inspections, schedules, lifecycle and individual safety holds |

The administrator sidebar has exactly seven destinations: Dashboard, Assets, Complaints, Work orders, Inspections, Preventive schedules and Accounts. Details/creation stay inside parent pages; My account is in the header.

Complaint flow: `SUBMITTED → ACCEPTED → WORK_ORDER_CREATED → RESOLVED`. Review may instead reject a complaint. Requester withdrawal is only before review; allowed reopening returns a complaint with no linked work order to `SUBMITTED`, preserving its identity/history.

Repair flow: `ASSIGNED → IN_PROGRESS → COMPLETED → VERIFIED`. An administrator may return completed, unverified work to `RETURNED` with a required reason; the same assigned technician records corrections and resubmits to `COMPLETED`. Each submission/review is preserved. Verification accepts the exact latest submission and resolves the complaint. Verified work cannot return; reassignment remains available only before initial start.

Availability precedence is retired → manual/safety hold → any started-unverified repair → active. Completion/correction alone never releases the asset. Verification closes only its complaint; overlapping repairs and independent holds stay authoritative. Failed inspections create individual holds; a passing result never clears older holds automatically. Administrator clearance needs a reason and eligible later same-asset evidence.

Preventive completion retains the completion-date-plus-frequency rule. It creates no preventive work order/completion-history record and clears no hold. Connected screens support search/filter/exact-record navigation, background loading, duplicate protection, unfinished-form protection, retained failure drafts and saved-result/read-failure recovery.

## Documentation

- [Current college report](output/pdf/AceOS_College_Project_Report.pdf) and [editable report source](docs/college-report.md)
- [Local setup and recovery](docs/foundation.md)
- [Final feature scope and future scope](docs/college-scope.md)
- [Final ER diagrams and complete relationship map](docs/er-diagram.md)
- [All 16 tables and their fields](docs/data-dictionary.md)
- [OOP implementation and source examples](docs/oop-design.md)
- [Atomic transaction examples](docs/transactions.md)
- [Verification evidence and its limits](docs/verification.md)
- [Notion knowledge hub](https://app.notion.com/p/3d786f0db4b980028970e6cd3d95ba68)

The earlier [4 October report](output/pdf/AceOS_Project_Report.pdf) documents frozen v1.0.0, not this migrated application. It remains unchanged. Documentation is complete. Task 6 was closed at Smit’s request; Task 7 final checks/package/local freeze is complete for v2.0.0.

## Setup and run

Follow the [complete setup guide](docs/foundation.md). The current app requires V000 followed by matching COMPLETE V001–V005 migrations, not `database/schema.sql` alone.

Use new isolated `aceos_next_dev` and disposable `aceos_next_test` schemas with separate operator/runtime accounts. This machine uses loopback port 13316. Keep credentials/backups private under ignored `.local/` or your private local environment.

On a new empty selected schema, execute `src/main/resources/database/V000__baseline.sql`. Set private `ACEOS_MIGRATION_URL`, `ACEOS_MIGRATION_USER`, `ACEOS_MIGRATION_PASSWORD`, then run:

```powershell
mvn compile
mvn exec:java "-Dexec.mainClass=com.aceos.database.Migrations"
```

Bootstrap the first administrator privately in an interactive console as described in the guide. Existing activated development accounts need no bootstrap/reset. Set the restricted runtime `ACEOS_DB_URL`, `ACEOS_DB_USER`, `ACEOS_DB_PASSWORD`, then launch:

```powershell
mvn javafx:run
```

## Build and checks

```powershell
mvn verify
```

Database acceptance checks require the disposable **aceos_next_test** runtime connection and private `ACEOS_TEST_OPERATOR_USER` / `ACEOS_TEST_OPERATOR_PASSWORD`. They refuse dev/original URLs and clear/rebuild disposable fixtures. Without a configured test database, database tests skip; that is not proof of database correctness. Enable JavaFX checks with `ACEOS_UI_TEST=true`. Results are under `target/surefire-reports`; controlled screenshots are under `target/ui-checks`.

Repair return passed **10 distinct DB checks and 6 distinct JavaFX checks**, zero failures/errors/skips in final focused runs, plus packaging and supported-size review at 800×520 and 1280×800. Reruns overlap those totals. Earlier chunk counts also overlap and are not one combined suite total. See [verification evidence](docs/verification.md). The final configured v2.0.0 verify run passed **89 tests: 46 DB, 3 password/session and 40 JavaFX**, with **zero failures/errors/skips**, and built the JAR. See [package instructions](distribution/START-HERE.md).

## Data and scope boundaries

Preserve original `aceos`, private backups and frozen v1.0.0 tag/release/assets. `database/demo_reset.sql` explicitly selects original `aceos`; it is a destructive v1 demo script and must not be used for this build. Close all AceOS windows before schema changes, back up and prove restoration, then migrate only the approved isolated schema. No destructive down migration or automatic migration at app startup.

Service checks protect the trusted local app. A modified client or someone holding database credentials can bypass Java authorization; remote enforcement is outside this college scope. No deployment, pre-deployment preparation, hosting/shared backend or production-only features. Other feature ideas are [future scope](docs/college-scope.md), not college completion obligations.
