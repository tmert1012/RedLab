package com.isidorefarm.redlab;

import java.util.HashMap;

public class CommandLineProject {

    protected static HashMap<String, String> commandLineArgs;

    public CommandLineProject() {
    }

    protected static void parseCommandLineArgs(String [] args) {
        commandLineArgs = new HashMap<String, String>();
        String name = "";
        String value = "";

        // process args
        for (int i = 0; i < args.length; i++) {
            name = args[i].trim();
            value = args[++i].trim();

            // remove dashes
            name = name.replaceAll("-", "").trim();

            System.out.println("name: " + name + ", value: " + value);

            // sanity check
            if (name.isEmpty())
                continue;

            // check for help name, set value
            if ( name.equals("help") )
                value = "help";

            // set pair
            commandLineArgs.put(name, value);

        }

    }

    public static boolean haveGoodArg(String name) {

        if ( commandLineArgs.containsKey(name) && !(commandLineArgs.get(name).isEmpty()) )
            return true;

        return false;
    }

    public static String getArg(String name) {
        return commandLineArgs.get(name);
    }


}
