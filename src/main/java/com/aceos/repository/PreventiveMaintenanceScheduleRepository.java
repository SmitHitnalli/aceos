package com.aceos.repository;

import com.aceos.database.DatabaseConnection;
import com.aceos.model.PreventiveMaintenanceSchedule;
import com.aceos.model.Asset;
import com.aceos.model.AssetStatus;
import com.aceos.model.MaintenanceScheduleStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.Optional;

public class PreventiveMaintenanceScheduleRepository {

    public PreventiveMaintenanceSchedule save(
            PreventiveMaintenanceSchedule schedule)
            throws SQLException {

        requirePersistedAsset(schedule);

        String sql = """
                INSERT INTO preventive_maintenance_schedules (
                    asset_id,
                    task_description,
                    frequency_days,
                    next_due_date,
                    status
                )
                VALUES (?, ?, ?, ?, ?)
                """;

        try (
                Connection connection
                = DatabaseConnection.getConnection(); PreparedStatement statement
                = connection.prepareStatement(
                        sql,
                        Statement.RETURN_GENERATED_KEYS
                )) {
            statement.setInt(
                    1,
                    schedule.getAsset().getAssetId()
            );
            statement.setString(
                    2,
                    schedule.getTaskDescription()
            );
            statement.setInt(
                    3,
                    schedule.getFrequencyDays()
            );
            statement.setDate(
                    4,
                    java.sql.Date.valueOf(
                            schedule.getNextDueDate()
                    )
            );
            statement.setString(
                    5,
                    schedule.getStatus().name()
            );

            int affectedRows = statement.executeUpdate();

            if (affectedRows != 1) {
                throw new SQLException(
                        "Expected one maintenance schedule "
                        + "to be inserted"
                );
            }

            try (ResultSet generatedKeys
                    = statement.getGeneratedKeys()) {

                if (generatedKeys.next()) {
                    return new PreventiveMaintenanceSchedule(
                            generatedKeys.getInt(1),
                            schedule.getAsset(),
                            schedule.getTaskDescription(),
                            schedule.getFrequencyDays(),
                            schedule.getNextDueDate(),
                            schedule.getStatus()
                    );
                }
            }

            throw new SQLException(
                    "MySQL did not return a generated "
                    + "maintenance schedule ID"
            );
        }
    }

    public Optional<PreventiveMaintenanceSchedule> findById(
            int maintenanceScheduleId)
            throws SQLException {

        String sql = """
            SELECT
                pms.maintenance_schedule_id,
                pms.task_description,
                pms.frequency_days,
                pms.next_due_date,
                pms.status AS schedule_status,

                a.asset_id,
                a.asset_code,
                a.name AS asset_name,
                a.category,
                a.location,
                a.status AS asset_status
            FROM preventive_maintenance_schedules pms
            INNER JOIN assets a
                ON a.asset_id = pms.asset_id
            WHERE pms.maintenance_schedule_id = ?
            """;

        try (
                Connection connection
                = DatabaseConnection.getConnection(); PreparedStatement statement
                = connection.prepareStatement(sql)) {
            statement.setInt(1, maintenanceScheduleId);

            try (ResultSet resultSet
                    = statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                        mapSchedule(resultSet)
                );
            }
        }
    }

    private static PreventiveMaintenanceSchedule mapSchedule(
            ResultSet resultSet)
            throws SQLException {

        Asset asset = new Asset(
                resultSet.getInt("asset_id"),
                resultSet.getString("asset_code"),
                resultSet.getString("asset_name"),
                resultSet.getString("category"),
                resultSet.getString("location"),
                AssetStatus.valueOf(
                        resultSet.getString("asset_status")
                )
        );

        return new PreventiveMaintenanceSchedule(
                resultSet.getInt("maintenance_schedule_id"),
                asset,
                resultSet.getString("task_description"),
                resultSet.getInt("frequency_days"),
                resultSet.getDate("next_due_date")
                        .toLocalDate(),
                MaintenanceScheduleStatus.valueOf(
                        resultSet.getString("schedule_status")
                )
        );
    }

    private static void requirePersistedAsset(
            PreventiveMaintenanceSchedule schedule) {

        if (schedule.getAsset().getAssetId() <= 0) {
            throw new IllegalArgumentException(
                    "The schedule asset must be saved first"
            );
        }
    }
}
