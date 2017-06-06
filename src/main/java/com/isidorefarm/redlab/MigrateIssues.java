package com.isidorefarm.redlab;


import com.isidorefarm.redlab.api.RedmineAPI;
import com.isidorefarm.redlab.config.ProjectMap;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Project;

import java.util.List;

public class MigrateIssues {

    private RedmineAPI redmineAPI;

    public MigrateIssues() {
        redmineAPI = null;
    }

    public void run() throws RedmineException {
        redmineAPI = new RedmineAPI();

        List<Issue> redmineIssues = null;
        Project redmineProject = null;
        for (ProjectMap projectMap : RedLab.config.getProjectMapList()) {
            RedLab.logInfo("\n\nprocessing project: " + projectMap.getRedmineKey());

            // get project by key
            redmineProject = redmineAPI.getProjectByKey(projectMap.getRedmineKey());
            if (redmineProject == null)
                continue;

            // get all issues for project
            redmineIssues = redmineAPI.getIssues(redmineProject.getId());

            // debug
            for (Issue redmineIssue : redmineIssues) {
                RedLab.logInfo("issue: " + redmineIssue.getId() + " - " + redmineIssue.getSubject());
            }

        }

    }


}
