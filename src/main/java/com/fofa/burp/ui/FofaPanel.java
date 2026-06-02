package com.fofa.burp.ui;

import burp.api.montoya.MontoyaApi;
import com.fofa.burp.api.FofaApiClient;
import com.fofa.burp.api.FofaResponse;
import com.fofa.burp.config.ConfigManager;
import com.fofa.burp.export.ExportManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;

/**
 * FOFA 插件主 UI 面板
 */
public class FofaPanel extends JPanel {
    private final MontoyaApi api;
    private final ConfigManager configManager;
    private final FofaApiClient apiClient;
    private final ResultTableModel tableModel;

    // UI 组件
    private JPasswordField apiKeyField;
    private JTextField queryField;
    private JTable resultTable;
    private JLabel statusLabel;
    private JButton queryButton;
    private JButton clearButton;
    private JButton exportButton;
    private JComboBox<String> exportFormatCombo;

    // 默认查询参数
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 1000;

    // 保存最后一次查询的信息
    private String lastQuery = "";
    private int lastTotalCount = 0;

    public FofaPanel(MontoyaApi api, ConfigManager configManager) {
        this.api = api;
        this.configManager = configManager;
        this.apiClient = new FofaApiClient(configManager.loadApiHost());
        this.tableModel = new ResultTableModel();

        initUI();
        loadConfig();
    }

    /**
     * 初始化 UI
     */
    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // 顶部面板：配置区域
        add(createConfigPanel(), BorderLayout.NORTH);

        // 中间面板：查询区域和结果显示
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.add(createQueryPanel(), BorderLayout.NORTH);
        centerPanel.add(createResultPanel(), BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);
    }

    /**
     * 创建配置面板
     */
    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("配置"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // API Key 标签
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("API Key:"), gbc);

        // API Key 输入框（缩窄宽度）
        gbc.gridx = 1;
        gbc.weightx = 0.7;
        apiKeyField = new JPasswordField(20);
        panel.add(apiKeyField, gbc);

        // 保存配置按钮
        gbc.gridx = 2;
        gbc.weightx = 0;
        JButton saveButton = new JButton("保存配置");
        saveButton.addActionListener(e -> saveConfig());
        panel.add(saveButton, gbc);

        // 配置代理按钮
        gbc.gridx = 3;
        gbc.weightx = 0;
        JButton proxyButton = new JButton("配置代理");
        proxyButton.addActionListener(e -> showProxyDialog());
        panel.add(proxyButton, gbc);

        return panel;
    }

    /**
     * 显示代理配置对话框
     */
    private void showProxyDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 代理类型说明
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(new JLabel("SOCKS5 代理配置（留空则不使用代理）"), gbc);

        // IP 标签
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("IP:"), gbc);

        // IP 输入框
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField proxyHostField = new JTextField(15);
        proxyHostField.setText(configManager.loadProxyHost());
        panel.add(proxyHostField, gbc);

        // 端口标签
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("Port:"), gbc);

        // 端口输入框
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField proxyPortField = new JTextField(15);
        proxyPortField.setText(configManager.loadProxyPort());
        panel.add(proxyPortField, gbc);

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "配置 SOCKS5 代理",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            String proxyHost = proxyHostField.getText().trim();
            String proxyPort = proxyPortField.getText().trim();

            // 验证端口号
            if (!proxyPort.isEmpty()) {
                try {
                    int port = Integer.parseInt(proxyPort);
                    if (port < 1 || port > 65535) {
                        JOptionPane.showMessageDialog(this, "端口号必须在 1-65535 之间", "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this, "端口号必须是数字", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            // 保存配置
            configManager.saveProxy(proxyHost, proxyPort);

            // 更新 API 客户端代理设置
            if (!proxyHost.isEmpty() && !proxyPort.isEmpty()) {
                apiClient.setProxy(proxyHost, Integer.parseInt(proxyPort));
                statusLabel.setText("代理已配置: " + proxyHost + ":" + proxyPort);
            } else {
                apiClient.setProxy("", 0);
                statusLabel.setText("代理已清除");
            }
        }
    }

    /**
     * 创建查询面板
     */
    private JPanel createQueryPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("查询"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 查询语句标签
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("查询语句:"), gbc);

        // 查询语句输入框
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        queryField = new JTextField(40);
        queryField.setToolTipText("例如: title=\"bing\" 或 domain=\"example.com\"");
        queryField.addActionListener(e -> performQuery());
        panel.add(queryField, gbc);

        // 按钮面板
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        queryButton = new JButton("查询");
        queryButton.addActionListener(e -> performQuery());
        buttonPanel.add(queryButton);

        clearButton = new JButton("清空结果");
        clearButton.addActionListener(e -> clearResults());
        buttonPanel.add(clearButton);

        // 分隔符
        buttonPanel.add(new JLabel("    "));

        // 导出格式
        buttonPanel.add(new JLabel("导出格式:"));
        exportFormatCombo = new JComboBox<>(new String[]{"CSV", "TXT"});
        buttonPanel.add(exportFormatCombo);

        // 导出按钮
        exportButton = new JButton("导出");
        exportButton.addActionListener(e -> exportResults());
        buttonPanel.add(exportButton);

        panel.add(buttonPanel, gbc);

        return panel;
    }

    /**
     * 创建结果面板
     */
    private JPanel createResultPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new TitledBorder("查询结果"));

        // 结果表格
        resultTable = new JTable(tableModel);
        resultTable.setAutoCreateRowSorter(true);
        resultTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        resultTable.setFillsViewportHeight(true);

        // 创建右键菜单
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem copyUrlItem = new JMenuItem("复制 URL");
        copyUrlItem.addActionListener(e -> copySelectedColumn(0));
        popupMenu.add(copyUrlItem);

        JMenuItem copyIpItem = new JMenuItem("复制 IP");
        copyIpItem.addActionListener(e -> copySelectedColumn(2));
        popupMenu.add(copyIpItem);

        JMenuItem copyHostItem = new JMenuItem("复制 Host");
        copyHostItem.addActionListener(e -> copySelectedColumn(1));
        popupMenu.add(copyHostItem);

        popupMenu.addSeparator();

        JMenuItem copyRowItem = new JMenuItem("复制整行");
        copyRowItem.addActionListener(e -> copySelectedRows());
        popupMenu.add(copyRowItem);

        // 添加鼠标监听器
        resultTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                handlePopup(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                handlePopup(e);
            }

            private void handlePopup(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = resultTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        // 如果点击的行不在已选中的行中，则选中该行
                        if (!resultTable.isRowSelected(row)) {
                            resultTable.setRowSelectionInterval(row, row);
                        }
                        popupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        // 状态标签
        statusLabel = new JLabel("就绪");
        statusLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        panel.add(statusLabel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * 复制选中行的指定列到剪贴板
     * @param column 列索引
     */
    private void copySelectedColumn(int column) {
        int[] selectedRows = resultTable.getSelectedRows();
        if (selectedRows.length == 0) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selectedRows.length; i++) {
            int modelRow = resultTable.convertRowIndexToModel(selectedRows[i]);
            Object value = tableModel.getValueAt(modelRow, column);
            if (value != null) {
                if (i > 0) {
                    sb.append("\n");
                }
                sb.append(value.toString());
            }
        }

        copyToClipboard(sb.toString());
    }

    /**
     * 复制选中的整行到剪贴板
     */
    private void copySelectedRows() {
        int[] selectedRows = resultTable.getSelectedRows();
        if (selectedRows.length == 0) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selectedRows.length; i++) {
            int modelRow = resultTable.convertRowIndexToModel(selectedRows[i]);
            if (i > 0) {
                sb.append("\n");
            }
            for (int col = 0; col < tableModel.getColumnCount(); col++) {
                if (col > 0) {
                    sb.append("\t");
                }
                Object value = tableModel.getValueAt(modelRow, col);
                sb.append(value != null ? value.toString() : "");
            }
        }

        copyToClipboard(sb.toString());
    }

    /**
     * 复制文本到剪贴板
     * @param text 要复制的文本
     */
    private void copyToClipboard(String text) {
        if (text != null && !text.isEmpty()) {
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(text);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }

    /**
     * 加载配置
     */
    private void loadConfig() {
        String apiKey = configManager.loadApiKey();
        if (!apiKey.isEmpty()) {
            apiKeyField.setText(apiKey);
        }

        // 加载代理配置
        String proxyHost = configManager.loadProxyHost();
        String proxyPort = configManager.loadProxyPort();
        if (!proxyHost.isEmpty() && !proxyPort.isEmpty()) {
            try {
                apiClient.setProxy(proxyHost, Integer.parseInt(proxyPort));
            } catch (NumberFormatException e) {
                // 忽略无效的端口配置
            }
        }
    }

    /**
     * 保存配置
     */
    private void saveConfig() {
        String apiKey = new String(apiKeyField.getPassword());
        configManager.saveApiKey(apiKey);
        JOptionPane.showMessageDialog(this, "配置已保存", "成功", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 执行查询
     */
    private void performQuery() {
        String apiKey = new String(apiKeyField.getPassword());
        String query = queryField.getText().trim();

        // 验证输入
        if (apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先配置 API Key", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (query.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入查询语句", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 禁用按钮
        setButtonsEnabled(false);
        statusLabel.setText("查询中...");

        // 在后台线程执行查询
        SwingWorker<FofaResponse, Void> worker = new SwingWorker<>() {
            @Override
            protected FofaResponse doInBackground() throws Exception {
                return apiClient.search(apiKey, query, DEFAULT_PAGE, DEFAULT_SIZE);
            }

            @Override
            protected void done() {
                try {
                    FofaResponse response = get();
                    tableModel.clear();
                    tableModel.addRows(response.getResults());

                    // 保存查询信息用于导出
                    lastQuery = query;
                    lastTotalCount = response.getSize();

                    statusLabel.setText(String.format(
                            "查询完成 - 总数: %s, 当前显示: %d 条（预览）",
                            formatNumber(lastTotalCount),
                            response.getResultCount()
                    ));
                    api.logging().logToOutput("FOFA 查询成功: 总数 " + lastTotalCount + ", 显示 " + response.getResultCount() + " 条");
                } catch (Exception ex) {
                    String errorMsg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    statusLabel.setText("查询失败: " + errorMsg);
                    JOptionPane.showMessageDialog(
                            FofaPanel.this,
                            "查询失败: " + errorMsg,
                            "错误",
                            JOptionPane.ERROR_MESSAGE
                    );
                    api.logging().logToError("FOFA 查询失败: " + errorMsg);
                } finally {
                    setButtonsEnabled(true);
                }
            }
        };

        worker.execute();
    }

    /**
     * 清空结果
     */
    private void clearResults() {
        tableModel.clear();
        lastQuery = "";
        lastTotalCount = 0;
        statusLabel.setText("结果已清空");
    }

    /**
     * 导出结果
     */
    private void exportResults() {
        if (lastTotalCount == 0 || lastQuery.isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有可导出的数据，请先执行查询", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String apiKey = new String(apiKeyField.getPassword());
        if (apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先配置 API Key", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 计算页数（每页 1000 条）
        int totalPages = (lastTotalCount + 999) / 1000;
        int freePages = Math.min(totalPages, 10);

        // 创建导出页数选择对话框
        JPanel dialogPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // 提示信息
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        String infoText = String.format(
            "当前共 %d 页（%s 条数据），其中免费页数 %d 页。",
            totalPages, formatNumber(lastTotalCount), freePages
        );
        dialogPanel.add(new JLabel(infoText), gbc);

        gbc.gridy = 1;
        dialogPanel.add(new JLabel("全部导出请直接点击确定，导出部分数据请输入页数。"), gbc);

        // 导出页数输入
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        dialogPanel.add(new JLabel("请输入要导出的页数:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField exportPageField = new JTextField(10);
        dialogPanel.add(exportPageField, gbc);

        // 显示对话框
        int dialogResult = JOptionPane.showConfirmDialog(
            this,
            dialogPanel,
            "导出设置",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (dialogResult != JOptionPane.OK_OPTION) {
            return;
        }

        // 解析导出页数
        int exportPages;
        String inputText = exportPageField.getText().trim();
        if (inputText.isEmpty()) {
            // 空白则导出全部（但最多 10 页免费）
            exportPages = freePages;
        } else {
            try {
                exportPages = Integer.parseInt(inputText);
                if (exportPages < 1) {
                    JOptionPane.showMessageDialog(this, "导出页数必须大于 0", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (exportPages > totalPages) {
                    exportPages = totalPages;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "请输入有效的页数", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // 选择导出文件
        JFileChooser fileChooser = new JFileChooser();
        String format = (String) exportFormatCombo.getSelectedItem();
        fileChooser.setDialogTitle("导出为 " + format);

        // 生成带时间戳的文件名
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String defaultFileName = "fofa_result_" + timestamp + "." + format.toLowerCase();
        fileChooser.setSelectedFile(new File(defaultFileName));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            final int finalExportPages = exportPages;
            final String query = lastQuery;

            // 禁用按钮
            setButtonsEnabled(false);
            statusLabel.setText("正在导出...");

            // 在后台线程执行导出
            SwingWorker<int[], Void> worker = new SwingWorker<>() {
                @Override
                protected int[] doInBackground() throws Exception {
                    java.util.List<String[]> allData = new java.util.ArrayList<>();
                    java.util.Set<String> seenUrls = new java.util.HashSet<>();
                    int totalFetched = 0;

                    // 分页查询数据
                    for (int page = 1; page <= finalExportPages; page++) {
                        final int currentPage = page;
                        SwingUtilities.invokeLater(() ->
                            statusLabel.setText("正在导出第 " + currentPage + "/" + finalExportPages + " 页...")
                        );

                        FofaResponse response = apiClient.search(apiKey, query, page, DEFAULT_SIZE);
                        if (response.getResults() != null) {
                            totalFetched += response.getResultCount();
                            // 转换 List<List<String>> 为 List<String[]>，并去重
                            for (java.util.List<String> row : response.getResults()) {
                                if (row != null && row.size() >= 5) {
                                    String host = row.get(0);
                                    String ip = row.get(1);
                                    String port = row.get(2);
                                    String protocol = row.get(3);
                                    String title = row.get(4);

                                    // 构建 URL
                                    String url;
                                    if (host != null && (host.startsWith("http://") || host.startsWith("https://"))) {
                                        url = host;
                                    } else {
                                        url = protocol + "://" + host;
                                        if (port != null && !port.isEmpty() &&
                                            !port.equals("80") && !port.equals("443")) {
                                            url += ":" + port;
                                        }
                                    }

                                    // 去重：基于 URL 判断是否已存在
                                    if (!seenUrls.contains(url)) {
                                        seenUrls.add(url);
                                        String[] rowData = new String[7];
                                        rowData[0] = String.valueOf(allData.size() + 1);
                                        rowData[1] = url;
                                        rowData[2] = host;
                                        rowData[3] = ip;
                                        rowData[4] = port;
                                        rowData[5] = protocol;
                                        rowData[6] = title;
                                        allData.add(rowData);
                                    }
                                }
                            }
                        }

                        // 如果返回结果少于 1000 条，说明已经没有更多数据
                        if (response.getResultCount() < DEFAULT_SIZE) {
                            break;
                        }

                        // 遵守 API 速率限制：< 2 次/秒
                        if (page < finalExportPages) {
                            Thread.sleep(600);
                        }
                    }

                    ExportManager.export(allData, file, format);
                    return new int[]{allData.size(), totalFetched};
                }

                @Override
                protected void done() {
                    try {
                        int[] result = get();
                        int exportedCount = result[0];
                        statusLabel.setText("导出完成");

                        JOptionPane.showMessageDialog(
                                FofaPanel.this,
                                "导出成功: " + file.getAbsolutePath() + "\n共导出 " + exportedCount + " 条数据",
                                "成功",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                        api.logging().logToOutput("导出成功: " + file.getAbsolutePath() + ", 共 " + exportedCount + " 条");
                    } catch (Exception ex) {
                        String errorMsg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        statusLabel.setText("导出失败");
                        JOptionPane.showMessageDialog(
                                FofaPanel.this,
                                "导出失败: " + errorMsg,
                                "错误",
                                JOptionPane.ERROR_MESSAGE
                        );
                        api.logging().logToError("导出失败: " + errorMsg);
                    } finally {
                        setButtonsEnabled(true);
                    }
                }
            };

            worker.execute();
        }
    }

    /**
     * 设置按钮启用状态
     */
    private void setButtonsEnabled(boolean enabled) {
        queryButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
        exportButton.setEnabled(enabled);
    }

    /**
     * 格式化数字为国际计数法（千分位逗号分隔）
     * 例如: 1234567 -> "1,234,567"
     */
    private String formatNumber(int number) {
        return String.format("%,d", number);
    }
}
