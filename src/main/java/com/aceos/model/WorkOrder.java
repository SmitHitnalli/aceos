package com.aceos.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class WorkOrder {

    private int workOrderId;
    private final Complaint complaint;
    private final User technician;
    private WorkOrderStatus status;
    private final LocalDateTime assignedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime verifiedAt;

    public WorkOrder(int workOrderId, Complaint complaint, User technician) {
        this.complaint = Objects.requireNonNull(complaint, "Complaint is required");
        this.technician = Objects.requireNonNull(technician, "Technician is required");

        if (complaint.getStatus() != ComplaintStatus.ACCEPTED) {
            throw new IllegalStateException("The complaint must be accepted before creating a work order");
        }
        if (technician.getRole() != UserRole.TECHNICIAN) {
            throw new IllegalArgumentException("The assigned user must be a technician");
        }

        this.workOrderId = workOrderId;
        this.status = WorkOrderStatus.ASSIGNED;
        this.assignedAt = LocalDateTime.now();
        complaint.markWorkOrderCreated();
    }

    public void start() {
        requireStatus(WorkOrderStatus.ASSIGNED, "Only an assigned work order can be started");
        status = WorkOrderStatus.IN_PROGRESS;
        startedAt = LocalDateTime.now();
        complaint.getAsset().setStatus(AssetStatus.UNDER_MAINTENANCE);
    }

    public void complete() {
        requireStatus(WorkOrderStatus.IN_PROGRESS, "Only an active work order can be completed");
        status = WorkOrderStatus.COMPLETED;
        completedAt = LocalDateTime.now();
    }

    public void verify(User administrator) {
        Objects.requireNonNull(administrator, "Administrator is required");
        if (administrator.getRole() != UserRole.ADMINISTRATOR) {
            throw new IllegalArgumentException("Only an administrator can verify a work order");
        }

        requireStatus(WorkOrderStatus.COMPLETED, "Only a completed work order can be verified");
        status = WorkOrderStatus.VERIFIED;
        verifiedAt = LocalDateTime.now();
        complaint.getAsset().setStatus(AssetStatus.ACTIVE);
        complaint.resolve();
    }

    private void requireStatus(WorkOrderStatus expectedStatus, String message) {
        if (status != expectedStatus) {
            throw new IllegalStateException(message);
        }
    }

    public int getWorkOrderId() {
        return workOrderId;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public User getTechnician() {
        return technician;
    }

    public WorkOrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }
}
