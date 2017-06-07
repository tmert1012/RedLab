package com.isidorefarm.redlab;


import com.isidorefarm.redlab.api.GitLabAPIWrapper;
import com.isidorefarm.redlab.api.RedmineAPIWrapper;
import com.isidorefarm.redlab.config.ProjectMap;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Project;
import com.taskadapter.redmineapi.bean.User;
import com.taskadapter.redmineapi.bean.Version;
import org.gitlab.api.models.GitlabMilestone;
import org.gitlab.api.models.GitlabProject;
import org.gitlab.api.models.GitlabUser;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class Migrate {

    private RedmineAPIWrapper redmineAPIWrapper;
    private GitLabAPIWrapper gitLabAPIWrapper;


    public Migrate() {
        redmineAPIWrapper = null;
        gitLabAPIWrapper = null;
    }

    public void run() throws RedmineException, IOException {
        redmineAPIWrapper = new RedmineAPIWrapper();
        gitLabAPIWrapper = new GitLabAPIWrapper();

        Project redmineProject = null;
        GitlabProject gitlabProject = null;

        // each project
        for (ProjectMap projectMap : RedLab.config.getProjectMapList()) {
            RedLab.logInfo("\n\nprocessing project: " + projectMap.getRedmineKey());

            // get redmine project by key
            redmineProject = redmineAPIWrapper.getProjectByKey(projectMap.getRedmineKey());
            if (redmineProject == null)
                continue;

            // get gitlab project by key
            gitlabProject = gitLabAPIWrapper.getProjectByKey(projectMap.getGitLabKey());
            if (gitlabProject == null)
                continue;

            migrateVersionsToMilestones(redmineProject, gitlabProject);
            migrateIssues(redmineProject, gitlabProject);

        }

    }

    private void migrateVersionsToMilestones(Project redmineProject, GitlabProject gitlabProject) throws RedmineException, IOException {
        List<Version> versions = redmineAPIWrapper.getVersions(redmineProject.getId());
        HashMap<String, GitlabMilestone> milestoneHashMap = getMilestoneHashMap(gitlabProject);

        // check if version exists as a milestone, add if not
        for (Version version : versions) {
            if ( !milestoneHashMap.containsKey(version.getName()) )
                gitLabAPIWrapper.createMilestone(
                        gitlabProject.getId(),
                        version.getName(),
                        version.getDescription(),
                        version.getDueDate()
                );
        }

    }

    private void migrateIssues(Project redmineProject, GitlabProject gitlabProject) throws RedmineException, IOException {

        // get all issues for project
        List<Issue> redmineIssues = redmineAPIWrapper.getIssues(redmineProject.getId());

        // get all milestones
        HashMap<String, GitlabMilestone> milestoneHashMap = getMilestoneHashMap(gitlabProject);

        // each issue
        GitlabMilestone gitlabMilestone = null;
        for (Issue redmineIssue : redmineIssues) {
            gitlabMilestone = null;

            // issue has a target version
            if (redmineIssue.getTargetVersion() != null) {

                // get corresponding gitlab milestone
                if (milestoneHashMap.containsKey(redmineIssue.getTargetVersion().getName())) {
                    gitlabMilestone = milestoneHashMap.get(redmineIssue.getTargetVersion().getName());
                }
                // error, version wasn't previously imported
                else {
                    RedLab.logInfo("Unable to lookup version as milestone in GitLab: " + redmineIssue.getTargetVersion().getName() + ", not adding issue.");
                    continue;
                }
            }

            addIssue(redmineIssue, gitlabProject, gitlabMilestone);
        }

    }

    private HashMap<String, GitlabMilestone> getMilestoneHashMap(GitlabProject gitlabProject) throws IOException {
        List<GitlabMilestone> milestones = gitLabAPIWrapper.getMilestones(gitlabProject.getId());

        // key is version title or milestone name
        HashMap<String, GitlabMilestone> milestoneHashMap = new HashMap<String, GitlabMilestone>();
        for (GitlabMilestone milestone : milestones)
            milestoneHashMap.put(milestone.getTitle(), milestone);

        return milestoneHashMap;
    }

    private void addIssue(Issue redmineIssue, GitlabProject gitlabProject, GitlabMilestone gitlabMilestone) throws IOException {
        User redmineAssignee = redmineAPIWrapper.getAssignee(redmineIssue.getAssigneeId());
        GitlabUser gitlabAssignee = lookupGitLabAssignee(redmineAssignee);

        gitLabAPIWrapper.createIssue(
                gitlabProject.getId(),
                gitlabAssignee.getId(),
                (gitlabMilestone != null) ? gitlabMilestone.getId() : null,
                redmineIssue.getTracker().getName(),
                redmineIssue.getDescription(),
                "RM: " + redmineIssue.getId() + " - " + redmineIssue.getSubject()
        );
    }

    private GitlabUser lookupGitLabAssignee(User redmineAssignee) {

        // try and match by email or usernames
        for (GitlabUser gitlabUser : gitLabAPIWrapper.getUsers()) {
            if (gitlabUser.getEmail().toLowerCase().equals(redmineAssignee.getMail().toLowerCase()) ||
                    gitlabUser.getUsername().toLowerCase().equals(redmineAssignee.getLogin().toLowerCase()) )
                return gitlabUser;
        }

        RedLab.logInfo("Unable to map redmine->gitlab user: " + redmineAssignee.getLogin() + ", defaulting.");
        return gitLabAPIWrapper.getDefaultAssignee();

    }

}
