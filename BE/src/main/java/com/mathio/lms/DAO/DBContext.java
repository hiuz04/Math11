package com.mathio.lms.DAO;

import com.mathio.lms.model.Environment;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;

import javax.sql.DataSource;

/** Quản lý pool SQL Server; nơi mượn Connection phải đóng sau khi dùng. */
public final class DBContext implements AutoCloseable {
    private final DataSource source;

    public DBContext(DataSource source) {
        this.source = Objects.requireNonNull(source);
    }

    public static DBContext configured() {
        HikariConfig config = new HikariConfig();
        String url =
                Environment.get(
                        "DB_URL",
                        "jdbc:sqlserver://"
                                + Environment.get("DB_HOST", "localhost")
                                + ":"
                                + Environment.get("DB_PORT", "1433")
                                + ";databaseName="
                                + Environment.get("DB_NAME", "math11_tutor_db")
                                + ";encrypt="
                                + Environment.get("DB_ENCRYPT", "true")
                                + ";trustServerCertificate="
                                + Environment.get("DB_TRUST_SERVER_CERTIFICATE", "false"));
        String normalized = url.toLowerCase(Locale.ROOT);
        if (Environment.production()
                && (Environment.get("DB_TRUST_SERVER_CERTIFICATE", "false").equalsIgnoreCase("true")
                        || normalized.contains("trustservercertificate=true")
                        || !(normalized.contains("encrypt=true")
                                || normalized.contains("encrypt=strict")))) {
            throw new IllegalStateException(
                    "Production JDBC requires encryption and certificate validation");
        }
        config.setJdbcUrl(url);
        // Tomcat initializes DriverManager before scanning web application libraries.
        config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        config.setUsername(Environment.get("DB_USERNAME", "sa"));
        config.setPassword(Environment.get("DB_PASSWORD", "123"));
        int poolSize = Environment.integer("DB_POOL_SIZE", 10, 2, 50);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(Math.min(2, poolSize));
        config.setValidationTimeout(3000);
        config.addDataSourceProperty("loginTimeout", 10);
        config.addDataSourceProperty("socketTimeout", 45000);
        config.setConnectionTimeout(10000);
        config.setInitializationFailTimeout(-1);
        config.setPoolName("Mathio");
        return new DBContext(new HikariDataSource(config));
    }

    public Connection getConnection() throws SQLException {
        return source.getConnection();
    }

    /** Run File in NetBeans, or pass the path to a separate .env as the first argument. */
    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Cách chạy: DBContext [đường-dẫn-file.env]");
            System.exit(2);
        }
        try {
            String configuredFile = Environment.get("MATHIO_ENV_FILE", "");
            Path envFile =
                    Path.of(
                            args.length == 1
                                    ? args[0]
                                    : configuredFile.isBlank() ? ".env" : configuredFile);
            if (Files.isRegularFile(envFile)) {
                Environment.load(envFile);
            } else if (args.length == 1 || !configuredFile.isBlank()) {
                throw new IllegalStateException("Configuration file not found");
            }

            try (DBContext context = configured();
                    Connection connection = context.getConnection();
                    var statement =
                            connection.prepareStatement(
                                    "SELECT 1 AS connection_test, DB_NAME() AS database_name,"
                                            + " @@SERVERNAME AS server_name")) {
                statement.setQueryTimeout(5);
                try (var result = statement.executeQuery()) {
                    if (!result.next() || result.getInt("connection_test") != 1) {
                        throw new SQLException(
                                "Connection check did not return the expected result");
                    }
                    System.out.println("Kết nối SQL Server thành công.");
                    System.out.println("Server: " + result.getString("server_name"));
                    System.out.println("Database: " + result.getString("database_name"));
                }
            }
        } catch (SQLException e) {
            SQLException detail = e;
            for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql) detail = sql;
            }
            System.err.println(
                    "Kết nối SQL Server thất bại. Mã lỗi: "
                            + detail.getErrorCode()
                            + "; SQLState: "
                            + detail.getSQLState());
            System.err.println(
                    detail.getErrorCode() == 18456
                            ? "Kiểm tra DB_USERNAME, DB_PASSWORD và quyền đăng nhập SQL Server."
                            : "Kiểm tra host/port, tên database, tài khoản và cấu hình TLS.");
            System.exit(1);
        } catch (RuntimeException e) {
            System.err.println("Không thể kiểm tra kết nối. Kiểm tra file .env và cấu hình DB_*.");
            System.exit(1);
        }
    }

    @Override
    public void close() {
        if (source instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new IllegalStateException("Unable to close the database connection pool", e);
            }
        }
    }
}
