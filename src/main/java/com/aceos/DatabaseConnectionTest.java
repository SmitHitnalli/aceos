package com.aceos;

import com.aceos.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnectionTest {

    public static void main(String[] args) {
        com.aceos.security.LegacyVerificationGuard.retired();
        try (Connection connection = DatabaseConnection.getConnection()) {
            System.out.println("Connected database: " + connection.getCatalog());
            System.out.println("Connection valid: " + connection.isValid(2));
        } catch (SQLException | IllegalStateException exception) {
            System.err.println(
                    "Database connection failed: " + exception.getMessage()
            );
        }
    }
}