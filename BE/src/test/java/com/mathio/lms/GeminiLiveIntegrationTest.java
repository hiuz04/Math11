package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;

import com.mathio.lms.model.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Costs a real Gemini request only with explicit opt-in and an environment test key. */
@EnabledIfEnvironmentVariable(named = "RUN_GEMINI_INTEGRATION", matches = "true")
class GeminiLiveIntegrationTest {
    @Test
    void configuredGeminiReturnsStructuredContent() {
        String key = Environment.get("GEMINI_API_KEY", "");
        assertFalse(key.isBlank(), "Configure a dedicated test key without printing it");
        var result =
                new GeminiClient()
                        .generate(
                                new GeminiSettings(
                                        Environment.get("GEMINI_MODEL", "gemini-2.5-flash"),
                                        0,
                                        256,
                                        true,
                                        key),
                                "Return only JSON.",
                                "Return JSON {ok:true,value:4} after computing 2+2.",
                                null);
        assertTrue(result.path("ok").isBoolean() && result.path("ok").asBoolean());
        assertEquals(4, result.path("value").asInt());
    }
}
