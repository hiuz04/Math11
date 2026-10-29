package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mathio.lms.DAO.Database;
import com.mathio.lms.DAO.UserDAO;
import com.mathio.lms.control.Application;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

class ApiIdempotencyTest {
    @Test
    void controllerReplaysTheSameStatusAndJsonAndRejectsChangedPayload() throws Exception {
        Connection connection = mock(Connection.class);
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenReturn(connection);
        Application app = new Application();
        app.database = new Database(source);
        app.userDAO = mock(UserDAO.class);
        when(app.userDAO.saveUser(any(), eq(0L), any())).thenReturn(Map.of("id", 8));
        Map<String, Object> saved = new HashMap<>();
        Actor actor = new Actor(1, "ADMIN", 1);
        try (MockedStatic<Database> sql = mockStatic(Database.class)) {
            sql.when(() -> Database.one(any(), anyString(), any(Object[].class)))
                    .thenAnswer(inv -> saved.isEmpty() ? null : saved);
            sql.when(() -> Database.update(any(), anyString(), any(Object[].class)))
                    .thenAnswer(
                            inv -> {
                                if (((String) inv.getArgument(1))
                                        .startsWith("INSERT idempotency_keys")) {
                                    Object[] args = (Object[]) inv.getRawArguments()[2];
                                    saved.put("route", args[2]);
                                    saved.put("fingerprint", args[3]);
                                    saved.put("response_json", args[4]);
                                }
                                return 1;
                            });
            List<String> bodies = new ArrayList<>();
            for (int repeat = 0; repeat < 2; repeat++) {
                HttpServletRequest request =
                        ApiContractTest.request(
                                app, actor, "POST", "/users", "{\"name\":\"Saved\"}");
                when(request.getHeader("Idempotency-Key")).thenReturn("same-key-123456789");
                HttpServletResponse response = mock(HttpServletResponse.class);
                int[] status = {200};
                doAnswer(
                                inv -> {
                                    status[0] = inv.getArgument(0);
                                    return null;
                                })
                        .when(response)
                        .setStatus(anyInt());
                when(response.getStatus()).thenAnswer(inv -> status[0]);
                StringWriter output = new StringWriter();
                when(response.getWriter()).thenReturn(new PrintWriter(output));
                ApiContractTest.controller("/users").service(request, response);
                assertEquals(201, status[0]);
                bodies.add(output.toString());
            }
            assertEquals(List.of("{\"id\":8}", "{\"id\":8}"), bodies);
            HttpServletRequest changed =
                    ApiContractTest.request(app, actor, "POST", "/users", "{\"name\":\"Changed\"}");
            when(changed.getHeader("Idempotency-Key")).thenReturn("same-key-123456789");
            assertEquals(
                    409,
                    assertThrows(
                                    ApiException.class,
                                    () ->
                                            ApiContractTest.controller("/users")
                                                    .service(
                                                            changed,
                                                            mock(HttpServletResponse.class)))
                            .status);
            verify(app.userDAO, times(1)).saveUser(eq(actor), eq(0L), any());
            verify(connection, times(2)).commit();
            verify(connection).rollback();
        }
    }
}
