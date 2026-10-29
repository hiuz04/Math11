package com.mathio.lms.control;

import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/** Nhận request và trả JSON cho Reporting; giữ nguyên đường dẫn API. */
@WebServlet(
        name = "ReportingController",
        urlPatterns = {
            "/api/dashboard",
            "/api/search",
            "/api/analytics",
            "/api/students/*",
            "/api/learning-history",
            "/api/notifications",
            "/api/notifications/*",
            "/api/admin/audit-logs"
        })
public class ReportingController extends ApiController {
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
        if (path.equals("/search") && method.equals("GET")) {
            writeJson(response, application(request).reportingDAO.getSearch(currentUser(request),
                    request.getParameter("q"), offset(request), limit(request)));
            return;
        }
        if (path.equals("/dashboard") && method.equals("GET")) {
            showDashboard(request, response);
            return;
        }
        if (path.equals("/analytics") && method.equals("GET")) {
            showAnalytics(request, response);
            return;
        }
        if (path.matches("/students/\\d+/statistics") && method.equals("GET")) {
            showStudentStatistics(request, response);
            return;
        }
        if (path.equals("/learning-history")) {
            showLearningHistory(request, response);
            return;
        }
        if (path.equals("/notifications") && method.equals("GET")) {
            showNotifications(request, response);
            return;
        }
        if (path.matches("/notifications/\\d+/read") && method.equals("POST")) {
            readNotification(request, response);
            return;
        }
        if (path.equals("/admin/audit-logs") && method.equals("GET")) {
            showAuditLogs(request, response);
            return;
        }
        throw new ApiException(404, "API không tồn tại.");
    }

    private void showDashboard(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        Object result = app.reportingDAO.getDashboard(currentUser);
        writeJson(response, result);
    }

    private void showAnalytics(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        Object result = app.reportingDAO.getAnalytics(currentUser);
        writeJson(response, result);
    }

    private void showStudentStatistics(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        String path = getPath(request);
        Object result = null;
        currentUser.requireStaff();
        result = app.reportingDAO.getStudentStatistics(currentUser, number(path.split("/")[2]));
        writeJson(response, result);
    }

    private void showLearningHistory(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        String method = request.getMethod();
        Object result = null;
        if (method.equals("GET"))
            result =
                    paginated(request)
                            ? app.reportingDAO.getLearningHistory(
                                    currentUser,
                                    queryId(request, "studentId"),
                                    offset(request),
                                    limit(request))
                            : app.reportingDAO.getLearningHistory(
                                    currentUser, queryId(request, "studentId"));
        else throw new ApiException(403, "Lịch sử và điểm chỉ được tạo từ nghiệp vụ máy chủ.");
        writeJson(response, result);
    }

    private void showNotifications(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        Object result = app.reportingDAO.getNotifications(currentUser);
        writeJson(response, result);
    }

    private void readNotification(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        String path = getPath(request);
        Object result = null;
        app.reportingDAO.markNotificationRead(currentUser, number(path.split("/")[2]));
        result = Map.of("saved", true);
        writeJson(response, result);
    }

    private void showAuditLogs(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        Object result = app.reportingDAO.getAuditLogs(currentUser);
        writeJson(response, result);
    }
}
