package com.isidorefarm.redlab.config;

public class GitLabOptions {

    private String apiKey;
    private String baseURL;

    public GitLabOptions(String apiKey, String baseURL) {
        this.apiKey = apiKey;
        this.baseURL = baseURL;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseURL() {
        return baseURL;
    }

    @Override
    public String toString() {
        return "GitLabOptions{" +
                "apiKey='" + apiKey + '\'' +
                ", baseURL='" + baseURL + '\'' +
                '}';
    }
}
