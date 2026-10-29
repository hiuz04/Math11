package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mathio.lms.DAO.*;
import com.mathio.lms.control.*;
import com.mathio.lms.model.*;

import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.*;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** Real Tomcat/filter/cookies/JSON transport; business services are explicit test fixtures. */
class HttpSecurityTest {
    @TempDir Path dir;
    HttpClient http;
    String base, token;

    HttpResponse<String> request(
            String method,
            String path,
            String body,
            String contentType,
            boolean csrf,
            String origin)
            throws Exception {
        var b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(8));
        if (contentType != null) b.header("Content-Type", contentType);
        if (csrf) b.header("X-CSRF-Token", token);
        if (origin != null) b.header("Origin", origin);
        return http.send(
                b.method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void postPutBodylessDeleteKeepCsrfOriginAndSessionRules() throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(dir.toString());
        tomcat.setPort(0);
        tomcat.getConnector();
        var context = tomcat.addContext("/Math11", dir.toString());
        context.setUseHttpOnly(true);
        var cookies = new Rfc6265CookieProcessor();
        cookies.setSameSiteCookies("lax");
        context.setCookieProcessor(cookies);
        Application app = new Application();
        app.authService = mock(AuthService.class);
        app.userDAO = mock(UserDAO.class);
        app.questionDAO = mock(QuestionDAO.class);
        app.examService = mock(ExamService.class);
        app.essayService = mock(EssayService.class);
        app.reportingDAO = mock(ReportingDAO.class);
        app.configurationService = mock(ConfigurationService.class);
        Actor admin = new Actor(1, "ADMIN", 1);
        when(app.authService.login(any())).thenReturn(admin);
        when(app.authService.validateSession(any()))
                .thenAnswer(
                        inv -> {
                            Actor a = inv.getArgument(0);
                            if (a == null) throw new ApiException(401, "Đăng nhập.");
                            return a;
                        });
        when(app.authService.getProfile(any())).thenReturn(Map.of("id", 1, "role", "ADMIN"));
        when(app.userDAO.saveUser(any(), anyLong(), any())).thenReturn(Map.of("id", 2));
        when(app.questionDAO.getQuestions(any())).thenReturn(Json.array());
        when(app.examService.getExams(any())).thenReturn(Json.array());
        when(app.reportingDAO.getDashboard(any())).thenReturn(Json.array());
        when(app.configurationService.getConfiguration(any()))
                .thenReturn(Map.of("enabled", false, "hasApiKey", false));
        context.getServletContext().setAttribute(Application.class.getName(), app);
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
            base = "http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/Math11/api";
            CookieManager jar = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            http =
                    HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(3))
                            .cookieHandler(jar)
                            .build();
            var first = request("GET", "/auth/csrf", null, null, false, null);
            assertEquals(200, first.statusCode());
            assertTrue(
                    first.headers().allValues("set-cookie").stream()
                            .anyMatch(s -> s.contains("HttpOnly") && s.contains("SameSite=Lax")));
            token = Json.parse(first.body()).path("csrfToken").asText();
            String before = jar.getCookieStore().getCookies().get(0).getValue();
            var login = request("POST", "/auth/login", "{}", "application/json", true, null);
            assertEquals(200, login.statusCode());
            token = Json.parse(login.body()).path("csrfToken").asText();
            assertNotEquals(before, jar.getCookieStore().getCookies().get(0).getValue());
            when(app.essayService.submitEssay(any(), any())).thenAnswer(inv -> {
                var input = (com.fasterxml.jackson.databind.JsonNode) inv.getArgument(1);
                assertEquals("Keep my text", input.path("content").path("text").asText());
                assertEquals(ImageStorage.decode(SecurityTest.png()).hash(), ImageStorage.decode(input.path("content").path("image").asText()).hash());
                return Map.of("id", 9);
            });
            String boundary = "mathio-test-boundary";
            var bytes = new java.io.ByteArrayOutputStream();
            bytes.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"payload\"\r\n\r\n{\"questionId\":7,\"content\":{\"text\":\"Keep my text\"}}\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"../../never-used.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            bytes.write(java.util.Base64.getDecoder().decode(SecurityTest.png().split(",", 2)[1]));
            bytes.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var upload = HttpRequest.newBuilder(URI.create(base + "/essays"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("X-CSRF-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build();
            assertEquals(201, http.send(upload, HttpResponse.BodyHandlers.ofString()).statusCode());
            String multipart = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1)
                    .replace("{\"questionId\":7,\"content\":", "{\"questionId\":7,\"answers\":{},\"essayAnswers\":{},\"content\":");
            when(app.examService.saveAnswers(any(), eq(5L), any(), eq(false))).thenAnswer(inv -> {
                var input = (com.fasterxml.jackson.databind.JsonNode) inv.getArgument(2);
                assertFalse(input.has("content"));
                assertFalse(input.has("questionId"));
                assertEquals("Keep my text", input.path("essayAnswers").path("7").path("content").path("text").asText());
                assertEquals(ImageStorage.decode(SecurityTest.png()).hash(), ImageStorage.decode(input.path("essayAnswers").path("7").path("content").path("image").asText()).hash());
                return Map.of("id", 5);
            });
            var examUpload = HttpRequest.newBuilder(URI.create(base + "/quiz-attempts/5/answers"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("X-CSRF-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1))).build();
            assertEquals(200, http.send(examUpload, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(400, request("POST", "/users", "invalid", "multipart/form-data; boundary=x", true, null).statusCode());
            assertEquals(
                    201,
                    request("POST", "/users", "{}", "application/json; charset=UTF-8", true, null)
                            .statusCode());
            assertEquals(
                    200,
                    request("PUT", "/users/2", "{}", "application/json", true, null).statusCode());
            assertEquals(204, request("DELETE", "/users/2", null, null, true, null).statusCode());
            var chunkedEmpty =
                    HttpRequest.newBuilder(URI.create(base + "/users/4"))
                            .timeout(Duration.ofSeconds(8))
                            .header("X-CSRF-Token", token)
                            .method(
                                    "DELETE",
                                    HttpRequest.BodyPublishers.ofInputStream(
                                            () -> new java.io.ByteArrayInputStream(new byte[0])))
                            .build();
            assertEquals(
                    204,
                    http.send(chunkedEmpty, HttpResponse.BodyHandlers.ofString()).statusCode());
            var chunkedBody =
                    HttpRequest.newBuilder(URI.create(base + "/users/4"))
                            .timeout(Duration.ofSeconds(8))
                            .header("X-CSRF-Token", token)
                            .method(
                                    "DELETE",
                                    HttpRequest.BodyPublishers.ofInputStream(
                                            () ->
                                                    new java.io.ByteArrayInputStream(
                                                            "{}"
                                                                    .getBytes(
                                                                            java.nio.charset
                                                                                    .StandardCharsets
                                                                                    .UTF_8))))
                            .build();
            assertEquals(
                    400, http.send(chunkedBody, HttpResponse.BodyHandlers.ofString()).statusCode());
            verify(app.userDAO).deleteUser(eq(admin), eq(2L));
            assertEquals(403, request("DELETE", "/users/3", null, null, false, null).statusCode());
            assertEquals(
                    403,
                    request("DELETE", "/users/3", null, null, true, "https://evil.example")
                            .statusCode());
            assertEquals(400, request("PUT", "/users/3", "{}", null, true, null).statusCode());
            assertEquals(
                    400,
                    request("POST", "/users", "{}", "application/jsonp", true, null).statusCode());
            assertEquals(
                    400,
                    request("DELETE", "/users/3", "malformed", "application/json", true, null)
                            .statusCode());
            verify(app.userDAO, never()).deleteUser(any(), eq(3L));
            for (String path :
                    new String[] {
                        "/auth/me", "/questions", "/exams", "/dashboard", "/admin/config/gemini"
                    })
                assertEquals(200, request("GET", path, null, null, false, null).statusCode());
            assertEquals(204, request("POST", "/auth/logout", null, null, true, null).statusCode());
            assertEquals(401, request("GET", "/auth/me", null, null, false, null).statusCode());
            assertEquals(401, request("POST", "/essays", "{}", "application/json", true, null).statusCode());
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }
    }
}
