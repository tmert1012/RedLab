package com.isidorefarm.redlab.api;


import com.isidorefarm.redlab.RedLab;
import com.isidorefarm.redlab.entities.SafeModeEntities;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.*;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public class GitLabAPIWrapper {

    private GitlabAPI gitlabAPI;
    private List<GitlabProject> projects;
    private List<GitlabUser> users;
    private SafeModeEntities safeModeEntities; // for safe mode only


    public GitLabAPIWrapper() throws IOException {
        gitlabAPI = GitlabAPI.connect(RedLab.config.getGitLabOptions().getBaseURL(), RedLab.config.getGitLabOptions().getApiKey());

        projects = gitlabAPI.getProjects();
        users = gitlabAPI.getUsers();
        safeModeEntities = new SafeModeEntities();
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
        GitlabIssue gitlabIssue;

        if (RedLab.config.isSafeMode()) {
            String[] lablesList = {labels};

            GitlabMilestone milestone = new GitlabMilestone();
            milestone.setId(milestoneId);

            GitlabUser user = new GitlabUser();
            user.setId(assigneeId);

            gitlabIssue = new GitlabIssue();
            gitlabIssue.setId(-1);
            gitlabIssue.setProjectId(projectId);
            gitlabIssue.setAssignee(user);
            gitlabIssue.setMilestone(milestone);
            gitlabIssue.setLabels(lablesList);
            gitlabIssue.setDescription(description);
            gitlabIssue.setTitle(title);

            safeModeEntities.addGitlabIssue(gitlabIssue);
        }
        else {
            gitlabIssue = gitlabAPI.createIssue(projectId, assigneeId, milestoneId, labels, description, title);
        }

        RedLab.logInfo("Added GitLab Issue: " + gitlabIssue.toString());

        return gitlabIssue;
    }

    public List<GitlabMilestone> getMilestones(int projectId) throws IOException {

        if (RedLab.config.isSafeMode())
            return safeModeEntities.getGitlabMilestones(projectId);
        else
            return gitlabAPI.getMilestones(projectId);
    }

    public GitlabMilestone createMilestone(int projectId, String title, String description, Date dueDate) throws IOException {
        GitlabMilestone milestone;

        if (RedLab.config.isSafeMode()) {
            milestone = new GitlabMilestone();
            milestone.setId(-1);
            milestone.setProjectId(projectId);
            milestone.setTitle(title);
            milestone.setDescription(description);
            milestone.setDueDate(dueDate);

            safeModeEntities.addGitlabMileStone(milestone);
        }
        else {
            milestone = gitlabAPI.createMilestone(projectId, title, description, dueDate);
        }

        RedLab.logInfo("Added GitLab Milestone: " + milestone.toString());

        return milestone;
    }

    public GitlabNote createNote(GitlabIssue gitlabIssue, String message) throws IOException {
        GitlabNote gitlabNote;

        if (RedLab.config.isSafeMode()) {
            gitlabNote = new GitlabNote();
            gitlabNote.setId(-1);
            gitlabNote.setBody(message);

            safeModeEntities.addGitlabNote(gitlabIssue.getId(), gitlabNote);
        }
        else {
            gitlabNote = gitlabAPI.createNote(gitlabIssue, message);
        }

        RedLab.logInfo("Added GitLab Note: " + gitlabNote.toString());

        return gitlabNote;
    }

    public List<GitlabIssue> getIssues(GitlabProject gitlabProject) throws IOException {

        if (RedLab.config.isSafeMode())
            return safeModeEntities.getGitlabIssues();
        else
            return gitlabAPI.getIssues(gitlabProject);
    }

    public List<GitlabNote> getNotes(GitlabIssue gitlabIssue) throws IOException {

        if (RedLab.config.isSafeMode())
            return safeModeEntities.getGitlabNotes(gitlabIssue.getId());
        else
            return gitlabAPI.getNotes(gitlabIssue);
    }

}
