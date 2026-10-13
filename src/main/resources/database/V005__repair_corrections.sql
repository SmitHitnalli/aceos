ALTER TABLE maintenance_records
    ADD COLUMN submission_number INT NOT NULL DEFAULT 1,
    ADD COLUMN submitted_by_user_id INT NULL,
    ADD COLUMN author_name VARCHAR(100) NULL,
    ADD COLUMN author_role VARCHAR(20) NULL,
    MODIFY recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD CONSTRAINT uq_submission_number UNIQUE (work_order_id, submission_number),
    ADD CONSTRAINT uq_submission_work_record UNIQUE (work_order_id, maintenance_record_id),
    ADD CONSTRAINT chk_submission_number CHECK (submission_number > 0),
    ADD CONSTRAINT fk_submission_author FOREIGN KEY (submitted_by_user_id) REFERENCES users(user_id),
    ADD CONSTRAINT chk_submission_author CHECK (
        (submitted_by_user_id IS NULL AND author_name IS NULL AND author_role IS NULL)
        OR (submitted_by_user_id IS NOT NULL AND author_name IS NOT NULL AND author_role IS NOT NULL AND author_role = 'TECHNICIAN'));
ALTER TABLE maintenance_records DROP INDEX uq_maintenance_records_work_order;
ALTER TABLE work_orders
    DROP CHECK chk_work_orders_status,
    ADD CONSTRAINT chk_work_orders_status CHECK (status IN ('ASSIGNED','IN_PROGRESS','COMPLETED','RETURNED','VERIFIED')),
    ADD COLUMN current_submission_id INT NULL,
    ADD COLUMN verified_submission_id INT NULL,
    ADD CONSTRAINT fk_work_current_submission FOREIGN KEY (work_order_id,current_submission_id)
        REFERENCES maintenance_records(work_order_id,maintenance_record_id),
    ADD CONSTRAINT fk_work_verified_submission FOREIGN KEY (work_order_id,verified_submission_id)
        REFERENCES maintenance_records(work_order_id,maintenance_record_id);
UPDATE work_orders w JOIN maintenance_records m ON m.work_order_id=w.work_order_id
SET w.current_submission_id=m.maintenance_record_id,
    w.verified_submission_id=IF(w.status='VERIFIED',m.maintenance_record_id,NULL);
CREATE TABLE repair_reviews (
    review_id INT AUTO_INCREMENT PRIMARY KEY,
    maintenance_record_id INT NOT NULL UNIQUE,
    kind VARCHAR(10) NOT NULL,
    actor_user_id INT NOT NULL,
    author_name VARCHAR(100) NOT NULL,
    author_role VARCHAR(20) NOT NULL,
    reason VARCHAR(2000) NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_repair_review_record FOREIGN KEY (maintenance_record_id) REFERENCES maintenance_records(maintenance_record_id),
    CONSTRAINT fk_repair_review_actor FOREIGN KEY (actor_user_id) REFERENCES users(user_id),
    CONSTRAINT chk_repair_review_kind CHECK (kind IN ('RETURN','VERIFY')),
    CONSTRAINT chk_repair_review_role CHECK (author_role='ADMINISTRATOR'),
    CONSTRAINT chk_repair_review_reason CHECK (
        (kind='RETURN' AND reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 2000)
        OR (kind='VERIFY' AND reason IS NULL))
);
