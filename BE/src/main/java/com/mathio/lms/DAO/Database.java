package com.mathio.lms.DAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

public class Database implements AutoCloseable {
    private final DBContext context;
    private final ThreadLocal<Connection> currentTransaction = new ThreadLocal<>();
    private final ThreadLocal<Connection> currentRead = new ThreadLocal<>();
    private final ThreadLocal<Boolean> rollbackOnly = new ThreadLocal<>();

    public void requireNoTransaction() {
        if (currentTransaction.get() != null || currentRead.get() != null)
            throw new IllegalStateException(
                    "External HTTP calls must run outside JDBC transactions");
    }

    public Database(DataSource source) {
        this(new DBContext(source));
    }

    public Database(DBContext context) {
        this.context = Objects.requireNonNull(context);
    }

    public static Database configured() {
        return new Database(DBContext.configured());
    }

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws Exception;
    }

    public <T> T read(Work<T> work) {
        Connection active = currentTransaction.get();
        if (active == null) active = currentRead.get();
        if (active != null) {
            return execute(work, active);
        }
        try (Connection connection = context.getConnection()) {
            currentRead.set(connection);
            try {
                return execute(work, connection);
            } finally {
                currentRead.remove();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database operation failed", e);
        }
    }

    private <T> T execute(Work<T> work, Connection connection) {
        try {
            return work.run(connection);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Database operation failed", e);
        }
    }

    public <T> T transaction(Work<T> work) {
        if (currentTransaction.get() != null) {
            try {
                return read(work);
            } catch (RuntimeException | Error e) {
                rollbackOnly.set(true);
                throw e;
            }
        }
        return read(
                connection -> {
                    connection.setAutoCommit(false);
                    currentTransaction.set(connection);
                    rollbackOnly.set(false);
                    Throwable failure = null;
                    boolean completed = false;
                    try {
                        T result = work.run(connection);
                        if (Boolean.TRUE.equals(rollbackOnly.get()))
                            throw new IllegalStateException(
                                    "Nested transaction failed; transaction is rollback-only");
                        connection.commit();
                        completed = true;
                        return result;
                    } catch (Exception | Error e) {
                        failure = e;
                        try {
                            connection.rollback();
                            completed = true;
                        } catch (SQLException rollbackError) {
                            e.addSuppressed(rollbackError);
                        }
                        throw e;
                    } finally {
                        currentTransaction.remove();
                        rollbackOnly.remove();
                        try {
                            if (completed) {
                                connection.setAutoCommit(true);
                            }
                        } catch (SQLException resetError) {
                            if (failure == null) {
                                throw resetError;
                            }
                            failure.addSuppressed(resetError);
                        }
                    }
                });
    }

    public static PreparedStatement prepare(Connection connection, String sql, Object... args)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            statement.setQueryTimeout(
                    com.mathio.lms.model.Environment.integer(
                            "DB_QUERY_TIMEOUT_SECONDS", 30, 1, 300));
            bind(statement, args);
            return statement;
        } catch (SQLException | RuntimeException e) {
            try {
                statement.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    private static void bind(PreparedStatement statement, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
    }

    public static List<Map<String, Object>> rows(Connection connection, String sql, Object... args)
            throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, args);
                ResultSet resultSet = statement.executeQuery()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(mapRow(resultSet));
            }
            return rows;
        }
    }

    /** Giữ nguyên alias và chuỗi thời gian của JSON API hiện tại. */
    private static Map<String, Object> mapRow(ResultSet resultSet) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        ResultSetMetaData metadata = resultSet.getMetaData();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            Object value = resultSet.getObject(index);
            if (value instanceof Timestamp timestamp) {
                value = timestamp.toInstant().toString();
            } else if (value instanceof java.sql.Date date) {
                value = date.toLocalDate().toString();
            }
            row.put(metadata.getColumnLabel(index), value);
        }
        return row;
    }

    public static Map<String, Object> one(Connection connection, String sql, Object... args)
            throws SQLException {
        var rows = rows(connection, sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static int update(Connection connection, String sql, Object... args)
            throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, args)) {
            return statement.executeUpdate();
        }
    }

    public static long insert(Connection connection, String sql, Object... args)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setQueryTimeout(
                    com.mathio.lms.model.Environment.integer(
                            "DB_QUERY_TIMEOUT_SECONDS", 30, 1, 300));
            bind(statement, args);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Insert did not return a generated key");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public void close() {
        context.close();
    }
}
