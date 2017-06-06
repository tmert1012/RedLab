package com.isidorefarm.redlab.api;


import com.isidorefarm.redlab.RedLab;
import org.gitlab.api.GitlabAPI;

public class GitLabAPI {

    private GitlabAPI gitlabAPI;

    public GitLabAPI() {
        gitlabAPI = GitlabAPI.connect(RedLab.config.getGitLabOptions().getBaseURL(), RedLab.config.getGitLabOptions().getApiKey());
    }

    public void addIssue() {

    }

}
