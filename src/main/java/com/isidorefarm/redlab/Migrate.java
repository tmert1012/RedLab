package com.isidorefarm.redlab;


import com.isidorefarm.redlab.api.GitLabAPIWrapper;
import com.isidorefarm.redlab.api.RedmineAPIWrapper;
import com.isidorefarm.redlab.config.ProjectMap;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.bean.*;
import org.gitlab.api.models.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
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

            // clear out previous project info
            redmineAPIWrapper.reset();
            gitLabAPIWrapper.reset();

            RedLab.logger.setCurrentRedmineProjectKey(projectMap.getRedmineKey());
            RedLab.logger.logInfo("PROJECT: " + projectMap.getRedmineKey());

            // get redmine project by key
            redmineProject = redmineAPIWrapper.getProjectByKey(projectMap.getRedmineKey());
            if (redmineProject == null)
                continue;

            // get gitlab project by key
            gitlabProject = gitLabAPIWrapper.getProject(projectMap);
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

        RedLab.logger.logInfo(System.lineSeparator() + "VERSIONS:");

        // check if version exists as a milestone, add if not
        GitlabMilestone milestone;
        for (Version version : versions) {
            milestone = null;

            RedLab.logger.logInfo("checking version exists as milestone in gitlab: '" + version.getName() + "', " + version.getStatus());

            milestone = gitLabAPIWrapper.getMilestoneByTitle(gitlabProject, version.getName());

            if (milestone == null) {
                String state = version.getStatus().equals("closed") ? "close" : "activate";

                milestone = new GitlabMilestone();
                milestone.setCreatedDate(version.getCreatedOn());
                milestone.setDescription(version.getDescription());
                milestone.setDueDate(version.getDueDate());
                milestone.setProjectId(gitlabProject.getId());
                milestone.setState(state);
                milestone.setTitle(version.getName());
                milestone.setUpdatedDate(version.getUpdatedOn());

                milestone = gitLabAPIWrapper.createMilestone(gitlabProject.getId(), milestone);
                gitLabAPIWrapper.updateMilestone(milestone, state);
            }
            else
                RedLab.logger.logInfo("milestone exists, skipping.");
        }

    }

    private void migrateIssues(Project redmineProject, GitlabProject gitlabProject) throws RedmineException, IOException {
        RedLab.logger.logInfo(System.lineSeparator() + "ISSUES:");

        // get all issues and milestones for project
        List<Issue> redmineIssues = redmineAPIWrapper.getIssues(redmineProject.getId());

        // each redmine issue
        GitlabMilestone gitlabMilestone = null;
        GitlabIssue existingGitlabIssue = null;
        for (Issue redmineIssue : redmineIssues) {

            // call again, to get full issue data
            redmineIssue = redmineAPIWrapper.getIssueById(redmineIssue.getId());

            RedLab.logger.logInfo(System.lineSeparator() + "ISSUE: '" + redmineIssue.getSubject() + "' (" + redmineIssue.getId() + ")");

            // lookup existing milestone. if redmine ticket has version but can't look it up in gitlab, error.
            try {
                gitlabMilestone = getCorrespondingGitlabMilestone(redmineIssue, gitlabProject);
            } catch (Exception e) {
                continue;
            }

            // check for existing issue, don't want dupe issues
            existingGitlabIssue = getExistingGitlabIssue(gitlabProject, redmineIssue);

            // migrate issue and notes
            migrateIssue(redmineIssue, gitlabProject, gitlabMilestone, existingGitlabIssue);

            RedLab.logger.logInfo("ticket migrate complete.");
        }

    }

    private GitlabMilestone getCorrespondingGitlabMilestone(Issue redmineIssue, GitlabProject gitlabProject) throws Exception {
        GitlabMilestone gitlabMilestone = null;

        // issue has a target version
        if (redmineIssue.getTargetVersion() != null) {

            RedLab.logger.logInfo("redmine issue has target version.");

            // get corresponding gitlab milestone
            gitlabMilestone = gitLabAPIWrapper.getMilestoneByTitle(gitlabProject, redmineIssue.getTargetVersion().getName());

            if (gitlabMilestone != null) {
                RedLab.logger.logInfo("found corresponding gitlab milestone: " + gitlabMilestone.getTitle());
            }
            // error, version wasn't previously imported
            else {
                RedLab.logger.logError("unable to lookup version as milestone in gitlab: " + redmineIssue.getTargetVersion().getName() + ", not adding issue.");
                throw new Exception();
            }
        }

        return gitlabMilestone;
    }

    private GitlabIssue getExistingGitlabIssue(GitlabProject gitlabProject, Issue redmineIssue) throws IOException {
        String issueTitle = getGitlabIssueTitle(redmineIssue);
        GitlabIssue existingGitlabIssue = gitLabAPIWrapper.getIssueByTitle(gitlabProject, issueTitle);

        // lookup existing gitlab issue, if exists
        if (existingGitlabIssue != null)
            RedLab.logger.logInfo("found existing gitlab issue (" + existingGitlabIssue.getIid() + ")");

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

        // add created date note
        gitLabAPIWrapper.createNote(
                gitlabIssue,
                "Added by " + redmineIssue.getAuthorName() + " on " + sdf.format(redmineIssue.getCreatedOn())
        );

        // migrate notes for this issue
        migrateNotes(redmineIssue, gitlabIssue);

        // update gitlab issue as closed, if redmine ticket is closed
        setGitlabIssueClosed(redmineIssue, gitlabIssue);

        // close redmine ticket, if enabled
        closeRedmineIssue(redmineIssue, gitlabProject, gitlabIssue);

    }

    private void setGitlabIssueClosed(Issue redmineIssue, GitlabIssue gitlabIssue) throws IOException {

        // close ticket in gitlab
        IssueStatus issueStatus = redmineAPIWrapper.getIssueStatus(redmineIssue.getStatusId());
        if (!issueStatus.isClosed() || (gitlabIssue.getState() != null && gitlabIssue.getState().equals(GitlabIssue.STATE_CLOSED))) {
            RedLab.logger.logInfo("redmine issue (" + redmineIssue.getId() + ") isn't closed or gitlab issue (" + gitlabIssue.getId() + ") is already closed, skipping gitlab issue close.");
            return;
        }

        // submit closed note
        Date closedDate = redmineAPIWrapper.getClosedOn(redmineIssue);
        gitLabAPIWrapper.createNote(
                gitlabIssue,
                "Closed On: " + (closedDate == null ? "null" : sdf.format(closedDate))
        );

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

    }

    private void migrateNotes(Issue redmineIssue, GitlabIssue gitlabIssue) throws IOException {
        HashMap<String, GitlabNote> existingNotes = getRedmineNotesAlreadyMigratedHashMap(gitlabIssue);

        // sort list to preserve created order
        ArrayList<Journal> journals = new ArrayList<Journal>();
        journals.addAll(redmineIssue.getJournals());

        Collections.sort(journals, new Comparator<Journal>() {

            @Override
            public int compare(Journal o1, Journal o2) {
                return o1.getCreatedOn().compareTo(o2.getCreatedOn());
            }

        });

        // journals -> notes
        String message = null;
        String journalDetails = null;
        for (Journal journal: journals) {

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
                    (journalDetails == null || journalDetails.equals("") ? "" : journalDetails + "\n\n") +
                    (journal.getNotes() == null || journal.getNotes().equals("") ? "" : journal.getNotes() + "\n\n") +
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
                    User oldUser = redmineAPIWrapper.getUser(jd.getOldValue());
                    User newUser = redmineAPIWrapper.getUser(jd.getNewValue());
                    journalDetails += "**assignee** updated from *" + (oldUser == null ? "Unassigned" : oldUser.getFullName()) + "* to *" + (newUser == null ? "Unassigned" : newUser.getFullName()) + "*";
                    break;
                case "status_id":
                    IssueStatus oldStatus = redmineAPIWrapper.getIssueStatus(jd.getOldValue());
                    IssueStatus newStatus = redmineAPIWrapper.getIssueStatus(jd.getNewValue());
                    journalDetails += "**status** changed from *" + (oldStatus == null ? "Not Set" : oldStatus.getName()) + "* to *" + (newStatus == null ? "Not Set" : newStatus.getName()) + "*";
                    break;
                case "project_id":
                    Project oldProject = redmineAPIWrapper.getProjectById(jd.getOldValue());
                    Project newProject = redmineAPIWrapper.getProjectById(jd.getNewValue());
                    journalDetails += "**project** changed from *" + (oldProject == null ? "Not Set" : oldProject.getName()) + "* to *" + (newProject == null ? "Not Set" : newProject.getName()) + "*";
                    break;
                case "tracker_id":
                    Tracker oldTracker = redmineAPIWrapper.getTracker(jd.getOldValue());
                    Tracker newTracker = redmineAPIWrapper.getTracker(jd.getNewValue());
                    journalDetails += "**tracker** changed from *" + (oldTracker == null ? "Not Set" : oldTracker.getName()) + "* to *" + (newTracker == null ? "Not Set" : newTracker.getName()) + "*";
                    break;
                case "priority_id":
                    IssuePriority oldPriority = redmineAPIWrapper.getIssuePriority(jd.getOldValue());
                    IssuePriority newPriority = redmineAPIWrapper.getIssuePriority(jd.getNewValue());
                    journalDetails += "**priority** changed from *" + (oldPriority == null ? "Not Set" : oldPriority.getName()) + "* to *" + (newPriority == null ? "Not Set" : newPriority.getName()) + "*";
                    break;
                case "fixed_version_id":
                    Version oldVersion = redmineAPIWrapper.getVersion(jd.getOldValue());
                    Version newVersion = redmineAPIWrapper.getVersion(jd.getNewValue());
                    journalDetails += "**version** changed from *" + (oldVersion == null ? "Not Set" : oldVersion.getName()) + "* to *" + (newVersion == null ? "Not Set" : newVersion.getName()) + "*";
                    break;
                default:
                    journalDetails += "**" + jd.getName() + "** was changed from *" + jd.getOldValue() + "* to *" + jd.getNewValue() + "*";

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

    private GitlabUser lookupGitLabAssignee(User redmineAssignee) throws IOException {

        if (redmineAssignee == null)
            return null;

        // search
        ArrayList<GitlabUser> users = new ArrayList<GitlabUser>();
        users.addAll( gitLabAPIWrapper.findUser(redmineAssignee.getLogin().toLowerCase()) );
        if (users.size() == 0)
            users.addAll( gitLabAPIWrapper.findUser(redmineAssignee.getMail().toLowerCase()) );


        if (users.size() == 1) {
            RedLab.logger.logInfo("found gitlab assignee '" + users.get(0).getUsername() + "'");
            return users.get(0);
        }

        if (users.size() > 1) {
            RedLab.logger.logError("multiple results for gitlab assignee with (" + redmineAssignee.getLogin().toLowerCase() + " or " + redmineAssignee.getMail().toLowerCase() + "), using first result.");
            return users.get(0);
        }

        RedLab.logger.logError("unable to map redmine->gitlab user: " + redmineAssignee.getLogin() + ", using default assignee.");
        return gitLabAPIWrapper.getDefaultAssignee();

    }

    // key: redmine journalID
    private HashMap<String, GitlabNote> getRedmineNotesAlreadyMigratedHashMap(GitlabIssue gitlabIssue) throws IOException {
        List<GitlabNote> notes = gitLabAPIWrapper.getNotes(gitlabIssue);
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
