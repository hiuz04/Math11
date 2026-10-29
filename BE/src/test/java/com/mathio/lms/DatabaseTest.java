package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mathio.lms.DAO.Database;
import com.mathio.lms.model.ApiException;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

class DatabaseTest {
    @Test
    void rollbackFailurePreservesOriginalErrorWithoutEnablingAutoCommit() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        SQLException rollbackError = new SQLException("rollback failed");
        doThrow(rollbackError).when(connection).rollback();
        Database database = new Database(source);
        ApiException original = new ApiException(409, "conflict");
        assertSame(
                original,
                assertThrows(
                        ApiException.class,
                        () ->
                                database.transaction(
                                        tx -> {
                                            throw original;
                                        })));
        assertArrayEquals(new Throwable[] {rollbackError}, original.getSuppressed());
        verify(connection, never()).setAutoCommit(true);
        verify(connection).close();
        database.read(tx -> null);
        verify(source, times(2)).getConnection();
    }

    @Test
    void bindingFailureClosesStatementAndKeepsTheSqlException() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement("SELECT ?")).thenReturn(statement);
        SQLException original = new SQLException("binding failed");
        doThrow(original).when(statement).setObject(1, "value");
        assertSame(
                original,
                assertThrows(
                        SQLException.class,
                        () -> Database.prepare(connection, "SELECT ?", "value")));
        verify(statement).close();
    }

    @Test
    void nestedTransactionsCommitOnce() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection c = mock(Connection.class);
        when(source.getConnection()).thenReturn(c);
        Database db = new Database(source);
        int result =
                db.transaction(
                        tx ->
                                db.transaction(
                                        nested -> {
                                            assertSame(tx, nested);
                                            return 7;
                                        }));
        assertEquals(7, result);
        verify(c).commit();
        verify(c, never()).rollback();
        verify(source).getConnection();
    }

    @Test
    void failureRollsBackAndCleansThreadState() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection c = mock(Connection.class);
        when(source.getConnection()).thenReturn(c);
        Database db = new Database(source);
        assertThrows(
                ApiException.class,
                () ->
                        db.transaction(
                                tx -> {
                                    throw new ApiException(409, "conflict");
                                }));
        verify(c).rollback();
        verify(c, never()).commit();
        db.read(tx -> null);
        verify(source, times(2)).getConnection();
    }

    @Test
    void caughtNestedFailureStillForcesOuterRollback() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection c = mock(Connection.class);
        when(source.getConnection()).thenReturn(c);
        Database db = new Database(source);
        assertThrows(
                IllegalStateException.class,
                () ->
                        db.transaction(
                                tx -> {
                                    assertThrows(
                                            ApiException.class,
                                            () ->
                                                    db.transaction(
                                                            nested -> {
                                                                throw new ApiException(
                                                                        409, "conflict");
                                                            }));
                                    return "must not commit";
                                }));
        verify(c).rollback();
        verify(c, never()).commit();
    }

    @Test
    void nestedReadsReuseTheBorrowedConnectionAndBlockExternalHttp() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection c = mock(Connection.class);
        when(source.getConnection()).thenReturn(c);
        Database db = new Database(source);
        db.read(
                outer -> {
                    db.read(
                            inner -> {
                                assertSame(outer, inner);
                                assertThrows(IllegalStateException.class, db::requireNoTransaction);
                                return null;
                            });
                    return null;
                });
        verify(source).getConnection();
        verify(c).close();
        assertDoesNotThrow(db::requireNoTransaction);
    }

    @Test
    void parametersNeverEnterSqlString() throws Exception {
        Connection c = mock(Connection.class);
        PreparedStatement p = mock(PreparedStatement.class);
        when(c.prepareStatement("SELECT id FROM users WHERE email=?")).thenReturn(p);
        String malicious = "' OR 1=1 --";
        Database.prepare(c, "SELECT id FROM users WHERE email=?", malicious);
        verify(p).setObject(1, malicious);
        verify(c).prepareStatement("SELECT id FROM users WHERE email=?");
    }
}
