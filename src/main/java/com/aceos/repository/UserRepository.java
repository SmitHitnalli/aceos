package com.aceos.repository;

import com.aceos.database.DatabaseConnection;
import com.aceos.model.User;
import com.aceos.model.UserRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public class UserRepository {

    public User save(User user) throws SQLException {
        String sql = """
                INSERT INTO users (name, email, role)
                VALUES (?, ?, ?)
                """;

        try (
                Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        sql,
                        Statement.RETURN_GENERATED_KEYS
                )
        ) {
            statement.setString(1, user.getName());
            statement.setString(2, user.getEmail());
            statement.setString(3, user.getRole().name());

            int affectedRows = statement.executeUpdate();

            if (affectedRows != 1) {
                throw new SQLException("Expected one user to be inserted");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int generatedId = generatedKeys.getInt(1);

                    return new User(
                            generatedId,
                            user.getName(),
                            user.getEmail(),
                            user.getRole()
                    );
                }
            }

            throw new SQLException("MySQL did not return a generated user ID");
        }
    }

    public Optional<User> findById(int userId) throws SQLException {
        String sql = """
                SELECT user_id, name, email, role
                FROM users
                WHERE user_id = ?
                """;

        try (
                Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setInt(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    User user = new User(
                            resultSet.getInt("user_id"),
                            resultSet.getString("name"),
                            resultSet.getString("email"),
                            UserRole.valueOf(resultSet.getString("role"))
                    );

                    return Optional.of(user);
                }

                return Optional.empty();
            }
        }
    }
}