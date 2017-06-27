# RedLab

## Redmine -> GitLab Issue Migration Tool

RedLab migrates issue data from Redmine into GitLab, should you need to switch over and retain issue data historically in GitLab. RedLab uses both [redmine-java-api](https://github.com/taskadapter/redmine-java-api) and [java-gitlab-api
](https://github.com/timols/java-gitlab-api). Redlab is duplicate aware and does it's best to not migrate data that already exists, should the process fail mid migration.

## Does
* Issues (Adds created and closed dates as Notes)
* Journals -> Notes (in similar format as Redmine)
* Versions -> Milestones
* Tracker, Priority -> Labels
* Maps Assignee (has default assignee option as well)
* Closes redmine ticket once migration complete (optional)

## Does Not
* Migrate attachments
* Update GitLab issue's created date. Issue created date is the date created in Gitlab. There is a note with original create date however.

## Usage
* Copy redlab-config.json.sample -> redlab-config.json
* Edit redlab-config.json
* Build project or use "gradle run"

## Suggested
Try migrating a test project in Redmine (with test issues) to a test project in GitLab so that you may review the migrated issues in GitLab. Then migrate a live redmine project to a dummy gitlab project with autoCloseRedmineIssues off. Make sure to test all the features above. You may also run with safeMode on, and no changes will be made to Redmine or GitLab. SafeMode just shows what it would do.
 
