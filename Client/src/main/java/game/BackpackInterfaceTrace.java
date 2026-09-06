package game;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/** One-shot client dump for interface 671 text/component ownership. */
final class BackpackInterfaceTrace {

    private static final String REPO_RELATIVE_LOG = "Server/data/logs/backpack-debug.txt";
    private static final String SERVER_RELATIVE_LOG = "data/logs/backpack-debug.txt";
    private static final int INTERFACE_ID = 671;
    private static final int MAX_COMPONENT_PROBE = 64;

    private static boolean dumped;
    private static boolean writeFailureReported;

    private BackpackInterfaceTrace() {
    }

    static void dumpOnce() {
        if (dumped)
            return;
        boolean foundAny = false;
        int lastProbed = -1;
        for (int component = 0; component < MAX_COMPONENT_PROBE; component++) {
            InterfaceDefinitions definition;
            try {
                definition = Class512.method6083((INTERFACE_ID << 16) | component, (short) 3691);
            } catch (ArrayIndexOutOfBoundsException e) {
                log("IFACE671 boundary component=" + component);
                break;
            } catch (RuntimeException e) {
                log("IFACE671 probe-failed component=" + component + " error="
                        + e.getClass().getSimpleName() + ":" + String.valueOf(e.getMessage()));
                break;
            }
            lastProbed = component;
            if (definition == null)
                continue;
            foundAny = true;
            StringBuilder strings = new StringBuilder();
            append(strings, "s747", definition.aString747);
            append(strings, "s748", definition.aString748);
            append(strings, "s829", definition.aString829);
            append(strings, "s847", definition.aString847);
            append(strings, "s849", definition.aString849);
            append(strings, "s856", definition.aString856);
            if (strings.length() > 0)
                log("IFACE671 component=" + component + " " + strings.toString());
        }
        if (foundAny) {
            log("IFACE671 dump-complete lastProbed=" + lastProbed);
            dumped = true;
        }
    }

    private static void append(StringBuilder builder, String field, String value) {
        if (value == null || value.length() == 0)
            return;
        if (builder.length() > 0)
            builder.append(' ');
        builder.append(field).append("=\"")
                .append(value.replace("\r", "\\r").replace("\n", "\\n"))
                .append('\"');
    }

    private static synchronized void log(String message) {
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
