package com.aceos.database;

import static com.aceos.security.SecurityRepository.*;

import com.aceos.security.*;

import java.sql.*;
import java.util.Arrays;

/** Console-only operator bootstrap/recovery. Runtime account has no operator_setup privilege. */
public final class Bootstrap {
    private Bootstrap() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1)
            throw new IllegalArgumentException(
                    "Usage: Bootstrap <administrator ID> [recover], or Bootstrap create <name>"
                        + " <email>");
        String url = System.getenv("ACEOS_MIGRATION_URL");
        Migrations.requireIsolated(url);
        var console = System.console();
        if (console == null)
            throw new IllegalStateException("Run this command in an interactive local console");
        char[] chars = console.readPassword("Private initial password (15–128 characters): ");
        try (Connection c =
                DriverManager.getConnection(
                        url,
                        System.getenv("ACEOS_MIGRATION_USER"),
                        System.getenv("ACEOS_MIGRATION_PASSWORD"))) {
            if (args[0].equals("create")) {
                if (args.length != 3) throw new IllegalArgumentException("Provide name and email");
                create(c, args[1], args[2], new String(chars));
            } else
                activate(
                        c,
                        Integer.parseInt(args[0]),
                        new String(chars),
                        args.length == 2 && args[1].equals("recover"));
        } finally {
            Arrays.fill(chars, '\0');
        }
        System.out.println(
                "Administrator activated. Sign in locally. No password was printed or stored in"
                    + " source.");
    }

    public static int create(Connection connection, String name, String email, String password)
            throws SQLException {
        Migrations.requireIsolated(connection.getMetaData().getURL());
        Passwords.validate(password);
        if (name == null
                || name.isBlank()
                || name.trim().length() > 100
                || email == null
                || !email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || email.trim().length() > 255)
            throw new IllegalArgumentException("Provide a valid name and email");
        try (Transactions tx = new Transactions(connection)) {
            lockGate();
            try (Statement s = connection.createStatement()) {
                s.executeQuery("SELECT setup_id FROM operator_setup WHERE setup_id=1 FOR UPDATE")
                        .close();
            }
            if (count("SELECT COUNT(*) FROM users WHERE role='ADMINISTRATOR'") > 0)
                throw new IllegalStateException(
                        "Link an existing administrator instead of creating a duplicate");
            int id;
            try (PreparedStatement p =
                    connection.prepareStatement(
                            "INSERT INTO users(name,email,role) VALUES (?,?,'ADMINISTRATOR')",
                            Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, name.trim());
                p.setString(2, email.trim());
                p.executeUpdate();
                try (ResultSet r = p.getGeneratedKeys()) {
                    r.next();
                    id = r.getInt(1);
                }
            }
            update(
                    "INSERT INTO user_accounts(user_id,login_email,password_hash,state,must_change)"
                        + " VALUES (?,?,?,'ACTIVE',FALSE)",
                    id,
                    email.trim().toLowerCase(java.util.Locale.ROOT),
                    Passwords.hash(password));
            audit(null, "OPERATOR_BOOTSTRAP", id);
            tx.commit();
            return id;
        }
    }

    public static void activate(Connection connection, int id, String password, boolean recovery)
            throws SQLException {
        Migrations.requireIsolated(connection.getMetaData().getURL());
        Passwords.validate(password);
        try (Transactions tx = new Transactions(connection)) {
            lockGate();
            try (Statement s = connection.createStatement();
                    ResultSet r =
                            s.executeQuery(
                                    "SELECT setup_id FROM operator_setup WHERE setup_id=1 FOR"
                                        + " UPDATE")) {
                if (!r.next()) throw new SQLException("Operator setup unavailable");
            }
            var a = account(null, id);
            if (a == null || a.user().getRole() != com.aceos.model.UserRole.ADMINISTRATOR)
                throw new IllegalArgumentException("Choose an existing administrator ID");
            if (!recovery
                    && count(
                                    "SELECT COUNT(*) FROM users u JOIN user_accounts a ON"
                                        + " a.user_id=u.user_id WHERE u.role='ADMINISTRATOR' AND"
                                        + " a.state='ACTIVE' AND a.must_change=FALSE")
                            > 0)
                throw new IllegalStateException(
                        "An active administrator already exists; bootstrap is disabled");
            update(
                    "UPDATE user_accounts SET"
                        + " password_hash=?,state='ACTIVE',must_change=FALSE,temporary_expires_at=NULL,auth_version=auth_version+1,failed_attempts=0,locked_until=NULL,failure_window=NULL"
                        + " WHERE user_id=?",
                    Passwords.hash(password),
                    id);
            audit(null, recovery ? "OPERATOR_RECOVERY" : "OPERATOR_BOOTSTRAP", id);
            tx.commit();
        }
    }
}
