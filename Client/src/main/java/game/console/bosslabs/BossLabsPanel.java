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
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import game.console.ConsoleTheme;

/**
 * BossLabs external encounter editor.
 *
 * DRAFT state remains client-local. Search, inspection and explicit publish
 * actions route through the BossLabs client/server bridge while Matrix3 remains
 * authoritative for NPC/combat/world behavior.
 */
public final class BossLabsPanel extends JPanel implements BossLabsClientBridge.Listener {

    private static final long serialVersionUID = 1721751006227827808L;

    private final JTextField npcSearchField = new JTextField();
    private final JLabel searchStatus = new JLabel("Search by NPC id or name.");
    private final DefaultListModel<BossLabsClientBridge.SearchResult> searchResultsModel =
            new DefaultListModel<BossLabsClientBridge.SearchResult>();
    private final JList<BossLabsClientBridge.SearchResult> searchResults =
            new JList<BossLabsClientBridge.SearchResult>(searchResultsModel);
    private JScrollPane searchResultsScroll;

    private final JLabel draftState = createStateLabel("DRAFT", ConsoleTheme.ACCENT);
    private final JLabel liveState = createStateLabel("LIVE: none", ConsoleTheme.CARD_HOVER);
    private final JLabel savedState = createStateLabel("SAVED: none", ConsoleTheme.CARD_HOVER);

    private final JTextField definitionIdField = new JTextField();
    private final JTextField displayNameField = new JTextField();
    private final JTextField npcIdField = new JTextField();

    private final JLabel combatLevelValue = createValueLabel("-");
    private final JLabel sizeValue = createValueLabel("-");
    private final JLabel hitpointsValue = createValueLabel("-");
    private final JLabel attackSpeedValue = createValueLabel("-");
    private final JLabel attackAnimationValue = createValueLabel("-");
    private final JLabel defenceAnimationValue = createValueLabel("-");
    private final JLabel deathAnimationValue = createValueLabel("-");
    private final JLabel respawnDelayValue = createValueLabel("-");
    private final JLabel attackGraphicValue = createValueLabel("-");
    private final JLabel attackProjectileValue = createValueLabel("-");
    private final JLabel aggressiveValue = createValueLabel("-");
    private final JLabel aggressionRangeValue = createValueLabel("-");
    private final JLabel poisonImmuneValue = createValueLabel("-");
    private final JLabel combatSourceValue = createValueLabel("Waiting for inspection");
    private final JLabel combatScriptValue = createValueLabel("-");
    private final JLabel ownershipValue = createValueLabel("-");

    private final JButton applyLiveButton = new JButton("Apply Live");
    private final JButton saveApplyButton = new JButton("Save & Apply");
    private final JButton undoButton = new JButton("Undo Last Apply");
    private final JButton applySavedButton = new JButton("Apply Saved");
    private final JLabel publishStatus = new JLabel("Select an NPC to inspect its BossLabs state.");

    private final BossLabsDefinitionEditor definitionEditor;

    private boolean suppressDraftEvents;
    private boolean draftDirty;
    private int selectedNpcId = -1;
    private boolean hasBossLabsDefinition;
    private boolean savedAvailable;
    private boolean rollbackAvailable;

    public BossLabsPanel() {
        super(new BorderLayout());
        setBackground(ConsoleTheme.WINDOW);
        setBorder(ConsoleTheme.panelPadding(16, 16, 16, 16));

        definitionEditor = new BossLabsDefinitionEditor(new Runnable() {
            @Override
            public void run() {
                markDraftChanged();
            }
        });

        add(createTopArea(), BorderLayout.NORTH);
        add(createTabs(), BorderLayout.CENTER);
        add(createPublishBar(), BorderLayout.SOUTH);

        installDraftListeners();
        updateDraftState();
        updatePublishButtons();
        BossLabsClientBridge.setListener(this);
    }

    void disposeBridge() {
        BossLabsClientBridge.clearListener(this);
    }

    BossLabsDraftDefinition getArenaDraft() {
        return definitionEditor.getDraft();
    }

    void arenaDraftChanged() {
        markDraftChanged();
        definitionEditor.getAttacksComponent().repaint();
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

        JLabel subtitle = new JLabel("Matrix3 encounter editor - connected development bridge");
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
        top.add(Box.createVerticalStrut(6));
        top.add(createSearchResults());
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

    private JComponent createSearchResults() {
        searchResults.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        searchResults.setBackground(ConsoleTheme.INPUT);
        searchResults.setForeground(ConsoleTheme.TEXT);
        searchResults.setFont(ConsoleTheme.BODY_FONT);
        searchResults.setFixedCellHeight(28);
        searchResults.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            BossLabsClientBridge.SearchResult result = searchResults.getSelectedValue();
            if (result != null) {
                inspectNpc(result.getNpcId());
            }
        });

        searchResultsScroll = new JScrollPane(searchResults);
        searchResultsScroll.setAlignmentX(LEFT_ALIGNMENT);
        searchResultsScroll.setPreferredSize(new Dimension(500, 112));
        searchResultsScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 112));
        searchResultsScroll.setVisible(false);
        ConsoleTheme.styleScrollPane(searchResultsScroll);
        return searchResultsScroll;
    }

    private void prepareSearch() {
        String query = npcSearchField.getText() == null ? "" : npcSearchField.getText().trim();
        if (query.length() == 0) {
            searchStatus.setText("Enter an NPC id or name.");
            return;
        }

        searchResultsModel.clear();
        searchResults.clearSelection();
        searchResultsScroll.setVisible(false);
        revalidate();

        if (isAllDigits(query)) {
            try {
                inspectNpc(Integer.parseInt(query));
            } catch (NumberFormatException ex) {
                searchStatus.setText("NPC id is too large: " + query);
            }
            return;
        }

        searchStatus.setText("Searching Matrix3 NPC definitions for \"" + query + "\"...");
        BossLabsClientBridge.requestSearch(query);
    }

    private void inspectNpc(int npcId) {
        selectedNpcId = npcId;
        searchStatus.setText("Inspecting NPC " + npcId + "...");
        publishStatus.setText("Loading Matrix3/BossLabs ownership...");
        updatePublishButtons();
        BossLabsClientBridge.requestInspect(npcId);
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
        tabs.addTab("Attacks", definitionEditor.getAttacksComponent());
        tabs.addTab("Phases", definitionEditor.getPhasesComponent());
        tabs.addTab("Mechanics", createPlaceholderTab("Mechanics",
                "Reusable encounter mechanics are added only when the first boss proves a need.",
                "BossLabs will not grow a speculative general-purpose scripting engine."));
        tabs.addTab("Arena / Tiles", new BossLabsArenaPanel(this));
        tabs.addTab("Drops", createPlaceholderTab("Drops",
                "Drop editing will route through Matrix3's existing drop authority when that authoring slice is implemented.",
                "BossLabs does not own a second drop engine."));
        tabs.addTab("Testing", createTestingTab());
        return tabs;
    }

    private JComponent createIdentityTab() {
        JPanel content = createVerticalContent();
        content.add(createSectionCard("Boss definition", createIdentityForm()));
        content.add(Box.createVerticalStrut(10));
        content.add(createSectionCard("Combat ownership", createOwnershipForm()));
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

    private JComponent createOwnershipForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        addFormRow(form, 0, "Combat script", combatScriptValue);
        addFormRow(form, 1, "Ownership", ownershipValue);
        return form;
    }

    private JComponent createStatsTab() {
        JPanel content = createVerticalContent();
        JPanel values = new JPanel(new GridBagLayout());
        values.setOpaque(false);

        addFormRow(values, 0, "Combat level", combatLevelValue);
        addFormRow(values, 1, "Size", sizeValue);
        addFormRow(values, 2, "Hitpoints", hitpointsValue);
        addFormRow(values, 3, "Attack speed", attackSpeedValue);
        addFormRow(values, 4, "Attack animation", attackAnimationValue);
        addFormRow(values, 5, "Defence animation", defenceAnimationValue);
        addFormRow(values, 6, "Death animation", deathAnimationValue);
        addFormRow(values, 7, "Respawn delay", respawnDelayValue);
        addFormRow(values, 8, "Attack graphic", attackGraphicValue);
        addFormRow(values, 9, "Attack projectile", attackProjectileValue);
        addFormRow(values, 10, "Aggressive", aggressiveValue);
        addFormRow(values, 11, "Aggression range", aggressionRangeValue);
        addFormRow(values, 12, "Poison immune", poisonImmuneValue);
        addFormRow(values, 13, "Combat source", combatSourceValue);

        content.add(createSectionCard("Matrix3 NPC inspection", values));
        content.add(Box.createVerticalStrut(10));
        content.add(createInfoCard(
                "Authority",
                "Inspection values are read-only here and come from Matrix3's existing NPC/combat definitions.",
                "BossLabs only publishes fields it actually owns."));
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
        JLabel note = new JLabel("Canvas remains draft-only. Effect authoring/publishing is a later BossLabs slice.");
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
            button.setToolTipText("BossLabs testing API is not implemented yet.");

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
                "Testing API pending",
                "Search, inspection and complete definition publishing use the live BossLabs bridge.",
                "Spawn/reset/force-phase controls stay disabled until matching authoritative server APIs are implemented."));
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

        publishStatus.setFont(ConsoleTheme.SMALL_FONT);
        publishStatus.setForeground(ConsoleTheme.MUTED_TEXT);
        bar.add(publishStatus, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setBackground(ConsoleTheme.WINDOW);
        configureServerButton(applySavedButton, e -> applySaved());
        configureServerButton(undoButton, e -> undoLastApply());
        configureServerButton(applyLiveButton, e -> publish(false));
        configureServerButton(saveApplyButton, e -> publish(true));
        actions.add(applySavedButton);
        actions.add(undoButton);
        actions.add(applyLiveButton);
        actions.add(saveApplyButton);
        bar.add(actions, BorderLayout.EAST);
        return bar;
    }

    private void configureServerButton(JButton button, java.awt.event.ActionListener listener) {
        ConsoleTheme.styleButton(button);
        button.addActionListener(listener);
    }

    private void publish(boolean save) {
        Integer npcId = readNpcId();
        if (npcId == null || npcId.intValue() != selectedNpcId) {
            publishStatus.setText("Reload the NPC before publishing after changing its NPC id.");
            return;
        }

        BossLabsDraftDefinition draft = definitionEditor.getDraft();
        if (draft == null) {
            publishStatus.setText("No BossLabs draft is loaded.");
            return;
        }
        syncDraftIdentity(draft, npcId.intValue());
        String validation = draft.validate();
        if (validation != null) {
            publishStatus.setText(validation);
            return;
        }

        publishStatus.setForeground(ConsoleTheme.MUTED_TEXT);
        publishStatus.setText(save ? "Saving and applying complete BossLabs draft..." : "Applying complete BossLabs draft live...");
        BossLabsClientBridge.requestPublishDefinition(draft, save);
    }

    private void undoLastApply() {
        if (selectedNpcId < 0 || !rollbackAvailable) {
            return;
        }
        publishStatus.setText("Restoring previous live BossLabs definition...");
        BossLabsClientBridge.requestUndo(selectedNpcId);
    }

    private void applySaved() {
        if (selectedNpcId < 0 || !savedAvailable) {
            return;
        }
        publishStatus.setText("Applying saved BossLabs definition...");
        BossLabsClientBridge.requestApplySaved(selectedNpcId);
    }

    private Integer readNpcId() {
        String text = npcIdField.getText() == null ? "" : npcIdField.getText().trim();
        try {
            return text.length() == 0 ? null : Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void syncDraftIdentity(BossLabsDraftDefinition draft, int npcId) {
        draft.setId(definitionIdField.getText() == null ? "" : definitionIdField.getText().trim());
        draft.setDisplayName(displayNameField.getText() == null ? "" : displayNameField.getText().trim());
        draft.setNpcId(npcId);
    }

    private void updatePublishButtons() {
        Integer draftNpcId = readNpcId();
        boolean sameNpc = selectedNpcId >= 0 && draftNpcId != null && draftNpcId.intValue() == selectedNpcId;
        BossLabsDraftDefinition draft = definitionEditor == null ? null : definitionEditor.getDraft();
        boolean validDraft = false;
        if (sameNpc && draft != null) {
            syncDraftIdentity(draft, draftNpcId.intValue());
            validDraft = draft.validate() == null;
        }

        applyLiveButton.setEnabled(validDraft);
        saveApplyButton.setEnabled(validDraft);
        undoButton.setEnabled(sameNpc && rollbackAvailable);
        applySavedButton.setEnabled(sameNpc && savedAvailable);
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
        updatePublishButtons();
    }

    private void updateDraftState() {
        draftState.setText(draftDirty ? "DRAFT: modified" : "DRAFT: clean");
        draftState.setBackground(draftDirty ? ConsoleTheme.ACCENT_DARK : ConsoleTheme.CARD_HOVER);
    }

    private void setDraftClean() {
        draftDirty = false;
        updateDraftState();
    }

    private void withSuppressedDraftEvents(Runnable runnable) {
        suppressDraftEvents = true;
        try {
            runnable.run();
        } finally {
            suppressDraftEvents = false;
        }
    }

    private String defaultDefinitionId(String value) {
        String source = value == null ? "" : value.trim().toLowerCase();
        StringBuilder result = new StringBuilder();
        boolean underscore = false;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                result.append(character);
                underscore = false;
            } else if (!underscore && result.length() > 0) {
                result.append('_');
                underscore = true;
            }
        }
        while (result.length() > 0 && result.charAt(result.length() - 1) == '_')
            result.deleteCharAt(result.length() - 1);
        return result.toString();
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

    private static JLabel createValueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ConsoleTheme.BODY_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        return label;
    }

    @Override
    public void onSearchStarted(int requestId) {
        searchResultsModel.clear();
        searchResultsScroll.setVisible(true);
        searchStatus.setText("Searching Matrix3 NPC definitions...");
        revalidate();
        repaint();
    }

    @Override
    public void onSearchResult(int requestId, BossLabsClientBridge.SearchResult result) {
        searchResultsModel.addElement(result);
    }

    @Override
    public void onSearchFinished(int requestId, int count) {
        searchResultsScroll.setVisible(count > 0);
        searchStatus.setText(count == 0 ? "No NPCs matched that name." : count + " NPC result" + (count == 1 ? "" : "s") + ". Select one to inspect.");
        revalidate();
        repaint();
    }

    @Override
    public void onSearchError(int requestId, String message) {
        searchStatus.setText("Search failed: " + message);
    }

    @Override
    public void onInspection(int requestId, final BossLabsClientBridge.Inspection inspection) {
        selectedNpcId = inspection.getNpcId();
        final BossLabsDraftDefinition blankDraft = new BossLabsDraftDefinition("", inspection.getName(), inspection.getNpcId());
        definitionEditor.setDraft(blankDraft);

        withSuppressedDraftEvents(new Runnable() {
            @Override
            public void run() {
                npcIdField.setText(Integer.toString(inspection.getNpcId()));
                displayNameField.setText(inspection.getName());
                definitionIdField.setText("");
            }
        });

        combatLevelValue.setText(Integer.toString(inspection.getCombatLevel()));
        sizeValue.setText(Integer.toString(inspection.getSize()));
        hitpointsValue.setText(Integer.toString(inspection.getHitpoints()));
        attackSpeedValue.setText(Integer.toString(inspection.getAttackSpeed()));
        attackAnimationValue.setText(Integer.toString(inspection.getAttackAnimation()));
        defenceAnimationValue.setText(Integer.toString(inspection.getDefenceAnimation()));
        deathAnimationValue.setText(Integer.toString(inspection.getDeathAnimation()));
        respawnDelayValue.setText(Integer.toString(inspection.getRespawnDelay()));
        attackGraphicValue.setText(Integer.toString(inspection.getAttackGraphic()));
        attackProjectileValue.setText(Integer.toString(inspection.getAttackProjectile()));
        aggressiveValue.setText(inspection.isAggressive() ? "Yes" : "No");
        aggressionRangeValue.setText(Integer.toString(inspection.getAggressionRange()));
        poisonImmuneValue.setText(inspection.isPoisonImmune() ? "Yes" : "No");
        combatSourceValue.setText(inspection.getCombatSource());

        searchStatus.setText("Loaded " + inspection.getName() + " [" + inspection.getNpcId() + "].");
        publishStatus.setText("Inspection loaded. Waiting for ownership/definition state...");
        updatePublishButtons();
    }

    @Override
    public void onOwnership(int requestId, final BossLabsClientBridge.Ownership ownership) {
        if (ownership.getNpcId() != selectedNpcId) {
            return;
        }

        hasBossLabsDefinition = ownership.isBossLabsDefinition();
        savedAvailable = ownership.isSaved();
        rollbackAvailable = ownership.isRollbackAvailable();

        withSuppressedDraftEvents(new Runnable() {
            @Override
            public void run() {
                if (ownership.isBossLabsDefinition()) {
                    definitionIdField.setText(ownership.getDefinitionId());
                    if (ownership.getDisplayName().length() > 0) {
                        displayNameField.setText(ownership.getDisplayName());
                    }
                } else {
                    definitionIdField.setText(defaultDefinitionId(displayNameField.getText()));
                }
            }
        });

        String scriptName = ownership.getScriptName();
        combatScriptValue.setText(scriptName == null || scriptName.length() == 0 ? "Matrix3 default" : scriptName);
        ownershipValue.setText(ownership.isBossLabsDefinition() ? "BossLabs live definition" : "Matrix3 Java/default combat");
        liveState.setText(ownership.isBossLabsDefinition() ? "LIVE: BossLabs" : "LIVE: Matrix3");
        liveState.setBackground(ownership.isBossLabsDefinition() ? ConsoleTheme.ACCENT_DARK : ConsoleTheme.CARD_HOVER);
        savedState.setText(ownership.isSaved() ? "SAVED: yes" : "SAVED: no");
        savedState.setBackground(ownership.isSaved() ? ConsoleTheme.ACCENT_DARK : ConsoleTheme.CARD_HOVER);

        if (ownership.isBossLabsDefinition()) {
            publishStatus.setText("BossLabs ownership loaded. Loading complete phase/attack definition...");
        } else {
            BossLabsDraftDefinition draft = definitionEditor.getDraft();
            if (draft != null)
                syncDraftIdentity(draft, selectedNpcId);
            setDraftClean();
            publishStatus.setText("Matrix3 NPC ready for first-time BossLabs authoring. Add a phase and attack to enable Apply.");
        }
        updatePublishButtons();
    }

    @Override
    public void onDefinitionLoaded(int requestId, final BossLabsDraftDefinition definition) {
        if (definition == null || definition.getNpcId() != selectedNpcId)
            return;
        definitionEditor.setDraft(definition);
        hasBossLabsDefinition = true;
        withSuppressedDraftEvents(new Runnable() {
            @Override
            public void run() {
                npcIdField.setText(Integer.toString(definition.getNpcId()));
                definitionIdField.setText(definition.getId());
                displayNameField.setText(definition.getDisplayName());
            }
        });
        setDraftClean();
        publishStatus.setForeground(ConsoleTheme.MUTED_TEXT);
        publishStatus.setText("Complete BossLabs definition loaded. Phases and attacks are ready to edit.");
        updatePublishButtons();
    }

    @Override
    public void onDefinitionEmpty(int requestId, int npcId) {
        if (npcId != selectedNpcId)
            return;
        BossLabsDraftDefinition draft = definitionEditor.getDraft();
        if (draft == null) {
            draft = new BossLabsDraftDefinition(defaultDefinitionId(displayNameField.getText()),
                    displayNameField.getText(), npcId);
            definitionEditor.setDraft(draft);
        }
        syncDraftIdentity(draft, npcId);
        setDraftClean();
        updatePublishButtons();
    }

    @Override
    public void onInspectionMissing(int requestId, int npcId) {
        if (npcId == selectedNpcId) {
            selectedNpcId = -1;
        }
        hasBossLabsDefinition = false;
        savedAvailable = false;
        rollbackAvailable = false;
        definitionEditor.setDraft(null);
        searchStatus.setText("NPC " + npcId + " was not found.");
        publishStatus.setText("No NPC loaded.");
        updatePublishButtons();
    }

    @Override
    public void onActionResult(BossLabsClientBridge.ActionResult result) {
        publishStatus.setText(result.getMessage());
        publishStatus.setForeground(result.isSuccess() ? ConsoleTheme.ACCENT : ConsoleTheme.MUTED_TEXT);
    }

    /**
     * Draft-only encounter-relative tile canvas. It intentionally edits no
     * combat/world state; this checkpoint proves zoom/pan/hover/selection
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
