package com.aceos.model;

import java.time.LocalDate;
import java.util.Objects;

public class PreventiveMaintenanceSchedule {

    private int maintenanceScheduleId;
    private final Asset asset;
    private final String taskDescription;
    private final int frequencyDays;
    private LocalDate nextDueDate;
    private MaintenanceScheduleStatus status;

    public PreventiveMaintenanceSchedule(
            Asset asset,
            String taskDescription,
            int frequencyDays,
            LocalDate nextDueDate) {
        this(
                0,
                asset,
                taskDescription,
                frequencyDays,
                nextDueDate,
                MaintenanceScheduleStatus.ACTIVE
        );
    }

    public PreventiveMaintenanceSchedule(
            int maintenanceScheduleId,
            Asset asset,
            String taskDescription,
            int frequencyDays,
            LocalDate nextDueDate,
            MaintenanceScheduleStatus status) {

        if (maintenanceScheduleId < 0) {
            throw new IllegalArgumentException(
                    "Maintenance schedule ID cannot be negative"
            );
        }

        if (taskDescription == null || taskDescription.isBlank()) {
            throw new IllegalArgumentException(
                    "Task description is required"
            );
        }

        if (frequencyDays <= 0) {
            throw new IllegalArgumentException(
                    "Frequency must be greater than zero"
            );
        }

        this.maintenanceScheduleId = maintenanceScheduleId;
        this.asset = Objects.requireNonNull(
                asset,
                "Asset is required"
        );
        this.taskDescription = taskDescription;
        this.frequencyDays = frequencyDays;
        this.nextDueDate = Objects.requireNonNull(
                nextDueDate,
                "Next due date is required"
        );
        this.status = Objects.requireNonNull(
                status,
                "Schedule status is required"
        );
    }

    public boolean isDueOn(LocalDate date) {
        Objects.requireNonNull(date, "Date is required");

        return status == MaintenanceScheduleStatus.ACTIVE
                && !nextDueDate.isAfter(date);
    }

    public void pause() {
        if (status != MaintenanceScheduleStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only an active schedule can be paused"
            );
        }

        status = MaintenanceScheduleStatus.PAUSED;
    }

    public void activate() {
        if (status != MaintenanceScheduleStatus.PAUSED) {
            throw new IllegalStateException(
                    "Only a paused schedule can be activated"
            );
        }

        status = MaintenanceScheduleStatus.ACTIVE;
    }

    public void recordCompletion(LocalDate completionDate) {
        Objects.requireNonNull(
                completionDate,
                "Completion date is required"
        );

        if (status != MaintenanceScheduleStatus.ACTIVE) {
            throw new IllegalStateException(
                    "A paused schedule cannot be completed"
            );
        }

        nextDueDate = completionDate.plusDays(frequencyDays);
    }

    public int getMaintenanceScheduleId() {
        return maintenanceScheduleId;
    }

    public void setMaintenanceScheduleId(
            int maintenanceScheduleId) {
        this.maintenanceScheduleId = maintenanceScheduleId;
    }

    public Asset getAsset() {
        return asset;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public int getFrequencyDays() {
        return frequencyDays;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public MaintenanceScheduleStatus getStatus() {
        return status;
    }
}
