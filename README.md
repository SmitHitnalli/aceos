# AceOS

AceOS (Asset, Compliance & Equipment Operations System) is a JavaFX desktop application for institutional assets, complaints, maintenance work, preventive schedules and inspections. It is an independent academic project inspired by campus operations, not an official university deployment.

## Current state

**F1/F2, Phase 2 role workspaces and Phase 3 complaint-to-repair and asset-profile chunks are implemented and locally verified on `codex/role-based-foundation`.** They add login/accounts, service-enforced roles and ownership, inspection assignment, data-preserving migrations, a refreshed shell, actionable dashboards, connected complaint/repair screens and authorized asset profiles with linked availability controls. The published [v1.0.0 release](https://github.com/SmitHitnalli/aceos/releases/tag/v1.0.0) remains preserved. The next release number is undecided; the Maven version is still 1.0.0, so branch build output must not be mistaken for the frozen release.

The stack remains Java 24, JavaFX 24.0.2, JDBC/MySQL 8 and Maven. Java models hold lifecycle rules, services enforce identity/permissions and coordinate transactions, and repositories contain SQL. JavaFX database operations run in the background.

## Implemented behaviour

- Sign in/out, 30-minute idle session expiry, Argon2id password hashing and forced temporary-password replacement.
- Administrator-created accounts; activation/reset credentials expire after 24 hours and are displayed once for private delivery.
- Persistent failed-login cooldown, account unlock/deactivation/reactivation and guarded role changes.
- Requesters submit as themselves and see their own complaints/progress; technicians see and act on assigned jobs/inspections; administrators review, assign, verify and manage.
- Permission-scoped lists, detail lookup, technical history projections and dashboard totals. Actor IDs are derived from the session.
- Transactional complaint → assignment → start → completion/maintenance record → administrative verification.
- Manual asset lifecycle is separate from availability: Retired → manual/safety hold → started unverified repair → Active. Verification closes only its complaint; every overlapping repair and independent hold remains in force.
- Failed inspections atomically create auditable safety holds. Only administrators can clear a hold, with a reason and a later passing reinspection or verified corrective repair on the same asset. A passing result alone does not clear a hold.
- Administrator availability controls manage manual Out-of-service/Active and guarded retirement. Requesters can report non-retired assets even during repair or a hold. Retirement requires all jobs/inspections finished and preventive schedules paused.
- Preventive schedules retain creation, pause, activation and recurring completion; inspections support assignment and technician pass/fail completion.
- Refreshed Segoe UI/navy-blue/light shell with role navigation, active-screen feedback, account/sign-out controls, a collapsible menu, a short screen transition and a Reduce motion switch (`-Daceos.reducedMotion=true` defaults it on).
- Requester dashboard shows own open/resolved totals and six recent complaints with linked repair progress. Technician dashboard shows assigned/in-progress/completed repair queues, scheduled/overdue inspections and bounded assigned rows. Administrator dashboard shows review, assignment, verification, unassigned/overdue inspection, overdue schedule and open safety-hold queues, plus management shortcuts.
- Dashboard complaint/repair rows open their exact authorized details. Queue shortcuts open searchable lists, with administrator review/assignment/verification filters. Counts and recent rows load together in a background authorized operation; returning to Dashboard reloads its data.
- Reporting uses a searchable non-retired asset selector (code, name, location and availability). Submission retains input on failure and links directly to the submitted complaint.
- Complaint/repair lists support search/status filters, selected-row opening, double-click and Enter. Details show the complaint, current asset availability, technician, work performed and a timeline from recorded review events and repair timestamps. Missing historical review times are not invented.
- Administrators accept/reject from complaint details, choose an active technician to assign or reassign unstarted work, and verify completed repairs. Technicians start/complete only assigned work; requesters follow only their own complaints/progress. Reads/writes use existing authorized services; active technician choices and detail projections are scoped background operations.
- Asset directory search/availability filters and selected-row, double-click or Enter opening connect exact authorized profiles. Profiles distinguish manual lifecycle from effective availability. Requesters see only their own completion records on non-retired reporting assets; technicians see technical maintenance history only for assigned assets, without unrelated requester identity. Existing retired reporting and historical progress lookup rules remain intact.
- Administrator profiles show lifecycle reasons/actors/times and active/cleared safety holds. Lifecycle changes and hold clearance use the existing guarded services; hold and eligible later same-asset inspection/verified-repair evidence are selected records, not typed IDs. No eligible evidence is an explicit state. Saving reloads the profile; returning reloads the directory. Failure retains the reason and disables stale actions until refresh.
- The connected screens use the approved styling and scrollable small-window layouts. Refresh and submissions suppress duplicates; failed actions retain input and require refreshed progress before retrying. Registration, inspection, preventive and account management refresh remain separate Phase 3 work.
- Numbered migrations preserve existing users, operational IDs and references. Migrated accounts await activation; existing scheduled inspections remain unassigned.

The Phase 3 complaint-to-repair and asset-profile/availability chunks are delivered. A bounded inspection-workflow refresh is the proposed next review, subject to a separate go-ahead. AceOS remains a local college project: deployment, pre-deployment preparation, hosting/shared-backend work and production-only features are excluded.

## Setup and run

Follow [the F1/F2 setup, permissions and recovery guide](docs/foundation.md). Use **new isolated** `aceos_next_dev` and `aceos_next_test` schemas, separate runtime and migration/operator accounts, and private environment-provided credentials. Do not migrate/reset the existing `aceos` database in place.

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

Password/session unit tests run without MySQL. Database acceptance checks need `ACEOS_DB_URL` naming **aceos_next_test**, the restricted runtime credentials and `ACEOS_TEST_OPERATOR_USER` / `ACEOS_TEST_OPERATOR_PASSWORD` for disposable fixtures. They refuse development/original database URLs. Set `ACEOS_UI_TEST=true` to include JavaFX layout/input-recovery checks, including the F2 availability controls and asset statuses. See `target/surefire-reports` and `target/ui-checks` for results.

F2 verification passed 25 relevant checks across focused runs: 21 database acceptance checks, 3 password/session unit checks and 1 changed-screen JavaFX check at 800×520 and 1280×800. Unchanged F1 UI checks were not repeated. Both isolated pre-F2 backups were restored and compared before migration. V002 refuses inconsistent legacy rows and historical failed-inspection ambiguity until a mapping is reviewed; no automatic reinterpretation or down migration.

Phase 2 verification passed four focused checks (two database acceptance and two JavaFX), with no failures/errors/skips, plus packaging. They cover scoped queue transitions and ownership, bounded recent rows, anonymous/logged-out denial, overdue schedules/inspections, safety holds surviving verification, all dashboard destinations, forbidden role navigation, active-screen/account/logout controls, responsive layouts at 800×520 and 1280×800, reduced motion, background loading, duplicate refresh, failure/retry and empty states. UI snapshots use controlled display fixtures; private sign-in and human keyboard/screen-reader/live-window action feedback remain outstanding.

The first Phase 3 chunk passed six focused checks (two database acceptance and four JavaFX), with no failures/errors/skips, plus packaging. Coverage includes detail/linked-record ownership, session denial, actual review/assignment/reassignment/start/completion/verification timelines, maintenance text, independent holds surviving verification, non-retired reporting choices, active technician choices and stale-choice rejection. UI checks cover exact-record dashboard navigation, row/Enter opening, search/status recovery, assignment/verification, missing technicians, duplicate refresh/submission/action protection, retained input after failures, and scrollable layouts at 800×520 and 1280×800. Controlled snapshots were visually reviewed. Unchanged F1/F2 checks were not repeated; private sign-in and human keyboard/screen-reader/live-window feedback remain outstanding.

Obsolete standalone verification classes have been removed from application source; current automated tests are under `src/test/java`. The frozen v1.0.0 tag retains its original executable checks and setup instructions.

The asset-profile chunk passed five focused checks (two database acceptance and three JavaFX; zero failures/errors/skips across final focused runs) plus packaging. Coverage includes history ownership, anonymous/logout denial, preserved retired/historical lookup rules, administrator-only lifecycle/hold history, exclusion of old/foreign/backdated/unverified evidence, stale lifecycle/hold rejection and independent holds surviving clearance. UI coverage includes exact-asset Enter/selected-row navigation, role controls, selected evidence, duplicate actions/refresh, retained reason after failure, refresh/retry/empty/search states and scrollable action reachability at 800×520 and 1280×800. Controlled snapshots were visually reviewed. Unchanged F1/F2 checks were not repeated; private sign-in and human keyboard/screen-reader/live-window feedback remain outstanding.

## Baseline and limits

The original `database/schema.sql` and destructive `database/demo_reset.sql` are preserved for v1.0.0. The reset explicitly selects `aceos` and clears its operational tables; do not run it against valuable data or as next-version setup. Use the tagged v1.0.0 README when reproducing that release.

Foundation checks enforce permissions in the trusted local client. Database credentials or a modified client can bypass Java service checks; this milestone is not a secure remote multi-user deployment. Human checks still include private development bootstrap, keyboard/screen-reader operation and reset feedback across live windows. No deployment, new release/tag, attachments, notifications or advanced workflows are included.
