package com.mathio.lms.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Nhận request và trả JSON cho Exam; giữ nguyên đường dẫn API. */
@WebServlet(
        name = "ExamController",
        urlPatterns = {"/api/exams", "/api/exams/*", "/api/quiz-attempts", "/api/quiz-attempts/*"})
@jakarta.servlet.annotation.MultipartConfig(maxFileSize = 8 * 1024 * 1024, maxRequestSize = 12_000_000, fileSizeThreshold = 65536)
public class ExamController extends ApiController {
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
        if (path.matches("/quiz-attempts/\\d+/grade") && method.equals("POST")) {
            writeJson(
                    response,
                    application(request)
                            .examService
                            .gradeAttempt(
                                    currentUser(request),
                                    number(path.split("/")[2]),
                                    requestBody(request)));
            return;
        }
        if (path.matches("/exams/\\d+/publish-results") && method.equals("POST")) {
            writeJson(
                    response,
                    application(request)
                            .examService
                            .publishResults(currentUser(request), number(path.split("/")[2])));
            return;
        }
        if (path.equals("/exams")) {
            handleExams(request, response);
            return;
        }
        if (path.matches("/exams/\\d+/attempts") && method.equals("POST")) {
            createExamAttempt(request, response);
            return;
        }
        if (path.matches("/exams/\\d+")) {
            handleExam(request, response);
            return;
        }
        if (path.equals("/quiz-attempts") && method.equals("GET")) {
            showAttemptList(request, response);
            return;
        }
        if (path.matches("/quiz-attempts/\\d+") && method.equals("GET")) {
            showAttemptDetail(request, response);
            return;
        }
        if (path.matches("/quiz-attempts/\\d+/(answers|submit)") && method.equals("POST")) {
            submitAnswers(request, response);
            return;
        }
        throw new ApiException(404, "API không tồn tại.");
    }

    private void handleExams(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        String method = request.getMethod();
        Object result = null;
        if (method.equals("GET"))
            result =
                    paginated(request)
                            ? app.examService.getExams(currentUser, offset(request), limit(request))
                            : app.examService.getExams(currentUser);
        else if (method.equals("POST")) {
            result = app.examService.saveExam(currentUser, input);
            response.setStatus(201);
        } else throw new ApiException(405, "Phương thức không hỗ trợ.");
        writeJson(response, result);
    }

    private void createExamAttempt(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        String path = getPath(request);
        Object result = app.examService.startAttempt(currentUser, number(path.split("/")[2]));
        response.setStatus(201);
        writeJson(response, result);
    }

    private void handleExam(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        String method = request.getMethod();
        String path = getPath(request);
        Object result = null;
        long id = number(path.substring(7));
        if (method.equals("GET")) result = app.examService.getExamById(currentUser, id);
        else if (method.equals("DELETE")) {
            app.examService.deleteExam(currentUser, id);
            response.setStatus(204);
            return;
        } else throw new ApiException(405, "Phương thức không hỗ trợ.");
        writeJson(response, result);
    }

    private void showAttemptList(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        Object result =
                paginated(request)
                        ? app.examService.getAttemptHistory(
                                currentUser, offset(request), limit(request))
                        : app.examService.getAttemptHistory(currentUser);
        writeJson(response, result);
    }

    private void showAttemptDetail(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        String path = getPath(request);
        Object result = app.examService.getAttemptById(currentUser, number(path.substring(15)));
        writeJson(response, result);
    }

    private void submitAnswers(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        String path = getPath(request);
        Object result =
                app.examService.saveAnswers(
                        currentUser, number(path.split("/")[2]), input, path.endsWith("/submit"));
        writeJson(response, result);
    }
}
