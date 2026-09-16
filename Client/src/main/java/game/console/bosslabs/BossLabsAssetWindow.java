package game.console.bosslabs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import game.ClientConsoleBridge;
import game.console.ConsoleTheme;

/**
 * Shared BossLabs animation/GFX browser.
 *
 * Matrix3 cache animation and graphic definitions are numeric/unnamed. Instead
 * of inventing a second asset database, this window reuses the IDs already in
 * the current BossLabs workspace, provides fast nearby-ID browsing, and reuses
 * Matrix3's existing admin emote/gfx commands for live preview on the player.
 */
public final class BossLabsAssetWindow extends JDialog {

    private static final long serialVersionUID = -5099092276502351067L;
    private static final int HISTORY_LIMIT = 40;

    private static BossLabsAssetWindow instance;

    private final JComponent bossRoot;
    private JTextField targetField;

    private final JComboBox<AssetType> type = new JComboBox<AssetType>(AssetType.values());
    private final JTextField idField = new JTextField("-1");
    private final JCheckBox autoPreview = new JCheckBox("Auto-preview while stepping");
    private final JLabel targetLabel = new JLabel("Target: none");
    private final JLabel status = new JLabel("Choose an asset ID or reuse one from the current boss.");
    private final JLabel previewHint = new JLabel();

    private final JTextField referenceSearch = new JTextField();
    private final DefaultListModel<AssetReference> referenceModel = new DefaultListModel<AssetReference>();
    private final JList<AssetReference> referenceList = new JList<AssetReference>(referenceModel);
    private final List<AssetReference> allReferences = new ArrayList<AssetReference>();

    private final DefaultListModel<AssetReference> historyModel = new DefaultListModel<AssetReference>();
    private final JList<AssetReference> historyList = new JList<AssetReference>(historyModel);

    private final JButton useId = new JButton("Use ID in BossLabs");
    private final JButton copyId = new JButton("Copy ID");
    private final JButton preview = new JButton("Preview on Player");
    private final JButton clearTarget = new JButton("Set Target to -1");

    public static void open(Window owner, JComponent bossRoot, JTextField targetField) {
        Runnable opener = new Runnable() {
            @Override
            public void run() {
                if (instance == null || !instance.isDisplayable() || instance.getOwner() != owner) {
                    if (instance != null && instance.isDisplayable())
                        instance.dispose();
                    instance = new BossLabsAssetWindow(owner, bossRoot, targetField);
                } else {
                    instance.setTargetField(targetField);
                    instance.refreshBossReferences();
                }
                instance.setVisible(true);
                instance.toFront();
                instance.requestFocus();
            }
        };
        if (SwingUtilities.isEventDispatchThread())
            opener.run();
        else
            SwingUtilities.invokeLater(opener);
    }

    public static void closeForOwner(Window owner) {
        if (instance != null && instance.getOwner() == owner) {
            instance.dispose();
            instance = null;
        }
    }

    private BossLabsAssetWindow(Window owner, JComponent bossRoot, JTextField targetField) {
        super(owner, "BossLabs - Asset Browser", ModalityType.MODELESS);
        this.bossRoot = bossRoot;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setBackground(ConsoleTheme.WINDOW);
        setMinimumSize(new Dimension(720, 560));
        setSize(860, 680);
        setLocationRelativeTo(owner);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(ConsoleTheme.WINDOW);
        root.setBorder(ConsoleTheme.panelPadding(12, 12, 12, 12));
        root.add(createHeader(), BorderLayout.NORTH);
        root.add(createWorkspace(), BorderLayout.CENTER);
        root.add(createActions(), BorderLayout.SOUTH);
        setContentPane(root);

        installListeners();
        setTargetField(targetField);
        refreshBossReferences();
        updatePreviewHint();
        updateActions();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (instance == BossLabsAssetWindow.this)
                    instance = null;
            }
        });
    }

    private JComponent createHeader() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ConsoleTheme.CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(10, 12, 10, 12)));

        JLabel title = new JLabel("Animation / FX Asset Browser");
        title.setFont(ConsoleTheme.SECTION_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel explanation = new JLabel("<html>Matrix3 animation and GFX definitions are numeric rather than named. "
                + "Browse nearby IDs, preview them through Matrix3's existing admin commands, or reuse IDs already present in this boss.</html>");
        explanation.setFont(ConsoleTheme.SMALL_FONT);
        explanation.setForeground(ConsoleTheme.MUTED_TEXT);
        explanation.setAlignmentX(LEFT_ALIGNMENT);

        targetLabel.setFont(ConsoleTheme.SMALL_FONT);
        targetLabel.setForeground(ConsoleTheme.ACCENT);
        targetLabel.setAlignmentX(LEFT_ALIGNMENT);

        panel.add(title);
        panel.add(Box.createVerticalStrut(4));
        panel.add(explanation);
        panel.add(Box.createVerticalStrut(6));
        panel.add(targetLabel);
        return panel;
    }

    private JComponent createWorkspace() {
        JPanel browser = new JPanel(new BorderLayout(0, 8));
        browser.setBackground(ConsoleTheme.PANEL);
        browser.add(createBrowserControls(), BorderLayout.NORTH);

        JSplitPane lists = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                createReferencePanel(), createHistoryPanel());
        lists.setResizeWeight(0.68);
        lists.setDividerLocation(280);
        lists.setBorder(null);
        lists.setBackground(ConsoleTheme.PANEL);
        browser.add(lists, BorderLayout.CENTER);
        return browser;
    }

    private JComponent createBrowserControls() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(ConsoleTheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(10, 10, 10, 10)));

        type.setFont(ConsoleTheme.BODY_FONT);
        type.setBackground(ConsoleTheme.INPUT);
        type.setForeground(ConsoleTheme.TEXT);
        ConsoleTheme.styleTextField(idField);
        idField.setToolTipText("Raw Matrix3 animation or graphic ID. -1 means no authored asset when written back to BossLabs.");

        addRow(card, 0, "Asset type", type);
        addRow(card, 1, "Asset ID", idField);

        JPanel stepping = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        stepping.setOpaque(false);
        JButton minusTen = button("-10");
        JButton previous = button("Previous");
        JButton next = button("Next");
        JButton plusTen = button("+10");
        minusTen.addActionListener(e -> step(-10));
        previous.addActionListener(e -> step(-1));
        next.addActionListener(e -> step(1));
        plusTen.addActionListener(e -> step(10));
        stepping.add(minusTen);
        stepping.add(previous);
        stepping.add(next);
        stepping.add(plusTen);
        addRow(card, 2, "Browse", stepping);

        autoPreview.setOpaque(false);
        autoPreview.setForeground(ConsoleTheme.TEXT);
        autoPreview.setFont(ConsoleTheme.BODY_FONT);
        addRow(card, 3, "", autoPreview);

        previewHint.setFont(ConsoleTheme.SMALL_FONT);
        previewHint.setForeground(ConsoleTheme.MUTED_TEXT);
        addRow(card, 4, "Preview", previewHint);
        return card;
    }

    private JComponent createReferencePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(ConsoleTheme.PANEL);

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        JLabel label = new JLabel("Assets already used in this BossLabs workspace");
        label.setFont(ConsoleTheme.SECTION_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        top.add(label, BorderLayout.WEST);

        JButton refresh = button("Refresh");
        refresh.addActionListener(e -> refreshBossReferences());
        top.add(refresh, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        ConsoleTheme.styleTextField(referenceSearch);
        referenceSearch.setToolTipText("Filter current boss asset references by ID, type or field description.");
        panel.add(referenceSearch, BorderLayout.SOUTH);

        referenceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        referenceList.setBackground(ConsoleTheme.INPUT);
        referenceList.setForeground(ConsoleTheme.TEXT);
        referenceList.setSelectionBackground(ConsoleTheme.CARD_HOVER);
        referenceList.setFont(ConsoleTheme.BODY_FONT);
        JScrollPane scroll = new JScrollPane(referenceList);
        ConsoleTheme.styleScrollPane(scroll);

        JPanel center = new JPanel(new BorderLayout(0, 5));
        center.setOpaque(false);
        center.add(scroll, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JComponent createHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(ConsoleTheme.PANEL);

        JPanel titleRow = new JPanel(new BorderLayout(6, 0));
        titleRow.setOpaque(false);
        JLabel label = new JLabel("Preview history");
        label.setFont(ConsoleTheme.SECTION_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        titleRow.add(label, BorderLayout.WEST);
        JButton clear = button("Clear");
        clear.addActionListener(e -> historyModel.clear());
        titleRow.add(clear, BorderLayout.EAST);
        panel.add(titleRow, BorderLayout.NORTH);

        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.setBackground(ConsoleTheme.INPUT);
        historyList.setForeground(ConsoleTheme.TEXT);
        historyList.setSelectionBackground(ConsoleTheme.CARD_HOVER);
        historyList.setFont(ConsoleTheme.BODY_FONT);
        JScrollPane scroll = new JScrollPane(historyList);
        ConsoleTheme.styleScrollPane(scroll);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JComponent createActions() {
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setBackground(ConsoleTheme.WINDOW);

        status.setFont(ConsoleTheme.SMALL_FONT);
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        status.setAlignmentX(LEFT_ALIGNMENT);
        outer.add(status);
        outer.add(Box.createVerticalStrut(6));

        JPanel row = new JPanel(new java.awt.GridLayout(1, 4, 6, 0));
        row.setOpaque(false);
        styleButton(preview);
        styleButton(useId);
        styleButton(copyId);
        styleButton(clearTarget);
        preview.addActionListener(e -> previewCurrent());
        useId.addActionListener(e -> useCurrentId());
        copyId.addActionListener(e -> copyCurrentId());
        clearTarget.addActionListener(e -> setTargetValue("-1"));
        row.add(preview);
        row.add(useId);
        row.add(copyId);
        row.add(clearTarget);
        outer.add(row);
        return outer;
    }

    private void installListeners() {
        type.addActionListener(e -> {
            updatePreviewHint();
            updateActions();
        });
        idField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateActions(); }
            @Override public void removeUpdate(DocumentEvent e) { updateActions(); }
            @Override public void changedUpdate(DocumentEvent e) { updateActions(); }
        });
        idField.addActionListener(e -> previewCurrent());
        referenceSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterReferences(); }
            @Override public void removeUpdate(DocumentEvent e) { filterReferences(); }
            @Override public void changedUpdate(DocumentEvent e) { filterReferences(); }
        });
        referenceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting())
                adoptReference(referenceList.getSelectedValue());
        });
        historyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting())
                adoptReference(historyList.getSelectedValue());
        });
    }

    private void setTargetField(JTextField candidate) {
        targetField = isAssetField(candidate) ? candidate : null;
        if (targetField == null) {
            targetLabel.setText("Target: none — focus an Animation/GFX/Projectile ID field before opening Tools → Asset Browser.");
            return;
        }

        String tooltip = targetField.getToolTipText();
        AssetType inferred = classify(tooltip);
        if (inferred != null)
            type.setSelectedItem(inferred);
        String current = targetField.getText() == null ? "" : targetField.getText().trim();
        if (isInteger(current))
            idField.setText(current);
        targetLabel.setText("Target: " + compactLabel(tooltip));
    }

    private void refreshBossReferences() {
        allReferences.clear();
        Set<String> seen = new LinkedHashSet<String>();
        collectReferences(bossRoot, seen);
        filterReferences();
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        status.setText(allReferences.isEmpty()
                ? "No non-negative animation/GFX IDs are currently authored in the open BossLabs fields."
                : "Found " + allReferences.size() + " reusable asset reference(s) in the current BossLabs workspace.");
    }

    private void collectReferences(Component component, Set<String> seen) {
        if (component instanceof JTextField) {
            JTextField field = (JTextField) component;
            String tooltip = field.getToolTipText();
            AssetType assetType = classify(tooltip);
            String value = field.getText() == null ? "" : field.getText().trim();
            if (assetType != null && isInteger(value)) {
                try {
                    int id = Integer.parseInt(value);
                    if (id >= 0) {
                        String key = assetType.name() + ":" + id + ":" + compactLabel(tooltip);
                        if (seen.add(key))
                            allReferences.add(new AssetReference(assetType, id, compactLabel(tooltip)));
                    }
                } catch (NumberFormatException ignored) {
                    // Overflowing values are not useful asset references.
                }
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents())
                collectReferences(child, seen);
        }
    }

    private void filterReferences() {
        String value = referenceSearch.getText();
        String query = value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
        referenceModel.clear();
        for (AssetReference reference : allReferences) {
            if (query.length() == 0 || reference.searchText().contains(query))
                referenceModel.addElement(reference);
        }
    }

    private void adoptReference(AssetReference reference) {
        if (reference == null)
            return;
        type.setSelectedItem(reference.type);
        idField.setText(Integer.toString(reference.id));
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        status.setText("Selected " + reference.type.displayName + " " + reference.id + " from " + reference.label + ".");
    }

    private void step(int delta) {
        Integer current = parseCurrentId(true);
        if (current == null)
            return;
        long next = (long) current.intValue() + delta;
        if (next < 0)
            next = 0;
        if (next > Integer.MAX_VALUE)
            next = Integer.MAX_VALUE;
        idField.setText(Long.toString(next));
        if (autoPreview.isSelected())
            previewCurrent();
    }

    private void previewCurrent() {
        Integer id = parseCurrentId(false);
        if (id == null)
            return;
        AssetType selected = (AssetType) type.getSelectedItem();
        if (selected == null)
            return;
        String command = selected == AssetType.ANIMATION ? "emote " + id : "gfx " + id;
        String error = ClientConsoleBridge.queueConsoleCommand(command);
        if (error != null) {
            status.setForeground(ConsoleTheme.MUTED_TEXT);
            status.setText("Preview failed: " + error);
            return;
        }
        addHistory(new AssetReference(selected, id.intValue(), "previewed on player"));
        status.setForeground(ConsoleTheme.ACCENT);
        status.setText("Queued Matrix3 " + selected.displayName.toLowerCase(Locale.ENGLISH) + " preview for ID " + id + ".");
    }

    private void addHistory(AssetReference reference) {
        for (int index = 0; index < historyModel.size(); index++) {
            AssetReference existing = historyModel.get(index);
            if (existing.type == reference.type && existing.id == reference.id) {
                historyModel.remove(index);
                break;
            }
        }
        historyModel.add(0, reference);
        while (historyModel.size() > HISTORY_LIMIT)
            historyModel.remove(historyModel.size() - 1);
    }

    private void useCurrentId() {
        Integer id = parseCurrentId(true);
        if (id == null)
            return;
        setTargetValue(Integer.toString(id.intValue()));
    }

    private void setTargetValue(String value) {
        if (targetField == null || !targetField.isDisplayable()) {
            status.setForeground(ConsoleTheme.MUTED_TEXT);
            status.setText("No BossLabs asset field is targeted. Focus one, then reopen the Asset Browser.");
            return;
        }
        targetField.setText(value);
        targetField.requestFocusInWindow();
        status.setForeground(ConsoleTheme.ACCENT);
        status.setText("Wrote " + value + " into the targeted BossLabs field. Use that editor's Save action to commit the DRAFT change.");
        refreshBossReferences();
    }

    private void copyCurrentId() {
        Integer id = parseCurrentId(true);
        if (id == null)
            return;
        StringSelection selection = new StringSelection(Integer.toString(id.intValue()));
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        status.setForeground(ConsoleTheme.ACCENT);
        status.setText("Copied asset ID " + id + " to the clipboard.");
    }

    private Integer parseCurrentId(boolean allowMinusOne) {
        String value = idField.getText() == null ? "" : idField.getText().trim();
        try {
            int id = Integer.parseInt(value);
            if (id < (allowMinusOne ? -1 : 0)) {
                status.setForeground(ConsoleTheme.MUTED_TEXT);
                status.setText(allowMinusOne ? "Asset ID must be -1 or greater." : "Preview requires an asset ID of 0 or greater.");
                return null;
            }
            return Integer.valueOf(id);
        } catch (NumberFormatException e) {
            status.setForeground(ConsoleTheme.MUTED_TEXT);
            status.setText("Asset ID must be a whole number.");
            return null;
        }
    }

    private void updatePreviewHint() {
        AssetType selected = (AssetType) type.getSelectedItem();
        if (selected == AssetType.ANIMATION) {
            previewHint.setText("Uses Matrix3's existing admin emote command on your player.");
        } else {
            previewHint.setText("Uses Matrix3's existing admin gfx command. Projectile IDs are GFX IDs; this previews the visual, not its flight path.");
        }
    }

    private void updateActions() {
        boolean valid = isInteger(idField.getText() == null ? "" : idField.getText().trim());
        preview.setEnabled(valid);
        copyId.setEnabled(valid);
        useId.setEnabled(valid && targetField != null);
        clearTarget.setEnabled(targetField != null);
    }

    private static boolean isAssetField(JTextField field) {
        return field != null && classify(field.getToolTipText()) != null;
    }

    private static AssetType classify(String text) {
        if (text == null)
            return null;
        String value = text.toLowerCase(Locale.ENGLISH);
        if (value.contains("animation"))
            return AssetType.ANIMATION;
        if (value.contains("graphic") || value.contains("gfx") || value.contains("projectile"))
            return AssetType.GRAPHIC;
        return null;
    }

    private static String compactLabel(String text) {
        if (text == null || text.trim().length() == 0)
            return "BossLabs asset field";
        String value = text.trim();
        return value.length() <= 88 ? value : value.substring(0, 85) + "...";
    }

    private static boolean isInteger(String value) {
        if (value == null || value.length() == 0)
            return false;
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start == value.length())
            return false;
        for (int index = start; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index)))
                return false;
        }
        return true;
    }

    private static void addRow(JPanel panel, int row, String labelText, Component component) {
        JLabel label = new JLabel(labelText);
        label.setFont(ConsoleTheme.BODY_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.WEST;
        left.insets = new Insets(4, 0, 4, 10);
        panel.add(label, left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1.0;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(4, 0, 4, 0);
        panel.add(component, right);
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        styleButton(button);
        return button;
    }

    private static void styleButton(JButton button) {
        ConsoleTheme.styleButton(button);
        button.setMargin(new Insets(5, 7, 5, 7));
    }

    private enum AssetType {
        ANIMATION("Animation"),
        GRAPHIC("GFX / Projectile");

        private final String displayName;

        private AssetType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final class AssetReference {
        private final AssetType type;
        private final int id;
        private final String label;

        private AssetReference(AssetType type, int id, String label) {
            this.type = type;
            this.id = id;
            this.label = label == null ? "BossLabs asset" : label;
        }

        private String searchText() {
            return (type.displayName + " " + id + " " + label).toLowerCase(Locale.ENGLISH);
        }

        @Override
        public String toString() {
            return type.displayName + "  [" + id + "]  ·  " + label;
        }
    }
}
