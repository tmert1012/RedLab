package com.isidorefarm.redlab.config;


public class RedmineOptions {

    private String apiKey;
    private String baseURL;
    private boolean autoCloseRedmineIssues;
    private int autoCloseStatusId;

    public RedmineOptions(String apiKey, String baseURL, boolean autoCloseRedmineIssues, int autoCloseStatusId) {
        this.apiKey = apiKey;
        this.baseURL = baseURL;
        this.autoCloseRedmineIssues = autoCloseRedmineIssues;
        this.autoCloseStatusId = autoCloseStatusId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseURL() {
        return baseURL;
    }

    public boolean autoCloseRedmineIssues() {
        return autoCloseRedmineIssues;
    }

    public int getAutoCloseStatusId() {
        return autoCloseStatusId;
    }

    @Override
    public String toString() {
        return "RedmineOptions{" +
                "apiKey='" + apiKey + '\'' +
                ", baseURL='" + baseURL + '\'' +
                ", autoCloseRedmineIssues=" + autoCloseRedmineIssues +
                ", autoCloseStatusId=" + autoCloseStatusId +
                '}';
    }

}
