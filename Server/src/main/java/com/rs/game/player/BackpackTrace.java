package com.rs.game.player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Narrow runtime trace for Backpack interface ownership diagnostics. */
final class BackpackTrace {

    private static final String REPO_RELATIVE_LOG = "Server/data/logs/backpack-debug.txt";
    private static final String SERVER_RELATIVE_LOG = "data/logs/backpack-debug.txt";

    private static boolean writeFailureReported;

    private BackpackTrace() {
    }

    static synchronized void log(String message) {
        BufferedWriter writer = null;
        try {
            File file = resolveLogFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists())
                parent.mkdirs();
            writer = new BufferedWriter(new FileWriter(file, true));
            writer.write("[" + timestamp() + "] [SERVER] [" + Thread.currentThread().getName() + "] " + message);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            if (!writeFailureReported) {
                writeFailureReported = true;
                System.err.println("Unable to write backpack debug log: " + e.getMessage());
            }
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    if (!writeFailureReported) {
                        writeFailureReported = true;
                        System.err.println("Unable to close backpack debug log: " + e.getMessage());
                    }
                }
            }
        }
    }

    private static File resolveLogFile() {
        File start = new File(System.getProperty("user.dir", "."));
        File directory = start;
        for (int depth = 0; directory != null && depth < 6; depth++, directory = directory.getParentFile()) {
            File repoServer = new File(directory, "Server");
            if (repoServer.isDirectory())
                return new File(directory, REPO_RELATIVE_LOG);
        }
        return new File(start, SERVER_RELATIVE_LOG);
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
    }
}
