package com.aceos.model;

import java.time.LocalDateTime;

/** Public-to-authorized-participants, append-only complaint history; no contact details. */
public record ComplaintNote(long id, int authorId, String authorName, UserRole authorRole,
                            Kind kind, String body, LocalDateTime at) {
    public enum Kind { COMMENT, ACCEPT_REASON, REJECT_REASON }
}
