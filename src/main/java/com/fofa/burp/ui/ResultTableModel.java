package com.fofa.burp.ui;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * 结果表格数据模型
 * 用于在 JTable 中显示 FOFA 查询结果
 */
public class ResultTableModel extends AbstractTableModel {
    private static final String[] COLUMN_NAMES = {"ID", "URL", "Host", "IP", "Port", "Protocol", "Title"};
    private final List<String[]> data;

    public ResultTableModel() {
        this.data = new ArrayList<>();
    }

    @Override
    public int getRowCount() {
        return data.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= data.size()) {
            return null;
        }
        String[] row = data.get(rowIndex);
        if (columnIndex < 0 || columnIndex >= row.length) {
            return "";
        }
        return row[columnIndex];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    /**
     * 添加一行数据
     * @param row 数据行，顺序为: id, url, host, ip, port, protocol, title
     */
    public void addRow(String[] row) {
        if (row != null && row.length == COLUMN_NAMES.length) {
            data.add(row);
            int rowIndex = data.size() - 1;
            fireTableRowsInserted(rowIndex, rowIndex);
        }
    }

    /**
     * 添加多行数据
     * @param rows 数据行列表
     */
    public void addRows(List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        int firstRow = data.size();
        for (List<String> row : rows) {
            if (row != null && row.size() >= 5) {
                // FOFA API 返回顺序: host, ip, port, protocol, title
                String[] rowData = new String[COLUMN_NAMES.length];
                String host = row.get(0);
                String ip = row.get(1);
                String port = row.get(2);
                String protocol = row.get(3);
                String title = row.get(4);

                // 构建 URL: protocol://host:port
                // 检查 host 是否已经包含协议前缀
                String url;
                if (host != null && (host.startsWith("http://") || host.startsWith("https://"))) {
                    // host 已包含协议，直接使用
                    url = host;
                } else {
                    // host 不包含协议，需要添加
                    url = protocol + "://" + host;
                    if (port != null && !port.isEmpty() &&
                        !port.equals("80") && !port.equals("443")) {
                        url += ":" + port;
                    }
                }

                // 生成 ID（从 1 开始）
                String id = String.valueOf(data.size() + 1);

                rowData[0] = id;       // id
                rowData[1] = url;      // url
                rowData[2] = host;     // host
                rowData[3] = ip;       // ip
                rowData[4] = port;     // port
                rowData[5] = protocol; // protocol
                rowData[6] = title;    // title
                data.add(rowData);
            }
        }
        int lastRow = data.size() - 1;
        if (lastRow >= firstRow) {
            fireTableRowsInserted(firstRow, lastRow);
        }
    }

    /**
     * 清空所有数据
     */
    public void clear() {
        int rowCount = data.size();
        if (rowCount > 0) {
            data.clear();
            fireTableRowsDeleted(0, rowCount - 1);
        }
    }

    /**
     * 获取所有数据
     * @return 数据列表
     */
    public List<String[]> getAllData() {
        return new ArrayList<>(data);
    }

    /**
     * 获取数据行数
     * @return 行数
     */
    public int getDataSize() {
        return data.size();
    }
}
