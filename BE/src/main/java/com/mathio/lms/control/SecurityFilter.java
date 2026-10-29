package com.mathio.lms.control;

import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;
import com.mathio.lms.model.Environment;
import com.mathio.lms.model.Json;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@WebFilter(urlPatterns = "/api/*")
public class SecurityFilter implements Filter {
    private static final Set<String> CORS_METHODS =
            Set.of("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS");
    private static final Set<String> CORS_HEADERS =
            Set.of("content-type", "x-csrf-token", "idempotency-key");

    @Override
    public void doFilter(
            ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "same-origin");
        String requestId = UUID.randomUUID().toString();
        response.setHeader("X-Request-ID", requestId);
        response.addHeader("Vary", "Origin");
        try {
            String origin = request.getHeader("Origin");
            boolean allowedOrigin =
                    origin != null
                            && !origin.equals("null")
                            && !origin.equals("*")
                            && Arrays.stream(
                                            Environment.get(
                                                            "APP_ORIGIN",
                                                            "http://localhost:8443,http://127.0.0.1:8443")
                                                    .split(","))
                                    .map(String::trim)
                                    .anyMatch(origin::equals);
            ApiException.require(
                    origin == null || allowedOrigin, 403, "Origin không được phép.");
            if (allowedOrigin) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Access-Control-Allow-Credentials", "true");
                response.setHeader("Access-Control-Expose-Headers", "X-Request-ID");
            }
            if (request.getMethod().equals("OPTIONS")) {
                String method = request.getHeader("Access-Control-Request-Method");
                String headers = request.getHeader("Access-Control-Request-Headers");
                ApiException.require(
                        allowedOrigin && method != null && CORS_METHODS.contains(method),
                        403,
                        "CORS preflight không được phép.");
                if (headers != null && !headers.isBlank()) {
                    ApiException.require(
                            Arrays.stream(headers.split(","))
                                    .map(String::trim)
                                    .map(header -> header.toLowerCase(Locale.ROOT))
                                    .allMatch(CORS_HEADERS::contains),
                            403,
                            "CORS header không được phép.");
                }
                response.addHeader("Vary", "Access-Control-Request-Method");
                response.addHeader("Vary", "Access-Control-Request-Headers");
                response.setHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token, Idempotency-Key");
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }
            ApiException.require(
                    request.getContentLengthLong() <= 12_000_000, 413, "Yêu cầu quá lớn.");
            String path = request.getRequestURI().substring(request.getContextPath().length());
            boolean safe = Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
            if (!path.equals("/api/auth/login") && !path.equals("/api/auth/csrf")) {
                HttpSession s = request.getSession(false);
                Actor actor = s == null ? null : (Actor) s.getAttribute("actor");
                try {
                    actor =
                            Application.get(request.getServletContext())
                                    .authService
                                    .validateSession(actor);
                } catch (ApiException e) {
                    if (s != null && e.status == 401) s.invalidate();
                    throw e;
                }
                request.setAttribute("actor", actor);
            }
            if (!safe) {
                HttpSession session = request.getSession(false);
                String expected = session == null ? null : (String) session.getAttribute("csrf"),
                        actual = request.getHeader("X-CSRF-Token");
                ApiException.require(
                        expected != null
                                && actual != null
                                && MessageDigest.isEqual(
                                        expected.getBytes(StandardCharsets.UTF_8),
                                        actual.getBytes(StandardCharsets.UTF_8)),
                        403,
                        "CSRF token không hợp lệ. Hãy tải lại trang.");
                // Unknown/chunked length is checked against actual bytes by ApiController.
                boolean hasBody = request.getContentLengthLong() > 0;
                ApiException.require(
                        !hasBody
                                || request.getContentType() != null
                                        && request.getContentType()
                                                .split(";", 2)[0]
                                                .trim()
                                                .toLowerCase(Locale.ROOT)
                                                .equals("application/json")
                                || request.getMethod().equals("POST")
                                        && MultipartContent.allowed(path.substring("/api".length()))
                                        && request.getContentType() != null
                                        && request.getContentType().split(";", 2)[0].trim().equalsIgnoreCase("multipart/form-data"),
                        400,
                        "Yêu cầu phải dùng application/json.");
            }
            chain.doFilter(request, response);
        } catch (ApiException e) {
            error(response, e.status, e.getMessage(), requestId);
        } catch (Exception e) {
            System.err.println(
                "[ERROR] Request " + requestId
                + " | " + request.getMethod()
                + " " + request.getRequestURI()
            );

            e.printStackTrace(System.err);

            error(
                response,
                500,
                "Không thể xử lý yêu cầu. Vui lòng thử lại.",
                requestId
            );
        }
    }

    private void error(HttpServletResponse response, int status, String message, String id)
            throws IOException {
        if (response.isCommitted()) return;
        response.resetBuffer();
        response.setStatus(status);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter()
                .write(
                        Json.stringify(
                                Map.of(
                                        "error",
                                        Map.of(
                                                "code",
                                                "HTTP_" + status,
                                                "message",
                                                message,
                                                "requestId",
                                                id))));
    }
}
