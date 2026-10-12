# F1/F2 local foundation

F1/F2, Phase 2 shell/role dashboards and the first Phase 3 complaint-to-repair chunk are implemented on `codex/role-based-foundation`. The published release remains v1.0.0; the next version number is undecided. Java 24, JavaFX 24.0.2, JDBC and MySQL remain the architecture. AceOS is a local college project; deployment, pre-deployment preparation, hosting/shared-backend work and production-only features are excluded.

## Accounts and authentication

Administrators create accounts; there is no public signup. Email lookup is trimmed/lowercased; passwords are not trimmed. Passwords accept 15–128 Unicode characters and use individually salted Argon2id (19 MiB, 2 iterations, parallelism 1) through Bouncy Castle 1.86. See [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

New/migrated accounts are PENDING_ACTIVATION. Administrators issue 24-hour temporary credentials, shown once for private delivery. First login permits only password replacement/logout. Replacement consumes the credential and activates the account. Reset invalidates sessions and repeats this flow; forgotten-password identity checks happen outside the app.

Five failed logins within 15 minutes impose a persistent 15-minute cooldown; administrators can unlock accounts. Sessions expire after 30 idle minutes. Logout, password/reset, role changes and deactivation invalidate sessions. No remember-me or shared/default passwords.

Deactivate/reactivate instead of deleting. Outstanding unverified jobs or scheduled inspections block technician deactivation/role changes. F1 permits reassignment only before work starts or inspection completes. Self-deactivation/self role changes and removing the last active administrator are rejected. Historical records survive later role changes.

## Permissions

| Area | Requester | Technician | Administrator |
|---|---|---|---|
| Accounts | Own password/profile identity | Own password/profile identity | Create, activate, reset, unlock, deactivate, reactivate, manage roles |
| Complaints | Submit as self; own list/details | Complaints linked to assigned work | Review all and assign work |
| Assets | Non-retired reporting lookup | Assets linked to assigned jobs/inspections | All; register, lifecycle and evidence-backed hold clearance |
| Work | Own complaint progress | Assigned jobs; start/complete | Assign/reassign unstarted jobs; verify |
| History | Own complaint completion records | Technical history of assigned assets without unrelated requester identity | All history |
| Inspections | No standalone inspection access | Assigned inspections; pass/fail | Schedule, assign/reassign, review |
| Preventive schedules | No records | Read on assigned assets | Create, pause, activate, complete recurrence |
| Users | Own identity only | Own identity only; necessary requester context in assigned work | User directory |
| Totals | Own complaint/job totals | Assigned complaint/job/failed-inspection totals | Institution totals |

Service checks apply to lists, individual lookups and writes. Read scopes are applied in SQL before object reconstruction. Actor-ID fields are removed from forms; compatibility overloads reject actor IDs that differ from the authenticated account. Target technician IDs remain assignment inputs. Completion produces one maintenance record atomically; invalid/stale actions roll back.

All service operations validate sessions and use the same transaction as repository writes/audit. READ COMMITTED prevents an early schema read from retaining a stale snapshot while waiting for a lock. Transaction-bound asset locks coordinate availability changes. A single security coordination row serializes local service operations to protect role/reset/deactivation races. This intentionally simple local mechanism prioritizes correctness; larger-scale concurrency is future work. SQL remains in repositories/security persistence helpers, never JavaFX views. Database calls run on background virtual threads, with UI callbacks on the JavaFX thread; input is retained on failure.

The trusted local client and its service checks are the enforcement boundary. Someone possessing database credentials or modifying the desktop client can bypass Java checks. This limitation describes the local academic application; remote enforcement/backend work is outside its scope.

## Fresh isolated setup

Use an administrative MySQL connection to create **new** `aceos_next_dev` and disposable `aceos_next_test` schemas. Preserve the existing `aceos` database and v1.0.0 tag/assets. Never run `database/demo_reset.sql` on valuable data or on the next-version schemas: it explicitly selects `aceos`.

1. Select the intended empty schema in your SQL client and execute `src/main/resources/database/V000__baseline.sql`. This creates the seven operational tables without switching databases.
2. Create a separate migration/operator MySQL account with DDL and DML access to that isolated schema. Configure `ACEOS_MIGRATION_URL`, `ACEOS_MIGRATION_USER`, `ACEOS_MIGRATION_PASSWORD` in your local terminal. URLs must use explicit localhost/127.0.0.1 ports and name `aceos_next_dev` or `aceos_next_test`.
3. Build, then run the numbered migration explicitly:

```powershell
mvn compile
mvn exec:java "-Dexec.mainClass=com.aceos.database.Migrations"
```

`V001__accounts.sql` adds `user_accounts`, nullable inspection assignment, `action_audit`, `security_gate`, operator-only `operator_setup` and migration metadata. Existing users/operational IDs/foreign keys are preserved; passwords are not invented, and scheduled inspections are not assigned automatically. Normalized email collisions/invalid users stop preflight. History stores checksums and STARTED/COMPLETE status. MySQL DDL is not treated as rollback-safe: an interrupted/mismatched migration stops for deliberate restore/repair. The app refuses absent/incompatible migration state.

`V002__availability.sql` separates manual lifecycle from effective `assets.status`, adds independent auditable safety holds, administrator lifecycle history and precise evidence-recording timestamps. Before first application, preflight reports affected IDs for inconsistent work timestamps, unsupported UNDER_MAINTENANCE, ACTIVE with started-unverified jobs, retired assets with unfinished operations, or any historical failed inspection. Stop for a reviewed mapping; do not invent a manual state, hold or historical clearance. Supported UNDER_MAINTENANCE maps to manual ACTIVE; ACTIVE/OUT_OF_SERVICE/RETIRED manual states and all operational IDs/history remain intact. V001/V002 checksums and completion are required at sign-in. Existing F1 runtime accounts need SELECT/INSERT/UPDATE grants on only the two added tables after migration. Historical passing inspections have no new recording timestamp and cannot serve as later clearance evidence.

4. Create a separate runtime account. Grant SELECT/INSERT/UPDATE only on `users`, `assets`, `complaints`, `work_orders`, `maintenance_records`, `preventive_maintenance_schedules`, `asset_inspections`, `user_accounts`, `action_audit`, `security_gate`, `asset_safety_holds`, `asset_lifecycle_history`; grant SELECT on `schema_migrations`. Give the runtime account no access to `operator_setup` and no DDL/DROP/DELETE permissions.
5. Prepare an interactive operator console for private administrator bootstrap:

```powershell
mvn dependency:build-classpath "-Dmdep.outputFile=.local/classpath.txt"
$foundationClasspath = "target\classes;" + (Get-Content -Raw -LiteralPath '.local/classpath.txt').Trim()
java -cp $foundationClasspath com.aceos.database.Bootstrap create "Your name" "you@institution.edu"
```

The bootstrap command prompts privately for your chosen initial password; it never prints it. If an ADMINISTRATOR already exists, use their numeric ID instead of `create ...`. Initial bootstrap is disabled once an active administrator exists. Explicit operator recovery uses `Bootstrap <ID> recover`; it invalidates sessions and audits recovery. The runtime account cannot use either path because it cannot access `operator_setup`.

6. Set `ACEOS_DB_URL`, `ACEOS_DB_USER`, `ACEOS_DB_PASSWORD` for the runtime account and run `mvn javafx:run`. Sign in, create other accounts and deliver temporary credentials privately. Account management and minimal assignment controls are available through administrator navigation.

Never commit environment files, credentials, dumps or temporary secrets. `.local/` and `target/` are ignored. On this machine, the isolated instance uses port **13316**, with private local environment files under `.local/`; the existing server/database is untouched. Its development administrator is pending activation until private operator bootstrap. The test database contains throwaway test accounts, not development logins.

## Existing data, backup and recovery

Do not migrate the valuable original database in place. Taking/migrating a copy is a separate approved action. Stop the application before schema changes. Before doing so: back up, prove restoration into a fresh isolated schema, compare operational row counts/IDs/links, inspect normalized-email collisions and invalid references, then migrate the copy. Keep the v1 executable with its original schema; do not run it blindly against migrated data. There is no destructive down migration.

The local F1 check restored a development backup into a disposable restore schema and confirmed its user count/account state. It did not copy or migrate the valuable v1 database. Obsolete executable verification classes have been removed from application source; current tests live under `src/test/java`. `Main` launches the authenticated JavaFX application.

## Verification

```powershell
# Runtime test connection: ACEOS_DB_URL must name aceos_next_test.
# Private operator fixture connection: ACEOS_TEST_OPERATOR_USER / ACEOS_TEST_OPERATOR_PASSWORD.
mvn verify
# Optional JavaFX snapshot/interaction checks:
$env:ACEOS_UI_TEST = 'true'
mvn verify
```

Unit checks run without a database. Integration checks require test environment variables and refuse a dev/original database. Fixtures deliberately clear/reseed only the disposable test schema. UI checks need a local graphical JavaFX runtime. Reports are in `target/surefire-reports`; snapshots are in `target/ui-checks`.

The checks cover accounts/sessions, role ownership, workflow transactions, concurrency, migrations and operator isolation. JavaFX checks capture login/password fields at 800×520 and 1280×800 and test input recovery. JavaFX 24 may emit non-failing toolkit/deprecated-Unsafe warnings.

Human checks remaining: private development administrator bootstrap/sign-in, keyboard/screen-reader navigation, credential delivery and reset feedback across live windows.

## Asset availability and safety holds

Manual lifecycle: ACTIVE, OUT_OF_SERVICE or RETIRED. Effective availability precedence: RETIRED → OUT_OF_SERVICE for a manual or safety hold → UNDER_MAINTENANCE while any started-unverified job remains → ACTIVE. Submission, acceptance and assignment alone do not take an asset offline. Completion awaits verification. Overlapping repairs are permitted; verifying one closes only its complaint, and repair on an Out-of-service asset does not lift its hold. Inspection compliance remains a separate result.

Every failed inspection atomically records its own hold. Only administrators may clear individual holds with a nonblank reason and exactly one same-asset evidence ID: a passing reinspection recorded after the failure and with a completion date no earlier than the failed inspection (same-day reinspections are supported), or a corrective work order verified after the hold. Earlier, unrelated, unverified or missing evidence is rejected. Clearance records the actor, reason, evidence and time; another hold/manual stop/repair still restricts availability. Manual lifecycle changes retain reason/actor/history and reject stale expected lifecycle values. Retirement requires no unfinished jobs, scheduled inspections or active preventive schedules, preserves history, and blocks new operations. Reopening/cancellation/repair-return remain outside F2.

Administrator navigation includes **Asset Availability** for ID-based loading, lifecycle changes and hold clearance. Asset Directory labels manual lifecycle separately from availability; existing active-asset totals use the recalculated status. Full connected refresh is still Phase 3; refresh/reload existing directories after actions.

**F2 verification (2026-10-12):** 25 distinct relevant checks passed across focused runs: 21 database, 3 password/session and 1 F2 JavaFX check. Coverage includes overlapping completion/verification orders, failure during repair, independent holds, role denial, same-asset/later evidence and backdated evidence rejection, manual stop/retirement/stale changes, concurrent verification/failure, linked-write rollback, real V1→V2 supported mapping, rerun/checksum/interruption and preserved IDs/history/scoped totals. F2 snapshots/input recovery passed at 800×520 and 1280×800; unchanged F1 UI captures were not repeated. Both ignored pre-F2 development/test backups restored into disposable schemas; user/account/migration checksums matched before migration. V002 applied/reran on only the isolated schemas; development account data remains pending private bootstrap. Human checks remain private sign-in and live-window keyboard/screen-reader/action feedback; snapshots use controlled display fixtures, not dev logins.

## Connected complaint-to-repair screens

Phase 2 queues and bounded recent progress load through one authorized background service operation. Complaint/repair rows now open their exact scoped details. Review, assignment and verification shortcuts open filtered connected lists. The shell includes active navigation, collapsible small-window menus, account/logout and a 120ms fade disabled by Reduce motion or the `aceos.reducedMotion` JVM property.

Four focused Phase 2 checks passed (two database, two JavaFX; zero failures/errors/skips) with packaging. Coverage includes ownership before bounded retrieval, queue/workflow transitions, overdue scheduled work, safety holds surviving verification, anonymous/logout denials, dashboard destinations and forbidden navigation, account/logout, active navigation, 800×520 and 1280×800 layouts, reduced-motion cancellation, background loading, duplicate refresh and error/retry/empty recovery. Unchanged F1/F2 checks were not repeated. Controlled snapshots do not complete private development sign-in or human keyboard/screen-reader/live-window feedback checks.

The first Phase 3 chunk adds searchable authorized non-retired reporting assets and active technicians, complaint/repair lists with row/Enter opening, complaint/work timelines, maintenance text and actions from details. Existing review/assignment/start/completion/verification services continue to enforce roles, ownership and current state. Failure retains form input; failed actions require refreshed details before retry. Timelines use persisted review audit entries and work timestamps, without inventing missing historical events. Scoped projections do not expose unrelated complaints or repair records.

Six focused Phase 3 checks passed (two database, four JavaFX; zero failures/errors/skips) plus packaging. They cover detail ownership/session denial, timeline/workflow transitions, stale actions and technician choices, reporting eligibility, hold preservation, exact-record dashboard/row/Enter navigation, selectors, assignment/verification, no-technician states, duplicate protection and retained input/retry. Both supported sizes include scrollable form/action reachability. Controlled snapshots were reviewed; private bootstrap/sign-in and human keyboard/screen-reader/live-window feedback remain outstanding. Unchanged F1/F2 checks were not repeated.

Next: review asset profiles/history and supporting management-screen refresh as a separate bounded Phase 3 chunk. Edit/archive semantics, cancellation/reopening/repair-return and advanced workflows require later scope decisions. No deployment or new release/tag.
