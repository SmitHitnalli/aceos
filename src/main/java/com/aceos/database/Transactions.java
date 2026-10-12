package com.aceos.database;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

/** One JDBC transaction across service authorization, repositories and audit writes. */
public final class Transactions implements AutoCloseable {
    private static final ThreadLocal<Connection> CURRENT = new ThreadLocal<>();
    private final Connection owner;
    private boolean committed;

    public Transactions() throws SQLException {
        this(DatabaseConnection.openConnection());
    }

    public Transactions(Connection connection) throws SQLException {
        if (CURRENT.get() != null) throw new SQLException("Nested service transaction");
        owner = connection;
        // Schema reads precede the security gate. A repeatable-read snapshot taken before
        // waiting on that gate would hide newly committed holds/jobs from later reads.
        owner.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        owner.setAutoCommit(false);
        Connection borrowed =
                (Connection)
                        Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) -> {
                                    // Existing repositories borrow the connection; only this owner
                                    // commits/closes.
                                    if (method.getName().equals("close")
                                            || method.getName().equals("commit")
                                            || method.getName().equals("setAutoCommit"))
                                        return null;
                                    try {
                                        return method.invoke(owner, args);
                                    } catch (InvocationTargetException exception) {
                                        throw exception.getCause();
                                    }
                                });
        CURRENT.set(borrowed);
    }

    static Connection current() {
        return CURRENT.get();
    }

    public void commit() throws SQLException {
        owner.commit();
        committed = true;
    }

    @Override
    public void close() throws SQLException {
        CURRENT.remove();
        try {
            if (!committed) owner.rollback();
        } finally {
            owner.close();
        }
    }
}
