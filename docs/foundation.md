# Local setup and recovery

This guide covers the current V001–V005 college build, including accounts, availability, complaint notes/reopening and repair correction. It does not reproduce original seven-table v1.0.0; use the tagged release instructions for that baseline.

## Prerequisites and separation

Use Java 24, Maven, MySQL 8 and a graphical Windows environment for the verified JavaFX build. JavaFX 24.0.2, Connector/J 26.7.0 and Bouncy Castle 1.86 are declared in `pom.xml`. This machine has a separate MySQL instance on loopback port 13316, with `aceos_next_dev` and disposable `aceos_next_test`. Check whether it is running before starting another server.

Use a private migration/operator account for DDL, deliberate bootstrap/recovery and disposable fixtures. Use a different restricted runtime account for the app. Runtime gets no operator-setup, DDL, DROP or DELETE access. Never publish credentials, environment files or dumps. Existing development accounts are activated and human-confirmed; do not reset/bootstrap them just to follow fresh-setup instructions.

## Fresh empty isolated setup

1. Create a new empty `aceos_next_dev` and a separate disposable `aceos_next_test`. Preserve original `aceos`; never use `database/demo_reset.sql` for this build.
2. Select the intended empty schema and execute `src/main/resources/database/V000__baseline.sql`. It has no database switch. `database/schema.sql` is the frozen baseline, not current setup.
3. Set private `ACEOS_MIGRATION_URL`, `ACEOS_MIGRATION_USER`, `ACEOS_MIGRATION_PASSWORD`. URLs must explicitly name local dev/test, for example `jdbc:mysql://127.0.0.1:13316/aceos_next_dev`, with connection options required by your local configuration.
4. Run:

```powershell
mvn compile
mvn exec:java "-Dexec.mainClass=com.aceos.database.Migrations"
```

The runner requires matching checksums and COMPLETE V001–V005 records plus compatible live definitions. Interrupted/mismatched migrations stop for deliberate restoration or reviewed repair. MySQL DDL is not rollback-safe. App startup checks compatibility; it does not migrate automatically.

## Migration effects

| Migration | Purpose | Preservation rules |
|---|---|---|
| V001 accounts | Accounts, inspection assignment, audit, security gate, operator setup | Preserve user IDs; invent no passwords; old scheduled inspections unassigned |
| V002 availability | Manual lifecycle, safety holds, lifecycle history, evidence timestamps | Preserve supported IDs/history; inconsistent data or ambiguous failures stop for review |
| V003 notes | Append-only comments and review reasons | Invent no historical reasons |
| V004 reopening | WITHDRAWN and withdrawal/reopening note kinds | Preserve complaint identity/text/priority/report time |
| V005 corrections | Numbered submissions/reviews and current/verified evidence links | Existing records become submission 1; preserve IDs/text/dates; unknown authors/reviews stay unknown |

A complaint still has at most one work order. V005 replaces one maintenance record per work order with UNIQUE(work_order_id, submission_number). Composite evidence FKs prevent cross-work submission links. Compatibility checks include complete normalized correction rules, required history references and submission indexes.

## Restricted runtime permissions

Grant permissions within only the intended isolated schema, using your actual private account names. Avoid broad schema-wide privileges that defeat the table restrictions.

| Tables | Runtime permissions |
|---|---|
| users, assets, complaints, work_orders, preventive_maintenance_schedules, asset_inspections, user_accounts, action_audit, security_gate, asset_safety_holds, asset_lifecycle_history | SELECT, INSERT, UPDATE |
| maintenance_records, complaint_notes, repair_reviews | SELECT, INSERT only |
| schema_migrations | SELECT only |
| operator_setup | None |

These are current application grants, not a claim that every history table is immutable at privilege level. Submission/note/review rows have no runtime UPDATE/DELETE permission. On upgrade remove the previous maintenance_records UPDATE grant and add SELECT/INSERT on repair_reviews.

## First administrator only

After migration/grants, create or activate the first administrator in an **interactive local operator console**. Its 15–128-character passphrase is prompted privately, not passed on the command line.

```powershell
mvn dependency:build-classpath "-Dmdep.outputFile=.local/classpath.txt"
$aceosBootstrapClasspath = "target\classes;" + (Get-Content -Raw -LiteralPath '.local/classpath.txt').Trim()
java -cp $aceosBootstrapClasspath com.aceos.database.Bootstrap create "Your name" "you@institution.edu"
```

Use the intended identity. If an administrator user already exists from migration, use its numeric ID instead of `create ...`. Duplicate creation is rejected; normal bootstrap is disabled once an active administrator exists. Deliberate emergency recovery uses `Bootstrap <ID> recover`, invalidates sessions and audits the action. It is not a routine demo step.

## Runtime launch and activation

Set private `ACEOS_DB_URL`, `ACEOS_DB_USER`, `ACEOS_DB_PASSWORD` for the restricted account, then run `mvn javafx:run`. The plugin launches `com.aceos.ui.AceOsApplication`; `com.aceos.Main` also delegates to the authenticated app.

Administrators create accounts and issue once-displayed activation/reset credentials for private delivery outside the app. They expire after 24 hours and force password replacement. Passwords are not trimmed. Replacement activates the account and invalidates its previous authentication flow. No public signup.

## Account and workflow safeguards

- Argon2id uses independent random salts. Password replacement accepts 15–128 Unicode characters. Five failed attempts within 15 minutes impose a 15-minute cooldown.
- Sessions expire after 30 idle minutes. Logout, password/reset, role changes and deactivation invalidate sessions.
- Outstanding unverified jobs (including returned work) or scheduled inspections block technician deactivation/role changes. Self-deactivation/role changes and removing the last active administrator are rejected.
- Repair reassignment is only before initial start; inspection reassignment is only while scheduled. Actors come from the session; services check roles/ownership and scoped reads before persistence.
- Return requires a nonblank reason up to 2,000 Unicode characters. Corrections use existing maintenance-text validation, up to 5,000 Java string characters. Expected submission/return-review IDs reject stale actions; ID-only verification supports only an untouched first submission.
- Original assignment/start/first-completion times remain. Correction times and administrator review reasons are separate immutable records. Only latest-submission verification resolves the complaint.

## Availability and safety holds

Precedence is retired → manual/safety hold → any started-unverified repair → active. Completed/returned/resubmitted work stays unverified until accepted; other repairs/holds remain independent. Failed inspections create individual holds atomically. Passing inspections never auto-clear earlier holds.

Administrator clearance needs a reason and later same-asset passing reinspection or verified repair. Reinspection must be recorded after hold creation and have a completion date no earlier than its failure. Repair verification must be after hold creation; the evidence description comes from its accepted submission. Retirement requires no unfinished jobs, scheduled inspections or active preventive schedules.

Preventive completion advances due date to completion date plus frequency; it creates no work order/completion-history record and clears no hold.

## Existing data and recovery

Close all AceOS windows before schema changes. Back up the intended isolated schemas, prove restoration into new scratch schemas, and compare original IDs/rows/links/text/times and earlier migration metadata. Migrate test first; migrate development only after checks. For changed table shapes, compare original fields rather than whole-table checksums.

V005 was applied only to isolated dev/test after both restores matched all 15 pre-migration table checksums. All 14 original non-migration tables/fields and V001–V004 metadata were preserved. No historical authors/reviews invented. Original aceos and previous migration resources remain untouched; backups/restores stay private.

If metadata is STARTED, checksum/definition differs or preflight reports inconsistent evidence, stop for deliberate backup restoration/mapping. Do not rewrite metadata to pretend success or use a destructive down migration.

Failed writes retain UI inputs and disable stale actions until Refresh. Saved writes survive follow-up read failure; Refresh reads without repeating the write. Stay/Discard protects unfinished forms, and saves block navigation/re-entry. The guard stores no password/draft copies.

## Verification

Run `mvn verify` with `ACEOS_DB_URL` naming **aceos_next_test**, its restricted runtime credentials and private `ACEOS_TEST_OPERATOR_USER` / `ACEOS_TEST_OPERATOR_PASSWORD`. Database tests refuse dev/original URLs and deliberately clear/reseed/rebuild disposable fixtures. Without configuration, integration tests skip and no DB correctness is established.

Set `ACEOS_UI_TEST=true` for graphical checks. Reports are in `target/surefire-reports`; controlled screenshots are in `target/ui-checks`. Repair return passed ten distinct DB and six distinct UI checks across implementation/verification, zero failures/errors/skips in final focused runs, plus packaging and supported-size review. Smit confirmed its five-step live walkthrough and earlier human checks. Final v2.0.0 checks passed 89 tests (46 DB, 3 password/session, 40 JavaFX), zero failures/errors/skips; see [verification evidence](verification.md) and [package notes](release-v2.0.0.md).

The enforcement boundary is the trusted local client. A modified client or database-credential holder can bypass Java authorization. Remote enforcement, deployment and production-only work are outside the college scope.
