CREATE TABLE complaint_notes (
 note_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 complaint_id INT NOT NULL,
 author_user_id INT NOT NULL,
 author_role VARCHAR(20) NOT NULL,
 kind VARCHAR(20) NOT NULL,
 body VARCHAR(2000) NOT NULL,
 recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_note_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE RESTRICT,
 CONSTRAINT fk_note_author FOREIGN KEY (author_user_id) REFERENCES users(user_id) ON DELETE RESTRICT,
 CONSTRAINT chk_note_role CHECK (author_role IN ('REQUESTER','TECHNICIAN','ADMINISTRATOR')),
 CONSTRAINT chk_note_kind CHECK (kind IN ('COMMENT','ACCEPT_REASON','REJECT_REASON')),
 CONSTRAINT chk_note_body CHECK (CHAR_LENGTH(TRIM(body)) BETWEEN 1 AND 2000),
 INDEX idx_notes_complaint (complaint_id, recorded_at, note_id)
);
