CREATE TABLE user_accounts (
 user_id INT PRIMARY KEY,
 login_email VARCHAR(255) NOT NULL UNIQUE,
 password_hash VARCHAR(255) NULL,
 state VARCHAR(25) NOT NULL DEFAULT 'PENDING_ACTIVATION',
 must_change BOOLEAN NOT NULL DEFAULT TRUE,
 temporary_expires_at TIMESTAMP NULL,
 failed_attempts INT NOT NULL DEFAULT 0,
 failure_window TIMESTAMP NULL,
 locked_until TIMESTAMP NULL,
 auth_version BIGINT NOT NULL DEFAULT 1,
 CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT,
 CONSTRAINT chk_account_state CHECK (state IN ('PENDING_ACTIVATION','ACTIVE','DISABLED'))
);
INSERT INTO user_accounts(user_id, login_email) SELECT user_id, LOWER(TRIM(email)) FROM users;
ALTER TABLE asset_inspections ADD COLUMN assigned_to_user_id INT NULL,
 ADD CONSTRAINT fk_inspection_assignee FOREIGN KEY (assigned_to_user_id) REFERENCES users(user_id) ON DELETE RESTRICT;
CREATE TABLE security_gate (gate_id INT PRIMARY KEY);
INSERT INTO security_gate VALUES (1);
CREATE TABLE operator_setup (setup_id INT PRIMARY KEY);
INSERT INTO operator_setup VALUES (1);
CREATE TABLE action_audit (
 audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 actor_user_id INT NULL,
 action VARCHAR(80) NOT NULL,
 target_id INT NULL,
 recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_audit_actor FOREIGN KEY(actor_user_id) REFERENCES users(user_id) ON DELETE RESTRICT
);
