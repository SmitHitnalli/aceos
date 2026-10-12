package com.aceos.repository;

import com.aceos.database.DatabaseConnection;
import com.aceos.model.Asset;
import com.aceos.model.AssetInspection;
import com.aceos.model.AssetStatus;
import com.aceos.model.InspectionStatus;
import com.aceos.model.User;
import com.aceos.model.UserRole;
import com.aceos.security.Scope;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AssetInspectionRepository {

    public AssetInspection save(AssetInspection inspection) throws SQLException {

        requireNewScheduledInspection(inspection);

        String sql =
                """
                INSERT INTO asset_inspections (
                    asset_id,
                    inspection_type,
                    scheduled_date,
                    status
                )
                VALUES (?, ?, ?, ?)
                """;

        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, inspection.getAsset().getAssetId());
            statement.setString(2, inspection.getInspectionType());
            statement.setDate(3, java.sql.Date.valueOf(inspection.getScheduledDate()));
            statement.setString(4, inspection.getStatus().name());

            int affectedRows = statement.executeUpdate();

            if (affectedRows != 1) {
                throw new SQLException("Expected one inspection " + "to be inserted");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {

                if (generatedKeys.next()) {
                    return new AssetInspection(
                            generatedKeys.getInt(1),
                            inspection.getAsset(),
                            inspection.getInspectionType(),
                            inspection.getScheduledDate(),
                            inspection.getStatus(),
                            null,
                            null,
                            null);
                }
            }

            throw new SQLException("MySQL did not return a generated " + "inspection ID");
        }
    }

    public Optional<AssetInspection> findById(int inspectionId) throws SQLException {
        String sql =
                """
                SELECT
                    i.inspection_id,
                    i.assigned_to_user_id,
                    i.inspection_type,
                    i.scheduled_date,
                    i.status AS inspection_status,
                    i.notes,
                    i.completed_date,

                    a.asset_id,
                    a.asset_code,
                    a.name AS asset_name,
                    a.category,
                    a.location,
                    a.status AS asset_status,
                    a.manual_lifecycle,

                    u.user_id AS inspector_user_id,
                    u.name AS inspector_name,
                    u.email AS inspector_email,
                    u.role AS inspector_role
                FROM asset_inspections i
                INNER JOIN assets a
                    ON a.asset_id = i.asset_id
                LEFT JOIN users u
                    ON u.user_id = i.inspected_by_user_id
                WHERE i.inspection_id = ?
                """;
        sql = Scope.filter(sql, "inspections", "i");

        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, inspectionId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapInspection(resultSet));
            }
        }
    }

    public List<AssetInspection> findAll() throws SQLException {
        String sql =
                """
                SELECT
                    i.inspection_id,
                    i.assigned_to_user_id,
                    i.inspection_type,
                    i.scheduled_date,
                    i.status AS inspection_status,
                    i.notes,
                    i.completed_date,

                    a.asset_id,
                    a.asset_code,
                    a.name AS asset_name,
                    a.category,
                    a.location,
                    a.status AS asset_status,
                    a.manual_lifecycle,

                    u.user_id AS inspector_user_id,
                    u.name AS inspector_name,
                    u.email AS inspector_email,
                    u.role AS inspector_role
                FROM asset_inspections i
                INNER JOIN assets a
                    ON a.asset_id = i.asset_id
                LEFT JOIN users u
                    ON u.user_id = i.inspected_by_user_id
                ORDER BY
                    CASE
                        WHEN i.status = 'SCHEDULED' THEN 0
                        ELSE 1
                    END,
                    i.scheduled_date,
                    i.inspection_id
                """;
        sql = Scope.filter(sql, "inspections", "i");

        List<AssetInspection> inspections = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                inspections.add(mapInspection(resultSet));
            }
        }

        return inspections;
    }

    public void updateCompletion(AssetInspection inspection) throws SQLException {
        requireCompletedInspection(inspection);

        String sql =
                """
                UPDATE asset_inspections
                SET status = ?,
                    inspected_by_user_id = ?,
                    notes = ?,
                    completed_date = ?,
                    completed_at = CURRENT_TIMESTAMP(6)
                WHERE inspection_id = ?
                  AND status = 'SCHEDULED'
                """;

        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, inspection.getStatus().name());
            statement.setInt(2, inspection.getInspectedBy().getUserId());
            statement.setString(3, inspection.getNotes());
            statement.setDate(4, java.sql.Date.valueOf(inspection.getCompletedDate()));
            statement.setInt(5, inspection.getInspectionId());

            int affectedRows = statement.executeUpdate();

            if (affectedRows != 1) {
                throw new SQLException(
                        "The inspection was not in the " + "expected SCHEDULED state");
            }
        }
    }

    private static AssetInspection mapInspection(ResultSet resultSet) throws SQLException {
        Asset asset =
                new Asset(
                        resultSet.getInt("asset_id"),
                        resultSet.getString("asset_code"),
                        resultSet.getString("asset_name"),
                        resultSet.getString("category"),
                        resultSet.getString("location"),
                        AssetStatus.valueOf(resultSet.getString("asset_status")),
                        com.aceos.model.ManualLifecycle.valueOf(resultSet.getString("manual_lifecycle")));

        User inspectedBy = null;
        int inspectorId = resultSet.getInt("inspector_user_id");

        if (!resultSet.wasNull()) {
            inspectedBy =
                    new User(
                            inspectorId,
                            resultSet.getString("inspector_name"),
                            resultSet.getString("inspector_email"),
                            UserRole.valueOf(resultSet.getString("inspector_role")));
        }

        java.sql.Date completedDate = resultSet.getDate("completed_date");

        AssetInspection mapped =
                new AssetInspection(
                        resultSet.getInt("inspection_id"),
                        asset,
                        resultSet.getString("inspection_type"),
                        resultSet.getDate("scheduled_date").toLocalDate(),
                        InspectionStatus.valueOf(resultSet.getString("inspection_status")),
                        inspectedBy,
                        resultSet.getString("notes"),
                        completedDate == null ? null : completedDate.toLocalDate());
        mapped.setAssignedTechnicianId(resultSet.getInt("assigned_to_user_id"));
        return mapped;
    }

    private static void requireCompletedInspection(AssetInspection inspection) {
        if (inspection.getInspectionId() <= 0) {
            throw new IllegalArgumentException("The inspection must be saved first");
        }

        if (inspection.getStatus() != InspectionStatus.PASSED
                && inspection.getStatus() != InspectionStatus.FAILED) {
            throw new IllegalArgumentException("Only a completed inspection " + "can be persisted");
        }

        if (inspection.getInspectedBy() == null || inspection.getInspectedBy().getUserId() <= 0) {
            throw new IllegalArgumentException("The inspector must be saved first");
        }
    }

    private static void requireNewScheduledInspection(AssetInspection inspection) {

        if (inspection.getInspectionId() != 0) {
            throw new IllegalArgumentException("A new inspection cannot " + "already have an ID");
        }

        if (inspection.getAsset().getAssetId() <= 0) {
            throw new IllegalArgumentException("The inspection asset " + "must be saved first");
        }

        if (inspection.getStatus() != InspectionStatus.SCHEDULED) {
            throw new IllegalArgumentException("A new inspection must be scheduled");
        }
    }
}
