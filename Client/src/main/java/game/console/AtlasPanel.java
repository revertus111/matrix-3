package game.console;

import game.atlas.AtlasEvidenceStore;
import game.atlas.AtlasEvidenceStore.EvidenceView;
import game.atlas.AtlasInvestigationIndex;
import game.atlas.AtlasInvestigationIndex.RelationshipEntry;
import game.atlas.AtlasInvestigationIndex.SymbolEntry;
import game.atlas.AtlasSchema.EvidenceRecord;
import game.atlas.AtlasSchema.EvidenceStatus;
import game.atlas.AtlasSearchEngine;
import game.atlas.AtlasSearchEngine.SearchMatch;
import game.atlas.AtlasSearchEngine.SearchResult;
import game.atlas.AtlasWorkspace;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

/**
 * Client Console browser/editor over the existing Client Atlas engine.
 *
 * Atlas remains authoritative for indexing, search, relationships, and curated
 * evidence persistence. This panel only provides a lazy Swing control surface.
 */
public final class AtlasPanel extends JPanel {

    private static final long serialVersionUID = -1658784684675152967L;
    private static final int SEARCH_LIMIT = 50;
    private static final int RELATION_LIMIT_PER_DIRECTION = 60;

    private final Object indexLock = new Object();

    private final JTextField searchField = new JTextField("Class1");
    private final JButton searchButton = new JButton("Search");
    private final JButton reloadButton = new JButton("Reload");
    private final JLabel statusLabel = new JLabel("Search to load the current Atlas index.");

    private final DefaultListModel<SearchRow> resultModel = new DefaultListModel<SearchRow>();
    private final JList<SearchRow> resultList = new JList<SearchRow>(resultModel);

    private final JTextArea symbolText = readOnlyArea(7, true);
    private final DefaultListModel<RelationshipRow> relationshipModel =
            new DefaultListModel<RelationshipRow>();
    private final JList<RelationshipRow> relationshipList =
            new JList<RelationshipRow>(relationshipModel);
    private final JButton openRelationshipButton = new JButton("Open selected relation");

    private final JLabel evidenceFreshnessLabel = new JLabel("No symbol selected");
    private final JTextArea evidenceWarningArea = readOnlyArea(3, true);
    private final JComboBox<EvidenceStatus> evidenceStatus =
            new JComboBox<EvidenceStatus>(EvidenceStatus.values());
    private final JTextField aliasField = new JTextField();
    private final JTextArea claimArea = editableArea(5);
    private final JTextArea referencesArea = editableArea(4);
    private final JButton saveEvidenceButton = new JButton("Save evidence");
    private final JButton deleteEvidenceButton = new JButton("Delete evidence");

    private volatile AtlasWorkspace workspace;
    private volatile AtlasInvestigationIndex index;
    private volatile AtlasEvidenceStore evidenceStore;
    private SymbolEntry selectedSymbol;
    private int detailGeneration;

    public AtlasPanel() {
        super(new BorderLayout(0, 10));
        setBackground(ConsoleTheme.PANEL);
        setBorder(ConsoleTheme.panelPadding(18, 14, 16, 14));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBrowser(), BorderLayout.CENTER);

        configureInteractions();
        setEvidenceEditorEnabled(false);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JLabel title = new JLabel("CLIENT ATLAS");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Search symbols, follow relationships, and curate evidence");
        subtitle.setFont(ConsoleTheme.SMALL_FONT);
        subtitle.setForeground(ConsoleTheme.ACCENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        JPanel searchRow = new JPanel(new BorderLayout(7, 0));
        searchRow.setOpaque(false);
        searchRow.setAlignmentX(LEFT_ALIGNMENT);
        ConsoleTheme.styleTextField(searchField);
        ConsoleTheme.styleButton(searchButton);
        ConsoleTheme.styleButton(reloadButton);

        JPanel searchButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        searchButtons.setOpaque(false);
        searchButtons.add(searchButton);
        searchButtons.add(reloadButton);

        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(searchButtons, BorderLayout.EAST);

        statusLabel.setFont(ConsoleTheme.SMALL_FONT);
        statusLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);

        header.add(title);
        header.add(Box.createVerticalStrut(3));
        header.add(subtitle);
        header.add(Box.createVerticalStrut(12));
        header.add(searchRow);
        header.add(Box.createVerticalStrut(7));
        header.add(statusLabel);
        return header;
    }

    private JSplitPane buildBrowser() {
        JPanel resultsCard = createCard("Search results");
        configureList(resultList);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setCellRenderer(new SearchRowRenderer());
        JScrollPane resultsScroll = new JScrollPane(resultList);
        ConsoleTheme.styleScrollPane(resultsScroll);
        resultsScroll.setPreferredSize(new Dimension(1, 170));
        resultsCard.add(resultsScroll, BorderLayout.CENTER);

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBackground(ConsoleTheme.PANEL);

        JPanel symbolCard = createCard("Exact symbol");
        symbolCard.add(wrap(symbolText), BorderLayout.CENTER);

        JPanel relationshipCard = createCard("Relationships");
        configureList(relationshipList);
        relationshipList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        relationshipList.setCellRenderer(new RelationshipRowRenderer());
        JScrollPane relationScroll = new JScrollPane(relationshipList);
        ConsoleTheme.styleScrollPane(relationScroll);
        relationScroll.setPreferredSize(new Dimension(1, 165));
        relationshipCard.add(relationScroll, BorderLayout.CENTER);
        ConsoleTheme.styleButton(openRelationshipButton);
        relationshipCard.add(openRelationshipButton, BorderLayout.SOUTH);

        JPanel evidenceCard = buildEvidenceCard();

        details.add(symbolCard);
        details.add(Box.createVerticalStrut(10));
        details.add(relationshipCard);
        details.add(Box.createVerticalStrut(10));
        details.add(evidenceCard);
        details.add(Box.createVerticalGlue());

        JScrollPane detailScroll = new JScrollPane(details);
        detailScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        detailScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(detailScroll);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, resultsCard, detailScroll);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setBackground(ConsoleTheme.PANEL);
        split.setDividerSize(5);
        split.setResizeWeight(0.28D);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(false);
        split.setDividerLocation(190);
        return split;
    }

    private JPanel buildEvidenceCard() {
        JPanel card = createCard("Curated evidence");

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(ConsoleTheme.CARD);

        evidenceFreshnessLabel.setFont(ConsoleTheme.SECTION_FONT);
        evidenceFreshnessLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        evidenceFreshnessLabel.setAlignmentX(LEFT_ALIGNMENT);

        evidenceWarningArea.setForeground(ConsoleTheme.MUTED_TEXT);
        evidenceWarningArea.setBackground(ConsoleTheme.CARD);
        evidenceWarningArea.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

        styleCombo(evidenceStatus);
        ConsoleTheme.styleTextField(aliasField);
        styleEditableArea(claimArea);
        styleEditableArea(referencesArea);
        ConsoleTheme.styleButton(saveEvidenceButton);
        ConsoleTheme.styleButton(deleteEvidenceButton);

        body.add(evidenceFreshnessLabel);
        body.add(Box.createVerticalStrut(5));
        body.add(evidenceWarningArea);
        body.add(Box.createVerticalStrut(8));
        body.add(fieldLabel("Classification"));
        body.add(Box.createVerticalStrut(4));
        body.add(evidenceStatus);
        body.add(Box.createVerticalStrut(8));
        body.add(fieldLabel("Alias (external only)"));
        body.add(Box.createVerticalStrut(4));
        body.add(aliasField);
        body.add(Box.createVerticalStrut(8));
        body.add(fieldLabel("Note / claim"));
        body.add(Box.createVerticalStrut(4));
        body.add(wrap(claimArea));
        body.add(Box.createVerticalStrut(8));
        body.add(fieldLabel("Supporting references - one per line"));
        body.add(Box.createVerticalStrut(4));
        body.add(wrap(referencesArea));
        body.add(Box.createVerticalStrut(10));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        actions.add(saveEvidenceButton);
        actions.add(deleteEvidenceButton);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        body.add(actions);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private void configureInteractions() {
        searchField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runSearch(false);
            }
        });
        searchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runSearch(false);
            }
        });
        reloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runSearch(true);
            }
        });

        resultList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                SearchRow row = resultList.getSelectedValue();
                if (row != null) {
                    selectSymbol(row.symbol);
                }
            }
        });
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    SearchRow row = resultList.getSelectedValue();
                    if (row != null) {
                        selectSymbol(row.symbol);
                    }
                }
            }
        });

        relationshipList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedRelationship();
                }
            }
        });
        openRelationshipButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openSelectedRelationship();
            }
        });

        saveEvidenceButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveEvidence();
            }
        });
        deleteEvidenceButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteEvidence();
            }
        });
    }

    private void runSearch(final boolean forceReload) {
        final String query = searchField.getText() == null ? "" : searchField.getText().trim();
        if (query.length() == 0) {
            setStatus("Enter an Atlas symbol/name query.", true);
            return;
        }

        setSearchBusy(true);
        setStatus(forceReload ? "Reloading Atlas and searching..." : "Searching Atlas...", false);

        new SwingWorker<SearchPayload, Void>() {
            private Exception failure;

            @Override
            protected SearchPayload doInBackground() {
                try {
                    AtlasInvestigationIndex loaded = ensureIndex(forceReload);
                    SearchResult result = new AtlasSearchEngine(loaded).search(query, SEARCH_LIMIT);
                    return new SearchPayload(loaded, result);
                } catch (Exception ex) {
                    failure = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    if (failure != null) {
                        clearResults();
                        setStatus("Atlas search failed: " + failure.getMessage(), true);
                        return;
                    }
                    SearchPayload payload = get();
                    showSearchResult(payload.index, payload.result);
                } catch (Exception ex) {
                    clearResults();
                    setStatus("Atlas search failed: " + ex.getMessage(), true);
                } finally {
                    setSearchBusy(false);
                }
            }
        }.execute();
    }

    private AtlasInvestigationIndex ensureIndex(boolean forceReload) throws IOException {
        synchronized (indexLock) {
            if (forceReload) {
                index = null;
                workspace = null;
                evidenceStore = null;
            }
            if (index != null) {
                return index;
            }
            Path clientRoot = AtlasWorkspace.findClientRoot(Paths.get("."));
            AtlasWorkspace loadedWorkspace = new AtlasWorkspace(clientRoot);
            AtlasInvestigationIndex loadedIndex =
                    AtlasInvestigationIndex.load(loadedWorkspace, loadedWorkspace.defaultClassRoot());
            workspace = loadedWorkspace;
            evidenceStore = new AtlasEvidenceStore(loadedWorkspace);
            index = loadedIndex;
            return loadedIndex;
        }
    }

    private void showSearchResult(AtlasInvestigationIndex loadedIndex, SearchResult result) {
        resultModel.clear();
        for (SearchMatch match : result.getMatches()) {
            resultModel.addElement(new SearchRow(match));
        }

        StringBuilder status = new StringBuilder(160);
        status.append(result.getTotalMatches()).append(" match");
        if (result.getTotalMatches() != 1L) {
            status.append("es");
        }
        if (result.isTruncated()) {
            status.append(" (showing ").append(result.getMatches().size()).append(')');
        }
        status.append(" | ").append(loadedIndex.getSymbolCount()).append(" symbols / ")
                .append(loadedIndex.getRelationshipCount()).append(" relationships");
        setStatus(status.toString(), false);

        if (result.isResolved()) {
            selectSymbol(result.getResolvedSymbol());
            selectResultRow(result.getResolvedSymbol().getId());
        } else if (resultModel.size() > 0) {
            resultList.setSelectedIndex(0);
        } else {
            clearSelectionDetails();
        }
    }

    private void selectResultRow(String symbolId) {
        for (int i = 0; i < resultModel.size(); i++) {
            SearchRow row = resultModel.get(i);
            if (row.symbol.getId().equals(symbolId)) {
                resultList.setSelectedIndex(i);
                resultList.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    private void selectSymbol(final SymbolEntry symbol) {
        if (symbol == null) {
            return;
        }
        selectedSymbol = symbol;
        final int generation = ++detailGeneration;
        symbolText.setText(formatSymbol(symbol));
        relationshipModel.clear();
        evidenceFreshnessLabel.setText("Loading evidence...");
        evidenceWarningArea.setText("");
        setEvidenceEditorEnabled(false);

        new SwingWorker<DetailPayload, Void>() {
            private Exception failure;

            @Override
            protected DetailPayload doInBackground() {
                try {
                    AtlasInvestigationIndex loadedIndex = index;
                    AtlasEvidenceStore store = evidenceStore;
                    if (loadedIndex == null || store == null) {
                        throw new IOException("Atlas index is not loaded");
                    }
                    List<RelationshipRow> rows = relationshipRows(loadedIndex, symbol);
                    EvidenceView evidence = store.getView(symbol.getId(), loadedIndex);
                    return new DetailPayload(rows, evidence);
                } catch (Exception ex) {
                    failure = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                if (generation != detailGeneration || selectedSymbol != symbol) {
                    return;
                }
                try {
                    if (failure != null) {
                        relationshipModel.clear();
                        evidenceFreshnessLabel.setText("Evidence unavailable");
                        evidenceWarningArea.setText(failure.getMessage());
                        setStatus("Atlas detail load failed: " + failure.getMessage(), true);
                        return;
                    }
                    DetailPayload payload = get();
                    relationshipModel.clear();
                    for (RelationshipRow row : payload.relationships) {
                        relationshipModel.addElement(row);
                    }
                    showEvidence(payload.evidence);
                    setStatus("Selected exact symbol: " + symbol.getId(), false);
                } catch (Exception ex) {
                    evidenceFreshnessLabel.setText("Evidence unavailable");
                    evidenceWarningArea.setText(ex.getMessage());
                    setStatus("Atlas detail load failed: " + ex.getMessage(), true);
                }
            }
        }.execute();
    }

    private List<RelationshipRow> relationshipRows(AtlasInvestigationIndex loadedIndex,
            SymbolEntry symbol) {
        List<RelationshipRow> rows = new ArrayList<RelationshipRow>();
        List<RelationshipEntry> outgoing = loadedIndex.outgoing(symbol.getId());
        int outgoingCount = Math.min(outgoing.size(), RELATION_LIMIT_PER_DIRECTION);
        for (int i = 0; i < outgoingCount; i++) {
            RelationshipEntry relation = outgoing.get(i);
            String targetId = loadedIndex.getSymbol(relation.getTarget()) != null
                    ? relation.getTarget() : null;
            rows.add(new RelationshipRow(true, relation, targetId));
        }
        List<RelationshipEntry> incoming = loadedIndex.incoming(symbol.getId());
        int incomingCount = Math.min(incoming.size(), RELATION_LIMIT_PER_DIRECTION);
        for (int i = 0; i < incomingCount; i++) {
            RelationshipEntry relation = incoming.get(i);
            String sourceId = loadedIndex.getSymbol(relation.getFromId()) != null
                    ? relation.getFromId() : null;
            rows.add(new RelationshipRow(false, relation, sourceId));
        }
        return rows;
    }

    private void openSelectedRelationship() {
        RelationshipRow row = relationshipList.getSelectedValue();
        if (row == null) {
            setStatus("Select a relationship first.", true);
            return;
        }
        if (row.navigableSymbolId == null) {
            setStatus("That relationship target is a constant/type/value, not an exact Atlas symbol.", true);
            return;
        }
        AtlasInvestigationIndex loadedIndex = index;
        SymbolEntry symbol = loadedIndex == null ? null : loadedIndex.getSymbol(row.navigableSymbolId);
        if (symbol == null) {
            setStatus("Relationship symbol is not present in the current Atlas index.", true);
            return;
        }
        searchField.setText(symbol.getId());
        resultModel.clear();
        resultModel.addElement(new SearchRow(symbol));
        resultList.setSelectedIndex(0);
        selectSymbol(symbol);
    }

    private void showEvidence(EvidenceView view) {
        if (view == null) {
            evidenceFreshnessLabel.setText("No curated evidence");
            evidenceFreshnessLabel.setForeground(ConsoleTheme.MUTED_TEXT);
            evidenceWarningArea.setText("Create a note/claim below. Exact obfuscated ID remains primary.");
            evidenceStatus.setSelectedItem(EvidenceStatus.UNKNOWN);
            aliasField.setText("");
            claimArea.setText("");
            referencesArea.setText("");
            deleteEvidenceButton.setEnabled(false);
            setEvidenceEditorEnabled(true);
            deleteEvidenceButton.setEnabled(false);
            return;
        }

        EvidenceRecord record = view.getRecord();
        evidenceFreshnessLabel.setText(view.getFreshnessStatus());
        evidenceFreshnessLabel.setForeground(view.isCurrent()
                ? ConsoleTheme.ACCENT : new java.awt.Color(230, 171, 82));
        evidenceWarningArea.setText(view.getWarning() == null
                ? "Evidence matches the current Atlas fingerprint and exact subject."
                : view.getWarning());
        evidenceStatus.setSelectedItem(record.getStatus());
        aliasField.setText(record.getAlias() == null ? "" : record.getAlias());
        claimArea.setText(record.getClaim());
        referencesArea.setText(joinLines(record.getSupportingReferences()));
        setEvidenceEditorEnabled(true);
        deleteEvidenceButton.setEnabled(true);
    }

    private void saveEvidence() {
        final SymbolEntry symbol = selectedSymbol;
        final AtlasInvestigationIndex loadedIndex = index;
        final AtlasEvidenceStore store = evidenceStore;
        if (symbol == null || loadedIndex == null || store == null) {
            setStatus("Select an exact Atlas symbol first.", true);
            return;
        }

        final EvidenceStatus status = (EvidenceStatus) evidenceStatus.getSelectedItem();
        final String alias = aliasField.getText();
        final String claim = claimArea.getText() == null ? "" : claimArea.getText().trim();
        final List<String> references = splitReferences(referencesArea.getText());
        if (claim.length() == 0) {
            setStatus("Note / claim is required before saving evidence.", true);
            claimArea.requestFocusInWindow();
            return;
        }

        setEvidenceBusy(true);
        setStatus("Saving curated evidence...", false);
        new SwingWorker<EvidenceView, Void>() {
            private Exception failure;

            @Override
            protected EvidenceView doInBackground() {
                try {
                    store.upsert(loadedIndex, symbol.getId(), status, alias, claim, references);
                    return store.getView(symbol.getId(), loadedIndex);
                } catch (Exception ex) {
                    failure = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    if (failure != null) {
                        setStatus("Evidence save failed: " + failure.getMessage(), true);
                        return;
                    }
                    if (selectedSymbol == symbol) {
                        showEvidence(get());
                    }
                    setStatus("Evidence saved for " + symbol.getId(), false);
                } catch (Exception ex) {
                    setStatus("Evidence save failed: " + ex.getMessage(), true);
                } finally {
                    setEvidenceBusy(false);
                }
            }
        }.execute();
    }

    private void deleteEvidence() {
        final SymbolEntry symbol = selectedSymbol;
        final AtlasEvidenceStore store = evidenceStore;
        if (symbol == null || store == null) {
            setStatus("Select an exact Atlas symbol first.", true);
            return;
        }

        setEvidenceBusy(true);
        setStatus("Deleting curated evidence...", false);
        new SwingWorker<Boolean, Void>() {
            private Exception failure;

            @Override
            protected Boolean doInBackground() {
                try {
                    return Boolean.valueOf(store.delete(symbol.getId()));
                } catch (Exception ex) {
                    failure = ex;
                    return Boolean.FALSE;
                }
            }

            @Override
            protected void done() {
                try {
                    if (failure != null) {
                        setStatus("Evidence delete failed: " + failure.getMessage(), true);
                        return;
                    }
                    boolean removed = get().booleanValue();
                    if (selectedSymbol == symbol) {
                        showEvidence(null);
                    }
                    setStatus(removed ? "Evidence deleted for " + symbol.getId()
                            : "No evidence record existed for " + symbol.getId(), false);
                } catch (Exception ex) {
                    setStatus("Evidence delete failed: " + ex.getMessage(), true);
                } finally {
                    setEvidenceBusy(false);
                }
            }
        }.execute();
    }

    private void clearResults() {
        resultModel.clear();
        clearSelectionDetails();
    }

    private void clearSelectionDetails() {
        selectedSymbol = null;
        detailGeneration++;
        symbolText.setText("No exact symbol selected.");
        relationshipModel.clear();
        evidenceFreshnessLabel.setText("No symbol selected");
        evidenceFreshnessLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        evidenceWarningArea.setText("");
        aliasField.setText("");
        claimArea.setText("");
        referencesArea.setText("");
        evidenceStatus.setSelectedItem(EvidenceStatus.UNKNOWN);
        setEvidenceEditorEnabled(false);
    }

    private void setSearchBusy(boolean busy) {
        searchField.setEnabled(!busy);
        searchButton.setEnabled(!busy);
        reloadButton.setEnabled(!busy);
    }

    private void setEvidenceBusy(boolean busy) {
        if (busy) {
            evidenceStatus.setEnabled(false);
            aliasField.setEnabled(false);
            claimArea.setEnabled(false);
            referencesArea.setEnabled(false);
            saveEvidenceButton.setEnabled(false);
            deleteEvidenceButton.setEnabled(false);
        } else {
            setEvidenceEditorEnabled(selectedSymbol != null);
        }
    }

    private void setEvidenceEditorEnabled(boolean enabled) {
        evidenceStatus.setEnabled(enabled);
        aliasField.setEnabled(enabled);
        claimArea.setEnabled(enabled);
        referencesArea.setEnabled(enabled);
        saveEvidenceButton.setEnabled(enabled);
        if (!enabled) {
            deleteEvidenceButton.setEnabled(false);
        }
    }

    private void setStatus(String text, boolean error) {
        statusLabel.setText(text == null ? "" : text);
        statusLabel.setForeground(error ? new java.awt.Color(235, 120, 120) : ConsoleTheme.MUTED_TEXT);
    }

    private static JPanel createCard(String titleText) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(ConsoleTheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(12, 12, 12, 12)));
        card.setAlignmentX(LEFT_ALIGNMENT);

        JLabel title = new JLabel(titleText);
        title.setFont(ConsoleTheme.SECTION_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        card.add(title, BorderLayout.NORTH);
        return card;
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static JScrollPane wrap(JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        ConsoleTheme.styleScrollPane(scroll);
        scroll.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        return scroll;
    }

    private static JTextArea readOnlyArea(int rows, boolean wrap) {
        JTextArea area = new JTextArea(rows, 1);
        area.setEditable(false);
        area.setLineWrap(wrap);
        area.setWrapStyleWord(wrap);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setForeground(ConsoleTheme.TEXT);
        area.setBackground(ConsoleTheme.INPUT);
        area.setCaretColor(ConsoleTheme.TEXT);
        area.setBorder(ConsoleTheme.panelPadding(7, 8, 7, 8));
        return area;
    }

    private static JTextArea editableArea(int rows) {
        JTextArea area = new JTextArea(rows, 1);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(ConsoleTheme.BODY_FONT);
        area.setForeground(ConsoleTheme.TEXT);
        area.setBackground(ConsoleTheme.INPUT);
        area.setCaretColor(ConsoleTheme.TEXT);
        area.setSelectionColor(ConsoleTheme.ACCENT_DARK);
        area.setSelectedTextColor(ConsoleTheme.TEXT);
        area.setBorder(ConsoleTheme.panelPadding(7, 8, 7, 8));
        return area;
    }

    private static void styleEditableArea(JTextArea area) {
        area.setForeground(ConsoleTheme.TEXT);
        area.setBackground(ConsoleTheme.INPUT);
        area.setCaretColor(ConsoleTheme.TEXT);
        area.setSelectionColor(ConsoleTheme.ACCENT_DARK);
        area.setSelectedTextColor(ConsoleTheme.TEXT);
    }

    private static void styleCombo(JComboBox<EvidenceStatus> combo) {
        combo.setFont(ConsoleTheme.BODY_FONT);
        combo.setForeground(ConsoleTheme.TEXT);
        combo.setBackground(ConsoleTheme.INPUT);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        combo.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 2734522263889939695L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                label.setText(value instanceof EvidenceStatus
                        ? ((EvidenceStatus) value).getWireValue() : String.valueOf(value));
                label.setFont(ConsoleTheme.BODY_FONT);
                label.setForeground(ConsoleTheme.TEXT);
                label.setBackground(isSelected ? ConsoleTheme.ACCENT_DARK : ConsoleTheme.INPUT);
                return label;
            }
        });
    }

    private static void configureList(JList<?> list) {
        list.setFont(ConsoleTheme.SMALL_FONT);
        list.setForeground(ConsoleTheme.TEXT);
        list.setBackground(ConsoleTheme.INPUT);
        list.setSelectionForeground(ConsoleTheme.TEXT);
        list.setSelectionBackground(ConsoleTheme.ACCENT_DARK);
        list.setFixedCellHeight(30);
        list.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
    }

    private static String formatSymbol(SymbolEntry symbol) {
        StringBuilder out = new StringBuilder(512);
        out.append("ID: ").append(symbol.getId()).append('\n');
        out.append("Kind: ").append(symbol.getKind().name()).append('\n');
        out.append("Owner: ").append(symbol.getOwner()).append('\n');
        out.append("Name: ").append(symbol.getName()).append('\n');
        out.append("Descriptor: ").append(symbol.getDescriptor()).append('\n');
        if (symbol.getSignature() != null) {
            out.append("Signature: ").append(symbol.getSignature()).append('\n');
        }
        if (symbol.getSourcePath() != null) {
            out.append("Source: ").append(symbol.getSourcePath()).append('\n');
        }
        return out.toString();
    }

    private static String shortDisplay(SymbolEntry symbol) {
        String owner = symbol.getOwner();
        int slash = owner == null ? -1 : owner.lastIndexOf('/');
        String simpleOwner = slash >= 0 ? owner.substring(slash + 1) : owner;
        if (symbol.getKind().name().equals("CLASS")
                || symbol.getKind().name().equals("INTERFACE")
                || symbol.getKind().name().equals("ENUM")
                || symbol.getKind().name().equals("ANNOTATION")) {
            return simpleOwner;
        }
        return simpleOwner + "." + symbol.getName();
    }

    private static String compact(String value, int limit) {
        if (value == null) {
            return "";
        }
        String flattened = value.replace('\n', ' ').replace('\r', ' ');
        if (flattened.length() <= limit) {
            return flattened;
        }
        return flattened.substring(0, Math.max(0, limit - 3)) + "...";
    }

    private static String joinLines(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(values.get(i));
        }
        return out.toString();
    }

    private static List<String> splitReferences(String value) {
        if (value == null || value.trim().length() == 0) {
            return Collections.emptyList();
        }
        String[] lines = value.split("\\r?\\n");
        List<String> result = new ArrayList<String>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() > 0) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static final class SearchPayload {
        private final AtlasInvestigationIndex index;
        private final SearchResult result;

        private SearchPayload(AtlasInvestigationIndex index, SearchResult result) {
            this.index = index;
            this.result = result;
        }
    }

    private static final class DetailPayload {
        private final List<RelationshipRow> relationships;
        private final EvidenceView evidence;

        private DetailPayload(List<RelationshipRow> relationships, EvidenceView evidence) {
            this.relationships = relationships;
            this.evidence = evidence;
        }
    }

    private static final class SearchRow {
        private final SymbolEntry symbol;
        private final int score;
        private final String reason;

        private SearchRow(SearchMatch match) {
            this.symbol = match.getSymbol();
            this.score = match.getScore();
            this.reason = match.getReason();
        }

        private SearchRow(SymbolEntry symbol) {
            this.symbol = symbol;
            this.score = 1000;
            this.reason = "exact-navigation";
        }

        @Override
        public String toString() {
            return shortDisplay(symbol) + "  [" + symbol.getKind().name().toLowerCase(Locale.ROOT)
                    + ", " + score + ", " + reason + "]";
        }
    }

    private static final class RelationshipRow {
        private final boolean outgoing;
        private final RelationshipEntry relationship;
        private final String navigableSymbolId;

        private RelationshipRow(boolean outgoing, RelationshipEntry relationship,
                String navigableSymbolId) {
            this.outgoing = outgoing;
            this.relationship = relationship;
            this.navigableSymbolId = navigableSymbolId;
        }

        @Override
        public String toString() {
            String direction = outgoing ? "OUT" : "IN";
            String target = outgoing ? relationship.getTarget() : relationship.getFromId();
            return direction + " " + relationship.getType().name() + "  " + compact(target, 92);
        }
    }

    private static final class SearchRowRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 8424106149229024819L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            label.setText(String.valueOf(value));
            label.setFont(ConsoleTheme.SMALL_FONT);
            label.setForeground(ConsoleTheme.TEXT);
            label.setBackground(isSelected ? ConsoleTheme.ACCENT_DARK : ConsoleTheme.INPUT);
            label.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            return label;
        }
    }

    private static final class RelationshipRowRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 6761122856114144453L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            label.setText(String.valueOf(value));
            label.setFont(ConsoleTheme.SMALL_FONT);
            label.setForeground(ConsoleTheme.TEXT);
            label.setBackground(isSelected ? ConsoleTheme.ACCENT_DARK : ConsoleTheme.INPUT);
            label.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            if (value instanceof RelationshipRow
                    && ((RelationshipRow) value).navigableSymbolId == null
                    && !isSelected) {
                label.setForeground(ConsoleTheme.MUTED_TEXT);
            }
            return label;
        }
    }
}
