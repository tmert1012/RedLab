package com.isidorefarm.redlab.api;


import com.isidorefarm.redlab.RedLab;
import com.taskadapter.redmineapi.Include;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.*;
import com.taskadapter.redmineapi.internal.ResultsWrapper;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class RedmineAPIWrapper {

    final private int LIMIT = 100;

    private RedmineManager redmineManager;
    private List<Project> projects;
    private HashMap<Integer, User> userHashMap;
    private HashMap<Integer, IssueStatus> issueStatusHashMap;


    public RedmineAPIWrapper() throws RedmineException {
        redmineManager = RedmineManagerFactory.createWithApiKey(RedLab.config.getRedmineOptions().getBaseURL(), RedLab.config.getRedmineOptions().getApiKey());

        projects = redmineManager.getProjectManager().getProjects();

        userHashMap = new HashMap<Integer, User>();
        for (User user : redmineManager.getUserManager().getUsers()) {
            if (RedLab.config.isDebugMode()) RedLab.logger.logInfo("adding user: " + user.getId() + ", " + user.getFullName());
            userHashMap.put(user.getId(), user);
        }

        issueStatusHashMap = new HashMap<Integer, IssueStatus>();
        for (IssueStatus issueStatus : redmineManager.getIssueManager().getStatuses()) {
            if (RedLab.config.isDebugMode()) RedLab.logger.logInfo("adding issueStatus: " + issueStatus.getId() + ", " + issueStatus.getName());
            issueStatusHashMap.put(issueStatus.getId(), issueStatus);
        }

    }

    public List<Version> getVersions(int projectID) throws RedmineException {
        return redmineManager.getProjectManager().getVersions(projectID);
    }

    public List<Issue> getIssues(int projectID) throws RedmineException {
        List<Issue> issues = new ArrayList<Issue>();

        HashMap<String, String> params = new HashMap<String, String>();
        params.put("project_id", Integer.toString(projectID));
        params.put("status_id", "*");
        params.put("offset", "0");
        params.put("limit", Integer.toString(LIMIT));

        Integer issueCount = null;
        int callCount = 1;
        ResultsWrapper<Issue> resultsWrapper = null;
        while (issueCount == null || issueCount >= LIMIT) {

            resultsWrapper = redmineManager.getIssueManager().getIssues(params);

            issueCount = resultsWrapper.getResultsNumber();
            RedLab.logger.logInfo("getting issues. iteration: " + callCount + ", issue count: " + issueCount);

            issues.addAll(resultsWrapper.getResults());

            params.put("offset", Integer.toString(LIMIT * callCount));
            callCount++;

        }

        return issues;
    }

    public Issue getIssueById(int issueId) throws RedmineException {
        return redmineManager.getIssueManager().getIssueById(issueId, Include.journals, Include.relations, Include.changesets, Include.watchers);
    }

    public Project getProjectByKey(String projectKey) {

        for (Project project : projects)
            if (project.getIdentifier().equals(projectKey)) {
                RedLab.logger.logInfo("found redmine project: '" + project.getName() + "'");
                return project;
            }

        RedLab.logger.logInfo("unable to lookup redmine project by projectKey: " + projectKey + ", skipping.");
        return null;
    }

    public User getAssignee(Issue redmineIssue) {

        // assignee not set in ticket
        if (redmineIssue == null) {
            RedLab.logger.logInfo("assignee not set in redmine ticket, skipping");
            return null;
        }

        if (userHashMap.containsKey(redmineIssue.getAssigneeId())) {
            User user = userHashMap.get(redmineIssue.getAssigneeId());
            RedLab.logger.logInfo("found redmine assignee '" + user.getLogin() + "' (" + redmineIssue.getAssigneeId() + ")");
            return user;
        }

        RedLab.logger.logInfo("unable to lookup redmine assignee: " + redmineIssue.getAssigneeId() + ", skipping.");
        return null;
    }

    public void updateIssue(Issue redmineIssue) throws RedmineException {

        if (!RedLab.config.isSafeMode())
            redmineManager.getIssueManager().update(redmineIssue);

        RedLab.logger.logInfo("updated redmine issue: " + redmineIssue.getId());
    }

    public HashMap<Integer, User> getUserHashMap() {
        return userHashMap;
    }

    public HashMap<Integer, IssueStatus> getIssueStatusHashMap() {
        return issueStatusHashMap;
    }

    // workaround for bug: https://github.com/taskadapter/redmine-java-api/issues/291
    public Date getClosedOn(Issue redmineIssue) {
        Date closedDate = null;

        for (Journal journal : redmineIssue.getJournals())
            for (JournalDetail journalDetail : journal.getDetails())
                if (journalDetail.getName().equals("status_id")) {
                    IssueStatus issueStatus = issueStatusHashMap.get( Integer.parseInt(journalDetail.getNewValue()) );

                    if (issueStatus != null && issueStatus.isClosed() && (closedDate == null || closedDate.before(journal.getCreatedOn())))
                        closedDate = journal.getCreatedOn();
                }

        return closedDate;
    }

}
