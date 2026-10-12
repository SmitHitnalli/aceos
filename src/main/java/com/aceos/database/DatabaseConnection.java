package com.aceos.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {

    private static final String URL_VARIABLE = "ACEOS_DB_URL";
    private static final String USER_VARIABLE = "ACEOS_DB_USER";
    private static final String PASSWORD_VARIABLE = "ACEOS_DB_PASSWORD";

    private DatabaseConnection() {}

    public static Connection getConnection() throws SQLException {
        Connection current = Transactions.current();
        return current == null ? openConnection() : current;
    }

    static Connection openConnection() throws SQLException {
        String url = requireEnvironmentVariable(URL_VARIABLE);
        String user = requireEnvironmentVariable(USER_VARIABLE);
        String password = requireEnvironmentVariable(PASSWORD_VARIABLE);

        return DriverManager.getConnection(url, user, password);
    }

    private static String requireEnvironmentVariable(String variableName) {
        String value = System.getenv(variableName);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: " + variableName);
        }

        return value;
    }
}
