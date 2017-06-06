package com.isidorefarm.redlab.config;


public class Redmine {

    private String apiKey;
    private String baseURL;

    public Redmine(String apiKey, String baseURL) {
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
        return "Redmine{" +
                "apiKey='" + apiKey + '\'' +
                ", baseURL='" + baseURL + '\'' +
                '}';
    }
}
