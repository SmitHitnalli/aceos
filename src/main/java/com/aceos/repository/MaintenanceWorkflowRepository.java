package com.aceos.repository;

import com.aceos.database.DatabaseConnection;
import com.aceos.model.ComplaintStatus;
import com.aceos.model.MaintenanceRecord;
import com.aceos.model.WorkOrder;
import com.aceos.model.WorkOrderStatus;

import java.sql.Connection;
import java.sql.SQLException;

public class MaintenanceWorkflowRepository {

    public MaintenanceRecord completeWithMaintenanceRecord(
            WorkOrder workOrder,
            String actionTaken) throws SQLException {
        requirePersistedInProgressWorkOrder(
                workOrder,
                actionTaken
        );

        try (Connection connection =
                     DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                WorkOrder completedWorkOrder =
                        copyWorkOrder(workOrder);

                completedWorkOrder.complete();

                MaintenanceRecord maintenanceRecord =
                        new MaintenanceRecord(
                                completedWorkOrder,
                                actionTaken
                        );

                WorkOrderRepository.persistCompletion(
                        connection,
                        completedWorkOrder
                );

                MaintenanceRecord savedMaintenanceRecord =
                        MaintenanceRecordRepository
                                .insertMaintenanceRecord(
                                        connection,
                                        maintenanceRecord
                                );

                AvailabilityRepository.recalculate(workOrder.getComplaint().getAsset().getAssetId());
                connection.commit();

                return savedMaintenanceRecord;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }

                throw exception;
            }
        }
    }

    private static WorkOrder copyWorkOrder(
            WorkOrder workOrder) {
        return new WorkOrder(
                workOrder.getWorkOrderId(),
                workOrder.getComplaint(),
                workOrder.getTechnician(),
                workOrder.getStatus(),
                workOrder.getAssignedAt(),
                workOrder.getStartedAt(),
                workOrder.getCompletedAt(),
                workOrder.getVerifiedAt()
        );
    }

    private static void requirePersistedInProgressWorkOrder(
            WorkOrder workOrder,
            String actionTaken) {
        if (workOrder.getWorkOrderId() <= 0) {
            throw new IllegalArgumentException(
                    "The work order must be saved first"
            );
        }

        if (workOrder.getStatus()
                != WorkOrderStatus.IN_PROGRESS) {
            throw new IllegalArgumentException(
                    "Only an in-progress work order "
                            + "can be completed"
            );
        }

        if (workOrder.getStartedAt() == null) {
            throw new IllegalArgumentException(
                    "The in-progress work order requires "
                            + "a start time"
            );
        }

        if (workOrder
                .getComplaint()
                .getStatus()
                != ComplaintStatus.WORK_ORDER_CREATED) {
            throw new IllegalArgumentException(
                    "The work-order complaint must remain open"
            );
        }

        if (actionTaken == null || actionTaken.isBlank()) {
            throw new IllegalArgumentException(
                    "The maintenance action is required"
            );
        }
    }
}
