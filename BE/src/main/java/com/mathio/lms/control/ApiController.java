package com.mathio.lms.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;
import com.mathio.lms.model.IdempotencyService;
import com.mathio.lms.model.Json;
import com.mathio.lms.model.StoredResponse;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/** Xử lý JSON và idempotency dùng chung; mỗi Controller tự điều hướng module của mình. */
public abstract class ApiController extends HttpServlet {
    private static final int MAX_REQUEST_BYTES = 12_000_000;
    private static final String REQUEST_BODY = ApiController.class.getName() + ".body";

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        JsonNode input =
                isWrite(request)
                                || request.getMethod().equals("DELETE")
                                || request.getMethod().equals("PATCH")
                        ? readRequestBody(request)
                        : Json.object();
        request.setAttribute(REQUEST_BODY, input);

        String key = request.getHeader("Idempotency-Key");
        Actor currentUser = currentUser(request);
        String path = getPath(request);
        boolean mutation = isWrite(request) || request.getMethod().equals("DELETE");
        if (key != null && currentUser != null && !path.startsWith("/auth/") && mutation) {
            IdempotencyService service = new IdempotencyService(application(request).database);
            StoredResponse saved =
                    service.execute(
                            currentUser,
                            key,
                            request.getMethod() + " " + path,
                            Json.stringify(input),
                            () -> {
                                StringWriter buffer = new StringWriter();
                                PrintWriter writer = new PrintWriter(buffer);
                                HttpServletResponseWrapper captured =
                                        new HttpServletResponseWrapper(response) {
                                            @Override
                                            public PrintWriter getWriter() {
                                                return writer;
                                            }
                                        };
                                dispatch(request, captured);
                                writer.flush();
                                return new StoredResponse(captured.getStatus(), buffer.toString());
                            });
            response.setStatus(saved.getStatus());
            if (saved.getStatus() != HttpServletResponse.SC_NO_CONTENT) {
                response.getWriter().write(saved.getBody());
            }
            return;
        }
        dispatch(request, response);
    }

    private void dispatch(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        switch (request.getMethod()) {
            case "GET" -> doGet(request, response);
            case "POST" -> doPost(request, response);
            case "PUT" -> doPut(request, response);
            case "DELETE" -> doDelete(request, response);
            // Giữ cách trả 404/405 của API cũ đối với các HTTP method còn lại.
            default -> handleRequest(request, response);
        }
    }

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
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        handleRequest(request, response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        handleRequest(request, response);
    }

    protected abstract void handleRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException;

    private JsonNode readRequestBody(HttpServletRequest request) throws IOException {
        if (request.getContentType() != null && request.getContentType().split(";", 2)[0].trim().equalsIgnoreCase("multipart/form-data")) {
            ApiException.require(request.getMethod().equals("POST") && MultipartContent.allowed(getPath(request)), 400, "Endpoint không hỗ trợ upload ảnh.");
            return MultipartContent.read(request);
        }
        byte[] data = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
        ApiException.require(data.length <= MAX_REQUEST_BYTES, 413, "Yêu cầu quá lớn.");
        String type = request.getContentType();
        ApiException.require(
                data.length == 0
                        || type != null
                                && type.split(";", 2)[0]
                                        .trim()
                                        .equalsIgnoreCase("application/json"),
                400,
                "Yêu cầu phải dùng application/json.");
        return data.length == 0
                ? Json.object()
                : Json.parse(new String(data, StandardCharsets.UTF_8));
    }

    protected JsonNode requestBody(HttpServletRequest request) {
        return (JsonNode) request.getAttribute(REQUEST_BODY);
    }

    protected Application application(HttpServletRequest request) {
        return Application.get(request.getServletContext());
    }

    protected Actor currentUser(HttpServletRequest request) {
        return (Actor) request.getAttribute("actor");
    }

    protected String getPath(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        String path = request.getServletPath() + (pathInfo == null ? "" : pathInfo);
        return path.substring("/api".length());
    }

    protected boolean isWrite(HttpServletRequest request) {
        return request.getMethod().equals("POST") || request.getMethod().equals("PUT");
    }

    protected long number(String value) {
        try {
            long id = Long.parseLong(value);
            ApiException.require(id > 0, 400, "ID không hợp lệ.");
            return id;
        } catch (NumberFormatException e) {
            throw new ApiException(400, "ID không hợp lệ.");
        }
    }

    protected long queryId(HttpServletRequest request, String key) {
        String value = request.getParameter(key);
        return value == null ? 0 : number(value);
    }

    protected boolean paginated(HttpServletRequest request) {
        return request.getParameter("offset") != null || request.getParameter("limit") != null;
    }

    protected int offset(HttpServletRequest request) {
        return pageNumber(request, "offset", 0, 0, 1_000_000);
    }

    protected int limit(HttpServletRequest request) {
        return pageNumber(request, "limit", 100, 1, 100);
    }

    private int pageNumber(HttpServletRequest request, String key, int fallback, int min, int max) {
        String value = request.getParameter(key);
        if (value == null) return fallback;
        try {
            int n = Integer.parseInt(value);
            ApiException.require(n >= min && n <= max, 400, "Phân trang không hợp lệ.");
            return n;
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Phân trang không hợp lệ.");
        }
    }

    protected void writeJson(HttpServletResponse response, Object result) throws IOException {
        response.getWriter().write(Json.stringify(result));
    }
}
