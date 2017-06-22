package com.isidorefarm.redlab.entities;


import org.gitlab.api.models.GitlabIssue;
import org.gitlab.api.models.GitlabMilestone;
import org.gitlab.api.models.GitlabNote;

import java.util.ArrayList;
import java.util.HashMap;

public class SafeModeEntities {

    private ArrayList<GitlabIssue> gitlabIssues;
    private HashMap<Integer, ArrayList<GitlabMilestone>> milestoneHashMap; // projectID, arraylist milestone
    private HashMap<Integer, ArrayList<GitlabNote>> notesHashMap; // issueID, array note

    public SafeModeEntities() {
        gitlabIssues = new ArrayList<GitlabIssue>();
        milestoneHashMap = new HashMap<Integer, ArrayList<GitlabMilestone>>();
        notesHashMap = new HashMap<Integer, ArrayList<GitlabNote>>();
    }

    public void reset() {
        gitlabIssues.clear();
        milestoneHashMap.clear();
        notesHashMap.clear();
    }

    public void addGitlabIssue(GitlabIssue issue) {

        // set fake id
        issue.setId( gitlabIssues.size() );

        gitlabIssues.add(issue);
    }

    public void addGitlabMileStone(GitlabMilestone milestone) {

        if (!milestoneHashMap.containsKey(milestone.getProjectId()))
            milestoneHashMap.put(milestone.getProjectId(), new ArrayList<GitlabMilestone>());

        // set fake id
        milestone.setId( milestoneHashMap.get(milestone.getProjectId()).size() );

        milestoneHashMap.get(milestone.getProjectId()).add(milestone);
    }

    public void addGitlabNote(int issueID, GitlabNote note) {

        if (!notesHashMap.containsKey(issueID))
            notesHashMap.put(issueID, new ArrayList<GitlabNote>());

        // set fake id
        note.setId( notesHashMap.get(issueID).size() );

        notesHashMap.get(issueID).add(note);
    }

    public ArrayList<GitlabIssue> getGitlabIssues() {
        return gitlabIssues;
    }

    public GitlabIssue getGitlabIssue(int issueID) {
        for (GitlabIssue issue : gitlabIssues)
            if (issue.getId() == issueID)
                return issue;

        return null;
    }

    public ArrayList<GitlabMilestone> getGitlabMilestones(int projectID) {

        if (!milestoneHashMap.containsKey(projectID))
            return new ArrayList<GitlabMilestone>();

        return milestoneHashMap.get(projectID);
    }

    public ArrayList<GitlabNote> getGitlabNotes(int issueID) {

        if (!notesHashMap.containsKey(issueID))
            return new ArrayList<GitlabNote>();

        return notesHashMap.get(issueID);
    }
}
