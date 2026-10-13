package com.aceos.repository;

import com.aceos.model.*;
import com.aceos.security.SecurityRepository;
import com.aceos.security.Scope;
import java.sql.*;
import java.util.*;

/** Called inside the authorized service transaction, before any asset-linked write. */
public final class AvailabilityRepository {
    private AvailabilityRepository() {}

    public static ManualLifecycle lock(int assetId) throws SQLException {
        try (var p = SecurityRepository.connection().prepareStatement(
                "SELECT manual_lifecycle FROM assets WHERE asset_id=? FOR UPDATE")) {
            p.setInt(1, assetId);
            try (var r = p.executeQuery()) {
                if (!r.next()) throw new IllegalStateException("Asset no longer available");
                return ManualLifecycle.valueOf(r.getString(1));
            }
        }
    }

    public static void requireOperational(int assetId) throws SQLException {
        if (lock(assetId) == ManualLifecycle.RETIRED)
            throw new IllegalStateException("A retired asset cannot receive new operations");
    }

    public static AssetStatus recalculate(int assetId) throws SQLException {
        ManualLifecycle lifecycle = lock(assetId);
        AssetStatus status;
        if (lifecycle == ManualLifecycle.RETIRED) status = AssetStatus.RETIRED;
        else if (lifecycle == ManualLifecycle.OUT_OF_SERVICE || SecurityRepository.count(
                "SELECT COUNT(*) FROM asset_safety_holds WHERE asset_id=? AND cleared_at IS NULL", assetId) > 0)
            status = AssetStatus.OUT_OF_SERVICE;
        else if (SecurityRepository.count(
                "SELECT COUNT(*) FROM work_orders w JOIN complaints c ON c.complaint_id=w.complaint_id"
                        + " WHERE c.asset_id=? AND w.started_at IS NOT NULL AND w.verified_at IS NULL", assetId) > 0)
            status = AssetStatus.UNDER_MAINTENANCE;
        else status = AssetStatus.ACTIVE;
        SecurityRepository.update("UPDATE assets SET status=? WHERE asset_id=?", status.name(), assetId);
        return status;
    }

    public static void changeLifecycle(int assetId, ManualLifecycle expected, ManualLifecycle next, String reason)
            throws SQLException {
        SecurityRepository.requireRole(Scope.user(), UserRole.ADMINISTRATOR);
        if (lock(assetId) != expected) throw new IllegalStateException("Lifecycle changed; refresh the asset first");
        if (expected == ManualLifecycle.RETIRED && next != expected)
            throw new IllegalStateException("Retired assets cannot be reopened in this milestone");
        if (next == ManualLifecycle.RETIRED && (SecurityRepository.count(
                "SELECT COUNT(*) FROM work_orders w JOIN complaints c ON c.complaint_id=w.complaint_id"
                        + " WHERE c.asset_id=? AND w.status<>'VERIFIED'", assetId) > 0
                || SecurityRepository.count("SELECT COUNT(*) FROM asset_inspections WHERE asset_id=? AND status='SCHEDULED'", assetId) > 0
                || SecurityRepository.count("SELECT COUNT(*) FROM preventive_maintenance_schedules WHERE asset_id=? AND status='ACTIVE'", assetId) > 0))
            throw new IllegalStateException("Finish jobs and inspections and pause preventive schedules before retirement");
        SecurityRepository.update("UPDATE assets SET manual_lifecycle=? WHERE asset_id=?", next.name(), assetId);
        // Reason is stored separately from the short audit event, without erasing prior lifecycle history.
        SecurityRepository.update("INSERT INTO asset_lifecycle_history(asset_id,actor_user_id,previous_lifecycle,next_lifecycle,reason) VALUES (?,?,?,?,?)",
                assetId, Scope.user().getUserId(), expected.name(), next.name(), reason);
        recalculate(assetId);
    }

    public static void createFailureHold(int assetId, int inspectionId) throws SQLException {
        lock(assetId);
        SecurityRepository.update("INSERT INTO asset_safety_holds(asset_id,failed_inspection_id,created_by_user_id) VALUES (?,?,?)",
                assetId, inspectionId, Scope.user().getUserId());
        recalculate(assetId);
    }

    public static List<SafetyHold> holds(int assetId) throws SQLException {
        SecurityRepository.requireRole(Scope.user(), UserRole.ADMINISTRATOR);
        var result = new ArrayList<SafetyHold>();
        try (var p = SecurityRepository.connection().prepareStatement(
                "SELECT * FROM asset_safety_holds WHERE asset_id=? ORDER BY hold_id")) {
            p.setInt(1, assetId);
            try (var r = p.executeQuery()) {
                while (r.next()) result.add(new SafetyHold(r.getInt("hold_id"), assetId,
                        r.getInt("failed_inspection_id"), r.getTimestamp("created_at").toLocalDateTime(),
                        r.getObject("cleared_by_user_id", Integer.class), r.getString("clearance_reason"),
                        r.getObject("passing_inspection_id", Integer.class), r.getObject("verified_work_order_id", Integer.class),
                        r.getTimestamp("cleared_at") == null ? null : r.getTimestamp("cleared_at").toLocalDateTime()));
            }
        }
        return result;
    }

    public static void clearHold(int assetId, int holdId, String reason, Integer inspectionId, Integer workOrderId)
            throws SQLException {
        SecurityRepository.requireRole(Scope.user(), UserRole.ADMINISTRATOR);
        lock(assetId);
        if ((inspectionId == null) == (workOrderId == null))
            throw new IllegalArgumentException("Choose exactly one passing reinspection or verified corrective repair");
        String evidence = inspectionId != null
                ? "EXISTS (SELECT 1 FROM asset_inspections i JOIN asset_inspections f ON f.inspection_id=h.failed_inspection_id WHERE i.inspection_id=? AND i.asset_id=h.asset_id AND i.status='PASSED' AND i.completed_at>h.created_at AND i.completed_date>=f.completed_date)"
                : "EXISTS (SELECT 1 FROM work_orders w JOIN complaints c ON c.complaint_id=w.complaint_id WHERE w.work_order_id=? AND c.asset_id=h.asset_id AND w.status='VERIFIED' AND w.verified_at>h.created_at)";
        if (SecurityRepository.count("SELECT COUNT(*) FROM asset_safety_holds h WHERE h.hold_id=? AND h.asset_id=? AND h.cleared_at IS NULL AND " + evidence,
                holdId, assetId, inspectionId != null ? inspectionId : workOrderId) != 1)
            throw new IllegalStateException("Active hold requires later passing reinspection or verified corrective repair on the same asset");
        if (SecurityRepository.update("UPDATE asset_safety_holds SET cleared_by_user_id=?,clearance_reason=?,passing_inspection_id=?,verified_work_order_id=?,cleared_at=CURRENT_TIMESTAMP(6) WHERE hold_id=? AND asset_id=? AND cleared_at IS NULL",
                Scope.user().getUserId(), reason, inspectionId, workOrderId, holdId, assetId) != 1)
            throw new IllegalStateException("Hold changed; refresh first");
        SecurityRepository.audit(Scope.user().getUserId(), "SAFETY_HOLD_CLEARED", holdId);
        recalculate(assetId);
    }

    public static List<AssetProfile.LifecycleEvent> lifecycleHistory(int assetId) throws SQLException {
        SecurityRepository.requireRole(Scope.user(), UserRole.ADMINISTRATOR);
        var rows = new ArrayList<AssetProfile.LifecycleEvent>();
        try (var p = SecurityRepository.connection().prepareStatement(
                "SELECT * FROM asset_lifecycle_history WHERE asset_id=? ORDER BY changed_at DESC,history_id DESC")) {
            p.setInt(1, assetId);
            try (var r = p.executeQuery()) {
                while (r.next()) rows.add(new AssetProfile.LifecycleEvent(
                        ManualLifecycle.valueOf(r.getString("previous_lifecycle")),
                        ManualLifecycle.valueOf(r.getString("next_lifecycle")), r.getString("reason"),
                        r.getInt("actor_user_id"), r.getTimestamp("changed_at").toLocalDateTime()));
            }
        }
        return rows;
    }

    /** Mirrors clearance eligibility; writes still revalidate evidence and the active hold. */
    public static List<AssetProfile.ClearanceEvidence> clearanceEvidence(int assetId) throws SQLException {
        SecurityRepository.requireRole(Scope.user(), UserRole.ADMINISTRATOR);
        var rows = new ArrayList<AssetProfile.ClearanceEvidence>();
        String sql = """
                SELECT h.hold_id,i.inspection_id AS id,1 AS inspection,i.inspection_type AS description,i.completed_at AS at
                FROM asset_safety_holds h JOIN asset_inspections f ON f.inspection_id=h.failed_inspection_id
                JOIN asset_inspections i ON i.asset_id=h.asset_id
                WHERE h.asset_id=? AND h.cleared_at IS NULL AND i.status='PASSED'
                AND i.completed_at>h.created_at AND i.completed_date>=f.completed_date
                UNION ALL
                SELECT h.hold_id,w.work_order_id,0,m.action_taken,w.verified_at
                FROM asset_safety_holds h JOIN complaints c ON c.asset_id=h.asset_id
                JOIN work_orders w ON w.complaint_id=c.complaint_id
                JOIN maintenance_records m ON m.work_order_id=w.work_order_id AND m.maintenance_record_id=w.verified_submission_id
                WHERE h.asset_id=? AND h.cleared_at IS NULL AND w.status='VERIFIED' AND w.verified_at>h.created_at
                ORDER BY hold_id,at DESC,id DESC
                """;
        try (var p = SecurityRepository.connection().prepareStatement(sql)) {
            p.setInt(1, assetId); p.setInt(2, assetId);
            try (var r = p.executeQuery()) {
                while (r.next()) rows.add(new AssetProfile.ClearanceEvidence(r.getInt("hold_id"),
                        r.getInt("id"), r.getBoolean("inspection"), r.getString("description"),
                        r.getTimestamp("at").toLocalDateTime()));
            }
        }
        return rows;
    }
}
