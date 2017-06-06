package com.isidorefarm.redlab.config;


public class RedmineOptions {

    private String apiKey;
    private String baseURL;

    public RedmineOptions(String apiKey, String baseURL) {
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
        return "RedmineOptions{" +
                "apiKey='" + apiKey + '\'' +
                ", baseURL='" + baseURL + '\'' +
                '}';
    }
}
