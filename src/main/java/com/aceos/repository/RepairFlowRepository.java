package com.aceos.repository;

import com.aceos.model.*;
import com.aceos.security.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Called inside the service authorization transaction; scopes precede reconstruction. */
public final class RepairFlowRepository {
    public List<User> activeTechnicians() throws SQLException {
        var result = new ArrayList<User>();
        try (var p = SecurityRepository.connection().prepareStatement("""
                SELECT u.* FROM users u JOIN user_accounts a ON a.user_id=u.user_id
                WHERE u.role='TECHNICIAN' AND a.state='ACTIVE' AND a.must_change=FALSE
                ORDER BY u.name,u.user_id
                """); var r = p.executeQuery()) {
            while (r.next()) result.add(new User(r.getInt("user_id"), r.getString("name"),
                    r.getString("email"), UserRole.TECHNICIAN));
        }
        return result;
    }

    public RepairDetail detail(Complaint complaint) throws SQLException {
        WorkOrder work = null;
        String sql = Scope.filter("SELECT w.work_order_id FROM work_orders w JOIN complaints c ON"
                + " c.complaint_id=w.complaint_id WHERE c.complaint_id=?", "work_orders", "w");
        try (var p = SecurityRepository.connection().prepareStatement(sql)) {
            p.setInt(1, complaint.getComplaintId());
            try (var r = p.executeQuery()) {
                if (r.next()) work = new WorkOrderRepository().findById(r.getInt(1)).orElseThrow();
            }
        }
        var events = new ArrayList<RepairDetail.Event>();
        events.add(new RepairDetail.Event("Complaint reported", complaint.getReportedAt()));
        // The parent complaint has passed SQL ownership checks. Expose only complaint transitions.
        try (var p = SecurityRepository.connection().prepareStatement("""
                SELECT action,recorded_at FROM action_audit WHERE target_id=?
                AND action IN ('acceptComplaint','rejectComplaint','COMPLAINT_WITHDRAWN','COMPLAINT_REOPENED') ORDER BY recorded_at,audit_id
                """)) {
            p.setInt(1, complaint.getComplaintId());
            try (var r = p.executeQuery()) {
                while (r.next()) events.add(new RepairDetail.Event(
                        switch(r.getString(1)) {
                            case "acceptComplaint" -> "Complaint accepted";
                            case "rejectComplaint" -> "Complaint rejected";
                            case "COMPLAINT_WITHDRAWN" -> "Complaint withdrawn";
                            default -> "Complaint reopened · awaiting review";
                        },
                        r.getTimestamp(2).toLocalDateTime()));
            }
        }
        String action = null;
        List<RepairSubmission> submissions=List.of();
        if (work != null) {
            events.add(new RepairDetail.Event("Repair assigned", work.getAssignedAt()));
            try (var p = SecurityRepository.connection().prepareStatement("""
                    SELECT recorded_at FROM action_audit WHERE target_id=? AND action='WORK_REASSIGNED'
                    ORDER BY recorded_at,audit_id
                    """)) {
                p.setInt(1, work.getWorkOrderId());
                try (var r = p.executeQuery()) {
                    while (r.next()) events.add(new RepairDetail.Event("Repair reassigned", r.getTimestamp(1).toLocalDateTime()));
                }
            }
            if (work.getStartedAt() != null) events.add(new RepairDetail.Event("Repair started", work.getStartedAt()));
            if (work.getCompletedAt() != null) events.add(new RepairDetail.Event("Repair completed · awaiting verification", work.getCompletedAt()));
            if (work.getVerifiedAt() != null) events.add(new RepairDetail.Event("Repair verified · complaint resolved", work.getVerifiedAt()));
            submissions=new RepairReviewRepository().submissions(work);
            for(var submission:submissions) {
                if(submission.recordId()==work.getCurrentSubmissionId())action=submission.actionTaken();
                if(submission.number()>1)events.add(new RepairDetail.Event("Correction submitted · submission "+submission.number(),submission.at()));
                if("RETURN".equals(submission.reviewKind()))events.add(new RepairDetail.Event(
                        "Repair returned for correction · submission "+submission.number(),submission.reviewedAt()));
            }
        }
        events.sort(java.util.Comparator.comparing(RepairDetail.Event::at));
        return new RepairDetail(complaint, work, action, events, new ComplaintNoteRepository().list(complaint.getComplaintId()),submissions);
    }
}
