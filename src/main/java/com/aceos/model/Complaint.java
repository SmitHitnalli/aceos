package com.aceos.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class Complaint {

    private int complaintId;
    private final Asset asset;
    private final User reportedBy;
    private String description;
    private ComplaintPriority priority;
    private ComplaintStatus status;
    private final LocalDateTime reportedAt;

    public Complaint(
            int complaintId,
            Asset asset,
            User reportedBy,
            String description,
            ComplaintPriority priority) {
        this.complaintId = complaintId;
        this.asset = Objects.requireNonNull(asset, "Asset is required");
        this.reportedBy = Objects.requireNonNull(reportedBy, "Requester is required");
        this.description = requireText(description, "Description is required");
        this.priority = Objects.requireNonNull(priority, "Priority is required");
        this.status = ComplaintStatus.SUBMITTED;
        this.reportedAt = LocalDateTime.now();
    }

    public void accept(User administrator) {
        requireAdministrator(administrator);
        requireStatus(ComplaintStatus.SUBMITTED, "Only a submitted complaint can be accepted");
        status = ComplaintStatus.ACCEPTED;
    }

    public void reject(User administrator) {
        requireAdministrator(administrator);
        requireStatus(ComplaintStatus.SUBMITTED, "Only a submitted complaint can be rejected");
        status = ComplaintStatus.REJECTED;
    }

    void markWorkOrderCreated() {
        requireStatus(ComplaintStatus.ACCEPTED, "The complaint must be accepted first");
        status = ComplaintStatus.WORK_ORDER_CREATED;
    }

    void resolve() {
        requireStatus(
                ComplaintStatus.WORK_ORDER_CREATED,
                "A complaint can be resolved only after a work order is created");
        status = ComplaintStatus.RESOLVED;
    }

    private void requireStatus(ComplaintStatus expectedStatus, String message) {
        if (status != expectedStatus) {
            throw new IllegalStateException(message);
        }
    }

    private static void requireAdministrator(User user) {
        Objects.requireNonNull(user, "Administrator is required");
        if (user.getRole() != UserRole.ADMINISTRATOR) {
            throw new IllegalArgumentException("Only an administrator can perform this action");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public int getComplaintId() {
        return complaintId;
    }

    public Asset getAsset() {
        return asset;
    }

    public User getReportedBy() {
        return reportedBy;
    }

    public String getDescription() {
        return description;
    }

    public ComplaintPriority getPriority() {
        return priority;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public LocalDateTime getReportedAt() {
        return reportedAt;
    }
}
