package com.isidorefarm.redlab;

import com.isidorefarm.redlab.config.Config;
import com.isidorefarm.redlab.entities.Logger;


public class RedLab extends CommandLineProject {

    public static Config config;
    public static Logger logger;

    public static void main(String[] args) {
        parseCommandLineArgs(args);

        try {

            config = Config.load();
            logger = new Logger();


            // just dump config and bail
            if (haveGoodArg("checkConfig")) {
                logger.logInfo(config.toString());
                return;
            }

            // give attachment overview summary. shows counts by filename. good for spotting files to ignore, such as email footer images.
            else if (haveGoodArg("attachmentOverview")) {
                AttachmentOverview ao = new AttachmentOverview();
                ao.run();
            }

            // run migration
            else {
                Migrate migrate = new Migrate();
                migrate.run();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            logger.closeLogging();
        }

    }

}
