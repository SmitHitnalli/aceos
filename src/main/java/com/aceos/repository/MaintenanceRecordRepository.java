package com.aceos.repository;

import com.aceos.database.DatabaseConnection;
import com.aceos.model.MaintenanceRecord;
import com.aceos.model.WorkOrder;
import com.aceos.model.WorkOrderStatus;
import com.aceos.security.Scope;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Optional;

public class MaintenanceRecordRepository {

    public MaintenanceRecord save(MaintenanceRecord maintenanceRecord) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return insertMaintenanceRecord(connection, maintenanceRecord);
        }
    }

    static MaintenanceRecord insertMaintenanceRecord(
            Connection connection, MaintenanceRecord maintenanceRecord) throws SQLException {
        requireNewMaintenanceRecord(maintenanceRecord);

        String sql =
                """
                INSERT INTO maintenance_records (
                    work_order_id,
                    action_taken,
                    recorded_at
                )
                SELECT
                    work_order_id,
                    ?,
                    ?
                FROM work_orders
                WHERE work_order_id = ?
                  AND status = 'COMPLETED'
                """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, maintenanceRecord.getActionTaken());
            statement.setTimestamp(2, Timestamp.valueOf(maintenanceRecord.getRecordedAt()));
            statement.setInt(3, maintenanceRecord.getWorkOrder().getWorkOrderId());

            int affectedRows = statement.executeUpdate();

            if (affectedRows != 1) {
                throw new SQLException(
                        "The work order was not in the expected " + "COMPLETED database state");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return new MaintenanceRecord(
                            generatedKeys.getInt(1),
                            maintenanceRecord.getWorkOrder(),
                            maintenanceRecord.getActionTaken(),
                            maintenanceRecord.getRecordedAt());
                }
            }

            throw new SQLException("MySQL did not return a generated " + "maintenance-record ID");
        }
    }

    public Optional<MaintenanceRecord> findById(int maintenanceRecordId) throws SQLException {
        String sql =
                """
                SELECT
                    maintenance_record_id,
                    work_order_id,
                    action_taken,
                    recorded_at
                FROM maintenance_records
                WHERE maintenance_record_id = ?
                """;
        sql = Scope.filter(sql, "records", "");

        int loadedMaintenanceRecordId;
        int workOrderId;
        String actionTaken;
        Timestamp recordedAt;

        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, maintenanceRecordId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                loadedMaintenanceRecordId = resultSet.getInt("maintenance_record_id");
                workOrderId = resultSet.getInt("work_order_id");
                actionTaken = resultSet.getString("action_taken");
                recordedAt = resultSet.getTimestamp("recorded_at");
            }
        }

        WorkOrder workOrder =
                new WorkOrderRepository()
                        .findById(workOrderId)
                        .orElseThrow(
                                () ->
                                        new SQLException(
                                                "The maintenance record's work order "
                                                        + "could not be retrieved"));

        return Optional.of(
                new MaintenanceRecord(
                        loadedMaintenanceRecordId,
                        workOrder,
                        actionTaken,
                        recordedAt.toLocalDateTime()));
    }

    private static void requireNewMaintenanceRecord(MaintenanceRecord maintenanceRecord) {
        if (maintenanceRecord.getMaintenanceRecordId() != 0) {
            throw new IllegalArgumentException("Only a new maintenance record can be inserted");
        }

        WorkOrder workOrder = maintenanceRecord.getWorkOrder();

        if (workOrder.getWorkOrderId() <= 0) {
            throw new IllegalArgumentException(
                    "The maintenance-record work order " + "must be saved first");
        }

        if (workOrder.getStatus() != WorkOrderStatus.COMPLETED) {
            throw new IllegalArgumentException(
                    "The maintenance-record work order " + "must be completed");
        }

        if (workOrder.getCompletedAt() == null) {
            throw new IllegalArgumentException(
                    "The completed work order requires " + "a completion time");
        }
    }
}
