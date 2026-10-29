package com.mathio.lms.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Nhận request và trả JSON cho Configuration; giữ nguyên đường dẫn API. */
@WebServlet(
        name = "ConfigurationController",
        urlPatterns = {"/api/admin/config/*"})
public class ConfigurationController extends ApiController {
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
        if (path.equals("/admin/config/gemini")) {
            handleGeminiConfiguration(request, response);
            return;
        }
        if (path.equals("/admin/config/gemini/test") && method.equals("POST")) {
            testGemini(request, response);
            return;
        }
        if (path.equals("/admin/config/prompts")) {
            handlePrompts(request, response);
            return;
        }
        throw new ApiException(404, "API không tồn tại.");
    }

    private void handleGeminiConfiguration(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        String method = request.getMethod();
        Object result = null;
        if (method.equals("GET")) result = app.configurationService.getConfiguration(currentUser);
        else if (method.equals("POST"))
            result = app.configurationService.saveConfiguration(currentUser, input);
        else throw new ApiException(405, "Phương thức không hỗ trợ.");
        writeJson(response, result);
    }

    private void testGemini(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        Object result = app.aiService.testConnection(currentUser, input);
        writeJson(response, result);
    }

    private void handlePrompts(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        String method = request.getMethod();
        Object result = null;
        if (method.equals("GET")) result = app.configurationService.getPrompts(currentUser);
        else if (method.equals("POST")) {
            app.configurationService.savePrompts(currentUser, input);
            result = app.configurationService.getPrompts(currentUser);
        } else throw new ApiException(405, "Phương thức không hỗ trợ.");
        writeJson(response, result);
    }
}
