package com.isidorefarm.redlab;


import com.isidorefarm.redlab.api.RedmineAPIWrapper;
import com.isidorefarm.redlab.config.ProjectMap;
import com.isidorefarm.redlab.entities.AttachmentStat;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.bean.Attachment;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Project;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class AttachmentOverview {

    private RedmineAPIWrapper redmineAPIWrapper;
    private HashMap<String, AttachmentStat> fileCountMap; // filename, attachment stat


    public AttachmentOverview() {
        redmineAPIWrapper = null;
        fileCountMap = new HashMap<String, AttachmentStat>();
    }

    public void run() throws RedmineException, IOException {
        redmineAPIWrapper = new RedmineAPIWrapper();

        Project project = null;
        List<Issue> issues = null;

        // each project
        for (ProjectMap projectMap : RedLab.config.getProjectMapList()) {

            // get redmine project by key
            project = redmineAPIWrapper.getProjectByKey(projectMap.getRedmineKey());
            if (project == null)
                continue;

            // get all issues and milestones for project
            issues = redmineAPIWrapper.getIssues(project.getId());

            // each redmine issue
            for (Issue redmineIssue : issues) {

                // call again, to get full issue data
                redmineIssue = redmineAPIWrapper.getIssueById(redmineIssue.getId());

                // each attachment
                AttachmentStat attachmentStat = null;
                for (Attachment attachment : redmineIssue.getAttachments()) {
                    String fileName = attachment.getFileName().toLowerCase().trim();

                    if (!fileCountMap.containsKey(fileName))
                        fileCountMap.put(fileName, new AttachmentStat(fileName));

                    attachmentStat = fileCountMap.get(fileName);
                    attachmentStat.increment();

                }

            }


        } // project

        printOverview();
    }

    public void printOverview() {

        ArrayList<AttachmentStat> attachmentStats = new ArrayList<AttachmentStat>();
        attachmentStats.addAll(fileCountMap.values());

        Collections.sort(attachmentStats);

        for (AttachmentStat attachmentStat : attachmentStats) {

            if (attachmentStat.getCount() > 3)
                System.out.println(attachmentStat.getFilename() + ": " + attachmentStat.getCount());
        }

    }


}
