package com.mathio.lms.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathio.lms.model.ApiException;
import com.mathio.lms.model.ImageStorage;
import com.mathio.lms.model.Json;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

/** Multipart transport adapter; existing service owns validation, storage, ACL and transactions. */
final class MultipartContent {
    static boolean allowed(String path) {
        return path.equals("/essays") || path.equals("/ai/essays/evaluate-retry") || path.equals("/ai/chat")
                || path.matches("/quiz-attempts/\\d+/answers");
    }
    static JsonNode read(HttpServletRequest request) throws IOException {
        try {
            var parts = request.getParts();
            ApiException.require(parts.size() == 2 && parts.stream().filter(p -> p.getName().equals("payload")).count() == 1
                    && parts.stream().filter(p -> p.getName().equals("image")).count() == 1,
                    400, "Upload cần payload và một ảnh.");
            var payload = request.getPart("payload");
            ApiException.require(payload.getSize() <= 200_000, 413, "Bài làm quá dài.");
            JsonNode input;
            try (var stream = payload.getInputStream()) {
                input = Json.parse(new String(stream.readNBytes(200_001), StandardCharsets.UTF_8));
            }
            ApiException.require(input.isObject() && input.path("content").isObject(), 400, "Nội dung bài làm không hợp lệ.");
            var file = request.getPart("image");
            ApiException.require(file.getSize() > 0 && file.getSize() <= 8 * 1024 * 1024, 413, "Ảnh vượt quá 8 MB hoặc rỗng.");
            String mime = file.getContentType();
            ApiException.require(mime != null && Set.of("image/jpeg", "image/png", "image/webp").contains(mime), 400, "Chỉ nhận ảnh JPEG, PNG hoặc WEBP.");
            byte[] bytes;
            try (var stream = file.getInputStream()) { bytes = stream.readNBytes(8 * 1024 * 1024 + 1); }
            ApiException.require(bytes.length <= 8 * 1024 * 1024, 413, "Ảnh vượt quá 8 MB.");
            String data = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
            ImageStorage.decode(data); // Verify actual image format, dimensions and full decoding.
            ((ObjectNode) input.path("content")).put("image", data);
            if (request.getRequestURI().matches(".*/quiz-attempts/\\d+/answers")) {
                String questionId = Long.toString(Json.id(input, "questionId"));
                ApiException.require(input.path("essayAnswers").isObject(), 400, "Bài tự luận không hợp lệ.");
                ObjectNode item = Json.object(); item.set("content", input.path("content"));
                ((ObjectNode) input.path("essayAnswers")).set(questionId, item);
                ((ObjectNode) input).remove(java.util.List.of("content", "questionId"));
            }
            if (request.getRequestURI().endsWith("/ai/chat")) {
                ((ObjectNode) input).put("image", data);
                ((ObjectNode) input).remove("content");
            }
            return input;
        } catch (IllegalStateException e) {
            throw new ApiException(413, "Upload vượt quá dung lượng cho phép.");
        } catch (ServletException e) {
            throw new ApiException(400, "Dữ liệu multipart không hợp lệ.");
        }
    }
    private MultipartContent() {}
}
