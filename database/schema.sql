CREATE DATABASE IF NOT EXISTS aceos
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE aceos;

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

