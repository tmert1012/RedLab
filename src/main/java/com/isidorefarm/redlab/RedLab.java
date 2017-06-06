package com.isidorefarm.redlab;

import com.isidorefarm.redlab.config.Config;

import java.io.IOException;


public class RedLab extends CommandLineProject {

    public static Config config;


    public static void main(String[] args) {
        parseCommandLineArgs(args);

        try {

            // load redlab-config.json
            config = Config.load();

            MigrateIssues migrateIssues = new MigrateIssues();
            migrateIssues.run();


        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
