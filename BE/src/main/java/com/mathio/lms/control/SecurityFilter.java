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
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@WebFilter(urlPatterns = "/api/*")
public class SecurityFilter implements Filter {
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
        try {
            ApiException.require(
                    request.getContentLengthLong() <= 12_000_000, 413, "Yêu cầu quá lớn.");
            String path = request.getRequestURI().substring(request.getContextPath().length());
            boolean safe = Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
            ApiException.require(
                    !request.getMethod().equals("OPTIONS"),
                    403,
                    "Chỉ hỗ trợ truy cập cùng origin qua proxy.");
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
                String origin = request.getHeader("Origin");
                Set<String> origins =
                        new HashSet<>(
                                Arrays.asList(
                                        Environment.get(
                                                        "APP_ORIGIN",
                                                        "http://localhost:8443,http://127.0.0.1:8443")
                                                .split(",")));
                ApiException.require(
                        origin == null || origins.contains(origin), 403, "Origin không được phép.");
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
            request.getServletContext().log("Request " + requestId + " failed", e);
            error(response, 500, "Không thể xử lý yêu cầu. Vui lòng thử lại.", requestId);
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
