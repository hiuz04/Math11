package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mathio.lms.model.*;

import org.junit.jupiter.api.Test;

import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;

class GeminiCapacityTest {
    @Test
    void twentyConcurrentRequestsAdmitEightAndRejectExcessWithoutQueueingConnections()
            throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body())
                .thenReturn(
                        "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{\\\"ok\\\":true}\"}]}}]}");
        CountDownLatch entered = new CountDownLatch(8),
                rejected = new CountDownLatch(12),
                release = new CountDownLatch(1);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(
                        inv -> {
                            entered.countDown();
                            assertTrue(release.await(8, TimeUnit.SECONDS));
                            return response;
                        });
        GeminiClient client = new GeminiClient(http);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 20; i++)
                results.add(
                        pool.submit(
                                () -> {
                                    try {
                                        assertTrue(
                                                client.generate(
                                                                new GeminiSettings(
                                                                        "fixture",
                                                                        0,
                                                                        512,
                                                                        true,
                                                                        "fixture-only"),
                                                                "system",
                                                                "prompt",
                                                                null)
                                                        .path("ok")
                                                        .asBoolean());
                                        return 200;
                                    } catch (ApiException e) {
                                        rejected.countDown();
                                        return e.status;
                                    }
                                }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTrue(rejected.await(5, TimeUnit.SECONDS));
            release.countDown();
            int ok = 0, busy = 0;
            for (var result : results) {
                int code = result.get(5, TimeUnit.SECONDS);
                if (code == 200) ok++;
                else if (code == 503) busy++;
                else fail("Unexpected status");
            }
            assertEquals(8, ok);
            assertEquals(12, busy);
            verify(http, times(8))
                    .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
