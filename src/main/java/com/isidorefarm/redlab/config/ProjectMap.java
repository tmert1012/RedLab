package com.isidorefarm.redlab.config;


public class ProjectMap {

    private String redmineID;
    private String gitlabID;

    public ProjectMap(String redmineID, String gitlabID) {
        this.redmineID = redmineID;
        this.gitlabID = gitlabID;
    }

    public String getRedmineID() {
        return redmineID;
    }

    public String getGitlabID() {
        return gitlabID;
    }

    @Override
    public String toString() {
        return "ProjectMap{" +
                "redmineID='" + redmineID + '\'' +
                ", gitlabID='" + gitlabID + '\'' +
                '}';
    }
}
