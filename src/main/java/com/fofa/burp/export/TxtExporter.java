package com.fofa.burp.export;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * TXT 导出器
 * 将数据导出为 TXT 格式
 */
public class TxtExporter {

    /**
     * 导出数据为 TXT 格式
     * 格式: 每行一个 URL
     *
     * @param data 数据列表
     * @param file 目标文件
     * @throws IOException 文件写入失败
     */
    public static void export(List<String[]> data, File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {

            for (String[] row : data) {
                if (row == null || row.length < 2) {
                    continue;
                }

                // URL 现在在索引 1（因为索引 0 是 ID）
                String url = row[1] != null ? row[1] : "";

                // 只导出 URL
                writer.write(url);
                writer.newLine();
            }
        }
    }
}
