package game;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.imageio.ImageIO;

import game.AssetStudioCapture.CaptureBatch;
import game.AssetStudioCapture.CaptureEntry;

/**
 * Writes one paired Asset Studio evidence capture: deterministic scene metadata
 * plus a screenshot of Matrix3's live client canvas.
 *
 * This helper never mutates scene objects, cache definitions, collision, or
 * renderer ownership. Numbered annotations are applied to the saved image after
 * the live canvas has already been captured.
 */
public final class AssetStudioEvidenceCapture {

    private static final Path CAPTURE_DIR =
            Paths.get("data/construction/asset_studio/captures");
    private static final int PANEL_WIDTH = 390;
    private static final int PANEL_PADDING = 16;
    private static final int ROW_HEIGHT = 18;

    private static final Comparator<CaptureEntry> EVIDENCE_ORDER =
            new Comparator<CaptureEntry>() {
                @Override
                public int compare(CaptureEntry a, CaptureEntry b) {
                    int value = compareInt(a.getPlane(), b.getPlane());
                    if (value != 0) return value;
                    value = compareInt(a.getWorldY(), b.getWorldY());
                    if (value != 0) return value;
                    value = compareInt(a.getWorldX(), b.getWorldX());
                    if (value != 0) return value;
                    value = safe(a.getSlot()).compareTo(safe(b.getSlot()));
                    if (value != 0) return value;
                    value = compareInt(a.getId(), b.getId());
                    if (value != 0) return value;
                    value = compareInt(a.getType(), b.getType());
                    if (value != 0) return value;
                    return compareInt(a.getRotation(), b.getRotation());
                }
            };

    private AssetStudioEvidenceCapture() {
    }

    public static Result capture(CaptureBatch batch) {
        if (batch == null || !batch.isSuccess()) {
            return Result.failure(batch == null
                    ? "Capture batch is unavailable."
                    : batch.getError());
        }
        if (batch.getEntries().isEmpty()) {
            return Result.failure("The evidence capture contains no live scene objects.");
        }
        if (Class584.aCanvas7745 == null || !Class584.aCanvas7745.isShowing()
                || Class584.aCanvas7745.getWidth() <= 0
                || Class584.aCanvas7745.getHeight() <= 0) {
            return Result.failure("Matrix3's live game canvas is not visible.");
        }

        try {
            Files.createDirectories(CAPTURE_DIR);
            String captureId = allocateCaptureId();
            Path png = CAPTURE_DIR.resolve(captureId + ".png");
            Path tsv = CAPTURE_DIR.resolve(captureId + ".tsv");
            Path pngTemp = CAPTURE_DIR.resolve(captureId + ".png.tmp");
            Path tsvTemp = CAPTURE_DIR.resolve(captureId + ".tsv.tmp");

            List<CaptureEntry> ordered =
                    new ArrayList<CaptureEntry>(batch.getEntries());
            Collections.sort(ordered, EVIDENCE_ORDER);

            BufferedImage live = captureLiveCanvas();
            BufferedImage annotated = annotate(live, captureId, batch, ordered);
            List<String> lines = buildTsv(captureId, batch, ordered);

            try {
                ImageIO.write(annotated, "png", pngTemp.toFile());
                Files.write(tsvTemp, lines, StandardCharsets.UTF_8);
                Files.move(pngTemp, png, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tsvTemp, tsv, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception writeFailure) {
                // Never leave a half-pair behind. A failed evidence transaction
                // is cleaned up and the next click receives a fresh sequence id.
                Files.deleteIfExists(png);
                Files.deleteIfExists(tsv);
                throw writeFailure;
            } finally {
                Files.deleteIfExists(pngTemp);
                Files.deleteIfExists(tsvTemp);
            }

            return Result.success(captureId, png, tsv, ordered.size());
        } catch (Exception ex) {
            return Result.failure("Evidence capture failed: " + ex.getMessage());
        }
    }

    private static BufferedImage captureLiveCanvas() throws Exception {
        GraphicsConfiguration config = Class584.aCanvas7745.getGraphicsConfiguration();
        Robot robot = config == null
                ? new Robot()
                : new Robot(config.getDevice());

        // Asset Studio is hidden by its caller before this worker runs. Give the
        // desktop compositor a brief chance to expose the game canvas.
        robot.delay(120);

        Point location = Class584.aCanvas7745.getLocationOnScreen();
        Rectangle bounds = new Rectangle(location.x, location.y,
                Class584.aCanvas7745.getWidth(), Class584.aCanvas7745.getHeight());
        return robot.createScreenCapture(bounds);
    }

    private static BufferedImage annotate(BufferedImage source, String captureId,
            CaptureBatch batch, List<CaptureEntry> ordered) {
        int outputWidth = source.getWidth() + PANEL_WIDTH;
        int outputHeight = Math.max(source.getHeight(), 620);
        BufferedImage output = new BufferedImage(
                outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = output.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, outputWidth, outputHeight);
            g.drawImage(source, 0, 0, null);

            int panelX = source.getWidth();
            g.setColor(new Color(24, 26, 30));
            g.fillRect(panelX, 0, PANEL_WIDTH, outputHeight);

            int x = panelX + PANEL_PADDING;
            int y = 28;

            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
            g.drawString(captureId, x, y);

            y += 22;
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            g.setColor(new Color(205, 210, 218));
            g.drawString("center=" + batch.getCenterX() + "," + batch.getCenterY()
                    + "," + batch.getPlane() + "  radius=" + batch.getRadius(), x, y);
            y += 18;
            g.drawString("order=plane,y,x,slot,id,type,rotation", x, y);
            y += 26;

            int dimension = batch.getRadius() * 2 + 1;
            int gridSize = Math.min(PANEL_WIDTH - PANEL_PADDING * 2, 288);
            int cell = Math.max(18, gridSize / Math.max(1, dimension));
            gridSize = cell * dimension;
            int gridX = x;
            int gridY = y;

            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g.setColor(new Color(165, 170, 180));
            g.drawString("+Y", gridX + 2, gridY - 5);

            for (int row = 0; row < dimension; row++) {
                for (int col = 0; col < dimension; col++) {
                    int tileX = batch.getCenterX() - batch.getRadius() + col;
                    int tileY = batch.getCenterY() + batch.getRadius() - row;
                    int drawX = gridX + col * cell;
                    int drawY = gridY + row * cell;

                    g.setColor(new Color(43, 46, 53));
                    g.fillRect(drawX, drawY, cell, cell);
                    g.setColor(new Color(78, 83, 94));
                    g.drawRect(drawX, drawY, cell, cell);

                    String indexes = indexesAt(ordered, tileX, tileY, batch.getPlane());
                    if (indexes.length() > 0) {
                        g.setColor(new Color(255, 221, 87));
                        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD,
                                indexes.length() > 5 ? 8 : 10));
                        FontMetrics metrics = g.getFontMetrics();
                        int textX = drawX + Math.max(2,
                                (cell - metrics.stringWidth(indexes)) / 2);
                        int textY = drawY + (cell + metrics.getAscent()
                                - metrics.getDescent()) / 2;
                        g.drawString(indexes, textX, textY);
                    }
                }
            }

            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g.setColor(new Color(165, 170, 180));
            g.drawString("+X ->", gridX + gridSize - 38, gridY + gridSize + 13);

            y = gridY + gridSize + 38;
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
            g.setColor(Color.WHITE);
            g.drawString("Numbered object rows", x, y);
            y += 17;

            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            int availableRows = Math.max(0, (outputHeight - y - 22) / ROW_HEIGHT);
            int rows = Math.min(ordered.size(), availableRows);
            for (int i = 0; i < rows; i++) {
                CaptureEntry entry = ordered.get(i);
                g.setColor(entry.isRailCandidate()
                        ? new Color(255, 221, 87)
                        : new Color(215, 218, 224));
                String line = "#" + (i + 1)
                        + " ID=" + entry.getId()
                        + " T=" + entry.getType()
                        + " R=" + entry.getRotation()
                        + " (" + (entry.getWorldX() - batch.getCenterX())
                        + "," + (entry.getWorldY() - batch.getCenterY()) + ")";
                g.drawString(line, x, y);
                y += ROW_HEIGHT;
            }
            if (rows < ordered.size()) {
                g.setColor(new Color(165, 170, 180));
                g.drawString("+" + (ordered.size() - rows)
                        + " more row(s) in TSV", x, y);
            }
        } finally {
            g.dispose();
        }
        return output;
    }

    private static String indexesAt(List<CaptureEntry> ordered,
            int worldX, int worldY, int plane) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ordered.size(); i++) {
            CaptureEntry entry = ordered.get(i);
            if (entry.getWorldX() == worldX && entry.getWorldY() == worldY
                    && entry.getPlane() == plane) {
                if (builder.length() > 0) {
                    builder.append('/');
                }
                builder.append(i + 1);
            }
        }
        return builder.toString();
    }

    private static List<String> buildTsv(String captureId, CaptureBatch batch,
            List<CaptureEntry> ordered) {
        List<String> lines = new ArrayList<String>();
        lines.add("# captureId=" + captureId);
        lines.add("# center=" + batch.getCenterX() + "," + batch.getCenterY()
                + "," + batch.getPlane());
        lines.add("# radius=" + batch.getRadius());
        lines.add("# ordering=plane,y,x,slot,id,type,rotation");
        lines.add("index\tid\tname\ttype\trotation\tslot\tx\ty\tplane"
                + "\tdx\tdy\tsizeX\tsizeY\trailCandidate\tsuggestedTag");
        for (int i = 0; i < ordered.size(); i++) {
            CaptureEntry entry = ordered.get(i);
            lines.add((i + 1) + "\t"
                    + entry.getId() + "\t"
                    + safe(entry.getName()) + "\t"
                    + entry.getType() + "\t"
                    + entry.getRotation() + "\t"
                    + safe(entry.getSlot()) + "\t"
                    + entry.getWorldX() + "\t"
                    + entry.getWorldY() + "\t"
                    + entry.getPlane() + "\t"
                    + (entry.getWorldX() - batch.getCenterX()) + "\t"
                    + (entry.getWorldY() - batch.getCenterY()) + "\t"
                    + entry.getSizeX() + "\t"
                    + entry.getSizeY() + "\t"
                    + entry.isRailCandidate() + "\t"
                    + safe(entry.getSuggestedTag()));
        }
        return lines;
    }

    private static String allocateCaptureId() throws Exception {
        String day = new SimpleDateFormat("yyyyMMdd").format(new Date());
        for (int sequence = 1; sequence <= 9999; sequence++) {
            String id = "capture_" + day + "_" + String.format("%03d", sequence);
            if (!Files.exists(CAPTURE_DIR.resolve(id + ".png"))
                    && !Files.exists(CAPTURE_DIR.resolve(id + ".tsv"))
                    && !Files.exists(CAPTURE_DIR.resolve(id + ".png.tmp"))
                    && !Files.exists(CAPTURE_DIR.resolve(id + ".tsv.tmp"))) {
                return id;
            }
        }
        throw new IllegalStateException("No free evidence capture sequence remains for " + day + ".");
    }

    private static int compareInt(int a, int b) {
        return a < b ? -1 : a == b ? 0 : 1;
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\t', ' ')
                .replace('\r', ' ').replace('\n', ' ');
    }

    public static final class Result {
        private final boolean success;
        private final String error;
        private final String captureId;
        private final Path png;
        private final Path tsv;
        private final int objectCount;

        private Result(boolean success, String error, String captureId,
                Path png, Path tsv, int objectCount) {
            this.success = success;
            this.error = error;
            this.captureId = captureId;
            this.png = png;
            this.tsv = tsv;
            this.objectCount = objectCount;
        }

        private static Result failure(String error) {
            return new Result(false, error, null, null, null, 0);
        }

        private static Result success(String captureId, Path png, Path tsv,
                int objectCount) {
            return new Result(true, null, captureId, png, tsv, objectCount);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getError() {
            return error;
        }

        public String getCaptureId() {
            return captureId;
        }

        public Path getPng() {
            return png;
        }

        public Path getTsv() {
            return tsv;
        }

        public int getObjectCount() {
            return objectCount;
        }
    }
}
