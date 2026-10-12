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

    public WorkOrder(Complaint complaint, User technician) {
        this(0, complaint, technician);
    }

    public WorkOrder(int workOrderId, Complaint complaint, User technician) {
        this(
                workOrderId,
                complaint,
                technician,
                WorkOrderStatus.ASSIGNED,
                LocalDateTime.now().withNano(0),
                null,
                null,
                null);

        if (technician.getRole() != UserRole.TECHNICIAN) {
            throw new IllegalArgumentException("The assigned user must be a technician");
        }
        if (complaint.getStatus() != ComplaintStatus.ACCEPTED) {
            throw new IllegalStateException(
                    "The complaint must be accepted before creating a work order");
        }

        complaint.markWorkOrderCreated();
    }

    public WorkOrder(
            int workOrderId,
            Complaint complaint,
            User technician,
            WorkOrderStatus status,
            LocalDateTime assignedAt,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            LocalDateTime verifiedAt) {
        this.workOrderId = workOrderId;
        this.complaint = Objects.requireNonNull(complaint, "Complaint is required");
        this.technician = Objects.requireNonNull(technician, "Technician is required");

        this.status = Objects.requireNonNull(status, "Work-order status is required");
        this.assignedAt =
                Objects.requireNonNull(assignedAt, "Assignment time is required").withNano(0);
        this.startedAt = normalizeTimestamp(startedAt);
        this.completedAt = normalizeTimestamp(completedAt);
        this.verifiedAt = verifiedAt;
    }

    public void start() {
        requireStatus(WorkOrderStatus.ASSIGNED, "Only an assigned work order can be started");

        status = WorkOrderStatus.IN_PROGRESS;
        startedAt = LocalDateTime.now().withNano(0);
    }

    public void complete() {
        requireStatus(WorkOrderStatus.IN_PROGRESS, "Only an active work order can be completed");

        status = WorkOrderStatus.COMPLETED;
        completedAt = LocalDateTime.now().withNano(0);
    }

    public void verify(User administrator) {
        Objects.requireNonNull(administrator, "Administrator is required");

        if (administrator.getRole() != UserRole.ADMINISTRATOR) {
            throw new IllegalArgumentException("Only an administrator can verify a work order");
        }

        requireStatus(WorkOrderStatus.COMPLETED, "Only a completed work order can be verified");

        status = WorkOrderStatus.VERIFIED;
        verifiedAt = LocalDateTime.now().withNano(0);
        complaint.resolve();
    }

    private void requireStatus(WorkOrderStatus expectedStatus, String message) {
        if (status != expectedStatus) {
            throw new IllegalStateException(message);
        }
    }

    private static LocalDateTime normalizeTimestamp(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.withNano(0);
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
