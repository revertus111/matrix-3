package game.atlas;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Deterministic SHA-256 identity for the compiled client classes indexed by Atlas.
 */
public final class AtlasFingerprint {

    private static final String ATLAS_PREFIX = "game/atlas/";

    private AtlasFingerprint() {
    }

    public static String compute(Path classRoot) throws IOException {
        if (classRoot == null || !Files.isDirectory(classRoot)) {
            throw new IOException("Client Atlas class root does not exist: " + classRoot);
        }

        final List<Path> classFiles = new ArrayList<Path>();
        try (Stream<Path> stream = Files.walk(classRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .filter(path -> !normalizedRelativePath(classRoot, path).startsWith(ATLAS_PREFIX))
                    .forEach(classFiles::add);
        }

        if (classFiles.isEmpty()) {
            throw new IOException("No compiled client .class files found under: " + classRoot);
        }

        Collections.sort(classFiles, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                return normalizedRelativePath(classRoot, left)
                        .compareTo(normalizedRelativePath(classRoot, right));
            }
        });

        MessageDigest digest = sha256();
        byte[] buffer = new byte[8192];
        for (Path classFile : classFiles) {
            String relative = normalizedRelativePath(classRoot, classFile);
            byte[] pathBytes = relative.getBytes(StandardCharsets.UTF_8);
            updateLong(digest, pathBytes.length);
            digest.update(pathBytes);
            updateLong(digest, Files.size(classFile));

            try (InputStream input = new BufferedInputStream(Files.newInputStream(classFile))) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
        }

        return toHex(digest.digest());
    }

    static String normalizedRelativePath(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable in this Java runtime", ex);
        }
    }

    private static void updateLong(MessageDigest digest, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            if (unsigned < 0x10) {
                builder.append('0');
            }
            builder.append(Integer.toHexString(unsigned));
        }
        return builder.toString();
    }
}
