# Final data dictionary

Current V001–V005 schema, verified on 13 October 2026. All 16 tables are included. Types/defaults/nullability/keys come from read-only database metadata, cross-checked against migrations. No application data or credentials are included.

PK = primary key; FK = foreign key; UQ member = participates in a unique key (not necessarily unique alone). MySQL BOOLEAN appears as tinyint(1). `<none>` means no non-null default reported; nullable columns can default to SQL NULL. Service rules supplement database constraints.

## users

Domain identities and current roles; retained rather than deleted.

| Field | SQL type | Nullable | Default | Key | Meaning |
|---|---|---|---|---|---|
| `user_id` | `int` | NO | `<none>` auto increment | PK | Primary identifier. |
| `name` | `varchar(100)` | NO | `<none>` | - | Current display name. |
| `email` | `varchar(255)` | NO | `<none>` | UQ member | Domain email; unique in users. |
| `role` | `varchar(20)` | NO | `<none>` | - | Current REQUESTER, TECHNICIAN or ADMINISTRATOR role. |

Roles are constrained; email is unique. Account/assignment safeguards are service rules.

- Unique `uq_users_email`: `email`

## user_accounts

Authentication state, normalized login, credential hashes, cooldown and session invalidation version.

| Field | SQL type | Nullable | Default | Key | Meaning |
|---|---|---|---|---|---|
| `user_id` | `int` | NO | `<none>` | PK, FK | Primary identity and users foreign key. |
| `login_email` | `varchar(255)` | NO | `<none>` | UQ member | Trimmed lowercase login identifier, unique separately from domain email. |
| `password_hash` | `varchar(255)` | YES | `<none>` | - | Encoded Argon2id hash, never plaintext; null before activation/when disabled. |
| `state` | `varchar(25)` | NO | `PENDING_ACTIVATION` | - | Persisted account state; see table rules. |
| `must_change` | `tinyint(1)` | NO | `1` | - | Forced-password-replacement flag; BOOLEAN stored by MySQL as tinyint. |
| `temporary_expires_at` | `timestamp` | YES | `<none>` | - | Expiry of issued temporary credentials. |
| `failed_attempts` | `int` | NO | `0` | - | Persistent failed-login counter. |
| `failure_window` | `timestamp` | YES | `<none>` | - | Start of failure-counting window. |
| `locked_until` | `timestamp` | YES | `<none>` | - | Cooldown expiry; LOCKED is a derived UI state. |
| `auth_version` | `bigint` | NO | `1` | - | Incremented to invalidate existing sessions. |

Persisted states are PENDING_ACTIVATION, ACTIVE and DISABLED. LOCKED is derived from locked_until; not a fourth stored state.

- Unique `login_email`: `login_email`
- FK `fk_accounts_user`: (user_id) → users (user_id)

## assets

Registered asset details, manual lifecycle and transactionally recalculated availability.

| Field | SQL type | Nullable | Default | Key | Meaning |
|---|---|---|---|---|---|
| `asset_id` | `int` | NO | `<none>` auto increment | PK | Primary identifier. |
| `asset_code` | `varchar(50)` | NO | `<none>` | UQ member | Unique human-readable asset code. |
| `name` | `varchar(100)` | NO | `<none>` | - | Current display name. |
| `category` | `varchar(100)` | NO | `<none>` | - | Asset category text. |
| `location` | `varchar(150)` | NO | `<none>` | - | Asset location text. |
| `status` | `varchar(30)` | NO | `<none>` | - | Current workflow/availability state; see table rules. |
| `manual_lifecycle` | `varchar(30)` | NO | `<none>` | - | Administrator-controlled ACTIVE, OUT_OF_SERVICE or RETIRED lifecycle. |

Effective status is ACTIVE, UNDER_MAINTENANCE, OUT_OF_SERVICE or RETIRED. Manual lifecycle omits UNDER_MAINTENANCE. Effective status is a derived value persisted by recalculation.

- Unique `uq_assets_asset_code`: `asset_code`

## complaints

Requester report and current complaint lifecycle, preserving original identity and reported time.

| Field | SQL type | Nullable | Default | Key | Meaning |
|---|---|---|---|---|---|
| `complaint_id` | `int` | NO | `<none>` auto increment | PK | Primary identifier. |
| `asset_id` | `int` | NO | `<none>` | FK | Related asset reference. |
| `reported_by_user_id` | `int` | NO | `<none>` | FK | Original requester reference. |
| `description` | `text` | NO | `<none>` | - | Original reported problem. |
| `priority` | `varchar(20)` | NO | `<none>` | - | LOW, MEDIUM, HIGH or CRITICAL. |
| `status` | `varchar(30)` | NO | `<none>` | - | Current workflow/availability state; see table rules. |
| `reported_at` | `datetime` | NO | `CURRENT_TIMESTAMP` | - | Original complaint report timestamp. |

States: SUBMITTED, ACCEPTED, REJECTED, WORK_ORDER_CREATED, RESOLVED, WITHDRAWN. Closed complaints cannot receive comments. Reopening requires no linked work and a non-retired asset.

- FK `fk_complaints_asset`: (asset_id) → assets (asset_id)
- FK `fk_complaints_reported_by`: (reported_by_user_id) → users (user_id)

## work_orders

One repair per complaint; assignment, progress and exact current/accepted submission links.

| Field | SQL type | Nullable | Default | Key | Meaning |
|---|---|---|---|---|---|
| `work_order_id` | `int` | NO | `<none>` auto increment | PK, FK | Primary identifier. |
| `complaint_id` | `int` | NO | `<none>` | FK, UQ member | Parent complaint reference. |
| `technician_user_id` | `int` | NO | `<none>` | FK | Assigned technician reference; reassignment only before start. |
| `status` | `varchar(20)` | NO | `<none>` | - | Current workflow/availability state; see table rules. |
| `assigned_at` | `datetime` | NO | `CURRENT_TIMESTAMP` | - | Original assignment time. |
| `started_at` | `datetime` | YES | `<none>` | - | First start time; null before start. |
| `completed_at` | `datetime` | YES | `<none>` | - | First repair completion time; preserved during correction cycles. |
| `verified_at` | `datetime(6)` | YES | `<none>` | - | Final administrator verification time; null while unverified. |
| `current_submission_id` | `int` | YES | `<none>` | FK | Exact current submission; composite FK includes this work_order_id. |
| `verified_submission_id` | `int` | YES | `<none>` | FK | Exact accepted submission; composite FK includes this work_order_id. |

States: ASSIGNED, IN_PROGRESS, COMPLETED, RETURNED, VERIFIED. complaint_id unique; started/first-completed timestamps retained across correction. Composite pointer FKs enforce same-work evidence; services enforce state coherence.

- Unique `uq_work_orders_complaint`: `complaint_id`
- FK `fk_work_current_submission`: (work_order_id, current_submission_id) → maintenance_records (work_order_id, maintenance_record_id)
- FK `fk_work_orders_complaint`: (complaint_id) → complaints (complaint_id)
- FK `fk_work_orders_technician`: (technician_user_id) → users (user_id)
- FK `fk_work_verified_submission`: (work_order_id, verified_submission_id) → maintenance_records (work_order_id, maintenance_record_id)

## maintenance_records

Immutable numbered repair submissions, including the original completion and corrections.

| Field | SQL type | Nullable | Default | Key | Meaning |
|---|---|---|---|---|---|
| `maintenance_record_id` | `int` | NO | `<none>` auto increment | PK, UQ member | Primary identifier. |
| `work_order_id` | `int` | NO | `<none>` | FK, UQ member | Parent repair reference. |
| `action_taken` | `text` | NO | `<none>` | - | Submitted work/correction description; earlier text is not overwritten. |
| `recorded_at` | `datetime(6)` | NO | `CURRENT_TIMESTAMP(6)` | - | Stored submission/note/review/audit recording time. |
| `submission_number` | `int` | NO | `1` | UQ member | Positive sequence within a work order; existing records migrate as 1. |
| `submitted_by_user_id` | `int` | YES | `<none>` | FK | Submitter reference; null for unknown migrated historical authors. |
| `author_name` | `varchar(100)` | YES | `<none>` | - | Name snapshot at submission/review; unknown legacy submission stays null. |
| `author_role` | `varchar(20)` | YES | `<none>` | - | Role snapshot at posting; repair submitter TECHNICIAN, reviewer ADMINISTRATOR. |

UNIQUE(work_order_id, submission_number). Positive submission number; author snapshot is wholly unknown or contains a technician identity/name/role. Runtime SELECT/INSERT only.

- Unique `uq_submission_number`: `work_order_id`, `submission_number`
- Unique `uq_submission_work_record`: `work_order_id`, `maintenance_record_id`
- FK `fk_maintenance_records_work_order`: (work_order_id) → work_orders (work_order_id)
- FK `fk_submission_author`: (submitted_by_user_id) → users (user_id)

## repair_reviews

Append-only terminal administrator RETURN or VERIFY review of an exact submission.

| Field | SQL type | Nullable | Default | Key | Meaning |
|---|---|---|---|---|---|
| `review_id` | `int` | NO | `<none>` auto increment | PK | Primary identifier. |
| `maintenance_record_id` | `int` | NO | `<none>` | FK, UQ member | Exact reviewed submission reference. |
| `kind` | `varchar(10)` | NO | `<none>` | - | RETURN or VERIFY terminal review kind. |
| `actor_user_id` | `int` | NO | `<none>` | FK | Authenticated reviewing administrator reference. |
| `author_name` | `varchar(100)` | NO | `<none>` | - | Administrator name snapshot at review. |
| `author_role` | `varchar(20)` | NO | `<none>` | - | Administrator role snapshot at review. |
| `reason` | `varchar(2000)` | YES | `<none>` | - | Required explanation for lifecycle/RETURN; VERIFY review has null reason. |
| `recorded_at` | `datetime(6)` | NO | `CURRENT_TIMESTAMP(6)` | - | Stored submission/note/review/audit recording time. |

One review per maintenance_record_id. kind RETURN requires nonblank reason up to 2000 Unicode characters; VERIFY has no reason. Reviewer role ADMINISTRATOR. Runtime SELECT/INSERT only.

- Unique `maintenance_record_id`: `maintenance_record_id`
- FK `fk_repair_review_actor`: (actor_user_id) → users (user_id)
- FK `fk_repair_review_record`: (maintenance_record_id) → maintenance_records (maintenance_record_id)

## complaint_notes

Append-only comments and required review/withdrawal/reopening reasons.

| Field | SQL type | Nullable | Default | Key | Meaning |
|---|---|---|---|---|---|
| `note_id` | `bigint` | NO | `<none>` auto increment | PK | Primary identifier. |
| `complaint_id` | `int` | NO | `<none>` | FK | Parent complaint reference. |
| `author_user_id` | `int` | NO | `<none>` | FK | Posting user reference; displayed complaint-note name joins current users.name. |
| `author_role` | `varchar(20)` | NO | `<none>` | - | Requester, technician or administrator role snapshot at posting. |
| `kind` | `varchar(20)` | NO | `<none>` | - | COMMENT or acceptance/rejection/withdrawal/reopening reason kind. |
| `body` | `varchar(2000)` | NO | `<none>` | - | Nonblank complaint comment or reason, at most 2000 Unicode characters. |
| `recorded_at` | `timestamp` | NO | `CURRENT_TIMESTAMP` | - | Stored submission/note/review/audit recording time. |

Kinds COMMENT, ACCEPT_REASON, REJECT_REASON, WITHDRAW_REASON, REOPEN_REASON. Valid posting roles and nonblank body up to 2000 Unicode characters. Store author ID/role; name is read from users. Runtime SELECT/INSERT only.

- FK `fk_note_author`: (author_user_id) → users (user_id)
- FK `fk_note_complaint`: (complaint_id) → complaints (complaint_id)

## asset_inspections

Scheduled/assigned inspections and retained pass/fail findings and dates.

| Field | SQL type | Nullable | Default | Key | Meaning |
|---|---|---|---|---|---|
| `inspection_id` | `int` | NO | `<none>` auto increment | PK | Primary identifier. |
| `asset_id` | `int` | NO | `<none>` | FK | Related asset reference. |
| `inspection_type` | `varchar(150)` | NO | `<none>` | - | Inspection task/type description. |
| `scheduled_date` | `date` | NO | `<none>` | - | Planned inspection date. |
| `status` | `varchar(20)` | NO | `SCHEDULED` | - | Current workflow/availability state; see table rules. |
| `inspected_by_user_id` | `int` | YES | `<none>` | FK | Actual completing inspector; null while scheduled. |
| `notes` | `text` | YES | `<none>` | - | Completed inspection findings. |
| `completed_date` | `date` | YES | `<none>` | - | Inspection completion date; distinct from recording timestamp. |
| `assigned_to_user_id` | `int` | YES | `<none>` | FK | Assigned inspector; nullable for unassigned scheduled inspections. |
| `completed_at` | `datetime(6)` | YES | `<none>` | - | Precise inspection result recording time; separate from completed_date. |

SCHEDULED has no completing inspector/date. PASSED/FAILED require inspector, notes and completion date; nonblank findings/assignment/date policy is checked by service. Failed completion creates a hold atomically.

- FK `fk_asset_inspections_asset`: (asset_id) → assets (asset_id)
- FK `fk_asset_inspections_inspector`: (inspected_by_user_id) → users (user_id)
- FK `fk_inspection_assignee`: (assigned_to_user_id) → users (user_id)

## preventive_maintenance_schedules

Recurring active/paused tasks with frequency and next due date; no completion-history rows.

| Field | SQL type | Nullable | Default | Key | Meaning |
|---|---|---|---|---|---|
| `maintenance_schedule_id` | `int` | NO | `<none>` auto increment | PK | Primary identifier. |
| `asset_id` | `int` | NO | `<none>` | FK | Related asset reference. |
| `task_description` | `varchar(255)` | NO | `<none>` | - | Recurring maintenance task description. |
| `frequency_days` | `int` | NO | `<none>` | - | Positive recurrence interval in days. |
| `next_due_date` | `date` | NO | `<none>` | - | Current due date; completion advances it by frequency. |
| `status` | `varchar(20)` | NO | `ACTIVE` | - | Current workflow/availability state; see table rules. |

ACTIVE or PAUSED, frequency_days > 0. Completion updates next_due_date; no preventive work order/history table.

- FK `fk_preventive_schedule_asset`: (asset_id) → assets (asset_id)

## asset_safety_holds

Individual failed-inspection holds and explicit evidence-backed administrator clearance.

| Field | SQL type | Nullable | Default | Key | Meaning |
|---|---|---|---|---|---|
| `hold_id` | `int` | NO | `<none>` auto increment | PK | Primary identifier. |
| `asset_id` | `int` | NO | `<none>` | FK | Related asset reference. |
| `failed_inspection_id` | `int` | NO | `<none>` | FK, UQ member | Unique originating failed inspection; each failure has its own hold. |
| `created_by_user_id` | `int` | NO | `<none>` | FK | User who recorded the failed inspection/hold. |
| `created_at` | `datetime(6)` | NO | `CURRENT_TIMESTAMP(6)` | - | Hold recording time. |
| `cleared_by_user_id` | `int` | YES | `<none>` | FK | Administrator who cleared this hold; null while active. |
| `clearance_reason` | `varchar(1000)` | YES | `<none>` | - | Required explicit clearance explanation; null while active. |
| `passing_inspection_id` | `int` | YES | `<none>` | FK | Optional chosen later passing inspection evidence. |
| `verified_work_order_id` | `int` | YES | `<none>` | FK | Optional chosen verified corrective repair evidence. |
| `cleared_at` | `datetime(6)` | YES | `<none>` | - | Recorded clearance time; null while active. |

An active hold has no clearance fields. Cleared hold requires administrator/reason/time and exactly one evidence type. Temporal/same-asset evidence validity is rechecked by service.

- Unique `failed_inspection_id`: `failed_inspection_id`
- FK `asset_safety_holds_ibfk_1`: (asset_id) → assets (asset_id)
- FK `asset_safety_holds_ibfk_2`: (failed_inspection_id) → asset_inspections (inspection_id)
- FK `asset_safety_holds_ibfk_3`: (created_by_user_id) → users (user_id)
- FK `asset_safety_holds_ibfk_4`: (cleared_by_user_id) → users (user_id)
- FK `asset_safety_holds_ibfk_5`: (passing_inspection_id) → asset_inspections (inspection_id)
- FK `asset_safety_holds_ibfk_6`: (verified_work_order_id) → work_orders (work_order_id)

## asset_lifecycle_history

Recorded manual lifecycle transitions with actor, reason and time.

| Field | SQL type | Nullable | Default | Key | Meaning |
|---|---|---|---|---|---|
| `history_id` | `int` | NO | `<none>` auto increment | PK | Primary identifier. |
| `asset_id` | `int` | NO | `<none>` | FK | Related asset reference. |
| `actor_user_id` | `int` | NO | `<none>` | FK | Acting administrator reference. |
| `previous_lifecycle` | `varchar(30)` | NO | `<none>` | - | Lifecycle before the recorded transition. |
| `next_lifecycle` | `varchar(30)` | NO | `<none>` | - | Lifecycle after the recorded transition. |
| `reason` | `varchar(1000)` | NO | `<none>` | - | Required manual lifecycle change explanation. |
| `changed_at` | `datetime(6)` | NO | `CURRENT_TIMESTAMP(6)` | - | Manual lifecycle transition recording time. |

Actor/reason/time preserve manual transitions. Runtime table grants do not by themselves guarantee append-only history; application writes insert transitions.

- FK `asset_lifecycle_history_ibfk_1`: (asset_id) → assets (asset_id)
- FK `asset_lifecycle_history_ibfk_2`: (actor_user_id) → users (user_id)

## action_audit

Application/operator events. Interpret target_id with action; it has no generic target foreign key.

| Field | SQL type | Nullable | Default | Key | Meaning |
|---|---|---|---|---|---|
| `audit_id` | `bigint` | NO | `<none>` auto increment | PK | Primary identifier. |
| `actor_user_id` | `int` | YES | `<none>` | FK | Acting user reference; nullable audit actor can represent operator activity. |
| `action` | `varchar(80)` | NO | `<none>` | - | Recorded event name. |
| `target_id` | `int` | YES | `<none>` | - | Event-specific target identifier; pair with action. |
| `recorded_at` | `timestamp` | NO | `CURRENT_TIMESTAMP` | - | Stored submission/note/review/audit recording time. |

Recorded events are written with their operation transaction. target_id is intentionally not a foreign key because targets have different entity types.

- FK `fk_audit_actor`: (actor_user_id) → users (user_id)

## security_gate

Single-row local coordination gate, not a user-facing work item.

| Field | SQL type | Nullable | Default | Key | Meaning |
|---|---|---|---|---|---|
| `gate_id` | `int` | NO | `<none>` | PK | Single coordination-row key, value 1. |

The current application serializes local service operations through row 1.


## operator_setup

Operator-only coordination row for deliberate bootstrap/recovery.

| Field | SQL type | Nullable | Default | Key | Meaning |
|---|---|---|---|---|---|
| `setup_id` | `int` | NO | `<none>` | PK | Single operator coordination-row key, value 1. |

No runtime permission. Operator bootstrap/recovery uses this coordination row.


## schema_migrations

Operator-managed migration version/checksum/STARTED or COMPLETE state; runtime reads only.

| Field | SQL type | Nullable | Default | Key | Meaning |
|---|---|---|---|---|---|
| `version` | `int` | NO | `<none>` | PK | Numbered migration version. |
| `checksum` | `char(64)` | NO | `<none>` | - | Canonical resource SHA-256. |
| `status` | `varchar(20)` | NO | `<none>` | - | STARTED or COMPLETE operator migration state. |

Runtime checks versions 1-5 COMPLETE and matching resource checksums plus schema definitions. No automatic migration at sign-in.
