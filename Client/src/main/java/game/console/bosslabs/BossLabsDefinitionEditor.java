package game.console.bosslabs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
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
 * Client-local authoring controls for BossLabs phase and attack DRAFT data.
 */
public final class BossLabsDefinitionEditor {

    private final Runnable changeListener;

    private final JPanel phasesRoot = new JPanel(new BorderLayout(10, 10));
    private final JPanel attacksRoot = new JPanel(new BorderLayout(10, 10));

    private final DefaultListModel<BossLabsDraftDefinition.Phase> phaseListModel =
            new DefaultListModel<BossLabsDraftDefinition.Phase>();
    private final JList<BossLabsDraftDefinition.Phase> phaseList =
            new JList<BossLabsDraftDefinition.Phase>(phaseListModel);
    private final JTextField phaseIdField = new JTextField();
    private final JTextField phaseMinField = new JTextField();
    private final JTextField phaseMaxField = new JTextField();
    private final JLabel phaseStatus = createStatus("Add a phase to begin authoring combat.");

    private final DefaultComboBoxModel<BossLabsDraftDefinition.Phase> attackPhaseModel =
            new DefaultComboBoxModel<BossLabsDraftDefinition.Phase>();
    private final JComboBox<BossLabsDraftDefinition.Phase> attackPhaseBox =
            new JComboBox<BossLabsDraftDefinition.Phase>(attackPhaseModel);
    private final DefaultListModel<BossLabsDraftDefinition.Attack> attackListModel =
            new DefaultListModel<BossLabsDraftDefinition.Attack>();
    private final JList<BossLabsDraftDefinition.Attack> attackList =
            new JList<BossLabsDraftDefinition.Attack>(attackListModel);
    private final JTextField attackIdField = new JTextField();
    private final JComboBox<String> attackStyleBox = new JComboBox<String>(new String[] { "Melee", "Range", "Magic" });
    private final JTextField animationField = new JTextField();
    private final JTextField graphicField = new JTextField();
    private final JTextField projectileField = new JTextField();
    private final JTextField maxHitField = new JTextField();
    private final JTextField combatDelayField = new JTextField();
    private final JButton addAttackButton = new JButton("Add Attack");
    private final JLabel attackStatus = createStatus("Select a phase before adding attacks.");

    private BossLabsDraftDefinition draft;
    private boolean suppressSelectionEvents;

    public BossLabsDefinitionEditor(Runnable changeListener) {
        this.changeListener = changeListener;
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
    }

    private void buildPhasesPanel() {
        phasesRoot.setBackground(ConsoleTheme.PANEL);
        phasesRoot.setBorder(ConsoleTheme.panelPadding(12, 12, 12, 12));

        JPanel heading = verticalHeading("Phases", "Health-range phases. Each phase must contain at least one attack.");
        phasesRoot.add(heading, BorderLayout.NORTH);

        phaseList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleList(phaseList);
        phaseList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !suppressSelectionEvents)
                loadSelectedPhase();
        });

        JPanel listCard = createListCard(phaseList, 235);
        JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        listButtons.setOpaque(false);
        JButton add = new JButton("Add Phase");
        JButton remove = new JButton("Remove");
        styleButton(add);
        styleButton(remove);
        add.addActionListener(e -> addPhase());
        remove.addActionListener(e -> removePhase());
        listButtons.add(add);
        listButtons.add(remove);
        listCard.add(listButtons, BorderLayout.SOUTH);
        phasesRoot.add(listCard, BorderLayout.WEST);

        JPanel formCard = createCard();
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        styleField(phaseIdField, "Stable phase id, for example phase_1");
        styleField(phaseMinField, "Minimum HP percent, inclusive");
        styleField(phaseMaxField, "Maximum HP percent, inclusive");
        addFormRow(form, 0, "Phase ID", phaseIdField);
        addFormRow(form, 1, "Min HP %", phaseMinField);
        addFormRow(form, 2, "Max HP %", phaseMaxField);

        JButton update = new JButton("Update Phase");
        styleButton(update);
        update.addActionListener(e -> updatePhase());

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        form.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        update.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        phaseStatus.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        body.add(form);
        body.add(Box.createVerticalStrut(10));
        body.add(update);
        body.add(Box.createVerticalStrut(8));
        body.add(phaseStatus);
        formCard.add(body, BorderLayout.CENTER);
        phasesRoot.add(formCard, BorderLayout.CENTER);
    }

    private void buildAttacksPanel() {
        attacksRoot.setBackground(ConsoleTheme.PANEL);
        attacksRoot.setBorder(ConsoleTheme.panelPadding(12, 12, 12, 12));

        JPanel top = verticalHeading("Attacks", "Edit the attacks available inside one phase. -1 uses the NPC default where supported.");

        JPanel phaseChooser = new JPanel(new BorderLayout(8, 0));
        phaseChooser.setOpaque(false);
        JLabel phaseLabel = new JLabel("Phase");
        phaseLabel.setFont(ConsoleTheme.BODY_FONT);
        phaseLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        attackPhaseBox.setFont(ConsoleTheme.BODY_FONT);
        attackPhaseBox.setForeground(ConsoleTheme.TEXT);
        attackPhaseBox.setBackground(ConsoleTheme.INPUT);
        attackPhaseBox.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
        attackPhaseBox.addActionListener(e -> {
            if (!suppressSelectionEvents)
                refreshAttackList(0);
        });
        phaseChooser.add(phaseLabel, BorderLayout.WEST);
        phaseChooser.add(attackPhaseBox, BorderLayout.CENTER);
        top.add(Box.createVerticalStrut(8));
        top.add(phaseChooser);
        attacksRoot.add(top, BorderLayout.NORTH);

        attackList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleList(attackList);
        attackList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !suppressSelectionEvents)
                loadSelectedAttack();
        });

        JPanel listCard = createListCard(attackList, 235);
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

        JPanel formCard = createCard();
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        styleField(attackIdField, "Stable attack id, for example fire_projectile");
        styleField(animationField, "Animation id, or -1 for NPC default");
        styleField(graphicField, "Graphic id, or -1 for NPC default");
        styleField(projectileField, "Projectile id, or -1 for NPC default");
        styleField(maxHitField, "Max hit override, or -1 for NPC default");
        styleField(combatDelayField, "Combat delay override, or -1 for NPC default");
        attackStyleBox.setFont(ConsoleTheme.BODY_FONT);
        attackStyleBox.setForeground(ConsoleTheme.TEXT);
        attackStyleBox.setBackground(ConsoleTheme.INPUT);
        attackStyleBox.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));

        addFormRow(form, 0, "Attack ID", attackIdField);
        addFormRow(form, 1, "Style", attackStyleBox);
        addFormRow(form, 2, "Animation", animationField);
        addFormRow(form, 3, "Graphic", graphicField);
        addFormRow(form, 4, "Projectile", projectileField);
        addFormRow(form, 5, "Max hit", maxHitField);
        addFormRow(form, 6, "Combat delay", combatDelayField);

        JButton update = new JButton("Update Attack");
        styleButton(update);
        update.addActionListener(e -> updateAttack());

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        form.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        update.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        attackStatus.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        body.add(form);
        body.add(Box.createVerticalStrut(10));
        body.add(update);
        body.add(Box.createVerticalStrut(8));
        body.add(attackStatus);
        formCard.add(body, BorderLayout.CENTER);
        attacksRoot.add(formCard, BorderLayout.CENTER);
    }

    private void addPhase() {
        if (draft == null)
            return;
        int number = draft.getPhases().size() + 1;
        BossLabsDraftDefinition.Phase phase = new BossLabsDraftDefinition.Phase("phase_" + number, 0, 100);
        draft.getPhases().add(phase);
        refreshPhaseViews(draft.getPhases().size() - 1);
        phaseStatus.setText("Phase added. Set its HP range, then add at least one attack.");
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
            phaseStatus.setText("Min and max HP must be whole numbers.");
            return;
        }
        String id = trim(phaseIdField.getText());
        if (id.length() == 0) {
            phaseStatus.setText("Phase ID is required.");
            return;
        }
        phase.setId(id);
        phase.setMinimumHealthPercent(minimum.intValue());
        phase.setMaximumHealthPercent(maximum.intValue());
        refreshPhaseViews(phaseList.getSelectedIndex());
        phaseStatus.setText("Phase updated in local draft.");
        changed();
    }

    private void loadSelectedPhase() {
        BossLabsDraftDefinition.Phase phase = phaseList.getSelectedValue();
        if (phase == null) {
            phaseIdField.setText("");
            phaseMinField.setText("");
            phaseMaxField.setText("");
            return;
        }
        phaseIdField.setText(phase.getId());
        phaseMinField.setText(Integer.toString(phase.getMinimumHealthPercent()));
        phaseMaxField.setText(Integer.toString(phase.getMaximumHealthPercent()));
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

    private BossLabsDraftDefinition.Phase selectedAttackPhase() {
        Object selected = attackPhaseBox.getSelectedItem();
        return selected instanceof BossLabsDraftDefinition.Phase ? (BossLabsDraftDefinition.Phase) selected : null;
    }

    private void addAttack() {
        BossLabsDraftDefinition.Phase phase = selectedAttackPhase();
        if (phase == null) {
            attackStatus.setText("Add or select a phase first.");
            return;
        }
        int number = phase.getAttacks().size() + 1;
        phase.getAttacks().add(new BossLabsDraftDefinition.Attack("attack_" + number, 0, -1, -1, -1, -1, -1));
        refreshAttackList(phase.getAttacks().size() - 1);
        attackStatus.setText("Attack added. NPC defaults are selected initially.");
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
        BossLabsDraftDefinition.Attack attack = attackList.getSelectedValue();
        if (attack == null) {
            attackStatus.setText("Select an attack first.");
            return;
        }

        Integer animation = parseInteger(animationField.getText());
        Integer graphic = parseInteger(graphicField.getText());
        Integer projectile = parseInteger(projectileField.getText());
        Integer maxHit = parseInteger(maxHitField.getText());
        Integer combatDelay = parseInteger(combatDelayField.getText());
        if (animation == null || graphic == null || projectile == null || maxHit == null || combatDelay == null) {
            attackStatus.setText("Animation/GFX/projectile/max hit/combat delay must be whole numbers.");
            return;
        }
        String id = trim(attackIdField.getText());
        if (id.length() == 0) {
            attackStatus.setText("Attack ID is required.");
            return;
        }

        attack.setId(id);
        attack.setCombatStyle(attackStyleBox.getSelectedIndex());
        attack.setAnimationId(animation.intValue());
        attack.setGraphicId(graphic.intValue());
        attack.setProjectileId(projectile.intValue());
        attack.setMaxHitOverride(maxHit.intValue());
        attack.setCombatDelayOverride(combatDelay.intValue());
        refreshAttackList(attackList.getSelectedIndex());
        attackStatus.setText("Attack updated in local draft.");
        changed();
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
            attackIdField.setText("");
            attackStyleBox.setSelectedIndex(0);
            animationField.setText("-1");
            graphicField.setText("-1");
            projectileField.setText("-1");
            maxHitField.setText("-1");
            combatDelayField.setText("-1");
            return;
        }
        attackIdField.setText(attack.getId());
        attackStyleBox.setSelectedIndex(Math.max(0, Math.min(2, attack.getCombatStyle())));
        animationField.setText(Integer.toString(attack.getAnimationId()));
        graphicField.setText(Integer.toString(attack.getGraphicId()));
        projectileField.setText(Integer.toString(attack.getProjectileId()));
        maxHitField.setText(Integer.toString(attack.getMaxHitOverride()));
        combatDelayField.setText(Integer.toString(attack.getCombatDelayOverride()));
    }

    private JPanel verticalHeading(String titleText, String subtitleText) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        JLabel title = new JLabel(titleText);
        title.setFont(ConsoleTheme.SECTION_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel(subtitleText);
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
        return card;
    }

    private void styleList(JList<?> list) {
        list.setBackground(ConsoleTheme.INPUT);
        list.setForeground(ConsoleTheme.TEXT);
        list.setFont(ConsoleTheme.BODY_FONT);
        list.setFixedCellHeight(28);
    }

    private void styleField(JTextField field, String tooltip) {
        field.setToolTipText(tooltip);
        field.setPreferredSize(new Dimension(220, 34));
        ConsoleTheme.styleTextField(field);
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
        JLabel label = new JLabel(text);
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
        if (changeListener != null)
            changeListener.run();
    }
}
