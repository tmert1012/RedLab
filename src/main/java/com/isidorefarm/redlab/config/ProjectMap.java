package com.isidorefarm.redlab.config;


public class ProjectMap {

    private String redmineKey;
    private String gitLabKey;

    public ProjectMap(String redmineKey, String gitLabKey) {
        this.redmineKey = redmineKey;
        this.gitLabKey = gitLabKey;
    }

    public String getRedmineKey() {
        return redmineKey;
    }

    public String getGitLabKey() {
        return gitLabKey;
    }

    @Override
    public String toString() {
        return "ProjectMap{" +
                "redmineKey='" + redmineKey + '\'' +
                ", gitLabKey='" + gitLabKey + '\'' +
                '}';
    }
}
