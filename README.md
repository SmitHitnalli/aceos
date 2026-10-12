# AceOS

AceOS (Asset, Compliance & Equipment Operations System) is a JavaFX desktop application for institutional assets, complaints, maintenance work, preventive schedules and inspections. It is an independent academic project inspired by campus operations, not an official university deployment.

## Current state

**F1 foundation is implemented locally on `codex/role-based-foundation`.** It adds login/accounts, service-enforced roles and ownership, inspection assignment, data-preserving migrations and automated tests. The published [v1.0.0 release](https://github.com/SmitHitnalli/aceos/releases/tag/v1.0.0) remains preserved. The next release number is undecided; the Maven version is still 1.0.0, so branch build output must not be mistaken for the frozen release.

The stack remains Java 24, JavaFX 24.0.2, JDBC/MySQL 8 and Maven. Java models hold lifecycle rules, services enforce identity/permissions and coordinate transactions, and repositories contain SQL. JavaFX database operations run in the background.

## Implemented behaviour

- Sign in/out, 30-minute idle session expiry, Argon2id password hashing and forced temporary-password replacement.
- Administrator-created accounts; activation/reset credentials expire after 24 hours and are displayed once for private delivery.
- Persistent failed-login cooldown, account unlock/deactivation/reactivation and guarded role changes.
- Requesters submit as themselves and see their own complaints/progress; technicians see and act on assigned jobs/inspections; administrators review, assign, verify and manage.
- Permission-scoped lists, detail lookup, technical history projections and dashboard totals. Actor IDs are derived from the session.
- Transactional complaint → assignment → start → completion/maintenance record → administrative verification.
- Preventive schedules retain creation, pause, activation and recurring completion; inspections support assignment and technician pass/fail completion.
- Existing searchable directories and forms remain available through role-gated navigation. Login/password/account additions use the approved Segoe UI, navy/blue/light visual direction.
- Numbered migrations preserve existing users, operational IDs and references. Migrated accounts await activation; existing scheduled inspections remain unassigned.

The full role-dashboard/shell refresh is Phase 2, and connected lists/details/selectors are Phase 3. F2 asset availability is the next reviewed chunk; F1 retains the existing conservative lifecycle behaviour. All development/testing is local; deployment comes last.

## Setup and run

Follow [the F1 setup, permissions and recovery guide](docs/foundation.md). Use **new isolated** `aceos_next_dev` and `aceos_next_test` schemas, separate runtime and migration/operator accounts, and private environment-provided credentials. Do not migrate/reset the existing `aceos` database in place.

For a fresh empty isolated schema, execute `src/main/resources/database/V000__baseline.sql`, then configure `ACEOS_MIGRATION_URL`, `ACEOS_MIGRATION_USER`, `ACEOS_MIGRATION_PASSWORD` and run:

```powershell
mvn compile
mvn exec:java "-Dexec.mainClass=com.aceos.database.Migrations"
```

Bootstrap your initial administrator privately using the interactive operator command described in the guide. Configure `ACEOS_DB_URL`, `ACEOS_DB_USER`, `ACEOS_DB_PASSWORD` for the restricted runtime account, then launch:

```powershell
mvn javafx:run
```

Credentials, local databases and backups belong outside source control; `.local/` is ignored. On the current development machine, the isolated instance uses loopback port 13316. Its administrator still needs private operator bootstrap before development sign-in.

## Build and verify

```powershell
mvn verify
```

Password/session unit tests run without MySQL. Database acceptance checks need `ACEOS_DB_URL` naming **aceos_next_test**, the restricted runtime credentials and `ACEOS_TEST_OPERATOR_USER` / `ACEOS_TEST_OPERATOR_PASSWORD` for disposable fixtures. They refuse development/original database URLs. Set `ACEOS_UI_TEST=true` to include JavaFX login/password snapshots and login failure/input-recovery checks. See `target/surefire-reports` and `target/ui-checks` for results.

Obsolete standalone verification classes have been removed from application source; current automated tests are under `src/test/java`. The frozen v1.0.0 tag retains its original executable checks and setup instructions.

## Baseline and limits

The original `database/schema.sql` and destructive `database/demo_reset.sql` are preserved for v1.0.0. The reset explicitly selects `aceos` and clears its operational tables; do not run it against valuable data or as next-version setup. Use the tagged v1.0.0 README when reproducing that release.

F1 checks enforce permissions in the trusted local client. Database credentials or a modified client can bypass Java service checks; this milestone is not a secure remote multi-user deployment. Human checks still include private development bootstrap, keyboard/screen-reader operation and reset feedback across live windows. No deployment, new release/tag, attachments, notifications or advanced workflows are included.
