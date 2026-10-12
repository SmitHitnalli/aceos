# F1 local foundation

F1 is implemented on `codex/role-based-foundation`. The published release remains v1.0.0; the next version number is undecided. Java 24, JavaFX 24.0.2, JDBC and MySQL remain the architecture. Full dashboard/shell refresh is Phase 2; connected screens are Phase 3; deployment is last.

## Accounts and authentication

Administrators create accounts. There is no public signup or privileged role self-selection. Email lookup trims/lowercases with `Locale.ROOT`; passwords are never trimmed. Passwords are 15–128 Unicode characters, with paste/passphrases supported. Argon2id uses unique 16-byte salts, 19 MiB memory, 2 iterations and parallelism 1 through Bouncy Castle 1.86. Encoded hashes retain algorithm/parameters; there are no shared/default passwords. See [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

New/migrated accounts are `PENDING_ACTIVATION`. An administrator issues a random 24-hour temporary credential, displayed once for private manual delivery. It permits only password change/logout. Password replacement consumes the credential and sets `ACTIVE`. Reset invalidates sessions immediately and requires the same change flow. Forgotten passwords are handled by an administrator after identity verification outside the app; no email service is implied.

Five failed logins within 15 minutes impose a persistent 15-minute cooldown. Login failures do not disclose account existence. Administrators can unlock accounts. Logout revokes the in-memory session; 30 minutes without service activity expires it. Password changes, resets, account disabling and role changes invalidate other sessions through an authentication version. There is no remember-me.

Deactivate instead of deleting; history is retained. Outstanding unverified work and scheduled inspections block deactivation/role changes until resolved/reassigned. Only ASSIGNED jobs and SCHEDULED inspections can be reassigned in F1. Self-deactivation/self role change and removing the last active administrator are rejected. Historical work and inspection results remain readable after a role change.

## Permissions

| Area | Requester | Technician | Administrator |
|---|---|---|---|
| Accounts | Own password/profile identity | Own password/profile identity | Create, activate, reset, unlock, deactivate, reactivate, manage roles |
| Complaints | Submit as self; own list/details | Complaints linked to assigned work | Review all and assign work |
| Assets | Non-retired reporting lookup | Assets linked to assigned jobs/inspections | All; register |
| Work | Own complaint progress | Assigned jobs; start/complete | Assign/reassign unstarted jobs; verify |
| History | Own complaint completion records | Technical history of assigned assets without unrelated requester identity | All history |
| Inspections | No standalone inspection access | Assigned inspections; pass/fail | Schedule, assign/reassign, review |
| Preventive schedules | No records | Read on assigned assets | Create, pause, activate, complete recurrence |
| Users | Own identity only | Own identity only; necessary requester context in assigned work | User directory |
| Totals | Own complaint/job totals | Assigned complaint/job/failed-inspection totals | Institution totals |

Service checks apply to lists, individual lookups and writes. Read scopes are applied in SQL before object reconstruction. Actor-ID fields are removed from forms; compatibility overloads reject actor IDs that differ from the authenticated account. Target technician IDs remain assignment inputs. Completion produces one maintenance record atomically; invalid/stale actions roll back.

All service operations validate sessions and use the same transaction as repository writes/audit. A single security coordination row serializes local service operations to protect role/reset/deactivation races. This intentionally simple local mechanism prioritizes correctness; larger-scale concurrency is future work. SQL remains in repositories/security persistence helpers, never JavaFX views. Database calls run on background virtual threads, with UI callbacks on the JavaFX thread; input is retained on failure.

The trusted local client and its service checks are the enforcement boundary for this milestone. Someone possessing database credentials or modifying the desktop client can bypass Java checks. This is not a secure remote multi-user deployment. Establish a trusted backend/enforcement boundary before deployment.

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

4. Create a separate runtime account. Grant SELECT/INSERT/UPDATE only on `users`, `assets`, `complaints`, `work_orders`, `maintenance_records`, `preventive_maintenance_schedules`, `asset_inspections`, `user_accounts`, `action_audit`, `security_gate`; grant SELECT on `schema_migrations`. Give the runtime account no access to `operator_setup` and no DDL/DROP/DELETE permissions.
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

Do not migrate the valuable original database in place. Taking/migrating a copy is a separate approved action. Before doing so: back up, prove restoration into a fresh isolated schema, compare operational row counts/IDs/links, inspect normalized-email collisions and invalid references, then migrate the copy. Keep the v1 executable with its original schema; do not run it blindly against migrated data. There is no destructive down migration.

The local F1 check restored a development backup into a disposable restore schema and confirmed its user count/account state. It did not copy or migrate the valuable v1 database. Legacy executable `*Test` entry points are retired to prevent accidental unauthenticated mutations; `Main` launches the authenticated JavaFX application.

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

Coverage includes forced activation/reset/expiry, hashed credentials, duplicate email, cooldown, role/ownership/anonymous denials, scoped lists/details/totals, inspection assignment, history projection, role-change history, stale sessions, operator isolation/recovery, reassignment/deactivation safeguards, concurrent last-admin removal, concurrent duplicate completion, migration rerun/checksum/partial-failure detection, backup restore and login input recovery. JavaFX snapshots verify login/password fields at 800×520 and 1280×800. Toolkit/module/deprecated-Unsafe warnings may appear with JavaFX 24; they are distinct from compiler/test failures.

Remaining human checks: private administrator bootstrap and sign-in on the development schema; keyboard-only login/account/assignment navigation; account credential delivery/reset while another desktop window is open; screen-reader behaviour and visual review on the actual display. Automated service checks cover session invalidation, but these human checks have not been claimed as completed.

## Approved visual direction and next chunk

Segoe UI; navy #14263D navigation, blue #1D4ED8 primary actions, #F4F7FB canvas, white surfaces, #172B45 body text, #52647A secondary text and #D5DFEB borders. Spacing follows a 4/8px grid with 8px controls/12px panels. Login/password/account additions use this direction. Existing operational screens remain recognizable until the reviewed Phase 2/3 refresh. F1 introduces no decorative motion; reduced-motion behaviour will be part of that refresh.

F2 proposal: separate manual asset lifecycle from availability derived across **all** started unverified repairs and safety holds. Verify one repair without restoring an asset prematurely; introduce explicit failed-inspection hold/release rules and a reviewed legacy-status mapping. F1 intentionally retains the conservative v1 transitions. F2 implementation requires the next chunk's go-ahead.
