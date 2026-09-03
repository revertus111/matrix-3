package game.console;

import game.ClientConsoleItemBridge;
import game.DevDefinitionBridge;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Lazy definition search used by the Dev Mode tile Spawn Browser.
 *
 * NPC/object names come from Matrix3's normal client definition loaders. Item
 * names and thumbnails reuse the existing Client Console Item Browser bridge.
 */
public final class DevSpawnSearchPanel extends JPanel {

    private static final long serialVersionUID = -6581941256384629920L;

    public static final int NPC = 0;
    public static final int OBJECT = 1;
    public static final int ITEM = 2;

    private static final int MAX_VISIBLE_RESULTS = 120;
    private static final int SEARCH_DEBOUNCE_MS = 250;

    private final int kind;
    private final SelectionListener listener;
    private final List<Entry> entries = Collections.synchronizedList(new ArrayList<Entry>());
    private final Map<Integer, Entry> byId = Collections.synchronizedMap(new HashMap<Integer, Entry>());
    private final Map<Integer, ImageIcon> thumbnails = Collections.synchronizedMap(new HashMap<Integer, ImageIcon>());
    private final Set<Integer> thumbnailPending = Collections.synchronizedSet(new HashSet<Integer>());
    private final Set<Integer> thumbnailFailed = Collections.synchronizedSet(new HashSet<Integer>());
    private final AtomicBoolean indexing = new AtomicBoolean();

    private final JTextField search = new JTextField();
    private final JLabel status = new JLabel("Type a name or ID to search.");
    private final JLabel previewIcon = new JLabel("·", SwingConstants.CENTER);
    private final JLabel previewName = new JLabel("Nothing selected");
    private final JLabel previewMeta = new JLabel(" ");
    private final DefaultListModel<Entry> model = new DefaultListModel<Entry>();
    private final JList<Entry> list = new JList<Entry>(model);
    private final Timer searchDebounce = new Timer(SEARCH_DEBOUNCE_MS, e -> applySearch());

    private volatile int scanned;
    private volatile int total;
    private String appliedQuery = "";

    public DevSpawnSearchPanel(int kind, SelectionListener listener) {
        super(new BorderLayout(10, 8));
        this.kind = kind;
        this.listener = listener;
        setBackground(ConsoleTheme.CARD);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        setPreferredSize(new Dimension(620, 235));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 235));

        searchDebounce.setRepeats(false);
        buildUi();
        installListeners();
    }

    private void buildUi() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(ConsoleTheme.CARD);

        search.setToolTipText("Search by " + kindLabel().toLowerCase(Locale.ENGLISH) + " name or ID");
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        search.setAlignmentX(Component.LEFT_ALIGNMENT);
        ConsoleTheme.styleTextField(search);

        status.setFont(ConsoleTheme.SMALL_FONT);
        status.setForeground(ConsoleTheme.ACCENT);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);

        top.add(search);
        top.add(Box.createVerticalStrut(5));
        top.add(status);
        add(top, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(ConsoleTheme.PANEL);
        list.setForeground(ConsoleTheme.TEXT);
        list.setSelectionBackground(ConsoleTheme.CARD_HOVER);
        list.setFixedCellHeight(48);
        list.setCellRenderer(new EntryRenderer());

        JScrollPane results = new JScrollPane(list);
        results.setPreferredSize(new Dimension(420, 160));
        results.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        ConsoleTheme.styleScrollPane(results);
        results.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
        add(results, BorderLayout.CENTER);

        JPanel preview = new JPanel();
        preview.setLayout(new BoxLayout(preview, BoxLayout.Y_AXIS));
        preview.setBackground(ConsoleTheme.PANEL);
        preview.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        preview.setPreferredSize(new Dimension(165, 160));

        previewIcon.setPreferredSize(new Dimension(140, 72));
        previewIcon.setMaximumSize(new Dimension(140, 72));
        previewIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
        previewIcon.setForeground(ConsoleTheme.MUTED_TEXT);
        previewName.setFont(ConsoleTheme.SECTION_FONT);
        previewName.setForeground(ConsoleTheme.TEXT);
        previewName.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewMeta.setFont(ConsoleTheme.SMALL_FONT);
        previewMeta.setForeground(ConsoleTheme.MUTED_TEXT);
        previewMeta.setAlignmentX(Component.LEFT_ALIGNMENT);

        preview.add(previewIcon);
        preview.add(Box.createVerticalStrut(7));
        preview.add(previewName);
        preview.add(Box.createVerticalStrut(3));
        preview.add(previewMeta);
        add(preview, BorderLayout.EAST);
    }

    private void installListeners() {
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { searchDebounce.restart(); }
            @Override public void removeUpdate(DocumentEvent e) { searchDebounce.restart(); }
            @Override public void changedUpdate(DocumentEvent e) { searchDebounce.restart(); }
        });

        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectionChanged();
            }
        });

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() < 2) {
                    return;
                }
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0) {
                    list.setSelectedIndex(index);
                    selectionChanged();
                }
            }
        });
    }

    private void applySearch() {
        String value = search.getText();
        appliedQuery = value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
        refreshResults();
    }

    private void refreshResults() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    refreshResults();
                }
            });
            return;
        }

        String query = appliedQuery;
        if (query.length() == 0) {
            model.clear();
            status.setText("Type a name or ID to search.");
            clearPreview();
            return;
        }

        if (isNumeric(query)) {
            Entry entry = null;
            try {
                entry = getOrLoad(Integer.parseInt(query));
            } catch (NumberFormatException ex) {
                // Overflowing numeric searches simply produce no result.
            }
            model.clear();
            if (entry != null) {
                model.addElement(entry);
                status.setText("Exact ID match.");
            } else if (!definitionsReady()) {
                status.setText("Waiting for " + kindLabel() + " definitions...");
            } else {
                status.setText("No usable " + kindLabel().toLowerCase(Locale.ENGLISH) + " found for ID " + query + ".");
            }
            clearPreview();
            return;
        }

        startIndexing();
        List<Entry> snapshot;
        synchronized (entries) {
            snapshot = new ArrayList<Entry>(entries);
        }
        List<Entry> matches = new ArrayList<Entry>();
        int matchCount = 0;
        for (Entry entry : snapshot) {
            if (entry.name.toLowerCase(Locale.ENGLISH).contains(query)) {
                matchCount++;
                if (matches.size() < MAX_VISIBLE_RESULTS) {
                    matches.add(entry);
                }
            }
        }
        Collections.sort(matches, new Comparator<Entry>() {
            @Override
            public int compare(Entry left, Entry right) {
                return Integer.compare(left.id, right.id);
            }
        });

        Entry selected = list.getSelectedValue();
        int selectedId = selected == null ? -1 : selected.id;
        model.clear();
        int restore = -1;
        for (int i = 0; i < matches.size(); i++) {
            Entry entry = matches.get(i);
            model.addElement(entry);
            if (entry.id == selectedId) {
                restore = i;
            }
        }
        if (restore >= 0) {
            list.setSelectedIndex(restore);
        }

        String shown = matchCount > MAX_VISIBLE_RESULTS
                ? "showing first " + MAX_VISIBLE_RESULTS + " of " + matchCount
                : matchCount + " shown";
        if (!definitionsReady()) {
            status.setText("Waiting for " + kindLabel() + " definitions...");
        } else if (total > 0 && scanned < total) {
            status.setText("Indexing " + scanned + " / " + total + " · " + shown);
        } else {
            status.setText("Indexed " + entries.size() + " usable " + kindLabel().toLowerCase(Locale.ENGLISH) + "s · " + shown);
        }
    }

    private void startIndexing() {
        if (!indexing.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!definitionsReady()) {
                    try {
                        Thread.sleep(250L);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                total = definitionCount();
                for (int id = 0; id < total; id++) {
                    Entry entry = loadEntry(id);
                    if (entry != null) {
                        entries.add(entry);
                        byId.put(Integer.valueOf(id), entry);
                    }
                    scanned = id + 1;
                    if ((id & 255) == 0) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                refreshResults();
                            }
                        });
                        Thread.yield();
                    }
                }
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        refreshResults();
                    }
                });
            }
        }, "Matrix3-DevSpawn-" + kindLabel() + "Index");
        thread.setDaemon(true);
        thread.start();
    }

    private void selectionChanged() {
        Entry entry = list.getSelectedValue();
        if (entry == null) {
            clearPreview();
            return;
        }

        previewName.setText(entry.name);
        previewMeta.setText("ID " + entry.id);
        if (kind == ITEM) {
            ImageIcon icon = thumbnail(entry.id);
            if (icon == null) {
                previewIcon.setIcon(null);
                previewIcon.setText("loading preview...");
            } else {
                Image scaled = icon.getImage().getScaledInstance(90, 80, Image.SCALE_SMOOTH);
                previewIcon.setIcon(new ImageIcon(scaled));
                previewIcon.setText("");
            }
        } else {
            previewIcon.setIcon(null);
            previewIcon.setText(kind == NPC ? "NPC" : "OBJECT");
        }

        if (listener != null) {
            listener.selected(entry.id, entry.name);
        }
    }

    private void clearPreview() {
        previewIcon.setIcon(null);
        previewIcon.setText("·");
        previewName.setText("Nothing selected");
        previewMeta.setText(" ");
    }

    private Entry getOrLoad(int id) {
        Entry existing = byId.get(Integer.valueOf(id));
        if (existing != null) {
            return existing;
        }
        Entry loaded = loadEntry(id);
        if (loaded != null) {
            byId.put(Integer.valueOf(id), loaded);
        }
        return loaded;
    }

    private Entry loadEntry(int id) {
        if (kind == NPC) {
            DevDefinitionBridge.DefinitionInfo info = DevDefinitionBridge.getNpcInfo(id);
            return info == null ? null : new Entry(info.getId(), info.getName());
        }
        if (kind == OBJECT) {
            DevDefinitionBridge.DefinitionInfo info = DevDefinitionBridge.getObjectInfo(id);
            return info == null ? null : new Entry(info.getId(), info.getName());
        }
        ClientConsoleItemBridge.ItemInfo info = ClientConsoleItemBridge.getItemInfo(id);
        return info == null ? null : new Entry(info.getItemId(), info.getName());
    }

    private boolean definitionsReady() {
        if (kind == NPC) {
            return DevDefinitionBridge.isNpcDefinitionsReady();
        }
        if (kind == OBJECT) {
            return DevDefinitionBridge.isObjectDefinitionsReady();
        }
        return ClientConsoleItemBridge.isItemDefinitionsReady();
    }

    private int definitionCount() {
        if (kind == NPC) {
            return DevDefinitionBridge.getNpcCount();
        }
        if (kind == OBJECT) {
            return DevDefinitionBridge.getObjectCount();
        }
        return ClientConsoleItemBridge.getItemCount();
    }

    private String kindLabel() {
        if (kind == NPC) {
            return "NPC";
        }
        if (kind == OBJECT) {
            return "Object";
        }
        return "Item";
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

    private ImageIcon thumbnail(final int itemId) {
        if (kind != ITEM) {
            return null;
        }
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
                        Entry selected = list.getSelectedValue();
                        if (selected != null && selected.id == completedId) {
                            selectionChanged();
                        }
                    }
                });
        if (!queued) {
            thumbnailPending.remove(Integer.valueOf(itemId));
        }
        return null;
    }

    private final class EntryRenderer extends JPanel implements ListCellRenderer<Entry> {
        private static final long serialVersionUID = 6615775223179317814L;
        private final JLabel icon = new JLabel("·", SwingConstants.CENTER);
        private final JLabel name = new JLabel();
        private final JLabel meta = new JLabel();

        private EntryRenderer() {
            super(new BorderLayout(8, 0));
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 9));
            icon.setPreferredSize(new Dimension(42, 34));
            icon.setForeground(ConsoleTheme.MUTED_TEXT);

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

            add(icon, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Entry> source, Entry value,
                int index, boolean isSelected, boolean cellHasFocus) {
            setBackground(isSelected ? ConsoleTheme.CARD_HOVER : ConsoleTheme.PANEL);
            name.setText(value.name);
            meta.setText("ID " + value.id);
            if (kind == ITEM) {
                ImageIcon image = thumbnail(value.id);
                icon.setIcon(image);
                icon.setText(image == null ? "·" : "");
            } else {
                icon.setIcon(null);
                icon.setText(kind == NPC ? "N" : "O");
            }
            return this;
        }
    }

    private static final class Entry {
        private final int id;
        private final String name;

        private Entry(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public interface SelectionListener {
        void selected(int id, String name);
    }
}
