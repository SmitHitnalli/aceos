package com.aceos.database;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.HexFormat;

/** Explicit operator tool; never migrates the app's configured database automatically. */
public final class Migrations {
    private Migrations() {}

    public static void requireIsolated(String url) {
        if (url == null
                || !url.matches(
                        "jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/aceos_next_(dev|test)(?:\\?.*)?"))
            throw new IllegalArgumentException(
                    "Use an explicit local aceos_next_dev or aceos_next_test URL");
    }

    public static void main(String[] args) throws Exception {
        String url = System.getenv("ACEOS_MIGRATION_URL");
        requireIsolated(url);
        try (Connection c =
                DriverManager.getConnection(
                        url,
                        System.getenv("ACEOS_MIGRATION_USER"),
                        System.getenv("ACEOS_MIGRATION_PASSWORD"))) {
            migrate(c);
            System.out.println("Foundation schema verified; existing operational data preserved.");
        }
    }

    public static void migrate(Connection c) throws Exception {
        requireIsolated(c.getMetaData().getURL());
        try (Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("SELECT GET_LOCK('aceos_foundation_migration',10)")) {
                if (!r.next() || r.getInt(1) != 1)
                    throw new SQLException("Another migration is running");
            }
            try {
                s.execute(
                        "CREATE TABLE IF NOT EXISTS schema_migrations (version INT PRIMARY KEY,"
                            + " checksum CHAR(64) NOT NULL, status VARCHAR(20) NOT NULL)");
                preflight(c);
                byte[] bytes;
                try (var in =
                        Migrations.class.getResourceAsStream("/database/V001__accounts.sql")) {
                    if (in == null) throw new SQLException("Missing migration resource");
                    bytes = in.readAllBytes();
                }
                String checksum =
                        HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                try (ResultSet r =
                        s.executeQuery(
                                "SELECT checksum,status FROM schema_migrations WHERE version=1")) {
                    if (r.next()) {
                        if (!checksum.equals(r.getString(1)) || !"COMPLETE".equals(r.getString(2)))
                            throw new SQLException(
                                    "Migration mismatch or partial migration: restore reviewed"
                                        + " backup before retrying");
                        assertCompatible(c);
                        return;
                    }
                }
                try (PreparedStatement p =
                        c.prepareStatement(
                                "INSERT INTO schema_migrations VALUES (1,?,'STARTED')")) {
                    p.setString(1, checksum);
                    p.executeUpdate();
                }
                for (String sql : new String(bytes, StandardCharsets.UTF_8).split(";"))
                    if (!sql.isBlank()) s.execute(sql);
                s.executeUpdate("UPDATE schema_migrations SET status='COMPLETE' WHERE version=1");
                assertCompatible(c);
            } finally {
                s.execute("SELECT RELEASE_LOCK('aceos_foundation_migration')");
            }
        }
    }

    private static void preflight(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
                ResultSet r =
                        s.executeQuery(
                                "SELECT LOWER(TRIM(email)) FROM users GROUP BY LOWER(TRIM(email))"
                                    + " HAVING COUNT(*)>1")) {
            if (r.next())
                throw new SQLException(
                        "Normalized email collision: resolve copied data explicitly");
        }
        try (Statement s = c.createStatement();
                ResultSet r =
                        s.executeQuery(
                                "SELECT COUNT(*) FROM users WHERE role NOT IN"
                                    + " ('REQUESTER','TECHNICIAN','ADMINISTRATOR') OR"
                                    + " TRIM(email)=''")) {
            r.next();
            if (r.getInt(1) != 0) throw new SQLException("Invalid user data in migration source");
        }
    }

    public static void assertCompatible(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
                ResultSet r =
                        s.executeQuery(
                                "SELECT version,status,checksum FROM schema_migrations ORDER BY"
                                    + " version")) {
            if (!r.next() || r.getInt(1) != 1 || !"COMPLETE".equals(r.getString(2)))
                throw new SQLException("Run the reviewed foundation migration before signing in");
            try (var in = Migrations.class.getResourceAsStream("/database/V001__accounts.sql")) {
                String checksum =
                        HexFormat.of()
                                .formatHex(
                                        MessageDigest.getInstance("SHA-256")
                                                .digest(in.readAllBytes()));
                if (!checksum.equals(r.getString(3)) || r.next())
                    throw new SQLException("Incompatible schema migration version/checksum");
            } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) {
                throw new SQLException("Migration verification failed", e);
            }
        }
        try (Statement s = c.createStatement()) {
            s.executeQuery("SELECT auth_version FROM user_accounts LIMIT 0").close();
            s.executeQuery("SELECT assigned_to_user_id FROM asset_inspections LIMIT 0").close();
        }
    }
}
