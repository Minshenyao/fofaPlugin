package com.fofa.burp.api;

import java.util.List;

/**
 * FOFA API 响应数据模型
 */
public class FofaResponse {
    private boolean error;
    private String errmsg;
    private int size;
    private int page;
    private String mode;
    private String query;
    private List<List<String>> results;

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public String getErrmsg() {
        return errmsg;
    }

    public void setErrmsg(String errmsg) {
        this.errmsg = errmsg;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<List<String>> getResults() {
        return results;
    }

    public void setResults(List<List<String>> results) {
        this.results = results;
    }

    /**
     * 检查响应是否成功
     */
    public boolean isSuccess() {
        return !error;
    }

    /**
     * 获取结果数量
     */
    public int getResultCount() {
        return results != null ? results.size() : 0;
    }
}
