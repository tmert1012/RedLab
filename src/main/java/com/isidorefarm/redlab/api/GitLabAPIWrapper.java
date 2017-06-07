package com.isidorefarm.redlab.api;


import com.isidorefarm.redlab.RedLab;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabIssue;
import org.gitlab.api.models.GitlabMilestone;
import org.gitlab.api.models.GitlabProject;
import org.gitlab.api.models.GitlabUser;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

public class GitLabAPIWrapper {

    private GitlabAPI gitlabAPI;
    private List<GitlabProject> projects;
    private List<GitlabUser> users;


    public GitLabAPIWrapper() throws IOException {
        gitlabAPI = GitlabAPI.connect(RedLab.config.getGitLabOptions().getBaseURL(), RedLab.config.getGitLabOptions().getApiKey());

        projects = gitlabAPI.getProjects();
        users = gitlabAPI.getUsers();
    }

    public GitlabProject getProjectByKey(String gitLabKey) {

        for (GitlabProject project : projects) {
            if (project.getName().equals(gitLabKey) || project.getNameWithNamespace().equals(gitLabKey))
                return project;
        }

        RedLab.logInfo("Unable to lookup GitLab project from key: " + gitLabKey);
        return null;

    }

    public List<GitlabUser> getUsers() {
        return users;
    }

    public GitlabUser getDefaultAssignee() {

        for (GitlabUser gitlabUser : users) {
            if (gitlabUser.getUsername().equals(RedLab.config.getGitLabOptions().getDefaultAssigneeUsername()) )
                return gitlabUser;
        }

        RedLab.logInfo("Unable to lookup GitLab default assignee: " + RedLab.config.getGitLabOptions().getDefaultAssigneeUsername());
        return null;
    }

    public GitlabIssue createIssue(int projectId, int assigneeId, int milestoneId, String labels, String description, String title) throws IOException {
        return gitlabAPI.createIssue(projectId, assigneeId, milestoneId, labels, description, title);
    }

    public List<GitlabMilestone> getMilestones(Serializable projectId) throws IOException {
        return gitlabAPI.getMilestones(projectId);
    }

    public GitlabMilestone createMilestone(Serializable projectId, String title, String description, Date dueDate) throws IOException {
        return gitlabAPI.createMilestone(projectId, title, description, dueDate);
    }

}
