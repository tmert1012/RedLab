package com.isidorefarm.redlab.config;

public class GitLabOptions {

    private String apiKey;
    private String baseURL;
    private String defaultAssigneeUsername;

    public GitLabOptions(String apiKey, String baseURL, String defaultAssigneeUsername) {
        this.apiKey = apiKey;
        this.baseURL = baseURL;
        this.defaultAssigneeUsername = defaultAssigneeUsername;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseURL() {
        return baseURL;
    }

    public String getDefaultAssigneeUsername() { return defaultAssigneeUsername; }

    @Override
    public String toString() {
        return "GitLabOptions{" +
                "apiKey='" + apiKey + '\'' +
                ", baseURL='" + baseURL + '\'' +
                ", defaultAssigneeUsername='" + defaultAssigneeUsername + '\'' +
                '}';
    }
}
