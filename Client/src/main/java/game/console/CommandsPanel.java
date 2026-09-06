package game.console;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import game.ClientConsoleBridge;

public final class CommandsPanel extends JPanel {

    private static final long serialVersionUID = -2142724755054426808L;
    private static final long DANGER_CONFIRM_WINDOW_MS = 5000L;

    private final JTextField searchField = new JTextField();
    private final JComboBox<String> categoryBox = new JComboBox<String>(CommandCatalog.getCategories());
    private final JLabel countLabel = new JLabel();
    private final DefaultListModel<CommandCatalog.Entry> resultModel = new DefaultListModel<CommandCatalog.Entry>();
    private final JList<CommandCatalog.Entry> resultList = new JList<CommandCatalog.Entry>(resultModel);

    private final JLabel selectedLabel = new JLabel("Select a command");
    private final JTextArea usageText = new JTextArea("Choose a result or press Enter from search.");
    private final JTextField argumentsField = new JTextField();
    private final JLabel previewLabel = new JLabel("::");
    private final JLabel statusLabel = new JLabel("Commands execute through Matrix3's normal server command path.");
    private final JButton runButton = new JButton("RUN COMMAND");

    private CommandCatalog.Entry selectedEntry;
    private String pendingDangerCommand;
    private long pendingDangerUntil;

    public CommandsPanel() {
        super(new BorderLayout());
        setBackground(ConsoleTheme.PANEL);
        setOpaque(true);

        add(createHeader(), BorderLayout.NORTH);
        add(createCommandScroll(), BorderLayout.CENTER);
        add(createComposer(), BorderLayout.SOUTH);

        filterCommands();
    }

    private JPanel createHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ConsoleTheme.PANEL);
        header.setBorder(ConsoleTheme.panelPadding(20, 18, 10, 18));

        JLabel title = new JLabel("COMMANDS");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Search-first Matrix3 command palette");
        subtitle.setFont(ConsoleTheme.SMALL_FONT);
        subtitle.setForeground(ConsoleTheme.ACCENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        countLabel.setFont(ConsoleTheme.SMALL_FONT);
        countLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        countLabel.setAlignmentX(LEFT_ALIGNMENT);

        searchField.setToolTipText("Search command name, category, or usage");
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        searchField.setAlignmentX(LEFT_ALIGNMENT);
        ConsoleTheme.styleTextField(searchField);
        searchField.getDocument().addDocumentListener(new SimpleDocumentListener() {
            @Override
            public void changed() {
                filterCommands();
            }
        });
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    moveResultSelection(1);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    moveResultSelection(-1);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    selectHighlightedResult();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchField.setText("");
                    e.consume();
                }
            }
        });

        categoryBox.setFont(ConsoleTheme.BODY_FONT);
        categoryBox.setForeground(ConsoleTheme.TEXT);
        categoryBox.setBackground(ConsoleTheme.INPUT);
        categoryBox.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
        categoryBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        categoryBox.setAlignmentX(LEFT_ALIGNMENT);
        categoryBox.addActionListener(e -> filterCommands());

        JLabel keyboardHint = new JLabel("Up/Down navigate  ·  Enter select  ·  Esc clear");
        keyboardHint.setFont(ConsoleTheme.SMALL_FONT);
        keyboardHint.setForeground(ConsoleTheme.MUTED_TEXT);
        keyboardHint.setAlignmentX(LEFT_ALIGNMENT);

        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        header.add(Box.createVerticalStrut(5));
        header.add(countLabel);
        header.add(Box.createVerticalStrut(12));
        header.add(searchField);
        header.add(Box.createVerticalStrut(7));
        header.add(categoryBox);
        header.add(Box.createVerticalStrut(6));
        header.add(keyboardHint);
        return header;
    }

    private JScrollPane createCommandScroll() {
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setBackground(ConsoleTheme.PANEL);
        resultList.setForeground(ConsoleTheme.TEXT);
        resultList.setSelectionBackground(ConsoleTheme.CARD_HOVER);
        resultList.setFixedCellHeight(48);
        resultList.setCellRenderer(new CommandRenderer());
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int index = resultList.locationToIndex(e.getPoint());
                if (index < 0 || resultList.getCellBounds(index, index) == null
                        || !resultList.getCellBounds(index, index).contains(e.getPoint())) {
                    return;
                }
                resultList.setSelectedIndex(index);
                selectHighlightedResult();
            }
        });
        resultList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    selectHighlightedResult();
                    e.consume();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultList);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(scrollPane);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, ConsoleTheme.BORDER));
        return scrollPane;
    }

    private JPanel createComposer() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ConsoleTheme.PANEL);
        wrapper.setBorder(ConsoleTheme.panelPadding(10, 18, 18, 18));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ConsoleTheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(12, 12, 12, 12)));

        selectedLabel.setFont(ConsoleTheme.SECTION_FONT);
        selectedLabel.setForeground(ConsoleTheme.TEXT);
        selectedLabel.setAlignmentX(LEFT_ALIGNMENT);

        usageText.setEditable(false);
        usageText.setFocusable(false);
        usageText.setOpaque(false);
        usageText.setLineWrap(true);
        usageText.setWrapStyleWord(true);
        usageText.setRows(2);
        usageText.setFont(ConsoleTheme.SMALL_FONT);
        usageText.setForeground(ConsoleTheme.MUTED_TEXT);
        usageText.setAlignmentX(LEFT_ALIGNMENT);

        JLabel argumentsTitle = new JLabel("Arguments");
        argumentsTitle.setFont(ConsoleTheme.SMALL_FONT);
        argumentsTitle.setForeground(ConsoleTheme.MUTED_TEXT);
        argumentsTitle.setAlignmentX(LEFT_ALIGNMENT);

        argumentsField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        argumentsField.setAlignmentX(LEFT_ALIGNMENT);
        argumentsField.setEnabled(false);
        ConsoleTheme.styleTextField(argumentsField);
        argumentsField.addActionListener(e -> runSelectedCommand());
        argumentsField.getDocument().addDocumentListener(new SimpleDocumentListener() {
            @Override
            public void changed() {
                resetDangerConfirmation();
                updatePreview();
            }
        });

        JLabel previewTitle = new JLabel("Preview");
        previewTitle.setFont(ConsoleTheme.SMALL_FONT);
        previewTitle.setForeground(ConsoleTheme.MUTED_TEXT);
        previewTitle.setAlignmentX(LEFT_ALIGNMENT);

        previewLabel.setFont(ConsoleTheme.BODY_FONT);
        previewLabel.setForeground(ConsoleTheme.TEXT);
        previewLabel.setAlignmentX(LEFT_ALIGNMENT);

        runButton.setAlignmentX(LEFT_ALIGNMENT);
        runButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        runButton.setEnabled(false);
        ConsoleTheme.styleButton(runButton);
        runButton.addActionListener(e -> runSelectedCommand());

        statusLabel.setFont(ConsoleTheme.SMALL_FONT);
        statusLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);

        card.add(selectedLabel);
        card.add(Box.createVerticalStrut(3));
        card.add(usageText);
        card.add(Box.createVerticalStrut(8));
        card.add(argumentsTitle);
        card.add(Box.createVerticalStrut(4));
        card.add(argumentsField);
        card.add(Box.createVerticalStrut(8));
        card.add(previewTitle);
        card.add(Box.createVerticalStrut(3));
        card.add(previewLabel);
        card.add(Box.createVerticalStrut(9));
        card.add(runButton);
        card.add(Box.createVerticalStrut(7));
        card.add(statusLabel);

        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    private void filterCommands() {
        String query = searchField.getText() == null
                ? ""
                : searchField.getText().trim().toLowerCase(Locale.ENGLISH);
        Object selectedCategory = categoryBox.getSelectedItem();
        String category = selectedCategory == null ? CommandCatalog.CATEGORY_ALL : selectedCategory.toString();
        CommandCatalog.Entry previouslyHighlighted = resultList.getSelectedValue();

        resultModel.clear();
        for (CommandCatalog.Entry entry : CommandCatalog.getEntries()) {
            boolean categoryMatches = CommandCatalog.CATEGORY_ALL.equals(category)
                    || entry.getCategory().equals(category);
            boolean queryMatches = query.length() == 0
                    || entry.getName().contains(query)
                    || entry.getCategory().toLowerCase(Locale.ENGLISH).contains(query)
                    || entry.getUsage().toLowerCase(Locale.ENGLISH).contains(query);
            if (categoryMatches && queryMatches) {
                resultModel.addElement(entry);
            }
        }

        restoreResultSelection(previouslyHighlighted);
        countLabel.setText(resultModel.size() + " shown / " + CommandCatalog.getEntries().size() + " commands");
    }

    private void restoreResultSelection(CommandCatalog.Entry previous) {
        if (resultModel.isEmpty()) {
            resultList.clearSelection();
            return;
        }
        if (previous != null) {
            for (int index = 0; index < resultModel.size(); index++) {
                if (resultModel.getElementAt(index).getName().equals(previous.getName())) {
                    resultList.setSelectedIndex(index);
                    return;
                }
            }
        }
        resultList.setSelectedIndex(0);
    }

    private void moveResultSelection(int direction) {
        if (resultModel.isEmpty()) {
            return;
        }
        int index = resultList.getSelectedIndex();
        if (index < 0) {
            index = 0;
        } else {
            index = Math.max(0, Math.min(resultModel.size() - 1, index + direction));
        }
        resultList.setSelectedIndex(index);
        resultList.ensureIndexIsVisible(index);
    }

    private void selectHighlightedResult() {
        CommandCatalog.Entry entry = resultList.getSelectedValue();
        if (entry != null) {
            selectCommand(entry);
        }
    }

    private void selectCommand(CommandCatalog.Entry entry) {
        selectedEntry = entry;
        selectedLabel.setText((entry.isDangerous() ? "! " : "") + "::" + entry.getName());
        usageText.setText("Usage: " + entry.getUsage() + "  ·  " + entry.getCategory());
        usageText.setCaretPosition(0);
        argumentsField.setEnabled(true);
        argumentsField.setText("");
        argumentsField.setToolTipText(entry.getUsage());
        runButton.setEnabled(true);
        resetDangerConfirmation();
        statusLabel.setForeground(entry.isDangerous() ? ConsoleTheme.ACCENT : ConsoleTheme.MUTED_TEXT);
        statusLabel.setText(entry.isDangerous()
                ? "Protected command: Run requires a second confirmation."
                : "Ready.");
        updatePreview();
        argumentsField.requestFocusInWindow();
    }

    private void updatePreview() {
        if (selectedEntry == null) {
            previewLabel.setText("::");
            return;
        }
        previewLabel.setText(buildCommand());
    }

    private String buildCommand() {
        StringBuilder command = new StringBuilder("::").append(selectedEntry.getName());
        String arguments = argumentsField.getText() == null ? "" : argumentsField.getText().trim();
        if (arguments.length() > 0) {
            command.append(' ').append(arguments);
        }
        return command.toString();
    }

    private void runSelectedCommand() {
        if (selectedEntry == null) {
            return;
        }

        String displayCommand = buildCommand();
        String command = displayCommand.substring(2);

        if (selectedEntry.isDangerous()) {
            long now = System.currentTimeMillis();
            if (!command.equals(pendingDangerCommand) || now > pendingDangerUntil) {
                pendingDangerCommand = command;
                pendingDangerUntil = now + DANGER_CONFIRM_WINDOW_MS;
                runButton.setText("CONFIRM RUN");
                statusLabel.setForeground(ConsoleTheme.ACCENT);
                statusLabel.setText("Dangerous command. Click Confirm Run within 5 seconds.");
                return;
            }
        }

        resetDangerConfirmation();
        String error = ClientConsoleBridge.queueConsoleCommand(command);
        if (error == null) {
            statusLabel.setForeground(ConsoleTheme.ACCENT);
            statusLabel.setText("Queued " + displayCommand + " through the normal server command path.");
        } else {
            statusLabel.setForeground(ConsoleTheme.MUTED_TEXT);
            statusLabel.setText(error);
        }
    }

    private void resetDangerConfirmation() {
        pendingDangerCommand = null;
        pendingDangerUntil = 0L;
        runButton.setText("RUN COMMAND");
    }

    private static final class CommandRenderer extends JPanel implements ListCellRenderer<CommandCatalog.Entry> {
        private static final long serialVersionUID = 7504484118137589999L;

        private final JLabel name = new JLabel();
        private final JLabel meta = new JLabel();
        private final JLabel danger = new JLabel("!", SwingConstants.CENTER);

        private CommandRenderer() {
            super(new BorderLayout(8, 0));
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

            JPanel text = new JPanel();
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setOpaque(false);

            name.setFont(ConsoleTheme.BODY_FONT);
            name.setForeground(ConsoleTheme.TEXT);
            meta.setFont(ConsoleTheme.SMALL_FONT);
            meta.setForeground(ConsoleTheme.MUTED_TEXT);
            danger.setFont(ConsoleTheme.SECTION_FONT);
            danger.setForeground(ConsoleTheme.ACCENT);
            danger.setPreferredSize(new Dimension(18, 30));

            text.add(name);
            text.add(Box.createVerticalStrut(2));
            text.add(meta);
            add(text, BorderLayout.CENTER);
            add(danger, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends CommandCatalog.Entry> list,
                CommandCatalog.Entry entry, int index, boolean isSelected, boolean cellHasFocus) {
            setBackground(isSelected ? ConsoleTheme.CARD_HOVER : ConsoleTheme.PANEL);
            name.setText("::" + entry.getName());
            meta.setText(entry.getCategory() + "  ·  " + entry.getUsage());
            danger.setVisible(entry.isDangerous());
            return this;
        }
    }

    private abstract static class SimpleDocumentListener implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent e) {
            changed();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            changed();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            changed();
        }

        public abstract void changed();
    }
}
