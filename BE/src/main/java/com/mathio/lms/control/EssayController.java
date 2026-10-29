package com.mathio.lms.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Nhận request và trả JSON cho Essay; giữ nguyên đường dẫn API. */
@WebServlet(
        name = "EssayController",
        urlPatterns = {"/api/essays", "/api/essays/*", "/api/files/*", "/api/exam-files/*"})
@jakarta.servlet.annotation.MultipartConfig(maxFileSize = 8 * 1024 * 1024, maxRequestSize = 12_000_000, fileSizeThreshold = 65536)
public class EssayController extends ApiController {
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
        if (path.matches("/essays/\\d+/grade") && method.equals("POST")) {
            writeJson(
                    response,
                    application(request)
                            .essayService
                            .gradeEssay(
                                    currentUser(request),
                                    number(path.split("/")[2]),
                                    requestBody(request)));
            return;
        }
        if (path.matches("/exam-files/\\d+") && method.equals("GET")) {
            var file =
                    application(request)
                            .examService
                            .getFile(currentUser(request), number(path.split("/")[2]));
            response.setContentType(file.get("mime").toString());
            response.setHeader("Content-Disposition", "inline");
            response.getOutputStream().write((byte[]) file.get("bytes"));
            return;
        }
        if (path.equals("/essays")) {
            handleEssays(request, response);
            return;
        }
        if (path.matches("/essays/\\d+")) {
            handleEssay(request, response);
            return;
        }
        if (path.matches("/files/\\d+") && method.equals("GET")) {
            showFile(request, response);
            return;
        }
        throw new ApiException(404, "API không tồn tại.");
    }

    private void handleEssays(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        String method = request.getMethod();
        Object result = null;
        if (method.equals("GET"))
            result =
                    paginated(request)
                            ? app.essayService.getEssays(
                                    currentUser, offset(request), limit(request))
                            : app.essayService.getEssays(currentUser);
        else if (method.equals("POST")) {
            result = app.essayService.submitEssay(currentUser, input);
            response.setStatus(201);
        } else throw new ApiException(405, "Phương thức không hỗ trợ.");
        writeJson(response, result);
    }

    private void handleEssay(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        String method = request.getMethod();
        String path = getPath(request);
        Object result = null;
        long id = number(path.substring(8));
        if (method.equals("GET")) result = app.essayService.getEssayById(currentUser, id);
        else if (method.equals("POST"))
            throw new ApiException(409, "Bài đã nộp. Máy chủ quản lý điểm và trạng thái.");
        else throw new ApiException(405, "Phương thức không hỗ trợ.");
        writeJson(response, result);
    }

    private void showFile(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        boolean write = isWrite(request);
        String path = getPath(request);
        var f = app.essayService.getFile(currentUser, number(path.substring(7)));
        response.setContentType(f.get("mime").toString());
        response.setHeader("Content-Disposition", "inline");
        response.getOutputStream().write((byte[]) f.get("bytes"));
        return;
    }
}
