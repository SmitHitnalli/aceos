package com.aceos.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class MaintenanceRecord {

    private int maintenanceRecordId;
    private final WorkOrder workOrder;
    private final String actionTaken;
    private final LocalDateTime recordedAt;

    public MaintenanceRecord(
            WorkOrder workOrder,
            String actionTaken) {
        this(
                0,
                workOrder,
                actionTaken,
                LocalDateTime.now().withNano(0)
        );

        requireCompletedWorkOrder(workOrder);
    }

    public MaintenanceRecord(
            int maintenanceRecordId,
            WorkOrder workOrder,
            String actionTaken) {
        this(
                maintenanceRecordId,
                workOrder,
                actionTaken,
                LocalDateTime.now().withNano(0)
        );

        requireCompletedWorkOrder(workOrder);
    }

    public MaintenanceRecord(
            int maintenanceRecordId,
            WorkOrder workOrder,
            String actionTaken,
            LocalDateTime recordedAt) {
        this.workOrder = Objects.requireNonNull(
                workOrder,
                "Work order is required"
        );

        if (workOrder.getStatus() != WorkOrderStatus.COMPLETED
                && workOrder.getStatus() != WorkOrderStatus.RETURNED
                && workOrder.getStatus() != WorkOrderStatus.VERIFIED) {
            throw new IllegalStateException(
                    "A maintenance record requires a previously completed repair"
            );
        }

        if (actionTaken == null || actionTaken.isBlank()) {
            throw new IllegalArgumentException(
                    "The maintenance action is required"
            );
        }

        this.maintenanceRecordId = maintenanceRecordId;
        this.actionTaken = actionTaken;
        this.recordedAt = Objects.requireNonNull(
                recordedAt,
                "Record time is required"
        ).withNano(0);
    }

    private static void requireCompletedWorkOrder(
            WorkOrder workOrder) {
        if (workOrder.getStatus() != WorkOrderStatus.COMPLETED) {
            throw new IllegalStateException(
                    "A new maintenance record can be created "
                            + "only for a completed work order"
            );
        }
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
