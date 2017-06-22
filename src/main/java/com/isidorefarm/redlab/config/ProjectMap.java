package com.isidorefarm.redlab.config;


public class ProjectMap {

    private String redmineKey;
    private String gitlabGroup;
    private String gitlabProject;


    public ProjectMap(String redmineKey, String gitlabGroup, String gitlabProject) {
        this.redmineKey = redmineKey;
        this.gitlabGroup = gitlabGroup;
        this.gitlabProject = gitlabProject;
    }

    public String getRedmineKey() {
        return redmineKey;
    }

    public String getGitlabGroup() {
        return gitlabGroup;
    }

    public String getGitlabProject() {
        return gitlabProject;
    }

    @Override
    public String toString() {
        return "ProjectMap{" +
                "redmineKey='" + redmineKey + '\'' +
                ", gitlabGroup='" + gitlabGroup + '\'' +
                ", gitlabProject='" + gitlabProject + '\'' +
                '}';
    }

}
