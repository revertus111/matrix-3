package game.console;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import game.ClientConsoleBridge;
import game.ClientConsoleItemBridge;

public final class ItemBrowserPanel extends JPanel {

    private static final long serialVersionUID = 6358356620719959796L;
    private static final int MAX_VISIBLE_RESULTS = 160;
    private static final int SEARCH_DEBOUNCE_MS = 1250;
    private static final String ALL = "all";
    private static final String FAVORITES = "favorites";
    private static final String CATEGORIES = "categories";
    private static final String PRESETS = "presets";

    private final ItemBrowserStore store = new ItemBrowserStore();
    private final List<ItemEntry> items = Collections.synchronizedList(new ArrayList<ItemEntry>());
    private final Map<Integer, ItemEntry> byId = Collections.synchronizedMap(new HashMap<Integer, ItemEntry>());
    private final Map<Integer, ImageIcon> thumbnails = Collections.synchronizedMap(new HashMap<Integer, ImageIcon>());
    private final Set<Integer> thumbnailPending = Collections.synchronizedSet(new HashSet<Integer>());
    private final Set<Integer> thumbnailFailed = Collections.synchronizedSet(new HashSet<Integer>());
    private final AtomicBoolean indexing = new AtomicBoolean();

    private final JTextField search = new JTextField();
    private final Timer searchDebounce = new Timer(SEARCH_DEBOUNCE_MS, e -> applySearchQuery());
    private final JLabel status = new JLabel("Waiting for item definitions...");
    private final JLabel selectedName = new JLabel("Select an item");
    private final JLabel selectedIcon = new JLabel("·", SwingConstants.CENTER);
    private final DefaultListModel<ItemEntry> model = new DefaultListModel<ItemEntry>();
    private final JList<ItemEntry> list = new JList<ItemEntry>(model);
    private final JToggleButton all = new JToggleButton("All");
    private final JToggleButton favorites = new JToggleButton("★");
    private final JToggleButton categories = new JToggleButton("Categories");
    private final JToggleButton presets = new JToggleButton("Presets");
    private final JButton scope = new JButton("Choose category");
    private final JButton favorite = new JButton("☆");
    private final JButton category = new JButton("Category");
    private final JButton preset = new JButton("Preset");
    private final JButton presetInventory = new JButton("Preset → Inventory");
    private final JButton presetBank = new JButton("Preset → Bank");

    private volatile int scanned;
    private volatile int total;
    private String mode = ALL;
    private String selectedCategory;
    private String selectedPreset;
    private String appliedSearchQuery = "";

    public ItemBrowserPanel() {
        super(new BorderLayout());
        setBackground(ConsoleTheme.PANEL);
        setOpaque(true);
        searchDebounce.setRepeats(false);

        List<String> savedCategories = store.getCategories();
        if (!savedCategories.isEmpty()) {
            selectedCategory = savedCategories.get(0);
        }
        List<String> savedPresets = store.getPresets();
        if (!savedPresets.isEmpty()) {
            selectedPreset = savedPresets.get(0);
        }

        add(createHeader(), BorderLayout.NORTH);
        add(createResults(), BorderLayout.CENTER);
        add(createActions(), BorderLayout.SOUTH);
        installListeners();
        setMode(ALL);
        startIndexing();
    }

    private JPanel createHeader() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ConsoleTheme.PANEL);
        panel.setBorder(ConsoleTheme.panelPadding(18, 16, 10, 16));

        JLabel title = new JLabel("ITEM BROWSER");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);
        status.setFont(ConsoleTheme.SMALL_FONT);
        status.setForeground(ConsoleTheme.ACCENT);
        status.setAlignmentX(LEFT_ALIGNMENT);
        search.setToolTipText("Search by item id or name");
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        search.setAlignmentX(LEFT_ALIGNMENT);
        ConsoleTheme.styleTextField(search);

        JPanel modes = new JPanel(new GridLayout(1, 4, 5, 0));
        modes.setBackground(ConsoleTheme.PANEL);
        configureMode(all, ALL);
        configureMode(favorites, FAVORITES);
        configureMode(categories, CATEGORIES);
        configureMode(presets, PRESETS);
        ButtonGroup group = new ButtonGroup();
        group.add(all);
        group.add(favorites);
        group.add(categories);
        group.add(presets);
        modes.add(all);
        modes.add(favorites);
        modes.add(categories);
        modes.add(presets);

        ConsoleTheme.styleButton(scope);
        scope.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        scope.setAlignmentX(LEFT_ALIGNMENT);
        scope.addActionListener(e -> showScopeMenu());

        panel.add(title);
        panel.add(Box.createVerticalStrut(3));
        panel.add(status);
        panel.add(Box.createVerticalStrut(12));
        panel.add(search);
        panel.add(Box.createVerticalStrut(8));
        panel.add(modes);
        panel.add(Box.createVerticalStrut(6));
        panel.add(scope);
        return panel;
    }

    private JScrollPane createResults() {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(ConsoleTheme.PANEL);
        list.setForeground(ConsoleTheme.TEXT);
        list.setSelectionBackground(ConsoleTheme.CARD_HOVER);
        list.setFixedCellHeight(54);
        list.setCellRenderer(new ItemRenderer());
        JScrollPane pane = new JScrollPane(list);
        pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        ConsoleTheme.styleScrollPane(pane);
        pane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, ConsoleTheme.BORDER));
        return pane;
    }

    private JPanel createActions() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ConsoleTheme.PANEL);
        panel.setBorder(ConsoleTheme.panelPadding(10, 16, 14, 16));

        JPanel selection = new JPanel(new BorderLayout(8, 0));
        selection.setBackground(ConsoleTheme.PANEL);
        selectedIcon.setPreferredSize(new Dimension(50, 44));
        selectedIcon.setForeground(ConsoleTheme.MUTED_TEXT);
        selectedName.setFont(ConsoleTheme.SECTION_FONT);
        selectedName.setForeground(ConsoleTheme.TEXT);
        selection.add(selectedIcon, BorderLayout.WEST);
        selection.add(selectedName, BorderLayout.CENTER);

        JPanel organize = new JPanel(new GridLayout(1, 3, 5, 0));
        organize.setBackground(ConsoleTheme.PANEL);
        styleAction(favorite);
        styleAction(category);
        styleAction(preset);
        favorite.addActionListener(e -> toggleFavorite());
        category.addActionListener(e -> showCategoryMenu(category, selectedEntry()));
        preset.addActionListener(e -> showPresetMenu(preset, selectedEntry()));
        organize.add(favorite);
        organize.add(category);
        organize.add(preset);

        JPanel presetRow = new JPanel(new GridLayout(1, 2, 5, 0));
        presetRow.setBackground(ConsoleTheme.PANEL);
        styleAction(presetInventory);
        styleAction(presetBank);
        presetInventory.addActionListener(e -> spawnPreset(false));
        presetBank.addActionListener(e -> spawnPreset(true));
        presetRow.add(presetInventory);
        presetRow.add(presetBank);

        panel.add(selection);
        panel.add(Box.createVerticalStrut(6));
        panel.add(organize);
        panel.add(Box.createVerticalStrut(6));
        panel.add(quantityRow("Inventory", false));
        panel.add(Box.createVerticalStrut(5));
        panel.add(quantityRow("Bank", true));
        panel.add(Box.createVerticalStrut(6));
        panel.add(presetRow);
        return panel;
    }

    private JPanel quantityRow(String labelText, final boolean bank) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ConsoleTheme.PANEL);
        JLabel label = new JLabel(labelText);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        label.setPreferredSize(new Dimension(58, 30));
        row.add(label, BorderLayout.WEST);

        JPanel buttons = new JPanel(new GridLayout(1, 4, 4, 0));
        buttons.setBackground(ConsoleTheme.PANEL);
        addQuantityButton(buttons, "1", 1, bank);
        addQuantityButton(buttons, "5", 5, bank);
        addQuantityButton(buttons, "10", 10, bank);
        JButton x = new JButton("X");
        styleAction(x);
        x.addActionListener(e -> {
            ItemEntry entry = selectedEntry();
            int amount = promptAmount(1);
            if (entry != null && amount > 0) {
                spawn(entry, amount, bank);
            }
        });
        buttons.add(x);
        row.add(buttons, BorderLayout.CENTER);
        return row;
    }

    private void addQuantityButton(JPanel panel, String text, int amount, boolean bank) {
        JButton button = new JButton(text);
        styleAction(button);
        button.addActionListener(e -> {
            ItemEntry entry = selectedEntry();
            if (entry != null) {
                spawn(entry, amount, bank);
            }
        });
        panel.add(button);
    }

    private void styleAction(JButton button) {
        ConsoleTheme.styleButton(button);
        button.setMargin(new java.awt.Insets(5, 5, 5, 5));
    }

    private void configureMode(JToggleButton button, String target) {
        ConsoleTheme.styleButton(button);
        button.addActionListener(e -> setMode(target));
    }

    private void installListeners() {
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { scheduleSearchRefresh(); }
            @Override public void removeUpdate(DocumentEvent e) { scheduleSearchRefresh(); }
            @Override public void changedUpdate(DocumentEvent e) { scheduleSearchRefresh(); }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelection();
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int index = list.locationToIndex(e.getPoint());
                if (index < 0 || list.getCellBounds(index, index) == null
                        || !list.getCellBounds(index, index).contains(e.getPoint())) {
                    return;
                }
                list.setSelectedIndex(index);
                if (e.getClickCount() >= 2) {
                    spawn(model.getElementAt(index), 1, false);
                }
            }

            @Override public void mousePressed(MouseEvent e) { popup(e); }
            @Override public void mouseReleased(MouseEvent e) { popup(e); }
            private void popup(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int index = list.locationToIndex(e.getPoint());
                if (index < 0) {
                    return;
                }
                list.setSelectedIndex(index);
                ItemEntry entry = selectedEntry();
                if (entry != null) {
                    itemPopup(entry).show(list, e.getX(), e.getY());
                }
            }
        });
    }

    private void scheduleSearchRefresh() {
        searchDebounce.restart();
    }

    private void applySearchQuery() {
        String value = search.getText();
        appliedSearchQuery = value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
        refreshResults();
    }

    private JPopupMenu itemPopup(ItemEntry entry) {
        JPopupMenu popup = popup();
        popup.add(spawnMenu("Spawn to Inventory", entry, false));
        popup.add(spawnMenu("Spawn to Bank", entry, true));
        popup.addSeparator();

        JMenuItem fav = menuItem(store.isFavorite(entry.id) ? "★ Remove Favorite" : "☆ Add Favorite");
        fav.addActionListener(e -> {
            store.toggleFavorite(entry.id);
            refreshResults();
            updateSelection();
        });
        popup.add(fav);
        popup.add(categoryMenu(entry));
        popup.add(presetMenu(entry));

        if (PRESETS.equals(mode) && selectedPreset != null) {
            Map<Integer, Integer> values = store.getPresetItems(selectedPreset);
            if (values.containsKey(Integer.valueOf(entry.id))) {
                popup.addSeparator();
                JMenuItem qty = menuItem("Set preset quantity...");
                qty.addActionListener(e -> {
                    int current = values.get(Integer.valueOf(entry.id)).intValue();
                    int amount = promptAmount(current);
                    if (amount > 0) {
                        store.addToPreset(selectedPreset, entry.id, amount);
                        refreshResults();
                    }
                });
                popup.add(qty);
                JMenuItem remove = menuItem("Remove from " + selectedPreset);
                remove.addActionListener(e -> {
                    store.removeFromPreset(selectedPreset, entry.id);
                    refreshResults();
                });
                popup.add(remove);
            }
        }
        return popup;
    }

    private JMenu spawnMenu(String text, ItemEntry entry, boolean bank) {
        JMenu menu = menu(text);
        addSpawn(menu, "1", entry, 1, bank);
        addSpawn(menu, "5", entry, 5, bank);
        addSpawn(menu, "10", entry, 10, bank);
        JMenuItem x = menuItem("X...");
        x.addActionListener(e -> {
            int amount = promptAmount(1);
            if (amount > 0) {
                spawn(entry, amount, bank);
            }
        });
        menu.add(x);
        return menu;
    }

    private void addSpawn(JMenu menu, String text, ItemEntry entry, int amount, boolean bank) {
        JMenuItem item = menuItem(text);
        item.addActionListener(e -> spawn(entry, amount, bank));
        menu.add(item);
    }

    private JMenu categoryMenu(ItemEntry entry) {
        JMenu menu = menu("Categories");
        for (String name : store.getCategories()) {
            final boolean included = store.categoryContains(name, entry.id);
            JMenuItem item = menuItem((included ? "✓ " : "") + name);
            item.addActionListener(e -> {
                store.setCategoryMembership(name, entry.id, !included);
                refreshResults();
            });
            menu.add(item);
        }
        if (menu.getItemCount() > 0) {
            menu.addSeparator();
        }
        JMenuItem create = menuItem("+ New Category...");
        create.addActionListener(e -> {
            String name = promptName("New item category");
            if (name != null && store.createCategory(name)) {
                selectedCategory = name;
                store.setCategoryMembership(name, entry.id, true);
                setMode(CATEGORIES);
            }
        });
        menu.add(create);
        return menu;
    }

    private JMenu presetMenu(ItemEntry entry) {
        JMenu menu = menu("Presets");
        for (String name : store.getPresets()) {
            final boolean included = store.getPresetItems(name).containsKey(Integer.valueOf(entry.id));
            JMenuItem item = menuItem((included ? "✓ " : "") + name);
            item.addActionListener(e -> {
                if (included) {
                    store.removeFromPreset(name, entry.id);
                } else {
                    store.addToPreset(name, entry.id, 1);
                }
                refreshResults();
            });
            menu.add(item);
        }
        if (menu.getItemCount() > 0) {
            menu.addSeparator();
        }
        JMenuItem create = menuItem("+ New Preset...");
        create.addActionListener(e -> {
            String name = promptName("New item preset");
            if (name != null && store.createPreset(name)) {
                selectedPreset = name;
                store.addToPreset(name, entry.id, 1);
                setMode(PRESETS);
            }
        });
        menu.add(create);
        return menu;
    }

    private void showCategoryMenu(Component invoker, ItemEntry entry) {
        if (entry == null) {
            return;
        }
        JMenu menu = categoryMenu(entry);
        JPopupMenu popup = menu.getPopupMenu();
        stylePopup(popup);
        popup.show(invoker, 0, invoker.getHeight());
    }

    private void showPresetMenu(Component invoker, ItemEntry entry) {
        if (entry == null) {
            return;
        }
        JMenu menu = presetMenu(entry);
        JPopupMenu popup = menu.getPopupMenu();
        stylePopup(popup);
        popup.show(invoker, 0, invoker.getHeight());
    }

    private void showScopeMenu() {
        JPopupMenu popup = popup();
        if (CATEGORIES.equals(mode)) {
            for (String name : store.getCategories()) {
                JMenuItem item = menuItem((name.equals(selectedCategory) ? "✓ " : "") + name);
                item.addActionListener(e -> {
                    selectedCategory = name;
                    updateScope();
                    refreshResults();
                });
                popup.add(item);
            }
            popup.addSeparator();
            JMenuItem create = menuItem("+ New Category...");
            create.addActionListener(e -> {
                String name = promptName("New item category");
                if (name != null && store.createCategory(name)) {
                    selectedCategory = name;
                    updateScope();
                    refreshResults();
                }
            });
            popup.add(create);
        } else if (PRESETS.equals(mode)) {
            for (String name : store.getPresets()) {
                JMenuItem item = menuItem((name.equals(selectedPreset) ? "✓ " : "") + name);
                item.addActionListener(e -> {
                    selectedPreset = name;
                    updateScope();
                    refreshResults();
                });
                popup.add(item);
            }
            popup.addSeparator();
            JMenuItem create = menuItem("+ New Preset...");
            create.addActionListener(e -> {
                String name = promptName("New item preset");
                if (name != null && store.createPreset(name)) {
                    selectedPreset = name;
                    updateScope();
                    refreshResults();
                }
            });
            popup.add(create);
        }
        popup.show(scope, 0, scope.getHeight());
    }

    private JPopupMenu popup() {
        JPopupMenu popup = new JPopupMenu();
        stylePopup(popup);
        return popup;
    }

    private void stylePopup(JPopupMenu popup) {
        popup.setBackground(ConsoleTheme.CARD);
        popup.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
    }

    private JMenu menu(String text) {
        JMenu menu = new JMenu(text);
        menu.setFont(ConsoleTheme.BODY_FONT);
        menu.setForeground(ConsoleTheme.TEXT);
        menu.setBackground(ConsoleTheme.CARD);
        return menu;
    }

    private JMenuItem menuItem(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setFont(ConsoleTheme.BODY_FONT);
        item.setForeground(ConsoleTheme.TEXT);
        item.setBackground(ConsoleTheme.CARD);
        item.setOpaque(true);
        return item;
    }

    private void setMode(String value) {
        mode = value;
        all.setSelected(ALL.equals(value));
        favorites.setSelected(FAVORITES.equals(value));
        categories.setSelected(CATEGORIES.equals(value));
        presets.setSelected(PRESETS.equals(value));
        scope.setVisible(CATEGORIES.equals(value) || PRESETS.equals(value));
        presetInventory.setVisible(PRESETS.equals(value));
        presetBank.setVisible(PRESETS.equals(value));
        updateScope();
        refreshResults();
    }

    private void updateScope() {
        if (CATEGORIES.equals(mode)) {
            scope.setText(selectedCategory == null ? "Choose / create category" : "Category: " + selectedCategory);
        } else if (PRESETS.equals(mode)) {
            scope.setText(selectedPreset == null ? "Choose / create preset" : "Preset: " + selectedPreset);
        }
    }

    private void refreshResults() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> refreshResults());
            return;
        }
        String query = appliedSearchQuery;
        int resultLimit = query.length() == 0 ? MAX_VISIBLE_RESULTS : Integer.MAX_VALUE;
        List<ItemEntry> matches = new ArrayList<ItemEntry>();
        int matchCount = 0;

        if (PRESETS.equals(mode)) {
            if (selectedPreset != null) {
                for (Map.Entry<Integer, Integer> presetItem : store.getPresetItems(selectedPreset).entrySet()) {
                    ItemEntry entry = getOrLoad(presetItem.getKey().intValue());
                    if (entry != null && matches(entry, query)) {
                        matchCount++;
                        if (matches.size() < resultLimit) {
                            matches.add(new ItemEntry(entry.id, entry.name, presetItem.getValue().intValue()));
                        }
                    }
                }
            }
        } else if (isNumeric(query)) {
            try {
                ItemEntry entry = getOrLoad(Integer.parseInt(query));
                if (entry != null && inScope(entry.id)) {
                    matches.add(entry);
                    matchCount = 1;
                }
            } catch (NumberFormatException ex) {
                // Overflowing numeric searches simply show no match.
            }
        } else {
            List<ItemEntry> snapshot;
            synchronized (items) {
                snapshot = new ArrayList<ItemEntry>(items);
            }
            for (ItemEntry entry : snapshot) {
                if (inScope(entry.id) && matches(entry, query)) {
                    matchCount++;
                    if (matches.size() < resultLimit) {
                        matches.add(entry);
                    }
                }
            }
            Collections.sort(matches, new Comparator<ItemEntry>() {
                @Override
                public int compare(ItemEntry left, ItemEntry right) {
                    return Integer.compare(left.id, right.id);
                }
            });
        }

        ItemEntry selected = selectedEntry();
        int selectedId = selected == null ? -1 : selected.id;
        model.clear();
        int restore = -1;
        for (int i = 0; i < matches.size(); i++) {
            model.addElement(matches.get(i));
            if (matches.get(i).id == selectedId) {
                restore = i;
            }
        }
        if (restore >= 0) {
            list.setSelectedIndex(restore);
        }

        String shown = matchCount > resultLimit
                ? " · showing first " + resultLimit + " of " + matchCount
                : " · " + matchCount + " shown";
        if (total > 0 && scanned < total) {
            status.setText("Indexing " + scanned + " / " + total + shown);
        } else if (total > 0) {
            status.setText("Indexed " + items.size() + " usable items" + shown);
        }
        updateSelection();
    }

    private boolean inScope(int itemId) {
        if (FAVORITES.equals(mode)) {
            return store.isFavorite(itemId);
        }
        if (CATEGORIES.equals(mode)) {
            return selectedCategory != null && store.categoryContains(selectedCategory, itemId);
        }
        return true;
    }

    private boolean matches(ItemEntry entry, String query) {
        return query.length() == 0
                || entry.name.toLowerCase(Locale.ENGLISH).contains(query)
                || Integer.toString(entry.id).equals(query);
    }

    private boolean isNumeric(String value) {
        if (value.length() == 0) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private ItemEntry getOrLoad(int itemId) {
        ItemEntry entry = byId.get(Integer.valueOf(itemId));
        if (entry != null) {
            return entry;
        }
        ClientConsoleItemBridge.ItemInfo info = ClientConsoleItemBridge.getItemInfo(itemId);
        if (info == null) {
            return null;
        }
        entry = new ItemEntry(info.getItemId(), info.getName(), 1);
        byId.put(Integer.valueOf(itemId), entry);
        return entry;
    }

    private void startIndexing() {
        if (!indexing.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!ClientConsoleItemBridge.isItemDefinitionsReady()) {
                    try {
                        Thread.sleep(250L);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                total = ClientConsoleItemBridge.getItemCount();
                for (int id = 0; id < total; id++) {
                    ClientConsoleItemBridge.ItemInfo info = ClientConsoleItemBridge.getItemInfo(id);
                    if (info != null) {
                        ItemEntry entry = new ItemEntry(info.getItemId(), info.getName(), 1);
                        items.add(entry);
                        byId.put(Integer.valueOf(id), entry);
                    }
                    scanned = id + 1;
                    if ((id & 255) == 0) {
                        SwingUtilities.invokeLater(() -> refreshResults());
                        Thread.yield();
                    }
                }
                SwingUtilities.invokeLater(() -> refreshResults());
            }
        }, "Matrix3-ItemBrowserIndex");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateSelection() {
        ItemEntry entry = selectedEntry();
        boolean enabled = entry != null;
        favorite.setEnabled(enabled);
        category.setEnabled(enabled);
        preset.setEnabled(enabled);
        if (!enabled) {
            selectedName.setText("Select an item");
            selectedIcon.setIcon(null);
            selectedIcon.setText("·");
            favorite.setText("☆");
            return;
        }
        selectedName.setText(entry.name + "  ·  " + entry.id + (entry.amount > 1 ? "  ×" + entry.amount : ""));
        favorite.setText(store.isFavorite(entry.id) ? "★" : "☆");
        ImageIcon icon = thumbnail(entry.id);
        if (icon == null) {
            selectedIcon.setIcon(null);
            selectedIcon.setText("·");
        } else {
            Image scaled = icon.getImage().getScaledInstance(45, 40, Image.SCALE_SMOOTH);
            selectedIcon.setIcon(new ImageIcon(scaled));
            selectedIcon.setText("");
        }
    }

    private void toggleFavorite() {
        ItemEntry entry = selectedEntry();
        if (entry != null) {
            store.toggleFavorite(entry.id);
            refreshResults();
        }
    }

    private ItemEntry selectedEntry() {
        return list.getSelectedValue();
    }

    private void spawn(ItemEntry entry, int amount, boolean bank) {
        if (entry == null || amount <= 0) {
            return;
        }
        String command = "itembrowser " + (bank ? "bank" : "inventory") + " " + entry.id + " " + amount;
        String error = ClientConsoleBridge.queueConsoleCommand(command);
        status.setText(error == null
                ? (bank ? "Bank" : "Inventory") + " spawn queued: " + entry.name + " ×" + amount
                : error);
    }

    private void spawnPreset(boolean bank) {
        if (selectedPreset == null) {
            status.setText("Choose or create a preset first.");
            return;
        }
        Map<Integer, Integer> values = new LinkedHashMap<Integer, Integer>(store.getPresetItems(selectedPreset));
        if (values.isEmpty()) {
            status.setText("Preset " + selectedPreset + " is empty.");
            return;
        }
        int queued = 0;
        for (Map.Entry<Integer, Integer> value : values.entrySet()) {
            String command = "itembrowser " + (bank ? "bank" : "inventory") + " "
                    + value.getKey() + " " + value.getValue();
            String error = ClientConsoleBridge.queueConsoleCommand(command);
            if (error != null) {
                status.setText("Queued " + queued + " preset items. " + error);
                return;
            }
            queued++;
        }
        status.setText("Queued " + queued + " items from " + selectedPreset + (bank ? " → Bank" : " → Inventory"));
    }

    private int promptAmount(int current) {
        String value = JOptionPane.showInputDialog(this, "Amount", Integer.toString(Math.max(1, current)));
        if (value == null) {
            return -1;
        }
        try {
            int amount = Integer.parseInt(value.trim());
            if (amount <= 0) {
                throw new NumberFormatException();
            }
            return amount;
        } catch (NumberFormatException ex) {
            status.setText("Amount must be a positive whole number.");
            return -1;
        }
    }

    private String promptName(String title) {
        String value = JOptionPane.showInputDialog(this, title);
        if (value == null) {
            return null;
        }
        value = value.trim();
        if (value.length() == 0) {
            status.setText("Name cannot be empty.");
            return null;
        }
        return value;
    }

    private ImageIcon thumbnail(final int itemId) {
        ImageIcon icon = thumbnails.get(Integer.valueOf(itemId));
        if (icon != null || thumbnailFailed.contains(Integer.valueOf(itemId))) {
            return icon;
        }
        if (!thumbnailPending.add(Integer.valueOf(itemId))) {
            return null;
        }
        boolean queued = ClientConsoleItemBridge.requestThumbnail(itemId, 1,
                new ClientConsoleItemBridge.ThumbnailCallback() {
                    @Override
                    public void thumbnailReady(int completedId, BufferedImage image) {
                        thumbnailPending.remove(Integer.valueOf(completedId));
                        if (image == null) {
                            thumbnailFailed.add(Integer.valueOf(completedId));
                        } else {
                            thumbnails.put(Integer.valueOf(completedId), new ImageIcon(image));
                        }
                        list.repaint();
                        ItemEntry selected = selectedEntry();
                        if (selected != null && selected.id == completedId) {
                            updateSelection();
                        }
                    }
                });
        if (!queued) {
            thumbnailPending.remove(Integer.valueOf(itemId));
        }
        return null;
    }

    private final class ItemRenderer extends JPanel implements ListCellRenderer<ItemEntry> {
        private static final long serialVersionUID = -7370360175080184532L;
        private final JLabel icon = new JLabel();
        private final JLabel name = new JLabel();
        private final JLabel meta = new JLabel();
        private final JLabel star = new JLabel("☆", SwingConstants.CENTER);

        private ItemRenderer() {
            super(new BorderLayout(8, 0));
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            icon.setPreferredSize(new Dimension(40, 36));
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            JPanel text = new JPanel();
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setOpaque(false);
            name.setFont(ConsoleTheme.BODY_FONT);
            name.setForeground(ConsoleTheme.TEXT);
            meta.setFont(ConsoleTheme.SMALL_FONT);
            meta.setForeground(ConsoleTheme.MUTED_TEXT);
            text.add(name);
            text.add(Box.createVerticalStrut(2));
            text.add(meta);
            star.setPreferredSize(new Dimension(22, 36));
            star.setForeground(ConsoleTheme.ACCENT);
            add(icon, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            add(star, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ItemEntry> source, ItemEntry value,
                int index, boolean isSelected, boolean cellHasFocus) {
            setBackground(isSelected ? ConsoleTheme.CARD_HOVER : ConsoleTheme.PANEL);
            name.setText(value.name);
            meta.setText("ID " + value.id + (value.amount > 1 ? "  ·  ×" + value.amount : ""));
            star.setText(store.isFavorite(value.id) ? "★" : "☆");
            ImageIcon image = thumbnail(value.id);
            icon.setIcon(image);
            icon.setText(image == null ? "·" : "");
            icon.setForeground(ConsoleTheme.MUTED_TEXT);
            return this;
        }
    }

    private static final class ItemEntry {
        private final int id;
        private final String name;
        private final int amount;

        private ItemEntry(int id, String name, int amount) {
            this.id = id;
            this.name = name;
            this.amount = Math.max(1, amount);
        }
    }
}
