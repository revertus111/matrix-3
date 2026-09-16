package game.console.bosslabs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import game.console.ConsoleTheme;

/**
 * Client-local creator-facing authoring controls for BossLabs phase and attack
 * DRAFT data. Runtime ownership remains server-side.
 */
public final class BossLabsDefinitionEditor {

    public interface SelectionListener {
        void onSelectionChanged(BossLabsDraftDefinition.Phase phase, BossLabsDraftDefinition.Attack attack);
    }

    private static final String[] TILE_EFFECT_NAMES = {
            "Damage players", "Heal players", "Damage boss", "Heal boss" };
    private static final String[] PHASE_ACTION_NAMES = {
            "Play animation", "Play graphic", "Heal boss", "Spawn minions" };

    private final Runnable changeListener;
    private final SelectionListener selectionListener;

    private final JPanel phasesRoot = new JPanel(new BorderLayout(10, 10));
    private final JPanel attacksRoot = new JPanel(new BorderLayout(10, 10));

    private final DefaultListModel<BossLabsDraftDefinition.Phase> phaseListModel =
            new DefaultListModel<BossLabsDraftDefinition.Phase>();
    private final JList<BossLabsDraftDefinition.Phase> phaseList =
            new JList<BossLabsDraftDefinition.Phase>(phaseListModel);
    private final JTextField phaseNameField = new JTextField();
    private final JTextField phaseMinField = new JTextField();
    private final JTextField phaseMaxField = new JTextField();
    private final JTextField phaseIdField = new JTextField();
    private final JLabel phaseStatus = createStatus("Add a phase to begin authoring combat.");
    private final DefaultListModel<BossLabsDraftDefinition.PhaseAction> entryActionListModel =
            new DefaultListModel<BossLabsDraftDefinition.PhaseAction>();
    private final JList<BossLabsDraftDefinition.PhaseAction> entryActionList =
            new JList<BossLabsDraftDefinition.PhaseAction>(entryActionListModel);
    private final DefaultListModel<BossLabsDraftDefinition.PhaseAction> exitActionListModel =
            new DefaultListModel<BossLabsDraftDefinition.PhaseAction>();
    private final JList<BossLabsDraftDefinition.PhaseAction> exitActionList =
            new JList<BossLabsDraftDefinition.PhaseAction>(exitActionListModel);
    private final JComboBox<String> phaseActionTypeBox = new JComboBox<String>(PHASE_ACTION_NAMES);
    private final JTextField phaseActionValueField = new JTextField();
    private final JTextField phaseActionAmountField = new JTextField("1");
    private final JTextField phaseActionRadiusField = new JTextField("1");
    private final JLabel phaseActionStatus = createStatus("Transition actions are optional.");

    private final DefaultComboBoxModel<BossLabsDraftDefinition.Phase> attackPhaseModel =
            new DefaultComboBoxModel<BossLabsDraftDefinition.Phase>();
    private final JComboBox<BossLabsDraftDefinition.Phase> attackPhaseBox =
            new JComboBox<BossLabsDraftDefinition.Phase>(attackPhaseModel);
    private final DefaultListModel<BossLabsDraftDefinition.Attack> attackListModel =
            new DefaultListModel<BossLabsDraftDefinition.Attack>();
    private final JList<BossLabsDraftDefinition.Attack> attackList =
            new JList<BossLabsDraftDefinition.Attack>(attackListModel);
    private final JTextField attackNameField = new JTextField();
    private final JTextField attackIdField = new JTextField();
    private final JComboBox<String> attackStyleBox = new JComboBox<String>(new String[] { "Melee", "Range", "Magic" });
    private final JComboBox<String> targetModeBox = new JComboBox<String>(new String[] { "Current target", "Random nearby player" });
    private final JTextField targetRangeField = new JTextField();
    private final JTextField rotationWeightField = new JTextField();
    private final JTextField cooldownAttacksField = new JTextField();
    private final JComboBox<String> repeatModeBox = new JComboBox<String>(new String[] {
            "Allow immediate repeat", "Prefer another ready attack" });
    private final JTextField animationField = new JTextField();
    private final JTextField graphicField = new JTextField();
    private final JTextField projectileField = new JTextField();
    private final JComboBox<String> impactEffectBox = new JComboBox<String>(TILE_EFFECT_NAMES);
    private final JTextField maxHitField = new JTextField();
    private final JTextField combatDelayField = new JTextField();
    private final JTextField telegraphGraphicField = new JTextField();
    private final JTextField impactGraphicField = new JTextField();
    private final JTextField telegraphTicksField = new JTextField();
    private final JComboBox<String> hazardEffectBox = new JComboBox<String>(TILE_EFFECT_NAMES);
    private final JTextField hazardGraphicField = new JTextField();
    private final JTextField hazardDurationField = new JTextField();
    private final JTextField hazardIntervalField = new JTextField();
    private final JTextField hazardMaxHitField = new JTextField();
    private final JButton addAttackButton = new JButton("Add Attack");
    private final JLabel attackStatus = createStatus("Select a phase before adding attacks.");
    private final JLabel attackPatternSummary = createStatus("No attack selected.");

    private BossLabsDraftDefinition draft;
    private boolean suppressSelectionEvents;

    public BossLabsDefinitionEditor(Runnable changeListener, SelectionListener selectionListener) {
        this.changeListener = changeListener;
        this.selectionListener = selectionListener;
        installRenderers();
        buildPhasesPanel();
        buildAttacksPanel();
    }

    public JComponent getPhasesComponent() {
        return phasesRoot;
    }

    public JComponent getAttacksComponent() {
        return attacksRoot;
    }

    public BossLabsDraftDefinition getDraft() {
        return draft;
    }

    public void setDraft(BossLabsDraftDefinition draft) {
        this.draft = draft;
        refreshPhaseViews(0);
        refreshSelectedAttackSummary();
    }

    public void refreshSelectedAttackSummary() {
        updateAttackPatternSummary(attackList.getSelectedValue());
        attackList.repaint();
        notifySelectionChanged();
    }

    private void installRenderers() {
        DefaultListCellRenderer phaseRenderer = new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof BossLabsDraftDefinition.Phase)
                    setText(phaseLabel((BossLabsDraftDefinition.Phase) value));
                return this;
            }
        };
        phaseList.setCellRenderer(phaseRenderer);
        attackPhaseBox.setRenderer(phaseRenderer);

        attackList.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof BossLabsDraftDefinition.Attack)
                    setText(attackLabel((BossLabsDraftDefinition.Attack) value));
                return this;
            }
        });
    }

    private void buildPhasesPanel() {
        phasesRoot.setBackground(ConsoleTheme.PANEL);
        phasesRoot.setBorder(ConsoleTheme.panelPadding(12, 12, 12, 12));
        phasesRoot.add(verticalHeading("Phases",
                "Define when the boss changes behavior. BossLabs manages internal keys from the names you use."), BorderLayout.NORTH);

        phaseList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleList(phaseList);
        phaseList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !suppressSelectionEvents) {
                syncAttackPhaseToPhaseList();
                loadSelectedPhase();
                refreshAttackList(0);
                notifySelectionChanged();
            }
        });

        JPanel listCard = createListCard(phaseList, 260);
        JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        listButtons.setOpaque(false);
        JButton add = new JButton("+ Add Phase");
        JButton remove = new JButton("Remove");
        styleButton(add);
        styleButton(remove);
        add.addActionListener(e -> addPhase());
        remove.addActionListener(e -> removePhase());
        listButtons.add(add);
        listButtons.add(remove);
        listCard.add(listButtons, BorderLayout.SOUTH);
        phasesRoot.add(listCard, BorderLayout.WEST);

        JPanel editorColumn = new JPanel();
        editorColumn.setLayout(new BoxLayout(editorColumn, BoxLayout.Y_AXIS));
        editorColumn.setOpaque(false);

        JPanel setupCard = createCard();
        setupCard.add(verticalHeading("Phase setup",
                "Give the phase a useful name and HP range. The internal Phase ID is generated from the name."), BorderLayout.NORTH);
        JPanel setupForm = new JPanel(new GridBagLayout());
        setupForm.setOpaque(false);
        styleField(phaseNameField, "Creator-facing phase name, for example Enrage or Minion Phase");
        styleField(phaseMaxField, "Upper HP percentage for this phase, inclusive");
        styleField(phaseMinField, "Lower HP percentage for this phase, inclusive");
        addFormRow(setupForm, 0, "Phase name", phaseNameField);
        addFormRow(setupForm, 1, "Starts at HP %", phaseMaxField);
        addFormRow(setupForm, 2, "Ends at HP %", phaseMinField);

        JButton update = new JButton("Save Phase");
        JButton addAttack = new JButton("+ Add Attack to This Phase");
        styleButton(update);
        styleButton(addAttack);
        update.addActionListener(e -> updatePhase());
        addAttack.addActionListener(e -> addAttackToSelectedPhase());
        JPanel setupButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        setupButtons.setOpaque(false);
        setupButtons.add(update);
        setupButtons.add(addAttack);

        JPanel setupBody = new JPanel();
        setupBody.setLayout(new BoxLayout(setupBody, BoxLayout.Y_AXIS));
        setupBody.setOpaque(false);
        setupBody.add(setupForm);
        setupBody.add(Box.createVerticalStrut(8));
        setupBody.add(setupButtons);
        setupBody.add(Box.createVerticalStrut(6));
        setupBody.add(phaseStatus);
        setupCard.add(setupBody, BorderLayout.CENTER);

        editorColumn.add(setupCard);
        editorColumn.add(Box.createVerticalStrut(10));
        editorColumn.add(createCollapsibleSection("Transition actions", false, createPhaseActionsBody()));
        editorColumn.add(Box.createVerticalStrut(10));
        editorColumn.add(createCollapsibleSection("Advanced / internal", false, createPhaseAdvancedBody()));

        JScrollPane editorScroll = new JScrollPane(editorColumn);
        editorScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        editorScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        editorScroll.setBorder(BorderFactory.createEmptyBorder());
        ConsoleTheme.styleScrollPane(editorScroll);
        phasesRoot.add(editorScroll, BorderLayout.CENTER);
    }

    private JComponent createPhaseActionsBody() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        styleList(entryActionList);
        styleList(exitActionList);
        entryActionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        exitActionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel lists = new JPanel(new GridLayout(1, 2, 8, 0));
        lists.setOpaque(false);
        lists.add(createPhaseActionList("On Enter", entryActionList));
        lists.add(createPhaseActionList("On Exit", exitActionList));

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setOpaque(false);
        styleCombo(phaseActionTypeBox);
        styleField(phaseActionValueField, "Animation/GFX ID, heal amount, or minion NPC ID depending on the selected action");
        styleField(phaseActionAmountField, "Minion amount, 1-8");
        styleField(phaseActionRadiusField, "Minion spawn radius, 1-8 tiles");
        addFormRow(controls, 0, "Action", phaseActionTypeBox);
        addFormRow(controls, 1, "Value / NPC ID", phaseActionValueField);
        addFormRow(controls, 2, "Minion amount", phaseActionAmountField);
        addFormRow(controls, 3, "Minion radius", phaseActionRadiusField);
        phaseActionTypeBox.addActionListener(e -> updatePhaseActionInputs());
        updatePhaseActionInputs();

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);
        JButton addEnter = new JButton("+ On Enter");
        JButton addExit = new JButton("+ On Exit");
        JButton removeEnter = new JButton("Remove Enter");
        JButton removeExit = new JButton("Remove Exit");
        styleButton(addEnter);
        styleButton(addExit);
        styleButton(removeEnter);
        styleButton(removeExit);
        addEnter.addActionListener(e -> addPhaseAction(true));
        addExit.addActionListener(e -> addPhaseAction(false));
        removeEnter.addActionListener(e -> removePhaseAction(true));
        removeExit.addActionListener(e -> removePhaseAction(false));
        buttons.add(addEnter);
        buttons.add(addExit);
        buttons.add(removeEnter);
        buttons.add(removeExit);

        body.add(lists);
        body.add(Box.createVerticalStrut(8));
        body.add(controls);
        body.add(Box.createVerticalStrut(8));
        body.add(buttons);
        body.add(Box.createVerticalStrut(6));
        body.add(phaseActionStatus);
        return body;
    }

    private JComponent createPhaseAdvancedBody() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        styleReadOnlyField(phaseIdField, "Generated BossLabs Phase ID used by persistence/testing internals");
        addFormRow(form, 0, "Internal Phase ID", phaseIdField);
        return form;
    }

    private JPanel createPhaseActionList(String titleText, JList<BossLabsDraftDefinition.PhaseAction> list) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setOpaque(false);
        JLabel title = new JLabel(titleText);
        title.setFont(ConsoleTheme.BODY_FONT);
        title.setForeground(ConsoleTheme.MUTED_TEXT);
        panel.add(title, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(220, 120));
        ConsoleTheme.styleScrollPane(scroll);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void buildAttacksPanel() {
        attacksRoot.setBackground(ConsoleTheme.PANEL);
        attacksRoot.setBorder(ConsoleTheme.panelPadding(12, 12, 12, 12));

        JPanel top = verticalHeading("Attacks",
                "Start with the basics. Rotation, FX, area behavior, hazards, and internal details stay out of the way until you need them.");
        JPanel phaseChooser = new JPanel(new BorderLayout(8, 0));
        phaseChooser.setOpaque(false);
        JLabel phaseLabel = new JLabel("Editing phase");
        phaseLabel.setFont(ConsoleTheme.BODY_FONT);
        phaseLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        styleCombo(attackPhaseBox);
        attackPhaseBox.addActionListener(e -> {
            if (!suppressSelectionEvents) {
                syncPhaseListToAttackPhase();
                loadSelectedPhase();
                refreshAttackList(0);
                notifySelectionChanged();
            }
        });
        phaseChooser.add(phaseLabel, BorderLayout.WEST);
        phaseChooser.add(attackPhaseBox, BorderLayout.CENTER);
        top.add(Box.createVerticalStrut(8));
        top.add(phaseChooser);
        attacksRoot.add(top, BorderLayout.NORTH);

        attackList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleList(attackList);
        attackList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !suppressSelectionEvents) {
                loadSelectedAttack();
                notifySelectionChanged();
            }
        });

        JPanel listCard = createListCard(attackList, 285);
        JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        listButtons.setOpaque(false);
        styleButton(addAttackButton);
        JButton remove = new JButton("Remove");
        styleButton(remove);
        addAttackButton.addActionListener(e -> addAttack());
        remove.addActionListener(e -> removeAttack());
        listButtons.add(addAttackButton);
        listButtons.add(remove);
        listCard.add(listButtons, BorderLayout.SOUTH);
        attacksRoot.add(listCard, BorderLayout.WEST);

        JPanel editorColumn = new JPanel();
        editorColumn.setLayout(new BoxLayout(editorColumn, BoxLayout.Y_AXIS));
        editorColumn.setOpaque(false);

        editorColumn.add(createCollapsibleSection("Basic", true, createAttackBasicBody()));
        editorColumn.add(Box.createVerticalStrut(8));
        editorColumn.add(createCollapsibleSection("Rotation", false, createAttackRotationBody()));
        editorColumn.add(Box.createVerticalStrut(8));
        editorColumn.add(createCollapsibleSection("Animation & FX", false, createAttackPresentationBody()));
        editorColumn.add(Box.createVerticalStrut(8));
        editorColumn.add(createCollapsibleSection("Area / Telegraph", false, createAttackAreaBody()));
        editorColumn.add(Box.createVerticalStrut(8));
        editorColumn.add(createCollapsibleSection("Lingering Ground Effect", false, createAttackHazardBody()));
        editorColumn.add(Box.createVerticalStrut(8));
        editorColumn.add(createCollapsibleSection("Advanced / internal", false, createAttackAdvancedBody()));

        JScrollPane editorScroll = new JScrollPane(editorColumn);
        editorScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        editorScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        editorScroll.setBorder(BorderFactory.createEmptyBorder());
        ConsoleTheme.styleScrollPane(editorScroll);
        attacksRoot.add(editorScroll, BorderLayout.CENTER);
    }

    private JComponent createAttackBasicBody() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        styleField(attackNameField, "Creator-facing attack name, for example Meteor Strike");
        styleCombo(attackStyleBox);
        styleCombo(targetModeBox);
        styleField(targetRangeField, "Maximum range for Random nearby player targeting, 1-32 tiles");
        styleField(maxHitField, "Damage max hit, or -1 to use the NPC's normal style max hit");
        styleField(combatDelayField, "Attack delay override, or -1 to use the NPC default");
        addFormRow(form, 0, "Attack name", attackNameField);
        addFormRow(form, 1, "Style", attackStyleBox);
        addFormRow(form, 2, "Target", targetModeBox);
        addFormRow(form, 3, "Target range", targetRangeField);
        addFormRow(form, 4, "Damage / max hit", maxHitField);
        addFormRow(form, 5, "Attack delay", combatDelayField);

        JButton update = new JButton("Save Attack");
        styleButton(update);
        update.addActionListener(e -> updateAttack());

        JPanel patternCard = new JPanel(new BorderLayout(0, 4));
        patternCard.setOpaque(false);
        JLabel patternTitle = new JLabel("Attack Pattern");
        patternTitle.setFont(ConsoleTheme.BODY_FONT);
        patternTitle.setForeground(ConsoleTheme.TEXT);
        JLabel patternHint = new JLabel("Paint AoE/ground geometry in the Attack Pattern tab. This summary follows the selected attack.");
        patternHint.setFont(ConsoleTheme.SMALL_FONT);
        patternHint.setForeground(ConsoleTheme.MUTED_TEXT);
        patternCard.add(patternTitle, BorderLayout.NORTH);
        JPanel patternText = new JPanel();
        patternText.setLayout(new BoxLayout(patternText, BoxLayout.Y_AXIS));
        patternText.setOpaque(false);
        patternText.add(attackPatternSummary);
        patternText.add(Box.createVerticalStrut(3));
        patternText.add(patternHint);
        patternCard.add(patternText, BorderLayout.CENTER);

        body.add(form);
        body.add(Box.createVerticalStrut(8));
        body.add(update);
        body.add(Box.createVerticalStrut(8));
        body.add(patternCard);
        body.add(Box.createVerticalStrut(8));
        body.add(attackStatus);
        return body;
    }

    private JComponent createAttackRotationBody() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        styleField(rotationWeightField, "Relative weighted-selection chance, 1-1000");
        styleField(cooldownAttacksField, "Attack opportunities this attack sits out after use, 0-100");
        styleCombo(repeatModeBox);
        addFormRow(form, 0, "Weight", rotationWeightField);
        addFormRow(form, 1, "Cooldown attacks", cooldownAttacksField);
        addFormRow(form, 2, "Repeat rule", repeatModeBox);
        return form;
    }

    private JComponent createAttackPresentationBody() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        styleField(animationField, "Animation ID, or -1 for the NPC default");
        styleField(graphicField, "NPC graphic ID, or -1 for the NPC default");
        styleField(projectileField, "Projectile ID, or -1 for the NPC default");
        addFormRow(form, 0, "Animation", animationField);
        addFormRow(form, 1, "NPC graphic", graphicField);
        addFormRow(form, 2, "Projectile", projectileField);
        return form;
    }

    private JComponent createAttackAreaBody() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        styleCombo(impactEffectBox);
        styleField(telegraphGraphicField, "Ground warning GFX, or -1 for none");
        styleField(impactGraphicField, "Ground impact GFX, or -1 for none");
        styleField(telegraphTicksField, "Ticks between warning and impact, 0-50");
        addFormRow(form, 0, "Impact effect", impactEffectBox);
        addFormRow(form, 1, "Warning GFX", telegraphGraphicField);
        addFormRow(form, 2, "Impact GFX", impactGraphicField);
        addFormRow(form, 3, "Warning ticks", telegraphTicksField);
        return form;
    }

    private JComponent createAttackHazardBody() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        styleCombo(hazardEffectBox);
        styleField(hazardGraphicField, "Lingering ground GFX, or -1 for none");
        styleField(hazardDurationField, "Duration in ticks; 0 disables the lingering effect");
        styleField(hazardIntervalField, "Effect interval in ticks, 1-50");
        styleField(hazardMaxHitField, "Hazard amount/max hit, or -1 for NPC style max hit");
        addFormRow(form, 0, "Ground effect", hazardEffectBox);
        addFormRow(form, 1, "Ground GFX", hazardGraphicField);
        addFormRow(form, 2, "Duration", hazardDurationField);
        addFormRow(form, 3, "Tick interval", hazardIntervalField);
        addFormRow(form, 4, "Amount / max hit", hazardMaxHitField);
        return form;
    }

    private JComponent createAttackAdvancedBody() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        styleReadOnlyField(attackIdField, "Generated BossLabs Attack ID used by persistence/testing internals");
        addFormRow(form, 0, "Internal Attack ID", attackIdField);
        return form;
    }

    private void addPhase() {
        if (draft == null)
            return;
        String id = nextPhaseId();
        BossLabsDraftDefinition.Phase phase = new BossLabsDraftDefinition.Phase(id, 0, 100);
        draft.getPhases().add(phase);
        refreshPhaseViews(draft.getPhases().size() - 1);
        phaseStatus.setText("Phase added. Give it a useful name and HP range, then add an attack.");
        changed();
    }

    private void removePhase() {
        int index = phaseList.getSelectedIndex();
        if (draft == null || index < 0 || index >= draft.getPhases().size())
            return;
        draft.getPhases().remove(index);
        refreshPhaseViews(Math.max(0, index - 1));
        phaseStatus.setText("Phase removed from local draft.");
        changed();
    }

    private void updatePhase() {
        BossLabsDraftDefinition.Phase phase = phaseList.getSelectedValue();
        if (phase == null) {
            phaseStatus.setText("Select a phase first.");
            return;
        }
        Integer minimum = parseInteger(phaseMinField.getText());
        Integer maximum = parseInteger(phaseMaxField.getText());
        if (minimum == null || maximum == null) {
            phaseStatus.setText("Phase HP values must be whole numbers.");
            return;
        }
        String creatorName = trim(phaseNameField.getText());
        if (creatorName.length() == 0) {
            phaseStatus.setText("Give the phase a name.");
            return;
        }
        String candidate = creatorName.equalsIgnoreCase(creatorLabel(phase.getId()))
                ? phase.getId() : slugify(creatorName);
        if (candidate.length() == 0) {
            phaseStatus.setText("Phase name must contain at least one letter or number.");
            return;
        }
        if (phaseIdExists(candidate, phase)) {
            phaseStatus.setText("Another phase already uses that name. Choose a different phase name.");
            return;
        }
        if (minimum.intValue() < 0 || minimum.intValue() > 100 || maximum.intValue() < 0 || maximum.intValue() > 100
                || minimum.intValue() > maximum.intValue()) {
            phaseStatus.setText("Phase HP must stay between 0 and 100, with Ends at HP no higher than Starts at HP.");
            return;
        }

        phase.setId(candidate);
        phase.setMinimumHealthPercent(minimum.intValue());
        phase.setMaximumHealthPercent(maximum.intValue());
        refreshPhaseViews(phaseList.getSelectedIndex());
        phaseStatus.setText("Phase saved in local draft.");
        changed();
    }

    private void loadSelectedPhase() {
        BossLabsDraftDefinition.Phase phase = phaseList.getSelectedValue();
        if (phase == null) {
            phaseNameField.setText("");
            phaseMinField.setText("");
            phaseMaxField.setText("");
            phaseIdField.setText("");
            refreshPhaseActionLists();
            notifySelectionChanged();
            return;
        }
        phaseNameField.setText(creatorLabel(phase.getId()));
        phaseMinField.setText(Integer.toString(phase.getMinimumHealthPercent()));
        phaseMaxField.setText(Integer.toString(phase.getMaximumHealthPercent()));
        phaseIdField.setText(phase.getId());
        refreshPhaseActionLists();
        notifySelectionChanged();
    }

    private void refreshPhaseActionLists() {
        entryActionListModel.clear();
        exitActionListModel.clear();
        BossLabsDraftDefinition.Phase phase = phaseList.getSelectedValue();
        if (phase == null)
            return;
        for (BossLabsDraftDefinition.PhaseAction action : phase.getEntryActions())
            entryActionListModel.addElement(action);
        for (BossLabsDraftDefinition.PhaseAction action : phase.getExitActions())
            exitActionListModel.addElement(action);
    }

    private void updatePhaseActionInputs() {
        boolean minions = phaseActionTypeBox.getSelectedIndex() == BossLabsDraftDefinition.PHASE_ACTION_SPAWN_MINIONS;
        phaseActionAmountField.setEnabled(minions);
        phaseActionRadiusField.setEnabled(minions);
        if (!minions) {
            phaseActionAmountField.setText("1");
            phaseActionRadiusField.setText("1");
        }
    }

    private void addPhaseAction(boolean entry) {
        BossLabsDraftDefinition.Phase phase = phaseList.getSelectedValue();
        if (phase == null) {
            phaseActionStatus.setText("Select a phase first.");
            return;
        }

        int type = phaseActionTypeBox.getSelectedIndex();
        Integer value = parseInteger(phaseActionValueField.getText());
        if (value == null) {
            phaseActionStatus.setText(type == BossLabsDraftDefinition.PHASE_ACTION_SPAWN_MINIONS
                    ? "Enter the minion NPC ID as a whole number."
                    : "Enter a whole-number value for this action.");
            return;
        }

        BossLabsDraftDefinition.PhaseAction action;
        if (type == BossLabsDraftDefinition.PHASE_ACTION_SPAWN_MINIONS) {
            Integer amount = parseInteger(phaseActionAmountField.getText());
            Integer radius = parseInteger(phaseActionRadiusField.getText());
            if (amount == null || radius == null) {
                phaseActionStatus.setText("Minion amount and radius must be whole numbers.");
                return;
            }
            action = new BossLabsDraftDefinition.PhaseAction(type, value.intValue(), amount.intValue(), radius.intValue());
        } else {
            action = new BossLabsDraftDefinition.PhaseAction(type, value.intValue());
        }

        if (entry)
            phase.getEntryActions().add(action);
        else
            phase.getExitActions().add(action);
        refreshPhaseActionLists();
        phaseList.repaint();
        phaseActionStatus.setText((entry ? "On-enter" : "On-exit") + " action added.");
        changed();
    }

    private void removePhaseAction(boolean entry) {
        BossLabsDraftDefinition.Phase phase = phaseList.getSelectedValue();
        if (phase == null)
            return;
        int index = entry ? entryActionList.getSelectedIndex() : exitActionList.getSelectedIndex();
        if (index < 0) {
            phaseActionStatus.setText("Select an " + (entry ? "on-enter" : "on-exit") + " action first.");
            return;
        }
        if (entry)
            phase.getEntryActions().remove(index);
        else
            phase.getExitActions().remove(index);
        refreshPhaseActionLists();
        phaseList.repaint();
        phaseActionStatus.setText((entry ? "On-enter" : "On-exit") + " action removed.");
        changed();
    }

    private void refreshPhaseViews(int preferredIndex) {
        suppressSelectionEvents = true;
        try {
            phaseListModel.clear();
            attackPhaseModel.removeAllElements();
            if (draft != null) {
                for (BossLabsDraftDefinition.Phase phase : draft.getPhases()) {
                    phaseListModel.addElement(phase);
                    attackPhaseModel.addElement(phase);
                }
            }
            if (!phaseListModel.isEmpty()) {
                int index = Math.max(0, Math.min(preferredIndex, phaseListModel.size() - 1));
                phaseList.setSelectedIndex(index);
                attackPhaseBox.setSelectedIndex(index);
            }
        } finally {
            suppressSelectionEvents = false;
        }
        loadSelectedPhase();
        refreshAttackList(0);
        addAttackButton.setEnabled(draft != null && !draft.getPhases().isEmpty());
        if (draft == null || draft.getPhases().isEmpty())
            attackStatus.setText("Add a phase before adding attacks.");
    }

    private void syncAttackPhaseToPhaseList() {
        int index = phaseList.getSelectedIndex();
        if (index < 0 || index >= attackPhaseModel.getSize())
            return;
        suppressSelectionEvents = true;
        try {
            attackPhaseBox.setSelectedIndex(index);
        } finally {
            suppressSelectionEvents = false;
        }
    }

    private void syncPhaseListToAttackPhase() {
        int index = attackPhaseBox.getSelectedIndex();
        if (index < 0 || index >= phaseListModel.getSize())
            return;
        suppressSelectionEvents = true;
        try {
            phaseList.setSelectedIndex(index);
        } finally {
            suppressSelectionEvents = false;
        }
    }

    private BossLabsDraftDefinition.Phase selectedAttackPhase() {
        Object selected = attackPhaseBox.getSelectedItem();
        return selected instanceof BossLabsDraftDefinition.Phase ? (BossLabsDraftDefinition.Phase) selected : null;
    }

    private void addAttackToSelectedPhase() {
        BossLabsDraftDefinition.Phase phase = phaseList.getSelectedValue();
        if (phase == null) {
            phaseStatus.setText("Select a phase first.");
            return;
        }
        syncAttackPhaseToPhaseList();
        addAttack();
        phaseStatus.setText("Attack added to " + creatorLabel(phase.getId()) + ". Open Attacks to configure it.");
    }

    private void addAttack() {
        BossLabsDraftDefinition.Phase phase = selectedAttackPhase();
        if (phase == null) {
            attackStatus.setText("Add or select a phase first.");
            return;
        }
        String id = nextAttackId(phase);
        phase.getAttacks().add(new BossLabsDraftDefinition.Attack(id, 0, -1, -1, -1, -1, -1));
        refreshAttackList(phase.getAttacks().size() - 1);
        attackStatus.setText("Attack added with safe defaults. Configure only what this attack needs.");
        changed();
    }

    private void removeAttack() {
        BossLabsDraftDefinition.Phase phase = selectedAttackPhase();
        int index = attackList.getSelectedIndex();
        if (phase == null || index < 0 || index >= phase.getAttacks().size())
            return;
        phase.getAttacks().remove(index);
        refreshAttackList(Math.max(0, index - 1));
        attackStatus.setText("Attack removed from local draft.");
        changed();
    }

    private void updateAttack() {
        BossLabsDraftDefinition.Phase phase = selectedAttackPhase();
        BossLabsDraftDefinition.Attack attack = attackList.getSelectedValue();
        if (phase == null || attack == null) {
            attackStatus.setText("Select an attack first.");
            return;
        }

        Integer targetRange = parseInteger(targetRangeField.getText());
        Integer rotationWeight = parseInteger(rotationWeightField.getText());
        Integer cooldownAttacks = parseInteger(cooldownAttacksField.getText());
        Integer animation = parseInteger(animationField.getText());
        Integer graphic = parseInteger(graphicField.getText());
        Integer projectile = parseInteger(projectileField.getText());
        Integer maxHit = parseInteger(maxHitField.getText());
        Integer combatDelay = parseInteger(combatDelayField.getText());
        Integer telegraphGraphic = parseInteger(telegraphGraphicField.getText());
        Integer impactGraphic = parseInteger(impactGraphicField.getText());
        Integer telegraphTicks = parseInteger(telegraphTicksField.getText());
        Integer hazardGraphic = parseInteger(hazardGraphicField.getText());
        Integer hazardDuration = parseInteger(hazardDurationField.getText());
        Integer hazardInterval = parseInteger(hazardIntervalField.getText());
        Integer hazardMaxHit = parseInteger(hazardMaxHitField.getText());
        if (targetRange == null || rotationWeight == null || cooldownAttacks == null || animation == null
                || graphic == null || projectile == null || maxHit == null || combatDelay == null
                || telegraphGraphic == null || impactGraphic == null || telegraphTicks == null
                || hazardGraphic == null || hazardDuration == null || hazardInterval == null || hazardMaxHit == null) {
            attackStatus.setText("One of the attack number fields is invalid. Use whole numbers only.");
            return;
        }

        String creatorName = trim(attackNameField.getText());
        if (creatorName.length() == 0) {
            attackStatus.setText("Give the attack a name.");
            return;
        }
        String candidate = creatorName.equalsIgnoreCase(creatorLabel(attack.getId()))
                ? attack.getId() : slugify(creatorName);
        if (candidate.length() == 0) {
            attackStatus.setText("Attack name must contain at least one letter or number.");
            return;
        }
        if (attackIdExists(phase, candidate, attack)) {
            attackStatus.setText("Another attack in this phase already uses that name.");
            return;
        }

        attack.setId(candidate);
        attack.setCombatStyle(attackStyleBox.getSelectedIndex());
        attack.setTargetMode(targetModeBox.getSelectedIndex());
        attack.setTargetRange(targetRange.intValue());
        attack.setRotationWeight(rotationWeight.intValue());
        attack.setCooldownAttacks(cooldownAttacks.intValue());
        attack.setImmediateRepeatAllowed(repeatModeBox.getSelectedIndex() == 0);
        attack.setAnimationId(animation.intValue());
        attack.setGraphicId(graphic.intValue());
        attack.setProjectileId(projectile.intValue());
        attack.setImpactTileEffectType(impactEffectBox.getSelectedIndex());
        attack.setMaxHitOverride(maxHit.intValue());
        attack.setCombatDelayOverride(combatDelay.intValue());
        attack.setTelegraphGraphicId(telegraphGraphic.intValue());
        attack.setImpactGraphicId(impactGraphic.intValue());
        attack.setTelegraphTicks(telegraphTicks.intValue());
        attack.setHazardTileEffectType(hazardEffectBox.getSelectedIndex());
        attack.setHazardGraphicId(hazardGraphic.intValue());
        attack.setHazardDurationTicks(hazardDuration.intValue());
        attack.setHazardTickInterval(hazardInterval.intValue());
        attack.setHazardMaxHitOverride(hazardMaxHit.intValue());
        attackIdField.setText(attack.getId());
        attackList.repaint();
        updateAttackPatternSummary(attack);
        attackStatus.setText("Attack saved in local draft.");
        changed();
        notifySelectionChanged();
    }

    private void refreshAttackList(int preferredIndex) {
        suppressSelectionEvents = true;
        try {
            attackListModel.clear();
            BossLabsDraftDefinition.Phase phase = selectedAttackPhase();
            if (phase != null) {
                for (BossLabsDraftDefinition.Attack attack : phase.getAttacks())
                    attackListModel.addElement(attack);
            }
            if (!attackListModel.isEmpty()) {
                int index = Math.max(0, Math.min(preferredIndex, attackListModel.size() - 1));
                attackList.setSelectedIndex(index);
            }
        } finally {
            suppressSelectionEvents = false;
        }
        loadSelectedAttack();
    }

    private void loadSelectedAttack() {
        BossLabsDraftDefinition.Attack attack = attackList.getSelectedValue();
        if (attack == null) {
            attackNameField.setText("");
            attackIdField.setText("");
            attackStyleBox.setSelectedIndex(0);
            targetModeBox.setSelectedIndex(BossLabsDraftDefinition.TARGET_CURRENT);
            targetRangeField.setText("14");
            rotationWeightField.setText("1");
            cooldownAttacksField.setText("0");
            repeatModeBox.setSelectedIndex(0);
            animationField.setText("-1");
            graphicField.setText("-1");
            projectileField.setText("-1");
            impactEffectBox.setSelectedIndex(BossLabsDraftDefinition.TILE_EFFECT_DAMAGE_PLAYERS);
            maxHitField.setText("-1");
            combatDelayField.setText("-1");
            telegraphGraphicField.setText("-1");
            impactGraphicField.setText("-1");
            telegraphTicksField.setText("0");
            hazardEffectBox.setSelectedIndex(BossLabsDraftDefinition.TILE_EFFECT_DAMAGE_PLAYERS);
            hazardGraphicField.setText("-1");
            hazardDurationField.setText("0");
            hazardIntervalField.setText("1");
            hazardMaxHitField.setText("-1");
            updateAttackPatternSummary(null);
            notifySelectionChanged();
            return;
        }
        attackNameField.setText(creatorLabel(attack.getId()));
        attackIdField.setText(attack.getId());
        attackStyleBox.setSelectedIndex(Math.max(0, Math.min(2, attack.getCombatStyle())));
        targetModeBox.setSelectedIndex(Math.max(0, Math.min(1, attack.getTargetMode())));
        targetRangeField.setText(Integer.toString(attack.getTargetRange()));
        rotationWeightField.setText(Integer.toString(attack.getRotationWeight()));
        cooldownAttacksField.setText(Integer.toString(attack.getCooldownAttacks()));
        repeatModeBox.setSelectedIndex(attack.isImmediateRepeatAllowed() ? 0 : 1);
        animationField.setText(Integer.toString(attack.getAnimationId()));
        graphicField.setText(Integer.toString(attack.getGraphicId()));
        projectileField.setText(Integer.toString(attack.getProjectileId()));
        impactEffectBox.setSelectedIndex(Math.max(0, Math.min(3, attack.getImpactTileEffectType())));
        maxHitField.setText(Integer.toString(attack.getMaxHitOverride()));
        combatDelayField.setText(Integer.toString(attack.getCombatDelayOverride()));
        telegraphGraphicField.setText(Integer.toString(attack.getTelegraphGraphicId()));
        impactGraphicField.setText(Integer.toString(attack.getImpactGraphicId()));
        telegraphTicksField.setText(Integer.toString(attack.getTelegraphTicks()));
        hazardEffectBox.setSelectedIndex(Math.max(0, Math.min(3, attack.getHazardTileEffectType())));
        hazardGraphicField.setText(Integer.toString(attack.getHazardGraphicId()));
        hazardDurationField.setText(Integer.toString(attack.getHazardDurationTicks()));
        hazardIntervalField.setText(Integer.toString(attack.getHazardTickInterval()));
        hazardMaxHitField.setText(Integer.toString(attack.getHazardMaxHitOverride()));
        updateAttackPatternSummary(attack);
        notifySelectionChanged();
    }

    private void updateAttackPatternSummary(BossLabsDraftDefinition.Attack attack) {
        if (attack == null) {
            attackPatternSummary.setText("No attack selected.");
            return;
        }
        if (attack.getTilePattern().isEmpty()) {
            attackPatternSummary.setText(attack.getHazardDurationTicks() > 0
                    ? "No tiles painted - disable the ground effect or paint a pattern before publishing."
                    : "Single target - no painted pattern.");
            return;
        }
        String text = attack.getTilePattern().size() + " painted tile(s)";
        if (attack.getTelegraphTicks() > 0)
            text += " • warning " + attack.getTelegraphTicks() + "t";
        if (attack.getHazardDurationTicks() > 0)
            text += " • ground effect " + attack.getHazardDurationTicks() + "t";
        attackPatternSummary.setText(text);
    }

    private String nextPhaseId() {
        int number = draft == null ? 1 : draft.getPhases().size() + 1;
        String candidate;
        do {
            candidate = "phase_" + number++;
        } while (phaseIdExists(candidate, null));
        return candidate;
    }

    private String nextAttackId(BossLabsDraftDefinition.Phase phase) {
        int number = phase == null ? 1 : phase.getAttacks().size() + 1;
        String candidate;
        do {
            candidate = "attack_" + number++;
        } while (attackIdExists(phase, candidate, null));
        return candidate;
    }

    private boolean phaseIdExists(String id, BossLabsDraftDefinition.Phase ignore) {
        if (draft == null)
            return false;
        for (BossLabsDraftDefinition.Phase phase : draft.getPhases()) {
            if (phase != ignore && phase.getId().equalsIgnoreCase(id))
                return true;
        }
        return false;
    }

    private boolean attackIdExists(BossLabsDraftDefinition.Phase phase, String id, BossLabsDraftDefinition.Attack ignore) {
        if (phase == null)
            return false;
        for (BossLabsDraftDefinition.Attack attack : phase.getAttacks()) {
            if (attack != ignore && attack.getId().equalsIgnoreCase(id))
                return true;
        }
        return false;
    }

    private void notifySelectionChanged() {
        if (selectionListener == null)
            return;
        BossLabsDraftDefinition.Phase phase = selectedAttackPhase();
        if (phase == null)
            phase = phaseList.getSelectedValue();
        BossLabsDraftDefinition.Attack attack = attackList.getSelectedValue();
        selectionListener.onSelectionChanged(phase, attack);
    }

    private String phaseLabel(BossLabsDraftDefinition.Phase phase) {
        if (phase == null)
            return "No phase";
        return creatorLabel(phase.getId()) + "  •  " + phase.getMaximumHealthPercent() + "% → "
                + phase.getMinimumHealthPercent() + "% HP  •  " + phase.getAttacks().size() + " attack"
                + (phase.getAttacks().size() == 1 ? "" : "s");
    }

    private String attackLabel(BossLabsDraftDefinition.Attack attack) {
        if (attack == null)
            return "No attack";
        String text = creatorLabel(attack.getId()) + "  •  " + BossLabsDraftDefinition.styleName(attack.getCombatStyle());
        if (attack.getTargetMode() == BossLabsDraftDefinition.TARGET_RANDOM_NEARBY_PLAYER)
            text += "  •  random target";
        if (attack.getCooldownAttacks() > 0)
            text += "  •  cd " + attack.getCooldownAttacks();
        if (!attack.getTilePattern().isEmpty())
            text += "  •  AoE " + attack.getTilePattern().size();
        if (attack.getHazardDurationTicks() > 0)
            text += "  •  ground " + attack.getHazardDurationTicks() + "t";
        return text;
    }

    private String creatorLabel(String id) {
        String value = trim(id).replace('_', ' ').replace('-', ' ');
        if (value.length() == 0)
            return "Unnamed";
        StringBuilder result = new StringBuilder(value.length());
        boolean capitalize = true;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isWhitespace(character)) {
                if (result.length() > 0 && result.charAt(result.length() - 1) != ' ')
                    result.append(' ');
                capitalize = true;
            } else {
                result.append(capitalize ? Character.toUpperCase(character) : character);
                capitalize = false;
            }
        }
        return result.toString();
    }

    private String slugify(String value) {
        String source = trim(value).toLowerCase();
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

    private JPanel verticalHeading(String titleText, String subtitleText) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        JLabel title = new JLabel(titleText);
        title.setFont(ConsoleTheme.SECTION_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("<html>" + escapeHtml(subtitleText) + "</html>");
        subtitle.setFont(ConsoleTheme.SMALL_FONT);
        subtitle.setForeground(ConsoleTheme.MUTED_TEXT);
        subtitle.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(3));
        panel.add(subtitle);
        return panel;
    }

    private JPanel createListCard(JList<?> list, int width) {
        JPanel card = createCard();
        card.setPreferredSize(new Dimension(width, 420));
        JScrollPane scroll = new JScrollPane(list);
        ConsoleTheme.styleScrollPane(scroll);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel createCard() {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(ConsoleTheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(10, 10, 10, 10)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return card;
    }

    private JPanel createCollapsibleSection(String title, boolean expanded, JComponent body) {
        final JPanel card = createCard();
        final JButton toggle = new JButton();
        final boolean[] open = new boolean[] { expanded };
        styleButton(toggle);
        toggle.setHorizontalAlignment(JButton.LEFT);
        final JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(body, BorderLayout.CENTER);
        content.setVisible(expanded);

        Runnable refresh = new Runnable() {
            @Override
            public void run() {
                toggle.setText((open[0] ? "▼ " : "▶ ") + title);
                content.setVisible(open[0]);
                card.revalidate();
                card.repaint();
            }
        };
        toggle.addActionListener(e -> {
            open[0] = !open[0];
            refresh.run();
        });
        refresh.run();
        card.add(toggle, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private void styleList(JList<?> list) {
        list.setBackground(ConsoleTheme.INPUT);
        list.setForeground(ConsoleTheme.TEXT);
        list.setFont(ConsoleTheme.BODY_FONT);
        list.setFixedCellHeight(32);
    }

    private void styleField(JTextField field, String tooltip) {
        field.setToolTipText(tooltip);
        field.setPreferredSize(new Dimension(220, 34));
        ConsoleTheme.styleTextField(field);
    }

    private void styleReadOnlyField(JTextField field, String tooltip) {
        styleField(field, tooltip);
        field.setEditable(false);
        field.setFocusable(false);
    }

    private void styleCombo(JComboBox<?> combo) {
        combo.setFont(ConsoleTheme.BODY_FONT);
        combo.setForeground(ConsoleTheme.TEXT);
        combo.setBackground(ConsoleTheme.INPUT);
        combo.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
    }

    private void styleButton(JButton button) {
        ConsoleTheme.styleButton(button);
    }

    private void addFormRow(JPanel panel, int row, String labelText, JComponent component) {
        JLabel label = new JLabel(labelText);
        label.setFont(ConsoleTheme.BODY_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(4, 0, 4, 10);
        panel.add(label, labelConstraints);

        GridBagConstraints componentConstraints = new GridBagConstraints();
        componentConstraints.gridx = 1;
        componentConstraints.gridy = row;
        componentConstraints.weightx = 1.0;
        componentConstraints.fill = GridBagConstraints.HORIZONTAL;
        componentConstraints.insets = new Insets(4, 0, 4, 0);
        panel.add(component, componentConstraints);
    }

    private static JLabel createStatus(String text) {
        JLabel label = new JLabel("<html>" + escapeHtmlStatic(text) + "</html>");
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        return label;
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.valueOf(trim(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private void changed() {
        phaseList.repaint();
        attackList.repaint();
        if (changeListener != null)
            changeListener.run();
    }

    private String escapeHtml(String value) {
        return escapeHtmlStatic(value);
    }

    private static String escapeHtmlStatic(String value) {
        String safe = value == null ? "" : value;
        return safe.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
