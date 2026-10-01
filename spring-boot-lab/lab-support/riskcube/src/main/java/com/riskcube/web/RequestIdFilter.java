package com.riskcube.web;

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
import java.util.UUID;

/**
 * Gives every request an id, puts it in every log line ([req=...]) and returns it as X-Request-Id.
 * Support asks customers for this id: with it you find the exact log lines of their request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger access = LoggerFactory.getLogger("com.riskcube.access");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = request.getHeader("X-Request-Id");
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put("requestId", id);
        response.setHeader("X-Request-Id", id);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
            access.info("{} {}{} status={} took={}ms", request.getMethod(), request.getRequestURI(), query,
                    response.getStatus(), System.currentTimeMillis() - start);
            MDC.remove("requestId");
        }
    }
}
