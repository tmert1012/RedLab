package com.isidorefarm.redlab;


import com.isidorefarm.redlab.api.GitLabAPIWrapper;
import com.isidorefarm.redlab.api.RedmineAPIWrapper;
import com.isidorefarm.redlab.config.ProjectMap;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.bean.*;
import org.gitlab.api.models.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Migrate {

    private RedmineAPIWrapper redmineAPIWrapper;
    private GitLabAPIWrapper gitLabAPIWrapper;
    private SimpleDateFormat sdf;


    public Migrate() {
        redmineAPIWrapper = null;
        gitLabAPIWrapper = null;
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
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

            // make sure all versions exist as milestones in gitlab
            migrateVersionsToMilestones(redmineProject, gitlabProject);

            // main migration
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

        // get all issues and milestones for project
        List<Issue> redmineIssues = redmineAPIWrapper.getIssues(redmineProject.getId());
        HashMap<String, GitlabIssue> gitlabIssueHashMap = getGitlabIssueHashMap(gitlabProject);
        HashMap<String, GitlabMilestone> milestoneHashMap = getMilestoneHashMap(gitlabProject);

        // each redmine issue
        GitlabMilestone gitlabMilestone = null;
        GitlabIssue existingGitlabIssue = null;
        for (Issue redmineIssue : redmineIssues) {

            // lookup existing milestone. if redmine ticket has version but can't look it up in gitlab, error.
            try {
                gitlabMilestone = getCorrespondingGitlabMilestone(redmineIssue, milestoneHashMap);
            } catch (Exception e) {
               continue;
            }

            // check for existing issue, don't want dupe issues
            existingGitlabIssue = getExistingGitlabIssue(redmineIssue, gitlabIssueHashMap);

            // migrate issue and notes
            migrateIssue(redmineIssue, gitlabProject, gitlabMilestone, existingGitlabIssue);
        }

    }

    private GitlabMilestone getCorrespondingGitlabMilestone(Issue redmineIssue, HashMap<String, GitlabMilestone> milestoneHashMap) throws Exception {
        GitlabMilestone gitlabMilestone = null;

        // issue has a target version
        if (redmineIssue.getTargetVersion() != null) {

            // get corresponding gitlab milestone
            if (milestoneHashMap.containsKey(redmineIssue.getTargetVersion().getName())) {
                gitlabMilestone = milestoneHashMap.get(redmineIssue.getTargetVersion().getName());
            }
            // error, version wasn't previously imported
            else {
                RedLab.logInfo("Unable to lookup version as milestone in GitLab: " + redmineIssue.getTargetVersion().getName() + ", not adding issue.");
                throw new Exception();
            }
        }

        return gitlabMilestone;
    }

    private GitlabIssue getExistingGitlabIssue(Issue redmineIssue, HashMap<String, GitlabIssue> gitlabIssueHashMap) {
        GitlabIssue existingGitlabIssue = null;
        String issueTitle = getGitlabIssueTitle(redmineIssue);

        // lookup existing gitlab issue, if exists
        if (gitlabIssueHashMap.containsKey(issueTitle))
            existingGitlabIssue = gitlabIssueHashMap.get(issueTitle);

        return existingGitlabIssue;
    }

    private void migrateIssue(Issue redmineIssue, GitlabProject gitlabProject, GitlabMilestone gitlabMilestone, GitlabIssue existingGitlabIssue) throws IOException {
        User redmineAssignee = redmineAPIWrapper.getAssignee(redmineIssue.getAssigneeId());
        GitlabUser gitlabAssignee = lookupGitLabAssignee(redmineAssignee);
        GitlabIssue gitlabIssue = null;

        // use existing
        if (existingGitlabIssue != null) {
            gitlabIssue = existingGitlabIssue;
        }
        // new
        else {

            gitlabIssue = gitLabAPIWrapper.createIssue(
                    gitlabProject.getId(),
                    gitlabAssignee.getId(),
                    (gitlabMilestone != null) ? gitlabMilestone.getId() : null,
                    redmineIssue.getTracker().getName(),
                    redmineIssue.getDescription(),
                    getGitlabIssueTitle(redmineIssue)
            );

        }

        // migrate notes for this issue
        migrateNotes(redmineIssue, gitlabProject, gitlabIssue);

    }

    private void migrateNotes(Issue redmineIssue, GitlabProject gitlabProject, GitlabIssue gitlabIssue) throws IOException {
        HashMap<String, GitlabNote> existingNotes = getRedmineNotesAlreadyMigratedHashMap(gitlabIssue);

        // journals -> notes
        String message = null;
        for (Journal journal: redmineIssue.getJournals()) {

            // already have journal/note migrated, skip.
            if ( existingNotes.containsKey( Integer.toString(journal.getId()) ) ) {
                RedLab.logInfo("Note already exists on issue, skipping. journalID: " + journal.getId());
                continue;
            }

            // create message with additional redmine info, for good historical data
            message =
                    "Redmine Note:\n" +
                    "[[journalID:" + journal.getId() + "]]\n" +
                    "Added By: " + journal.getUser().getFullName() + " (" + journal.getUser().getLogin() + ")" + "\n" +
                    "Added On: " + (journal.getCreatedOn() == null ? "N/A" : sdf.format(journal.getCreatedOn())) + "\n\n" +
                    journal.getNotes();

            // submit note
            gitLabAPIWrapper.createNote(
                    gitlabProject.getId(),
                    gitlabIssue.getId(),
                    message
            );
        }
    }

    private String getGitlabIssueTitle(Issue redmineIssue) {
        return "RM: " + redmineIssue.getId() + " - " + redmineIssue.getSubject();
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

    // key milestone title
    private HashMap<String, GitlabMilestone> getMilestoneHashMap(GitlabProject gitlabProject) throws IOException {
        List<GitlabMilestone> milestones = gitLabAPIWrapper.getMilestones(gitlabProject.getId());

        // key is version title or milestone name
        HashMap<String, GitlabMilestone> milestoneHashMap = new HashMap<String, GitlabMilestone>();
        for (GitlabMilestone milestone : milestones)
            milestoneHashMap.put(milestone.getTitle(), milestone);

        return milestoneHashMap;
    }

    // key: issue title
    private HashMap<String, GitlabIssue> getGitlabIssueHashMap(GitlabProject gitlabProject) throws IOException {
        List<GitlabIssue> issues = gitLabAPIWrapper.getIssues(gitlabProject);

        HashMap<String, GitlabIssue> map = new HashMap<String, GitlabIssue>();
        for (GitlabIssue issue : issues)
            map.put(issue.getTitle(), issue);

        return map;
    }

    // key: redmine journalID
    private HashMap<String, GitlabNote> getRedmineNotesAlreadyMigratedHashMap(GitlabIssue gitlabIssue) throws IOException {
        List<GitlabNote> notes = gitLabAPIWrapper.getNotes(gitlabIssue);
        Pattern p = Pattern.compile("^*\\[\\[journalID:(\\d+)\\]\\]*$");
        Matcher m;

        HashMap<String, GitlabNote> map = new HashMap<String, GitlabNote>();
        String journalID;
        for (GitlabNote note : notes) {
            m = p.matcher(note.getBody());

            // we have a redmine journal/note, save it
            if (m.matches())
                journalID = m.group(1);
            // non redmine journal/note, skip
            else
                continue;

            map.put(journalID, note);
        }

        return map;
    }

}
