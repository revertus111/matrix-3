package game.console.bosslabs;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import game.console.ConsoleTheme;

/**
 * BossLabs Phase 3 editor shell.
 *
 * This panel owns local DRAFT presentation only. The client/server BossLabs
 * development bridge is intentionally not faked here; server-backed search,
 * inspection, live apply, save/apply, rollback, and encounter testing remain
 * disabled until that bridge is implemented through an authoritative path.
 */
public final class BossLabsPanel extends JPanel {

    private static final long serialVersionUID = 1721751006227827808L;

    private final JTextField npcSearchField = new JTextField();
    private final JLabel searchStatus = new JLabel("Search is local-only until the BossLabs server bridge is connected.");
    private final JLabel draftState = createStateLabel("DRAFT", ConsoleTheme.ACCENT);
    private final JLabel liveState = createStateLabel("LIVE: server", ConsoleTheme.CARD_HOVER);
    private final JLabel savedState = createStateLabel("SAVED: server", ConsoleTheme.CARD_HOVER);

    private final JTextField definitionIdField = new JTextField();
    private final JTextField displayNameField = new JTextField();
    private final JTextField npcIdField = new JTextField();

    private final JButton applyLiveButton = new JButton("Apply Live");
    private final JButton saveApplyButton = new JButton("Save & Apply");
    private final JButton undoButton = new JButton("Undo Last Apply");
    private final JButton applySavedButton = new JButton("Apply Saved");

    private boolean suppressDraftEvents;
    private boolean draftDirty;

    public BossLabsPanel() {
        super(new BorderLayout());
        setBackground(ConsoleTheme.WINDOW);
        setBorder(ConsoleTheme.panelPadding(16, 16, 16, 16));

        add(createTopArea(), BorderLayout.NORTH);
        add(createTabs(), BorderLayout.CENTER);
        add(createPublishBar(), BorderLayout.SOUTH);

        installDraftListeners();
        updateDraftState();
    }

    private JComponent createTopArea() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(ConsoleTheme.WINDOW);
        top.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        JPanel titleRow = new JPanel(new BorderLayout(12, 0));
        titleRow.setBackground(ConsoleTheme.WINDOW);
        titleRow.setAlignmentX(LEFT_ALIGNMENT);

        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setBackground(ConsoleTheme.WINDOW);

        JLabel title = new JLabel("BOSSLABS");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Matrix3 encounter editor - local draft shell");
        subtitle.setFont(ConsoleTheme.SMALL_FONT);
        subtitle.setForeground(ConsoleTheme.MUTED_TEXT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        titles.add(title);
        titles.add(Box.createVerticalStrut(3));
        titles.add(subtitle);
        titleRow.add(titles, BorderLayout.WEST);
        titleRow.add(createStateStrip(), BorderLayout.EAST);

        top.add(titleRow);
        top.add(Box.createVerticalStrut(12));
        top.add(createSearchBar());
        return top;
    }

    private JComponent createStateStrip() {
        JPanel states = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        states.setBackground(ConsoleTheme.WINDOW);
        states.add(draftState);
        states.add(liveState);
        states.add(savedState);
        return states;
    }

    private JComponent createSearchBar() {
        JPanel wrapper = new JPanel(new BorderLayout(8, 5));
        wrapper.setBackground(ConsoleTheme.CARD);
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(10, 10, 10, 10)));
        wrapper.setAlignmentX(LEFT_ALIGNMENT);

        JLabel label = new JLabel("NPC / Boss");
        label.setFont(ConsoleTheme.SECTION_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        wrapper.add(label, BorderLayout.WEST);

        npcSearchField.setToolTipText("Enter an NPC id or NPC name. No mode dropdown is used.");
        ConsoleTheme.styleTextField(npcSearchField);
        npcSearchField.addActionListener(e -> prepareSearch());
        wrapper.add(npcSearchField, BorderLayout.CENTER);

        JButton searchButton = new JButton("Search");
        ConsoleTheme.styleButton(searchButton);
        searchButton.addActionListener(e -> prepareSearch());
        wrapper.add(searchButton, BorderLayout.EAST);

        searchStatus.setFont(ConsoleTheme.SMALL_FONT);
        searchStatus.setForeground(ConsoleTheme.MUTED_TEXT);
        wrapper.add(searchStatus, BorderLayout.SOUTH);
        return wrapper;
    }

    private void prepareSearch() {
        String query = npcSearchField.getText() == null ? "" : npcSearchField.getText().trim();
        if (query.length() == 0) {
            searchStatus.setText("Enter an NPC id or name.");
            return;
        }

        boolean numeric = isAllDigits(query);
        if (numeric) {
            suppressDraftEvents = true;
            try {
                npcIdField.setText(query);
            } finally {
                suppressDraftEvents = false;
            }
            markDraftChanged();
            searchStatus.setText("NPC id query prepared: " + query + " - server lookup is not connected yet.");
        } else {
            searchStatus.setText("NPC name query prepared: " + query + " - server lookup is not connected yet.");
        }
    }

    private boolean isAllDigits(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return value.length() > 0;
    }

    private JComponent createTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(ConsoleTheme.BODY_FONT);
        tabs.setForeground(ConsoleTheme.TEXT);
        tabs.setBackground(ConsoleTheme.PANEL);
        tabs.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
        tabs.setOpaque(true);

        tabs.addTab("Identity", createIdentityTab());
        tabs.addTab("Stats", createStatsTab());
        tabs.addTab("Attacks", createPlaceholderTab("Attacks",
                "Attack definitions will populate from the selected BossDefinition once the server bridge is connected.",
                "The editor will preserve Matrix3 combat ownership and only publish complete validated definitions."));
        tabs.addTab("Phases", createPlaceholderTab("Phases",
                "Health-range phases and their enabled attacks will be edited here.",
                "Draft changes remain local until an explicit Apply Live or Save & Apply."));
        tabs.addTab("Mechanics", createPlaceholderTab("Mechanics",
                "Reusable encounter mechanics are added only when the first boss proves a need.",
                "BossLabs will not grow a speculative general-purpose scripting engine."));
        tabs.addTab("Arena / Tiles", createArenaTab());
        tabs.addTab("Drops", createPlaceholderTab("Drops",
                "Drop editing will route through Matrix3's existing drop authority after its bridge is wired.",
                "BossLabs does not own a second drop engine."));
        tabs.addTab("Testing", createTestingTab());
        return tabs;
    }

    private JComponent createIdentityTab() {
        JPanel content = createVerticalContent();
        content.add(createSectionCard("Boss definition", createIdentityForm()));
        content.add(Box.createVerticalStrut(10));
        content.add(createInfoCard(
                "Combat ownership",
                "Existing Matrix3 Java/default combat source will be shown after server inspection is connected.",
                "A legacy Java boss will never be falsely presented as already converted BossLabs mechanics."));
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JComponent createIdentityForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);

        configureEditableField(definitionIdField, "Stable BossLabs definition id, for example volcanic_warden");
        configureEditableField(displayNameField, "BossLabs display name");
        configureEditableField(npcIdField, "Matrix3 NPC id");

        addFormRow(form, 0, "Definition ID", definitionIdField);
        addFormRow(form, 1, "Display name", displayNameField);
        addFormRow(form, 2, "NPC ID", npcIdField);
        return form;
    }

    private JComponent createStatsTab() {
        JPanel content = createVerticalContent();
        JPanel values = new JPanel(new GridBagLayout());
        values.setOpaque(false);

        addReadOnlyRow(values, 0, "Combat level", "-");
        addReadOnlyRow(values, 1, "Size", "-");
        addReadOnlyRow(values, 2, "Hitpoints", "-");
        addReadOnlyRow(values, 3, "Attack speed", "-");
        addReadOnlyRow(values, 4, "Attack animation", "-");
        addReadOnlyRow(values, 5, "Defence animation", "-");
        addReadOnlyRow(values, 6, "Death animation", "-");
        addReadOnlyRow(values, 7, "Combat source", "Waiting for server inspection");

        content.add(createSectionCard("Matrix3 NPC inspection", values));
        content.add(Box.createVerticalStrut(10));
        content.add(createInfoCard(
                "Authority",
                "These values are intentionally read-only in this shell until each stable Matrix3 owner/path is bridged.",
                "BossLabs will not invent stat semantics that Matrix3 does not actually use."));
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JComponent createArenaTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(ConsoleTheme.PANEL);
        panel.setBorder(ConsoleTheme.panelPadding(12, 12, 12, 12));

        JPanel heading = new JPanel();
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.setBackground(ConsoleTheme.PANEL);

        JLabel title = new JLabel("Arena / Tile Composer");
        title.setFont(ConsoleTheme.SECTION_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel instructions = new JLabel("Mouse wheel: zoom  |  Middle drag: pan  |  Left click: select tile  |  Hover: coordinate preview");
        instructions.setFont(ConsoleTheme.SMALL_FONT);
        instructions.setForeground(ConsoleTheme.MUTED_TEXT);
        instructions.setAlignmentX(LEFT_ALIGNMENT);

        JLabel tileStatus = new JLabel("Hover a tile to preview its relative coordinate.");
        tileStatus.setFont(ConsoleTheme.SMALL_FONT);
        tileStatus.setForeground(ConsoleTheme.ACCENT);
        tileStatus.setAlignmentX(LEFT_ALIGNMENT);

        heading.add(title);
        heading.add(Box.createVerticalStrut(4));
        heading.add(instructions);
        heading.add(Box.createVerticalStrut(4));
        heading.add(tileStatus);
        panel.add(heading, BorderLayout.NORTH);

        TileCanvas canvas = new TileCanvas(tileStatus);
        panel.add(canvas, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(ConsoleTheme.CARD);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(8, 10, 8, 10)));
        JLabel note = new JLabel("Canvas is draft-only. Damage, hazard, healing, telegraph, timing and pattern effect data are not published by this shell.");
        note.setFont(ConsoleTheme.SMALL_FONT);
        note.setForeground(ConsoleTheme.MUTED_TEXT);
        footer.add(note, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent createTestingTab() {
        JPanel content = createVerticalContent();

        JPanel buttons = new JPanel(new GridBagLayout());
        buttons.setOpaque(false);
        String[] names = { "Spawn Boss", "Teleport To Boss", "Reset Encounter", "Force Phase", "Force Attack", "Kill Boss", "Clear Hazards", "Clear Minions" };
        for (int index = 0; index < names.length; index++) {
            JButton button = new JButton(names[index]);
            ConsoleTheme.styleButton(button);
            button.setEnabled(false);
            button.setToolTipText("Requires the BossLabs client/server development bridge.");

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = index % 2;
            constraints.gridy = index / 2;
            constraints.weightx = 1.0;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.insets = new Insets(4, 4, 4, 4);
            buttons.add(button, constraints);
        }

        content.add(createSectionCard("Encounter controls", buttons));
        content.add(Box.createVerticalStrut(10));
        content.add(createInfoCard(
                "Bridge required",
                "Testing controls stay disabled until they can route through an authoritative Matrix3 development path.",
                "The Swing window will never directly mutate server NPC/world state."));
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JComponent createPlaceholderTab(String title, String... lines) {
        JPanel content = createVerticalContent();
        content.add(createInfoCard(title, lines));
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JComponent createPublishBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(ConsoleTheme.WINDOW);
        bar.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));

        JLabel status = new JLabel("Publish controls require the BossLabs client/server bridge.");
        status.setFont(ConsoleTheme.SMALL_FONT);
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        bar.add(status, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setBackground(ConsoleTheme.WINDOW);
        configureServerButton(applySavedButton);
        configureServerButton(undoButton);
        configureServerButton(applyLiveButton);
        configureServerButton(saveApplyButton);
        actions.add(applySavedButton);
        actions.add(undoButton);
        actions.add(applyLiveButton);
        actions.add(saveApplyButton);
        bar.add(actions, BorderLayout.EAST);
        return bar;
    }

    private void configureServerButton(JButton button) {
        ConsoleTheme.styleButton(button);
        button.setEnabled(false);
        button.setToolTipText("Enabled after the BossLabs client/server bridge is implemented.");
    }

    private JPanel createVerticalContent() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(12, 12, 12, 12));
        return content;
    }

    private JComponent createSectionCard(String titleText, JComponent body) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(ConsoleTheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(12, 12, 12, 12)));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel title = new JLabel(titleText);
        title.setFont(ConsoleTheme.SECTION_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        card.add(title, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent createInfoCard(String titleText, String... lines) {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        for (int index = 0; index < lines.length; index++) {
            JLabel line = new JLabel("<html>" + escapeHtml(lines[index]) + "</html>");
            line.setFont(ConsoleTheme.SMALL_FONT);
            line.setForeground(ConsoleTheme.MUTED_TEXT);
            line.setAlignmentX(LEFT_ALIGNMENT);
            body.add(line);
            if (index + 1 < lines.length) {
                body.add(Box.createVerticalStrut(5));
            }
        }
        return createSectionCard(titleText, body);
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JScrollPane scroll(JComponent component) {
        JScrollPane scroll = new JScrollPane(component);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(scroll);
        return scroll;
    }

    private void configureEditableField(JTextField field, String tooltip) {
        field.setToolTipText(tooltip);
        field.setPreferredSize(new Dimension(260, 36));
        ConsoleTheme.styleTextField(field);
    }

    private void addFormRow(JPanel panel, int row, String labelText, JComponent component) {
        JLabel label = new JLabel(labelText);
        label.setFont(ConsoleTheme.BODY_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(5, 0, 5, 12);
        panel.add(label, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(5, 0, 5, 0);
        panel.add(component, fieldConstraints);
    }

    private void addReadOnlyRow(JPanel panel, int row, String labelText, String valueText) {
        JLabel value = new JLabel(valueText);
        value.setFont(ConsoleTheme.BODY_FONT);
        value.setForeground(ConsoleTheme.TEXT);
        addFormRow(panel, row, labelText, value);
    }

    private void installDraftListeners() {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markDraftChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markDraftChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                markDraftChanged();
            }
        };
        definitionIdField.getDocument().addDocumentListener(listener);
        displayNameField.getDocument().addDocumentListener(listener);
        npcIdField.getDocument().addDocumentListener(listener);
    }

    private void markDraftChanged() {
        if (suppressDraftEvents) {
            return;
        }
        draftDirty = true;
        updateDraftState();
    }

    private void updateDraftState() {
        draftState.setText(draftDirty ? "DRAFT: modified" : "DRAFT: clean");
        draftState.setBackground(draftDirty ? ConsoleTheme.ACCENT_DARK : ConsoleTheme.CARD_HOVER);
    }

    private static JLabel createStateLabel(String text, Color background) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        label.setBackground(background);
        label.setOpaque(true);
        label.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        return label;
    }

    /**
     * Draft-only encounter-relative tile canvas. It intentionally edits no
     * combat/world state; this checkpoint proves only zoom/pan/hover/selection
     * interaction and relative tile visualization.
     */
    private static final class TileCanvas extends JPanel {

        private static final long serialVersionUID = -3032546666474856889L;
        private static final int MIN_TILE_SIZE = 14;
        private static final int MAX_TILE_SIZE = 96;

        private final JLabel statusLabel;
        private int tileSize = 32;
        private int panX;
        private int panY;
        private Point lastPanPoint;
        private int hoverTileX = Integer.MIN_VALUE;
        private int hoverTileY = Integer.MIN_VALUE;
        private int selectedTileX = Integer.MIN_VALUE;
        private int selectedTileY = Integer.MIN_VALUE;

        private TileCanvas(JLabel statusLabel) {
            this.statusLabel = statusLabel;
            setBackground(ConsoleTheme.INPUT);
            setOpaque(true);
            setPreferredSize(new Dimension(700, 440));
            setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
            setFocusable(true);

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isMiddleMouseButton(e)) {
                        lastPanPoint = e.getPoint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (SwingUtilities.isMiddleMouseButton(e)) {
                        lastPanPoint = null;
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        updateHover(e.getPoint());
                        selectedTileX = hoverTileX;
                        selectedTileY = hoverTileY;
                        statusLabel.setText("Selected draft tile: " + selectedTileX + ", " + selectedTileY + " - no encounter effect assigned yet.");
                        requestFocusInWindow();
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverTileX = Integer.MIN_VALUE;
                    hoverTileY = Integer.MIN_VALUE;
                    repaint();
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    zoomAt(e.getPoint(), e.getWheelRotation());
                }
            };
            addMouseListener(mouse);
            addMouseWheelListener(mouse);

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHover(e.getPoint());
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (lastPanPoint != null) {
                        panX += e.getX() - lastPanPoint.x;
                        panY += e.getY() - lastPanPoint.y;
                        lastPanPoint = e.getPoint();
                        updateHover(e.getPoint());
                        repaint();
                    }
                }
            });
        }

        private void zoomAt(Point point, int wheelRotation) {
            int oldSize = tileSize;
            int requested = oldSize - wheelRotation * 4;
            int newSize = Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, requested));
            if (newSize == oldSize) {
                return;
            }

            double originX = getWidth() / 2.0 + panX;
            double originY = getHeight() / 2.0 + panY;
            double worldX = (point.x - originX) / oldSize;
            double worldY = (point.y - originY) / oldSize;

            tileSize = newSize;
            panX = (int) Math.round(point.x - worldX * tileSize - getWidth() / 2.0);
            panY = (int) Math.round(point.y - worldY * tileSize - getHeight() / 2.0);
            updateHover(point);
            repaint();
        }

        private void updateHover(Point point) {
            int originX = getWidth() / 2 + panX;
            int originY = getHeight() / 2 + panY;
            hoverTileX = floorDiv(point.x - originX, tileSize);
            hoverTileY = floorDiv(point.y - originY, tileSize);
            statusLabel.setText("Hover tile: " + hoverTileX + ", " + hoverTileY + "  |  zoom " + tileSize + " px/tile");
            repaint();
        }

        private int floorDiv(int value, int divisor) {
            int result = value / divisor;
            if ((value ^ divisor) < 0 && result * divisor != value) {
                result--;
            }
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
                for (int x = startX; x < getWidth(); x += tileSize) {
                    g.drawLine(x, 0, x, getHeight());
                }
                for (int y = startY; y < getHeight(); y += tileSize) {
                    g.drawLine(0, y, getWidth(), y);
                }

                g.setStroke(new BasicStroke(2.0f));
                g.setColor(ConsoleTheme.ACCENT_DARK);
                g.drawLine(originX, 0, originX, getHeight());
                g.drawLine(0, originY, getWidth(), originY);

                if (selectedTileX != Integer.MIN_VALUE) {
                    int selectedX = originX + selectedTileX * tileSize;
                    int selectedY = originY + selectedTileY * tileSize;
                    g.setColor(new Color(ConsoleTheme.ACCENT.getRed(), ConsoleTheme.ACCENT.getGreen(), ConsoleTheme.ACCENT.getBlue(), 70));
                    g.fillRect(selectedX + 1, selectedY + 1, Math.max(1, tileSize - 1), Math.max(1, tileSize - 1));
                    g.setColor(ConsoleTheme.ACCENT);
                    g.drawRect(selectedX, selectedY, tileSize, tileSize);
                }

                if (hoverTileX != Integer.MIN_VALUE) {
                    int hoverX = originX + hoverTileX * tileSize;
                    int hoverY = originY + hoverTileY * tileSize;
                    g.setColor(ConsoleTheme.TEXT);
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawRect(hoverX, hoverY, tileSize, tileSize);
                }

                g.setFont(ConsoleTheme.SMALL_FONT);
                g.setColor(ConsoleTheme.MUTED_TEXT);
                g.drawString("0,0", originX + 5, originY - 6);
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
