package game.console;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

final class CacheEditorProcessLauncher {

    private static Process activeProcess;

    private CacheEditorProcessLauncher() {
    }

    public static synchronized boolean open() throws IOException {
        if (activeProcess != null && activeProcess.isAlive()) {
            return false;
        }

        File serverDirectory = findServerDirectory();
        File wrapper = new File(serverDirectory, isWindows() ? "gradlew.bat" : "gradlew").getAbsoluteFile();
        List<String> command = new ArrayList<String>();
        if (isWindows()) {
            command.add("cmd.exe");
            command.add("/c");
            command.add("call");
            command.add(wrapper.getAbsolutePath());
        } else {
            command.add(wrapper.getAbsolutePath());
        }
        command.add("-p");
        command.add(serverDirectory.getAbsolutePath());
        command.add("--no-daemon");
        command.add("runCacheEditor");

        System.out.println("[CacheEditor] Server project: " + serverDirectory.getAbsolutePath());
        System.out.println("[CacheEditor] Gradle wrapper: " + wrapper.getAbsolutePath());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(serverDirectory);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        activeProcess = builder.start();
        return true;
    }

    private static File findServerDirectory() throws IOException {
        File fromWorkingDirectory = findFrom(new File(System.getProperty("user.dir", ".")));
        if (fromWorkingDirectory != null) {
            return fromWorkingDirectory;
        }

        try {
            File codeLocation = new File(CacheEditorProcessLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File fromCodeLocation = findFrom(codeLocation);
            if (fromCodeLocation != null) {
                return fromCodeLocation;
            }
        } catch (URISyntaxException e) {
            // Fall through to the useful launch error below.
        }

        throw new IOException("Could not locate the Matrix3 Server project or Gradle wrapper from the Client runtime.");
    }

    private static File findFrom(File start) {
        File current = start == null ? null : start.getAbsoluteFile();
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (isServerDirectory(current)) {
                return current;
            }
            File child = new File(current, "Server");
            if (isServerDirectory(child)) {
                return child;
            }
            current = current.getParentFile();
        }
        return null;
    }

    private static boolean isServerDirectory(File directory) {
        if (directory == null || !directory.isDirectory() || !new File(directory, "build.gradle").isFile()) {
            return false;
        }
        File launcher = new File(directory,
                "src" + File.separator + "main" + File.separator + "java" + File.separator + "com" + File.separator
                        + "rs" + File.separator + "tools" + File.separator + "cacheeditor" + File.separator
                        + "CacheEditorLauncher.java");
        if (!launcher.isFile()) {
            return false;
        }
        return isWindows() ? new File(directory, "gradlew.bat").isFile() : new File(directory, "gradlew").isFile();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
