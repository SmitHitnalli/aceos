package com.aceos.security;

import com.aceos.model.User;

import java.time.Duration;
import java.time.Instant;

/** Can only be issued by authentication; never constructed from a caller-supplied user ID. */
public final class Session {
    private final User user;
    private final long version;
    private final boolean passwordChangeRequired;
    private Instant lastActivity = Instant.now();
    private boolean revoked;

    Session(User user, long version, boolean passwordChangeRequired) {
        this.user = user;
        this.version = version;
        this.passwordChangeRequired = passwordChangeRequired;
    }

    public User user() {
        return user;
    }

    public long version() {
        return version;
    }

    public boolean passwordChangeRequired() {
        return passwordChangeRequired;
    }

    public synchronized boolean validAt(Instant now) {
        return !revoked
                && Duration.between(lastActivity, now).compareTo(Duration.ofMinutes(30)) < 0;
    }

    public synchronized void touch(Instant now) {
        lastActivity = now;
    }

    public synchronized void revoke() {
        revoked = true;
    }
}
