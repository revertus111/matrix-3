package game.console;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import game.ClientConsoleBridge;

public final class CommandsPanel extends JPanel {

    private static final long serialVersionUID = -2142724755054426808L;
    private static final long DANGER_CONFIRM_WINDOW_MS = 5000L;

    private final JTextField searchField = new JTextField();
    private final JComboBox<String> categoryBox = new JComboBox<String>(CommandCatalog.getCategories());
    private final JPanel commandList = new JPanel();
    private final JLabel countLabel = new JLabel();
    private final JLabel selectedLabel = new JLabel("Select a command");
    private final JLabel usageLabel = new JLabel("Click any command button below.");
    private final JTextField argumentsField = new JTextField();
    private final JLabel previewLabel = new JLabel("::");
    private final JLabel statusLabel = new JLabel("Commands execute through Matrix3's normal server command path.");
    private final JButton runButton = new JButton("RUN COMMAND");
    private final List<CommandRow> rows = new ArrayList<CommandRow>();

    private CommandCatalog.Entry selectedEntry;
    private String pendingDangerCommand;
    private long pendingDangerUntil;

    public CommandsPanel() {
        super(new BorderLayout());
        setBackground(ConsoleTheme.PANEL);
        setOpaque(true);

        add(createHeader(), BorderLayout.NORTH);
        add(createCommandScroll(), BorderLayout.CENTER);

        buildCommandRows();
        filterCommands();
    }

    private JPanel createHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ConsoleTheme.PANEL);
        header.setBorder(ConsoleTheme.panelPadding(20, 18, 12, 18));

        JLabel title = new JLabel("COMMANDS");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Search, select, and run existing Matrix3 commands.");
        subtitle.setFont(ConsoleTheme.SMALL_FONT);
        subtitle.setForeground(ConsoleTheme.ACCENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        countLabel.setFont(ConsoleTheme.SMALL_FONT);
        countLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        countLabel.setAlignmentX(LEFT_ALIGNMENT);

        searchField.setToolTipText("Search command names");
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        searchField.setAlignmentX(LEFT_ALIGNMENT);
        ConsoleTheme.styleTextField(searchField);
        searchField.getDocument().addDocumentListener(new SimpleDocumentListener() {
            @Override
            public void changed() {
                filterCommands();
            }
        });

        categoryBox.setFont(ConsoleTheme.BODY_FONT);
        categoryBox.setForeground(ConsoleTheme.TEXT);
        categoryBox.setBackground(ConsoleTheme.INPUT);
        categoryBox.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
        categoryBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        categoryBox.setAlignmentX(LEFT_ALIGNMENT);
        categoryBox.addActionListener(e -> filterCommands());

        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        header.add(Box.createVerticalStrut(5));
        header.add(countLabel);
        header.add(Box.createVerticalStrut(12));
        header.add(searchField);
        header.add(Box.createVerticalStrut(8));
        header.add(categoryBox);
        header.add(Box.createVerticalStrut(12));
        header.add(createComposer());
        return header;
    }

    private JPanel createComposer() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ConsoleTheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(12, 12, 12, 12)));
        card.setAlignmentX(LEFT_ALIGNMENT);

        selectedLabel.setFont(ConsoleTheme.SECTION_FONT);
        selectedLabel.setForeground(ConsoleTheme.TEXT);
        selectedLabel.setAlignmentX(LEFT_ALIGNMENT);

        usageLabel.setFont(ConsoleTheme.SMALL_FONT);
        usageLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        usageLabel.setAlignmentX(LEFT_ALIGNMENT);

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
        card.add(usageLabel);
        card.add(Box.createVerticalStrut(10));
        card.add(argumentsTitle);
        card.add(Box.createVerticalStrut(4));
        card.add(argumentsField);
        card.add(Box.createVerticalStrut(9));
        card.add(previewTitle);
        card.add(Box.createVerticalStrut(3));
        card.add(previewLabel);
        card.add(Box.createVerticalStrut(10));
        card.add(runButton);
        card.add(Box.createVerticalStrut(8));
        card.add(statusLabel);
        return card;
    }

    private JScrollPane createCommandScroll() {
        commandList.setLayout(new BoxLayout(commandList, BoxLayout.Y_AXIS));
        commandList.setBackground(ConsoleTheme.PANEL);
        commandList.setBorder(ConsoleTheme.panelPadding(0, 18, 20, 18));

        JScrollPane scrollPane = new JScrollPane(commandList);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(scrollPane);
        return scrollPane;
    }

    private void buildCommandRows() {
        for (CommandCatalog.Entry entry : CommandCatalog.getEntries()) {
            JPanel row = new JPanel(new BorderLayout());
            row.setBackground(ConsoleTheme.PANEL);
            row.setOpaque(true);
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 41));
            row.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

            JButton button = new JButton((entry.isDangerous() ? "! " : "") + "::" + entry.getName());
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setToolTipText(entry.getCategory() + " - " + entry.getUsage());
            ConsoleTheme.styleButton(button);
            button.addActionListener(e -> selectCommand(entry));
            row.add(button, BorderLayout.CENTER);

            commandList.add(row);
            rows.add(new CommandRow(entry, row));
        }
    }

    private void selectCommand(CommandCatalog.Entry entry) {
        selectedEntry = entry;
        selectedLabel.setText((entry.isDangerous() ? "! " : "") + "::" + entry.getName());
        usageLabel.setText("Usage: " + entry.getUsage() + "  |  " + entry.getCategory());
        argumentsField.setEnabled(true);
        argumentsField.setText("");
        runButton.setEnabled(true);
        resetDangerConfirmation();
        statusLabel.setForeground(entry.isDangerous() ? ConsoleTheme.ACCENT : ConsoleTheme.MUTED_TEXT);
        statusLabel.setText(entry.isDangerous()
                ? "Protected command: Run requires a second confirmation."
                : "Ready.");
        updatePreview();
        argumentsField.requestFocusInWindow();
    }

    private void filterCommands() {
        String query = searchField.getText() == null
                ? ""
                : searchField.getText().trim().toLowerCase(Locale.ENGLISH);
        Object selectedCategory = categoryBox.getSelectedItem();
        String category = selectedCategory == null ? CommandCatalog.CATEGORY_ALL : selectedCategory.toString();

        int visible = 0;
        for (CommandRow row : rows) {
            boolean categoryMatches = CommandCatalog.CATEGORY_ALL.equals(category)
                    || row.entry.getCategory().equals(category);
            boolean queryMatches = query.length() == 0
                    || row.entry.getName().contains(query)
                    || row.entry.getCategory().toLowerCase(Locale.ENGLISH).contains(query);
            boolean show = categoryMatches && queryMatches;
            row.component.setVisible(show);
            if (show) {
                visible++;
            }
        }
        countLabel.setText(visible + " shown / " + CommandCatalog.getEntries().size() + " commands");
        commandList.revalidate();
        commandList.repaint();
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

    private static final class CommandRow {
        private final CommandCatalog.Entry entry;
        private final JPanel component;

        private CommandRow(CommandCatalog.Entry entry, JPanel component) {
            this.entry = entry;
            this.component = component;
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
