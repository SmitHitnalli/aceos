ALTER TABLE assets ADD COLUMN manual_lifecycle VARCHAR(30) NULL;
UPDATE assets SET manual_lifecycle = CASE WHEN status='UNDER_MAINTENANCE' THEN 'ACTIVE' ELSE status END;
ALTER TABLE assets MODIFY manual_lifecycle VARCHAR(30) NOT NULL,
    ADD CONSTRAINT chk_manual_lifecycle CHECK (manual_lifecycle IN ('ACTIVE','OUT_OF_SERVICE','RETIRED'));
ALTER TABLE asset_inspections ADD COLUMN completed_at DATETIME(6) NULL;
ALTER TABLE work_orders MODIFY verified_at DATETIME(6) NULL;
CREATE TABLE asset_lifecycle_history (
    history_id INT AUTO_INCREMENT PRIMARY KEY,
    asset_id INT NOT NULL,
    actor_user_id INT NOT NULL,
    previous_lifecycle VARCHAR(30) NOT NULL,
    next_lifecycle VARCHAR(30) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (asset_id) REFERENCES assets(asset_id),
    FOREIGN KEY (actor_user_id) REFERENCES users(user_id)
);
CREATE TABLE asset_safety_holds (
    hold_id INT AUTO_INCREMENT PRIMARY KEY,
    asset_id INT NOT NULL,
    failed_inspection_id INT NOT NULL UNIQUE,
    created_by_user_id INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    cleared_by_user_id INT NULL,
    clearance_reason VARCHAR(1000) NULL,
    passing_inspection_id INT NULL,
    verified_work_order_id INT NULL,
    cleared_at DATETIME(6) NULL,
    FOREIGN KEY (asset_id) REFERENCES assets(asset_id),
    FOREIGN KEY (failed_inspection_id) REFERENCES asset_inspections(inspection_id),
    FOREIGN KEY (created_by_user_id) REFERENCES users(user_id),
    FOREIGN KEY (cleared_by_user_id) REFERENCES users(user_id),
    FOREIGN KEY (passing_inspection_id) REFERENCES asset_inspections(inspection_id),
    FOREIGN KEY (verified_work_order_id) REFERENCES work_orders(work_order_id),
    INDEX ix_holds_asset (asset_id, cleared_at),
    CONSTRAINT chk_hold_clearance CHECK (
        (cleared_at IS NULL AND cleared_by_user_id IS NULL AND clearance_reason IS NULL
            AND passing_inspection_id IS NULL AND verified_work_order_id IS NULL)
        OR (cleared_at IS NOT NULL AND cleared_by_user_id IS NOT NULL
            AND clearance_reason IS NOT NULL AND CHAR_LENGTH(TRIM(clearance_reason)) > 0
            AND ((passing_inspection_id IS NOT NULL AND verified_work_order_id IS NULL)
                OR (passing_inspection_id IS NULL AND verified_work_order_id IS NOT NULL)))
    )
);
