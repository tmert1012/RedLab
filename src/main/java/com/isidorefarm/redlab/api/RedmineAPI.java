package com.isidorefarm.redlab.api;


import com.isidorefarm.redlab.RedLab;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Project;
import com.taskadapter.redmineapi.internal.ResultsWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RedmineAPI {

    final private int LIMIT = 100;

    private RedmineManager redmineManager;
    private List<Project> redmineProjects;

    public RedmineAPI() throws RedmineException {
        redmineManager = RedmineManagerFactory.createWithApiKey(RedLab.config.getRedmineOptions().getBaseURL(), RedLab.config.getRedmineOptions().getApiKey());

        // load all redmine projects
        redmineProjects = redmineManager.getProjectManager().getProjects();
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
            RedLab.logInfo("getting issues, iteration: " + callCount + ", issue count: " + issueCount);

            issues.addAll(resultsWrapper.getResults());

            params.put("offset", Integer.toString(LIMIT * callCount));
            callCount++;

        }

        return issues;
    }

    public Project getProjectByKey(String projectKey) {

        for (Project project : redmineProjects)
            if (project.getIdentifier().equals(projectKey))
                return project;

        RedLab.logInfo("Unable to lookup project by projectKey: " + projectKey + ", skipping.");
        return null;
    }


}
