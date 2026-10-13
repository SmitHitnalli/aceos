package com.aceos.repository;

import com.aceos.model.DashboardSummary;
import com.aceos.model.UserRole;
import com.aceos.security.Scope;
import com.aceos.security.SecurityRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public final class DashboardSummaryRepository {
    public DashboardSummary load() throws SQLException {
        var role = Scope.user().getRole();
        int actor = Scope.user().getUserId();
        var counts = new LinkedHashMap<String, Integer>();
        var items = new ArrayList<DashboardSummary.Item>();
        if (role == UserRole.REQUESTER) {
            count(counts, "open", "SELECT COUNT(*) FROM complaints WHERE reported_by_user_id=? AND status NOT IN ('RESOLVED','REJECTED','WITHDRAWN')", actor);
            count(counts, "resolved", "SELECT COUNT(*) FROM complaints WHERE reported_by_user_id=? AND status='RESOLVED'", actor);
            rows(items, "Complaint", "SELECT c.complaint_id id,a.name asset,c.status,CONCAT(c.priority,' · ',c.description,COALESCE(CONCAT(' · Repair: ',w.status),'')) detail FROM complaints c JOIN assets a ON a.asset_id=c.asset_id LEFT JOIN work_orders w ON w.complaint_id=c.complaint_id WHERE c.reported_by_user_id=? ORDER BY c.reported_at DESC,c.complaint_id DESC LIMIT 6", actor);
        } else if (role == UserRole.TECHNICIAN) {
            count(counts, "assigned", "SELECT COUNT(*) FROM work_orders WHERE technician_user_id=? AND status='ASSIGNED'", actor);
            count(counts, "started", "SELECT COUNT(*) FROM work_orders WHERE technician_user_id=? AND status='IN_PROGRESS'", actor);
            count(counts, "completed", "SELECT COUNT(*) FROM work_orders WHERE technician_user_id=? AND status='COMPLETED'", actor);
            count(counts, "corrections", "SELECT COUNT(*) FROM work_orders WHERE technician_user_id=? AND status='RETURNED'", actor);
            count(counts, "inspections", "SELECT COUNT(*) FROM asset_inspections WHERE assigned_to_user_id=? AND status='SCHEDULED'", actor);
            count(counts, "overdueInspections", "SELECT COUNT(*) FROM asset_inspections WHERE assigned_to_user_id=? AND status='SCHEDULED' AND scheduled_date<CURRENT_DATE", actor);
            rows(items, "Work order", "SELECT w.work_order_id id,a.name asset,w.status,CONCAT('Complaint #',c.complaint_id,' · ',c.priority) detail FROM work_orders w JOIN complaints c ON c.complaint_id=w.complaint_id JOIN assets a ON a.asset_id=c.asset_id WHERE w.technician_user_id=? AND w.status<>'VERIFIED' ORDER BY FIELD(w.status,'RETURNED','IN_PROGRESS','ASSIGNED','COMPLETED'),w.work_order_id LIMIT 6", actor);
            rows(items, "Inspection", "SELECT i.inspection_id id,a.name asset,i.status,CONCAT(i.inspection_type,' · Due ',i.scheduled_date) detail FROM asset_inspections i JOIN assets a ON a.asset_id=i.asset_id WHERE i.assigned_to_user_id=? AND i.status='SCHEDULED' ORDER BY i.scheduled_date,i.inspection_id LIMIT 6", actor);
        } else {
            count(counts, "review", "SELECT COUNT(*) FROM complaints WHERE status='SUBMITTED'");
            count(counts, "assignment", "SELECT COUNT(*) FROM complaints WHERE status='ACCEPTED'");
            count(counts, "verification", "SELECT COUNT(*) FROM work_orders WHERE status='COMPLETED'");
            count(counts, "unassignedInspections", "SELECT COUNT(*) FROM asset_inspections WHERE status='SCHEDULED' AND assigned_to_user_id IS NULL");
            count(counts, "overdueInspections", "SELECT COUNT(*) FROM asset_inspections WHERE status='SCHEDULED' AND scheduled_date<CURRENT_DATE");
            count(counts, "overdueSchedules", "SELECT COUNT(*) FROM preventive_maintenance_schedules WHERE status='ACTIVE' AND next_due_date<CURRENT_DATE");
            count(counts, "holds", "SELECT COUNT(*) FROM asset_safety_holds WHERE cleared_at IS NULL");
            rows(items, "Complaint", "SELECT c.complaint_id id,a.name asset,c.status,CONCAT(c.priority,' · ',c.description) detail FROM complaints c JOIN assets a ON a.asset_id=c.asset_id WHERE c.status='SUBMITTED' ORDER BY FIELD(c.priority,'CRITICAL','HIGH','MEDIUM','LOW'),c.reported_at,c.complaint_id LIMIT 6");
        }
        return new DashboardSummary(counts, items);
    }

    private void count(java.util.Map<String, Integer> counts, String key, String sql, Object... args) throws SQLException {
        counts.put(key, (int) SecurityRepository.count(sql, args));
    }

    private void rows(java.util.List<DashboardSummary.Item> items, String kind, String sql, Object... args) throws SQLException {
        try (var p = SecurityRepository.connection().prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            try (var r = p.executeQuery()) {
                while (r.next()) items.add(new DashboardSummary.Item(kind, r.getInt("id"), r.getString("asset"), r.getString("status"), r.getString("detail")));
            }
        }
    }
}
