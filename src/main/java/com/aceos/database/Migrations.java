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
                for (int version = 1; version <= 5; version++) apply(c, version);
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

    private static byte[] resource(int version) throws SQLException {
        String name = switch(version) { case 1 -> "V001__accounts.sql"; case 2 -> "V002__availability.sql"; case 3 -> "V003__complaint_notes.sql"; case 4 -> "V004__complaint_reopening.sql"; case 5 -> "V005__repair_corrections.sql"; default -> throw new SQLException("Unsupported migration version"); };
        try (var in = Migrations.class.getResourceAsStream("/database/" + name)) {
            if (in == null) throw new SQLException("Missing migration resource");
            return in.readAllBytes();
        } catch (java.io.IOException e) { throw new SQLException("Migration resource failed", e); }
    }

    private static void apply(Connection c, int version) throws SQLException {
        byte[] bytes = resource(version);
        String expected = checksum(bytes);
        try (var p = c.prepareStatement("SELECT checksum,status FROM schema_migrations WHERE version=?")) {
            p.setInt(1, version);
            try (var r = p.executeQuery()) {
                if (r.next()) {
                    if (!expected.equals(r.getString(1)) || !"COMPLETE".equals(r.getString(2)))
                        throw new SQLException("Migration " + version + " mismatch or partial migration: restore reviewed backup before retrying");
                    return;
                }
            }
        }
        if (version == 2) availabilityPreflight(c);
        if (version == 5) correctionPreflight(c);
        try (var p = c.prepareStatement("INSERT INTO schema_migrations VALUES (?,?,'STARTED')")) {
            p.setInt(1, version); p.setString(2, expected); p.executeUpdate();
        }
        try (var s = c.createStatement()) {
            for (String sql : new String(bytes, StandardCharsets.UTF_8).split(";"))
                if (!sql.isBlank()) s.execute(sql);
            s.executeUpdate("UPDATE schema_migrations SET status='COMPLETE' WHERE version=" + version);
        }
    }

    private static void rejectRows(Connection c, String sql, String message) throws SQLException {
        try (var s = c.createStatement(); var r = s.executeQuery(sql)) {
            var ids = new java.util.ArrayList<Integer>();
            while (r.next() && ids.size() < 20) ids.add(r.getInt(1));
            if (!ids.isEmpty()) throw new SQLException(message + " (IDs, up to 20): " + ids + "; reviewed mapping required before F2");
        }
    }

    public static void availabilityPreflight(Connection c) throws SQLException {
        rejectRows(c, "SELECT inspection_id FROM asset_inspections WHERE status='FAILED'",
                "Historical failed inspections have ambiguous hold/clearance history");
        rejectRows(c, "SELECT work_order_id FROM work_orders WHERE"
                + " (status='ASSIGNED' AND (started_at IS NOT NULL OR completed_at IS NOT NULL OR verified_at IS NOT NULL))"
                + " OR (status='IN_PROGRESS' AND (started_at IS NULL OR completed_at IS NOT NULL OR verified_at IS NOT NULL))"
                + " OR (status='COMPLETED' AND (started_at IS NULL OR completed_at IS NULL OR verified_at IS NOT NULL))"
                + " OR (status='VERIFIED' AND (started_at IS NULL OR completed_at IS NULL OR verified_at IS NULL))",
                "Work-order lifecycle timestamps inconsistent");
        rejectRows(c, "SELECT a.asset_id FROM assets a WHERE"
                + " (a.status='UNDER_MAINTENANCE' AND NOT EXISTS (SELECT 1 FROM work_orders w JOIN complaints x ON x.complaint_id=w.complaint_id WHERE x.asset_id=a.asset_id AND w.started_at IS NOT NULL AND w.verified_at IS NULL))"
                + " OR (a.status='ACTIVE' AND EXISTS (SELECT 1 FROM work_orders w JOIN complaints x ON x.complaint_id=w.complaint_id WHERE x.asset_id=a.asset_id AND w.started_at IS NOT NULL AND w.verified_at IS NULL))"
                + " OR (a.status='RETIRED' AND (EXISTS (SELECT 1 FROM work_orders w JOIN complaints x ON x.complaint_id=w.complaint_id WHERE x.asset_id=a.asset_id AND w.status<>'VERIFIED')"
                + " OR EXISTS (SELECT 1 FROM asset_inspections i WHERE i.asset_id=a.asset_id AND i.status='SCHEDULED')"
                + " OR EXISTS (SELECT 1 FROM preventive_maintenance_schedules p WHERE p.asset_id=a.asset_id AND p.status='ACTIVE')))",
                "Asset availability inconsistent with operational records");
    }

    public static void assertCompatible(Connection c) throws SQLException {
        try (var s = c.createStatement(); var r = s.executeQuery(
                "SELECT version,status,checksum FROM schema_migrations ORDER BY version")) {
            for (int version = 1; version <= 5; version++) {
                if (!r.next() || r.getInt(1) != version || !"COMPLETE".equals(r.getString(2))
                        || !checksum(resource(version)).equals(r.getString(3)))
                    throw new SQLException("Run reviewed migrations; incompatible version/checksum");
            }
            if (r.next()) throw new SQLException("Unsupported migration version");
        }
        try (var s = c.createStatement()) {
            s.executeQuery("SELECT auth_version FROM user_accounts LIMIT 0").close();
            s.executeQuery("SELECT assigned_to_user_id,completed_at FROM asset_inspections LIMIT 0").close();
            s.executeQuery("SELECT manual_lifecycle FROM assets LIMIT 0").close();
            s.executeQuery("SELECT clearance_reason FROM asset_safety_holds LIMIT 0").close();
            s.executeQuery("SELECT reason FROM asset_lifecycle_history LIMIT 0").close();
            s.executeQuery("SELECT note_id,body,author_role,kind,recorded_at FROM complaint_notes LIMIT 0").close();
            s.executeQuery("SELECT current_submission_id,verified_submission_id FROM work_orders LIMIT 0").close();
            s.executeQuery("SELECT submission_number,submitted_by_user_id,author_name,author_role FROM maintenance_records LIMIT 0").close();
            s.executeQuery("SELECT review_id,maintenance_record_id,kind,actor_user_id,author_name,author_role,reason,recorded_at FROM repair_reviews LIMIT 0").close();
        }
        try(var p=c.prepareStatement("SELECT CONSTRAINT_NAME,CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME IN ('chk_complaints_status','chk_note_kind')");var r=p.executeQuery()) {
            boolean status=false,notes=false;
            while(r.next()) {
                String clause=r.getString(2).replace("\\", "");
                if(r.getString(1).equals("chk_complaints_status"))status=clause.contains("'WITHDRAWN'");
                else notes=clause.contains("'WITHDRAW_REASON'") && clause.contains("'REOPEN_REASON'");
            }
            if(!status || !notes)throw new SQLException("Incompatible withdrawal/reopening constraints; run reviewed migrations");
        }
        requireConstraint(c,"chk_work_orders_status","(status in ('ASSIGNED','IN_PROGRESS','COMPLETED','RETURNED','VERIFIED'))");
        requireConstraint(c,"chk_submission_number","(submission_number > 0)");
        requireConstraint(c,"chk_submission_author","(((submitted_by_user_id is null) and (author_name is null) and (author_role is null)) or ((submitted_by_user_id is not null) and (author_name is not null) and (author_role is not null) and (author_role = 'TECHNICIAN')))");
        requireConstraint(c,"chk_repair_review_kind","(kind in ('RETURN','VERIFY'))");
        requireConstraint(c,"chk_repair_review_role","(author_role = 'ADMINISTRATOR')");
        requireConstraint(c,"chk_repair_review_reason","(((kind = 'RETURN') and (reason is not null) and (char_length(trim(reason)) between 1 and 2000)) or ((kind = 'VERIFY') and (reason is null)))");
        requireReference(c,"maintenance_records","fk_submission_author","submitted_by_user_id","users","user_id");
        requireReference(c,"repair_reviews","fk_repair_review_record","maintenance_record_id","maintenance_records","maintenance_record_id");
        requireReference(c,"repair_reviews","fk_repair_review_actor","actor_user_id","users","user_id");
        requireUnique(c,"maintenance_records","uq_submission_number","work_order_id,submission_number");
        requireUnique(c,"maintenance_records","uq_submission_work_record","work_order_id,maintenance_record_id");
        requireUnique(c,"repair_reviews","maintenance_record_id","maintenance_record_id");
        try(var p=c.prepareStatement("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='maintenance_records' AND INDEX_NAME='uq_maintenance_records_work_order'");var r=p.executeQuery()) {
            r.next();if(r.getInt(1)!=0)throw new SQLException("Obsolete single-submission constraint remains");
        }
        for(String name:new String[]{"fk_work_current_submission","fk_work_verified_submission"}) {
            String column=name.equals("fk_work_current_submission")?"current_submission_id":"verified_submission_id";
            try(var p=c.prepareStatement("SELECT COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='work_orders' AND CONSTRAINT_NAME=? ORDER BY ORDINAL_POSITION")) {
                p.setString(1,name);try(var r=p.executeQuery()) {
                    if(!r.next() || !"work_order_id".equals(r.getString(1)) || !"maintenance_records".equals(r.getString(2)) || !"work_order_id".equals(r.getString(3))
                            || !r.next() || !column.equals(r.getString(1)) || !"maintenance_records".equals(r.getString(2)) || !"maintenance_record_id".equals(r.getString(3)) || r.next())
                        throw new SQLException("Incompatible repair submission linkage");
                }
            }
        }
    }

    public static void correctionPreflight(Connection c) throws SQLException {
        rejectRows(c,"SELECT w.work_order_id FROM work_orders w JOIN complaints c ON c.complaint_id=w.complaint_id LEFT JOIN maintenance_records m ON m.work_order_id=w.work_order_id WHERE "
                + "w.status NOT IN ('ASSIGNED','IN_PROGRESS','COMPLETED','VERIFIED') "
                + "OR (w.status IN ('ASSIGNED','IN_PROGRESS') AND m.maintenance_record_id IS NOT NULL) "
                + "OR (w.status IN ('COMPLETED','VERIFIED') AND m.maintenance_record_id IS NULL) "
                + "OR (w.status='ASSIGNED' AND (w.started_at IS NOT NULL OR w.completed_at IS NOT NULL OR w.verified_at IS NOT NULL)) "
                + "OR (w.status='IN_PROGRESS' AND (w.started_at IS NULL OR w.completed_at IS NOT NULL OR w.verified_at IS NOT NULL)) "
                + "OR (w.status='COMPLETED' AND (w.started_at IS NULL OR w.completed_at IS NULL OR w.verified_at IS NOT NULL)) "
                + "OR (w.status='VERIFIED' AND (w.started_at IS NULL OR w.completed_at IS NULL OR w.verified_at IS NULL)) "
                + "OR (w.status<>'VERIFIED' AND c.status<>'WORK_ORDER_CREATED') OR (w.status='VERIFIED' AND c.status<>'RESOLVED')",
                "Repair correction evidence inconsistent");
    }
    private static String normalizeConstraint(String clause) {
        return clause.replace("\\", "").replace("`", "").replaceAll("(?i)_utf8mb4(?=')", "")
                .replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }
    private static void requireConstraint(Connection c,String name,String expected) throws SQLException {
        try(var p=c.prepareStatement("SELECT CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME=?")) {
            p.setString(1,name);try(var r=p.executeQuery()) {
                if(!r.next())throw new SQLException("Missing repair constraint: "+name);
                if(!normalizeConstraint(r.getString(1)).equals(normalizeConstraint(expected)))
                    throw new SQLException("Incompatible repair constraint: "+name);
            }
        }
    }
    private static void requireReference(Connection c,String table,String name,String column,String referencedTable,String referencedColumn) throws SQLException {
        try(var p=c.prepareStatement("SELECT COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME=?")) {
            p.setString(1,table);p.setString(2,name);try(var r=p.executeQuery()) {
                if(!r.next() || !column.equals(r.getString(1)) || !referencedTable.equals(r.getString(2)) || !referencedColumn.equals(r.getString(3)) || r.next())
                    throw new SQLException("Incompatible repair history reference: "+name);
            }
        }
    }
    private static void requireUnique(Connection c,String table,String index,String columns) throws SQLException {
        try(var p=c.prepareStatement("SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=? AND NON_UNIQUE=0")) {
            p.setString(1,table);p.setString(2,index);try(var r=p.executeQuery()) {
                if(!r.next() || !columns.equals(r.getString(1)))throw new SQLException("Incompatible repair index: "+index);
            }
        }
    }
    /** Git checkouts may use CRLF on Windows; line endings must not change migration identity. */
    public static String checksum(byte[] bytes) throws SQLException {
        byte[] canonical = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n").getBytes(StandardCharsets.UTF_8);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical)); }
        catch(java.security.NoSuchAlgorithmException e) { throw new SQLException("SHA-256 unavailable", e); }
    }
}
