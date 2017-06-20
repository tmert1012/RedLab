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
    private HashMap<String, FileWriter> errorFileHashMap;
    private String currentRedmineProjectKey; // which project is currently being processed, optional

    public Logger() throws IOException {
        init();
    }

    private void init() throws IOException {
        currentRedmineProjectKey = null;
        logFileHashMap = new HashMap<String, FileWriter>();
        errorFileHashMap = new HashMap<String, FileWriter>();

        // create log dir
        File baseLogDir = new File(LOG_DIR);
        if (!baseLogDir.exists())
            baseLogDir.mkdir();

        // add default
        logFileHashMap.put(DEFAULT_LOG_KEY, new FileWriter(new File(LOG_DIR + File.separator + DEFAULT_LOG_KEY + ".log")));
        errorFileHashMap.put(DEFAULT_LOG_KEY, new FileWriter(new File(LOG_DIR + File.separator + DEFAULT_LOG_KEY + ".errors.log")));

        // set log file for each project
        for (ProjectMap projectMap : RedLab.config.getProjectMapList()) {

            logFileHashMap.put(
                    projectMap.getRedmineKey(),
                    new FileWriter(new File(LOG_DIR + File.separator + projectMap.getRedmineKey().trim().toLowerCase() + ".log"))
            );

            errorFileHashMap.put(
                    projectMap.getRedmineKey(),
                    new FileWriter(new File(LOG_DIR + File.separator + projectMap.getRedmineKey().trim().toLowerCase() + ".errors.log"))
            );
        }

    }

    public void closeLogging() {
        FileWriter logfw = null;
        FileWriter errorfw = null;

        // close both files for each project
        for (ProjectMap projectMap : RedLab.config.getProjectMapList()) {
            logfw = logFileHashMap.get(projectMap.getRedmineKey());
            errorfw = errorFileHashMap.get(projectMap.getRedmineKey());

            if (logfw != null)
                try { logfw.close(); } catch (IOException e) {}

            if (errorfw != null)
                try { errorfw.close(); } catch (IOException e) {}
        }

    }

    public void logError(String msg) {

        try {

            if (errorFileHashMap.containsKey(currentRedmineProjectKey))
                errorFileHashMap.get(currentRedmineProjectKey).write(msg + System.lineSeparator());
            else
                errorFileHashMap.get(DEFAULT_LOG_KEY).write(msg + System.lineSeparator());

            logInfo(msg);

        } catch (IOException e) {
            e.printStackTrace();
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
