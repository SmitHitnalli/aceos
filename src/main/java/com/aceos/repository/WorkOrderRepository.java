package com.aceos.repository;

import com.aceos.database.DatabaseConnection;
import com.aceos.model.Asset;
import com.aceos.model.AssetStatus;
import com.aceos.model.Complaint;
import com.aceos.model.ComplaintPriority;
import com.aceos.model.ComplaintStatus;
import com.aceos.model.User;
import com.aceos.model.UserRole;
import com.aceos.model.WorkOrder;
import com.aceos.model.WorkOrderStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Optional;

public class WorkOrderRepository {

    public WorkOrder save(
            WorkOrder workOrder) throws SQLException {
        requireNewWorkOrder(workOrder);

        try (Connection connection =
                     DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                int generatedId =
                        insertWorkOrder(connection, workOrder);

                updateComplaintStatus(
                        connection,
                        workOrder.getComplaint()
                );

                connection.commit();

                return new WorkOrder(
                        generatedId,
                        workOrder.getComplaint(),
                        workOrder.getTechnician(),
                        workOrder.getStatus(),
                        workOrder.getAssignedAt(),
                        workOrder.getStartedAt(),
                        workOrder.getCompletedAt(),
                        workOrder.getVerifiedAt()
                );
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

    public Optional<WorkOrder> findById(
            int workOrderId) throws SQLException {
        String sql = """
                SELECT
                    w.work_order_id,
                    w.status AS work_order_status,
                    w.assigned_at,
                    w.started_at,
                    w.completed_at,
                    w.verified_at,

                    c.complaint_id,
                    c.description,
                    c.priority AS complaint_priority,
                    c.status AS complaint_status,
                    c.reported_at,

                    a.asset_id,
                    a.asset_code,
                    a.name AS asset_name,
                    a.category,
                    a.location,
                    a.status AS asset_status,

                    requester.user_id AS requester_id,
                    requester.name AS requester_name,
                    requester.email AS requester_email,
                    requester.role AS requester_role,

                    technician.user_id AS technician_id,
                    technician.name AS technician_name,
                    technician.email AS technician_email,
                    technician.role AS technician_role
                FROM work_orders w
                INNER JOIN complaints c
                    ON c.complaint_id = w.complaint_id
                INNER JOIN assets a
                    ON a.asset_id = c.asset_id
                INNER JOIN users requester
                    ON requester.user_id =
                       c.reported_by_user_id
                INNER JOIN users technician
                    ON technician.user_id =
                       w.technician_user_id
                WHERE w.work_order_id = ?
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setInt(1, workOrderId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                Asset asset = new Asset(
                        resultSet.getInt("asset_id"),
                        resultSet.getString("asset_code"),
                        resultSet.getString("asset_name"),
                        resultSet.getString("category"),
                        resultSet.getString("location"),
                        AssetStatus.valueOf(
                                resultSet.getString(
                                        "asset_status"
                                )
                        )
                );

                User requester = new User(
                        resultSet.getInt("requester_id"),
                        resultSet.getString("requester_name"),
                        resultSet.getString("requester_email"),
                        UserRole.valueOf(
                                resultSet.getString(
                                        "requester_role"
                                )
                        )
                );

                Complaint complaint = new Complaint(
                        resultSet.getInt("complaint_id"),
                        asset,
                        requester,
                        resultSet.getString("description"),
                        ComplaintPriority.valueOf(
                                resultSet.getString(
                                        "complaint_priority"
                                )
                        ),
                        ComplaintStatus.valueOf(
                                resultSet.getString(
                                        "complaint_status"
                                )
                        ),
                        resultSet
                                .getTimestamp("reported_at")
                                .toLocalDateTime()
                );

                User technician = new User(
                        resultSet.getInt("technician_id"),
                        resultSet.getString("technician_name"),
                        resultSet.getString("technician_email"),
                        UserRole.valueOf(
                                resultSet.getString(
                                        "technician_role"
                                )
                        )
                );

                WorkOrder workOrder = new WorkOrder(
                        resultSet.getInt("work_order_id"),
                        complaint,
                        technician,
                        WorkOrderStatus.valueOf(
                                resultSet.getString(
                                        "work_order_status"
                                )
                        ),
                        resultSet
                                .getTimestamp("assigned_at")
                                .toLocalDateTime(),
                        getNullableTimestamp(
                                resultSet,
                                "started_at"
                        ),
                        getNullableTimestamp(
                                resultSet,
                                "completed_at"
                        ),
                        getNullableTimestamp(
                                resultSet,
                                "verified_at"
                        )
                );

                return Optional.of(workOrder);
            }
        }
    }

    private static int insertWorkOrder(
            Connection connection,
            WorkOrder workOrder) throws SQLException {
        String sql = """
                INSERT INTO work_orders (
                    complaint_id,
                    technician_user_id,
                    status,
                    assigned_at,
                    started_at,
                    completed_at,
                    verified_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS
                     )) {
            statement.setInt(
                    1,
                    workOrder.getComplaint().getComplaintId()
            );
            statement.setInt(
                    2,
                    workOrder.getTechnician().getUserId()
            );
            statement.setString(
                    3,
                    workOrder.getStatus().name()
            );
            statement.setTimestamp(
                    4,
                    Timestamp.valueOf(workOrder.getAssignedAt())
            );

            setNullableTimestamp(
                    statement,
                    5,
                    workOrder.getStartedAt()
            );
            setNullableTimestamp(
                    statement,
                    6,
                    workOrder.getCompletedAt()
            );
            setNullableTimestamp(
                    statement,
                    7,
                    workOrder.getVerifiedAt()
            );

            int affectedRows = statement.executeUpdate();

            if (affectedRows != 1) {
                throw new SQLException(
                        "Expected one work order to be inserted"
                );
            }

            try (ResultSet generatedKeys =
                         statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }

            throw new SQLException(
                    "MySQL did not return a generated work-order ID"
            );
        }
    }

    private static void updateComplaintStatus(
            Connection connection,
            Complaint complaint) throws SQLException {
        String sql = """
                UPDATE complaints
                SET status = ?
                WHERE complaint_id = ?
                  AND status = 'ACCEPTED'
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            statement.setString(
                    1,
                    complaint.getStatus().name()
            );
            statement.setInt(
                    2,
                    complaint.getComplaintId()
            );

            int affectedRows = statement.executeUpdate();

            if (affectedRows != 1) {
                throw new SQLException(
                        "The complaint was not in the expected "
                                + "ACCEPTED database state"
                );
            }
        }
    }

    private static void requireNewWorkOrder(
            WorkOrder workOrder) {
        if (workOrder.getWorkOrderId() != 0) {
            throw new IllegalArgumentException(
                    "Only a new work order can be inserted"
            );
        }

        if (workOrder.getComplaint().getComplaintId() <= 0) {
            throw new IllegalArgumentException(
                    "The work-order complaint must be saved first"
            );
        }

        if (workOrder.getTechnician().getUserId() <= 0) {
            throw new IllegalArgumentException(
                    "The assigned technician must be saved first"
            );
        }

        if (workOrder.getStatus() != WorkOrderStatus.ASSIGNED) {
            throw new IllegalArgumentException(
                    "A new work order must be assigned"
            );
        }

        if (workOrder.getComplaint().getStatus()
                != ComplaintStatus.WORK_ORDER_CREATED) {
            throw new IllegalArgumentException(
                    "The complaint must be marked as having "
                            + "a work order"
            );
        }
    }

    private static void setNullableTimestamp(
            PreparedStatement statement,
            int parameterIndex,
            LocalDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(
                    parameterIndex,
                    Types.TIMESTAMP
            );
        } else {
            statement.setTimestamp(
                    parameterIndex,
                    Timestamp.valueOf(value)
            );
        }
    }

    private static LocalDateTime getNullableTimestamp(
            ResultSet resultSet,
            String columnName) throws SQLException {
        Timestamp timestamp =
                resultSet.getTimestamp(columnName);

        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }
}