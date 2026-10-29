package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mathio.lms.DAO.Database;
import com.mathio.lms.model.*;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

class DetachedIdempotencyTest {
    @Test
    void durablePhaseCResponseSurvivesDisconnectAndReplaysWithoutRepeatingTheWrite()
            throws Exception {
        DataSource ds = mock(DataSource.class);
        AtomicInteger borrowed = new AtomicInteger();
        when(ds.getConnection())
                .thenAnswer(
                        inv -> {
                            borrowed.incrementAndGet();
                            Connection c = mock(Connection.class);
                            doAnswer(
                                            x -> {
                                                borrowed.decrementAndGet();
                                                return null;
                                            })
                                    .when(c)
                                    .close();
                            return c;
                        });
        Database db = new Database(ds);
        Map<String, Object> saved = new HashMap<>();
        Actor actor = new Actor(1, "STUDENT", 1);
        AtomicInteger writes = new AtomicInteger();
        try (MockedStatic<Database> sql = mockStatic(Database.class)) {
            sql.when(() -> Database.one(any(), anyString(), any(Object[].class)))
                    .thenAnswer(inv -> saved.isEmpty() ? null : new HashMap<>(saved));
            sql.when(() -> Database.update(any(), anyString(), any(Object[].class)))
                    .thenAnswer(
                            inv -> {
                                String query = inv.getArgument(1);
                                Object[] a = (Object[]) inv.getRawArguments()[2];
                                if (query.startsWith("INSERT idempotency_keys")) {
                                    saved.put("route", a[2]);
                                    saved.put("fingerprint", a[3]);
                                    saved.put("status", "PROCESSING");
                                    saved.put("lease_token", a[4]);
                                    saved.put("live", 1);
                                }
                                if (query.startsWith(
                                        "UPDATE idempotency_keys SET status='COMPLETED'")) {
                                    if (!Objects.equals(a[3], saved.get("lease_token"))
                                            || !"PROCESSING".equals(saved.get("status"))) return 0;
                                    saved.put("status", "COMPLETED");
                                    saved.put("response_json", a[0]);
                                }
                                if (query.startsWith("UPDATE idempotency_keys SET status='FAILED'")
                                        && "PROCESSING".equals(saved.get("status")))
                                    saved.put("status", "FAILED");
                                return 1;
                            });
            var service = new IdempotencyService(db);
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            service.execute(
                                    actor,
                                    "long-request-123456",
                                    "POST /ai/chat",
                                    "{}",
                                    () -> {
                                        assertEquals(0, borrowed.get());
                                        db.requireNoTransaction();
                                        db.transaction(
                                                c -> {
                                                    writes.incrementAndGet();
                                                    IdempotencyService.completeInTransaction(
                                                            c, Map.of("conversationId", 8));
                                                    return null;
                                                });
                                        throw new IllegalStateException(
                                                "Disconnected after commit");
                                    }));
            assertEquals("COMPLETED", saved.get("status"));
            assertEquals(1, writes.get());
            StoredResponse replay =
                    new IdempotencyService(db)
                            .execute(
                                    actor,
                                    "long-request-123456",
                                    "POST /ai/chat",
                                    "{}",
                                    () -> {
                                        fail("The action must not run after restart/retry");
                                        return null;
                                    });
            assertEquals(200, replay.getStatus());
            assertEquals(8, Json.parse(replay.getBody()).path("conversationId").asLong());
            assertEquals(
                    409,
                    assertThrows(
                                    ApiException.class,
                                    () ->
                                            service.execute(
                                                    actor,
                                                    "long-request-123456",
                                                    "POST /ai/chat",
                                                    "changed",
                                                    () -> null))
                            .status);
            assertEquals(0, borrowed.get());
        }
    }

    @Test
    void longAiOperationIsRejectedWhenInvokedUnderAnOuterTransaction() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenReturn(mock(Connection.class));
        Database db = new Database(ds);
        assertThrows(
                IllegalStateException.class,
                () ->
                        db.transaction(
                                c -> {
                                    db.requireNoTransaction();
                                    return null;
                                }));
    }
}
