USE aceos;

START TRANSACTION;

INSERT INTO users (name, email, role)
VALUES
    ('Rahul Sharma', 'rahul@aceos.test', 'REQUESTER'),
    ('Priya Singh', 'priya@aceos.test', 'TECHNICIAN'),
    ('Amit Verma', 'amit@aceos.test', 'ADMINISTRATOR');

INSERT INTO assets (
    asset_code,
    name,
    category,
    location,
    status
)
VALUES (
    'MUJ-AC-001',
    'Lecture Hall Air Conditioner',
    'HVAC',
    'Academic Block 1 - Room 204',
    'ACTIVE'
);

SET @requester_id = (
    SELECT user_id
    FROM users
    WHERE email = 'rahul@aceos.test'
);

SET @technician_id = (
    SELECT user_id
    FROM users
    WHERE email = 'priya@aceos.test'
);

SET @asset_id = (
    SELECT asset_id
    FROM assets
    WHERE asset_code = 'MUJ-AC-001'
);

INSERT INTO complaints (
    asset_id,
    reported_by_user_id,
    description,
    priority,
    status
)
VALUES (
    @asset_id,
    @requester_id,
    'Air conditioner is not cooling the room.',
    'HIGH',
    'RESOLVED'
);

SET @complaint_id = LAST_INSERT_ID();

INSERT INTO work_orders (
    complaint_id,
    technician_user_id,
    status,
    started_at,
    completed_at,
    verified_at
)
VALUES (
    @complaint_id,
    @technician_id,
    'VERIFIED',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

SET @work_order_id = LAST_INSERT_ID();

INSERT INTO maintenance_records (
    work_order_id,
    action_taken
)
VALUES (
    @work_order_id,
    'Cleaned the air filter and refilled the refrigerant.'
);

COMMIT;
