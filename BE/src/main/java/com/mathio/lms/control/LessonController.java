package com.mathio.lms.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Nhận request và trả JSON cho Lesson; giữ nguyên đường dẫn API. */
@WebServlet(
        name = "LessonController",
        urlPatterns = {"/api/chapters", "/api/topics", "/api/lessons", "/api/lessons/*"})
public class LessonController extends ApiController {
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
        if (path.equals("/chapters") && method.equals("GET")) {
            showChapterList(request, response);
            return;
        }
        if (path.equals("/topics") && method.equals("GET")) {
            showTopicList(request, response);
            return;
        }
        if (path.equals("/lessons")) {
            handleLessons(request, response);
            return;
        }
        if (path.matches("/lessons/\\d+/progress") && method.equals("POST")) {
            updateLessonProgress(request, response);
            return;
        }
        if (path.matches("/lessons/\\d+")) {
            handleLesson(request, response);
            return;
        }
        throw new ApiException(404, "API không tồn tại.");
    }

    private void showChapterList(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        Object result = app.catalogDAO.getChapters(currentUser, queryId(request, "courseId"));
        writeJson(response, result);
    }

    private void showTopicList(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Object result = app.catalogDAO.getTopics();
        writeJson(response, result);
    }

    private void handleLessons(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        String method = request.getMethod();
        Object result = null;
        if (method.equals("GET"))
            result =
                    paginated(request)
                            ? app.catalogDAO.getLessons(
                                    currentUser,
                                    queryId(request, "chapterId"),
                                    offset(request),
                                    limit(request))
                            : app.catalogDAO.getLessons(currentUser, queryId(request, "chapterId"));
        else if (method.equals("POST")) {
            result = app.catalogDAO.saveLesson(currentUser, 0, input);
            response.setStatus(201);
        } else throw new ApiException(405, "Phương thức không hỗ trợ.");
        writeJson(response, result);
    }

    private void updateLessonProgress(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        String path = getPath(request);
        Object result =
                app.catalogDAO.updateProgress(currentUser, number(path.split("/")[2]), input);
        writeJson(response, result);
    }

    private void handleLesson(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        String method = request.getMethod();
        boolean write = isWrite(request);
        String path = getPath(request);
        Object result = null;
        long id = number(path.substring(9));
        if (method.equals("GET")) result = app.catalogDAO.getLessonById(currentUser, id);
        else if (write) result = app.catalogDAO.saveLesson(currentUser, id, input);
        else if (method.equals("DELETE")) {
            app.catalogDAO.deleteLesson(currentUser, id);
            response.setStatus(204);
            return;
        } else throw new ApiException(405, "Phương thức không hỗ trợ.");
        writeJson(response, result);
    }
}
