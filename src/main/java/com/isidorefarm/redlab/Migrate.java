package com.isidorefarm.redlab;


import com.isidorefarm.redlab.api.GitLabAPIWrapper;
import com.isidorefarm.redlab.api.RedmineAPIWrapper;
import com.isidorefarm.redlab.config.ProjectMap;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.bean.*;
import org.gitlab.api.models.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
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
        sdf = new SimpleDateFormat("MM/dd/yyyy hh:mm aa");
    }

    public void run() throws RedmineException, IOException {
        redmineAPIWrapper = new RedmineAPIWrapper();
        gitLabAPIWrapper = new GitLabAPIWrapper();

        Project redmineProject = null;
        GitlabProject gitlabProject = null;

        // each project
        for (ProjectMap projectMap : RedLab.config.getProjectMapList()) {
            RedLab.logger.setCurrentRedmineProjectKey(projectMap.getRedmineKey());
            RedLab.logger.logInfo("PROJECT: " + projectMap.getRedmineKey());

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

            RedLab.logger.logInfo("project migrate complete.");
        }

    }

    private void migrateVersionsToMilestones(Project redmineProject, GitlabProject gitlabProject) throws RedmineException, IOException {
        List<Version> versions = redmineAPIWrapper.getVersions(redmineProject.getId());
        HashMap<String, GitlabMilestone> milestoneHashMap = gitLabAPIWrapper.getMilestoneHashMap(gitlabProject, false);

        RedLab.logger.logInfo(System.lineSeparator() + "VERSIONS:");

        // check if version exists as a milestone, add if not
        for (Version version : versions) {

            RedLab.logger.logInfo("checking version exists as milestone in gitlab: '" + version.getName() + "'");

            if ( !milestoneHashMap.containsKey(version.getName()) )
                gitLabAPIWrapper.createMilestone(
                        gitlabProject.getId(),
                        version.getName(),
                        version.getDescription(),
                        version.getDueDate()
                );
            else
                RedLab.logger.logInfo("milestone exists, skipping.");
        }

    }

    private void migrateIssues(Project redmineProject, GitlabProject gitlabProject) throws RedmineException, IOException {
        RedLab.logger.logInfo(System.lineSeparator() + "ISSUES:");

        // get all issues and milestones for project
        List<Issue> redmineIssues = redmineAPIWrapper.getIssues(redmineProject.getId());
        HashMap<String, GitlabIssue> gitlabIssueHashMap = gitLabAPIWrapper.getGitlabIssueHashMap(gitlabProject, false);
        HashMap<String, GitlabMilestone> milestoneHashMap = gitLabAPIWrapper.getMilestoneHashMap(gitlabProject, RedLab.config.isSafeMode());

        // each redmine issue
        GitlabMilestone gitlabMilestone = null;
        GitlabIssue existingGitlabIssue = null;
        for (Issue redmineIssue : redmineIssues) {

            // call again, to get full issue data
            redmineIssue = redmineAPIWrapper.getIssueById(redmineIssue.getId());

            RedLab.logger.logInfo(System.lineSeparator() + "ISSUE: '" + redmineIssue.getSubject() + "' (" + redmineIssue.getId() + ")");

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

            RedLab.logger.logInfo("ticket migrate complete.");
        }

    }

    private GitlabMilestone getCorrespondingGitlabMilestone(Issue redmineIssue, HashMap<String, GitlabMilestone> milestoneHashMap) throws Exception {
        GitlabMilestone gitlabMilestone = null;

        // issue has a target version
        if (redmineIssue.getTargetVersion() != null) {

            RedLab.logger.logInfo("redmine issue has target version.");

            // get corresponding gitlab milestone
            if (milestoneHashMap.containsKey(redmineIssue.getTargetVersion().getName())) {
                gitlabMilestone = milestoneHashMap.get(redmineIssue.getTargetVersion().getName());
                RedLab.logger.logInfo("found corresponding gitlab milestone: " + gitlabMilestone.getTitle());
            }
            // error, version wasn't previously imported
            else {
                RedLab.logger.logInfo("unable to lookup version as milestone in gitlab: " + redmineIssue.getTargetVersion().getName() + ", not adding issue.");
                throw new Exception();
            }
        }

        return gitlabMilestone;
    }

    private GitlabIssue getExistingGitlabIssue(Issue redmineIssue, HashMap<String, GitlabIssue> gitlabIssueHashMap) {
        GitlabIssue existingGitlabIssue = null;
        String issueTitle = getGitlabIssueTitle(redmineIssue);

        // lookup existing gitlab issue, if exists
        if (gitlabIssueHashMap.containsKey(issueTitle)) {
            existingGitlabIssue = gitlabIssueHashMap.get(issueTitle);
            RedLab.logger.logInfo("found existing gitlab issue (" + existingGitlabIssue.getIid() + ")");
        }

        return existingGitlabIssue;
    }

    private void migrateIssue(Issue redmineIssue, GitlabProject gitlabProject, GitlabMilestone gitlabMilestone, GitlabIssue existingGitlabIssue) throws IOException, RedmineException {
        User redmineAssignee = redmineAPIWrapper.getAssignee(redmineIssue);
        GitlabUser gitlabAssignee = lookupGitLabAssignee(redmineAssignee);
        GitlabIssue gitlabIssue = null;

        // use existing
        if (existingGitlabIssue != null) {
            gitlabIssue = existingGitlabIssue;
        }
        // new
        else {

            // tracker, priority -> labels
            String[] labels = {
                redmineIssue.getTracker().getName(),
                redmineIssue.getPriorityText() + " Priority"
            };

            // create gitlab issue
            gitlabIssue = gitLabAPIWrapper.createIssue(
                    gitlabProject.getId(),
                    (gitlabAssignee != null ? gitlabAssignee.getId() : -1),
                    (gitlabMilestone != null ? gitlabMilestone.getId() : -1),
                    gitLabAPIWrapper.implodeLabels(labels),
                    redmineIssue.getDescription(),
                    getGitlabIssueTitle(redmineIssue)
            );

        }

        // update gitlab issue as closed, if redmine ticket is closed
        setGitlabIssueClosed(redmineIssue, gitlabIssue);

        // migrate notes for this issue
        migrateNotes(redmineIssue, gitlabIssue);

        // close redmine ticket, if enabled
        closeRedmineIssue(redmineIssue, gitlabProject, gitlabIssue);

    }

    private void setGitlabIssueClosed(Issue redmineIssue, GitlabIssue gitlabIssue) throws IOException {

        // close ticket in gitlab
        IssueStatus issueStatus = redmineAPIWrapper.getIssueStatusHashMap().get(redmineIssue.getStatusId());
        if (!issueStatus.isClosed() || gitlabIssue.getState().equals(GitlabIssue.STATE_CLOSED)) {
            RedLab.logger.logInfo("redmine issue (" + redmineIssue.getId() + ") isn't closed or gitlab issue (" + gitlabIssue.getId() + ") is already closed, skipping gitlab issue close.");
            return;
        }

        // set issue closed
        gitLabAPIWrapper.editIssue(
                gitlabIssue.getProjectId(),
                gitlabIssue.getId(),
                (gitlabIssue.getAssignee() != null ? gitlabIssue.getAssignee().getId() : -1),
                (gitlabIssue.getMilestone() != null ? gitlabIssue.getMilestone().getId() : -1),
                gitLabAPIWrapper.implodeLabels(gitlabIssue.getLabels()),
                gitlabIssue.getDescription(),
                gitlabIssue.getTitle(),
                GitlabIssue.Action.CLOSE);

        // submit closed note
        Date closedDate = redmineAPIWrapper.getClosedOn(redmineIssue);
        gitLabAPIWrapper.createNote(
                gitlabIssue,
                "Closed On: " + (closedDate == null ? "null" : sdf.format(closedDate))
        );

    }

    private void migrateNotes(Issue redmineIssue, GitlabIssue gitlabIssue) throws IOException {
        HashMap<String, GitlabNote> existingNotes = getRedmineNotesAlreadyMigratedHashMap(gitlabIssue);

        // journals -> notes
        String message = null;
        String journalDetails = null;
        for (Journal journal: redmineIssue.getJournals()) {

            RedLab.logger.logInfo("processing journal (" + journal.getId() + ")");

            // already have journal/note migrated, skip.
            if ( existingNotes.containsKey( Integer.toString(journal.getId()) ) ) {
                RedLab.logger.logInfo("journal already exists as note on issue, skipping.");
                continue;
            }

            // specific actions other then a basic note
            journalDetails = getJournalDetails(journal);

            // create message with additional redmine info, for good historical data
            message =
                    "Updated by " + journal.getUser().getFullName() + " on " + (journal.getCreatedOn() == null ? "N/A" : sdf.format(journal.getCreatedOn())) + "\n\n" +
                    (journalDetails.equals("") ? "" : journalDetails + "\n\n") +
                    (journal.getNotes().equals("") ? "" : journal.getNotes() + "\n\n") +
                    "journal id: " + journal.getId();

            // submit note
            gitLabAPIWrapper.createNote(
                    gitlabIssue,
                    message
            );
        }
    }

    private String getJournalDetails(Journal journal) {
        String journalDetails = "";

        for (JournalDetail jd : journal.getDetails()) {

            journalDetails += "* "; // markdown list item

            if (RedLab.config.isDebugMode())
                RedLab.logger.logInfo("processing journal detail: " + jd.getName() + ", old: " + jd.getOldValue() + ", new: " + jd.getNewValue());

            switch (jd.getName()) {
                case "assigned_to_id":
                    User oldUser = redmineAPIWrapper.getUserHashMap().get( Integer.parseInt(jd.getOldValue()) );
                    User newUser = redmineAPIWrapper.getUserHashMap().get( Integer.parseInt(jd.getNewValue()) );
                    journalDetails += "Assignee updated from " + (oldUser == null ? "Unassigned" : oldUser.getFullName()) + " to " + (newUser == null ? "Unassigned" : newUser.getFullName());
                    break;
                case "status_id":
                    IssueStatus oldStatus = redmineAPIWrapper.getIssueStatusHashMap().get( Integer.parseInt(jd.getOldValue()) );
                    IssueStatus newStatus = redmineAPIWrapper.getIssueStatusHashMap().get( Integer.parseInt(jd.getNewValue()) );
                    journalDetails += "Status changed from " + (oldStatus == null ? "Not Set" : oldStatus.getName()) + " to " + (newStatus == null ? "Not Set" : newStatus.getName());
                    break;
                default:
                    journalDetails += jd.getName() + " was changed from " + jd.getOldValue() + " to " + jd.getNewValue();

            }

            journalDetails += "\n";

        }

        return journalDetails;
    }

    private void closeRedmineIssue(Issue redmineIssue, GitlabProject gitlabProject, GitlabIssue gitlabIssue) throws RedmineException {

        // set to run auto close and issue isn't already closed
        if (!RedLab.config.getRedmineOptions().autoCloseRedmineIssues() || redmineIssue.getStatusId() == RedLab.config.getRedmineOptions().getAutoCloseStatusId()) {
            RedLab.logger.logInfo("not auto closing redmine ticket, auto close is false or ticket already migrated.");
            return;
        }

        // add migrate note
        redmineIssue.setNotes("Issue migrated to GitLab in project " + gitlabProject.getName() + ", issue ID: " + gitlabIssue.getIid());

        // update status
        redmineIssue.setStatusId(RedLab.config.getRedmineOptions().getAutoCloseStatusId());

        // set closed if not already
        if (redmineIssue.getClosedOn() == null)
            redmineIssue.setClosedOn(new Date());

        redmineAPIWrapper.updateIssue(redmineIssue);

    }

    private String getGitlabIssueTitle(Issue redmineIssue) {
        return "RM: " + redmineIssue.getId() + " - " + redmineIssue.getSubject();
    }

    private GitlabUser lookupGitLabAssignee(User redmineAssignee) {

        if (redmineAssignee == null)
            return null;

        // try and match by email or usernames
        for (GitlabUser gitlabUser : gitLabAPIWrapper.getUsers()) {
            if (gitlabUser.getEmail().toLowerCase().equals(redmineAssignee.getMail().toLowerCase()) ||
                    gitlabUser.getUsername().toLowerCase().equals(redmineAssignee.getLogin().toLowerCase()) ) {

                RedLab.logger.logInfo("mapped assignee to gitlab assignee. '" + gitlabUser.getUsername() + "' (" + gitlabUser.getId() + ")");
                return gitlabUser;
            }
        }

        RedLab.logger.logInfo("unable to map redmine->gitlab user: " + redmineAssignee.getLogin() + ", defaulting.");
        return gitLabAPIWrapper.getDefaultAssignee();

    }

    // key: redmine journalID
    private HashMap<String, GitlabNote> getRedmineNotesAlreadyMigratedHashMap(GitlabIssue gitlabIssue) throws IOException {
        List<GitlabNote> notes = gitLabAPIWrapper.getNotes(gitlabIssue, RedLab.config.isSafeMode());
        Pattern p = Pattern.compile("journal id: (\\d+)");
        Matcher m;

        HashMap<String, GitlabNote> map = new HashMap<String, GitlabNote>();
        String journalID;
        for (GitlabNote note : notes) {
            m = p.matcher(note.getBody());

            // we have a redmine journal/note, save it
            if (m.find())
                journalID = m.group(1);
            // non redmine journal/note, skip
            else
                continue;

            map.put(journalID, note);
        }

        return map;
    }

}
