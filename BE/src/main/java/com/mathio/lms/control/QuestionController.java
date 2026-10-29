package com.mathio.lms.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Nhận request và trả JSON cho Question; giữ nguyên đường dẫn API. */
@WebServlet(
        name = "QuestionController",
        urlPatterns = {"/api/questions", "/api/questions/*", "/api/exercises"})
public class QuestionController extends ApiController {
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
        if (path.equals("/questions") && method.equals("GET")) {
            showQuestionList(request, response);
            return;
        }
        if (path.equals("/questions/bulk") && method.equals("POST")) {
            createQuestions(request, response);
            return;
        }
        if (path.matches("/questions/\\d+")) {
            handleQuestion(request, response);
            return;
        }
        if (path.equals("/exercises") && method.equals("GET")) {
            showExerciseList(request, response);
            return;
        }
        throw new ApiException(404, "API không tồn tại.");
    }

    private void showQuestionList(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        Object result =
                paginated(request)
                        ? app.questionDAO.getQuestions(currentUser, offset(request), limit(request))
                        : app.questionDAO.getQuestions(currentUser);
        writeJson(response, result);
    }

    private void createQuestions(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        Object result = app.questionDAO.saveQuestions(currentUser, input);
        writeJson(response, result);
    }

    private void handleQuestion(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        String method = request.getMethod();
        String path = getPath(request);
        Object result = null;
        long id = number(path.substring(11));
        if (method.equals("GET")) result = app.questionDAO.getQuestionById(currentUser, id);
        else if (method.equals("DELETE")) {
            app.questionDAO.deleteQuestion(currentUser, id);
            response.setStatus(204);
            return;
        } else throw new ApiException(405, "Phương thức không hỗ trợ.");
        writeJson(response, result);
    }

    private void showExerciseList(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        Object result = app.questionDAO.getExercises(currentUser);
        writeJson(response, result);
    }
}
