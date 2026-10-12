package com.aceos.security;

import java.time.Instant;

/** Explicit one-time delivery result. Never log or serialize this record. */
public record IssuedCredential(int userId, String secret, Instant expiresAt) {
    @Override
    public String toString() {
        return "IssuedCredential[userId=" + userId + ", secret=REDACTED]";
    }
}
