package com.aceos.repository;

import com.aceos.database.DatabaseConnection;
import com.aceos.model.Asset;
import com.aceos.model.AssetStatus;
import com.aceos.model.Complaint;
import com.aceos.model.ComplaintPriority;
import com.aceos.model.ComplaintStatus;
import com.aceos.model.User;
import com.aceos.model.UserRole;
import com.aceos.security.Scope;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ComplaintRepository {

    public Complaint save(Complaint complaint) throws SQLException {
        requirePersistedReferences(complaint);

        String sql =
                """
                INSERT INTO complaints (
                    asset_id,
                    reported_by_user_id,
                    description,
                    priority,
                    status,
                    reported_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, complaint.getAsset().getAssetId());
            statement.setInt(2, complaint.getReportedBy().getUserId());
            statement.setString(3, complaint.getDescription());
            statement.setString(4, complaint.getPriority().name());
            statement.setString(5, complaint.getStatus().name());
            statement.setTimestamp(6, Timestamp.valueOf(complaint.getReportedAt()));

            int affectedRows = statement.executeUpdate();

            if (affectedRows != 1) {
                throw new SQLException("Expected one complaint to be inserted");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int generatedId = generatedKeys.getInt(1);

                    return new Complaint(
                            generatedId,
                            complaint.getAsset(),
                            complaint.getReportedBy(),
                            complaint.getDescription(),
                            complaint.getPriority(),
                            complaint.getStatus(),
                            complaint.getReportedAt());
                }
            }

            throw new SQLException("MySQL did not return a generated complaint ID");
        }
    }

    public void updateReview(Complaint complaint) throws SQLException {
        requirePersistedReview(complaint);

        String sql =
                """
                UPDATE complaints
                SET status = ?
                WHERE complaint_id = ?
                  AND status = 'SUBMITTED'
                """;

        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, complaint.getStatus().name());
            statement.setInt(2, complaint.getComplaintId());

            int affectedRows = statement.executeUpdate();

            if (affectedRows != 1) {
                throw new SQLException(
                        "The complaint was not in the expected " + "SUBMITTED database state");
            }
        }
    }

    public Optional<Complaint> findById(int complaintId) throws SQLException {
        String sql =
                """
                SELECT
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

                    u.user_id,
                    u.name AS reporter_name,
                    u.email AS reporter_email,
                    u.role AS reporter_role
                FROM complaints c
                INNER JOIN assets a
                    ON a.asset_id = c.asset_id
                INNER JOIN users u
                    ON u.user_id = c.reported_by_user_id
                WHERE c.complaint_id = ?
                """;
        sql = Scope.filter(sql, "complaints", "c");

        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, complaintId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapComplaint(resultSet));
            }
        }
    }

    public List<Complaint> findAll() throws SQLException {
        String sql =
                """
                SELECT
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

                    u.user_id,
                    u.name AS reporter_name,
                    u.email AS reporter_email,
                    u.role AS reporter_role
                FROM complaints c
                INNER JOIN assets a
                    ON a.asset_id = c.asset_id
                INNER JOIN users u
                    ON u.user_id = c.reported_by_user_id
                ORDER BY c.reported_at DESC,
                         c.complaint_id DESC
                """;
        sql = Scope.filter(sql, "complaints", "c");

        List<Complaint> complaints = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                complaints.add(mapComplaint(resultSet));
            }
        }

        return complaints;
    }

    private static Complaint mapComplaint(ResultSet resultSet) throws SQLException {
        Asset asset =
                new Asset(
                        resultSet.getInt("asset_id"),
                        resultSet.getString("asset_code"),
                        resultSet.getString("asset_name"),
                        resultSet.getString("category"),
                        resultSet.getString("location"),
                        AssetStatus.valueOf(resultSet.getString("asset_status")));

        User reportedBy =
                new User(
                        resultSet.getInt("user_id"),
                        resultSet.getString("reporter_name"),
                        resultSet.getString("reporter_email"),
                        UserRole.valueOf(resultSet.getString("reporter_role")));

        return new Complaint(
                resultSet.getInt("complaint_id"),
                asset,
                reportedBy,
                resultSet.getString("description"),
                ComplaintPriority.valueOf(resultSet.getString("complaint_priority")),
                ComplaintStatus.valueOf(resultSet.getString("complaint_status")),
                resultSet.getTimestamp("reported_at").toLocalDateTime());
    }

    private static void requirePersistedReview(Complaint complaint) {
        if (complaint.getComplaintId() <= 0) {
            throw new IllegalArgumentException("The complaint must be saved first");
        }

        if (complaint.getStatus() != ComplaintStatus.ACCEPTED
                && complaint.getStatus() != ComplaintStatus.REJECTED) {
            throw new IllegalArgumentException(
                    "Only an accepted or rejected complaint " + "review can be persisted");
        }
    }

    private static void requirePersistedReferences(Complaint complaint) {
        if (complaint.getAsset().getAssetId() <= 0) {
            throw new IllegalArgumentException("The complaint asset must be saved first");
        }

        if (complaint.getReportedBy().getUserId() <= 0) {
            throw new IllegalArgumentException("The complaint requester must be saved first");
        }
    }
}
