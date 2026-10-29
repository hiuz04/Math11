package com.mathio.lms.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/** Nhận request và trả JSON cho Course; giữ nguyên đường dẫn API. */
@WebServlet(
        name = "CourseController",
        urlPatterns = {"/api/courses", "/api/courses/*"})
public class CourseController extends ApiController {
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
        if (path.equals("/courses")) {
            handleCourses(request, response);
            return;
        }
        if (path.matches("/courses/\\d+/enrollments") && method.equals("POST")) {
            createEnrollment(request, response);
            return;
        }
        if (path.matches("/courses/\\d+")) {
            handleCourse(request, response);
            return;
        }
        throw new ApiException(404, "API không tồn tại.");
    }

    private void handleCourses(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        String method = request.getMethod();
        Object result = null;
        if (method.equals("GET"))
            result =
                    paginated(request)
                            ? app.catalogDAO.getCourses(
                                    currentUser, offset(request), limit(request))
                            : app.catalogDAO.getCourses(currentUser);
        else if (method.equals("POST")) {
            result = app.catalogDAO.saveCourse(currentUser, 0, input);
            response.setStatus(201);
        } else throw new ApiException(405, "Phương thức không hỗ trợ.");
        writeJson(response, result);
    }

    private void createEnrollment(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        String path = getPath(request);
        Object result = null;
        app.catalogDAO.enrollStudent(currentUser, number(path.split("/")[2]), input);
        result = Map.of("saved", true);
        writeJson(response, result);
    }

    private void handleCourse(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        String method = request.getMethod();
        boolean write = isWrite(request);
        String path = getPath(request);
        Object result = null;
        long id = number(path.substring(9));
        if (write) result = app.catalogDAO.saveCourse(currentUser, id, input);
        else if (method.equals("DELETE")) {
            app.catalogDAO.deleteCourse(currentUser, id);
            response.setStatus(204);
            return;
        } else throw new ApiException(405, "Phương thức không hỗ trợ.");
        writeJson(response, result);
    }
}
