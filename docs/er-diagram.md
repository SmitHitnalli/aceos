# Final entity relationship diagrams

The current schema contains 16 tables. The complete Mermaid map below enumerates every database FK; the three printable views group related areas for readability. Actor links and some evidence links are summarized in the views and fully enumerated in the relationship table. Standalone metadata tables have no invented relational links.

A complaint has zero or one repair; a repair has zero or many submissions; a submission has zero or one terminal administrator review. Current/verified pointers are optional but must refer to a submission of the same repair.

![Repair and complaint relationships](diagrams/aceos-er-repairs.svg)

![Availability inspection and schedule relationships](diagrams/aceos-er-availability.svg)

![Account and infrastructure relationships](diagrams/aceos-er-accounts.svg)

## Complete relationship map

```mermaid
erDiagram
    users {
        int user_id PK
        varchar_100_ name
        varchar_255_ email
        varchar_20_ role
    }
    user_accounts {
        int user_id PK
        varchar_255_ login_email
        varchar_255_ password_hash
        varchar_25_ state
        tinyint_1_ must_change
        timestamp temporary_expires_at
        int failed_attempts
        timestamp failure_window
        timestamp locked_until
        bigint auth_version
    }
    assets {
        int asset_id PK
        varchar_50_ asset_code
        varchar_100_ name
        varchar_100_ category
        varchar_150_ location
        varchar_30_ status
        varchar_30_ manual_lifecycle
    }
    complaints {
        int complaint_id PK
        int asset_id FK
        int reported_by_user_id FK
        text description
        varchar_20_ priority
        varchar_30_ status
        datetime reported_at
    }
    work_orders {
        int work_order_id PK
        int complaint_id FK
        int technician_user_id FK
        varchar_20_ status
        datetime assigned_at
        datetime started_at
        datetime completed_at
        datetime_6_ verified_at
        int current_submission_id FK
        int verified_submission_id FK
    }
    maintenance_records {
        int maintenance_record_id PK
        int work_order_id FK
        text action_taken
        datetime_6_ recorded_at
        int submission_number
        int submitted_by_user_id FK
        varchar_100_ author_name
        varchar_20_ author_role
    }
    repair_reviews {
        int review_id PK
        int maintenance_record_id FK
        varchar_10_ kind
        int actor_user_id FK
        varchar_100_ author_name
        varchar_20_ author_role
        varchar_2000_ reason
        datetime_6_ recorded_at
    }
    complaint_notes {
        bigint note_id PK
        int complaint_id FK
        int author_user_id FK
        varchar_20_ author_role
        varchar_20_ kind
        varchar_2000_ body
        timestamp recorded_at
    }
    asset_inspections {
        int inspection_id PK
        int asset_id FK
        varchar_150_ inspection_type
        date scheduled_date
        varchar_20_ status
        int inspected_by_user_id FK
        text notes
        date completed_date
        int assigned_to_user_id FK
        datetime_6_ completed_at
    }
    preventive_maintenance_schedules {
        int maintenance_schedule_id PK
        int asset_id FK
        varchar_255_ task_description
        int frequency_days
        date next_due_date
        varchar_20_ status
    }
    asset_safety_holds {
        int hold_id PK
        int asset_id FK
        int failed_inspection_id FK
        int created_by_user_id FK
        datetime_6_ created_at
        int cleared_by_user_id FK
        varchar_1000_ clearance_reason
        int passing_inspection_id FK
        int verified_work_order_id FK
        datetime_6_ cleared_at
    }
    asset_lifecycle_history {
        int history_id PK
        int asset_id FK
        int actor_user_id FK
        varchar_30_ previous_lifecycle
        varchar_30_ next_lifecycle
        varchar_1000_ reason
        datetime_6_ changed_at
    }
    action_audit {
        bigint audit_id PK
        int actor_user_id FK
        varchar_80_ action
        int target_id
        timestamp recorded_at
    }
    security_gate {
        int gate_id PK
    }
    operator_setup {
        int setup_id PK
    }
    schema_migrations {
        int version PK
        char_64_ checksum
        varchar_20_ status
    }
    users o|--o{ action_audit : "actor_user_id"
    assets ||--o{ asset_inspections : "asset_id"
    users o|--o{ asset_inspections : "inspected_by_user_id"
    users o|--o{ asset_inspections : "assigned_to_user_id"
    assets ||--o{ asset_lifecycle_history : "asset_id"
    users ||--o{ asset_lifecycle_history : "actor_user_id"
    assets ||--o{ asset_safety_holds : "asset_id"
    asset_inspections ||--o| asset_safety_holds : "failed_inspection_id"
    users ||--o{ asset_safety_holds : "created_by_user_id"
    users o|--o{ asset_safety_holds : "cleared_by_user_id"
    asset_inspections o|--o{ asset_safety_holds : "passing_inspection_id"
    work_orders o|--o{ asset_safety_holds : "verified_work_order_id"
    users ||--o{ complaint_notes : "author_user_id"
    complaints ||--o{ complaint_notes : "complaint_id"
    assets ||--o{ complaints : "asset_id"
    users ||--o{ complaints : "reported_by_user_id"
    work_orders ||--o{ maintenance_records : "work_order_id"
    users o|--o{ maintenance_records : "submitted_by_user_id"
    assets ||--o{ preventive_maintenance_schedules : "asset_id"
    users ||--o{ repair_reviews : "actor_user_id"
    maintenance_records ||--o| repair_reviews : "maintenance_record_id"
    users ||--o| user_accounts : "user_id"
    maintenance_records o|--o| work_orders : "work_order_id, current_submission_id"
    complaints ||--o| work_orders : "complaint_id"
    users ||--o{ work_orders : "technician_user_id"
    maintenance_records o|--o| work_orders : "work_order_id, verified_submission_id"
```

## Every foreign key

| Child table | Child fields | Referenced table | Referenced fields |
|---|---|---|---|
| `action_audit` | `actor_user_id` | `users` | `user_id` |
| `asset_inspections` | `asset_id` | `assets` | `asset_id` |
| `asset_inspections` | `inspected_by_user_id` | `users` | `user_id` |
| `asset_inspections` | `assigned_to_user_id` | `users` | `user_id` |
| `asset_lifecycle_history` | `asset_id` | `assets` | `asset_id` |
| `asset_lifecycle_history` | `actor_user_id` | `users` | `user_id` |
| `asset_safety_holds` | `asset_id` | `assets` | `asset_id` |
| `asset_safety_holds` | `failed_inspection_id` | `asset_inspections` | `inspection_id` |
| `asset_safety_holds` | `created_by_user_id` | `users` | `user_id` |
| `asset_safety_holds` | `cleared_by_user_id` | `users` | `user_id` |
| `asset_safety_holds` | `passing_inspection_id` | `asset_inspections` | `inspection_id` |
| `asset_safety_holds` | `verified_work_order_id` | `work_orders` | `work_order_id` |
| `complaint_notes` | `author_user_id` | `users` | `user_id` |
| `complaint_notes` | `complaint_id` | `complaints` | `complaint_id` |
| `complaints` | `asset_id` | `assets` | `asset_id` |
| `complaints` | `reported_by_user_id` | `users` | `user_id` |
| `maintenance_records` | `work_order_id` | `work_orders` | `work_order_id` |
| `maintenance_records` | `submitted_by_user_id` | `users` | `user_id` |
| `preventive_maintenance_schedules` | `asset_id` | `assets` | `asset_id` |
| `repair_reviews` | `actor_user_id` | `users` | `user_id` |
| `repair_reviews` | `maintenance_record_id` | `maintenance_records` | `maintenance_record_id` |
| `user_accounts` | `user_id` | `users` | `user_id` |
| `work_orders` | `work_order_id, current_submission_id` | `maintenance_records` | `work_order_id, maintenance_record_id` |
| `work_orders` | `complaint_id` | `complaints` | `complaint_id` |
| `work_orders` | `technician_user_id` | `users` | `user_id` |
| `work_orders` | `work_order_id, verified_submission_id` | `maintenance_records` | `work_order_id, maintenance_record_id` |

PKs, unique keys, field definitions and service/SQL boundaries are in [the data dictionary](data-dictionary.md). No FK is claimed for action_audit.target_id; its action determines the target type.
