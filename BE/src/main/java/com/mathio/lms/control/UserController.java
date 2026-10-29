package com.mathio.lms.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Nhận request và trả JSON cho User; giữ nguyên đường dẫn API. */
@WebServlet(
        name = "UserController",
        urlPatterns = {"/api/users", "/api/users/*"})
public class UserController extends ApiController {
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
        if (path.equals("/users")) {
            handleUsers(request, response);
            return;
        }
        if (path.matches("/users/\\d+")) {
            handleUser(request, response);
            return;
        }
        throw new ApiException(404, "API không tồn tại.");
    }

    private void handleUsers(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        String method = request.getMethod();
        Object result = null;
        if (method.equals("GET"))
            result =
                    paginated(request)
                            ? app.userDAO.getUsers(currentUser, offset(request), limit(request))
                            : app.userDAO.getUsers(currentUser);
        else if (method.equals("POST")) {
            result = app.userDAO.saveUser(currentUser, 0, input);
            response.setStatus(201);
        } else throw new ApiException(405, "Phương thức không hỗ trợ.");
        writeJson(response, result);
    }

    private void handleUser(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        String method = request.getMethod();
        boolean write = isWrite(request);
        String path = getPath(request);
        Object result = null;
        long id = number(path.substring(7));
        if (write) result = app.userDAO.saveUser(currentUser, id, input);
        else if (method.equals("DELETE")) {
            app.userDAO.deleteUser(currentUser, id);
            response.setStatus(204);
            return;
        } else throw new ApiException(405, "Phương thức không hỗ trợ.");
        writeJson(response, result);
    }
}
