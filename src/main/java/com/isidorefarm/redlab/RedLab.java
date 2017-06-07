package com.isidorefarm.redlab;

import com.isidorefarm.redlab.config.Config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


public class RedLab extends CommandLineProject {

    public static Config config;
    private static FileWriter logFileWriter;


    public static void main(String[] args) {
        parseCommandLineArgs(args);

        try {

            // load redlab-config.json
            config = Config.load();

            // set log file
            logFileWriter = new FileWriter(new File("redlab.log"));

            logInfo(config.toString());

            // copy redmine issues to gitlab, based on Config
            Migrate migrate = new Migrate();
            migrate.run();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (logFileWriter != null) try { logFileWriter.close(); } catch (IOException e) {}
        }

    }

    public static void logInfo(String msg) {
        System.out.println(msg);

        try {
            logFileWriter.write(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
