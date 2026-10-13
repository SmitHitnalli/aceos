package com.aceos.repository;

import com.aceos.model.*;
import com.aceos.security.*;
import java.sql.*;
import java.util.*;

/** Exact-submission correction writes inside the service authorization transaction. */
public final class RepairReviewRepository {
    public List<RepairSubmission> submissions(WorkOrder work) throws SQLException {
        var result = new ArrayList<RepairSubmission>();
        try (var p = SecurityRepository.connection().prepareStatement("""
                SELECT m.*,r.review_id,r.kind,r.actor_user_id,r.author_name reviewer_name,
                    r.author_role reviewer_role,r.reason,r.recorded_at reviewed_at
                FROM maintenance_records m LEFT JOIN repair_reviews r
                    ON r.maintenance_record_id=m.maintenance_record_id
                WHERE m.work_order_id=? ORDER BY m.submission_number
                """)) {
            p.setInt(1,work.getWorkOrderId());
            try (var r=p.executeQuery()) {
                while(r.next()) {
                    int id=r.getInt("maintenance_record_id");
                    String kind=r.getString("kind");
                    String disposition=kind!=null && kind.equals("RETURN") ? "Returned"
                            : id==work.getVerifiedSubmissionId() ? "Verified"
                            : id==work.getCurrentSubmissionId() ? "Awaiting review" : "Superseded";
                    var reviewed=r.getTimestamp("reviewed_at");
                    result.add(new RepairSubmission(id,r.getInt("submission_number"),
                            r.getString("action_taken"),r.getTimestamp("recorded_at").toLocalDateTime(),
                            (Integer)r.getObject("submitted_by_user_id"),r.getString("author_name"),
                            r.getString("author_role"),disposition,r.getInt("review_id"),kind,
                            (Integer)r.getObject("actor_user_id"),r.getString("reviewer_name"),
                            r.getString("reviewer_role"),r.getString("reason"),
                            reviewed==null?null:reviewed.toLocalDateTime()));
                }
            }
        }
        return List.copyOf(result);
    }

    private RepairSubmission current(WorkOrder w, int expected, WorkOrderStatus status) throws SQLException {
        if(w.getStatus()!=status || w.getVerifiedAt()!=null || w.getStartedAt()==null
                || w.getCompletedAt()==null || w.getComplaint().getStatus()!=ComplaintStatus.WORK_ORDER_CREATED
                || expected<=0 || w.getCurrentSubmissionId()!=expected)
            throw new IllegalStateException("Repair progress changed or completion evidence is missing; refresh before retrying");
        var records=submissions(w);
        if(records.isEmpty() || records.getLast().recordId()!=expected)
            throw new IllegalStateException("Current submission evidence is inconsistent; refresh before retrying");
        return records.getLast();
    }

    public void returnForCorrection(WorkOrder w,int expected,String reason) throws SQLException {
        SecurityRepository.requireRole(Scope.user(),UserRole.ADMINISTRATOR);
        var submission=current(w,expected,WorkOrderStatus.COMPLETED);
        if(submission.reviewId()!=0)throw new IllegalStateException("This submission was already reviewed");
        w.returnForCorrection(Scope.user());
        review(expected,"RETURN",reason);
        one(SecurityRepository.update("""
                UPDATE work_orders w JOIN complaints c ON c.complaint_id=w.complaint_id
                SET w.status='RETURNED' WHERE w.work_order_id=? AND w.status='COMPLETED'
                AND w.verified_at IS NULL AND w.current_submission_id=? AND c.status='WORK_ORDER_CREATED'
                """,w.getWorkOrderId(),expected));
        AvailabilityRepository.recalculate(w.getComplaint().getAsset().getAssetId());
    }

    public int resubmit(WorkOrder w,int expected,int returnId,String action) throws SQLException {
        SecurityRepository.requireRole(Scope.user(),UserRole.TECHNICIAN);
        var previous=current(w,expected,WorkOrderStatus.RETURNED);
        if(w.getTechnician().getUserId()!=Scope.user().getUserId() || returnId<=0
                || previous.reviewId()!=returnId || !"RETURN".equals(previous.reviewKind()))
            throw new IllegalStateException("The assigned repair or return changed; refresh before retrying");
        w.resubmitCorrection(Scope.user());
        int id;
        try(var p=SecurityRepository.connection().prepareStatement("""
                INSERT INTO maintenance_records(work_order_id,submission_number,action_taken,
                    submitted_by_user_id,author_name,author_role)
                SELECT w.work_order_id,?,?,?,?,'TECHNICIAN' FROM work_orders w
                JOIN complaints c ON c.complaint_id=w.complaint_id
                JOIN repair_reviews r ON r.maintenance_record_id=w.current_submission_id
                WHERE w.work_order_id=? AND w.status='RETURNED' AND w.current_submission_id=?
                AND w.verified_at IS NULL AND w.technician_user_id=? AND c.status='WORK_ORDER_CREATED'
                AND r.review_id=? AND r.kind='RETURN'
                """,Statement.RETURN_GENERATED_KEYS)) {
            SecurityRepository.bind(p,previous.number()+1,action,Scope.user().getUserId(),Scope.user().getName(),
                    w.getWorkOrderId(),expected,Scope.user().getUserId(),returnId);
            one(p.executeUpdate());
            try(var r=p.getGeneratedKeys()){if(!r.next())throw new SQLException("No submission ID returned");id=r.getInt(1);}
        }
        one(SecurityRepository.update("""
                UPDATE work_orders w JOIN complaints c ON c.complaint_id=w.complaint_id
                SET w.status='COMPLETED',w.current_submission_id=?
                WHERE w.work_order_id=? AND w.status='RETURNED' AND w.current_submission_id=?
                AND w.verified_at IS NULL AND w.technician_user_id=? AND c.status='WORK_ORDER_CREATED'
                """,id,w.getWorkOrderId(),expected,Scope.user().getUserId()));
        AvailabilityRepository.recalculate(w.getComplaint().getAsset().getAssetId());
        return id;
    }

    public void verify(WorkOrder w,int expected) throws SQLException {
        SecurityRepository.requireRole(Scope.user(),UserRole.ADMINISTRATOR);
        var submission=current(w,expected,WorkOrderStatus.COMPLETED);
        if(submission.reviewId()!=0)throw new IllegalStateException("This submission was already reviewed");
        w.verify(Scope.user());
        review(expected,"VERIFY",null);
        one(SecurityRepository.update("""
                UPDATE work_orders w JOIN complaints c ON c.complaint_id=w.complaint_id
                SET w.status='VERIFIED',w.verified_at=CURRENT_TIMESTAMP(6),w.verified_submission_id=?
                WHERE w.work_order_id=? AND w.status='COMPLETED' AND w.current_submission_id=?
                AND w.verified_at IS NULL AND c.status='WORK_ORDER_CREATED'
                """,expected,w.getWorkOrderId(),expected));
        one(SecurityRepository.update("UPDATE complaints SET status='RESOLVED' WHERE complaint_id=? AND status='WORK_ORDER_CREATED'",
                w.getComplaint().getComplaintId()));
        AvailabilityRepository.recalculate(w.getComplaint().getAsset().getAssetId());
    }

    private void review(int record,String kind,String reason) throws SQLException {
        one(SecurityRepository.update("""
                INSERT INTO repair_reviews(maintenance_record_id,kind,actor_user_id,author_name,author_role,reason)
                VALUES (?,?,?,?,'ADMINISTRATOR',?)
                """,record,kind,Scope.user().getUserId(),Scope.user().getName(),reason));
    }
    private static void one(int count) throws SQLException {
        if(count!=1)throw new SQLException("Repair progress changed; refresh before retrying");
    }
}
