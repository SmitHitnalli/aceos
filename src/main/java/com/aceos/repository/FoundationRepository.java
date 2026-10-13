package com.aceos.repository;

import com.aceos.model.*;
import com.aceos.security.*;

import java.sql.SQLException;
import java.time.Instant;

public final class FoundationRepository {
    public static final String INSERT_ACCOUNT =
            "INSERT INTO user_accounts(user_id,login_email) VALUES (?,?)";
    public static final String COUNT_ASSIGNED_WORK =
            "SELECT COUNT(*) FROM work_orders WHERE work_order_id=? AND technician_user_id=?";
    public static final String COUNT_ASSIGNED_INSPECTION =
            "SELECT COUNT(*) FROM asset_inspections WHERE inspection_id=? AND"
                + " assigned_to_user_id=?";
    public static final String COUNT_UNFINISHED_WORK =
            "SELECT COUNT(*) FROM work_orders WHERE technician_user_id=? AND status<>'VERIFIED'";
    public static final String COUNT_SCHEDULED_INSPECTIONS =
            "SELECT COUNT(*) FROM asset_inspections WHERE assigned_to_user_id=? AND"
                + " status='SCHEDULED'";
    public static final String COUNT_ACTIVE_ADMINISTRATORS =
            "SELECT COUNT(*) FROM users u JOIN user_accounts a ON a.user_id=u.user_id WHERE"
                + " u.role='ADMINISTRATOR' AND a.state='ACTIVE' AND a.must_change=FALSE";
    public static final String DISABLE_ACCOUNT =
            "UPDATE user_accounts SET"
                + " state='DISABLED',password_hash=NULL,temporary_expires_at=NULL,auth_version=auth_version+1"
                + " WHERE user_id=?";
    public static final String CHANGE_ROLE = "UPDATE users SET role=? WHERE user_id=?";
    public static final String INVALIDATE_SESSIONS =
            "UPDATE user_accounts SET auth_version=auth_version+1 WHERE user_id=?";
    public static final String UNLOCK_ACCOUNT =
            "UPDATE user_accounts SET failed_attempts=0,failure_window=NULL,locked_until=NULL WHERE"
                + " user_id=?";
    public static final String REASSIGN_UNSTARTED_WORK =
            "UPDATE work_orders SET technician_user_id=? WHERE work_order_id=? AND"
                + " status='ASSIGNED'";
    public static final String ASSIGN_SCHEDULED_INSPECTION =
            "UPDATE asset_inspections SET assigned_to_user_id=? WHERE inspection_id=? AND"
                + " status='SCHEDULED'";

    public DashboardStatistics statistics() throws SQLException {
        int id = Scope.user().getUserId();
        boolean requester = Scope.user().getRole() == UserRole.REQUESTER;
        int complaints =
                (int)
                        SecurityRepository.count(
                                "SELECT COUNT(*) FROM complaints c WHERE c.status NOT IN"
                                        + " ('REJECTED','RESOLVED','WITHDRAWN') AND "
                                        + (requester
                                                ? "c.reported_by_user_id=?"
                                                : "c.complaint_id IN (SELECT complaint_id FROM"
                                                      + " work_orders WHERE technician_user_id=?)"),
                                id);
        int jobs =
                (int)
                        SecurityRepository.count(
                                "SELECT COUNT(*) FROM work_orders w JOIN complaints c ON"
                                    + " c.complaint_id=w.complaint_id WHERE w.status<>'VERIFIED'"
                                    + " AND "
                                        + (requester
                                                ? "c.reported_by_user_id=?"
                                                : "w.technician_user_id=?"),
                                id);
        int failed =
                requester
                        ? 0
                        : (int)
                                SecurityRepository.count(
                                        "SELECT COUNT(*) FROM asset_inspections WHERE"
                                                + " assigned_to_user_id=? AND status='FAILED'",
                                        id);
        return new DashboardStatistics(0, 0, complaints, jobs, 0, failed);
    }

    public java.util.List<AccountSummary> accounts() throws SQLException {
        var rows = new java.util.ArrayList<AccountSummary>();
        try (var p =
                        SecurityRepository.connection()
                                .prepareStatement(
                                        "SELECT"
                                                + " u.*,a.state,a.must_change,a.locked_until"
                                                + " FROM users u JOIN user_accounts a ON"
                                                + " a.user_id=u.user_id ORDER BY u.name");
                var r = p.executeQuery()) {
            while (r.next())
                rows.add(
                        new AccountSummary(
                                r.getInt("user_id"),
                                r.getString("name"),
                                r.getString("email"),
                                UserRole.valueOf(r.getString("role")),
                                r.getString("state"),
                                r.getBoolean("must_change"),
                                r.getTimestamp("locked_until") != null
                                        && r.getTimestamp("locked_until")
                                                .toInstant()
                                                .isAfter(Instant.now())));
        }
        return rows;
    }

    public java.util.List<MaintenanceSummary> history(int assetId) throws SQLException {
        var rows = new java.util.ArrayList<MaintenanceSummary>();
        boolean requester = Scope.user().getRole() == UserRole.REQUESTER;
        String sql =
                "SELECT m.*,CASE WHEN r.kind='RETURN' THEN 'Returned' WHEN w.verified_submission_id=m.maintenance_record_id THEN 'Verified' WHEN w.current_submission_id=m.maintenance_record_id THEN 'Awaiting review' ELSE 'Superseded' END disposition FROM maintenance_records m JOIN work_orders w ON"
                        + " w.work_order_id=m.work_order_id JOIN complaints c ON"
                        + " c.complaint_id=w.complaint_id LEFT JOIN repair_reviews r ON r.maintenance_record_id=m.maintenance_record_id WHERE c.asset_id=?"
                        + (requester ? " AND c.reported_by_user_id=?" : "")
                        + " ORDER BY m.recorded_at DESC,m.maintenance_record_id DESC";
        try (var p = SecurityRepository.connection().prepareStatement(sql)) {
            p.setInt(1, assetId);
            if (requester) p.setInt(2, Scope.user().getUserId());
            try (var r = p.executeQuery()) {
                while (r.next())
                    rows.add(
                            new MaintenanceSummary(
                                    r.getInt("maintenance_record_id"),
                                    r.getInt("work_order_id"),
                                    r.getString("action_taken"),
                                    r.getTimestamp("recorded_at").toLocalDateTime(),r.getInt("submission_number"),r.getString("disposition")));
            }
        }
        return rows;
    }
}
