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

    public void addGitlabIssue(GitlabIssue issue) {
        gitlabIssues.add(issue);
    }

    public void addGitlabMileStone(GitlabMilestone milestone) {

        if (!milestoneHashMap.containsKey(milestone.getProjectId()))
            milestoneHashMap.put(milestone.getProjectId(), new ArrayList<GitlabMilestone>());

        milestoneHashMap.get(milestone.getProjectId()).add(milestone);
    }

    public void addGitlabNote(int issueID, GitlabNote note) {

        if (!notesHashMap.containsKey(issueID))
            notesHashMap.put(issueID, new ArrayList<GitlabNote>());

        notesHashMap.get(issueID).add(note);
    }

    public ArrayList<GitlabIssue> getGitlabIssues() {
        return gitlabIssues;
    }

    public ArrayList<GitlabMilestone> getGitlabMilestones(int projectID) {
        return milestoneHashMap.get(projectID);
    }

    public ArrayList<GitlabNote> getGitlabNotes(int issueID) {
        return notesHashMap.get(issueID);
    }
}
