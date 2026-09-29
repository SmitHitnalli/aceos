package com.aceos.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class MaintenanceRecord {

    private int maintenanceRecordId;
    private final WorkOrder workOrder;
    private final String actionTaken;
    private final LocalDateTime recordedAt;

    public MaintenanceRecord(int maintenanceRecordId, WorkOrder workOrder, String actionTaken) {
        this.workOrder = Objects.requireNonNull(workOrder, "Work order is required");

        if (workOrder.getStatus() != WorkOrderStatus.COMPLETED) {
            throw new IllegalStateException(
                    "A maintenance record can be created only for a completed work order");
        }
        if (actionTaken == null || actionTaken.isBlank()) {
            throw new IllegalArgumentException("The maintenance action is required");
        }

        this.maintenanceRecordId = maintenanceRecordId;
        this.actionTaken = actionTaken;
        this.recordedAt = LocalDateTime.now();
    }

    public int getMaintenanceRecordId() {
        return maintenanceRecordId;
    }

    public WorkOrder getWorkOrder() {
        return workOrder;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }
}
