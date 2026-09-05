package game.atlas;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import game.atlas.AtlasQueryEngine.QueryResult;
import game.atlas.AtlasRelationshipQueryEngine.RelationshipQueryResult;
import game.atlas.AtlasScanner.ScanResult;
import game.atlas.AtlasSchema.Metadata;
import game.atlas.AtlasSearchEngine.SearchResult;
import game.atlas.AtlasStructuralVerifier.VerificationResult;

/**
 * Small standalone human control surface over the offline Client Atlas engine.
 * The CLI remains available for automation, but normal developer use should not
 * require editing Eclipse program arguments.
 */
public final class ClientAtlasControl {

    private static final String WINDOW_TITLE = "Client Atlas Control";
    private static final String DEFAULT_QUERY = "Class1";

    private final AtlasWorkspace workspace;
    private final Path classRoot;

    private JFrame frame;
    private JTextField queryField;
    private JTextArea outputArea;
    private JLabel indexStatusValue;
    private JLabel classRootValue;
    private JLabel symbolCountValue;
    private JLabel relationshipCountValue;
    private JLabel fingerprintValue;
    private final List<JButton> taskButtons = new ArrayList<JButton>();

    private AtlasInvestigationIndex investigationIndex;
    private QueryResult lastQueryResult;
    private String lastQueryId;

    private ClientAtlasControl(AtlasWorkspace workspace) {
        this.workspace = workspace;
        this.classRoot = workspace.defaultClassRoot();
    }

    public static void main(String[] args) {
        launch();
    }

    public static void launch() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                installDarkDefaults();
                try {
                    Path clientRoot = AtlasWorkspace.findClientRoot(Paths.get("."));
                    ClientAtlasControl control = new ClientAtlasControl(new AtlasWorkspace(clientRoot));
                    control.showWindow();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null,
                            "Unable to open Client Atlas Control:\n" + ex.getMessage(),
                            WINDOW_TITLE, JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private void showWindow() {
        frame = new JFrame(WINDOW_TITLE);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(940, 580));
        frame.setSize(1060, 720);
        frame.setLocationByPlatform(true);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.add(buildStatusPanel(), BorderLayout.NORTH);
        root.add(buildCenterPanel(), BorderLayout.CENTER);
        root.add(buildActionPanel(), BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setVisible(true);
        refreshStatus();
    }

    private JPanel buildStatusPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Atlas status"));

        indexStatusValue = valueLabel("Checking...");
        classRootValue = valueLabel(classRoot.toString());
        symbolCountValue = valueLabel("-");
        relationshipCountValue = valueLabel("-");
        fingerprintValue = valueLabel("-");

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridy = 0;
        addStatusRow(panel, gbc, "Index", indexStatusValue);
        gbc.gridy++;
        addStatusRow(panel, gbc, "Compiled classes", classRootValue);
        gbc.gridy++;
        addStatusRow(panel, gbc, "Symbols", symbolCountValue);
        gbc.gridy++;
        addStatusRow(panel, gbc, "Relationships", relationshipCountValue);
        gbc.gridy++;
        addStatusRow(panel, gbc, "Fingerprint", fingerprintValue);
        return panel;
    }

    private JPanel buildCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search / Investigate"));
        queryField = new JTextField(DEFAULT_QUERY);
        queryField.setToolTipText("Examples: Class387 | Class387.method4844 | calls <symbol> | constant 762");
        JButton searchButton = taskButton("Search", new Runnable() {
            @Override
            public void run() {
                runSearch();
            }
        });
        queryField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runSearch();
            }
        });
        searchPanel.add(queryField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setLineWrap(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputArea.setText("Client Atlas is ready.\n\n"
                + "Friendly search: Class387 | Class387.method4844 | method4844\n"
                + "Relationships: calls | called-by | reads | written-by | references | constant\n"
                + "Neighborhood: neighbors <symbol> depth=1 or depth=2\n"
                + "Use Run Search Check for the one-click Bundle 2B local gate.\n");

        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Output"));

        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildActionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

        panel.add(taskButton("Run Search Check", new Runnable() {
            @Override
            public void run() {
                runInvestigationCheck();
            }
        }));
        panel.add(taskButton("Run Structural Check", new Runnable() {
            @Override
            public void run() {
                runPhase2Check();
            }
        }));
        panel.add(taskButton("Run Phase 1 Check", new Runnable() {
            @Override
            public void run() {
                runPhase1Check();
            }
        }));
        panel.add(taskButton("Scan / Rebuild Index", new Runnable() {
            @Override
            public void run() {
                runScan();
            }
        }));
        panel.add(taskButton("Refresh Status", new Runnable() {
            @Override
            public void run() {
                refreshStatus();
            }
        }));
        panel.add(taskButton("Export Last Result", new Runnable() {
            @Override
            public void run() {
                exportLastResult();
            }
        }));

        JButton openWorkspace = new JButton("Open Workspace");
        openWorkspace.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openWorkspace();
            }
        });
        panel.add(openWorkspace);
        return panel;
    }

    private JButton taskButton(String text, final Runnable task) {
        final JButton button = new JButton(text);
        taskButtons.add(button);
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                task.run();
            }
        });
        return button;
    }

    private void runInvestigationCheck() {
        runBackground("Running the consolidated investigation/search check...", new BackgroundTask() {
            private AtlasInvestigationVerifier.VerificationResult result;

            @Override
            public String execute() throws Exception {
                invalidateInvestigationIndex();
                clearLastExportableQuery();
                result = new AtlasInvestigationVerifier(workspace, classRoot).run();
                return result.getReport() + "\nReport: " + result.getReportPath();
            }

            @Override
            public void complete() {
                if (result != null) {
                    setStatus(result.getMetadata(), true);
                }
            }
        });
    }

    private void runPhase2Check() {
        runBackground("Running the consolidated structural check...", new BackgroundTask() {
            private VerificationResult result;

            @Override
            public String execute() throws Exception {
                invalidateInvestigationIndex();
                result = new AtlasStructuralVerifier(workspace, classRoot).run();
                clearLastExportableQuery();
                return result.getReport() + "\nReport: " + result.getReportPath();
            }

            @Override
            public void complete() {
                if (result != null) {
                    setStatus(result.getMetadata(), true);
                }
            }
        });
    }

    private void runScan() {
        runBackground("Scanning compiled client...", new BackgroundTask() {
            @Override
            public String execute() throws Exception {
                invalidateInvestigationIndex();
                ScanResult result = new AtlasScanner(workspace).scan(classRoot);
                clearLastExportableQuery();
                return "Client Atlas scan complete.\n"
                        + "Class files: " + result.getClassFileCount() + "\n"
                        + "Symbols: " + result.getSymbolCount() + "\n"
                        + "Relationships: " + result.getRelationshipCount() + "\n"
                        + "Client fingerprint: " + result.getClientFingerprint();
            }

            @Override
            public void complete() {
                refreshStatus();
            }
        });
    }

    private void refreshStatus() {
        runBackground("Refreshing Atlas status...", new BackgroundTask() {
            private Metadata metadata;
            private boolean current;

            @Override
            public String execute() throws Exception {
                if (!Files.isDirectory(classRoot)) {
                    invalidateInvestigationIndex();
                    throw new IOException("Compiled classes do not exist yet: " + classRoot
                            + "\nBuild Matrix3-Client in Eclipse first.");
                }
                if (!Files.isRegularFile(workspace.metadataFile())) {
                    invalidateInvestigationIndex();
                    return "No Atlas index exists yet. Click Run Structural Check or Scan / Rebuild Index.";
                }
                metadata = workspace.readMetadata();
                current = workspace.isCurrent(classRoot);
                if (metadata.getSchemaVersion() != AtlasWorkspace.SCHEMA_VERSION || !current) {
                    invalidateInvestigationIndex();
                }
                if (metadata.getSchemaVersion() != AtlasWorkspace.SCHEMA_VERSION) {
                    return "Atlas status refreshed. Schema " + metadata.getSchemaVersion()
                            + " requires rebuild to schema " + AtlasWorkspace.SCHEMA_VERSION + ".";
                }
                return "Atlas status refreshed. Index is " + (current ? "CURRENT" : "STALE") + ".";
            }

            @Override
            public void complete() {
                if (metadata == null) {
                    setStatusMissing();
                } else {
                    setStatus(metadata, current);
                }
            }
        });
    }

    private void runSearch() {
        final String raw = queryField.getText() == null ? "" : queryField.getText().trim();
        if (raw.length() == 0) {
            showError("Enter a symbol search or relationship command.");
            return;
        }

        runBackground("Searching " + raw + "...", new BackgroundTask() {
            private QueryResult exportableResult;
            private String exportableId;

            @Override
            public String execute() throws Exception {
                AtlasInvestigationIndex index = currentInvestigationIndex();
                AtlasRelationshipQueryEngine relationshipEngine = new AtlasRelationshipQueryEngine(index);
                StringBuilder output = new StringBuilder(2048);
                output.append("Index: ").append(index.getSymbolCount()).append(" symbols / ")
                        .append(index.getRelationshipCount()).append(" relationships")
                        .append(" | load ").append(formatMillis(index.getLoadNanos())).append(" ms\n\n");

                if (relationshipEngine.isRelationshipCommand(raw)) {
                    RelationshipQueryResult result = relationshipEngine.query(raw);
                    output.append(result.toDisplayText());
                    return output.toString();
                }

                SearchResult result = new AtlasSearchEngine(index).search(raw);
                output.append(result.toDisplayText());
                if (result.isResolved()) {
                    exportableId = result.getResolvedSymbol().getId();
                    exportableResult = new AtlasQueryEngine(workspace).queryExact(exportableId, classRoot);
                }
                return output.toString();
            }

            @Override
            public void complete() {
                lastQueryResult = exportableResult;
                lastQueryId = exportableId;
            }
        });
    }

    private AtlasInvestigationIndex currentInvestigationIndex() throws IOException {
        if (investigationIndex != null) {
            Metadata metadata = workspace.readMetadata();
            Metadata cached = investigationIndex.getMetadata();
            boolean sameSnapshot = metadata.getSchemaVersion() == AtlasWorkspace.SCHEMA_VERSION
                    && metadata.getClientFingerprint().equals(cached.getClientFingerprint())
                    && metadata.getGeneratedAtUtc().equals(cached.getGeneratedAtUtc())
                    && metadata.getSymbolCount() == cached.getSymbolCount()
                    && metadata.getRelationshipCount() == cached.getRelationshipCount();
            if (sameSnapshot && workspace.isCurrent(classRoot)) {
                return investigationIndex;
            }
            invalidateInvestigationIndex();
        }
        investigationIndex = AtlasInvestigationIndex.load(workspace, classRoot);
        return investigationIndex;
    }

    private void invalidateInvestigationIndex() {
        investigationIndex = null;
    }

    private void clearLastExportableQuery() {
        lastQueryResult = null;
        lastQueryId = null;
    }

    private void exportLastResult() {
        if (lastQueryResult == null || lastQueryId == null) {
            showError("Run a friendly/exact symbol search that resolves to one symbol first. "
                    + "Relationship-command export arrives in the assistant-export step.");
            return;
        }

        runBackground("Exporting last resolved symbol...", new BackgroundTask() {
            @Override
            public String execute() throws Exception {
                Path exportDirectory = workspace.getWorkspaceRoot().resolve("exports");
                String fileName = safeFileName(lastQueryId) + ".json";
                Path output = new AtlasQueryEngine(workspace).writeExport(lastQueryResult,
                        exportDirectory.resolve(fileName));
                return "Export written:\n" + output;
            }
        });
    }

    private void runPhase1Check() {
        runBackground("Running the Phase 1 regression check...", new BackgroundTask() {
            private Metadata metadata;

            @Override
            public String execute() throws Exception {
                invalidateInvestigationIndex();
                StringBuilder report = new StringBuilder(1024);

                ScanResult scan = new AtlasScanner(workspace).scan(classRoot);
                require(scan.getClassFileCount() > 0, "scan produced zero class files");
                require(scan.getSymbolCount() > 0, "scan produced zero symbols");
                require(scan.getRelationshipCount() > 0, "scan produced zero relationships");
                report.append("PASS  Scan: ").append(scan.getClassFileCount()).append(" classes, ")
                        .append(scan.getSymbolCount()).append(" symbols, ")
                        .append(scan.getRelationshipCount()).append(" relationships\n");

                metadata = workspace.readMetadata();
                require(workspace.isCurrent(classRoot), "persisted fingerprint is stale immediately after scan");
                report.append("PASS  Metadata reopen + current fingerprint\n");

                Coverage coverage = inspectCoverage();
                require(coverage.hasClass, "no CLASS symbol found");
                require(coverage.hasField, "no FIELD symbol found");
                require(coverage.hasMethod, "no METHOD symbol found");
                require(coverage.hasConstructor, "no CONSTRUCTOR symbol found");
                require(coverage.hasDeclares, "no DECLARES relationship found");
                require(coverage.hasExtends, "no EXTENDS relationship found");
                require(coverage.hasImplements, "no IMPLEMENTS relationship found");
                require(!coverage.hasAtlasSymbol, "generated symbols unexpectedly include game/atlas classes");
                report.append("PASS  Symbol + structural relationship coverage\n");
                report.append("PASS  game/atlas classes excluded from generated symbols\n");

                String classId = coverage.firstClassId;
                require(classId != null, "unable to choose a class symbol for query verification");
                AtlasQueryEngine engine = new AtlasQueryEngine(workspace);
                QueryResult classResult = engine.queryExact(classId, classRoot);
                require(classResult.toJson().contains("\"indexCurrent\":true"),
                        "class query did not report a current index");
                report.append("PASS  Exact class query: ").append(classId).append('\n');

                String memberId = findDeclaredMemberId(classId);
                if (memberId != null) {
                    engine.queryExact(memberId, classRoot);
                    report.append("PASS  Exact member query: ").append(memberId).append('\n');
                } else {
                    report.append("NOTE  No METHOD/CONSTRUCTOR DECLARES target found for chosen class.\n");
                }

                Path exportPath = workspace.getWorkspaceRoot().resolve("phase1-check.json");
                engine.writeExport(classResult, exportPath);
                require(Files.isRegularFile(exportPath) && Files.size(exportPath) > 0L,
                        "compact export was not written");
                report.append("PASS  Compact export: ").append(exportPath).append('\n');
                report.append("\nPHASE 1 REGRESSION CHECK: PASS\n");
                clearLastExportableQuery();
                return report.toString();
            }

            @Override
            public void complete() {
                if (metadata != null) {
                    setStatus(metadata, true);
                }
            }
        });
    }

    private Coverage inspectCoverage() throws IOException {
        Coverage coverage = new Coverage();
        try (BufferedReader reader = Files.newBufferedReader(workspace.symbolsFile(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                coverage.hasClass |= line.contains("\"kind\":\"CLASS\"");
                coverage.hasField |= line.contains("\"kind\":\"FIELD\"");
                coverage.hasMethod |= line.contains("\"kind\":\"METHOD\"");
                coverage.hasConstructor |= line.contains("\"kind\":\"CONSTRUCTOR\"");
                coverage.hasAtlasSymbol |= line.contains("game/atlas/");
                if (coverage.firstClassId == null && line.contains("\"kind\":\"CLASS\"")) {
                    coverage.firstClassId = extractJsonString(line, "id");
                }
            }
        }

        try (BufferedReader reader = Files.newBufferedReader(workspace.relationshipsFile(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                coverage.hasDeclares |= line.contains("\"type\":\"DECLARES\"");
                coverage.hasExtends |= line.contains("\"type\":\"EXTENDS\"");
                coverage.hasImplements |= line.contains("\"type\":\"IMPLEMENTS\"");
            }
        }
        return coverage;
    }

    private String findDeclaredMemberId(String classId) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(workspace.relationshipsFile(), StandardCharsets.UTF_8)) {
            String line;
            String fromToken = "\"fromId\":" + AtlasJson.quote(classId);
            while ((line = reader.readLine()) != null) {
                if (!line.contains(fromToken) || !line.contains("\"type\":\"DECLARES\"")) {
                    continue;
                }
                String target = extractJsonString(line, "target");
                if (target != null && (target.startsWith("METHOD:") || target.startsWith("CONSTRUCTOR:"))) {
                    return target;
                }
            }
        }
        return null;
    }

    private static String extractJsonString(String line, String field) {
        String token = "\"" + field + "\":\"";
        int start = line.indexOf(token);
        if (start < 0) {
            return null;
        }
        start += token.length();
        int end = line.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return line.substring(start, end);
    }

    private void openWorkspace() {
        try {
            workspace.ensureLayout();
            if (!Desktop.isDesktopSupported()) {
                throw new IOException("Desktop open is not supported on this system.");
            }
            Desktop.getDesktop().open(workspace.getWorkspaceRoot().toFile());
        } catch (Exception ex) {
            showError("Unable to open Atlas workspace: " + ex.getMessage());
        }
    }

    private void runBackground(final String startingMessage, final BackgroundTask task) {
        setTaskButtonsEnabled(false);
        appendOutput("\n> " + startingMessage + "\n");

        new SwingWorker<String, Void>() {
            private Exception failure;

            @Override
            protected String doInBackground() {
                try {
                    return task.execute();
                } catch (Exception ex) {
                    failure = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    if (failure != null) {
                        appendOutput("FAIL  " + failure.getMessage() + "\n");
                        showError(failure.getMessage());
                    } else {
                        String result = get();
                        if (result != null && result.length() > 0) {
                            appendOutput(result + "\n");
                        }
                        task.complete();
                    }
                } catch (Exception ex) {
                    appendOutput("FAIL  " + ex.getMessage() + "\n");
                    showError(ex.getMessage());
                } finally {
                    setTaskButtonsEnabled(true);
                }
            }
        }.execute();
    }

    private void setStatus(Metadata metadata, boolean current) {
        if (metadata.getSchemaVersion() != AtlasWorkspace.SCHEMA_VERSION) {
            indexStatusValue.setText("REBUILD REQUIRED (schema " + metadata.getSchemaVersion()
                    + " -> " + AtlasWorkspace.SCHEMA_VERSION + ")");
        } else {
            indexStatusValue.setText(current ? "CURRENT" : "STALE");
        }
        symbolCountValue.setText(Long.toString(metadata.getSymbolCount()));
        relationshipCountValue.setText(Long.toString(metadata.getRelationshipCount()));
        String fingerprint = metadata.getClientFingerprint();
        fingerprintValue.setText(fingerprint.length() > 20 ? fingerprint.substring(0, 20) + "..." : fingerprint);
        fingerprintValue.setToolTipText(fingerprint);
    }

    private void setStatusMissing() {
        indexStatusValue.setText(Files.isDirectory(classRoot) ? "NO INDEX" : "CLASSES MISSING");
        symbolCountValue.setText("-");
        relationshipCountValue.setText("-");
        fingerprintValue.setText("-");
    }

    private void setTaskButtonsEnabled(boolean enabled) {
        for (JButton button : taskButtons) {
            button.setEnabled(enabled);
        }
        if (queryField != null) {
            queryField.setEnabled(enabled);
        }
    }

    private void appendOutput(String text) {
        outputArea.append(text);
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, WINDOW_TITLE, JOptionPane.ERROR_MESSAGE);
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", Double.valueOf(nanos / 1000000.0D));
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }

    private static JLabel valueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private static void addStatusRow(JPanel panel, GridBagConstraints gbc, String label, JLabel value) {
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label + ":"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(value, gbc);
    }

    private static void installDarkDefaults() {
        Color background = new Color(35, 38, 43);
        Color panel = new Color(43, 47, 53);
        Color foreground = new Color(225, 228, 232);
        Color field = new Color(28, 31, 35);
        Color selection = new Color(70, 78, 88);

        UIManager.put("Panel.background", panel);
        UIManager.put("Label.foreground", foreground);
        UIManager.put("Button.background", new Color(57, 62, 70));
        UIManager.put("Button.foreground", foreground);
        UIManager.put("TextField.background", field);
        UIManager.put("TextField.foreground", foreground);
        UIManager.put("TextField.caretForeground", foreground);
        UIManager.put("TextArea.background", field);
        UIManager.put("TextArea.foreground", foreground);
        UIManager.put("TextArea.caretForeground", foreground);
        UIManager.put("TextArea.selectionBackground", selection);
        UIManager.put("ScrollPane.background", background);
        UIManager.put("Viewport.background", field);
        UIManager.put("TitledBorder.titleColor", foreground);
    }

    private interface BackgroundTask {
        String execute() throws Exception;

        default void complete() {
        }
    }

    private static final class Coverage {
        private boolean hasClass;
        private boolean hasField;
        private boolean hasMethod;
        private boolean hasConstructor;
        private boolean hasDeclares;
        private boolean hasExtends;
        private boolean hasImplements;
        private boolean hasAtlasSymbol;
        private String firstClassId;
    }
}
