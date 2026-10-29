package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;

import com.mathio.lms.model.Json;

import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;

/** Deploys the packaged WAR and discovers its actual annotations/listener; no SQL/Gemini writes. */
@EnabledIfEnvironmentVariable(named = "RUN_WAR_DEPLOYMENT", matches = "true")
class WarDeploymentTest {
    @TempDir Path dir;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void packagedWarStartsWithItsRealServletsCorsAndSessionCookies(boolean production) throws Exception {
        String contextPath = production ? "" : "/Math11";
        Path war = Path.of("target", "Math11.war").toAbsolutePath();
        assertTrue(Files.isRegularFile(war), "Build the WAR with mvn package first");
        Path config = dir.resolve("test.env");
        Files.writeString(
                config,
                "APP_ENV=" + (production ? "production" : "development") + "\n"
                    + "APP_ORIGIN=https://frontend.example\n"
                    + "DB_URL=jdbc:sqlserver://127.0.0.1:1;databaseName=unconfigured_test;encrypt=true;trustServerCertificate=false;loginTimeout=1\n"
                    + "DB_USER=fixture\n"
                    + "DB_PASSWORD=fixture\n"
                    + "STORAGE_DIR=private-storage\n");
        String previous = System.getProperty("MATHIO_ENV_FILE");
        System.setProperty("MATHIO_ENV_FILE", config.toString());
        Files.createDirectories(dir.resolve("tomcat/webapps"));
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(dir.resolve("tomcat").toString());
        tomcat.setPort(0);
        tomcat.getConnector();
        tomcat.getServer()
                .addLifecycleListener(new org.apache.catalina.core.JreMemoryLeakPreventionListener());
        tomcat.setAddDefaultWebXmlToWebapp(false);
        var context = tomcat.addWebapp(contextPath, war.toString());
        ((StandardJarScanner) context.getJarScanner()).setScanClassPath(false);
        // REST-only WAR: omit Jasper/JSP defaults from the embedded test container.
        Path defaults = dir.resolve("defaults.xml");
        Files.writeString(
                defaults,
                "<web-app xmlns=\"https://jakarta.ee/xml/ns/jakartaee\""
                    + " version=\"6.0\"><session-config><cookie-config><http-only>true</http-only></cookie-config></session-config></web-app>");
        for (var listener : context.findLifecycleListeners())
            if (listener instanceof org.apache.catalina.startup.ContextConfig configuration)
                configuration.setDefaultWebXml(defaults.toString());
        try {
            tomcat.start();
            assertTrue(context.getState().isAvailable());
            assertEquals("QuestionController", context.findServletMapping("/api/questions"));
            assertEquals("ExamController", context.findServletMapping("/api/quiz-attempts/*"));
            assertEquals("ReportingController", context.findServletMapping("/api/search"));
            // Tomcat reads @MultipartConfig when it first loads each servlet.
            for (String name : new String[] {"ExamController", "EssayController", "AiController"}) {
                var wrapper = (org.apache.catalina.Wrapper) context.findChild(name);
                wrapper.load();
                assertNotNull(wrapper.getMultipartConfigElement(), name);
                assertEquals(8 * 1024 * 1024, wrapper.getMultipartConfigElement().getMaxFileSize());
            }
            String base =
                    "http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + contextPath + "/api";
            HttpClient client =
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            var csrf =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/auth/csrf"))
                                    .header("Origin", "https://frontend.example")
                                    .timeout(Duration.ofSeconds(15))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, csrf.statusCode());
            assertFalse(Json.parse(csrf.body()).path("csrfToken").asText().isBlank());
            assertTrue(
                    csrf.headers().allValues("set-cookie").stream()
                            .anyMatch(s -> s.contains("HttpOnly")
                                    && s.contains(production ? "SameSite=None" : "SameSite=Lax")
                                    && (!production || s.contains("Secure"))));
            assertEquals("https://frontend.example", csrf.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
            assertEquals("true", csrf.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow());
            var preflight = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/users"))
                            .header("Origin", "https://frontend.example")
                            .header("Access-Control-Request-Method", "PUT")
                            .header("Access-Control-Request-Headers", "content-type,x-csrf-token,idempotency-key")
                            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(204, preflight.statusCode());
            assertEquals("https://frontend.example", preflight.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
            assertEquals(
                    401,
                    client.send(
                                    HttpRequest.newBuilder(URI.create(base + "/auth/me"))
                                            .timeout(Duration.ofSeconds(15))
                                            .build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .statusCode());
            assertEquals(
                    403,
                    client.send(
                                    HttpRequest.newBuilder(URI.create(base + "/auth/login"))
                                            .timeout(Duration.ofSeconds(15))
                                            .header("Content-Type", "application/json")
                                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                                            .build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .statusCode());
        } finally {
            tomcat.stop();
            tomcat.destroy();
            if (previous == null) System.clearProperty("MATHIO_ENV_FILE");
            else System.setProperty("MATHIO_ENV_FILE", previous);
        }
    }
}
