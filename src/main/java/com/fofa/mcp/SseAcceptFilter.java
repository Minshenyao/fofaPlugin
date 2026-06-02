package com.fofa.mcp;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * 为 SSE 端点（GET / 与 /sse）强制注入 Accept: text/event-stream 头的 servlet 过滤器。
 *
 * 背景：Javalin 5 的 {@code app.sse()} 仅在请求 Accept 头包含 text/event-stream 时才启用 SSE，
 * 否则返回空的 text/plain 响应。部分 MCP 客户端（例如 Codex 经 supergateway 桥接的 SSE 客户端）
 * 发起连接时不带该头，导致握手失败（Invalid content type, expected text/event-stream）。
 *
 * 本过滤器对 SSE 路径的 GET 请求包装 Accept 头，使任意客户端都能正常建立 SSE 连接；
 * 对其它路径（如 POST /messages、GET /health）与已携带该头的客户端（如 Claude Code）均无副作用。
 */
public class SseAcceptFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpReq
                && "GET".equalsIgnoreCase(httpReq.getMethod())
                && isSsePath(httpReq.getRequestURI())) {
            chain.doFilter(new EventStreamAcceptRequest(httpReq), response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isSsePath(String uri) {
        return "/".equals(uri) || "/sse".equals(uri);
    }

    /**
     * 包装请求，使 Accept 头始终返回 text/event-stream
     */
    private static class EventStreamAcceptRequest extends HttpServletRequestWrapper {
        private static final String EVENT_STREAM = "text/event-stream";

        EventStreamAcceptRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            if ("Accept".equalsIgnoreCase(name)) {
                return EVENT_STREAM;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("Accept".equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(EVENT_STREAM));
            }
            return super.getHeaders(name);
        }
    }
}
