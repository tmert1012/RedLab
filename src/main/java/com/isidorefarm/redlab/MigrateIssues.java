package com.isidorefarm.redlab;


import com.isidorefarm.redlab.config.ProjectMap;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;

import java.util.List;

public class MigrateIssues {

    private RedmineManager redmineManager;


    public MigrateIssues() {
        redmineManager = RedmineManagerFactory.createWithApiKey(RedLab.config.getRedmine().getBaseURL(), RedLab.config.getRedmine().getApiKey());
    }

    public void run() throws RedmineException {
        List<Issue> redmineIssues = null;

        for (ProjectMap projectMap : RedLab.config.getProjectMapList()) {
            redmineIssues = getRedmineIssues(projectMap.getRedmineID());

            for (Issue redmineIssue : redmineIssues) {

            }

        }

    }

    private List<Issue> getRedmineIssues(String projectKey) throws RedmineException {
        Integer queryId = null; // any

        return redmineManager.getIssueManager().getIssues(projectKey, queryId);

    }

}
