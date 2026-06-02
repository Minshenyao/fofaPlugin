package com.fofa.mcp;

import com.fofa.burp.api.FofaApiClient;
import com.fofa.burp.api.FofaResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.servlet.FilterHolder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FOFA MCP Server（独立运行版）
 *
 * 支持两种模式：
 *   - stdio：经典标准输入输出，逐行 JSON-RPC
 *   - http ：标准 MCP HTTP + SSE transport（协议版本 2024-11-05）
 *            GET  /          建立 SSE 长连接（/sse 为别名），首个事件 endpoint 指向消息端点
 *            POST /messages  接收 JSON-RPC，返回 202 Accepted，响应通过 SSE 流回传
 *
 * http 模式可被 Claude Code（{"type":"sse","url":"http://127.0.0.1:<port>"} 直连），
 * 或 Codex（经 supergateway --sse 桥接为 stdio）连接。
 */
public class FofaMcpServerSSE {
    private static final String VERSION = "1.0.0";
    private static final Gson gson = new Gson();
    private static FofaApiClient apiClient;
    private static String apiKey;

    // sessionId -> SSE 客户端
    private static final Map<String, SseClient> sseSessions = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            // 解析命令行参数
            String mode = "stdio";
            int port = 3000;

            for (int i = 0; i < args.length; i++) {
                if ("--mode".equals(args[i]) && i + 1 < args.length) {
                    mode = args[i + 1];
                } else if ("--port".equals(args[i]) && i + 1 < args.length) {
                    port = Integer.parseInt(args[i + 1]);
                }
            }

            // 初始化 API 客户端
            apiClient = new FofaApiClient();

            // 加载配置
            loadConfig();

            if ("http".equals(mode)) {
                runHttpServer(port);
            } else {
                runStdioServer();
            }
        } catch (Exception e) {
            System.err.println("MCP Server 启动失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 加载配置文件
     */
    private static void loadConfig() throws IOException {
        String configFile = System.getProperty("user.home") + "/.config/burp_fofa_config.yaml";
        Path configPath = Paths.get(configFile);

        if (!Files.exists(configPath)) {
            System.err.println("警告: 配置文件不存在: " + configFile);
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
     * 运行 HTTP + SSE 模式（标准 MCP SSE transport）
     */
    private static void runHttpServer(int port) {
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            // 让 SSE 端点(GET / 与 /sse)对任意客户端生效，不依赖客户端的 Accept 头
            config.jetty.contextHandlerConfig(handler ->
                    handler.addFilter(new FilterHolder(new SseAcceptFilter()), "/*",
                            EnumSet.of(DispatcherType.REQUEST)));
        }).start(port);

        System.err.println("FOFA MCP Server (SSE) 启动在端口: " + port);
        System.err.println("SSE 端点: http://127.0.0.1:" + port + "  (别名 /sse)");

        // 健康检查
        app.get("/health", ctx -> ctx.result("OK"));

        // 标准 MCP SSE transport：SSE 端点（根路径，/sse 为别名）
        app.sse("/", FofaMcpServerSSE::handleSseConnection);
        app.sse("/sse", FofaMcpServerSSE::handleSseConnection);

        // JSON-RPC 消息入口（响应经 SSE 回传）
        app.post("/messages", FofaMcpServerSSE::handleMessage);
    }

    /**
     * 建立 SSE 连接：注册 session，保持长连接，并通过 endpoint 事件告知客户端消息端点
     */
    private static void handleSseConnection(SseClient client) {
        String sessionId = UUID.randomUUID().toString();
        sseSessions.put(sessionId, client);
        client.keepAlive();
        client.onClose(() -> sseSessions.remove(sessionId));

        // endpoint 事件：data 为客户端 POST 消息的相对 URI（含 sessionId）
        client.sendEvent("endpoint", "/messages?sessionId=" + sessionId);
        System.err.println("MCP SSE 客户端已连接: " + sessionId);
    }

    /**
     * 处理 POST /messages：解析 JSON-RPC，响应经 SSE 回传，HTTP 立即返回 202 Accepted
     */
    private static void handleMessage(Context ctx) {
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
            } catch (Exception ignored) {
            }
        }

        // 按照 MCP SSE transport 规范，POST 仅回 202，真正的响应走 SSE 流
        ctx.status(202).result("Accepted");
    }

    /**
     * 运行 stdio 模式
     */
    private static void runStdioServer() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));

        String line;
        while ((line = reader.readLine()) != null) {
            try {
                JsonObject request = gson.fromJson(line, JsonObject.class);
                JsonObject response = handleRequest(request, null);

                if (response != null) {
                    writer.write(gson.toJson(response));
                    writer.newLine();
                    writer.flush();
                }
            } catch (Exception e) {
                JsonObject errorResponse = createErrorResponse(-1, "Internal error: " + e.getMessage());
                writer.write(gson.toJson(errorResponse));
                writer.newLine();
                writer.flush();
            }
        }
    }

    /**
     * 处理 MCP 请求（JSON-RPC）。返回 null 表示通知（notification），无需响应。
     */
    private static JsonObject handleRequest(JsonObject request, SseClient sseClient) {
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

    private static JsonObject handlePing(Object id) {
        JsonObject response = new JsonObject();
        response.add("jsonrpc", gson.toJsonTree("2.0"));
        if (id != null) response.add("id", gson.toJsonTree(id));
        response.add("result", new JsonObject());
        return response;
    }

    /**
     * 处理 initialize 请求
     */
    private static JsonObject handleInitialize(Object id) {
        JsonObject response = new JsonObject();
        response.add("jsonrpc", gson.toJsonTree("2.0"));
        if (id != null) response.add("id", gson.toJsonTree(id));

        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", "2024-11-05");

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "fofa-server");
        serverInfo.addProperty("version", VERSION);
        result.add("serverInfo", serverInfo);

        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());
        capabilities.add("logging", new JsonObject());
        result.add("capabilities", capabilities);

        response.add("result", result);
        return response;
    }

    /**
     * 处理 tools/list 请求
     */
    private static JsonObject handleToolsList(Object id) {
        JsonObject response = new JsonObject();
        response.add("jsonrpc", gson.toJsonTree("2.0"));
        if (id != null) response.add("id", gson.toJsonTree(id));

        JsonArray tools = new JsonArray();

        // fofa_search 工具
        JsonObject searchTool = new JsonObject();
        searchTool.addProperty("name", "fofa_search");
        searchTool.addProperty("description",
                "使用 FOFA 搜索引擎查询网络资产。支持查询语法：title=\"登录\", domain=\"example.com\", ip=\"1.1.1.1\", port=\"8080\", country=\"CN\" 等");

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

        JsonArray required = new JsonArray();
        required.add("query");
        searchSchema.add("required", required);

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
        batchSchema.add("required", required);
        batchTool.add("inputSchema", batchSchema);
        tools.add(batchTool);

        JsonObject result = new JsonObject();
        result.add("tools", tools);
        response.add("result", result);

        return response;
    }

    /**
     * 处理 tools/call 请求
     */
    private static JsonObject handleToolsCall(Object id, JsonObject params, SseClient sseClient) throws Exception {
        String toolName = params.get("name").getAsString();
        JsonObject arguments = params.getAsJsonObject("arguments");

        String result;
        switch (toolName) {
            case "fofa_search":
                result = handleFofaSearch(arguments, sseClient);
                break;
            case "fofa_search_batch":
                result = handleFofaSearchBatch(arguments, sseClient);
                break;
            default:
                throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        JsonObject response = new JsonObject();
        response.add("jsonrpc", gson.toJsonTree("2.0"));
        if (id != null) response.add("id", gson.toJsonTree(id));

        JsonObject resultObj = new JsonObject();
        JsonArray content = new JsonArray();
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", result);
        content.add(textContent);
        resultObj.add("content", content);

        response.add("result", resultObj);
        return response;
    }

    /**
     * 处理 fofa_search
     */
    private static String handleFofaSearch(JsonObject args, SseClient sseClient) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("未配置 FOFA API Key");
        }

        String query = args.get("query").getAsString();
        int page = args.has("page") ? args.get("page").getAsInt() : 1;
        int size = args.has("size") ? args.get("size").getAsInt() : 100;
        String format = args.has("format") ? args.get("format").getAsString() : "table";

        sendLogMessage(sseClient, "info", "正在查询 FOFA...");

        FofaResponse response = apiClient.search(apiKey, query, page, size);

        sendLogMessage(sseClient, "info", String.format("查询完成，获取 %d 条结果", response.getResultCount()));

        return formatResults(response, format);
    }

    /**
     * 处理 fofa_search_batch（执行期间通过 SSE 推送进度）
     */
    private static String handleFofaSearchBatch(JsonObject args, SseClient sseClient) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("未配置 FOFA API Key");
        }

        String query = args.get("query").getAsString();
        int maxPages = Math.min(args.has("max_pages") ? args.get("max_pages").getAsInt() : 5, 10);
        int size = args.has("size") ? args.get("size").getAsInt() : 1000;
        String format = args.has("format") ? args.get("format").getAsString() : "urls";

        List<List<String>> allResults = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        int totalCount = 0;

        sendLogMessage(sseClient, "info", String.format("开始批量查询，最多 %d 页", maxPages));

        for (int page = 1; page <= maxPages; page++) {
            sendLogMessage(sseClient, "info", String.format("正在查询第 %d/%d 页...", page, maxPages));

            FofaResponse response = apiClient.search(apiKey, query, page, size);

            if (page == 1) {
                totalCount = response.getSize();
                sendLogMessage(sseClient, "info", String.format("总共 %,d 条数据", totalCount));
            }

            List<List<String>> results = response.getResults();
            if (results != null) {
                int newCount = 0;
                for (List<String> row : results) {
                    if (row.size() >= 4) {
                        String host = row.get(0);
                        String port = row.get(2);
                        String protocol = row.get(3);

                        String url = buildUrl(host, port, protocol);
                        if (!seenUrls.contains(url)) {
                            seenUrls.add(url);
                            allResults.add(row);
                            newCount++;
                        }
                    }
                }

                sendLogMessage(sseClient, "info",
                        String.format("第 %d 页完成，获取 %d 条（新增 %d 条）", page, results.size(), newCount));
            }

            if (results == null || results.size() < size) {
                sendLogMessage(sseClient, "info", "已到达最后一页");
                break;
            }

            if (page < maxPages) {
                Thread.sleep(600);
            }
        }

        sendLogMessage(sseClient, "info",
                String.format("批量查询完成！共 %,d 条，已获取 %d 条（去重后）", totalCount, allResults.size()));

        FofaResponse mergedResponse = new FofaResponse();
        mergedResponse.setError(false);
        mergedResponse.setSize(totalCount);
        mergedResponse.setResults(allResults);

        String result = formatResults(mergedResponse, format);
        return String.format("批量查询完成: 共 %,d 条，已获取 %d 条（去重后）\n\n%s",
                totalCount, allResults.size(), result);
    }

    /**
     * 通过 SSE 推送日志/进度通知（MCP logging：notifications/message）
     */
    private static void sendLogMessage(SseClient client, String level, String message) {
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
            // 忽略发送失败
        }
    }

    /**
     * 构建 URL
     */
    private static String buildUrl(String host, String port, String protocol) {
        if (host != null && (host.startsWith("http://") || host.startsWith("https://"))) {
            return host;
        }
        String url = protocol + "://" + host;
        if (port != null && !port.equals("80") && !port.equals("443")) {
            url += ":" + port;
        }
        return url;
    }

    /**
     * 格式化结果
     */
    private static String formatResults(FofaResponse response, String format) {
        if ("json".equals(format)) {
            return gson.toJson(response);
        }

        List<List<String>> results = response.getResults();
        if (results == null || results.isEmpty()) {
            return "未找到结果";
        }

        if ("urls".equals(format)) {
            StringBuilder sb = new StringBuilder();
            for (List<String> row : results) {
                if (row.size() >= 4) {
                    String url = buildUrl(row.get(0), row.get(2), row.get(3));
                    sb.append(url).append("\n");
                }
            }
            return sb.toString();
        }

        // table 格式
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("查询结果: 共 %,d 条，当前第 %d 页，显示 %d 条\n\n",
                response.getSize(), response.getPage(), results.size()));
        sb.append("| # | URL | Host | IP | Port | Protocol | Title |\n");
        sb.append("|---|-----|------|----|----|----------|-------|\n");

        for (int i = 0; i < results.size(); i++) {
            List<String> row = results.get(i);
            if (row.size() >= 5) {
                String host = row.get(0) != null ? row.get(0) : "";
                String ip = row.get(1) != null ? row.get(1) : "";
                String port = row.get(2) != null ? row.get(2) : "";
                String protocol = row.get(3) != null ? row.get(3) : "";
                String title = row.get(4) != null ? row.get(4) : "";

                String url = buildUrl(host, port, protocol);
                title = title.replace("|", "\\|").replace("\n", " ");
                if (title.length() > 50) {
                    title = title.substring(0, 50);
                }

                sb.append(String.format("| %d | %s | %s | %s | %s | %s | %s |\n",
                        i + 1, url, host, ip, port, protocol, title));
            }
        }

        return sb.toString();
    }

    /**
     * 创建错误响应
     */
    private static JsonObject createErrorResponse(Object id, String message) {
        JsonObject response = new JsonObject();
        response.add("jsonrpc", gson.toJsonTree("2.0"));
        if (id != null) response.add("id", gson.toJsonTree(id));

        JsonObject error = new JsonObject();
        error.addProperty("code", -32603);
        error.addProperty("message", message);
        response.add("error", error);

        return response;
    }
}
