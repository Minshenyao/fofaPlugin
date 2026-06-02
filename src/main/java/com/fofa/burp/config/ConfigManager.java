package com.fofa.burp.config;

import burp.api.montoya.MontoyaApi;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置管理器
 * 负责保存和加载 FOFA API 配置到 ~/.config/burp_fofa_config.yaml
 */
public class ConfigManager {
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.config";
    private static final String CONFIG_FILE = CONFIG_DIR + "/burp_fofa_config.yaml";
    private static final String API_KEY_FIELD = "api_key";
    private static final String API_HOST_FIELD = "api_host";
    private static final String PROXY_HOST_FIELD = "proxy_host";
    private static final String PROXY_PORT_FIELD = "proxy_port";
    private static final String DEFAULT_API_HOST = "https://fofa.info";

    private final MontoyaApi api;

    public ConfigManager(MontoyaApi api) {
        this.api = api;
        ensureConfigFileExists();
    }

    /**
     * 确保配置文件存在
     */
    private void ensureConfigFileExists() {
        try {
            Path configDir = Paths.get(CONFIG_DIR);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            Path configFile = Paths.get(CONFIG_FILE);
            if (!Files.exists(configFile)) {
                Files.createFile(configFile);
                api.logging().logToOutput("创建配置文件: " + CONFIG_FILE);
            }
        } catch (IOException e) {
            api.logging().logToError("创建配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 保存 API Key
     * @param apiKey FOFA API Key
     */
    public void saveApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return;
        }

        try {
            Map<String, String> config = loadConfig();
            config.put(API_KEY_FIELD, apiKey.trim());
            saveConfig(config);
            api.logging().logToOutput("API Key 已保存到: " + CONFIG_FILE);
        } catch (IOException e) {
            api.logging().logToError("保存 API Key 失败: " + e.getMessage());
        }
    }

    /**
     * 加载 API Key
     * @return API Key，如果不存在返回空字符串
     */
    public String loadApiKey() {
        try {
            Map<String, String> config = loadConfig();
            String apiKey = config.get(API_KEY_FIELD);
            return apiKey != null ? apiKey : "";
        } catch (IOException e) {
            api.logging().logToError("加载 API Key 失败: " + e.getMessage());
            return "";
        }
    }

    /**
     * 加载 API Host（隐藏配置项）
     * @return API Host，如果不存在返回默认值
     */
    public String loadApiHost() {
        try {
            Map<String, String> config = loadConfig();
            String apiHost = config.get(API_HOST_FIELD);
            return (apiHost != null && !apiHost.isEmpty()) ? apiHost : DEFAULT_API_HOST;
        } catch (IOException e) {
            api.logging().logToError("加载 API Host 失败: " + e.getMessage());
            return DEFAULT_API_HOST;
        }
    }

    /**
     * 保存代理配置
     * @param proxyHost 代理主机
     * @param proxyPort 代理端口
     */
    public void saveProxy(String proxyHost, String proxyPort) {
        try {
            Map<String, String> config = loadConfig();
            if (proxyHost != null && !proxyHost.trim().isEmpty()) {
                config.put(PROXY_HOST_FIELD, proxyHost.trim());
            } else {
                config.remove(PROXY_HOST_FIELD);
            }
            if (proxyPort != null && !proxyPort.trim().isEmpty()) {
                config.put(PROXY_PORT_FIELD, proxyPort.trim());
            } else {
                config.remove(PROXY_PORT_FIELD);
            }
            saveConfig(config);
        } catch (IOException e) {
            api.logging().logToError("保存代理配置失败: " + e.getMessage());
        }
    }

    /**
     * 加载代理主机
     * @return 代理主机，如果不存在返回空字符串
     */
    public String loadProxyHost() {
        try {
            Map<String, String> config = loadConfig();
            String proxyHost = config.get(PROXY_HOST_FIELD);
            return proxyHost != null ? proxyHost : "";
        } catch (IOException e) {
            api.logging().logToError("加载代理主机失败: " + e.getMessage());
            return "";
        }
    }

    /**
     * 加载代理端口
     * @return 代理端口，如果不存在返回空字符串
     */
    public String loadProxyPort() {
        try {
            Map<String, String> config = loadConfig();
            String proxyPort = config.get(PROXY_PORT_FIELD);
            return proxyPort != null ? proxyPort : "";
        } catch (IOException e) {
            api.logging().logToError("加载代理端口失败: " + e.getMessage());
            return "";
        }
    }

    /**
     * 验证配置是否有效
     * @return 如果 API Key 不为空则返回 true
     */
    public boolean isConfigValid() {
        String apiKey = loadApiKey();
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * 清空配置
     */
    public void clearConfig() {
        try {
            Map<String, String> config = loadConfig();
            config.remove(API_KEY_FIELD);
            saveConfig(config);
            api.logging().logToOutput("配置已清空");
        } catch (IOException e) {
            api.logging().logToError("清空配置失败: " + e.getMessage());
        }
    }

    /**
     * 加载配置文件
     */
    private Map<String, String> loadConfig() throws IOException {
        Map<String, String> config = new HashMap<>();
        File file = new File(CONFIG_FILE);

        if (!file.exists() || file.length() == 0) {
            return config;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    // 移除 YAML 字符串的引号
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    } else if (value.startsWith("'") && value.endsWith("'")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    config.put(key, value);
                }
            }
        }

        return config;
    }

    /**
     * 保存配置文件
     */
    private void saveConfig(Map<String, String> config) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8))) {
            writer.write("# FOFA Burp Suite Plugin Configuration");
            writer.newLine();
            writer.write("# Auto-generated file");
            writer.newLine();
            writer.newLine();

            for (Map.Entry<String, String> entry : config.entrySet()) {
                writer.write(entry.getKey() + ": \"" + entry.getValue() + "\"");
                writer.newLine();
            }
        }
    }
}
