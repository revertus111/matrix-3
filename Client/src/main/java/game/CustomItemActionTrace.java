package game;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Narrow runtime trace for the data-driven custom item action pipeline.
 *
 * Both Client and Server append to the same repository-local log so one test
 * captures presentation and authoritative routing in chronological order.
 */
final class CustomItemActionTrace {

    private static final String REPO_RELATIVE_LOG = "Server/data/logs/custom-item-actions-debug.txt";
    private static final String SERVER_RELATIVE_LOG = "data/logs/custom-item-actions-debug.txt";

    private static boolean writeFailureReported;

    private CustomItemActionTrace() {
    }

    static synchronized void log(String message) {
        BufferedWriter writer = null;
        try {
            File file = resolveLogFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists())
                parent.mkdirs();
            writer = new BufferedWriter(new FileWriter(file, true));
            writer.write("[" + timestamp() + "] [CLIENT] [" + Thread.currentThread().getName() + "] " + message);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            if (!writeFailureReported) {
                writeFailureReported = true;
                System.err.println("Unable to write custom item action debug log: " + e.getMessage());
            }
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    if (!writeFailureReported) {
                        writeFailureReported = true;
                        System.err.println("Unable to close custom item action debug log: " + e.getMessage());
                    }
                }
            }
        }
    }

    private static File resolveLogFile() {
        File directory = new File(System.getProperty("user.dir", "."));
        for (int depth = 0; directory != null && depth < 6; depth++, directory = directory.getParentFile()) {
            File repoServer = new File(directory, "Server");
            if (repoServer.isDirectory())
                return new File(directory, REPO_RELATIVE_LOG);
            File serverData = new File(directory, "data");
            if (serverData.isDirectory())
                return new File(directory, SERVER_RELATIVE_LOG);
        }
        return new File(SERVER_RELATIVE_LOG);
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
    }
}
