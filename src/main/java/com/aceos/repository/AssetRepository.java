package com.aceos.repository;

import com.aceos.database.DatabaseConnection;
import com.aceos.model.Asset;
import com.aceos.model.AssetStatus;
import com.aceos.security.Scope;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AssetRepository {

    public Asset save(Asset asset) throws SQLException {
        String sql =
                """
                INSERT INTO assets (
                    asset_code,
                    name,
                    category,
                    location,
                    status,
                    manual_lifecycle
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, asset.getAssetCode());
            statement.setString(2, asset.getName());
            statement.setString(3, asset.getCategory());
            statement.setString(4, asset.getLocation());
            statement.setString(5, asset.getStatus().name());
            statement.setString(6, asset.getManualLifecycle().name());

            int affectedRows = statement.executeUpdate();

            if (affectedRows != 1) {
                throw new SQLException("Expected one asset to be inserted");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int generatedId = generatedKeys.getInt(1);

                    return new Asset(
                            generatedId,
                            asset.getAssetCode(),
                            asset.getName(),
                            asset.getCategory(),
                            asset.getLocation(),
                            asset.getStatus());
                }
            }

            throw new SQLException("MySQL did not return a generated asset ID");
        }
    }

    public Optional<Asset> findById(int assetId) throws SQLException {
        String sql =
                """
                SELECT
                    asset_id,
                    asset_code,
                    name,
                    category,
                    location,
                    status,
                    manual_lifecycle
                FROM assets
                WHERE asset_id = ?
                """;
        sql = Scope.filter(sql, "assets", "");

        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, assetId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Asset asset =
                            new Asset(
                                    resultSet.getInt("asset_id"),
                                    resultSet.getString("asset_code"),
                                    resultSet.getString("name"),
                                    resultSet.getString("category"),
                                    resultSet.getString("location"),
                                    AssetStatus.valueOf(resultSet.getString("status")),
                                    com.aceos.model.ManualLifecycle.valueOf(resultSet.getString("manual_lifecycle")));

                    return Optional.of(asset);
                }

                return Optional.empty();
            }
        }
    }

    public List<Asset> findAll() throws SQLException {
        String sql =
                """
                SELECT
                    asset_id,
                    asset_code,
                    name,
                    category,
                    location,
                    status,
                    manual_lifecycle
                FROM assets
                ORDER BY asset_code, asset_id
                """;
        sql = Scope.filter(sql, "assets", "");

        List<Asset> assets = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                assets.add(
                        new Asset(
                                resultSet.getInt("asset_id"),
                                resultSet.getString("asset_code"),
                                resultSet.getString("name"),
                                resultSet.getString("category"),
                                resultSet.getString("location"),
                                AssetStatus.valueOf(resultSet.getString("status")),
                                    com.aceos.model.ManualLifecycle.valueOf(resultSet.getString("manual_lifecycle"))));
            }
        }

        return assets;
    }
}
