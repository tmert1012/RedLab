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
        HashMap<String, GitlabMilestone> milestoneHashMap = gitLabAPIWrapper.getMilestoneHashMap(gitlabProject, false);

        // check if version exists as a milestone, add if not
        for (Version version : versions) {

            RedLab.logInfo("checking version: " + version.getName());

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
        HashMap<String, GitlabIssue> gitlabIssueHashMap = gitLabAPIWrapper.getGitlabIssueHashMap(gitlabProject, false);
        HashMap<String, GitlabMilestone> milestoneHashMap = gitLabAPIWrapper.getMilestoneHashMap(gitlabProject, RedLab.config.isSafeMode());

        // each redmine issue
        GitlabMilestone gitlabMilestone = null;
        GitlabIssue existingGitlabIssue = null;
        for (Issue redmineIssue : redmineIssues) {

            // call again, to get full issue data
            redmineIssue = redmineAPIWrapper.getIssueById(redmineIssue.getId());

            RedLab.logInfo("\n\nMIGRATE Redmine Issue: '" + redmineIssue.getSubject() + "' (" + redmineIssue.getId() + ")");

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

            RedLab.logInfo("redmine issue has target version.");

            // get corresponding gitlab milestone
            if (milestoneHashMap.containsKey(redmineIssue.getTargetVersion().getName())) {
                gitlabMilestone = milestoneHashMap.get(redmineIssue.getTargetVersion().getName());
                RedLab.logInfo("Found corresponding gitlab milestone: " + gitlabMilestone.getTitle());
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
        if (gitlabIssueHashMap.containsKey(issueTitle)) {
            existingGitlabIssue = gitlabIssueHashMap.get(issueTitle);
            RedLab.logInfo("Found existing Gitlab issue: " + existingGitlabIssue.getTitle());
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

            gitlabIssue = gitLabAPIWrapper.createIssue(
                    gitlabProject.getId(),
                    (gitlabAssignee != null ? gitlabAssignee.getId() : -1),
                    (gitlabMilestone != null ? gitlabMilestone.getId() : -1),
                    redmineIssue.getTracker().getName(),
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
        if (redmineIssue.getClosedOn() == null || gitlabIssue.getState().equals(GitlabIssue.STATE_CLOSED)) {
            RedLab.logInfo("Can't close gitlab issue. redmine issue (" + redmineIssue.getId() + ") isn't closed or gitlab issue (" + gitlabIssue.getId() + ") is already closed, skipping.");
            return;
        }

        String labelStr = "";
        for (String label : gitlabIssue.getLabels())
            labelStr += label + " ";

        gitLabAPIWrapper.editIssue(
                gitlabIssue.getProjectId(),
                gitlabIssue.getId(),
                gitlabIssue.getAssignee().getId(),
                gitlabIssue.getMilestone().getId(),
                labelStr.trim(),
                gitlabIssue.getDescription() + "\n\n" + "Closed On: " + sdf.format(redmineIssue.getClosedOn()),
                gitlabIssue.getTitle(),
                GitlabIssue.Action.CLOSE);

    }

    private void migrateNotes(Issue redmineIssue, GitlabIssue gitlabIssue) throws IOException {
        HashMap<String, GitlabNote> existingNotes = getRedmineNotesAlreadyMigratedHashMap(gitlabIssue);

        // journals -> notes
        String message = null;
        String journalDetails = null;
        for (Journal journal: redmineIssue.getJournals()) {

            RedLab.logInfo("processing journal/note: " + journal.getId());

            // already have journal/note migrated, skip.
            if ( existingNotes.containsKey( Integer.toString(journal.getId()) ) ) {
                RedLab.logInfo("Note already exists on issue, skipping. journalID: " + journal.getId());
                continue;
            }

            // specific actions other then a basic note
            journalDetails = getJournalDetails(journal);

            // create message with additional redmine info, for good historical data
            message =
                    "Updated by " + journal.getUser().getFullName() + " (" + (journal.getCreatedOn() == null ? "N/A" : sdf.format(journal.getCreatedOn())) + ")" + "\n\n" +
                    (journal.getNotes().equals("") ? "" : journal.getNotes() + "\n\n") +
                    (journalDetails.equals("") ? "" : journalDetails + "\n\n") +
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

            switch (jd.getName()) {
                case "assigned_to_id":
                    journalDetails += "Assignee updated from " + jd.getOldValue() + " to " + jd.getNewValue();
                    break;
                case "status_id":
                    journalDetails += "Status changed from " + jd.getOldValue() + " to " + jd.getNewValue();
                    break;
                default:
                    RedLab.logInfo("Unknown journal detail name: " + jd.getName());
                    journalDetails += jd.getName() + " from " + jd.getOldValue() + " to " + jd.getNewValue();
            }

        }

        return journalDetails;
    }

    private void closeRedmineIssue(Issue redmineIssue, GitlabProject gitlabProject, GitlabIssue gitlabIssue) throws RedmineException {

        // set to run auto close and issue isn't already closed
        if (!RedLab.config.getRedmineOptions().autoCloseRedmineIssues() || redmineIssue.getStatusId() == RedLab.config.getRedmineOptions().getAutoCloseStatusId()) {
            RedLab.logInfo("Not auto closing ticket, auto close is false or ticket already migrated.");
            return;
        }

        // add migrate note
        redmineIssue.setNotes("Issue migrated to GitLab with RedLab. project: " + gitlabProject.getName() + ", issueID: " + gitlabIssue.getId());

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

                RedLab.logInfo("Mapped redmineAssignee to gitlab assignee: " + gitlabUser.getUsername() + " (" + gitlabUser.getId() + ")");
                return gitlabUser;
            }
        }

        RedLab.logInfo("Unable to map redmine->gitlab user: " + redmineAssignee.getLogin() + ", defaulting.");
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
