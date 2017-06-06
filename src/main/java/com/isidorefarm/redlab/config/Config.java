package com.isidorefarm.redlab.config;


import com.google.gson.Gson;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class Config {

    private boolean safeMode;
    private Redmine redmine;
    private GitLab gitlab;
    private List<ProjectMap> projectMapList;

    public Config(boolean safeMode, Redmine redmine, GitLab gitlab, List<ProjectMap> projectMapList) {
        this.safeMode = safeMode;
        this.redmine = redmine;
        this.gitlab = gitlab;
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

    public Redmine getRedmine() {
        return redmine;
    }

    public GitLab getGitlab() {
        return gitlab;
    }

    public List<ProjectMap> getProjectMapList() {
        return projectMapList;
    }

    @Override
    public String toString() {
        return "Config{" +
                "safeMode=" + safeMode +
                ", redmine=" + redmine +
                ", gitlab=" + gitlab +
                ", projectMapList=" + projectMapList +
                '}';
    }

}
