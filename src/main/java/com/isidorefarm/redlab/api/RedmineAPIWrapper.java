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
    private HashMap<Integer, Tracker> trackerHashMap;
    private HashMap<Integer, IssuePriority> issuePriorityHashMap;
    private List<Version> versions;


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

        trackerHashMap = new HashMap<Integer, Tracker>();
        for (Tracker tracker : redmineManager.getIssueManager().getTrackers()) {
            if (RedLab.config.isDebugMode()) RedLab.logger.logInfo("adding tracker: " + tracker.getId() + ", " + tracker.getName());
            trackerHashMap.put(tracker.getId(), tracker);
        }

        issuePriorityHashMap = new HashMap<Integer, IssuePriority>();
        versions = null;
    }

    public void reset() {
        versions = null;
    }

    public List<Version> getVersions(int projectID) throws RedmineException {

        if (versions == null || versions.isEmpty())
            versions = redmineManager.getProjectManager().getVersions(projectID);

        return versions;
    }

    public Version getVersion(String versionID) {
        if (versionID == null || versionID.equals(""))
            return null;

        return getVersion(Integer.parseInt(versionID));
    }

    public Version getVersion(int versionID) {

        for (Version version : versions)
            if (version.getId() == versionID)
                return version;

        return null;
    }

    public List<Issue> getIssues(int projectID) throws RedmineException {
        List<Issue> issues = new ArrayList<Issue>();

        HashMap<String, String> params = new HashMap<String, String>();
        params.put("project_id", Integer.toString(projectID));
        params.put("status_id", "*");
        params.put("offset", "0");
        params.put("limit", Integer.toString(LIMIT));
        params.put("sort", "created");

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

        updateIssuePriorityHashMap(issues);

        return issues;
    }

    // save all issue priorities in map for reference later. workaround since /enumerations/issue_priorities.json returns a 404.
    private void updateIssuePriorityHashMap(List<Issue> issues) {
        IssuePriority issuePriority = null;

        for (Issue issue : issues) {

            // set issue priority if we don't have it
            if (!issuePriorityHashMap.containsKey(issue.getPriorityId())) {
                issuePriority = IssuePriorityFactory.create(issue.getPriorityId());
                issuePriority.setName(issue.getPriorityText());

                if (RedLab.config.isDebugMode()) RedLab.logger.logInfo("adding issue priority: " + issuePriority.getId() + ", " + issuePriority.getName());
                issuePriorityHashMap.put(issue.getPriorityId(), issuePriority);
            }

        }

    }

    public Issue getIssueById(int issueId) throws RedmineException {
        return redmineManager.getIssueManager().getIssueById(issueId, Include.journals, Include.relations, Include.changesets, Include.watchers, Include.attachments);
    }

    public Project getProjectByKey(String projectKey) {

        for (Project project : projects)
            if (project.getIdentifier().equals(projectKey)) {
                RedLab.logger.logInfo("found redmine project: '" + project.getName() + "'");
                return project;
            }

        RedLab.logger.logError("unable to lookup redmine project by projectKey: " + projectKey + ", skipping.");
        return null;
    }

    public Project getProjectById(String projectId) {
        if (projectId == null || projectId.equals(""))
            return null;

        return getProjectById( Integer.parseInt(projectId) );
    }

    public Project getProjectById(int projectId) {
        for (Project project : projects)
            if (project.getId() == projectId)
                return project;

        return null;
    }

    public User getAssignee(Issue redmineIssue) {

        // assignee not set in ticket
        if (redmineIssue.getAssigneeId() == null) {
            RedLab.logger.logInfo("assignee not set in redmine ticket, skipping");
            return null;
        }

        if (userHashMap.containsKey(redmineIssue.getAssigneeId())) {
            User user = userHashMap.get(redmineIssue.getAssigneeId());
            RedLab.logger.logInfo("found redmine assignee '" + user.getLogin() + "' (" + redmineIssue.getAssigneeId() + ")");
            return user;
        }

        RedLab.logger.logError("unable to lookup redmine assignee: " + redmineIssue.getAssigneeId() + ", skipping.");
        return null;
    }

    public void updateIssue(Issue redmineIssue) throws RedmineException {

        if (!RedLab.config.isSafeMode())
            redmineManager.getIssueManager().update(redmineIssue);

        RedLab.logger.logInfo("updated redmine issue: " + redmineIssue.getId());
    }

    public User getUser(String userId) {
        if (userId == null || userId.equals(""))
            return null;

        return getUser( Integer.parseInt(userId) );
    }

    public User getUser(int userId) {
        return userHashMap.get(userId);
    }

    public IssueStatus getIssueStatus(String statusId) {
        if (statusId == null || statusId.equals(""))
            return null;

        return getIssueStatus( Integer.parseInt(statusId) );
    }

    public IssueStatus getIssueStatus(int statusId) {
        return issueStatusHashMap.get(statusId);
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

    public Tracker getTracker(String trackerId) {
        if (trackerId == null || trackerId.equals(""))
            return null;

        return getTracker( Integer.parseInt(trackerId) );
    }

    public Tracker getTracker(int trackerId) {
        return trackerHashMap.get(trackerId);
    }

    public IssuePriority getIssuePriority(String priorityId) {
        if (priorityId == null || priorityId.equals(""))
            return null;

        return getIssuePriority( Integer.parseInt(priorityId) );
    }

    public IssuePriority getIssuePriority(int priorityId) {
        return issuePriorityHashMap.get(priorityId);
    }

}
