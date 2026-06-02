package com.fofa.burp.export;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV 导出器
 * 将数据导出为 CSV 格式
 */
public class CsvExporter {

    private static final String[] HEADERS = {"ID", "URL", "Host", "IP", "Port", "Protocol", "Title"};

    /**
     * 导出数据为 CSV 格式
     *
     * @param data 数据列表
     * @param file 目标文件
     * @throws IOException 文件写入失败
     */
    public static void export(List<String[]> data, File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {

            // 写入表头
            writer.write(String.join(",", HEADERS));
            writer.newLine();

            // 写入数据行
            for (String[] row : data) {
                if (row == null || row.length < HEADERS.length) {
                    continue;
                }

                StringBuilder line = new StringBuilder();
                for (int i = 0; i < HEADERS.length; i++) {
                    if (i > 0) {
                        line.append(",");
                    }
                    String value = row[i] != null ? row[i] : "";
                    // 如果字段包含逗号、双引号或换行符，需要用双引号包裹并转义
                    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                        value = "\"" + value.replace("\"", "\"\"") + "\"";
                    }
                    line.append(value);
                }
                writer.write(line.toString());
                writer.newLine();
            }
        }
    }
}
