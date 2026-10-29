package com.mathio.lms.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;
import com.mathio.lms.model.Environment;
import com.mathio.lms.model.Secrets;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Nhận request và trả JSON cho Auth; giữ nguyên đường dẫn API. */
@WebServlet(
        name = "AuthController",
        urlPatterns = {"/api/auth/*", "/api/profile"})
public class AuthController extends ApiController {
    private final Map<String, long[]> loginAttempts = new ConcurrentHashMap<>();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        handleRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        handleRequest(request, response);
    }

    @Override
    protected void handleRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String path = getPath(request);
        String method = request.getMethod();
        if (path.equals("/auth/csrf") && method.equals("GET")) {
            getCsrfToken(request, response);
            return;
        }
        if (path.equals("/auth/login") && method.equals("POST")) {
            login(request, response);
            return;
        }
        if (path.equals("/auth/me") && method.equals("GET")) {
            showCurrentUser(request, response);
            return;
        }
        if (path.equals("/auth/logout") && method.equals("POST")) {
            logout(request, response);
            return;
        }
        if (path.equals("/auth/change-password") && method.equals("POST")) {
            changePassword(request, response);
            return;
        }
        if (path.equals("/profile")) {
            handleProfile(request, response);
            return;
        }
        throw new ApiException(404, "API không tồn tại.");
    }

    private void getCsrfToken(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Object result = null;
        HttpSession session = request.getSession(true);
        if (session.getAttribute("csrf") == null) session.setAttribute("csrf", Secrets.token());
        session.setMaxInactiveInterval(
                Integer.parseInt(Environment.get("SESSION_TIMEOUT_MINUTES", "30")) * 60);
        result = Map.of("csrfToken", session.getAttribute("csrf"));
        writeJson(response, result);
    }

    private void login(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        JsonNode input = requestBody(request);
        Object result = null;
        long now = System.currentTimeMillis();
        if (loginAttempts.size() > 10000)
            loginAttempts.entrySet().removeIf(e -> now - e.getValue()[0] > 600000);
        long[] rate =
                loginAttempts.compute(
                        request.getRemoteAddr(),
                        (key, old) ->
                                old == null || now - old[0] > 600000
                                        ? new long[] {now, 1}
                                        : new long[] {old[0], old[1] + 1});
        ApiException.require(rate[1] <= 20, 429, "Quá nhiều lần đăng nhập. Hãy thử lại sau.");
        Actor actor = app.authService.login(input);
        request.changeSessionId();
        request.getSession().setAttribute("actor", actor);
        request.getSession().setAttribute("csrf", Secrets.token());
        result =
                Map.of(
                        "user",
                        app.authService.getProfile(actor),
                        "csrfToken",
                        request.getSession().getAttribute("csrf"));
        writeJson(response, result);
    }

    private void showCurrentUser(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        Object result =
                Map.of(
                        "user",
                        app.authService.getProfile(currentUser),
                        "csrfToken",
                        request.getSession().getAttribute("csrf"));
        writeJson(response, result);
    }

    private void logout(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        request.getSession().invalidate();
        response.setStatus(204);
        return;
    }

    private void changePassword(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        app.authService.changePassword(currentUser, input);
        request.getSession().invalidate();
        response.setStatus(204);
        return;
    }

    private void handleProfile(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        String method = request.getMethod();
        boolean write = isWrite(request);
        Object result = null;
        if (method.equals("GET")) result = app.authService.getProfile(currentUser);
        else if (write) {
            app.authService.updateProfile(currentUser, input);
            result = app.authService.getProfile(currentUser);
        } else throw new ApiException(405, "Phương thức không hỗ trợ.");
        writeJson(response, result);
    }
}
