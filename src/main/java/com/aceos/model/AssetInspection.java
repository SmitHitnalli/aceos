package com.aceos.model;

import java.time.LocalDate;

public class AssetInspection {

    private int inspectionId;
    private final Asset asset;
    private final String inspectionType;
    private final LocalDate scheduledDate;

    private InspectionStatus status;
    private User inspectedBy;
    private String notes;
    private LocalDate completedDate;
    private int assignedTechnicianId;

    public int getAssignedTechnicianId() {
        return assignedTechnicianId;
    }

    public void setAssignedTechnicianId(int id) {
        assignedTechnicianId = id;
    }

    public AssetInspection(Asset asset, String inspectionType, LocalDate scheduledDate) {

        this(0, asset, inspectionType, scheduledDate, InspectionStatus.SCHEDULED, null, null, null);
    }

    public AssetInspection(
            int inspectionId,
            Asset asset,
            String inspectionType,
            LocalDate scheduledDate,
            InspectionStatus status,
            User inspectedBy,
            String notes,
            LocalDate completedDate) {

        if (inspectionId < 0) {
            throw new IllegalArgumentException("Inspection ID cannot be negative");
        }

        if (asset == null) {
            throw new IllegalArgumentException("Inspection asset is required");
        }

        if (inspectionType == null || inspectionType.isBlank()) {
            throw new IllegalArgumentException("Inspection type is required");
        }

        if (scheduledDate == null) {
            throw new IllegalArgumentException("Scheduled date is required");
        }

        if (status == null) {
            throw new IllegalArgumentException("Inspection status is required");
        }

        validateState(status, inspectedBy, notes, completedDate);

        this.inspectionId = inspectionId;
        this.asset = asset;
        this.inspectionType = inspectionType.trim();
        this.scheduledDate = scheduledDate;
        this.status = status;
        this.inspectedBy = inspectedBy;
        this.notes = notes == null ? null : notes.trim();
        this.completedDate = completedDate;
    }

    public void complete(
            User inspector,
            InspectionStatus result,
            String completionNotes,
            LocalDate completionDate) {

        if (inspector == null || inspector.getRole() != UserRole.TECHNICIAN)
            throw new IllegalArgumentException("Only a technician can complete an inspection");
        if (status != InspectionStatus.SCHEDULED) {
            throw new IllegalStateException("Only a scheduled inspection " + "can be completed");
        }

        validateState(result, inspector, completionNotes, completionDate);

        status = result;
        inspectedBy = inspector;
        notes = completionNotes.trim();
        completedDate = completionDate;
    }

    public boolean isOverdueOn(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Comparison date is required");
        }

        return status == InspectionStatus.SCHEDULED && scheduledDate.isBefore(date);
    }

    public boolean isCompliant() {
        return status == InspectionStatus.PASSED;
    }

    private static void validateState(
            InspectionStatus status, User inspectedBy, String notes, LocalDate completedDate) {

        if (status == InspectionStatus.SCHEDULED) {
            if (inspectedBy != null || completedDate != null) {
                throw new IllegalArgumentException(
                        "A scheduled inspection cannot " + "have completion details");
            }

            return;
        }

        if (status != InspectionStatus.PASSED && status != InspectionStatus.FAILED) {
            throw new IllegalArgumentException("Inspection result must be " + "PASSED or FAILED");
        }

        if (inspectedBy == null) {
            throw new IllegalArgumentException("Inspector is required");
        }

        if (notes == null || notes.isBlank()) {
            throw new IllegalArgumentException("Inspection notes are required");
        }

        if (completedDate == null) {
            throw new IllegalArgumentException("Completion date is required");
        }
    }

    public int getInspectionId() {
        return inspectionId;
    }

    public void setInspectionId(int inspectionId) {
        this.inspectionId = inspectionId;
    }

    public Asset getAsset() {
        return asset;
    }

    public String getInspectionType() {
        return inspectionType;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public InspectionStatus getStatus() {
        return status;
    }

    public User getInspectedBy() {
        return inspectedBy;
    }

    public String getNotes() {
        return notes;
    }

    public LocalDate getCompletedDate() {
        return completedDate;
    }
}
