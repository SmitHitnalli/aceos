package com.aceos.security;

import static com.aceos.security.SecurityRepository.*;

import com.aceos.database.Transactions;
import com.aceos.model.User;
import com.aceos.model.UserRole;

import java.sql.*;
import java.time.Instant;
import java.util.Locale;

public final class Authentication {
    private static final String DUMMY = Passwords.hash("unavailable account dummy password");

    private Authentication() {}

    public static Session login(String email, String password) throws SQLException {
        try (Transactions tx = new Transactions()) {
            lockGate();
            Account a = account(email == null ? "" : email.trim().toLowerCase(Locale.ROOT), null);
            Instant now = Instant.now();
            boolean correct =
                    Passwords.matches(password, a == null || a.hash() == null ? DUMMY : a.hash());
            if (a == null
                    || !correct
                    || "DISABLED".equals(a.state())
                    || (a.locked() != null && a.locked().isAfter(now))
                    || (a.mustChange() && (a.expiry() == null || !a.expiry().isAfter(now)))) {
                if (a != null && (a.locked() == null || !a.locked().isAfter(now))) {
                    int n =
                            a.window() == null || a.window().plusSeconds(900).isBefore(now)
                                    ? 1
                                    : a.failures() + 1;
                    update(
                            AUTH_QUERY_1,
                            n,
                            n == 1 ? now : a.window(),
                            n >= 5 ? now.plusSeconds(900) : null,
                            a.user().getUserId());
                    audit(a.user().getUserId(), "LOGIN_FAILED", a.user().getUserId());
                }
                tx.commit();
                throw new IllegalArgumentException(
                        "Unable to sign in. Check your details or contact your administrator.");
            }
            update(AUTH_QUERY_2, a.user().getUserId());
            audit(a.user().getUserId(), "LOGIN", a.user().getUserId());
            tx.commit();
            return new Session(a.user(), a.version(), a.mustChange());
        }
    }

    public static Session changePassword(Session session, String current, String next)
            throws SQLException {
        Passwords.validate(next);
        try (Transactions tx = new Transactions()) {
            lockGate();
            User user = validate(session, true);
            Account a = account(null, user.getUserId());
            if (!Passwords.matches(current, a.hash()))
                throw new IllegalArgumentException("Current password is incorrect");
            if (Passwords.matches(next, a.hash()))
                throw new IllegalArgumentException("Choose a different password");
            update(AUTH_QUERY_3, Passwords.hash(next), user.getUserId());
            audit(user.getUserId(), "PASSWORD_CHANGED", user.getUserId());
            tx.commit();
            session.revoke();
            return new Session(user, a.version() + 1, false);
        }
    }

    public static IssuedCredential issue(User actor, int target) throws SQLException {
        requireRole(actor, UserRole.ADMINISTRATOR);
        Account a = account(null, target);
        if (a == null) throw new IllegalArgumentException("Account not found");
        if ("DISABLED".equals(a.state()))
            throw new IllegalArgumentException(
                    "Reactivate the account before resetting credentials");
        if (target == actor.getUserId())
            throw new IllegalArgumentException("Use Change password for your own account");
        if (a.user().getRole() == UserRole.ADMINISTRATOR
                && count(AUTH_QUERY_4) <= 1
                && "ACTIVE".equals(a.state()))
            throw new IllegalArgumentException(
                    "Keep another active administrator before resetting this account");
        return issueUnchecked(actor.getUserId(), target, "CREDENTIAL_ISSUED");
    }

    public static IssuedCredential issueUnchecked(Integer actor, int target, String event)
            throws SQLException {
        String secret = Passwords.temporarySecret();
        Instant expiry = Instant.now().plusSeconds(86400);
        update(AUTH_QUERY_5, Passwords.hash(secret), expiry, target);
        audit(actor, event, target);
        return new IssuedCredential(target, secret, expiry);
    }
}
