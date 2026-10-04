package com.aceos.repository;

import com.aceos.database.DatabaseConnection;
import com.aceos.model.DashboardStatistics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DashboardStatisticsRepository {

    public DashboardStatistics load()
            throws SQLException {
        String sql = """
                SELECT
                    (
                        SELECT COUNT(*)
                        FROM assets
                    ) AS total_assets,
                    (
                        SELECT COUNT(*)
                        FROM assets
                        WHERE status = 'ACTIVE'
                    ) AS active_assets,
                    (
                        SELECT COUNT(*)
                        FROM complaints
                        WHERE status NOT IN (
                            'REJECTED',
                            'RESOLVED'
                        )
                    ) AS open_complaints,
                    (
                        SELECT COUNT(*)
                        FROM work_orders
                        WHERE status <> 'VERIFIED'
                    ) AS active_work_orders,
                    (
                        SELECT COUNT(*)
                        FROM preventive_maintenance_schedules
                        WHERE status = 'ACTIVE'
                          AND next_due_date < CURRENT_DATE
                    ) AS overdue_schedules,
                    (
                        SELECT COUNT(*)
                        FROM asset_inspections
                        WHERE status = 'FAILED'
                    ) AS failed_inspections
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql);
                ResultSet resultSet =
                        statement.executeQuery()
        ) {
            if (!resultSet.next()) {
                throw new SQLException(
                        "Dashboard statistics were not returned"
                );
            }

            return new DashboardStatistics(
                    resultSet.getInt("total_assets"),
                    resultSet.getInt("active_assets"),
                    resultSet.getInt("open_complaints"),
                    resultSet.getInt("active_work_orders"),
                    resultSet.getInt("overdue_schedules"),
                    resultSet.getInt("failed_inspections")
            );
        }
    }
}