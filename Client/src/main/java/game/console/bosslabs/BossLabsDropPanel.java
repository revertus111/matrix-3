package game.console.bosslabs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import game.ClientConsoleItemBridge;
import game.console.ConsoleTheme;

/** Creator-facing editor for Matrix3 NPCDrops through BossLabs drop overrides. */
public final class BossLabsDropPanel extends JPanel implements BossLabsDropClientBridge.Listener {

    private static final long serialVersionUID = -7499180043616515863L;
    private static final int MAX_ITEM_RESULTS = 80;
    private static final int SEARCH_DEBOUNCE_MS = 350;

    private static final String[] RARITIES = {
            "Always — all entries",
            "Common — 90% bucket",
            "Uncommon — 70% bucket",
            "Rare — 0.6% bucket",
            "Very Rare — 0.36% bucket"
    };

    private final JLabel title = new JLabel("No NPC loaded");
    private final JLabel draftState = state("DRAFT: none");
    private final JLabel liveState = state("LIVE: -");
    private final JLabel savedState = state("SAVED: no");
    private final JLabel status = new JLabel("Select a boss/NPC first.");

    private final JCheckBox rareTable = new JCheckBox("Access Matrix3 rare drop table");
    private final DefaultListModel<EntryRow> entryModel = new DefaultListModel<EntryRow>();
    private final JList<EntryRow> entryList = new JList<EntryRow>(entryModel);
    private final JButton newEntry = new JButton("New Drop");
    private final JButton remove = new JButton("Remove Selected");

    private final JTextField itemSearch = new JTextField();
    private final JLabel itemSearchStatus = new JLabel("Item index waiting...");
    private final DefaultListModel<ItemChoice> itemResultModel = new DefaultListModel<ItemChoice>();
    private final JList<ItemChoice> itemResults = new JList<ItemChoice>(itemResultModel);
    private final JTextField itemId = new JTextField();
    private final JComboBox<String> rarity = new JComboBox<String>(RARITIES);
    private final JTextField minAmount = new JTextField("1");
    private final JTextField maxAmount = new JTextField("1");
    private final JButton addUpdate = new JButton("Add Drop");

    private final JButton reload = new JButton("Reload Current");
    private final JButton applyLive = new JButton("Apply Drops Live");
    private final JButton saveApply = new JButton("Save & Apply Drops");
    private final JButton undo = new JButton("Undo Drops");
    private final JButton applySaved = new JButton("Apply Saved Drops");
    private final JButton restoreMatrix = new JButton("Restore Matrix3");
    private final JButton deleteSaved = new JButton("Delete Saved Override");

    private final List<ItemChoice> itemIndex = Collections.synchronizedList(new ArrayList<ItemChoice>());
    private final AtomicBoolean indexing = new AtomicBoolean();
    private final Timer searchDebounce = new Timer(SEARCH_DEBOUNCE_MS, e -> refreshItemSearch());

    private volatile int indexedCount;
    private volatile int itemTotal;
    private int selectedNpcId = -1;
    private String selectedNpcName = "";
    private BossLabsDropDraftDefinition draft;
    private boolean dirty;
    private boolean suppressDraftEvents;
    private boolean savedAvailable;
    private boolean rollbackAvailable;
    private boolean loadingDrops;
    private int activeInspectRequestId = -1;
    private String liveSource = "Matrix3";

    public BossLabsDropPanel() {
        super(new BorderLayout(10, 10));
        setBackground(ConsoleTheme.PANEL);
        setBorder(ConsoleTheme.panelPadding(12, 12, 12, 12));
        searchDebounce.setRepeats(false);

        add(createHeader(), BorderLayout.NORTH);
        add(createWorkspace(), BorderLayout.CENTER);
        add(createPublishArea(), BorderLayout.SOUTH);

        installListeners();
        updateButtons();
        BossLabsDropClientBridge.setListener(this);
        startItemIndex();
    }

    void disposeBridge() {
        BossLabsDropClientBridge.clearListener(this);
    }

    void loadNpc(int npcId, String npcName) {
        selectedNpcId = npcId;
        selectedNpcName = npcName == null ? "" : npcName;
        draft = null;
        dirty = false;
        savedAvailable = false;
        rollbackAvailable = false;
        loadingDrops = true;
        liveSource = "Matrix3";
        title.setText(selectedNpcName + "  [" + npcId + "]");
        entryModel.clear();
        clearEntryEditor();
        updateStateLabels();
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        status.setText("Loading current Matrix3/BossLabs drops...");
        updateButtons();
        activeInspectRequestId = BossLabsDropClientBridge.requestInspect(npcId);
    }

    void clearSelection() {
        selectedNpcId = -1;
        selectedNpcName = "";
        draft = null;
        dirty = false;
        savedAvailable = false;
        rollbackAvailable = false;
        loadingDrops = false;
        activeInspectRequestId = -1;
        liveSource = "Matrix3";
        title.setText("No NPC loaded");
        entryModel.clear();
        clearEntryEditor();
        updateStateLabels();
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        status.setText("Select a boss/NPC first.");
        updateButtons();
    }

    private JPanel createHeader() {
        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.setBackground(ConsoleTheme.CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(10, 12, 10, 12)));

        title.setFont(ConsoleTheme.SECTION_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        panel.add(title, BorderLayout.WEST);

        JPanel states = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        states.setOpaque(false);
        states.add(draftState);
        states.add(liveState);
        states.add(savedState);
        panel.add(states, BorderLayout.EAST);

        rareTable.setOpaque(false);
        rareTable.setForeground(ConsoleTheme.TEXT);
        rareTable.setFont(ConsoleTheme.BODY_FONT);
        rareTable.setToolTipText("Preserves Matrix3's existing rare-drop-table roll. This does not change its rate.");
        panel.add(rareTable, BorderLayout.SOUTH);
        return panel;
    }

    private JSplitPane createWorkspace() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createEntryList(), createEntryEditor());
        split.setResizeWeight(0.48);
        split.setDividerLocation(430);
        split.setBorder(null);
        split.setBackground(ConsoleTheme.PANEL);
        return split;
    }

    private JPanel createEntryList() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(ConsoleTheme.PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));

        JLabel label = new JLabel("Drop table");
        label.setFont(ConsoleTheme.SECTION_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        panel.add(label, BorderLayout.NORTH);

        entryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        entryList.setBackground(ConsoleTheme.INPUT);
        entryList.setForeground(ConsoleTheme.TEXT);
        entryList.setSelectionBackground(ConsoleTheme.CARD_HOVER);
        entryList.setFont(ConsoleTheme.BODY_FONT);
        entryList.setFixedCellHeight(30);
        JScrollPane scroll = new JScrollPane(entryList);
        ConsoleTheme.styleScrollPane(scroll);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
        actions.setOpaque(false);
        styleButton(newEntry);
        styleButton(remove);
        newEntry.setToolTipText("Clear the editor and start a new drop row.");
        newEntry.addActionListener(e -> beginNewEntry());
        remove.addActionListener(e -> removeSelected());
        actions.add(newEntry);
        actions.add(remove);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createEntryEditor() {
        JPanel outer = new JPanel(new BorderLayout(0, 8));
        outer.setBackground(ConsoleTheme.PANEL);
        outer.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);

        JLabel searchTitle = new JLabel("Find item");
        searchTitle.setFont(ConsoleTheme.SECTION_FONT);
        searchTitle.setForeground(ConsoleTheme.TEXT);
        searchTitle.setAlignmentX(LEFT_ALIGNMENT);
        content.add(searchTitle);
        content.add(Box.createVerticalStrut(5));

        itemSearch.setToolTipText("Search by item id or name using the existing Client Console item-definition bridge.");
        itemSearch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        itemSearch.setAlignmentX(LEFT_ALIGNMENT);
        ConsoleTheme.styleTextField(itemSearch);
        content.add(itemSearch);
        content.add(Box.createVerticalStrut(3));

        itemSearchStatus.setFont(ConsoleTheme.SMALL_FONT);
        itemSearchStatus.setForeground(ConsoleTheme.MUTED_TEXT);
        itemSearchStatus.setAlignmentX(LEFT_ALIGNMENT);
        content.add(itemSearchStatus);
        content.add(Box.createVerticalStrut(5));

        itemResults.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        itemResults.setBackground(ConsoleTheme.INPUT);
        itemResults.setForeground(ConsoleTheme.TEXT);
        itemResults.setSelectionBackground(ConsoleTheme.CARD_HOVER);
        itemResults.setFont(ConsoleTheme.BODY_FONT);
        JScrollPane resultScroll = new JScrollPane(itemResults);
        resultScroll.setPreferredSize(new Dimension(300, 120));
        resultScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        resultScroll.setAlignmentX(LEFT_ALIGNMENT);
        ConsoleTheme.styleScrollPane(resultScroll);
        content.add(resultScroll);
        content.add(Box.createVerticalStrut(10));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setAlignmentX(LEFT_ALIGNMENT);
        configureField(itemId, "Item ID");
        configureField(minAmount, "Minimum quantity. Matrix3 legacy tables may contain zero.");
        configureField(maxAmount, "Maximum quantity. Must be at least the minimum when publishing.");
        rarity.setFont(ConsoleTheme.BODY_FONT);
        rarity.setBackground(ConsoleTheme.INPUT);
        rarity.setForeground(ConsoleTheme.TEXT);
        addFormRow(form, 0, "Item ID", itemId);
        addFormRow(form, 1, "Rarity", rarity);
        addFormRow(form, 2, "Min amount", minAmount);
        addFormRow(form, 3, "Max amount", maxAmount);
        content.add(form);
        content.add(Box.createVerticalStrut(10));

        JLabel semantics = new JLabel("<html>Matrix3 rolls rarity buckets, not per-item percentages. "
                + "Always drops every entry. Common/Uncommon pick from their successful bucket. "
                + "Repeated entries are preserved because Matrix3 array slots affect selection weight; repeated Always entries drop repeatedly. "
                + "Rare/Very Rare wearable items are automatically split into Matrix3's existing gear roll. "
                + "Ring of Wealth and the rare drop table remain Matrix3-owned.</html>");
        semantics.setFont(ConsoleTheme.SMALL_FONT);
        semantics.setForeground(ConsoleTheme.MUTED_TEXT);
        semantics.setAlignmentX(LEFT_ALIGNMENT);
        content.add(semantics);
        content.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        ConsoleTheme.styleScrollPane(scroll);
        outer.add(scroll, BorderLayout.CENTER);

        styleButton(addUpdate);
        addUpdate.setToolTipText("Add this row to the local drop DRAFT, or update the selected row.");
        addUpdate.setPreferredSize(new Dimension(220, 38));
        addUpdate.addActionListener(e -> addOrUpdateEntry());
        outer.add(addUpdate, BorderLayout.SOUTH);
        return outer;
    }

    private JPanel createPublishArea() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ConsoleTheme.PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));

        status.setFont(ConsoleTheme.SMALL_FONT);
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        status.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(status);
        panel.add(Box.createVerticalStrut(6));

        JPanel row1 = new JPanel(new java.awt.GridLayout(1, 4, 5, 0));
        row1.setOpaque(false);
        styleButton(reload);
        styleButton(applySaved);
        styleButton(undo);
        styleButton(restoreMatrix);
        reload.addActionListener(e -> reloadCurrent());
        applySaved.addActionListener(e -> applySaved());
        undo.addActionListener(e -> undo());
        restoreMatrix.addActionListener(e -> restoreMatrix3());
        row1.add(reload);
        row1.add(applySaved);
        row1.add(undo);
        row1.add(restoreMatrix);
        panel.add(row1);
        panel.add(Box.createVerticalStrut(5));

        JPanel row2 = new JPanel(new java.awt.GridLayout(1, 3, 5, 0));
        row2.setOpaque(false);
        styleButton(deleteSaved);
        styleButton(applyLive);
        styleButton(saveApply);
        deleteSaved.addActionListener(e -> deleteSaved());
        applyLive.addActionListener(e -> publish(false));
        saveApply.addActionListener(e -> publish(true));
        row2.add(deleteSaved);
        row2.add(applyLive);
        row2.add(saveApply);
        panel.add(row2);
        return panel;
    }

    private void installListeners() {
        rareTable.addActionListener(e -> {
            if (!suppressDraftEvents && draft != null && !loadingDrops) {
                draft.setAccessRareDropTable(rareTable.isSelected());
                markDirty();
            }
        });
        entryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting())
                loadSelectedEntry();
        });
        itemResults.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                ItemChoice choice = itemResults.getSelectedValue();
                if (choice != null) {
                    itemId.setText(Integer.toString(choice.id));
                    addUpdate.setText(entryList.getSelectedIndex() >= 0 ? "Update Drop" : "Add Drop");
                }
            }
        });
        itemSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { searchDebounce.restart(); }
            @Override public void removeUpdate(DocumentEvent e) { searchDebounce.restart(); }
            @Override public void changedUpdate(DocumentEvent e) { searchDebounce.restart(); }
        });
    }

    private void beginNewEntry() {
        if (draft == null || loadingDrops) {
            setError("Drops are not loaded yet. Wait for the current table or use Reload Current.");
            return;
        }
        entryList.clearSelection();
        clearEntryEditor();
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        status.setText("New drop row — choose an item, rarity and quantity, then press Add Drop.");
        itemSearch.requestFocusInWindow();
    }

    private void addOrUpdateEntry() {
        if (draft == null || selectedNpcId < 0 || loadingDrops) {
            setError("Drops are not loaded yet. Wait for the current table or use Reload Current.");
            return;
        }
        try {
            int id = Integer.parseInt(itemId.getText().trim());
            int min = Integer.parseInt(minAmount.getText().trim());
            int max = Integer.parseInt(maxAmount.getText().trim());
            int rarityIndex = rarity.getSelectedIndex();
            ClientConsoleItemBridge.ItemInfo info = ClientConsoleItemBridge.getItemInfo(id);
            if (info == null) {
                setError("Unknown/unloaded item id: " + id);
                return;
            }
            BossLabsDropDraftDefinition.Entry next =
                    new BossLabsDropDraftDefinition.Entry(rarityIndex, id, min, max);

            int selected = entryList.getSelectedIndex();
            if (selected >= 0 && selected < draft.getEntries().size()) {
                draft.getEntries().set(selected, next);
            } else {
                if (draft.getEntries().size() >= BossLabsDropDraftDefinition.MAX_ENTRIES) {
                    setError("Drop table is limited to " + BossLabsDropDraftDefinition.MAX_ENTRIES + " entries.");
                    return;
                }
                draft.getEntries().add(next);
                selected = draft.getEntries().size() - 1;
            }
            markDirty();
            refreshEntries();
            entryList.setSelectedIndex(selected);
            status.setForeground(ConsoleTheme.ACCENT);
            status.setText("Updated local drop DRAFT. Apply Live to test or Save & Apply to persist.");
        } catch (NumberFormatException e) {
            setError("Item ID and amounts must be whole numbers.");
        } catch (IllegalArgumentException e) {
            setError(e.getMessage());
        }
    }

    private void removeSelected() {
        if (draft == null || loadingDrops)
            return;
        int index = entryList.getSelectedIndex();
        if (index < 0 || index >= draft.getEntries().size())
            return;
        draft.getEntries().remove(index);
        markDirty();
        refreshEntries();
        clearEntryEditor();
        status.setForeground(ConsoleTheme.ACCENT);
        status.setText("Removed drop from local DRAFT.");
    }

    private void loadSelectedEntry() {
        int index = entryList.getSelectedIndex();
        if (draft == null || loadingDrops || index < 0 || index >= draft.getEntries().size()) {
            remove.setEnabled(false);
            addUpdate.setText("Add Drop");
            return;
        }
        BossLabsDropDraftDefinition.Entry entry = draft.getEntries().get(index);
        itemId.setText(Integer.toString(entry.getItemId()));
        rarity.setSelectedIndex(entry.getRarity());
        minAmount.setText(Integer.toString(entry.getMinAmount()));
        maxAmount.setText(Integer.toString(entry.getMaxAmount()));
        ClientConsoleItemBridge.ItemInfo info = ClientConsoleItemBridge.getItemInfo(entry.getItemId());
        itemSearch.setText(info == null ? "" : info.getName());
        remove.setEnabled(true);
        addUpdate.setText("Update Drop");
    }

    private void clearEntryEditor() {
        itemResults.clearSelection();
        itemId.setText("");
        rarity.setSelectedIndex(BossLabsDropDraftDefinition.COMMON);
        minAmount.setText("1");
        maxAmount.setText("1");
        remove.setEnabled(false);
        addUpdate.setText("Add Drop");
    }

    private void refreshEntries() {
        entryModel.clear();
        if (draft == null)
            return;
        for (BossLabsDropDraftDefinition.Entry entry : draft.getEntries()) {
            ClientConsoleItemBridge.ItemInfo info = ClientConsoleItemBridge.getItemInfo(entry.getItemId());
            String name = info == null ? "Item " + entry.getItemId() : info.getName();
            entryModel.addElement(new EntryRow(entry, name));
        }
        updateButtons();
    }

    private void publish(boolean save) {
        if (draft == null || selectedNpcId < 0 || loadingDrops) {
            setError("Drops are not ready to publish yet.");
            return;
        }
        draft.setNpcId(selectedNpcId);
        draft.setAccessRareDropTable(rareTable.isSelected());
        String validation = draft.validate();
        if (validation != null) {
            setError(validation);
            return;
        }
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        status.setText(save ? "Saving and applying drop override..." : "Applying drop override live...");
        BossLabsDropClientBridge.requestPublish(draft, save);
    }

    private void reloadCurrent() {
        if (selectedNpcId < 0)
            return;
        if (dirty) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Discard the local drop DRAFT and reload the current live table?",
                    "Reload Drops", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION)
                return;
        }
        loadingDrops = true;
        activeInspectRequestId = BossLabsDropClientBridge.requestInspect(selectedNpcId);
        updateStateLabels();
        updateButtons();
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        status.setText("Reloading current live drops...");
    }

    private void undo() {
        if (selectedNpcId >= 0 && rollbackAvailable && !loadingDrops) {
            status.setText("Restoring previous live drop table...");
            BossLabsDropClientBridge.requestUndo(selectedNpcId);
        }
    }

    private void applySaved() {
        if (selectedNpcId >= 0 && savedAvailable && !loadingDrops) {
            status.setText("Applying saved BossLabs drops live...");
            BossLabsDropClientBridge.requestApplySaved(selectedNpcId);
        }
    }

    private void restoreMatrix3() {
        if (selectedNpcId >= 0 && !loadingDrops) {
            status.setText("Restoring captured Matrix3 drop table...");
            BossLabsDropClientBridge.requestRestoreMatrix3(selectedNpcId);
        }
    }

    private void deleteSaved() {
        if (selectedNpcId < 0 || !savedAvailable || loadingDrops)
            return;
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete the saved BossLabs drop override and restore Matrix3 drops live?",
                "Delete Saved Drop Override", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION)
            return;
        status.setText("Deleting saved BossLabs drop override...");
        BossLabsDropClientBridge.requestDeleteSaved(selectedNpcId);
    }

    private void markDirty() {
        dirty = true;
        updateStateLabels();
        updateButtons();
    }

    private void updateStateLabels() {
        if (selectedNpcId < 0) {
            draftState.setText("DRAFT: none");
        } else if (loadingDrops) {
            draftState.setText("DRAFT: loading");
        } else {
            draftState.setText(draft == null ? "DRAFT: error" : dirty ? "DRAFT: modified" : "DRAFT: clean");
        }
        draftState.setBackground(dirty && !loadingDrops ? ConsoleTheme.ACCENT_DARK : ConsoleTheme.CARD_HOVER);
        liveState.setText("LIVE: " + (selectedNpcId < 0 ? "-" : liveSource));
        liveState.setBackground("BossLabs".equalsIgnoreCase(liveSource) ? ConsoleTheme.ACCENT_DARK : ConsoleTheme.CARD_HOVER);
        savedState.setText(savedAvailable ? "SAVED: yes" : "SAVED: no");
        savedState.setBackground(savedAvailable ? ConsoleTheme.ACCENT_DARK : ConsoleTheme.CARD_HOVER);
    }

    private void updateButtons() {
        boolean loaded = selectedNpcId >= 0 && draft != null && !loadingDrops;
        String validation = loaded ? draft.validate() : "No draft";
        applyLive.setEnabled(loaded && validation == null);
        saveApply.setEnabled(loaded && validation == null);
        reload.setEnabled(selectedNpcId >= 0 && !loadingDrops);
        undo.setEnabled(loaded && rollbackAvailable);
        applySaved.setEnabled(loaded && savedAvailable);
        restoreMatrix.setEnabled(loaded && "BossLabs".equalsIgnoreCase(liveSource));
        deleteSaved.setEnabled(loaded && savedAvailable);
        newEntry.setEnabled(loaded);
        remove.setEnabled(loaded && entryList.getSelectedIndex() >= 0);
        addUpdate.setEnabled(loaded);
        rareTable.setEnabled(loaded);
        itemSearch.setEnabled(loaded);
        itemResults.setEnabled(loaded);
        itemId.setEnabled(loaded);
        rarity.setEnabled(loaded);
        minAmount.setEnabled(loaded);
        maxAmount.setEnabled(loaded);
    }

    private void setError(String message) {
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        status.setText(message == null ? "Drop edit failed." : message);
    }

    private void startItemIndex() {
        if (!indexing.compareAndSet(false, true))
            return;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!ClientConsoleItemBridge.isItemDefinitionsReady()) {
                    try {
                        Thread.sleep(250L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                itemTotal = ClientConsoleItemBridge.getItemCount();
                for (int id = 0; id < itemTotal; id++) {
                    ClientConsoleItemBridge.ItemInfo info = ClientConsoleItemBridge.getItemInfo(id);
                    if (info != null)
                        itemIndex.add(new ItemChoice(info.getItemId(), info.getName()));
                    indexedCount = id + 1;
                    if ((id & 511) == 0)
                        SwingUtilities.invokeLater(() -> refreshItemSearchStatus());
                }
                SwingUtilities.invokeLater(() -> {
                    refreshItemSearchStatus();
                    refreshItemSearch();
                });
            }
        }, "Matrix3-BossLabsDropItemIndex");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshItemSearchStatus() {
        if (itemTotal <= 0) {
            itemSearchStatus.setText("Waiting for item definitions...");
        } else if (indexedCount < itemTotal) {
            itemSearchStatus.setText("Indexing items " + indexedCount + " / " + itemTotal + "...");
        } else {
            itemSearchStatus.setText("Item search ready — " + itemIndex.size() + " usable definitions.");
        }
    }

    private void refreshItemSearch() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> refreshItemSearch());
            return;
        }
        String value = itemSearch.getText();
        String query = value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
        itemResultModel.clear();
        if (query.length() == 0)
            return;

        if (isNumeric(query)) {
            try {
                ClientConsoleItemBridge.ItemInfo info = ClientConsoleItemBridge.getItemInfo(Integer.parseInt(query));
                if (info != null)
                    itemResultModel.addElement(new ItemChoice(info.getItemId(), info.getName()));
            } catch (NumberFormatException e) {
                // Overflowing numeric searches simply return nothing.
            }
            return;
        }

        List<ItemChoice> snapshot;
        synchronized (itemIndex) {
            snapshot = new ArrayList<ItemChoice>(itemIndex);
        }
        List<ItemChoice> matches = new ArrayList<ItemChoice>();
        for (ItemChoice choice : snapshot) {
            if (choice.name.toLowerCase(Locale.ENGLISH).contains(query)) {
                matches.add(choice);
                if (matches.size() >= MAX_ITEM_RESULTS)
                    break;
            }
        }
        Collections.sort(matches, new Comparator<ItemChoice>() {
            @Override
            public int compare(ItemChoice left, ItemChoice right) {
                return Integer.compare(left.id, right.id);
            }
        });
        for (ItemChoice choice : matches)
            itemResultModel.addElement(choice);
    }

    private boolean isNumeric(String value) {
        if (value.length() == 0)
            return false;
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index)))
                return false;
        }
        return true;
    }

    private void configureField(JTextField field, String tooltip) {
        field.setToolTipText(tooltip);
        field.setPreferredSize(new Dimension(180, 34));
        ConsoleTheme.styleTextField(field);
    }

    private void addFormRow(JPanel panel, int row, String labelText, java.awt.Component component) {
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

    private void styleButton(JButton button) {
        ConsoleTheme.styleButton(button);
        button.setMargin(new Insets(5, 6, 5, 6));
    }

    private static JLabel state(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        label.setBackground(ConsoleTheme.CARD_HOVER);
        label.setOpaque(true);
        label.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
        return label;
    }

    @Override
    public void onDropState(BossLabsDropClientBridge.DropState state) {
        if (state == null || state.getNpcId() != selectedNpcId)
            return;
        if (loadingDrops && activeInspectRequestId >= 0 && state.getRequestId() != activeInspectRequestId)
            return;
        suppressDraftEvents = true;
        try {
            draft = state.getDraft();
            if (draft == null)
                draft = new BossLabsDropDraftDefinition(selectedNpcId);
            liveSource = state.getSource();
            savedAvailable = state.isSaved();
            rollbackAvailable = state.isRollbackAvailable();
            dirty = false;
            loadingDrops = false;
            activeInspectRequestId = -1;
            rareTable.setSelected(draft.canAccessRareDropTable());
            refreshEntries();
            clearEntryEditor();
            updateStateLabels();
            updateButtons();
        } finally {
            suppressDraftEvents = false;
        }
        String validation = draft.validate();
        if (validation == null) {
            status.setForeground(ConsoleTheme.ACCENT);
            status.setText("Loaded current " + liveSource + " drop table — " + draft.getEntries().size() + " entries.");
        } else {
            status.setForeground(ConsoleTheme.MUTED_TEXT);
            status.setText("Loaded legacy Matrix3 drops, but one row needs correction before publishing: " + validation);
        }
    }

    @Override
    public void onDropActionResult(BossLabsDropClientBridge.DropActionResult result) {
        if (result == null)
            return;
        if (result.getNpcId() >= 0 && result.getNpcId() != selectedNpcId)
            return;
        if (!result.isSuccess() && result.getRequestId() == activeInspectRequestId) {
            loadingDrops = false;
            activeInspectRequestId = -1;
            updateStateLabels();
            updateButtons();
        }
        status.setForeground(result.isSuccess() ? ConsoleTheme.ACCENT : ConsoleTheme.MUTED_TEXT);
        status.setText(result.getMessage());
    }

    private static final class EntryRow {
        private final BossLabsDropDraftDefinition.Entry entry;
        private final String name;

        private EntryRow(BossLabsDropDraftDefinition.Entry entry, String name) {
            this.entry = entry;
            this.name = name;
        }

        @Override
        public String toString() {
            String amount = entry.getMinAmount() == entry.getMaxAmount()
                    ? Integer.toString(entry.getMinAmount())
                    : entry.getMinAmount() + "-" + entry.getMaxAmount();
            return name + "  [" + entry.getItemId() + "]  ·  "
                    + BossLabsDropDraftDefinition.rarityName(entry.getRarity()) + "  ·  ×" + amount;
        }
    }

    private static final class ItemChoice {
        private final int id;
        private final String name;

        private ItemChoice(int id, String name) {
            this.id = id;
            this.name = name == null ? "Item " + id : name;
        }

        @Override
        public String toString() {
            return name + "  [" + id + "]";
        }
    }
}
