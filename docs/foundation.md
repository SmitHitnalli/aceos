# F1/F2 local foundation

F1/F2, Phase 2 shell/role dashboards and Phase 3 complaint-to-repair, asset-profile, inspection, preventive-schedule, asset-registration and account-management chunks are implemented on `codex/role-based-foundation`. The published release remains v1.0.0; the next version number is undecided. Java 24, JavaFX 24.0.2, JDBC and MySQL remain the architecture. AceOS is a local college project; deployment, pre-deployment preparation, hosting/shared-backend work and production-only features are excluded.

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

Never commit environment files, credentials, dumps or temporary secrets. `.local/` and `target/` are ignored. On this machine, the isolated instance uses port **13316**, with private local environment files under `.local/`; the existing server/database is untouched. Its development administrator has now been privately activated and sign-in confirmed. The test database contains throwaway test accounts, not development logins.

## Existing data, backup and recovery

Do not migrate the valuable original database in place. Taking/migrating a copy is a separate approved action. Stop the application before schema changes. Before doing so: back up, prove restoration into a fresh isolated schema, compare operational row counts/IDs/links, inspect normalized-email collisions and invalid references, then migrate the copy. Keep the v1 executable with its original schema; do not run it blindly against migrated data. There is no destructive down migration.

The local F1 check restored a development backup into a disposable restore schema and confirmed its user count/account state. It did not copy or migrate the valuable v1 database. Obsolete executable verification classes have been removed from application source; current tests live under `src/test/java`. `Main` launches the authenticated JavaFX application.

## Verification

The current build requires completed, matching V001, V002 and V003 migration records. **V003__complaint_notes.sql** adds one append-only history table; it does not reinterpret old review events or populate missing historical reasons. Grant runtime SELECT/INSERT on `complaint_notes` after applying it with the isolated operator account. No runtime UPDATE/DELETE is needed for notes. Stop AceOS before schema changes, back up and prove restoration, then migrate only the isolated development/test schemas. Older pre-V003 clients reject the newer migration version; use the current build. The frozen v1.0.0 release continues to use its own original schema.

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

Human status: private administrator and requester/technician activation/sign-in are confirmed. Keyboard/screen-reader navigation and explicit reset/invalidation/action feedback across live windows remain outstanding.

## Asset availability and safety holds

Manual lifecycle: ACTIVE, OUT_OF_SERVICE or RETIRED. Effective availability precedence: RETIRED → OUT_OF_SERVICE for a manual or safety hold → UNDER_MAINTENANCE while any started-unverified job remains → ACTIVE. Submission, acceptance and assignment alone do not take an asset offline. Completion awaits verification. Overlapping repairs are permitted; verifying one closes only its complaint, and repair on an Out-of-service asset does not lift its hold. Inspection compliance remains a separate result.

Every failed inspection atomically records its own hold. Only administrators may clear individual holds with a nonblank reason and exactly one same-asset evidence ID: a passing reinspection recorded after the failure and with a completion date no earlier than the failed inspection (same-day reinspections are supported), or a corrective work order verified after the hold. Earlier, unrelated, unverified or missing evidence is rejected. Clearance records the actor, reason, evidence and time; another hold/manual stop/repair still restricts availability. Manual lifecycle changes retain reason/actor/history and reject stale expected lifecycle values. Retirement requires no unfinished jobs, scheduled inspections or active preventive schedules, preserves history, and blocks new operations. Reopening/cancellation/repair-return remain outside F2.

Administrator **Assets** opens searchable assets and exact selected profiles; the dashboard availability shortcut opens the same page. Profiles label manual lifecycle separately from availability; existing active-asset totals use the recalculated status. Lifecycle and hold controls use selected asset/hold/evidence records; writes reload the profile and returning reloads the directory.

**F2 verification (2026-10-12):** 25 distinct relevant checks passed across focused runs: 21 database, 3 password/session and 1 F2 JavaFX check. Coverage includes overlapping completion/verification orders, failure during repair, independent holds, role denial, same-asset/later evidence and backdated evidence rejection, manual stop/retirement/stale changes, concurrent verification/failure, linked-write rollback, real V1→V2 supported mapping, rerun/checksum/interruption and preserved IDs/history/scoped totals. F2 snapshots/input recovery passed at 800×520 and 1280×800; unchanged F1 UI captures were not repeated. Both ignored pre-F2 development/test backups restored into disposable schemas; user/account/migration checksums matched before migration. V002 applied/reran on only the isolated schemas; development account data remains pending private bootstrap. Human checks remain private sign-in and live-window keyboard/screen-reader/action feedback; snapshots use controlled display fixtures, not dev logins.

## Connected complaint-to-repair screens

Phase 2 queues and bounded recent progress load through one authorized background service operation. Complaint/repair rows now open their exact scoped details. Review, assignment and verification shortcuts open filtered connected lists. The shell includes active navigation, collapsible small-window menus, account/logout and a 120ms fade disabled by Reduce motion or the `aceos.reducedMotion` JVM property.

Four focused Phase 2 checks passed (two database, two JavaFX; zero failures/errors/skips) with packaging. Coverage includes ownership before bounded retrieval, queue/workflow transitions, overdue scheduled work, safety holds surviving verification, anonymous/logout denials, dashboard destinations and forbidden navigation, account/logout, active navigation, 800×520 and 1280×800 layouts, reduced-motion cancellation, background loading, duplicate refresh and error/retry/empty recovery. Unchanged F1/F2 checks were not repeated. Controlled snapshots do not complete private development sign-in or human keyboard/screen-reader/live-window feedback checks.

The first Phase 3 chunk adds searchable authorized non-retired reporting assets and active technicians, complaint/repair lists with row/Enter opening, complaint/work timelines, maintenance text and actions from details. Existing review/assignment/start/completion/verification services continue to enforce roles, ownership and current state. Failure retains form input; failed actions require refreshed details before retry. Timelines use persisted review audit entries and work timestamps, without inventing missing historical events. Scoped projections do not expose unrelated complaints or repair records.

Six focused Phase 3 checks passed (two database, four JavaFX; zero failures/errors/skips) plus packaging. They cover detail ownership/session denial, timeline/workflow transitions, stale actions and technician choices, reporting eligibility, hold preservation, exact-record dashboard/row/Enter navigation, selectors, assignment/verification, no-technician states, duplicate protection and retained input/retry. Both supported sizes include scrollable form/action reachability. Controlled snapshots were reviewed; private bootstrap/sign-in and human keyboard/screen-reader/live-window feedback remain outstanding. Unchanged F1/F2 checks were not repeated.

## Connected asset profiles and availability

The asset directory opens profiles by selected row, double-click or Enter. One authorized background service operation loads the asset and existing scoped maintenance projection, adding lifecycle history, holds and eligible clearance evidence only for administrators. Requesters retain non-retired reporting lookup and own completion history; technicians retain assigned-asset lookup and technical history without unrelated requester identity. Retired assets remain excluded from requester profiles while own historical complaint/repair progress keeps its existing linked lookup. No SQL scopes are broadened.

Administrator profiles record manual lifecycle separately from effective availability, show lifecycle reasons/actor IDs/times and active/cleared hold evidence/history, and use existing lifecycle/clearance services. Evidence choices are filtered in SQL to each active hold's later same-asset passing reinspection (including completion-date guard) or verified repair; the write still revalidates. No evidence disables clearance and explains the next step. Duplicate refresh/actions are suppressed, failures retain reason and disable stale actions until refreshed, and feedback is brought into view after saving. Profiles/controls are scrollable and inherit shell reduced motion.

Five focused checks passed (two database acceptance, three JavaFX; zero failures/errors/skips across final focused runs), plus packaging. Covered ownership/session denial, retired/historical lookup rules, administrator history, eligible/excluded evidence, stale lifecycle/hold rejection, independent holds, exact-record navigation, selected evidence, loading/retry/empty/search recovery, duplicates, retained reason and action reachability at 800×520 and 1280×800. Controlled snapshots were visually reviewed. The old ID-based F2 UI check is replaced by these connected-screen checks; unchanged F1/F2 database checks were not repeated. At that verification, private development sign-in and human keyboard/screen-reader/live-window feedback remain outstanding.

Asset-registration usability is delivered through the existing registration and authorized profile/directory services. Account management refresh is delivered through the existing guarded services. Edit/archive semantics, cancellation/reopening/repair-return and advanced workflows require later scope decisions. No deployment or new release/tag.

## Connected inspection workflow

Authorized inspection lists connect selected-row, double-click and Enter opening to exact details; technician dashboard inspection entries do the same. Lists support search and scheduled/unassigned/overdue/passed/failed filters. The administrator assignment shortcut opens the unassigned queue. Administrator scheduling uses searchable non-retired asset choices, then opens the created inspection for assignment. Active technician choices drive assignment/reassignment of scheduled inspections. Assigned technicians record pass/fail, required findings and completion date; completed details preserve inspector/date/findings. Requesters have no standalone inspection screen.

Existing services, repository scopes and transactions remain unchanged. Ownership and current-state rules are revalidated on writes. Every failed inspection creates its own safety hold atomically; passing results do not clear holds. Background loads/actions suppress duplicates; failed writes retain input and disable stale actions until refresh. Returning to the list reloads its records. No cancellation/reopening or new inspection semantics are added.

Five focused checks passed (two database acceptance, three JavaFX; zero failures/errors/skips) plus packaging. Covered requester/unassigned/stale-owner/session denials, assignment/reassignment, stale completion/assignment and inactive-technician rejection, independent failed-inspection holds and passing-result preservation. UI coverage includes exact dashboard/row/Enter links, scheduling/assignment/completion, searchable choices, no-technician states, read failure/empty/search recovery, duplicate protection and retained-input/refresh recovery for all three write paths. Controlled layouts and scrollable action reachability were visually reviewed at 800×520 and 1280×800. At that verification, private development sign-in and human keyboard/screen-reader/live-window credential/reset/action feedback remain outstanding. Unchanged F1/F2 checks were not repeated.

## Connected preventive schedules

Searchable authorized lists open exact details by selected row, double-click or Enter. Filters cover active, paused, due (including overdue), strictly overdue and upcoming schedules; the administrator overdue dashboard shortcut uses the same before-today rule as its count. Administrators create schedules with searchable non-retired asset choices, then pause/activate or record recurring completion from details. Technicians have a read-only assigned-asset schedule route and dashboard shortcut; requesters have no schedule route or records.

Existing services, SQL read scopes, state guards and transactions remain unchanged. Completion sets the next due date to completion date plus frequency and keeps the schedule active; it creates no work order or completion-history record. Past completion dates retain the existing service behavior, while future completion dates and past initial due dates are rejected. Manual/safety holds remain independent. Retired assets cannot receive new or activated schedules. No edit/archive/cancellation or new recurrence semantics are added.

Background loads/actions suppress duplicates. Failures retain asset choices, task/frequency and dates, disable stale actions and require refresh before retry. Successful writes reload details; returning reloads the list. Forms use the approved styling, scrollable layouts and inherited reduced-motion shell.

Five focused checks passed (two database acceptance and three JavaFX; zero failures/errors/skips), plus packaging. Covered SQL ownership for job/inspection-assigned assets and reassignment, requester/technician mutation denial, anonymous/logout denials, dates/recurrence/pause/activation/current state, retired assets and independent manual availability. UI coverage includes dashboard queue destinations, exact row/Enter details, authorized searchable asset choices, role actions, creation and all lifecycle failure/retry paths, duplicate protection, read/empty/search/filter recovery and retained inputs. Controlled layouts and creation/completion action reachability were visually reviewed at 800×520 and 1280×800. Unchanged F1/F2 checks were not repeated. At that verification, private development sign-in and human keyboard/screen-reader/live-window credential/reset/action feedback remain outstanding.

## Connected asset registration

Administrators register assets with the existing four required fields: unique code (50 characters), name (100), category (100) and location (150). Length and duplicate-code validation, role/session checks, transactions and initial Active lifecycle/availability remain in the existing service/repository. No new fields or asset edit/archive/import behavior are added.

The approved styled form is scrollable inside the role shell, with associated labels, length hints and Enter progression through fields; Enter in Location submits. Background submission freezes entries and suppresses duplicates. Failure retains every input and permits correction/retry. Success shows the generated ID/code/name and disables resubmission until explicit Register another asset. Open asset profile loads the exact saved ID through the authorized profile service; profile read failure can be retried independently of registration, and Back loads the authorized directory.

Three focused checks passed (one database acceptance and two JavaFX; zero failures/errors/skips in final focused runs), plus packaging. Coverage includes required/max-length/duplicate-code constraints, trimming and initial state, administrator-only registration, anonymous/logout/deactivated-session denial, exact saved profile, background calls, duplicate prevention, retained input, explicit reset, profile read retry and denied role routes. Controlled standalone and shell snapshots and action reachability were visually reviewed at 800×520 and 1280×800. At that verification, private development bootstrap/sign-in and human keyboard/screen-reader/live-window credential/reset/action feedback remain outstanding. Unchanged workflow checks were not repeated. Account management refresh is now delivered; local college-project scope and frozen v1.0.0 remain preserved.

## Connected account management

Administrator Accounts and User directory open the same authorized account summaries (no hashes or temporary secrets), with ID/name/email/role search and ALL/ACTIVE/PENDING_ACTIVATION/DISABLED/LOCKED filters. Selected-row, double-click and Enter open exact identity, role, state, password-replacement and cooldown details. Creation has its own name/email/role form; role changes use a separate selected-account role choice. Creating selects the returned account ID for deliberate credential issuance. Existing activation/reset, unlock, deactivation, reactivation and guarded role changes remain service operations; no new policy, authorization, SQL or transactions were introduced.

Loads and writes run in the background with duplicate protection. Failed creation retains name/email/role; failed account actions retain choices and disable stale actions until an authorized refresh. Refresh preserves the selected ID and proposed role; a missing account disables actions. Successful writes reload summaries and preserve feedback even if the follow-up read fails. Self-deactivation/role-change controls are disabled, and the service still enforces every administrator/assignment/session safeguard. A confirmation names the exact target before account actions. Temporary credentials remain displayed once for private delivery in the existing dialog policy; closing clears the display, and the directory never stores the secret.

My account shows the signed-in ID/name/email/role and existing password form, with associated labels and Enter progression. Password submissions freeze inputs, preserve whitespace and retain entries after failure; success clears fields and uses the existing session callback. Forced replacement retains its existing flow. Shell detail/creation/password forms scroll without an added outer account-management scroll container.

Nine focused checks passed (five database acceptance and four JavaFX; zero failures/errors/skips in final runs), plus packaging. Covered exact summary state transitions, creation/activation/reset/expiry, cooldown/unlock, guarded technician/administrator changes, role/anonymous/logout/session denial, search/filter/row/Enter navigation, all account action recovery coordination, one-time disposable credential cleanup, separate creation/role inputs, duplicates and retained input, read/empty/missing-record recovery, password failure/success/whitespace and both supported shell sizes. Controlled snapshots and action reachability at 800×520 and 1280×800 were visually reviewed. At that verification, private development bootstrap/sign-in, real private credential delivery, human keyboard/screen-reader and live-window reset/action feedback remain outstanding. The bounded Phase 3 review and recovery fixes are locally complete; the remaining human checks are keyboard/screen-reader use and explicit live-window reset/invalidation/action feedback. New workflows/edit/archive semantics require separate selection/go-ahead.

## Phase 3 recovery closeout

Leaving an unfinished form asks **Stay** or **Discard and leave**; Stay is the default and Escape keeps the form. This covers role navigation, My account, sign out, window close and internal Back buttons. During a save, departure is blocked until the result arrives, so reopening the screen cannot bypass its duplicate-save guard. Reads remain in the background and may be left normally. The guard keeps no copy of form text or passwords.

Repair, inspection, preventive-schedule and asset-availability actions now distinguish a confirmed save from a failed follow-up read. A failed refresh preserves the saved result and exact record context; refresh reloads that record without repeating the write. Actions remain disabled until fresh data is available. Failed writes retain input and their existing refresh-before-retry behavior. Account refresh already distinguishes saved actions from read failures.

Sixteen focused JavaFX checks passed with no failures/errors/skips, including four new closeout checks and twelve affected recovery/navigation checks. Controlled fixtures cover Stay/Discard, save-time shell re-entry/account/sign-out blocking, internal Back retention, saved-result/read-failure recovery and both supported sizes. Controlled snapshots were reviewed. No database/service/policy changes or database fixture runs were needed. Role activation/sign-in and reported functional walkthroughs are now confirmed. Keyboard/screen-reader and explicit live-window reset/invalidation/action-feedback checks still require Smit's participation. Phase 3 remains open for the human checks that have not been explicitly confirmed. The administrator navigation cleanup below removes the duplicate sidebar labels.

## Administrator navigation cleanup

The left sidebar contains exactly seven destinations, in order: **Dashboard, Assets, Complaints, Work orders, Inspections, Preventive schedules, Accounts**. My account remains in the header. Creation and record-detail forms are contained within these main destinations; they do not add sidebar entries.

Assets has Register asset and a protected Back to assets action; inspection, preventive and account lists retain their existing creation buttons. Dashboard review/assignment/verification, unassigned/overdue inspection, overdue schedule, availability and creation shortcuts are retained and highlight their parent tab. The administrator overdue inspection shortcut now selects the existing OVERDUE filter. Requester and technician navigation and service permissions remain unchanged.

Seven focused JavaFX checks passed with no failures/errors/skips. Coverage includes exact sidebar order/count, keyboard-focusable buttons, dashboard queue filters, parent-tab highlights, creation routes, retained registration input on Stay, protected departure and role denials at 800x520 and 1280x800. Controlled snapshots were visually reviewed. No service, repository, database policy or workflow-state changes were made.

During the local human walkthrough, Smit confirmed administrator sign-in, requester/technician activation/sign-in and complaint-to-verified-repair progress. He reported the functional walkthroughs were okay. This does not establish screen-reader behavior or an explicit reset/invalidation check across live windows; those human checks remain open.

## Phase 4 complaint comments and review reasons

Requesters post comments on their own open complaints; technicians post only on complaints linked to their current assigned repair; administrators post on any open complaint. All existing role/session/SQL read scopes still apply. Resolved or rejected complaints are read-only. Notes show author name, role at posting and recorded time to authorized participants only; they add no email/contact projection. Comments and acceptance/rejection reasons are append-only, with a 2,000-Unicode-character limit. Acceptance and rejection both require a reason, saved in the same transaction as the status transition and audit event. Reasonless legacy entry points cannot bypass this requirement. Historical reviews with no reason are explicitly labelled without invented text or times.

Connected complaint/repair details contain associated reason/comment inputs and actions. Background writes freeze all action inputs and suppress duplicates. Failed saves retain drafts and require refreshed progress before retry; successful saves clear only their own input, preserve unrelated drafts and remain explicit through read failure. Closed/already-reviewed records retain unfinished drafts for copying without enabling further writes. Shared departure protection and the seven-tab administrator sidebar remain in force.

V003 was applied only to aceos_next_dev and aceos_next_test after both backups restored with all 14 table checksums matching. All 13 pre-existing development data tables matched after migration; V001/V002 history was preserved and no historical notes were synthesized. Private backups and restore schemas are retained locally; the valuable original aceos database was not copied, reset or migrated.

Final focused verification passed eight database and eight JavaFX checks with zero failures/errors/skips: three new database and three new UI checks plus ten affected regression checks. Coverage includes ownership/reassignment/closed/session denial, Unicode limits, atomic failure rollback, concurrent review, migration rerun/interruption/history, required reasons, author/role/time display, role-shell supported-size layouts and action reachability, duplicate protection, retained drafts, protected departure and saved-result/read-failure recovery. Controlled snapshots at 800x520 and 1280x800 were visually reviewed. Smit confirmed the private live comments/review-reasons walkthrough works. Earlier keyboard/screen-reader and explicit live reset/invalidation/action-feedback checks remain open; this does not claim every edge case was individually exercised live.

This chunk adds no withdrawal/reopening, private administrator notes, comment edit/delete, changed repair/availability states, new release/tag or deployment.
