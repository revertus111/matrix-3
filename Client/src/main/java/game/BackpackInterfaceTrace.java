package game;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Focused client trace for interface 671 ownership and runtime layout. */
final class BackpackInterfaceTrace {

    private static final String REPO_RELATIVE_LOG = "Server/data/logs/backpack-debug.txt";
    private static final String SERVER_RELATIVE_LOG = "data/logs/backpack-debug.txt";
    private static final int INTERFACE_ID = 671;
    private static final int MAX_COMPONENT_PROBE = 64;
    private static final int MAX_GEOMETRY_SNAPSHOTS = 4;

    private static boolean stringsDumped;
    private static boolean writeFailureReported;
    private static int geometrySnapshots;
    private static String lastGeometrySignature;

    private BackpackInterfaceTrace() {
    }

    static void dumpOnce() {
        if (!stringsDumped)
            dumpStrings();
        captureGeometryIfChanged();
    }

    private static void dumpStrings() {
        boolean foundAny = false;
        int lastProbed = -1;
        for (int component = 0; component < MAX_COMPONENT_PROBE; component++) {
            InterfaceDefinitions definition = getComponent(component);
            if (definition == null) {
                if (isPastBoundary(component))
                    break;
                continue;
            }
            lastProbed = component;
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
            stringsDumped = true;
        }
    }

    private static void captureGeometryIfChanged() {
        if (geometrySnapshots >= MAX_GEOMETRY_SNAPSHOTS)
            return;
        String signature = geometrySignature();
        if (signature == null || signature.equals(lastGeometrySignature))
            return;
        lastGeometrySignature = signature;
        geometrySnapshots++;
        log("IFACE671 geometry-snapshot=" + geometrySnapshots + " signature=" + signature);

        for (int component = 0; component < MAX_COMPONENT_PROBE; component++) {
            InterfaceDefinitions definition = getComponent(component);
            if (definition == null) {
                if (isPastBoundary(component))
                    break;
                continue;
            }
            logGeometry(component, definition);
        }
    }

    private static String geometrySignature() {
        int[] components = { 3, 6, 16, 17, 27 };
        StringBuilder builder = new StringBuilder();
        for (int component : components) {
            InterfaceDefinitions definition = getComponent(component);
            if (definition == null)
                return null;
            if (builder.length() > 0)
                builder.append('|');
            builder.append(component).append(':')
                    .append(layoutX(definition)).append(',')
                    .append(layoutY(definition)).append(',')
                    .append(layoutWidth(definition)).append(',')
                    .append(layoutHeight(definition));
        }
        return builder.toString();
    }

    private static void logGeometry(int component, InterfaceDefinitions definition) {
        int type = definition.anInt752 * -1285279191;
        StringBuilder line = new StringBuilder();
        line.append("IFACE671 GEOM component=").append(component)
                .append(" type=").append(type)
                .append(" parent=").append(definition.anInt768 * -1604592419)
                .append(" base=").append(definition.anInt819 * 329065219)
                .append(',').append(definition.anInt793 * -885681489)
                .append(',').append(definition.anInt760 * 1473094557)
                .append(',').append(definition.anInt761 * 1647331279)
                .append(" align=").append(definition.aByte756)
                .append(',').append(definition.aByte757)
                .append(',').append(definition.aByte811)
                .append(',').append(definition.aByte755)
                .append(" layout=").append(layoutX(definition))
                .append(',').append(layoutY(definition))
                .append(',').append(layoutWidth(definition))
                .append(',').append(layoutHeight(definition))
                .append(" scroll=").append(definition.anInt774 * -1883230751)
                .append(',').append(definition.anInt775 * -2139739529)
                .append(" content=").append(definition.anInt854 * -1792394419)
                .append(" item=").append(definition.nvmtheindexisotherone * 411192987)
                .append(" children=")
                .append(definition.aClass73Array916 == null ? 0 : definition.aClass73Array916.length)
                .append(',')
                .append(definition.aClass73Array917 == null ? 0 : definition.aClass73Array917.length);
        if (type == 5)
            line.append(" sprite=").append(definition.anInt783 * 1554484939);
        if (type == 4) {
            line.append(" font=").append(definition.anInt906 * 1036765709);
            append(line, "text", definition.aString829);
        }
        log(line.toString());
    }

    private static int layoutX(InterfaceDefinitions definition) {
        return definition.anInt762 * 278882041;
    }

    private static int layoutY(InterfaceDefinitions definition) {
        return definition.anInt842 * -1681379547;
    }

    private static int layoutWidth(InterfaceDefinitions definition) {
        return definition.anInt764 * 669238293;
    }

    private static int layoutHeight(InterfaceDefinitions definition) {
        return definition.anInt765 * 1360982075;
    }

    private static InterfaceDefinitions getComponent(int component) {
        try {
            return Class512.method6083((INTERFACE_ID << 16) | component, (short) 3691);
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        } catch (RuntimeException e) {
            log("IFACE671 probe-failed component=" + component + " error="
                    + e.getClass().getSimpleName() + ":" + String.valueOf(e.getMessage()));
            return null;
        }
    }

    private static boolean isPastBoundary(int component) {
        try {
            Class512.method6083((INTERFACE_ID << 16) | component, (short) 3691);
            return false;
        } catch (ArrayIndexOutOfBoundsException e) {
            log("IFACE671 boundary component=" + component);
            return true;
        } catch (RuntimeException e) {
            log("IFACE671 probe-failed component=" + component + " error="
                    + e.getClass().getSimpleName() + ":" + String.valueOf(e.getMessage()));
            return true;
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
