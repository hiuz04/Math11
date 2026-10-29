package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mathio.lms.DAO.Database;
import com.mathio.lms.DAO.UserDAO;
import com.mathio.lms.control.Application;
import com.mathio.lms.control.SecurityFilter;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;
import com.mathio.lms.model.AuthService;
import com.mathio.lms.model.Json;

import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;

class TomcatSessionTest {
    @TempDir Path dir;

    @Test
    void realTomcatRotatesHttpOnlySessionChecksCsrfAndInvalidatesLogout() throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(dir.toString());
        tomcat.setPort(0);
        tomcat.getConnector();
        var context = tomcat.addContext("/mathio", dir.toString());
        Application app = new Application();
        app.authService = mock(AuthService.class);
        app.userDAO = new UserDAO(mock(Database.class));
        Actor student = new Actor(1, "STUDENT", 1);
        when(app.authService.login(any())).thenReturn(student);
        when(app.authService.validateSession(any()))
                .thenAnswer(
                        inv -> {
                            Actor a = inv.getArgument(0);
                            if (a == null) throw new ApiException(401, "Vui lòng đăng nhập.");
                            return a;
                        });
        when(app.authService.getProfile(any()))
                .thenReturn(
                        Map.of(
                                "id",
                                1,
                                "name",
                                "Test Student",
                                "email",
                                "student@test.vn",
                                "role",
                                "STUDENT"));
        context.getServletContext().setAttribute(Application.class.getName(), app);
        context.setUseHttpOnly(true);
        ApiContractTest.registerControllers(context);
        FilterDef def = new FilterDef();
        def.setFilterName("security");
        def.setFilter(new SecurityFilter());
        context.addFilterDef(def);
        FilterMap map = new FilterMap();
        map.setFilterName("security");
        map.addURLPattern("/api/*");
        context.addFilterMap(map);
        tomcat.start();
        try {
            String base =
                    "http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/mathio/api";
            CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
            assertEquals(
                    401,
                    http.send(
                                    HttpRequest.newBuilder(URI.create(base + "/auth/me")).build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .statusCode());
            var csrf =
                    http.send(
                            HttpRequest.newBuilder(URI.create(base + "/auth/csrf")).build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, csrf.statusCode());
            assertTrue(
                    csrf.headers().allValues("set-cookie").stream()
                            .anyMatch(v -> v.toLowerCase().contains("httponly")));
            String token = Json.parse(csrf.body()).path("csrfToken").asText();
            String initial = cookies.getCookieStore().getCookies().get(0).getValue();
            var denied =
                    http.send(
                            HttpRequest.newBuilder(URI.create(base + "/auth/login"))
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(403, denied.statusCode());
            verify(app.authService, never()).login(any());
            var login =
                    http.send(
                            HttpRequest.newBuilder(URI.create(base + "/auth/login"))
                                    .header("Content-Type", "application/json")
                                    .header("X-CSRF-Token", token)
                                    .POST(
                                            HttpRequest.BodyPublishers.ofString(
                                                    "{\"role\":\"ADMIN\"}"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, login.statusCode());
            assertEquals("STUDENT", Json.parse(login.body()).path("user").path("role").asText());
            assertNotEquals(initial, cookies.getCookieStore().getCookies().get(0).getValue());
            String next = Json.parse(login.body()).path("csrfToken").asText();
            assertEquals(
                    200,
                    http.send(
                                    HttpRequest.newBuilder(URI.create(base + "/profile")).build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .statusCode());
            assertEquals(
                    405,
                    http.send(
                                    HttpRequest.newBuilder(URI.create(base + "/users/%37")).build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .statusCode());
            assertEquals(
                    200,
                    http.send(
                                    HttpRequest.newBuilder(URI.create(base + "/auth/me")).build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .statusCode());
            assertEquals(
                    403,
                    http.send(
                                    HttpRequest.newBuilder(URI.create(base + "/users?role=ADMIN"))
                                            .build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .statusCode());
            assertEquals(
                    403,
                    http.send(
                                    HttpRequest.newBuilder(URI.create(base + "/auth/logout"))
                                            .header("Content-Type", "application/json")
                                            .header("X-CSRF-Token", next)
                                            .header("Origin", "https://evil.example")
                                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                                            .build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .statusCode());
            assertEquals(
                    204,
                    http.send(
                                    HttpRequest.newBuilder(URI.create(base + "/auth/logout"))
                                            .header("Content-Type", "application/json")
                                            .header("X-CSRF-Token", next)
                                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                                            .build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .statusCode());
            assertEquals(
                    401,
                    http.send(
                                    HttpRequest.newBuilder(URI.create(base + "/auth/me")).build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .statusCode());
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }
    }
}
