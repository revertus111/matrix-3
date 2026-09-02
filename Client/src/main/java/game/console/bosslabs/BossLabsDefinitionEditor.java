package game.console.bosslabs;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;

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
import javax.swing.SwingUtilities;

import game.console.ConsoleTheme;

/**
 * Client-local authoring controls for BossLabs phase and attack DRAFT data.
 */
public final class BossLabsDefinitionEditor {

    private static final int MAX_PATTERN_TILES = 128;
    private static final int MAX_TILE_OFFSET = 16;
    private static final String[] TILE_EFFECT_NAMES = {
            "Damage players", "Heal players", "Damage boss", "Heal boss" };

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
    private final JLabel patternStatus = createStatus("Select an attack, then click tiles to build a target-centered pattern.");
    private final TilePatternCanvas tilePatternCanvas = new TilePatternCanvas();

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
        tilePatternCanvas.repaint();
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

        JPanel top = verticalHeading("Attacks", "Edit weighted rotation, targeting, reusable tile effects, telegraphs, and lingering hazards.");

        JPanel phaseChooser = new JPanel(new BorderLayout(8, 0));
        phaseChooser.setOpaque(false);
        JLabel phaseLabel = new JLabel("Phase");
        phaseLabel.setFont(ConsoleTheme.BODY_FONT);
        phaseLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        styleCombo(attackPhaseBox);
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
        styleField(attackIdField, "Stable attack id, for example random_fireball");
        styleField(targetRangeField, "Maximum range for alternate-player targeting, 1-32 tiles");
        styleField(rotationWeightField, "Relative weighted-selection chance, 1-1000. Equal weights reproduce the old uniform selection.");
        styleField(cooldownAttacksField, "Future attack opportunities this attack must sit out after use, 0-100.");
        styleField(animationField, "Animation id, or -1 for NPC default");
        styleField(graphicField, "NPC graphic id, or -1 for NPC default");
        styleField(projectileField, "Projectile id, or -1 for NPC default");
        styleField(maxHitField, "Impact amount. Damage players uses this as max hit; heal/boss effects use a fixed amount. -1 uses NPC style max hit.");
        styleField(combatDelayField, "Combat delay override, or -1 for NPC default");
        styleField(telegraphGraphicField, "Ground warning graphic id, or -1 for none");
        styleField(impactGraphicField, "Ground impact graphic id, or -1 for none");
        styleField(telegraphTicksField, "Ticks between warning and impact, 0-50");
        styleField(hazardGraphicField, "Lingering ground graphic id, or -1 for none");
        styleField(hazardDurationField, "Hazard duration in ticks; 0 disables the lingering hazard");
        styleField(hazardIntervalField, "Effect interval in ticks, 1-50; must not exceed duration when enabled");
        styleField(hazardMaxHitField, "Hazard amount. Damage players uses this as max hit; heal/boss effects use a fixed amount. -1 uses NPC style max hit.");
        styleCombo(attackStyleBox);
        styleCombo(targetModeBox);
        styleCombo(repeatModeBox);
        styleCombo(impactEffectBox);
        styleCombo(hazardEffectBox);
        targetModeBox.setToolTipText("Random nearby player prefers someone other than the NPC's current combat target; solo fights fall back safely.");
        repeatModeBox.setToolTipText("Prefer another ready attack blocks an immediate repeat only when another non-cooldown attack is available.");
        impactEffectBox.setToolTipText("Action performed once on the painted tiles at impact.");
        hazardEffectBox.setToolTipText("Action repeated on the painted tiles while the lingering hazard is active.");

        addFormRow(form, 0, "Attack ID", attackIdField);
        addFormRow(form, 1, "Style", attackStyleBox);
        addFormRow(form, 2, "Target", targetModeBox);
        addFormRow(form, 3, "Target range", targetRangeField);
        addFormRow(form, 4, "Weight", rotationWeightField);
        addFormRow(form, 5, "Cooldown turns", cooldownAttacksField);
        addFormRow(form, 6, "Repeat rule", repeatModeBox);
        addFormRow(form, 7, "Animation", animationField);
        addFormRow(form, 8, "NPC graphic", graphicField);
        addFormRow(form, 9, "Projectile", projectileField);
        addFormRow(form, 10, "Impact effect", impactEffectBox);
        addFormRow(form, 11, "Impact amount / max hit", maxHitField);
        addFormRow(form, 12, "Combat delay", combatDelayField);
        addFormRow(form, 13, "Warning GFX", telegraphGraphicField);
        addFormRow(form, 14, "Impact GFX", impactGraphicField);
        addFormRow(form, 15, "Warning ticks", telegraphTicksField);
        addFormRow(form, 16, "Hazard effect", hazardEffectBox);
        addFormRow(form, 17, "Hazard GFX", hazardGraphicField);
        addFormRow(form, 18, "Hazard duration", hazardDurationField);
        addFormRow(form, 19, "Hazard interval", hazardIntervalField);
        addFormRow(form, 20, "Hazard amount / max hit", hazardMaxHitField);

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

        JPanel patternCard = createCard();
        patternCard.setPreferredSize(new Dimension(500, 245));
        JPanel patternHeading = verticalHeading("Tile pattern", "Origin 0,0 is the resolved attack target's snapshotted tile. Left click toggles effect tiles; middle drag pans; wheel zooms.");
        patternCard.add(patternHeading, BorderLayout.NORTH);
        patternCard.add(tilePatternCanvas, BorderLayout.CENTER);
        patternCard.add(patternStatus, BorderLayout.SOUTH);

        JPanel editorColumn = new JPanel(new BorderLayout(10, 10));
        editorColumn.setOpaque(false);
        editorColumn.add(formCard, BorderLayout.CENTER);
        editorColumn.add(patternCard, BorderLayout.SOUTH);

        JScrollPane editorScroll = new JScrollPane(editorColumn);
        editorScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        editorScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        editorScroll.setBorder(BorderFactory.createEmptyBorder());
        ConsoleTheme.styleScrollPane(editorScroll);
        attacksRoot.add(editorScroll, BorderLayout.CENTER);
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
        attackStatus.setText("Attack added. Default tile effects damage players; other combat defaults remain unchanged.");
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
            attackStatus.setText("Attack numeric fields must be whole numbers.");
            return;
        }
        String id = trim(attackIdField.getText());
        if (id.length() == 0) {
            attackStatus.setText("Attack ID is required.");
            return;
        }

        attack.setId(id);
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
            updatePatternStatus(null);
            tilePatternCanvas.repaint();
            return;
        }
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
        updatePatternStatus(attack);
        tilePatternCanvas.repaint();
    }

    private void updatePatternStatus(BossLabsDraftDefinition.Attack attack) {
        if (attack == null) {
            patternStatus.setText("Select an attack, then click tiles to build a target-centered pattern.");
            return;
        }
        if (attack.getTilePattern().isEmpty()) {
            if (attack.getHazardDurationTicks() > 0) {
                patternStatus.setText("Pattern cleared. Disable the hazard or repaint at least one tile before publishing.");
            } else if (attack.getImpactTileEffectType() != BossLabsDraftDefinition.TILE_EFFECT_DAMAGE_PLAYERS) {
                patternStatus.setText("Pattern cleared. " + BossLabsDraftDefinition.tileEffectName(attack.getImpactTileEffectType())
                        + " requires at least one painted impact tile.");
            } else {
                patternStatus.setText("No tile pattern: this attack uses normal single-target combat against its resolved target.");
            }
            return;
        }
        String text = attack.getTilePattern().size() + " tile(s) | impact: "
                + BossLabsDraftDefinition.tileEffectName(attack.getImpactTileEffectType());
        if (attack.getHazardDurationTicks() > 0) {
            text += " | hazard: " + BossLabsDraftDefinition.tileEffectName(attack.getHazardTileEffectType())
                    + " every " + attack.getHazardTickInterval() + " tick(s) for "
                    + attack.getHazardDurationTicks() + " tick(s)";
        }
        patternStatus.setText(text);
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
        attackList.repaint();
        if (changeListener != null)
            changeListener.run();
    }

    private final class TilePatternCanvas extends JPanel {

        private static final long serialVersionUID = -5092715298132887648L;
        private static final int MIN_TILE_SIZE = 16;
        private static final int MAX_TILE_SIZE = 64;

        private int tileSize = 28;
        private int panX;
        private int panY;
        private Point lastPanPoint;
        private int hoverX = Integer.MIN_VALUE;
        private int hoverY = Integer.MIN_VALUE;

        private TilePatternCanvas() {
            setBackground(ConsoleTheme.INPUT);
            setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
            setPreferredSize(new Dimension(480, 185));

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    if (SwingUtilities.isMiddleMouseButton(event))
                        lastPanPoint = event.getPoint();
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    if (SwingUtilities.isMiddleMouseButton(event))
                        lastPanPoint = null;
                }

                @Override
                public void mouseClicked(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event))
                        return;
                    updateHover(event.getPoint());
                    toggleHoveredTile();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hoverX = Integer.MIN_VALUE;
                    hoverY = Integer.MIN_VALUE;
                    repaint();
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent event) {
                    int requested = tileSize - event.getWheelRotation() * 4;
                    tileSize = Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, requested));
                    updateHover(event.getPoint());
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseWheelListener(mouse);
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent event) {
                    updateHover(event.getPoint());
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (lastPanPoint == null)
                        return;
                    panX += event.getX() - lastPanPoint.x;
                    panY += event.getY() - lastPanPoint.y;
                    lastPanPoint = event.getPoint();
                    updateHover(event.getPoint());
                    repaint();
                }
            });
        }

        private BossLabsDraftDefinition.Attack selectedAttack() {
            return attackList.getSelectedValue();
        }

        private void toggleHoveredTile() {
            BossLabsDraftDefinition.Attack attack = selectedAttack();
            if (attack == null) {
                patternStatus.setText("Select an attack first.");
                return;
            }
            if (Math.abs(hoverX) > MAX_TILE_OFFSET || Math.abs(hoverY) > MAX_TILE_OFFSET) {
                patternStatus.setText("Pattern tiles must stay within +/-" + MAX_TILE_OFFSET + " of the target.");
                return;
            }

            BossLabsDraftDefinition.TileOffset tile = new BossLabsDraftDefinition.TileOffset(hoverX, hoverY);
            if (attack.getTilePattern().contains(tile)) {
                attack.getTilePattern().remove(tile);
            } else {
                if (attack.getTilePattern().size() >= MAX_PATTERN_TILES) {
                    patternStatus.setText("Pattern limit reached: " + MAX_PATTERN_TILES + " tiles.");
                    return;
                }
                attack.getTilePattern().add(tile);
            }
            updatePatternStatus(attack);
            changed();
            repaint();
        }

        private void updateHover(Point point) {
            int originX = getWidth() / 2 + panX;
            int originY = getHeight() / 2 + panY;
            hoverX = floorDiv(point.x - originX, tileSize);
            hoverY = floorDiv(point.y - originY, tileSize);
            BossLabsDraftDefinition.Attack attack = selectedAttack();
            if (attack != null)
                patternStatus.setText("Hover " + hoverX + ", " + hoverY + " | " + attack.getTilePattern().size() + " painted tile(s)");
            repaint();
        }

        private int floorDiv(int value, int divisor) {
            int result = value / divisor;
            if ((value ^ divisor) < 0 && result * divisor != value)
                result--;
            return result;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int originX = getWidth() / 2 + panX;
                int originY = getHeight() / 2 + panY;
                int startX = positiveModulo(originX, tileSize);
                int startY = positiveModulo(originY, tileSize);

                g.setColor(ConsoleTheme.BORDER);
                for (int x = startX; x < getWidth(); x += tileSize)
                    g.drawLine(x, 0, x, getHeight());
                for (int y = startY; y < getHeight(); y += tileSize)
                    g.drawLine(0, y, getWidth(), y);

                BossLabsDraftDefinition.Attack attack = selectedAttack();
                if (attack != null) {
                    for (BossLabsDraftDefinition.TileOffset tile : attack.getTilePattern()) {
                        int drawX = originX + tile.getX() * tileSize;
                        int drawY = originY + tile.getY() * tileSize;
                        g.setColor(new Color(ConsoleTheme.ACCENT.getRed(), ConsoleTheme.ACCENT.getGreen(),
                                ConsoleTheme.ACCENT.getBlue(), 80));
                        g.fillRect(drawX + 1, drawY + 1, Math.max(1, tileSize - 1), Math.max(1, tileSize - 1));
                        g.setColor(ConsoleTheme.ACCENT);
                        g.drawRect(drawX, drawY, tileSize, tileSize);
                    }
                }

                g.setStroke(new BasicStroke(2.0f));
                g.setColor(ConsoleTheme.ACCENT_DARK);
                g.drawLine(originX, 0, originX, getHeight());
                g.drawLine(0, originY, getWidth(), originY);

                if (hoverX != Integer.MIN_VALUE) {
                    int drawX = originX + hoverX * tileSize;
                    int drawY = originY + hoverY * tileSize;
                    g.setColor(ConsoleTheme.TEXT);
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawRect(drawX, drawY, tileSize, tileSize);
                }

                g.setFont(ConsoleTheme.SMALL_FONT);
                g.setColor(ConsoleTheme.MUTED_TEXT);
                g.drawString("target 0,0", originX + 5, originY - 6);
            } finally {
                g.dispose();
            }
        }

        private int positiveModulo(int value, int modulus) {
            int result = value % modulus;
            return result < 0 ? result + modulus : result;
        }
    }
}
