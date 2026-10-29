package com.mathio.lms.control;

import com.mathio.lms.model.ApiException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Trả lỗi JSON cho các API không thuộc module nào. */
@WebServlet(name = "ApiServlet", urlPatterns = "/api/*")
public class ApiServlet extends ApiController {
    @Override
    protected void handleRequest(HttpServletRequest request, HttpServletResponse response) {
        throw new ApiException(404, "API không tồn tại.");
    }
}
