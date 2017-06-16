package com.isidorefarm.redlab.entities;

import com.isidorefarm.redlab.RedLab;
import com.isidorefarm.redlab.config.ProjectMap;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;


public class Logger {

    private final static String LOG_DIR = "logs";
    private final static String DEFAULT_LOG_KEY = "redlab";

    private HashMap<String, FileWriter> logFileHashMap;
    private String currentRedmineProjectKey; // which project is currently being processed, optional

    public Logger() throws IOException {
        init();
    }

    private void init() throws IOException {
        currentRedmineProjectKey = null;
        logFileHashMap = new HashMap<String, FileWriter>();

        // create log dir
        File baseLogDir = new File(LOG_DIR);
        if (!baseLogDir.exists())
            baseLogDir.mkdir();

        // add default
        logFileHashMap.put(DEFAULT_LOG_KEY, new FileWriter(new File(LOG_DIR + File.separator + DEFAULT_LOG_KEY + ".log")));

        // set log file for each project
        for (ProjectMap projectMap : RedLab.config.getProjectMapList()) {

            logFileHashMap.put(
                    projectMap.getRedmineKey(),
                    new FileWriter(new File(LOG_DIR + File.separator + projectMap.getRedmineKey().trim().toLowerCase() + ".log"))
            );
        }

    }

    public void closeLogging() {
        FileWriter fw = null;

        // set log file for each project
        for (ProjectMap projectMap : RedLab.config.getProjectMapList()) {
            fw = logFileHashMap.get(projectMap.getRedmineKey());

            if (fw != null)
                try { fw.close(); } catch (IOException e) {}
        }

    }

    public void logInfo(String msg) {
        System.out.println(msg);

        try {

            if (logFileHashMap.containsKey(currentRedmineProjectKey))
                logFileHashMap.get(currentRedmineProjectKey).write(msg + System.lineSeparator());
            else
                logFileHashMap.get(DEFAULT_LOG_KEY).write(msg + System.lineSeparator());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setCurrentRedmineProjectKey(String redmineProjectKey) {
        this.currentRedmineProjectKey = redmineProjectKey;
    }

}
