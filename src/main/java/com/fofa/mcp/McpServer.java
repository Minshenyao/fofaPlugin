package com.fofa.mcp;

import burp.api.montoya.MontoyaApi;
import com.fofa.burp.api.FofaApiClient;
import com.fofa.burp.config.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.servlet.FilterHolder;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 服务器 - 集成到 Burp 插件中
 *
 * 实现标准 MCP HTTP + SSE transport（协议版本 2024-11-05）：
 *   - GET  /          建立 SSE 长连接（/sse 为别名），首个事件 endpoint 指向消息端点
 *   - POST /messages  接收 JSON-RPC 请求，返回 202 Accepted，响应通过 SSE 流回传
 *
 * 可被 Claude Code（{"type":"sse","url":"http://127.0.0.1:38080"} 直连），
 * 或 Codex（经 supergateway --sse 桥接为 stdio）连接。
 */
public class McpServer {
    private static final Gson gson = new Gson();
    private static final int DEFAULT_PORT = 38080;

    // sessionId -> SSE 客户端
    private static final Map<String, SseClient> sseSessions = new ConcurrentHashMap<>();

    private final MontoyaApi api;
    private final ConfigManager configManager;
    private FofaApiClient apiClient;
    private Javalin app;
    private String apiKey;

    public McpServer(MontoyaApi api, ConfigManager configManager) {
        this.api = api;
        this.configManager = configManager;
    }

    /**
     * 启动 MCP 服务器
     */
    public void start() {
        try {
            // 初始化 API 客户端
            apiClient = new FofaApiClient();

            // 加载配置
            loadConfig();

            // 启动 HTTP + SSE 服务器
            startHttpServer(DEFAULT_PORT);

            api.logging().logToOutput("MCP 服务器（SSE）已启动: http://127.0.0.1:" + DEFAULT_PORT);
        } catch (Exception e) {
            api.logging().logToError("MCP 服务器启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 加载配置文件
     */
    private void loadConfig() throws IOException {
        String configFile = System.getProperty("user.home") + "/.config/burp_fofa_config.yaml";
        Path configPath = Paths.get(configFile);

        if (!Files.exists(configPath)) {
            api.logging().logToOutput("警告: 配置文件不存在: " + configFile);
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            String line;
            String proxyHost = null;
            String proxyPort = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");

                    switch (key) {
                        case "api_key":
                            apiKey = value;
                            break;
                        case "proxy_host":
                            proxyHost = value;
                            break;
                        case "proxy_port":
                            proxyPort = value;
                            break;
                    }
                }
            }

            // 设置代理
            if (proxyHost != null && !proxyHost.isEmpty() && proxyPort != null) {
                try {
                    apiClient.setProxy(proxyHost, Integer.parseInt(proxyPort));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    /**
     * 启动 HTTP + SSE 服务器（标准 MCP SSE transport）
     */
    private void startHttpServer(int port) {
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            // 让 SSE 端点(GET / 与 /sse)对任意客户端生效，不依赖客户端的 Accept 头
            config.jetty.contextHandlerConfig(handler ->
                    handler.addFilter(new FilterHolder(new SseAcceptFilter()), "/*",
                            EnumSet.of(DispatcherType.REQUEST)));
        }).start(port);

        // 健康检查
        app.get("/health", ctx -> ctx.result("OK"));

        // 标准 MCP SSE transport：SSE 端点（根路径，/sse 为别名）
        app.sse("/", this::handleSseConnection);
        app.sse("/sse", this::handleSseConnection);

        // JSON-RPC 消息入口（响应经 SSE 回传）
        app.post("/messages", this::handleMessage);
    }

    /**
     * 建立 SSE 连接：注册 session，保持长连接，并通过 endpoint 事件告知客户端消息端点
     */
    private void handleSseConnection(SseClient client) {
        String sessionId = UUID.randomUUID().toString();
        sseSessions.put(sessionId, client);
        client.keepAlive();
        client.onClose(() -> {
            sseSessions.remove(sessionId);
            api.logging().logToOutput("MCP SSE 客户端已断开: " + sessionId);
        });

        // endpoint 事件：data 为客户端 POST 消息的相对 URI（含 sessionId）
        client.sendEvent("endpoint", "/messages?sessionId=" + sessionId);
        api.logging().logToOutput("MCP SSE 客户端已连接: " + sessionId);
    }

    /**
     * 处理 POST /messages：解析 JSON-RPC，响应经 SSE 回传，HTTP 立即返回 202 Accepted
     */
    private void handleMessage(Context ctx) {
        String sessionId = ctx.queryParam("sessionId");
        SseClient client = sessionId != null ? sseSessions.get(sessionId) : null;
        if (client == null) {
            ctx.status(404).result("Session not found");
            return;
        }

        JsonObject request;
        try {
            request = gson.fromJson(ctx.body(), JsonObject.class);
        } catch (Exception e) {
            ctx.status(400).result("Invalid JSON-RPC message");
            return;
        }

        JsonObject response = handleRequest(request, client);
        if (response != null) {
            try {
                client.sendEvent("message", gson.toJson(response));
            } catch (Exception e) {
                api.logging().logToError("SSE 响应发送失败: " + e.getMessage());
            }
        }

        // 按照 MCP SSE transport 规范，POST 仅回 202，真正的响应走 SSE 流
        ctx.status(202).result("Accepted");
    }

    /**
     * 处理 MCP 请求（JSON-RPC）。返回 null 表示通知（notification），无需响应。
     */
    private JsonObject handleRequest(JsonObject request, SseClient sseClient) {
        String method = request.has("method") ? request.get("method").getAsString() : "";
        JsonObject params = request.has("params") && request.get("params").isJsonObject()
                ? request.getAsJsonObject("params") : new JsonObject();
        boolean isNotification = !request.has("id") || request.get("id").isJsonNull();
        Object id = isNotification ? null : request.get("id");

        try {
            switch (method) {
                case "initialize":
                    return handleInitialize(id);
                case "ping":
                    return handlePing(id);
                case "tools/list":
                    return handleToolsList(id);
                case "tools/call":
                    return handleToolsCall(id, params, sseClient);
                default:
                    // 通知（如 notifications/initialized）无需响应
                    if (isNotification) {
                        return null;
                    }
                    return createErrorResponse(id, "Unknown method: " + method);
            }
        } catch (Exception e) {
            if (isNotification) {
                return null;
            }
            return createErrorResponse(id, e.getMessage());
        }
    }

    private JsonObject handlePing(Object id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", gson.toJsonTree(id));
        response.add("result", new JsonObject());
        return response;
    }

    private JsonObject handleInitialize(Object id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", gson.toJsonTree(id));

        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", "2024-11-05");

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "fofa-server");
        serverInfo.addProperty("version", "1.0.0");
        result.add("serverInfo", serverInfo);

        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());
        capabilities.add("logging", new JsonObject());
        result.add("capabilities", capabilities);

        response.add("result", result);
        return response;
    }

    private JsonObject handleToolsList(Object id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", gson.toJsonTree(id));

        JsonArray tools = new JsonArray();

        // fofa_search 工具
        JsonObject searchTool = new JsonObject();
        searchTool.addProperty("name", "fofa_search");
        searchTool.addProperty("description", "使用 FOFA 搜索引擎查询网络资产。支持查询语法：title=\"登录\", domain=\"example.com\", ip=\"1.1.1.1\", port=\"8080\", country=\"CN\" 等");

        JsonObject searchSchema = new JsonObject();
        searchSchema.addProperty("type", "object");

        JsonObject searchProps = new JsonObject();
        JsonObject queryProp = new JsonObject();
        queryProp.addProperty("type", "string");
        queryProp.addProperty("description", "FOFA 查询语句");
        searchProps.add("query", queryProp);

        JsonObject pageProp = new JsonObject();
        pageProp.addProperty("type", "integer");
        pageProp.addProperty("description", "页码（从 1 开始），默认 1");
        pageProp.addProperty("default", 1);
        searchProps.add("page", pageProp);

        JsonObject sizeProp = new JsonObject();
        sizeProp.addProperty("type", "integer");
        sizeProp.addProperty("description", "每页数量（1-10000），默认 100");
        sizeProp.addProperty("default", 100);
        searchProps.add("size", sizeProp);

        JsonObject formatProp = new JsonObject();
        formatProp.addProperty("type", "string");
        formatProp.addProperty("description", "输出格式: table/json/urls，默认 table");
        formatProp.addProperty("default", "table");
        searchProps.add("format", formatProp);

        searchSchema.add("properties", searchProps);

        JsonArray searchRequired = new JsonArray();
        searchRequired.add("query");
        searchSchema.add("required", searchRequired);

        searchTool.add("inputSchema", searchSchema);
        tools.add(searchTool);

        // fofa_search_batch 工具
        JsonObject batchTool = new JsonObject();
        batchTool.addProperty("name", "fofa_search_batch");
        batchTool.addProperty("description", "批量查询 FOFA 资产（多页），自动分页、去重、遵守速率限制。执行期间通过 SSE 推送实时进度");

        JsonObject batchSchema = new JsonObject();
        batchSchema.addProperty("type", "object");

        JsonObject batchProps = new JsonObject();
        batchProps.add("query", queryProp);

        JsonObject maxPagesProp = new JsonObject();
        maxPagesProp.addProperty("type", "integer");
        maxPagesProp.addProperty("description", "最大查询页数（1-10），默认 5");
        maxPagesProp.addProperty("default", 5);
        batchProps.add("max_pages", maxPagesProp);

        JsonObject batchSizeProp = new JsonObject();
        batchSizeProp.addProperty("type", "integer");
        batchSizeProp.addProperty("description", "每页数量，默认 1000");
        batchSizeProp.addProperty("default", 1000);
        batchProps.add("size", batchSizeProp);

        JsonObject batchFormatProp = new JsonObject();
        batchFormatProp.addProperty("type", "string");
        batchFormatProp.addProperty("description", "输出格式，默认 urls");
        batchFormatProp.addProperty("default", "urls");
        batchProps.add("format", batchFormatProp);

        batchSchema.add("properties", batchProps);

        JsonArray batchRequired = new JsonArray();
        batchRequired.add("query");
        batchSchema.add("required", batchRequired);

        batchTool.add("inputSchema", batchSchema);
        tools.add(batchTool);

        JsonObject result = new JsonObject();
        result.add("tools", tools);

        response.add("result", result);
        return response;
    }

    private JsonObject handleToolsCall(Object id, JsonObject params, SseClient sseClient) {
        String toolName = params.get("name").getAsString();
        JsonObject arguments = params.getAsJsonObject("arguments");

        switch (toolName) {
            case "fofa_search":
                return handleFofaSearch(id, arguments);
            case "fofa_search_batch":
                return handleFofaSearchBatch(id, arguments, sseClient);
            default:
                return createErrorResponse(id, "Unknown tool: " + toolName);
        }
    }

    private JsonObject handleFofaSearch(Object id, JsonObject arguments) {
        String query = arguments.get("query").getAsString();
        int page = arguments.has("page") ? arguments.get("page").getAsInt() : 1;
        int size = arguments.has("size") ? arguments.get("size").getAsInt() : 100;
        String format = arguments.has("format") ? arguments.get("format").getAsString() : "table";

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", gson.toJsonTree(id));

        try {
            // 检查 API Key
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new Exception("API Key 未配置，请在配置文件中设置 api_key");
            }

            // 调用 FOFA API
            com.fofa.burp.api.FofaResponse fofaResponse = apiClient.search(apiKey, query, page, size);

            // 格式化输出
            String resultText = formatFofaResults(fofaResponse, format);

            JsonObject result = new JsonObject();
            JsonArray content = new JsonArray();
            JsonObject textContent = new JsonObject();
            textContent.addProperty("type", "text");
            textContent.addProperty("text", resultText);
            content.add(textContent);
            result.add("content", content);

            response.add("result", result);
            return response;

        } catch (Exception e) {
            api.logging().logToError("FOFA 搜索失败: " + e.getMessage());
            return createErrorResponse(id, "FOFA 搜索失败: " + e.getMessage());
        }
    }

    private JsonObject handleFofaSearchBatch(Object id, JsonObject arguments, SseClient sseClient) {
        String query = arguments.get("query").getAsString();
        int maxPages = arguments.has("max_pages") ? arguments.get("max_pages").getAsInt() : 5;
        int size = arguments.has("size") ? arguments.get("size").getAsInt() : 1000;
        String format = arguments.has("format") ? arguments.get("format").getAsString() : "urls";

        sendLogMessage(sseClient, "info", "开始批量查询，最多 " + maxPages + " 页");

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", gson.toJsonTree(id));

        try {
            // 检查 API Key
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new Exception("API Key 未配置，请在配置文件中设置 api_key");
            }

            StringBuilder allResults = new StringBuilder();
            int totalResults = 0;

            for (int page = 1; page <= maxPages; page++) {
                sendLogMessage(sseClient, "info", "正在查询第 " + page + " 页...");

                com.fofa.burp.api.FofaResponse fofaResponse = apiClient.search(apiKey, query, page, size);

                if (fofaResponse.getResultCount() == 0) {
                    break;
                }

                allResults.append(formatFofaResults(fofaResponse, format));
                allResults.append("\n\n");
                totalResults += fofaResponse.getResultCount();

                // 速率限制：每次请求后等待 1 秒
                if (page < maxPages) {
                    Thread.sleep(1000);
                }
            }

            sendLogMessage(sseClient, "info", "批量查询完成，共获取 " + totalResults + " 条结果");

            JsonObject result = new JsonObject();
            JsonArray content = new JsonArray();
            JsonObject textContent = new JsonObject();
            textContent.addProperty("type", "text");
            textContent.addProperty("text", allResults.toString());
            content.add(textContent);
            result.add("content", content);

            response.add("result", result);
            return response;

        } catch (Exception e) {
            api.logging().logToError("FOFA 批量搜索失败: " + e.getMessage());
            sendLogMessage(sseClient, "error", "批量查询失败: " + e.getMessage());
            return createErrorResponse(id, "FOFA 批量搜索失败: " + e.getMessage());
        }
    }

    /**
     * 格式化 FOFA 搜索结果
     */
    private String formatFofaResults(com.fofa.burp.api.FofaResponse fofaResponse, String format) {
        if (fofaResponse.getResultCount() == 0) {
            return "未找到匹配的结果";
        }

        StringBuilder sb = new StringBuilder();

        switch (format.toLowerCase()) {
            case "json":
                // JSON 格式
                sb.append(gson.toJson(fofaResponse.getResults()));
                break;

            case "urls":
                // 仅 URL 列表
                for (List<String> result : fofaResponse.getResults()) {
                    if (result.size() >= 4) {
                        String protocol = result.get(3);
                        String host = result.get(0);
                        sb.append(protocol).append("://").append(host).append("\n");
                    }
                }
                break;

            case "table":
            default:
                // 表格格式
                sb.append(String.format("查询: %s\n", fofaResponse.getQuery()));
                sb.append(String.format("页码: %d | 结果数: %d\n\n", fofaResponse.getPage(), fofaResponse.getResultCount()));
                sb.append(String.format("%-40s %-15s %-6s %-10s %-30s %-20s\n",
                    "Host", "IP", "Port", "Protocol", "Title", "Last Update"));
                sb.append("-".repeat(130)).append("\n");

                for (List<String> result : fofaResponse.getResults()) {
                    String host = result.size() > 0 ? result.get(0) : "";
                    String ip = result.size() > 1 ? result.get(1) : "";
                    String port = result.size() > 2 ? result.get(2) : "";
                    String protocol = result.size() > 3 ? result.get(3) : "";
                    String title = result.size() > 4 ? result.get(4) : "";
                    String lastUpdate = result.size() > 5 ? result.get(5) : "";

                    // 截断过长的字段
                    if (host.length() > 40) host = host.substring(0, 37) + "...";
                    if (title.length() > 30) title = title.substring(0, 27) + "...";

                    sb.append(String.format("%-40s %-15s %-6s %-10s %-30s %-20s\n",
                        host, ip, port, protocol, title, lastUpdate));
                }
                break;
        }

        return sb.toString();
    }

    /**
     * 通过 SSE 推送日志/进度通知（MCP logging：notifications/message）
     */
    private void sendLogMessage(SseClient client, String level, String message) {
        if (client == null) {
            return;
        }
        try {
            JsonObject notif = new JsonObject();
            notif.addProperty("jsonrpc", "2.0");
            notif.addProperty("method", "notifications/message");

            JsonObject params = new JsonObject();
            params.addProperty("level", level);
            params.addProperty("logger", "fofa");
            params.addProperty("data", message);
            notif.add("params", params);

            client.sendEvent("message", gson.toJson(notif));
        } catch (Exception e) {
            api.logging().logToError("SSE 通知发送失败: " + e.getMessage());
        }
    }

    private JsonObject createErrorResponse(Object id, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", gson.toJsonTree(id));

        JsonObject error = new JsonObject();
        error.addProperty("code", -32603);
        error.addProperty("message", message);
        response.add("error", error);

        return response;
    }

    /**
     * 停止服务器
     */
    public void stop() {
        if (app != null) {
            app.stop();
            sseSessions.clear();
            api.logging().logToOutput("MCP 服务器已停止");
        }
    }
}
