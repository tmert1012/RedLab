package com.isidorefarm.redlab.api;


import com.isidorefarm.redlab.RedLab;
import com.isidorefarm.redlab.entities.SafeModeEntities;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.*;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
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

    public GitlabIssue createIssue(int projectId, int assigneeId, int milestoneId, String labels, String description, String title) throws IOException {
        GitlabIssue gitlabIssue;

        if (RedLab.config.isSafeMode()) {
            GitlabMilestone milestone = new GitlabMilestone();
            milestone.setId(milestoneId);

            GitlabUser user = new GitlabUser();
            user.setId(assigneeId);

            gitlabIssue = new GitlabIssue();
            gitlabIssue.setProjectId(projectId);
            gitlabIssue.setAssignee(user);
            gitlabIssue.setMilestone(milestone);
            gitlabIssue.setLabels(explodeLabels(labels));
            gitlabIssue.setDescription(description);
            gitlabIssue.setTitle(title);

            safeModeEntities.addGitlabIssue(gitlabIssue);
        }
        else {
            gitlabIssue = gitlabAPI.createIssue(projectId, assigneeId, milestoneId, labels, description, title);
        }

        RedLab.logger.logInfo("added gitlab issue: '" + gitlabIssue.getTitle() + "' ("  + gitlabIssue.getId() + ")");

        return gitlabIssue;
    }

    public GitlabIssue editIssue(int projectId, int issueId, int assigneeId, int milestoneId, String labels, String description, String title, GitlabIssue.Action action) throws IOException {
            GitlabIssue gitlabIssue;

            if (RedLab.config.isSafeMode()) {

                gitlabIssue = safeModeEntities.getGitlabIssue(issueId);

                GitlabMilestone milestone = new GitlabMilestone();
                milestone.setId(milestoneId);

                GitlabUser user = new GitlabUser();
                user.setId(assigneeId);

                gitlabIssue.setProjectId(projectId);
                gitlabIssue.setId(issueId);
                gitlabIssue.setAssignee(user);
                gitlabIssue.setMilestone(milestone);
                gitlabIssue.setLabels(explodeLabels(labels));
                gitlabIssue.setDescription(description);
                gitlabIssue.setTitle(title);

            }
            else {
                gitlabIssue = gitlabAPI.editIssue(projectId, issueId, assigneeId, milestoneId, labels, description, title, action);
            }

            RedLab.logger.logInfo("updated gitlab issue: '" + gitlabIssue.getTitle() + "' ("  + gitlabIssue.getId() + ")");

            return gitlabIssue;
    }

    public GitlabMilestone createMilestone(int projectId, String title, String description, Date dueDate) throws IOException {
        GitlabMilestone milestone;

        if (RedLab.config.isSafeMode()) {
            milestone = new GitlabMilestone();
            milestone.setProjectId(projectId);
            milestone.setTitle(title);
            milestone.setDescription(description);
            milestone.setDueDate(dueDate);

            safeModeEntities.addGitlabMileStone(milestone);
        }
        else {
            milestone = gitlabAPI.createMilestone(projectId, title, description, dueDate);
        }

        RedLab.logger.logInfo("added gitlab milestone: '" + milestone.getTitle() + "' (" + milestone.getId() + ")");

        return milestone;
    }

    public GitlabNote createNote(GitlabIssue gitlabIssue, String message) throws IOException {
        GitlabNote gitlabNote;

        if (RedLab.config.isSafeMode()) {
            gitlabNote = new GitlabNote();
            gitlabNote.setBody(message);

            safeModeEntities.addGitlabNote(gitlabIssue.getId(), gitlabNote);
        }
        else {
            gitlabNote = gitlabAPI.createNote(gitlabIssue, message);
        }

        RedLab.logger.logInfo("added gitlab note. ("  + gitlabNote.getId() + ")");

        return gitlabNote;
    }

    public GitlabProject getProjectByKey(String gitLabKey) {

        for (GitlabProject project : projects) {

            if (project.getName().equals(gitLabKey) || project.getPathWithNamespace().equals(gitLabKey)) {
                RedLab.logger.logInfo("found gitlab project '" + project.getName() + "'");
                return project;
            }
        }

        RedLab.logger.logInfo("unable to lookup gitlab project from key: " + gitLabKey);
        return null;

    }

    public List<GitlabUser> getUsers() {
        return users;
    }

    public GitlabUser getDefaultAssignee() {

        for (GitlabUser gitlabUser : users) {
            if (gitlabUser.getUsername().equals(RedLab.config.getGitLabOptions().getDefaultAssigneeUsername()) ) {
                RedLab.logger.logInfo("found gitlab default assignee '" + gitlabUser.getUsername() + "'");
                return gitlabUser;
            }
        }

        RedLab.logger.logInfo("unable to lookup gitlab default assignee with '" + RedLab.config.getGitLabOptions().getDefaultAssigneeUsername() + "'");
        return null;
    }

    public List<GitlabMilestone> getMilestones(int projectId, boolean returnSafeModeEntities) throws IOException {

        if (returnSafeModeEntities)
            return safeModeEntities.getGitlabMilestones(projectId);
        else
            return gitlabAPI.getMilestones(projectId);
    }

    public List<GitlabIssue> getIssues(GitlabProject gitlabProject, boolean returnSafeModeEntities) throws IOException {

        if (returnSafeModeEntities)
            return safeModeEntities.getGitlabIssues();
        else
            return gitlabAPI.getIssues(gitlabProject);
    }

    public List<GitlabNote> getNotes(GitlabIssue gitlabIssue, boolean returnSafeModeEntities) throws IOException {

        if (returnSafeModeEntities)
            return safeModeEntities.getGitlabNotes(gitlabIssue.getId());
        else
            return gitlabAPI.getNotes(gitlabIssue);
    }

    // key milestone title
    public HashMap<String, GitlabMilestone> getMilestoneHashMap(GitlabProject gitlabProject, boolean returnSafeModeEntities) throws IOException {
        List<GitlabMilestone> milestones = getMilestones(gitlabProject.getId(), returnSafeModeEntities);

        // key is version title or milestone name
        HashMap<String, GitlabMilestone> milestoneHashMap = new HashMap<String, GitlabMilestone>();
        for (GitlabMilestone milestone : milestones)
            milestoneHashMap.put(milestone.getTitle(), milestone);

        return milestoneHashMap;
    }

    // key: issue title
    public HashMap<String, GitlabIssue> getGitlabIssueHashMap(GitlabProject gitlabProject, boolean returnSafeModeEntities) throws IOException {
        List<GitlabIssue> issues = getIssues(gitlabProject, returnSafeModeEntities);

        HashMap<String, GitlabIssue> map = new HashMap<String, GitlabIssue>();
        for (GitlabIssue issue : issues)
            map.put(issue.getTitle(), issue);

        return map;
    }


    public String[] explodeLabels(String labels) {

        if (labels.contains(","))
            return labels.split(",");
        else {
            String[] labelList = {labels};
            return labelList;
        }

    }

    public String implodeLabels(String[] labels) {
        String labelStr = "";

        for (String label : labels)
            labelStr += label + ",";

        return labelStr.replaceAll(",$", "");
    }

}
