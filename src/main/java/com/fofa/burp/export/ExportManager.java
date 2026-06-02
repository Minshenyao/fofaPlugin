package com.fofa.burp.export;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 导出管理器
 * 负责将查询结果导出为不同格式
 */
public class ExportManager {

    /**
     * 导出数据到文件
     *
     * @param data 数据列表，每行包含: host, ip, port, protocol, title
     * @param file 目标文件
     * @param format 导出格式 (CSV 或 TXT)
     * @throws IOException 文件写入失败
     */
    public static void export(List<String[]> data, File file, String format) throws IOException {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("没有可导出的数据");
        }

        if (file == null) {
            throw new IllegalArgumentException("文件不能为空");
        }

        if (format == null || format.trim().isEmpty()) {
            throw new IllegalArgumentException("导出格式不能为空");
        }

        String formatUpper = format.trim().toUpperCase();

        switch (formatUpper) {
            case "CSV":
                CsvExporter.export(data, file);
                break;
            case "TXT":
                TxtExporter.export(data, file);
                break;
            default:
                throw new IllegalArgumentException("不支持的导出格式: " + format);
        }
    }
}
