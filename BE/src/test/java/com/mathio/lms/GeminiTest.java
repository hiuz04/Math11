package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mathio.lms.model.ApiException;
import com.mathio.lms.model.GeminiClient;
import com.mathio.lms.model.GeminiSettings;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

class GeminiTest {
    final GeminiSettings valid =
            new GeminiSettings("gemini-test", 0.2, 1024, true, "server-secret");

    @Test
    void closedClientCannotStartAnotherRequest() {
        HttpClient http = mock(HttpClient.class);
        GeminiClient client = new GeminiClient(http);
        client.close();
        assertEquals(
                503,
                assertThrows(
                                ApiException.class,
                                () -> client.generate(valid, "system", "prompt", null))
                        .status);
        verifyNoInteractions(http);
    }

    @Test
    void disabledAndMissingKeyFailBeforeNetwork() {
        HttpClient http = mock(HttpClient.class);
        var client = new GeminiClient(http);
        assertEquals(
                503,
                assertThrows(
                                ApiException.class,
                                () ->
                                        client.generate(
                                                new GeminiSettings(
                                                        "test", 0.2, 100, false, "secret"),
                                                "s",
                                                "p",
                                                null))
                        .status);
        assertEquals(
                503,
                assertThrows(
                                ApiException.class,
                                () ->
                                        client.generate(
                                                new GeminiSettings("test", 0.2, 100, true, ""),
                                                "s",
                                                "p",
                                                null))
                        .status);
        verifyNoInteractions(http);
    }

    @Test
    void validStructuredOutputAndKeyHeader() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body())
                .thenReturn(
                        "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{\\\"correct\\\":true}\"}]}}]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(
                        inv -> {
                            HttpRequest request = inv.getArgument(0);
                            assertEquals(
                                    "server-secret",
                                    request.headers().firstValue("x-goog-api-key").orElseThrow());
                            assertFalse(request.uri().toString().contains("server-secret"));
                            assertTrue(request.timeout().orElseThrow().toSeconds() <= 45);
                            return response;
                        });
        assertTrue(
                new GeminiClient(http)
                        .generate(valid, "system", "prompt", null)
                        .path("correct")
                        .asBoolean());
    }

    @Test
    void timeoutIsUnavailable() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("private upstream"));
        var error =
                assertThrows(
                        ApiException.class,
                        () -> new GeminiClient(http).generate(valid, "s", "p", null));
        assertEquals(503, error.status);
        assertFalse(error.getMessage().contains("private upstream"));
    }

    @Test
    void invalidJsonAndTruncatedResponseAreRejected() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("not-json");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        assertEquals(
                502,
                assertThrows(
                                ApiException.class,
                                () -> new GeminiClient(http).generate(valid, "s", "p", null))
                        .status);
        when(response.body()).thenReturn("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}");
        assertEquals(
                502,
                assertThrows(
                                ApiException.class,
                                () -> new GeminiClient(http).generate(valid, "s", "p", null))
                        .status);
    }

    @Test
    void rateLimitRetriesOnceAndFailsHonestly() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(429);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        assertEquals(
                503,
                assertThrows(
                                ApiException.class,
                                () -> new GeminiClient(http).generate(valid, "s", "p", null))
                        .status);
        verify(http, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
