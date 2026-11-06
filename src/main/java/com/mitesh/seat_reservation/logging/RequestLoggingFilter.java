package com.mitesh.seat_reservation.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_FIELD = "request_id";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        long startedAt = System.nanoTime();
        boolean failed = false;

        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(REQUEST_ID_FIELD, requestId);
        MDC.put("http_method", request.getMethod());
        MDC.put("http_path", request.getRequestURI());

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException | Error exception) {
            failed = true;
            throw exception;
        } finally {
            int status = failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            MDC.put("http_status", Integer.toString(status));
            MDC.put("duration_ms", Long.toString(durationMs));
            logCompletedRequest(status);
            restoreContext(previousContext);
        }
    }

    private String resolveRequestId(String requestId) {
        if (requestId != null && SAFE_REQUEST_ID.matcher(requestId).matches()) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }

    private void logCompletedRequest(int status) {
        if (status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            LOGGER.error("request_completed");
        } else if (status >= HttpServletResponse.SC_BAD_REQUEST) {
            LOGGER.warn("request_completed");
        } else {
            LOGGER.info("request_completed");
        }
    }

    private void restoreContext(Map<String, String> previousContext) {
        if (previousContext == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousContext);
        }
    }
}
