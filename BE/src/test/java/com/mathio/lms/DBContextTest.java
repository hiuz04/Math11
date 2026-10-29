package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mathio.lms.DAO.DBContext;
import com.mathio.lms.DAO.Database;

import org.junit.jupiter.api.Test;

import java.sql.Connection;

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
}
