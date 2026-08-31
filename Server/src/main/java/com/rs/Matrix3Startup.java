package com.rs;

import java.io.File;
import java.io.IOException;

/**
 * Eclipse-only bootstrap that hands normal Matrix3 startup to run_game.bat.
 * The real server still starts through the Gradle runGame task.
 */
public final class Matrix3Startup {

    private Matrix3Startup() {
    }

    public static void main(String[] args) throws IOException {
        File serverDirectory = new File(System.getProperty("user.dir"));
        File runGame = new File(serverDirectory, "run_game.bat");
        if (!runGame.isFile()) {
            throw new IOException("Could not find run_game.bat in " + serverDirectory.getAbsolutePath());
        }

        String commandProcessor = System.getenv("ComSpec");
        if (commandProcessor == null || commandProcessor.trim().isEmpty()) {
            commandProcessor = "cmd.exe";
        }

        String detachedCommand = "start \"\" \"" + runGame.getAbsolutePath() + "\"";
        new ProcessBuilder(commandProcessor, "/c", detachedCommand)
                .directory(serverDirectory)
                .start();
    }
}
