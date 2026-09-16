package game.console;

import game.ClientConsoleInterfaceBridge.InterfaceCatalogSnapshot;
import game.ClientConsoleInterfaceBridge.OpenInterfaceSnapshot;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Searchable cache-interface browser for the Client Console Interface Editor. */
public final class InterfaceBrowserDialog extends JDialog {

    private static final long serialVersionUID = -8151482860557323665L;

    public interface SelectionHandler {
        void interfaceSelected(int interfaceId);
    }

    private final InterfaceCatalogSnapshot catalog;
    private final SelectionHandler selectionHandler;
    private final javax.swing.JTextField searchField = new javax.swing.JTextField();
    private final DefaultListModel<BrowserEntry> model = new DefaultListModel<BrowserEntry>();
    private final JList<BrowserEntry> list = new JList<BrowserEntry>(model);
    private final JLabel status = new JLabel();

    private InterfaceBrowserDialog(Window owner, InterfaceCatalogSnapshot catalog,
            SelectionHandler selectionHandler) {
        super(owner instanceof Frame ? (Frame) owner : null, "Interface Browser", ModalityType.MODELESS);
        this.catalog = catalog == null ? InterfaceCatalogSnapshot.emptyForUi() : catalog;
        this.selectionHandler = selectionHandler;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(430, 520));
        setPreferredSize(new Dimension(520, 650));

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(ConsoleTheme.PANEL);
        root.setBorder(ConsoleTheme.panelPadding(18, 18, 18, 18));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.add(ConsoleTheme.titleLabel("INTERFACE BROWSER"));
        header.add(Box.createVerticalStrut(4));
        header.add(ConsoleTheme.subtitleLabel("Browse every interface ID in the current cache"));
        header.add(Box.createVerticalStrut(10));

        ConsoleTheme.styleTextField(searchField);
        searchField.setToolTipText("Search an interface ID, or use open / active / root.");
        header.add(searchField);
        root.add(header, BorderLayout.NORTH);

        list.setBackground(ConsoleTheme.INPUT);
        list.setForeground(ConsoleTheme.TEXT);
        list.setSelectionBackground(ConsoleTheme.ACCENT_DARK);
        list.setSelectionForeground(ConsoleTheme.TEXT);
        list.setFixedCellHeight(30);
        list.setCellRenderer(new BrowserRenderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    loadSelected();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        ConsoleTheme.styleScrollPane(scroll);
        scroll.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
        root.add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 8));
        footer.setOpaque(false);
        ConsoleTheme.styleStatus(status, false);
        footer.add(status, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        buttons.setOpaque(false);
        JButton cancel = new JButton("Close");
        ConsoleTheme.styleButton(cancel);
        cancel.addActionListener(e -> dispose());
        JButton load = new JButton("Load Selected");
        ConsoleTheme.styleButton(load);
        load.addActionListener(e -> loadSelected());
        buttons.add(cancel);
        buttons.add(load);
        footer.add(buttons, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuild();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuild();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuild();
            }
        });

        setContentPane(root);
        rebuild();
        pack();
        setLocationRelativeTo(owner);
    }

    public static void open(Component owner, InterfaceCatalogSnapshot catalog,
            SelectionHandler selectionHandler) {
        Window window = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        InterfaceBrowserDialog dialog = new InterfaceBrowserDialog(window, catalog, selectionHandler);
        dialog.setVisible(true);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                dialog.searchField.requestFocusInWindow();
            }
        });
    }

    private void rebuild() {
        String filter = searchField.getText() == null
                ? "" : searchField.getText().trim().toLowerCase(Locale.ENGLISH);
        int preserve = list.getSelectedValue() == null ? -1 : list.getSelectedValue().interfaceId;

        model.clear();
        int total = catalog.getTotalInterfaceCount();
        for (int interfaceId = 0; interfaceId < total; interfaceId++) {
            OpenInterfaceSnapshot open = catalog.findOpen(interfaceId);
            BrowserEntry entry = new BrowserEntry(interfaceId, open);
            if (filter.length() == 0 || entry.searchText.contains(filter)) {
                model.addElement(entry);
            }
        }

        if (preserve >= 0) {
            for (int index = 0; index < model.size(); index++) {
                if (model.getElementAt(index).interfaceId == preserve) {
                    list.setSelectedIndex(index);
                    list.ensureIndexIsVisible(index);
                    break;
                }
            }
        } else if (catalog.getActiveInterfaceId() >= 0) {
            for (int index = 0; index < model.size(); index++) {
                if (model.getElementAt(index).interfaceId == catalog.getActiveInterfaceId()) {
                    list.setSelectedIndex(index);
                    list.ensureIndexIsVisible(index);
                    break;
                }
            }
        }

        status.setText(model.size() + " of " + total + " interfaces shown. Double-click to load.");
    }

    private void loadSelected() {
        BrowserEntry selected = list.getSelectedValue();
        if (selected == null) {
            status.setText("Select an interface first.");
            return;
        }
        if (selectionHandler != null) {
            selectionHandler.interfaceSelected(selected.interfaceId);
        }
        dispose();
    }

    private static final class BrowserEntry {
        private final int interfaceId;
        private final OpenInterfaceSnapshot open;
        private final String searchText;

        private BrowserEntry(int interfaceId, OpenInterfaceSnapshot open) {
            this.interfaceId = interfaceId;
            this.open = open;
            StringBuilder search = new StringBuilder(Integer.toString(interfaceId));
            if (open != null) {
                search.append(" open");
                if (open.isActive())
                    search.append(" active");
                if (open.isRoot())
                    search.append(" root");
            }
            searchText = search.toString().toLowerCase(Locale.ENGLISH);
        }
    }

    private static final class BrowserRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 139210254597713736L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            if (value instanceof BrowserEntry) {
                BrowserEntry entry = (BrowserEntry) value;
                StringBuilder text = new StringBuilder();
                if (entry.open != null && entry.open.isActive())
                    text.append("● ");
                text.append(entry.interfaceId);
                if (entry.open != null) {
                    text.append(entry.open.isRoot() ? "   ROOT" : "   OPEN");
                    if (entry.open.getComponentCount() > 0)
                        text.append("   ").append(entry.open.getComponentCount()).append(" comps");
                    if (!entry.open.isRoot() && entry.open.getParentHash() >= 0) {
                        text.append("   parent ")
                                .append(entry.open.getParentHash() >>> 16)
                                .append(':').append(entry.open.getParentHash() & 0xffff);
                    }
                }
                label.setText(text.toString());
            }
            label.setFont(ConsoleTheme.SMALL_FONT);
            label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            if (isSelected) {
                label.setBackground(ConsoleTheme.ACCENT_DARK);
                label.setForeground(ConsoleTheme.TEXT);
            } else {
                label.setBackground(index % 2 == 0 ? ConsoleTheme.INPUT : ConsoleTheme.CARD);
                label.setForeground(ConsoleTheme.TEXT);
            }
            return label;
        }
    }
}
