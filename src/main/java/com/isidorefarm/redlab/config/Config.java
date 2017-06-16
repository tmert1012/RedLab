package com.isidorefarm.redlab.config;


import com.google.gson.Gson;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class Config {

    private boolean safeMode;
    private boolean debugMode;
    private RedmineOptions redmineOptions;
    private GitLabOptions gitLabOptions;
    private List<ProjectMap> projectMapList;

    public Config(boolean safeMode, boolean debugMode, RedmineOptions redmineOptions, GitLabOptions gitLabOptions, List<ProjectMap> projectMapList) {
        this.safeMode = safeMode;
        this.debugMode = debugMode;
        this.redmineOptions = redmineOptions;
        this.gitLabOptions = gitLabOptions;
        this.projectMapList = projectMapList;
    }

    public static Config load() throws IOException {
        String json = FileUtils.readFileToString(new File("redlab-config.json"), Charsets.UTF_8);
        Gson gson = new Gson();

        return gson.fromJson(json, Config.class);
    }

    public boolean isSafeMode() {
        return safeMode;
    }

    public boolean isDebugMode() { return debugMode; }

    public RedmineOptions getRedmineOptions() {
        return redmineOptions;
    }

    public GitLabOptions getGitLabOptions() {
        return gitLabOptions;
    }

    public List<ProjectMap> getProjectMapList() {
        return projectMapList;
    }

    @Override
    public String toString() {
        return "Config{" +
                "safeMode=" + safeMode +
                ", debugMode=" + debugMode +
                ", redmineOptions=" + redmineOptions +
                ", gitLabOptions=" + gitLabOptions +
                ", projectMapList=" + projectMapList +
                '}';
    }
}
