package com.isidorefarm.redlab.config;

public class GitLab {

    private String apiKey;
    private String baseURL;

    public GitLab(String apiKey, String baseURL) {
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
        return "GitLab{" +
                "apiKey='" + apiKey + '\'' +
                ", baseURL='" + baseURL + '\'' +
                '}';
    }
}
