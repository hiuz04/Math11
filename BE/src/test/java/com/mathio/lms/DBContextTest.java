package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mathio.lms.DAO.DBContext;
import com.mathio.lms.DAO.Database;
import com.mathio.lms.model.Environment;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

class DBContextTest {
    interface Pool extends DataSource, AutoCloseable {
        @Override
        void close();
    }

    @Test
    void independentReadsBorrowAndReturnConnectionsThroughContext() throws Exception {
        Pool pool = mock(Pool.class);
        Connection first = mock(Connection.class);
        Connection second = mock(Connection.class);
        when(pool.getConnection()).thenReturn(first, second);
        try (Database db = new Database(new DBContext(pool))) {
            db.read(
                    c -> {
                        assertSame(first, c);
                        return null;
                    });
            db.read(
                    c -> {
                        assertSame(second, c);
                        return null;
                    });
            verify(first).close();
            verify(second).close();
            verify(pool, never()).close();
        }
        verify(pool).close();
    }

    @Test
    void transactionFailureReturnsConnectionAndClosesPool() throws Exception {
        Pool pool = mock(Pool.class);
        Connection connection = mock(Connection.class);
        when(pool.getConnection()).thenReturn(connection);
        try (Database db = new Database(new DBContext(pool))) {
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            db.transaction(
                                    c -> {
                                        throw new IllegalStateException("failed work");
                                    }));
            verify(connection).rollback();
            verify(connection).close();
        }
        verify(pool).close();
    }

    private static Map<String, String> settings() {
        return new HashMap<>(
                Map.of(
                        "DB_URL", "jdbc:sqlserver://database.example:1433;databaseName=math11;encrypt=true;trustServerCertificate=false",
                        "DB_USER", "deployment-user",
                        "DB_PASSWORD", "fixture=literal;password"));
    }

    private static MockedStatic<Environment> environment(Map<String, String> settings) {
        return mockStatic(
                Environment.class,
                invocation -> {
                    if (invocation.getMethod().getName().equals("get")) {
                        return settings.getOrDefault(
                                invocation.getArgument(0), invocation.getArgument(1));
                    }
                    return invocation.callRealMethod();
                });
    }

    @Test
    void deploymentSettingsReachThePoolAndDbUserOverridesLegacyUsername() {
        var settings = settings();
        settings.put("DB_USERNAME", "legacy-user");
        try (var env = environment(settings);
                var pools = mockConstruction(
                        HikariDataSource.class,
                        (pool, construction) -> {
                            HikariConfig config = (HikariConfig) construction.arguments().get(0);
                            assertEquals(settings.get("DB_URL"), config.getJdbcUrl());
                            assertEquals("deployment-user", config.getUsername());
                            assertEquals(settings.get("DB_PASSWORD"), config.getPassword());
                            assertEquals("com.microsoft.sqlserver.jdbc.SQLServerDriver", config.getDriverClassName());
                        });
                var context = DBContext.configured()) {
            assertEquals(1, pools.constructed().size());
        }
    }

    @Test
    void legacyUsernameStillWorksWithoutDbUser() {
        var settings = settings();
        settings.remove("DB_USER");
        settings.put("DB_USERNAME", "legacy-user");
        try (var env = environment(settings);
                var pools = mockConstruction(
                        HikariDataSource.class,
                        (pool, construction) -> {
                            HikariConfig config = (HikariConfig) construction.arguments().get(0);
                            assertEquals("legacy-user", config.getUsername());
                        });
                var context = DBContext.configured()) {
            assertEquals(1, pools.constructed().size());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_URL", "DB_USER", "DB_PASSWORD"})
    void missingOrBlankConfigurationFailsBeforeOpeningConnections(String key) {
        for (String value : new String[] {null, "", "  "}) {
            var settings = settings();
            settings.put(key, value);
            try (var env = environment(settings);
                    var pools = mockConstruction(HikariDataSource.class)) {
                var error = assertThrows(IllegalStateException.class, DBContext::configured);
                assertEquals("Missing required database configuration: " + key, error.getMessage());
                assertTrue(pools.constructed().isEmpty());
            }
        }
    }
}
