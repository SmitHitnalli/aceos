# AceOS College Project Report

Asset Compliance and Equipment Operations System

Java OOP and RDBMS college application

13 October 2026

This report describes the current locally verified application through V005 repair corrections. It replaces the earlier baseline report as the current assessment reference while preserving that original file. The college feature scope is closed and v2.0.0 is selected for the local freeze. The final configured run passed 89 tests with no failures/errors/skips. Task 6 was closed at Smit’s request; GitHub publication is separate.

## 1 Project context and objectives

AceOS records institutional equipment, receives requester complaints, assigns corrective repairs, preserves submitted work and administrator reviews, and tracks inspections and preventive schedules. A campus setting motivates the examples; this is an independent academic prototype, not an official institutional deployment or a proven operational installation.

The project demonstrates Java object modelling, state transitions, layered responsibilities, relational keys and constraints, JDBC persistence, transactions, role-scoped data access and recoverable desktop forms. Its main result is a complete complaint-to-verified-repair flow, including returning unsatisfactory work for correction without losing earlier evidence.

## 2 Final scope and users

All delivered functionality remains included: accounts/login/session safeguards; asset registration/profiles; complaints/comments/review reasons; withdrawal/reopening; assigned repairs and immutable correction history; workload/dashboards; inspections/independent safety holds; preventive schedules; and connected form recovery. The feature boundary is closed, not the final release process.

| Role | Main responsibility | Important boundary |
|---|---|---|
| Requester | Submit as self and follow own complaints/history | No assignment verification or inspection management |
| Technician | Start/complete assigned repairs and inspections; correct returned work | No administrator return/verification; no reassignment after start |
| Administrator | Manage identities/assets; review/assign/return/verify; manage schedules/holds | Cannot resubmit technician correction or bypass independent evidence rules |

Accounts are administrator-created. Roles use an enum and session-derived identity. Lists and exact detail reads are scoped before reconstruction; a hidden button is not the security boundary.

## 3 Architecture and implementation

JavaFX views call AceOsService; the service validates session/role/ownership and coordinates a transaction; repositories execute prepared SQL; MySQL enforces references and constraints. Domain objects express lifecycle changes. Security helpers provide authentication/session validation and the SQL scope. FxTasks performs database work on background virtual threads and returns callbacks to the JavaFX thread.

| Layer | Main examples | Responsibility |
|---|---|---|
| Desktop UI | WorkspaceView RepairFlowView AssetProfileView | Forms navigation feedback and background coordination |
| Service | AceOsService | Authorized application operations transaction and error boundary |
| Model | Complaint WorkOrder MaintenanceRecord and record projections | Domain transitions and typed data |
| Persistence | RepairReviewRepository AvailabilityRepository and JDBC repositories | SQL mapping conditional writes and evidence reads |
| Security and transaction | Authentication Session Scope Transactions | Identity scopes resource ownership and commit rollback |

The administrator sidebar has seven tabs: Dashboard, Assets, Complaints, Work orders, Inspections, Preventive schedules and Accounts. Creation/details stay within the parent route; My account is in the header. Navigation protects unsaved forms and prevents leaving/re-entering during a save.

## 4 Lifecycle and correction workflow

The normal complaint path is SUBMITTED to ACCEPTED to WORK_ORDER_CREATED to RESOLVED. Acceptance/rejection need an atomic recorded reason. A requester may withdraw an own SUBMITTED complaint to WITHDRAWN; requester/admin may reopen allowed WITHDRAWN records, and an administrator may reopen REJECTED records. Reopening requires a non-retired asset and no linked repair and returns to review without changing the original identity, priority, description or report time.

Repair progress is ASSIGNED to IN_PROGRESS to COMPLETED to VERIFIED. Administrator verification resolves its complaint. A completed unverified repair may instead become RETURNED with a mandatory administrator reason. The same technician submits fresh correction evidence and returns progress to COMPLETED; the administrator reviews that exact latest submission. Cycles may repeat. Verified work cannot return, and reassignment is allowed only before initial start.

Each work order retains its original assignment/start/first-completion time. Every correction is a new numbered maintenance record, and every return/verification reviews an exact submission. Earlier text is never overwritten. Current and accepted submission references include the work-order identity, preventing cross-work evidence. Historical missing authors/reviews remain explicitly unknown rather than fabricated.

Availability follows retired, then manual/safety hold, then any started-unverified repair, then active. Returning, resubmitting or completing does not clear availability restrictions. A verified repair closes only its own complaint; overlapping repairs and independent holds remain authoritative. Passing an inspection never automatically clears an earlier failed-inspection hold. Preventive completion remains date plus frequency and creates no work/history record.

## 5 Object oriented design

Encapsulation is visible in private WorkOrder/Complaint state and lifecycle methods. Services add authorization and persistence invariants. Composition links WorkOrder to Complaint/User, Complaint to Asset/reporter, and MaintenanceRecord to its work order. These are associations, not a claim that Java object deletion owns database history.

Abstraction separates UI/service/repository concerns. JDBC exposes Connection, PreparedStatement and ResultSet interfaces. Transactions implements AutoCloseable and supplies a borrowed Connection proxy so only the outer owner commits/closes. Generic Callable and ServiceOperation contracts separate an operation from background execution/authorization coordination.

Inheritance is used by JavaFX view classes: WorkspaceView extends BorderPane, RepairFlowView extends ScrollPane and DashboardView extends VBox. The domain does not invent requester/technician/administrator subclasses; UserRole represents those roles. Test preview services override AceOsService methods for controlled UI fixtures. JDBC driver interface implementations and that test substitution demonstrate polymorphism.

Overloaded constructors support new/reconstructed models, enums protect typed lifecycle/role values, and records describe bounded read projections. Records referencing mutable entities are not automatically deeply immutable. Asset/User legacy setters exist; public application mutations still belong to guarded services. See the separate OOP mapping for exact class references.

## 6 Relational design and normalization

The final schema has 16 tables: the original seven domain tables, accounts/audit/coordination/migration infrastructure, lifecycle/hold history, complaint notes and repair reviews. Primary keys identify records; foreign keys preserve links; unique constraints enforce code/login identities, one repair per complaint, submission numbering and one review per submission. Checks restrict persisted states and evidence shape; service checks enforce ownership, eligibility and temporal evidence.

Entities are decomposed by responsibility to avoid repeating asset/requester/work data in each record. Author snapshots in repair submissions/reviews deliberately preserve historical names/roles; assets.status is a derived projection persisted by transactionally recalculated availability. These are deliberate historical/projection choices, so the project does not claim every stored value is independent or that a formal whole-schema normal-form proof was performed.

Complaint notes store author ID and role at posting, with displayed names joined from current users; repair submissions/reviews additionally snapshot names. Audit target_id is event-specific and has no universal FK. security_gate, operator_setup and schema_migrations are infrastructure tables, not user-facing business entities. The dictionary documents every column, default, nullability, key and rule; the ER appendix includes every FK.

## 7 Transactions and consistency

AceOsService.execute owns authorization, writes and audit within Transactions. READ COMMITTED avoids a stale snapshot after waiting for the local security gate; asset locks coordinate availability. The borrowed JDBC connection ignores nested repository commit/close attempts so an operation cannot partly commit. An expected-state write must affect one row; any failure prevents commit and rolls back.

Return inserts an immutable review/reason, changes COMPLETED to RETURNED for the expected submission, recalculates availability and audits together. Resubmission inserts the next record, conditionally advances state/current pointer and audits without resetting old timestamps. Verification inserts the review, records the accepted submission/verification time, resolves the complaint and recalculates availability together. Audit failure therefore cannot leave half a lifecycle transition.

Failed-inspection completion and creation of its independent hold are atomic. Hold clearance separately checks a later passing reinspection or verified repair on the same asset. The accepted correction description is used for repair evidence, never returned text. Schema migrations are different: MySQL DDL is not treated as rollback-safe and interrupted migrations require reviewed restoration/repair.

## 8 Validation security and recoverable UI

Authentication uses salted Argon2id. Password replacement accepts 15–128 Unicode characters; temporary credentials expire after 24 hours. Five failed logins within 15 minutes impose a 15-minute cooldown; sessions expire after 30 idle minutes. Authentication versions invalidate sessions after password/reset/role/deactivation changes. Outstanding work blocks unsafe technician changes; self/last-administrator safeguards remain.

Comments/review/return reasons have nonblank 2000-Unicode-character bounds. Other maintenance/inspection text uses existing 5000-Java-string-character validation. Prepared statements bind inputs. Domain/service validation and SQL constraints are complementary. New submission/note/review tables have runtime SELECT/INSERT only, with no edit/delete path.

Background forms freeze write inputs and suppress duplicates. Failed writes retain drafts and require fresh progress before retry. Successful writes remain explicit through a failed follow-up read; refresh does not repeat the save. Unrelated/closed drafts remain copyable. Stay is the default departure choice; saving blocks navigation/re-entry and the guard stores no draft/password copies.

The trusted local client is the authorization boundary. Someone with database credentials or a modified client can bypass Java checks. Remote multi-user enforcement and production deployment are outside this college project.

## 9 Verification and results

Final v2.0.0 verification passed 89 configured checks: 46 database, 3 password/session and 40 JavaFX, with zero failures/errors/skips, and built the JAR in the same run. Repair return previously passed ten distinct database and six distinct JavaFX focused checks across implementation and verification, with zero failures/errors/skips in final focused runs. Packaging passed afterward with tests skipped in that packaging step. Controlled layouts/actions were reviewed at 800 by 520 and 1280 by 800. Repeated runs overlap these totals; earlier chunk counts also overlap and are not a single final full-suite count.

Database coverage includes roles/ownership/reset-invalidated sessions, correction cycles/history, stale exact-submission/return-review guards, competing actions/rollback, cross-work references, immutable runtime permissions, privacy/counts, independent holds/overlapping repairs and accepted evidence. Migration checks cover real V004 upgrade, fresh V000 through V005 setup, preflight inconsistency, rerun, STARTED/checksum mismatch, weakened rules and missing references.

UI coverage includes role controls, history labels, dashboard correction filter, associated labels/focus/visible actions, drafts, duplication/save/departure protection, verification feedback/read recovery and copyable closed drafts. Smit confirmed all five live repair-return walkthrough steps worked. He also confirmed earlier role/workload/withdrawal/reopening and remaining keyboard/screen-reader/reset/invalidation/feedback checks. These human reports are distinct from automated fixtures; individual earlier inspection/preventive edge cases were not separately recorded live.

Before V005, both isolated backups restored with all 15 table checksums matching. Migration preserved all 14 original non-migration tables/fields and V001–V004 metadata, inventing no historical actor/review rows. Original aceos and v1.0.0 stayed intact. Documentation updates do not justify rerunning unchanged workflows; final configured checks and local packaging are complete for v2.0.0.

## 10 Setup and reproducibility

The verified stack is Java 24, JavaFX 24.0.2, MySQL 8.0.46, Connector/J 26.7.0, Bouncy Castle 1.86 and Maven 3.9.16 on Windows. The existing private isolated instance uses loopback port 13316. Use fresh aceos_next_dev and disposable aceos_next_test, separate operator/runtime identities and environment-provided credentials. Existing activated development accounts need no reset/bootstrap.

On an empty selected schema execute V000__baseline.sql, then explicitly run com.aceos.database.Migrations with private migration variables. Grant the table-scoped runtime permissions in the setup guide; maintenance_records, complaint_notes and repair_reviews are SELECT/INSERT only, schema_migrations is read-only, and runtime cannot access operator_setup. Bootstrap the first administrator privately in an interactive console; run mvn javafx:run with runtime credentials.

mvn verify requires a disposable test URL plus runtime/test-operator credentials for database checks. ACEOS_UI_TEST=true enables graphical checks. Without those conditions, skipped checks are not correctness evidence. Tests may rebuild only disposable fixtures. database/demo_reset.sql selects original aceos and belongs to v1.0.0; never use it to prepare the current build or valuable data. Application startup verifies schema; it does not migrate automatically.

Close all AceOS windows before schema changes, back up and prove restoration, then migrate only the intended isolated schema. Keep dumps/configuration private. Maven now identifies v2.0.0; follow the preserved v1.0.0 tag for reproducing that older baseline. A standalone JAR is not claimed to bundle JavaFX/MySQL or private configuration.

## 11 Limitations future scope and conclusion

The final feature scope is sufficient for a local OOP/RDBMS college demonstration. There is no cancellation or return of verified repairs, no correction reassignment, no preventive work/history extension, and no automatic hold release. Additional notifications/calendar/attachments/QR/import-export/reporting, capacity/deadline/escalation, locations, commercial/inventory/vendor/integration and personalization features are future ideas, not college completion obligations. Deployment and pre-deployment preparation are excluded.

The final result is a coherent role-based application with traceable repair correction, relational integrity and transactional consistency. Tasks 1–5 are complete. Task 6 was closed at Smit’s request, without generated demo/viva artifacts. Task 7 final checks/package/local freeze is complete as v2.0.0; the frozen v1.0.0 baseline remains preserved.

## 12 Source references

The report describes the completed v2.0.0 college application. Current source and documentation are maintained on main. The annotated v2.0.0 tag identifies the packaged source; the GitHub release provides the college package, source archive and SHA-256 checksums.

- pom.xml and src/main/resources/database/V000__baseline.sql through V005__repair_corrections.sql
- src/main/java/com/aceos/model/Complaint.java and WorkOrder.java
- src/main/java/com/aceos/service/AceOsService.java
- src/main/java/com/aceos/database/Transactions.java Migrations.java and Bootstrap.java
- src/main/java/com/aceos/security/Authentication.java Session.java Scope.java and Passwords.java
- src/main/java/com/aceos/repository/RepairReviewRepository.java AvailabilityRepository.java and FoundationRepository.java
- src/main/java/com/aceos/ui/WorkspaceView.java FxTasks.java NavigationGuard.java and view/RepairFlowView.java
- src/test/java/com/aceos/FoundationTest.java and UiSmokeTest.java
- docs/college-scope.md foundation.md oop-design.md transactions.md verification.md and data-dictionary.md

The public repository and preserved baseline are at https://github.com/SmitHitnalli/aceos. The Notion knowledge hub is a documentation companion, not the runtime database.


## Appendix ER views

See [the complete relationship map](er-diagram.md).

## Appendix Data dictionary

See [all 16 tables](data-dictionary.md).
