package com.fofa.mcp;

import com.fofa.burp.api.FofaApiClient;
import com.fofa.burp.api.FofaResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * FOFA MCP Server
 * 实现 MCP (Model Context Protocol) 协议，让 Claude 可以调用 FOFA API
 */
public class FofaMcpServer {
    private static final String VERSION = "1.0.0";
    private static final Gson gson = new Gson();
    private static FofaApiClient apiClient;
    private static String apiKey;

    public static void main(String[] args) {
        try {
            // 加载配置
            loadConfig();

            // 初始化 API 客户端
            apiClient = new FofaApiClient();

            // 启动 MCP server（stdio 模式）
            runStdioServer();
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
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");

                    if ("api_key".equals(key)) {
                        apiKey = value;
                    } else if ("proxy_host".equals(key) && !value.isEmpty()) {
                        String proxyHost = value;
                        // 继续读取 proxy_port
                        String nextLine = reader.readLine();
                        if (nextLine != null && nextLine.contains("proxy_port:")) {
                            String[] portParts = nextLine.split(":", 2);
                            if (portParts.length == 2) {
                                String proxyPort = portParts[1].trim().replaceAll("^\"|\"$", "");
                                try {
                                    apiClient.setProxy(proxyHost, Integer.parseInt(proxyPort));
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 运行 stdio 模式的 MCP server
     */
    private static void runStdioServer() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));

        String line;
        while ((line = reader.readLine()) != null) {
            try {
                JsonObject request = gson.fromJson(line, JsonObject.class);
                JsonObject response = handleRequest(request);

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
     * 处理 MCP 请求
     */
    private static JsonObject handleRequest(JsonObject request) {
        String method = request.has("method") ? request.get("method").getAsString() : "";
        JsonObject params = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();
        Object id = request.has("id") ? request.get("id") : null;

        try {
            switch (method) {
                case "initialize":
                    return handleInitialize(id);
                case "tools/list":
                    return handleToolsList(id);
                case "tools/call":
                    return handleToolsCall(id, params);
                default:
                    return createErrorResponse(id, "Unknown method: " + method);
            }
        } catch (Exception e) {
            return createErrorResponse(id, e.getMessage());
        }
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
        result.addProperty("serverInfo", "FOFA MCP Server");
        result.addProperty("version", VERSION);

        JsonObject capabilities = new JsonObject();
        JsonObject tools = new JsonObject();
        capabilities.add("tools", tools);
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
        batchTool.addProperty("description", "批量查询 FOFA 资产（多页），自动分页、去重、遵守速率限制");

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
    private static JsonObject handleToolsCall(Object id, JsonObject params) throws Exception {
        String toolName = params.get("name").getAsString();
        JsonObject arguments = params.getAsJsonObject("arguments");

        String result;
        switch (toolName) {
            case "fofa_search":
                result = handleFofaSearch(arguments);
                break;
            case "fofa_search_batch":
                result = handleFofaSearchBatch(arguments);
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
    private static String handleFofaSearch(JsonObject args) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("未配置 FOFA API Key");
        }

        String query = args.get("query").getAsString();
        int page = args.has("page") ? args.get("page").getAsInt() : 1;
        int size = args.has("size") ? args.get("size").getAsInt() : 100;
        String format = args.has("format") ? args.get("format").getAsString() : "table";

        FofaResponse response = apiClient.search(apiKey, query, page, size);
        return formatResults(response, format);
    }

    /**
     * 处理 fofa_search_batch
     */
    private static String handleFofaSearchBatch(JsonObject args) throws Exception {
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

        for (int page = 1; page <= maxPages; page++) {
            FofaResponse response = apiClient.search(apiKey, query, page, size);

            if (page == 1) {
                totalCount = response.getSize();
            }

            List<List<String>> results = response.getResults();
            if (results != null) {
                for (List<String> row : results) {
                    if (row.size() >= 4) {
                        String host = row.get(0);
                        String port = row.get(2);
                        String protocol = row.get(3);

                        String url = buildUrl(host, port, protocol);
                        if (!seenUrls.contains(url)) {
                            seenUrls.add(url);
                            allResults.add(row);
                        }
                    }
                }
            }

            if (results == null || results.size() < size) {
                break;
            }

            if (page < maxPages) {
                Thread.sleep(600);
            }
        }

        FofaResponse mergedResponse = new FofaResponse();
        mergedResponse.setError(false);
        mergedResponse.setSize(totalCount);
        mergedResponse.setResults(allResults);

        String result = formatResults(mergedResponse, format);
        return String.format("批量查询完成: 共 %,d 条，已获取 %d 条（去重后）\n\n%s",
                totalCount, allResults.size(), result);
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
