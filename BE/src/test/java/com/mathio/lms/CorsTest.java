package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mathio.lms.control.SecurityFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.PrintWriter;
import java.io.StringWriter;

class CorsTest {
    private static final String ORIGIN = "https://frontend.example";
    private String previousOrigin;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private StringWriter body;

    @BeforeEach
    void configure() throws Exception {
        previousOrigin = System.getProperty("APP_ORIGIN");
        System.setProperty("APP_ORIGIN", " https://other.example, " + ORIGIN + " ");
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/auth/csrf");
        when(request.getContextPath()).thenReturn("");
        when(request.getHeader("Origin")).thenReturn(ORIGIN);
    }

    @AfterEach
    void restore() {
        if (previousOrigin == null) System.clearProperty("APP_ORIGIN");
        else System.setProperty("APP_ORIGIN", previousOrigin);
    }

    @Test
    void preflightAllowsJsonCsrfAndIdempotencyWithoutSession() throws Exception {
        when(request.getMethod()).thenReturn("OPTIONS");
        when(request.getRequestURI()).thenReturn("/api/users");
        when(request.getHeader("Access-Control-Request-Method")).thenReturn("PUT");
        when(request.getHeader("Access-Control-Request-Headers"))
                .thenReturn("content-type, X-CSRF-Token, idempotency-key");
        new SecurityFilter().doFilter(request, response, chain);
        verify(response).setStatus(204);
        verify(response).setHeader("Access-Control-Allow-Origin", ORIGIN);
        verify(response).setHeader("Access-Control-Allow-Credentials", "true");
        verify(response).setHeader("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token, Idempotency-Key");
        verify(request, never()).getSession(anyBoolean());
        verifyNoInteractions(chain);
        assertEquals("", body.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example", "https://frontend.example.evil.example", "null", "*"})
    void unlistedOriginsCannotReadThePublicCsrfEndpoint(String origin) throws Exception {
        when(request.getHeader("Origin")).thenReturn(origin);
        new SecurityFilter().doFilter(request, response, chain);
        verify(response).setStatus(403);
        verify(response, never()).setHeader(eq("Access-Control-Allow-Origin"), anyString());
        verifyNoInteractions(chain);
    }

    @Test
    void allowedOriginCanReadResponsesIncludingCsrfErrors() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        new SecurityFilter().doFilter(request, response, chain);
        verify(response).setStatus(403);
        verify(response).setHeader("Access-Control-Allow-Origin", ORIGIN);
        verify(response).setHeader("Access-Control-Allow-Credentials", "true");
        assertTrue(body.toString().contains("CSRF"));
        verifyNoInteractions(chain);
    }

    @Test
    void allowedCrossOriginMutationStillRequiresMatchingCsrfToken() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("csrf")).thenReturn("fixture-token");
        when(request.getHeader("X-CSRF-Token")).thenReturn("fixture-token");
        new SecurityFilter().doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verify(response).setHeader("Access-Control-Allow-Origin", ORIGIN);
    }

    @Test
    void preflightRejectsUnsupportedHeadersAndMethods() throws Exception {
        when(request.getMethod()).thenReturn("OPTIONS");
        when(request.getHeader("Access-Control-Request-Method")).thenReturn("POST");
        when(request.getHeader("Access-Control-Request-Headers")).thenReturn("X-Unapproved");
        new SecurityFilter().doFilter(request, response, chain);
        verify(response).setStatus(403);
        clearInvocations(response);
        when(request.getHeader("Access-Control-Request-Method")).thenReturn("TRACE");
        when(request.getHeader("Access-Control-Request-Headers")).thenReturn(null);
        new SecurityFilter().doFilter(request, response, chain);
        verify(response).setStatus(403);
        verifyNoInteractions(chain);
    }

    @Test
    void sameOriginProxyRequestsStillWorkWithoutCorsHeaders() throws Exception {
        when(request.getHeader("Origin")).thenReturn(null);
        new SecurityFilter().doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verify(response, never()).setHeader(eq("Access-Control-Allow-Origin"), anyString());
    }
}
