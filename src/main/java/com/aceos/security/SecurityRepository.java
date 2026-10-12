package com.aceos.security;

import com.aceos.database.DatabaseConnection;
import com.aceos.database.Migrations;
import com.aceos.model.User;
import com.aceos.model.UserRole;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class SecurityRepository {
    public static final String AUTH_QUERY_5 =
            "UPDATE user_accounts SET"
                + " password_hash=?,state='PENDING_ACTIVATION',must_change=TRUE,temporary_expires_at=?,failed_attempts=0,locked_until=NULL,failure_window=NULL,auth_version=auth_version+1"
                + " WHERE user_id=?";
    public static final String AUTH_QUERY_4 =
            "SELECT COUNT(*) FROM users u JOIN user_accounts a ON a.user_id=u.user_id WHERE"
                + " u.role='ADMINISTRATOR' AND a.state='ACTIVE' AND a.must_change=FALSE";
    public static final String AUTH_QUERY_3 =
            "UPDATE user_accounts SET"
                + " password_hash=?,state='ACTIVE',must_change=FALSE,temporary_expires_at=NULL,failed_attempts=0,locked_until=NULL,failure_window=NULL,auth_version=auth_version+1"
                + " WHERE user_id=?";
    public static final String AUTH_QUERY_2 =
            "UPDATE user_accounts SET failed_attempts=0,failure_window=NULL,locked_until=NULL WHERE"
                + " user_id=?";
    public static final String AUTH_QUERY_1 =
            "UPDATE user_accounts SET failed_attempts=?,failure_window=?,locked_until=? WHERE"
                + " user_id=?";

    private SecurityRepository() {}

    public static Connection connection() throws SQLException {
        return DatabaseConnection.getConnection();
    }

    public static void lockGate() throws SQLException {
        Migrations.assertCompatible(connection());
        try (Statement s = connection().createStatement();
                ResultSet r =
                        s.executeQuery(
                                "SELECT gate_id FROM security_gate WHERE gate_id=1 FOR UPDATE")) {
            if (!r.next()) throw new SQLException("Missing security coordination row");
        }
    }

    public static int update(String sql, Object... args) throws SQLException {
        try (PreparedStatement p = connection().prepareStatement(sql)) {
            bind(p, args);
            return p.executeUpdate();
        }
    }

    public static long count(String sql, Object... args) throws SQLException {
        try (PreparedStatement p = connection().prepareStatement(sql)) {
            bind(p, args);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }

    public static void bind(PreparedStatement p, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++)
            p.setObject(i + 1, args[i] instanceof Instant t ? Timestamp.from(t) : args[i]);
    }

    public static void audit(Integer actor, String action, Integer target) throws SQLException {
        update(
                "INSERT INTO action_audit(actor_user_id,action,target_id) VALUES (?,?,?)",
                actor,
                action,
                target);
    }

    public static Account account(String email, Integer id) throws SQLException {
        String sql =
                "SELECT u.*,a.* FROM users u JOIN user_accounts a ON a.user_id=u.user_id WHERE "
                        + (id == null ? "a.login_email=?" : "u.user_id=?")
                        + " FOR UPDATE";
        try (PreparedStatement p = connection().prepareStatement(sql)) {
            p.setObject(1, id == null ? email : id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return null;
                return new Account(
                        new User(
                                r.getInt("user_id"),
                                r.getString("name"),
                                r.getString("email"),
                                UserRole.valueOf(r.getString("role"))),
                        r.getString("password_hash"),
                        r.getString("state"),
                        r.getBoolean("must_change"),
                        instant(r, "temporary_expires_at"),
                        r.getInt("failed_attempts"),
                        instant(r, "failure_window"),
                        instant(r, "locked_until"),
                        r.getLong("auth_version"));
            }
        }
    }

    private static Instant instant(ResultSet r, String name) throws SQLException {
        Timestamp t = r.getTimestamp(name);
        return t == null ? null : t.toInstant();
    }

    public record Account(
            User user,
            String hash,
            String state,
            boolean mustChange,
            Instant expiry,
            int failures,
            Instant window,
            Instant locked,
            long version) {
        @Override
        public String toString() {
            return "Account[userId=" + user.getUserId() + ", state=" + state + "]";
        }
    }

    public static User validate(Session session, boolean allowTemporary) throws SQLException {
        if (session == null || !session.validAt(Instant.now()))
            throw new IllegalStateException("Session expired. Sign in again.");
        Account a = account(null, session.user().getUserId());
        if (a == null
                || a.version() != session.version()
                || "DISABLED".equals(a.state())
                || a.user().getRole() != session.user().getRole()
                || (a.mustChange() && (a.expiry() == null || !a.expiry().isAfter(Instant.now())))) {
            session.revoke();
            throw new IllegalStateException("Session expired. Sign in again.");
        }
        if (!allowTemporary && (!"ACTIVE".equals(a.state()) || a.mustChange()))
            throw new IllegalStateException("Choose your password before using the workspace");
        session.touch(Instant.now());
        return a.user();
    }

    public static void requireRole(User user, UserRole... roles) {
        if (Arrays.stream(roles).noneMatch(x -> x == user.getRole()))
            throw new IllegalStateException("You do not have permission for this action");
    }
}
