-- AceOS deterministic demonstration dataset
--
-- WARNING: This script deletes every existing row from all seven AceOS tables.
-- Run it only against a disposable demonstration or development database.
-- Use MySQL Workbench or another administrative connection because TRUNCATE
-- requires privileges that the restricted AceOS application user may not have.

USE aceos;

SET @aceos_previous_foreign_key_checks = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE asset_inspections;
TRUNCATE TABLE preventive_maintenance_schedules;
TRUNCATE TABLE maintenance_records;
TRUNCATE TABLE work_orders;
TRUNCATE TABLE complaints;
TRUNCATE TABLE assets;
TRUNCATE TABLE users;

SET FOREIGN_KEY_CHECKS = @aceos_previous_foreign_key_checks;

START TRANSACTION;

INSERT INTO users (
    user_id,
    name,
    email,
    role
)
VALUES
    (1, 'Rahul Sharma', 'rahul.requester@aceos.demo', 'REQUESTER'),
    (2, 'Priya Singh', 'priya.technician@aceos.demo', 'TECHNICIAN'),
    (3, 'Amit Verma', 'amit.administrator@aceos.demo', 'ADMINISTRATOR');

INSERT INTO assets (
    asset_id,
    asset_code,
    name,
    category,
    location,
    status
)
VALUES
    (
        1,
        'MUJ-PROJ-001',
        'Classroom Projector',
        'AV Equipment',
        'Academic Block 1 - Room 101',
        'ACTIVE'
    ),
    (
        2,
        'MUJ-AC-001',
        'Lecture Hall Air Conditioner',
        'HVAC',
        'Academic Block 1 - Room 204',
        'ACTIVE'
    ),
    (
        3,
        'MUJ-GEN-001',
        'Backup Generator',
        'Electrical',
        'Utility Block',
        'ACTIVE'
    ),
    (
        4,
        'MUJ-PUMP-001',
        'Water Pump',
        'Plumbing',
        'Services Block',
        'UNDER_MAINTENANCE'
    ),
    (
        5,
        'MUJ-LIFT-001',
        'Passenger Lift',
        'Vertical Transport',
        'Main Building',
        'UNDER_MAINTENANCE'
    ),
    (
        6,
        'MUJ-FIRE-001',
        'Fire Extinguisher',
        'Safety',
        'Library - Ground Floor',
        'ACTIVE'
    );

INSERT INTO complaints (
    complaint_id,
    asset_id,
    reported_by_user_id,
    description,
    priority,
    status,
    reported_at
)
VALUES
    (
        1,
        1,
        1,
        'Projector image flickers during presentations.',
        'HIGH',
        'SUBMITTED',
        CURRENT_TIMESTAMP - INTERVAL 5 DAY
    ),
    (
        2,
        2,
        1,
        'Air conditioner is not cooling the room.',
        'MEDIUM',
        'ACCEPTED',
        CURRENT_TIMESTAMP - INTERVAL 4 DAY
    ),
    (
        3,
        3,
        1,
        'Generator control panel shows a battery warning.',
        'HIGH',
        'WORK_ORDER_CREATED',
        CURRENT_TIMESTAMP - INTERVAL 3 DAY
    ),
    (
        4,
        4,
        1,
        'Water pump is producing abnormal vibration.',
        'CRITICAL',
        'WORK_ORDER_CREATED',
        CURRENT_TIMESTAMP - INTERVAL 2 DAY
    ),
    (
        5,
        5,
        1,
        'Lift doors do not close smoothly.',
        'HIGH',
        'WORK_ORDER_CREATED',
        CURRENT_TIMESTAMP - INTERVAL 4 DAY
    ),
    (
        6,
        6,
        1,
        'Fire-extinguisher wall bracket is loose.',
        'MEDIUM',
        'RESOLVED',
        CURRENT_TIMESTAMP - INTERVAL 10 DAY
    ),
    (
        7,
        1,
        1,
        'Projector remote control is missing.',
        'LOW',
        'REJECTED',
        CURRENT_TIMESTAMP - INTERVAL 1 DAY
    );

INSERT INTO work_orders (
    work_order_id,
    complaint_id,
    technician_user_id,
    status,
    assigned_at,
    started_at,
    completed_at,
    verified_at
)
VALUES
    (
        1,
        3,
        2,
        'ASSIGNED',
        CURRENT_TIMESTAMP - INTERVAL 3 DAY,
        NULL,
        NULL,
        NULL
    ),
    (
        2,
        4,
        2,
        'IN_PROGRESS',
        CURRENT_TIMESTAMP - INTERVAL 2 DAY,
        CURRENT_TIMESTAMP - INTERVAL 1 DAY,
        NULL,
        NULL
    ),
    (
        3,
        5,
        2,
        'COMPLETED',
        CURRENT_TIMESTAMP - INTERVAL 4 DAY,
        CURRENT_TIMESTAMP - INTERVAL 3 DAY,
        CURRENT_TIMESTAMP - INTERVAL 2 DAY,
        NULL
    ),
    (
        4,
        6,
        2,
        'VERIFIED',
        CURRENT_TIMESTAMP - INTERVAL 9 DAY,
        CURRENT_TIMESTAMP - INTERVAL 8 DAY,
        CURRENT_TIMESTAMP - INTERVAL 8 DAY,
        CURRENT_TIMESTAMP - INTERVAL 7 DAY
    );

INSERT INTO maintenance_records (
    maintenance_record_id,
    work_order_id,
    action_taken,
    recorded_at
)
VALUES
    (
        1,
        3,
        'Adjusted the lift-door alignment and tested the closing mechanism.',
        CURRENT_TIMESTAMP - INTERVAL 2 DAY
    ),
    (
        2,
        4,
        'Secured the fire-extinguisher bracket and confirmed safe mounting.',
        CURRENT_TIMESTAMP - INTERVAL 8 DAY
    );

INSERT INTO preventive_maintenance_schedules (
    maintenance_schedule_id,
    asset_id,
    task_description,
    frequency_days,
    next_due_date,
    status
)
VALUES
    (
        1,
        1,
        'Clean the projector filter and inspect the lamp.',
        90,
        CURRENT_DATE - INTERVAL 7 DAY,
        'ACTIVE'
    ),
    (
        2,
        2,
        'Perform quarterly HVAC cleaning and refrigerant inspection.',
        90,
        CURRENT_DATE + INTERVAL 30 DAY,
        'ACTIVE'
    ),
    (
        3,
        3,
        'Run the generator under load and inspect the battery.',
        30,
        CURRENT_DATE + INTERVAL 14 DAY,
        'PAUSED'
    );

INSERT INTO asset_inspections (
    inspection_id,
    asset_id,
    inspection_type,
    scheduled_date,
    status,
    inspected_by_user_id,
    notes,
    completed_date
)
VALUES
    (
        1,
        6,
        'Monthly fire-safety inspection',
        CURRENT_DATE + INTERVAL 14 DAY,
        'SCHEDULED',
        NULL,
        NULL,
        NULL
    ),
    (
        2,
        3,
        'Electrical safety inspection',
        CURRENT_DATE - INTERVAL 3 DAY,
        'PASSED',
        2,
        'Connections, battery and load test are within the expected range.',
        CURRENT_DATE - INTERVAL 3 DAY
    ),
    (
        3,
        5,
        'Lift safety inspection',
        CURRENT_DATE - INTERVAL 2 DAY,
        'FAILED',
        2,
        'Door alignment requires administrator verification after repair.',
        CURRENT_DATE - INTERVAL 2 DAY
    );

COMMIT;

-- Stable form IDs for the demonstration:
-- requester = 1, technician = 2, administrator = 3
-- submitted complaint = 1, accepted complaint = 2
-- assigned work order = 1, in-progress work order = 2
-- completed work order = 3, verified work order = 4

SELECT
    CASE
        WHEN
            (SELECT COUNT(*) FROM assets) = 6
            AND (SELECT COUNT(*) FROM assets WHERE status = 'ACTIVE') = 4
            AND (
                SELECT COUNT(*)
                FROM complaints
                WHERE status NOT IN ('REJECTED', 'RESOLVED')
            ) = 5
            AND (
                SELECT COUNT(*)
                FROM work_orders
                WHERE status <> 'VERIFIED'
            ) = 3
            AND (
                SELECT COUNT(*)
                FROM preventive_maintenance_schedules
                WHERE status = 'ACTIVE'
                  AND next_due_date < CURRENT_DATE
            ) = 1
            AND (
                SELECT COUNT(*)
                FROM asset_inspections
                WHERE status = 'FAILED'
            ) = 1
        THEN 'PASS'
        ELSE 'FAIL'
    END AS demo_dataset_verification,
    (SELECT COUNT(*) FROM assets) AS total_assets,
    (SELECT COUNT(*) FROM assets WHERE status = 'ACTIVE') AS active_assets,
    (
        SELECT COUNT(*)
        FROM complaints
        WHERE status NOT IN ('REJECTED', 'RESOLVED')
    ) AS open_complaints,
    (
        SELECT COUNT(*)
        FROM work_orders
        WHERE status <> 'VERIFIED'
    ) AS active_work_orders,
    (
        SELECT COUNT(*)
        FROM preventive_maintenance_schedules
        WHERE status = 'ACTIVE'
          AND next_due_date < CURRENT_DATE
    ) AS overdue_schedules,
    (
        SELECT COUNT(*)
        FROM asset_inspections
        WHERE status = 'FAILED'
    ) AS failed_inspections;
