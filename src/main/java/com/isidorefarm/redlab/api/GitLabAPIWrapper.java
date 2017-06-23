package com.isidorefarm.redlab.api;


import com.isidorefarm.redlab.RedLab;
import com.isidorefarm.redlab.config.ProjectMap;
import com.isidorefarm.redlab.entities.SafeModeEntities;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.http.Query;
import org.gitlab.api.models.*;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class GitLabAPIWrapper {

    private GitlabAPI gitlabAPI;
    private SafeModeEntities safeModeEntities; // for safe mode only
    private GitlabUser defaultAssignee;


    public GitLabAPIWrapper() throws IOException {
        gitlabAPI = GitlabAPI.connect(RedLab.config.getGitLabOptions().getBaseURL(), RedLab.config.getGitLabOptions().getApiKey());
        safeModeEntities = new SafeModeEntities();
        setDefaultAssignee();
    }

    private void setDefaultAssignee() throws IOException {
        defaultAssignee = null;
        List<GitlabUser> users = gitlabAPI.findUsers(RedLab.config.getGitLabOptions().getDefaultAssigneeUsername());

        for (GitlabUser user : users) {
            if (user.getUsername().equals(RedLab.config.getGitLabOptions().getDefaultAssigneeUsername())) {
                defaultAssignee = user;
                RedLab.logger.logInfo("found gitlab default assignee '" + defaultAssignee.getUsername() + "'");
            }
        }
        if (defaultAssignee == null)
            RedLab.logger.logError("unable to lookup gitlab default assignee with '" + RedLab.config.getGitLabOptions().getDefaultAssigneeUsername() + "'");

    }

    public GitlabUser getDefaultAssignee() {
        return defaultAssignee;
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

    public GitlabMilestone createMilestone(int projectId, GitlabMilestone milestone) throws IOException {

        if (RedLab.config.isSafeMode())
            safeModeEntities.addGitlabMileStone(milestone);
        else
            milestone = gitlabAPI.createMilestone(projectId, milestone);

        RedLab.logger.logInfo("added gitlab milestone: '" + milestone.getTitle() + "' (" + milestone.getId() + ")");

        return milestone;
    }

    public GitlabMilestone updateMilestone(GitlabMilestone milestone, String stateEvent) throws IOException {
        if (!RedLab.config.isSafeMode())
            milestone = gitlabAPI.updateMilestone(milestone, stateEvent);

        RedLab.logger.logInfo("updated gitlab milestone status: '" + milestone.getTitle() + "' (" + stateEvent + ")");
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

    public GitlabProject getProject(ProjectMap projectMap) throws UnsupportedEncodingException {
        ArrayList<GitlabProject> projects = new ArrayList<GitlabProject>();

        Query query = new Query();
        query.append("search", projectMap.getGitlabProject());

        String tailUrl = GitlabProject.URL + query.toString();

        projects.addAll(gitlabAPI.retrieve().getAll(tailUrl, GitlabProject[].class));

        // check results
        for (GitlabProject project : projects) {

            if (project.getPathWithNamespace().equals(projectMap.getGitlabGroup() + "/" + projectMap.getGitlabProject())) {
                RedLab.logger.logInfo("found gitlab project '" + project.getName() + "'");
                return project;
            }
        }

        RedLab.logger.logError("unable to lookup gitlab project: " + projectMap.getGitlabProject());
        return null;

    }

    public List<GitlabUser> findUser(String emailOrUsername) throws IOException {
        return gitlabAPI.findUsers(emailOrUsername);
    }

    public List<GitlabNote> getNotes(GitlabIssue gitlabIssue) throws IOException {
        ArrayList<GitlabNote> notes = new ArrayList<GitlabNote>();

        notes.addAll(safeModeEntities.getGitlabNotes(gitlabIssue.getId()));

        String tailUrl =
                GitlabProject.URL + "/" + sanitizeProjectId(gitlabIssue.getProjectId()) +
                GitlabIssue.URL + "/" + sanitizeProjectId(gitlabIssue.getId()) +
                GitlabNote.URL
                ;

        notes.addAll(gitlabAPI.retrieve().getAll(tailUrl, GitlabNote[].class));

        return notes;
    }

    public GitlabMilestone getMilestoneByTitle(GitlabProject gitlabProject, String title) throws IOException {
        ArrayList<GitlabMilestone> milestones = new ArrayList<GitlabMilestone>();

        milestones.addAll(safeModeEntities.getGitlabMilestones(gitlabProject.getId()));

        Query query = new Query();
        query.append("search", title);

        String tailUrl =
            GitlabProject.URL + "/" +
            sanitizeProjectId(gitlabProject.getId()) +
            GitlabMilestone.URL +
            query.toString();

        milestones.addAll(gitlabAPI.retrieve().getAll(tailUrl, GitlabMilestone[].class));

        // check results
        for (GitlabMilestone milestone : milestones)
            if (milestone.getTitle().equals(title))
                return milestone;

        return null;
    }

    public GitlabIssue getIssueByTitle(GitlabProject gitlabProject, String title) throws IOException {
        ArrayList<GitlabIssue> issues = new ArrayList<GitlabIssue>();

        issues.addAll(safeModeEntities.getGitlabIssues());

        Query query = new Query();
        query.append("search", title);

        String tailUrl =
            GitlabProject.URL + "/" + sanitizeProjectId(gitlabProject.getId()) +
            GitlabIssue.URL +
            query.toString();

        issues.addAll(gitlabAPI.retrieve().getAll(tailUrl, GitlabIssue[].class));

        // check results
        for (GitlabIssue issue : issues)
            if (issue.getTitle().equals(title))
                return issue;

        return null;
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

    private String sanitizeProjectId(Serializable projectId) {
        if (!(projectId instanceof String) && !(projectId instanceof Number)) {
            throw new IllegalArgumentException("projectId needs to be of type String or Number");
        }

        try {
            return URLEncoder.encode(String.valueOf(projectId), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException((e));
        }
    }

    public void reset() {
        safeModeEntities.reset();
    }

}
