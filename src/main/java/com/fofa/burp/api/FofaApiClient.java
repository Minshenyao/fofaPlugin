package com.fofa.burp.api;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * FOFA API 客户端
 * 负责与 FOFA API 进行通信
 */
public class FofaApiClient {
    private static final String DEFAULT_API_HOST = "https://fofa.info";
    private static final String DEFAULT_PROXY_HOST = "127.0.0.1";
    private static final int DEFAULT_PROXY_PORT = 7890;
    private static final String API_PATH = "/api/v1/search/all";
    private static final int TIMEOUT_SECONDS = 30;

    private OkHttpClient httpClient;
    private final Gson gson;
    private String apiHost;
    private String proxyHost;
    private int proxyPort;

    public FofaApiClient() {
        this(DEFAULT_API_HOST, DEFAULT_PROXY_HOST, DEFAULT_PROXY_PORT);
    }

    public FofaApiClient(String apiHost) {
        this(apiHost, DEFAULT_PROXY_HOST, DEFAULT_PROXY_PORT);
    }

    public FofaApiClient(String apiHost, String proxyHost, int proxyPort) {
        this.apiHost = (apiHost != null && !apiHost.isEmpty()) ? apiHost : DEFAULT_API_HOST;
        this.proxyHost = (proxyHost != null && !proxyHost.isEmpty()) ? proxyHost : DEFAULT_PROXY_HOST;
        this.proxyPort = proxyPort > 0 ? proxyPort : DEFAULT_PROXY_PORT;
        this.gson = new Gson();
        rebuildHttpClient();
    }

    /**
     * 重建 HTTP 客户端（更新代理配置时调用）
     */
    private void rebuildHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // 配置 SOCKS5 代理
        if (proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0) {
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort));
            builder.proxy(proxy);
        }

        this.httpClient = builder.build();
    }

    /**
     * 设置代理配置
     * @param proxyHost 代理主机
     * @param proxyPort 代理端口
     */
    public void setProxy(String proxyHost, int proxyPort) {
        this.proxyHost = (proxyHost != null && !proxyHost.isEmpty()) ? proxyHost : DEFAULT_PROXY_HOST;
        this.proxyPort = proxyPort > 0 ? proxyPort : DEFAULT_PROXY_PORT;
        rebuildHttpClient();
    }

    /**
     * 获取代理主机
     */
    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * 获取代理端口
     */
    public int getProxyPort() {
        return proxyPort;
    }

    /**
     * 设置 API Host
     * @param apiHost API 主机地址
     */
    public void setApiHost(String apiHost) {
        this.apiHost = (apiHost != null && !apiHost.isEmpty()) ? apiHost : DEFAULT_API_HOST;
    }

    /**
     * 获取当前 API Host
     * @return API 主机地址
     */
    public String getApiHost() {
        return apiHost;
    }

    /**
     * 执行 FOFA 搜索查询
     *
     * @param apiKey API Key
     * @param query 查询语句
     * @param page 页码（从 1 开始）
     * @param size 每页数量（默认 100，最大 10000）
     * @return FOFA 响应对象
     * @throws IOException 网络请求失败
     * @throws FofaApiException API 返回错误
     */
    public FofaResponse search(String apiKey, String query, int page, int size)
            throws IOException, FofaApiException {

        // 验证参数
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new FofaApiException("API Key 不能为空");
        }

        if (query == null || query.trim().isEmpty()) {
            throw new FofaApiException("查询语句不能为空");
        }

        if (page < 1) {
            throw new FofaApiException("页码必须大于 0");
        }

        if (size < 1 || size > 10000) {
            throw new FofaApiException("每页数量必须在 1-10000 之间");
        }

        // Base64 编码查询语句
        String encodedQuery = Base64.getEncoder()
                .encodeToString(query.getBytes(StandardCharsets.UTF_8));

        // 构建请求 URL
        String url = String.format(
                "%s%s?key=%s&qbase64=%s&fields=host,ip,port,protocol,title,lastupdatetime&page=%d&size=%d",
                apiHost,
                API_PATH,
                apiKey,
                encodedQuery,
                page,
                size
        );

        // 创建 HTTP 请求
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        // 执行请求
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP 请求失败: " + response.code() + " " + response.message());
            }

            String responseBody = response.body().string();

            // 解析 JSON 响应
            FofaResponse fofaResponse = gson.fromJson(responseBody, FofaResponse.class);

            // 检查 API 错误
            if (fofaResponse.isError()) {
                throw new FofaApiException("FOFA API 错误: " + fofaResponse.getErrmsg());
            }

            return fofaResponse;
        }
    }

    /**
     * FOFA API 异常
     */
    public static class FofaApiException extends Exception {
        public FofaApiException(String message) {
            super(message);
        }

        public FofaApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
