

CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,

    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (
        role IN ('REQUESTER', 'TECHNICIAN', 'ADMINISTRATOR')
    )
);

CREATE TABLE IF NOT EXISTS assets (
    asset_id INT AUTO_INCREMENT,
    asset_code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(100) NOT NULL,
    location VARCHAR(150) NOT NULL,
    status VARCHAR(30) NOT NULL,

    CONSTRAINT pk_assets PRIMARY KEY (asset_id),
    CONSTRAINT uq_assets_asset_code UNIQUE (asset_code),
    CONSTRAINT chk_assets_status CHECK (
        status IN (
            'ACTIVE',
            'UNDER_MAINTENANCE',
            'OUT_OF_SERVICE',
            'RETIRED'
        )
    )
);

CREATE TABLE IF NOT EXISTS complaints (
    complaint_id INT AUTO_INCREMENT,
    asset_id INT NOT NULL,
    reported_by_user_id INT NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_complaints PRIMARY KEY (complaint_id),

    CONSTRAINT chk_complaints_priority CHECK (
        priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),

    CONSTRAINT chk_complaints_status CHECK (
        status IN (
            'SUBMITTED',
            'ACCEPTED',
            'REJECTED',
            'WORK_ORDER_CREATED',
            'RESOLVED'
        )
    ),

    CONSTRAINT fk_complaints_asset
        FOREIGN KEY (asset_id)
        REFERENCES assets (asset_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT fk_complaints_reported_by
        FOREIGN KEY (reported_by_user_id)
        REFERENCES users (user_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS work_orders (
    work_order_id INT AUTO_INCREMENT,
    complaint_id INT NOT NULL,
    technician_user_id INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    verified_at DATETIME NULL,

    CONSTRAINT pk_work_orders PRIMARY KEY (work_order_id),

    CONSTRAINT uq_work_orders_complaint UNIQUE (complaint_id),

    CONSTRAINT chk_work_orders_status CHECK (
        status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'VERIFIED')
    ),

    CONSTRAINT fk_work_orders_complaint
        FOREIGN KEY (complaint_id)
        REFERENCES complaints (complaint_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT fk_work_orders_technician
        FOREIGN KEY (technician_user_id)
        REFERENCES users (user_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS maintenance_records (
    maintenance_record_id INT AUTO_INCREMENT,
    work_order_id INT NOT NULL,
    action_taken TEXT NOT NULL,
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_maintenance_records PRIMARY KEY (maintenance_record_id),

    CONSTRAINT uq_maintenance_records_work_order UNIQUE (work_order_id),

    CONSTRAINT fk_maintenance_records_work_order
        FOREIGN KEY (work_order_id)
        REFERENCES work_orders (work_order_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS preventive_maintenance_schedules (
    maintenance_schedule_id INT AUTO_INCREMENT,
    asset_id INT NOT NULL,
    task_description VARCHAR(255) NOT NULL,
    frequency_days INT NOT NULL,
    next_due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT pk_preventive_maintenance_schedules
        PRIMARY KEY (maintenance_schedule_id),

    CONSTRAINT chk_preventive_schedule_frequency
        CHECK (frequency_days > 0),

    CONSTRAINT chk_preventive_schedule_status
        CHECK (status IN ('ACTIVE', 'PAUSED')),

    CONSTRAINT fk_preventive_schedule_asset
        FOREIGN KEY (asset_id)
        REFERENCES assets (asset_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS asset_inspections (
    inspection_id INT AUTO_INCREMENT,
    asset_id INT NOT NULL,
    inspection_type VARCHAR(150) NOT NULL,
    scheduled_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    inspected_by_user_id INT NULL,
    notes TEXT NULL,
    completed_date DATE NULL,

    CONSTRAINT pk_asset_inspections
        PRIMARY KEY (inspection_id),

    CONSTRAINT chk_asset_inspections_status
        CHECK (
            status IN (
                'SCHEDULED',
                'PASSED',
                'FAILED'
            )
        ),

    CONSTRAINT chk_asset_inspections_completion
        CHECK (
            (
                status = 'SCHEDULED'
                AND inspected_by_user_id IS NULL
                AND completed_date IS NULL
            )
            OR
            (
                status IN ('PASSED', 'FAILED')
                AND inspected_by_user_id IS NOT NULL
                AND notes IS NOT NULL
                AND completed_date IS NOT NULL
            )
        ),

    CONSTRAINT fk_asset_inspections_asset
        FOREIGN KEY (asset_id)
        REFERENCES assets (asset_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT fk_asset_inspections_inspector
        FOREIGN KEY (inspected_by_user_id)
        REFERENCES users (user_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
);
