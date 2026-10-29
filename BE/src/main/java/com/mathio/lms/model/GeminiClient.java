package com.mathio.lms.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GeminiClient implements AutoCloseable {
    private volatile HttpClient http;
    private java.util.concurrent.ExecutorService executor;
    private boolean closed;
    private static final java.util.concurrent.Semaphore CAPACITY =
            new java.util.concurrent.Semaphore(8);

    public GeminiClient() {}

    public GeminiClient(HttpClient http) {
        this.http = http;
    }

    private synchronized HttpClient http() {
        ApiException.require(!closed, 503, "Ứng dụng AI đang dừng.");
        if (http == null) {
            executor =
                    java.util.concurrent.Executors.newCachedThreadPool(
                            task -> {
                                Thread thread = new Thread(task, "Math11-Gemini-HTTP");
                                thread.setDaemon(true);
                                thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
                                return thread;
                            });
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
                http =
                        HttpClient.newBuilder()
                                .executor(executor)
                                .connectTimeout(Duration.ofSeconds(10))
                                .followRedirects(HttpClient.Redirect.NEVER)
                                .build();
            } finally {
                thread.setContextClassLoader(previous);
            }
        }
        return http;
    }

    @Override
    public synchronized void close() {
        closed = true;
        http = null;
        if (executor != null) executor.shutdownNow();
    }

    public JsonNode generate(GeminiSettings settings, String system, String prompt, String image) {
        ApiException.require(CAPACITY.tryAcquire(), 503, "AI đang bận. Hãy thử lại sau.");
        try {
            return generateBounded(settings, system, prompt, image);
        } finally {
            CAPACITY.release();
        }
    }

    private JsonNode generateBounded(
            GeminiSettings settings, String system, String prompt, String image) {
        ApiException.require(settings.getEnabled(), 503, "Gemini đang tắt.");
        ApiException.require(!settings.getKey().isBlank(), 503, "Chưa cấu hình Gemini API key.");
        String base =
                Environment.get(
                                "GEMINI_API_BASE_URL",
                                "https://generativelanguage.googleapis.com/v1beta")
                        .replaceAll("/+$", "");
        URI uri = URI.create(base + "/models/" + settings.getModel() + ":generateContent");
        ApiException.require(
                uri.getScheme().equals("https")
                        || !Environment.production()
                                && Set.of("localhost", "127.0.0.1").contains(uri.getHost()),
                503,
                "Gemini URL phải dùng HTTPS.");
        ArrayNode parts = Json.array();
        parts.addObject().put("text", prompt);
        if (image != null) {
            ImageStorage.decode(image);
            int comma = image.indexOf(',');
            ApiException.require(comma > 0, 400, "Ảnh không hợp lệ.");
            ObjectNode inline = parts.addObject().putObject("inline_data");
            inline.put("mime_type", image.substring(5, image.indexOf(';')));
            inline.put("data", image.substring(comma + 1));
        }
        ObjectNode body = Json.object();
        body.set(
                "contents",
                Json.MAPPER.valueToTree(List.of(Map.of("role", "user", "parts", parts))));
        body.set(
                "systemInstruction",
                Json.MAPPER.valueToTree(Map.of("parts", List.of(Map.of("text", system)))));
        body.set(
                "generationConfig",
                Json.MAPPER.valueToTree(
                        Map.of(
                                "temperature",
                                settings.getTemperature(),
                                "maxOutputTokens",
                                settings.getMaxOutputTokens(),
                                "responseMimeType",
                                "application/json")));
        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(45))
                        .header("Content-Type", "application/json")
                        .header("x-goog-api-key", settings.getKey())
                        .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body)))
                        .build();
        for (int attempt = 0; attempt < 2; attempt++)
            try {
                var response = http().send(request, info -> new LimitedBodySubscriber());
                int status = response.statusCode();
                if ((status == 429 || status >= 500) && attempt == 0) {
                    Thread.sleep(500);
                    continue;
                }
                ApiException.require(
                        status >= 200 && status < 300,
                        status == 429 ? 503 : 502,
                        "Gemini không khả dụng (HTTP " + status + ").");
                ApiException.require(
                        response.body().length() < 2_000_000, 502, "Phản hồi AI quá lớn.");
                JsonNode root;
                try {
                    root = Json.MAPPER.readTree(response.body());
                } catch (Exception e) {
                    throw new ApiException(502, "Gemini trả JSON không hợp lệ.");
                }
                var candidate = root.path("candidates").path(0);
                ApiException.require(
                        candidate.path("finishReason").asText().equals("STOP"),
                        502,
                        "Gemini chưa hoàn thành hoặc từ chối phản hồi.");
                StringBuilder text = new StringBuilder();
                for (var part : candidate.path("content").path("parts"))
                    if (!part.path("thought").asBoolean()) text.append(part.path("text").asText());
                try {
                    JsonNode result = Json.MAPPER.readTree(text.toString());
                    ApiException.require(result != null, 502, "Gemini không trả nội dung.");
                    return result;
                } catch (ApiException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ApiException(502, "Gemini trả JSON không hợp lệ.");
                }
            } catch (HttpTimeoutException e) {
                throw new ApiException(503, "Gemini quá thời gian phản hồi.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException(503, "Yêu cầu AI bị gián đoạn.");
            } catch (java.io.IOException e) {
                throw new ApiException(503, "Không kết nối được Gemini.");
            }
        throw new ApiException(503, "Gemini không khả dụng.");
    }

    /** Bound buffering while receiving, before converting the response to JSON. */
    private static final class LimitedBodySubscriber
            implements HttpResponse.BodySubscriber<String> {
        private final HttpResponse.BodySubscriber<String> delegate =
                HttpResponse.BodySubscribers.ofString(java.nio.charset.StandardCharsets.UTF_8);
        private java.util.concurrent.Flow.Subscription subscription;
        private long bytes;
        private boolean failed;

        public java.util.concurrent.CompletionStage<String> getBody() {
            return delegate.getBody();
        }

        public void onSubscribe(java.util.concurrent.Flow.Subscription s) {
            subscription = s;
            delegate.onSubscribe(s);
        }

        public void onNext(List<java.nio.ByteBuffer> buffers) {
            if (failed) return;
            for (var buffer : buffers) bytes += buffer.remaining();
            if (bytes > 2_000_000) {
                failed = true;
                subscription.cancel();
                delegate.onError(new java.io.IOException("AI response exceeds maximum size"));
            } else delegate.onNext(buffers);
        }

        public void onError(Throwable e) {
            if (!failed) delegate.onError(e);
        }

        public void onComplete() {
            if (!failed) delegate.onComplete();
        }
    }
}
