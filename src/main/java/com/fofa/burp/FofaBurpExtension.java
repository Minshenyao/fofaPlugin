package com.fofa.burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.fofa.burp.config.ConfigManager;
import com.fofa.burp.ui.FofaPanel;
import com.fofa.mcp.McpServer;

/**
 * FOFA Burp Suite 插件入口点
 */
public class FofaBurpExtension implements BurpExtension {

    private McpServer mcpServer;
    private MontoyaApi api;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;

        // 设置扩展名称
        api.extension().setName("FOFA");

        // 输出加载日志
        api.logging().logToOutput("FOFA 插件正在加载...");

        try {
            // 初始化配置管理器
            ConfigManager configManager = new ConfigManager(api);

            // 创建 UI 面板
            FofaPanel fofaPanel = new FofaPanel(api, configManager);

            // 注册 UI 标签页到 Burp Suite
            api.userInterface().registerSuiteTab("FOFA", fofaPanel);

            // 启动 MCP 服务器
            mcpServer = new McpServer(api, configManager);
            mcpServer.start();

            // 注册卸载回调
            api.extension().registerUnloadingHandler(this::unload);

            // 输出加载成功日志
            api.logging().logToOutput("FOFA 插件加载成功！");
            api.logging().logToOutput("版本: 1.0.0");
            api.logging().logToOutput("说明: 集成 FOFA API 进行资产发现");

        } catch (Exception e) {
            api.logging().logToError("FOFA 插件加载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 插件卸载时的清理逻辑
     */
    private void unload() {
        api.logging().logToOutput("FOFA 插件正在卸载...");

        try {
            // 停止 MCP 服务器
            if (mcpServer != null) {
                mcpServer.stop();
            }

            api.logging().logToOutput("FOFA 插件已成功卸载");
        } catch (Exception e) {
            api.logging().logToError("FOFA 插件卸载时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
