package game.console.bosslabs;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import game.console.ConsoleTheme;

/**
 * Large BossLabs tile-authoring workspace over the same mutable DRAFT attack
 * patterns used by the Attacks editor.
 *
 * This is an authoring view only. Runtime ownership remains in BossCombatScript
 * and persisted definitions continue to store explicit attack-relative offsets.
 */
public final class BossLabsArenaPanel extends JPanel {

    private static final long serialVersionUID = 8609120545347421042L;

    private static final int MAX_PATTERN_TILES = 128;
    private static final int MAX_TILE_OFFSET = 16;
    private static final int PRESET_CROSS = 0;
    private static final int PRESET_HORIZONTAL_LINE = 1;
    private static final int PRESET_VERTICAL_LINE = 2;
    private static final int PRESET_FILLED_SQUARE = 3;
    private static final int PRESET_RING = 4;
    private static final int TRANSFORM_ROTATE_LEFT = 0;
    private static final int TRANSFORM_ROTATE_RIGHT = 1;
    private static final int TRANSFORM_MIRROR_X = 2;
    private static final int TRANSFORM_MIRROR_Y = 3;

    private final BossLabsPanel owner;
    private final DefaultComboBoxModel<BossLabsDraftDefinition.Phase> phaseModel =
            new DefaultComboBoxModel<BossLabsDraftDefinition.Phase>();
    private final JComboBox<BossLabsDraftDefinition.Phase> phaseBox =
            new JComboBox<BossLabsDraftDefinition.Phase>(phaseModel);
    private final DefaultComboBoxModel<BossLabsDraftDefinition.Attack> attackModel =
            new DefaultComboBoxModel<BossLabsDraftDefinition.Attack>();
    private final JComboBox<BossLabsDraftDefinition.Attack> attackBox =
            new JComboBox<BossLabsDraftDefinition.Attack>(attackModel);
    private final JTextField radiusField = new JTextField("2");
    private final JLabel summaryLabel = new JLabel("Select a BossLabs draft with a phase and attack.");
    private final JLabel geometryLabel = new JLabel("Geometry: no attack selected.");
    private final JLabel timingLabel = new JLabel("Timing: no attack selected.");
    private final JLabel statusLabel = new JLabel("Attack Pattern edits the selected attack's persisted relative tile pattern.");
    private final TileCanvas canvas = new TileCanvas();
    private final List<BossLabsDraftDefinition.TileOffset> patternClipboard =
            new ArrayList<BossLabsDraftDefinition.TileOffset>();
    private final List<BossLabsDraftDefinition.TileOffset> undoPattern =
            new ArrayList<BossLabsDraftDefinition.TileOffset>();

    private BossLabsDraftDefinition.Attack undoAttack;
    private boolean suppressSelectionEvents;

    public BossLabsArenaPanel(BossLabsPanel owner) {
        super(new BorderLayout(10, 10));
        if (owner == null)
            throw new IllegalArgumentException("BossLabs panel is required.");
        this.owner = owner;
        setBackground(ConsoleTheme.PANEL);
        setBorder(ConsoleTheme.panelPadding(12, 12, 12, 12));

        add(createHeader(), BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);

        phaseBox.addActionListener(e -> {
            if (!suppressSelectionEvents)
                refreshAttacks(null);
        });
        attackBox.addActionListener(e -> {
            if (!suppressSelectionEvents) {
                updateSummary();
                canvas.repaint();
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent event) {
                refreshFromDraft();
            }
        });
    }

    private JPanel createHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JLabel title = new JLabel("Attack Pattern Workspace");
        title.setFont(ConsoleTheme.SECTION_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel description = new JLabel("Author real BossLabs attack geometry. Origin 0,0 is the resolved attack target; fixed world-arena layout stays separate.");
        description.setFont(ConsoleTheme.SMALL_FONT);
        description.setForeground(ConsoleTheme.MUTED_TEXT);
        description.setAlignmentX(LEFT_ALIGNMENT);

        JPanel selectors = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        selectors.setOpaque(false);
        JLabel phaseLabel = smallLabel("Phase");
        JLabel attackLabel = smallLabel("Attack");
        styleCombo(phaseBox);
        styleCombo(attackBox);
        phaseBox.setPreferredSize(new Dimension(210, 32));
        attackBox.setPreferredSize(new Dimension(280, 32));
        JButton refresh = button("Refresh");
        refresh.setToolTipText("Reload phase/attack choices from the current BossLabs DRAFT.");
        refresh.addActionListener(e -> refreshFromDraft());
        selectors.add(phaseLabel);
        selectors.add(phaseBox);
        selectors.add(attackLabel);
        selectors.add(attackBox);
        selectors.add(refresh);

        JPanel tools = createTools();

        header.add(title);
        header.add(Box.createVerticalStrut(3));
        header.add(description);
        header.add(Box.createVerticalStrut(8));
        header.add(selectors);
        header.add(Box.createVerticalStrut(6));
        header.add(tools);
        return header;
    }

    private JPanel createTools() {
        JPanel tools = new JPanel();
        tools.setLayout(new BoxLayout(tools, BoxLayout.Y_AXIS));
        tools.setOpaque(false);

        JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        presets.setOpaque(false);
        JLabel radiusLabel = smallLabel("Radius");
        radiusField.setPreferredSize(new Dimension(48, 28));
        radiusField.setToolTipText("Preset radius from 0 to 16 tiles. Oversized presets are rejected before replacing the current pattern.");
        ConsoleTheme.styleTextField(radiusField);
        JButton cross = button("Cross");
        JButton horizontal = button("H Line");
        JButton vertical = button("V Line");
        JButton square = button("Square");
        JButton ring = button("Ring");
        JButton clear = button("Clear");
        cross.addActionListener(e -> applyPreset(PRESET_CROSS, "Cross"));
        horizontal.addActionListener(e -> applyPreset(PRESET_HORIZONTAL_LINE, "Horizontal line"));
        vertical.addActionListener(e -> applyPreset(PRESET_VERTICAL_LINE, "Vertical line"));
        square.addActionListener(e -> applyPreset(PRESET_FILLED_SQUARE, "Filled square"));
        ring.addActionListener(e -> applyPreset(PRESET_RING, "Ring"));
        clear.addActionListener(e -> clearPattern());
        cross.setToolTipText("Replace the selected pattern with a plus-shaped cross.");
        horizontal.setToolTipText("Replace the selected pattern with a horizontal line.");
        vertical.setToolTipText("Replace the selected pattern with a vertical line.");
        square.setToolTipText("Replace the selected pattern with a filled square.");
        ring.setToolTipText("Replace the selected pattern with a square perimeter ring.");
        clear.setToolTipText("Clear all painted tiles from the selected attack.");
        presets.add(radiusLabel);
        presets.add(radiusField);
        presets.add(cross);
        presets.add(horizontal);
        presets.add(vertical);
        presets.add(square);
        presets.add(ring);
        presets.add(clear);

        JPanel transforms = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        transforms.setOpaque(false);
        JButton undo = button("Undo Pattern");
        JButton rotateLeft = button("Rotate Left");
        JButton rotateRight = button("Rotate Right");
        JButton mirrorX = button("Mirror X");
        JButton mirrorY = button("Mirror Y");
        JButton nudgeLeft = button("Nudge Left");
        JButton nudgeRight = button("Nudge Right");
        JButton nudgeUp = button("Nudge Up");
        JButton nudgeDown = button("Nudge Down");
        undo.addActionListener(e -> undoPattern());
        rotateLeft.addActionListener(e -> transformPattern(TRANSFORM_ROTATE_LEFT, "Rotated pattern left"));
        rotateRight.addActionListener(e -> transformPattern(TRANSFORM_ROTATE_RIGHT, "Rotated pattern right"));
        mirrorX.addActionListener(e -> transformPattern(TRANSFORM_MIRROR_X, "Mirrored pattern across X"));
        mirrorY.addActionListener(e -> transformPattern(TRANSFORM_MIRROR_Y, "Mirrored pattern across Y"));
        nudgeLeft.addActionListener(e -> nudgePattern(-1, 0, "Nudged pattern left"));
        nudgeRight.addActionListener(e -> nudgePattern(1, 0, "Nudged pattern right"));
        nudgeUp.addActionListener(e -> nudgePattern(0, 1, "Nudged pattern up"));
        nudgeDown.addActionListener(e -> nudgePattern(0, -1, "Nudged pattern down"));
        undo.setToolTipText("Restore the pattern state from immediately before the last paint stroke, preset, paste, transform, nudge, or clear action.");
        rotateLeft.setToolTipText("Rotate every painted offset 90 degrees counter-clockwise around target 0,0.");
        rotateRight.setToolTipText("Rotate every painted offset 90 degrees clockwise around target 0,0.");
        mirrorX.setToolTipText("Flip the pattern left/right around target 0,0.");
        mirrorY.setToolTipText("Flip the pattern up/down around target 0,0.");
        nudgeLeft.setToolTipText("Shift the entire pattern one tile left if all offsets remain within +/-16.");
        nudgeRight.setToolTipText("Shift the entire pattern one tile right if all offsets remain within +/-16.");
        nudgeUp.setToolTipText("Shift the entire pattern one tile up if all offsets remain within +/-16.");
        nudgeDown.setToolTipText("Shift the entire pattern one tile down if all offsets remain within +/-16.");
        transforms.add(undo);
        transforms.add(rotateLeft);
        transforms.add(rotateRight);
        transforms.add(mirrorX);
        transforms.add(mirrorY);
        transforms.add(nudgeLeft);
        transforms.add(nudgeRight);
        transforms.add(nudgeUp);
        transforms.add(nudgeDown);

        JPanel workflow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        workflow.setOpaque(false);
        JButton copy = button("Copy Pattern");
        JButton paste = button("Paste Pattern");
        JButton center = button("Center View");
        copy.addActionListener(e -> copyPattern());
        paste.addActionListener(e -> pastePattern());
        center.addActionListener(e -> canvas.centerView());
        copy.setToolTipText("Copy the selected attack's geometry for reuse on another attack or phase.");
        paste.setToolTipText("Replace the selected attack's pattern with the copied geometry.");
        center.setToolTipText("Reset pan and zoom so target 0,0 is centered again.");
        workflow.add(copy);
        workflow.add(paste);
        workflow.add(center);

        tools.add(presets);
        tools.add(Box.createVerticalStrut(5));
        tools.add(transforms);
        tools.add(Box.createVerticalStrut(5));
        tools.add(workflow);
        return tools;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setBackground(ConsoleTheme.CARD);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(8, 10, 8, 10)));

        summaryLabel.setFont(ConsoleTheme.SMALL_FONT);
        summaryLabel.setForeground(ConsoleTheme.TEXT);
        summaryLabel.setAlignmentX(LEFT_ALIGNMENT);
        geometryLabel.setFont(ConsoleTheme.SMALL_FONT);
        geometryLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        geometryLabel.setAlignmentX(LEFT_ALIGNMENT);
        timingLabel.setFont(ConsoleTheme.SMALL_FONT);
        timingLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        timingLabel.setAlignmentX(LEFT_ALIGNMENT);
        statusLabel.setFont(ConsoleTheme.SMALL_FONT);
        statusLabel.setForeground(ConsoleTheme.ACCENT);
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);

        footer.add(summaryLabel);
        footer.add(Box.createVerticalStrut(3));
        footer.add(geometryLabel);
        footer.add(Box.createVerticalStrut(3));
        footer.add(timingLabel);
        footer.add(Box.createVerticalStrut(4));
        footer.add(statusLabel);
        return footer;
    }

    private JLabel smallLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        return label;
    }

    private JButton button(String text) {
        JButton button = new JButton(text);
        ConsoleTheme.styleButton(button);
        return button;
    }

    private void styleCombo(JComboBox<?> combo) {
        combo.setFont(ConsoleTheme.BODY_FONT);
        combo.setForeground(ConsoleTheme.TEXT);
        combo.setBackground(ConsoleTheme.INPUT);
        combo.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
    }

    private void refreshFromDraft() {
        String wantedPhase = selectedPhase() == null ? null : selectedPhase().getId();
        String wantedAttack = selectedAttack() == null ? null : selectedAttack().getId();
        BossLabsDraftDefinition draft = owner.getArenaDraft();

        suppressSelectionEvents = true;
        try {
            phaseModel.removeAllElements();
            if (draft != null) {
                for (BossLabsDraftDefinition.Phase phase : draft.getPhases())
                    phaseModel.addElement(phase);
            }
            selectPhaseById(wantedPhase);
        } finally {
            suppressSelectionEvents = false;
        }
        refreshAttacks(wantedAttack);
        updateSummary();
        canvas.repaint();
    }

    private void selectPhaseById(String id) {
        if (phaseModel.getSize() == 0)
            return;
        if (id != null) {
            for (int index = 0; index < phaseModel.getSize(); index++) {
                BossLabsDraftDefinition.Phase phase = phaseModel.getElementAt(index);
                if (id.equals(phase.getId())) {
                    phaseBox.setSelectedIndex(index);
                    return;
                }
            }
        }
        phaseBox.setSelectedIndex(0);
    }

    private void refreshAttacks(String wantedAttack) {
        suppressSelectionEvents = true;
        try {
            attackModel.removeAllElements();
            BossLabsDraftDefinition.Phase phase = selectedPhase();
            if (phase != null) {
                for (BossLabsDraftDefinition.Attack attack : phase.getAttacks())
                    attackModel.addElement(attack);
            }
            if (attackModel.getSize() > 0) {
                int selected = 0;
                if (wantedAttack != null) {
                    for (int index = 0; index < attackModel.getSize(); index++) {
                        if (wantedAttack.equals(attackModel.getElementAt(index).getId())) {
                            selected = index;
                            break;
                        }
                    }
                }
                attackBox.setSelectedIndex(selected);
            }
        } finally {
            suppressSelectionEvents = false;
        }
        updateSummary();
        canvas.repaint();
    }

    private BossLabsDraftDefinition.Phase selectedPhase() {
        Object selected = phaseBox.getSelectedItem();
        return selected instanceof BossLabsDraftDefinition.Phase ? (BossLabsDraftDefinition.Phase) selected : null;
    }

    private BossLabsDraftDefinition.Attack selectedAttack() {
        Object selected = attackBox.getSelectedItem();
        return selected instanceof BossLabsDraftDefinition.Attack ? (BossLabsDraftDefinition.Attack) selected : null;
    }

    private void updateSummary() {
        BossLabsDraftDefinition.Phase phase = selectedPhase();
        BossLabsDraftDefinition.Attack attack = selectedAttack();
        if (phase == null || attack == null) {
            summaryLabel.setText("Add/select a phase and attack before authoring attack geometry.");
            geometryLabel.setText("Geometry: no attack selected.");
            timingLabel.setText("Timing: no attack selected.");
            return;
        }

        summaryLabel.setText(creatorLabel(phase.getId()) + " / " + creatorLabel(attack.getId()) + "  •  "
                + BossLabsDraftDefinition.styleName(attack.getCombatStyle()) + "  •  impact: "
                + BossLabsDraftDefinition.tileEffectName(attack.getImpactTileEffectType()));
        updateGeometrySummary(attack);
        updateTimingSummary(attack);
    }

    private void updateGeometrySummary(BossLabsDraftDefinition.Attack attack) {
        if (attack.getTilePattern().isEmpty()) {
            geometryLabel.setText("Geometry: no painted tiles - this attack currently uses normal single-target behavior.");
            return;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        boolean includesOrigin = false;
        for (BossLabsDraftDefinition.TileOffset tile : attack.getTilePattern()) {
            minX = Math.min(minX, tile.getX());
            maxX = Math.max(maxX, tile.getX());
            minY = Math.min(minY, tile.getY());
            maxY = Math.max(maxY, tile.getY());
            includesOrigin |= tile.getX() == 0 && tile.getY() == 0;
        }
        geometryLabel.setText("Geometry: " + attack.getTilePattern().size() + " tile(s)  •  X " + minX + ".." + maxX
                + "  •  Y " + minY + ".." + maxY + "  •  target 0,0 "
                + (includesOrigin ? "affected" : "safe"));
    }

    private void updateTimingSummary(BossLabsDraftDefinition.Attack attack) {
        StringBuilder text = new StringBuilder("Timing: ");
        if (attack.getTelegraphTicks() > 0)
            text.append("warning delay ").append(attack.getTelegraphTicks()).append("t  →  ");
        text.append("impact");
        if (attack.getHazardDurationTicks() > 0) {
            text.append("  →  ground effect ").append(attack.getHazardDurationTicks()).append("t every ")
                    .append(attack.getHazardTickInterval()).append("t");
        } else {
            text.append("  •  no lingering ground effect");
        }
        timingLabel.setText(text.toString());
    }

    private void applyPreset(int preset, String label) {
        BossLabsDraftDefinition.Attack attack = requireAttack();
        if (attack == null)
            return;
        Integer radiusValue = parseInteger(radiusField.getText());
        if (radiusValue == null || radiusValue.intValue() < 0 || radiusValue.intValue() > MAX_TILE_OFFSET) {
            setStatus("Pattern radius must be a whole number between 0 and " + MAX_TILE_OFFSET + ".");
            return;
        }
        int radius = radiusValue.intValue();
        int expected = expectedPresetTileCount(preset, radius);
        if (expected < 0) {
            setStatus("Unsupported tile preset.");
            return;
        }
        if (expected > MAX_PATTERN_TILES) {
            setStatus(label + " radius " + radius + " would create " + expected
                    + " tiles; reduce the radius to stay within the " + MAX_PATTERN_TILES + "-tile limit.");
            return;
        }

        rememberUndo(attack);
        attack.getTilePattern().clear();
        if (preset == PRESET_CROSS) {
            for (int offset = -radius; offset <= radius; offset++) {
                addTile(attack, offset, 0);
                addTile(attack, 0, offset);
            }
        } else if (preset == PRESET_HORIZONTAL_LINE) {
            for (int x = -radius; x <= radius; x++)
                addTile(attack, x, 0);
        } else if (preset == PRESET_VERTICAL_LINE) {
            for (int y = -radius; y <= radius; y++)
                addTile(attack, 0, y);
        } else if (preset == PRESET_FILLED_SQUARE) {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++)
                    addTile(attack, x, y);
            }
        } else if (preset == PRESET_RING) {
            if (radius == 0) {
                addTile(attack, 0, 0);
            } else {
                for (int x = -radius; x <= radius; x++) {
                    addTile(attack, x, -radius);
                    addTile(attack, x, radius);
                }
                for (int y = -radius + 1; y <= radius - 1; y++) {
                    addTile(attack, -radius, y);
                    addTile(attack, radius, y);
                }
            }
        }
        draftChanged(label + " radius " + radius + " applied to " + creatorLabel(attack.getId()) + ".");
    }

    private int expectedPresetTileCount(int preset, int radius) {
        if (preset == PRESET_CROSS)
            return radius * 4 + 1;
        if (preset == PRESET_HORIZONTAL_LINE || preset == PRESET_VERTICAL_LINE)
            return radius * 2 + 1;
        if (preset == PRESET_FILLED_SQUARE) {
            int width = radius * 2 + 1;
            return width * width;
        }
        if (preset == PRESET_RING)
            return radius == 0 ? 1 : radius * 8;
        return -1;
    }

    private void clearPattern() {
        BossLabsDraftDefinition.Attack attack = requireAttack();
        if (attack == null)
            return;
        if (attack.getTilePattern().isEmpty()) {
            setStatus("The selected attack pattern is already empty.");
            return;
        }
        rememberUndo(attack);
        attack.getTilePattern().clear();
        draftChanged("Cleared the selected attack pattern.");
    }

    private void copyPattern() {
        BossLabsDraftDefinition.Attack attack = requireAttack();
        if (attack == null)
            return;
        patternClipboard.clear();
        for (BossLabsDraftDefinition.TileOffset tile : attack.getTilePattern())
            patternClipboard.add(new BossLabsDraftDefinition.TileOffset(tile.getX(), tile.getY()));
        setStatus("Copied " + patternClipboard.size() + " tile(s) from " + creatorLabel(attack.getId())
                + ". Select another attack and Paste Pattern to reuse the geometry.");
    }

    private void pastePattern() {
        BossLabsDraftDefinition.Attack attack = requireAttack();
        if (attack == null)
            return;
        if (patternClipboard.isEmpty()) {
            setStatus("Pattern clipboard is empty. Copy a painted attack first.");
            return;
        }
        if (patternClipboard.size() > MAX_PATTERN_TILES) {
            setStatus("Copied pattern exceeds the BossLabs tile limit.");
            return;
        }
        for (BossLabsDraftDefinition.TileOffset tile : patternClipboard) {
            if (Math.abs(tile.getX()) > MAX_TILE_OFFSET || Math.abs(tile.getY()) > MAX_TILE_OFFSET) {
                setStatus("Copied pattern contains an offset outside +/-" + MAX_TILE_OFFSET + ".");
                return;
            }
        }
        rememberUndo(attack);
        attack.getTilePattern().clear();
        for (BossLabsDraftDefinition.TileOffset tile : patternClipboard)
            attack.getTilePattern().add(new BossLabsDraftDefinition.TileOffset(tile.getX(), tile.getY()));
        draftChanged("Pasted " + patternClipboard.size() + " tile(s) into " + creatorLabel(attack.getId()) + ".");
    }

    private void rememberUndo(BossLabsDraftDefinition.Attack attack) {
        undoPattern.clear();
        if (attack != null) {
            for (BossLabsDraftDefinition.TileOffset tile : attack.getTilePattern())
                undoPattern.add(new BossLabsDraftDefinition.TileOffset(tile.getX(), tile.getY()));
        }
        undoAttack = attack;
    }

    private void undoPattern() {
        BossLabsDraftDefinition.Attack attack = requireAttack();
        if (attack == null)
            return;
        if (undoAttack != attack) {
            setStatus("No previous pattern state is available for this attack.");
            return;
        }
        attack.getTilePattern().clear();
        for (BossLabsDraftDefinition.TileOffset tile : undoPattern)
            attack.getTilePattern().add(new BossLabsDraftDefinition.TileOffset(tile.getX(), tile.getY()));
        undoPattern.clear();
        undoAttack = null;
        draftChanged("Restored the previous pattern state.");
    }

    private void transformPattern(int transform, String label) {
        BossLabsDraftDefinition.Attack attack = requireAttack();
        if (attack == null)
            return;
        if (attack.getTilePattern().isEmpty()) {
            setStatus("Paint or paste a pattern before transforming it.");
            return;
        }

        List<BossLabsDraftDefinition.TileOffset> transformed = new ArrayList<BossLabsDraftDefinition.TileOffset>();
        for (BossLabsDraftDefinition.TileOffset tile : attack.getTilePattern()) {
            int x = tile.getX();
            int y = tile.getY();
            int nextX;
            int nextY;
            if (transform == TRANSFORM_ROTATE_LEFT) {
                nextX = -y;
                nextY = x;
            } else if (transform == TRANSFORM_ROTATE_RIGHT) {
                nextX = y;
                nextY = -x;
            } else if (transform == TRANSFORM_MIRROR_X) {
                nextX = -x;
                nextY = y;
            } else if (transform == TRANSFORM_MIRROR_Y) {
                nextX = x;
                nextY = -y;
            } else {
                setStatus("Unsupported pattern transform.");
                return;
            }
            BossLabsDraftDefinition.TileOffset next = new BossLabsDraftDefinition.TileOffset(nextX, nextY);
            if (!transformed.contains(next))
                transformed.add(next);
        }

        rememberUndo(attack);
        replacePattern(attack, transformed);
        draftChanged(label + ".");
    }

    private void nudgePattern(int deltaX, int deltaY, String label) {
        BossLabsDraftDefinition.Attack attack = requireAttack();
        if (attack == null)
            return;
        if (attack.getTilePattern().isEmpty()) {
            setStatus("Paint or paste a pattern before nudging it.");
            return;
        }

        List<BossLabsDraftDefinition.TileOffset> shifted = new ArrayList<BossLabsDraftDefinition.TileOffset>();
        for (BossLabsDraftDefinition.TileOffset tile : attack.getTilePattern()) {
            int x = tile.getX() + deltaX;
            int y = tile.getY() + deltaY;
            if (Math.abs(x) > MAX_TILE_OFFSET || Math.abs(y) > MAX_TILE_OFFSET) {
                setStatus("Nudge rejected: at least one tile would move outside +/-" + MAX_TILE_OFFSET + ".");
                return;
            }
            shifted.add(new BossLabsDraftDefinition.TileOffset(x, y));
        }

        rememberUndo(attack);
        replacePattern(attack, shifted);
        draftChanged(label + ".");
    }

    private void replacePattern(BossLabsDraftDefinition.Attack attack,
            List<BossLabsDraftDefinition.TileOffset> replacement) {
        attack.getTilePattern().clear();
        attack.getTilePattern().addAll(replacement);
    }

    private BossLabsDraftDefinition.Attack requireAttack() {
        BossLabsDraftDefinition.Attack attack = selectedAttack();
        if (attack == null)
            setStatus("Select an attack first.");
        return attack;
    }

    private void addTile(BossLabsDraftDefinition.Attack attack, int x, int y) {
        BossLabsDraftDefinition.TileOffset tile = new BossLabsDraftDefinition.TileOffset(x, y);
        if (!attack.getTilePattern().contains(tile))
            attack.getTilePattern().add(tile);
    }

    private void draftChanged(String status) {
        owner.arenaDraftChanged();
        updateSummary();
        setStatus(status);
        canvas.repaint();
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value == null ? "" : value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String creatorLabel(String id) {
        String value = id == null ? "" : id.trim().replace('_', ' ').replace('-', ' ');
        if (value.length() == 0)
            return "Unnamed";
        StringBuilder result = new StringBuilder(value.length());
        boolean capitalize = true;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isWhitespace(character)) {
                if (result.length() > 0 && result.charAt(result.length() - 1) != ' ')
                    result.append(' ');
                capitalize = true;
            } else {
                result.append(capitalize ? Character.toUpperCase(character) : character);
                capitalize = false;
            }
        }
        return result.toString();
    }

    private final class TileCanvas extends JPanel {

        private static final long serialVersionUID = 2677977998358423860L;
        private static final int MIN_TILE_SIZE = 14;
        private static final int MAX_TILE_SIZE = 80;

        private int tileSize = 30;
        private int panX;
        private int panY;
        private Point lastPanPoint;
        private int hoverX = Integer.MIN_VALUE;
        private int hoverY = Integer.MIN_VALUE;
        private int paintButton = MouseEvent.NOBUTTON;
        private boolean strokeChanged;

        private TileCanvas() {
            setBackground(ConsoleTheme.INPUT);
            setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
            setPreferredSize(new Dimension(760, 430));
            setFocusable(true);

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    if (SwingUtilities.isMiddleMouseButton(event)) {
                        lastPanPoint = event.getPoint();
                        return;
                    }
                    if (!SwingUtilities.isLeftMouseButton(event) && !SwingUtilities.isRightMouseButton(event))
                        return;
                    updateHover(event.getPoint());
                    BossLabsDraftDefinition.Attack attack = requireAttack();
                    if (attack == null)
                        return;
                    rememberUndo(attack);
                    paintButton = event.getButton();
                    strokeChanged = false;
                    paintHoveredTile(paintButton == MouseEvent.BUTTON3);
                    requestFocusInWindow();
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    if (SwingUtilities.isMiddleMouseButton(event)) {
                        lastPanPoint = null;
                        return;
                    }
                    if (paintButton != MouseEvent.NOBUTTON) {
                        finishPaintStroke();
                        paintButton = MouseEvent.NOBUTTON;
                    }
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hoverX = Integer.MIN_VALUE;
                    hoverY = Integer.MIN_VALUE;
                    repaint();
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent event) {
                    zoomAt(event.getPoint(), event.getWheelRotation());
                }
            };
            addMouseListener(mouse);
            addMouseWheelListener(mouse);
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent event) {
                    updateHover(event.getPoint());
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (lastPanPoint != null) {
                        panX += event.getX() - lastPanPoint.x;
                        panY += event.getY() - lastPanPoint.y;
                        lastPanPoint = event.getPoint();
                        updateHover(event.getPoint());
                        repaint();
                        return;
                    }
                    if (paintButton == MouseEvent.NOBUTTON)
                        return;
                    updateHover(event.getPoint());
                    paintHoveredTile(paintButton == MouseEvent.BUTTON3);
                }
            });
        }

        private void paintHoveredTile(boolean erase) {
            BossLabsDraftDefinition.Attack attack = requireAttack();
            if (attack == null)
                return;
            if (Math.abs(hoverX) > MAX_TILE_OFFSET || Math.abs(hoverY) > MAX_TILE_OFFSET) {
                setStatus("Tile offsets must stay within +/-" + MAX_TILE_OFFSET + " of target 0,0.");
                return;
            }

            BossLabsDraftDefinition.TileOffset tile = new BossLabsDraftDefinition.TileOffset(hoverX, hoverY);
            boolean contains = attack.getTilePattern().contains(tile);
            if (erase) {
                if (!contains)
                    return;
                attack.getTilePattern().remove(tile);
            } else {
                if (contains)
                    return;
                if (attack.getTilePattern().size() >= MAX_PATTERN_TILES) {
                    setStatus("Pattern limit reached: " + MAX_PATTERN_TILES + " tiles.");
                    return;
                }
                attack.getTilePattern().add(tile);
            }
            strokeChanged = true;
            updateSummary();
            setStatus((erase ? "Erasing" : "Painting") + " tile " + hoverX + ", " + hoverY
                    + "  •  release mouse to commit this stroke.");
            repaint();
        }

        private void finishPaintStroke() {
            if (!strokeChanged)
                return;
            strokeChanged = false;
            owner.arenaDraftChanged();
            updateSummary();
            setStatus("Pattern paint stroke committed. Undo Pattern restores the previous stroke state.");
            repaint();
        }

        private void centerView() {
            tileSize = 30;
            panX = 0;
            panY = 0;
            setStatus("View centered on target 0,0.");
            repaint();
        }

        private void zoomAt(Point point, int wheelRotation) {
            int oldSize = tileSize;
            int requested = oldSize - wheelRotation * 4;
            int newSize = Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, requested));
            if (newSize == oldSize)
                return;

            double originX = getWidth() / 2.0 + panX;
            double originY = getHeight() / 2.0 + panY;
            double relativeX = (point.x - originX) / oldSize;
            double relativeY = (point.y - originY) / oldSize;
            tileSize = newSize;
            panX = (int) Math.round(point.x - relativeX * tileSize - getWidth() / 2.0);
            panY = (int) Math.round(point.y - relativeY * tileSize - getHeight() / 2.0);
            updateHover(point);
            repaint();
        }

        private void updateHover(Point point) {
            int originX = getWidth() / 2 + panX;
            int originY = getHeight() / 2 + panY;
            hoverX = floorDiv(point.x - originX, tileSize);
            hoverY = floorDiv(point.y - originY, tileSize);
            BossLabsDraftDefinition.Attack attack = selectedAttack();
            if (attack != null) {
                setStatus("Hover " + hoverX + ", " + hoverY + "  •  " + attack.getTilePattern().size()
                        + " painted  •  left paint  •  right erase  •  middle pan  •  wheel zoom");
            }
            repaint();
        }

        private int floorDiv(int value, int divisor) {
            int result = value / divisor;
            if ((value ^ divisor) < 0 && result * divisor != value)
                result--;
            return result;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int originX = getWidth() / 2 + panX;
                int originY = getHeight() / 2 + panY;
                int startX = positiveModulo(originX, tileSize);
                int startY = positiveModulo(originY, tileSize);

                g.setColor(ConsoleTheme.BORDER);
                for (int x = startX; x < getWidth(); x += tileSize)
                    g.drawLine(x, 0, x, getHeight());
                for (int y = startY; y < getHeight(); y += tileSize)
                    g.drawLine(0, y, getWidth(), y);

                BossLabsDraftDefinition.Attack attack = selectedAttack();
                if (attack != null) {
                    for (BossLabsDraftDefinition.TileOffset tile : attack.getTilePattern()) {
                        int drawX = originX + tile.getX() * tileSize;
                        int drawY = originY + tile.getY() * tileSize;
                        g.setColor(new Color(ConsoleTheme.ACCENT.getRed(), ConsoleTheme.ACCENT.getGreen(),
                                ConsoleTheme.ACCENT.getBlue(), 85));
                        g.fillRect(drawX + 1, drawY + 1, Math.max(1, tileSize - 1), Math.max(1, tileSize - 1));
                        g.setColor(ConsoleTheme.ACCENT);
                        g.drawRect(drawX, drawY, tileSize, tileSize);
                    }
                }

                g.setStroke(new BasicStroke(2.0f));
                g.setColor(ConsoleTheme.ACCENT_DARK);
                g.drawLine(originX, 0, originX, getHeight());
                g.drawLine(0, originY, getWidth(), originY);

                if (hoverX != Integer.MIN_VALUE) {
                    int drawX = originX + hoverX * tileSize;
                    int drawY = originY + hoverY * tileSize;
                    g.setColor(ConsoleTheme.TEXT);
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawRect(drawX, drawY, tileSize, tileSize);
                }

                g.setFont(ConsoleTheme.SMALL_FONT);
                g.setColor(ConsoleTheme.MUTED_TEXT);
                g.drawString("target 0,0", originX + 5, originY - 6);
            } finally {
                g.dispose();
            }
        }

        private int positiveModulo(int value, int modulus) {
            int result = value % modulus;
            return result < 0 ? result + modulus : result;
        }
    }
}
