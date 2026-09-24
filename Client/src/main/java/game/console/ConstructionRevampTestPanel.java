package game.console;

import game.ClientConsoleBridge;
import game.ConstructionPaletteOverlay;
import game.ConstructionRadialSelection;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * One-click runtime harness for the Construction Revamp workstream.
 *
 * Buttons call the same owner-only server commands used by the raw command
 * harness. This panel owns no settlement logic.
 */
public final class ConstructionRevampTestPanel extends JScrollPane {

    private static final long serialVersionUID = -8031161601344297457L;

    private final JTextArea status = ConsoleTheme.createWrappedText(
            "Ready. Bundle 2.4 radial multi-worker control is active.", 4);
    private final JComboBox<ConstructionRadialSelection.DragButton> radialDragButton =
            new JComboBox<ConstructionRadialSelection.DragButton>(ConstructionRadialSelection.DragButton.values());
    private final JSpinner workerSelector =
            new JSpinner(new SpinnerNumberModel(1, 1, 999999, 1));
    private final JSpinner workerOuterRingScale =
            new JSpinner(new SpinnerNumberModel(100, 25, 300, 5));
    private final JSpinner workerInnerRingScale =
            new JSpinner(new SpinnerNumberModel(70, 25, 300, 5));
    private final JSpinner demoHungerValue =
            new JSpinner(new SpinnerNumberModel(65, 0, 100, 5));
    private final JSpinner demoThirstValue =
            new JSpinner(new SpinnerNumberModel(35, 0, 100, 5));
    private final JSpinner demoEnergyValue =
            new JSpinner(new SpinnerNumberModel(70, 0, 100, 5));
    private final JSpinner needsArcScale =
            new JSpinner(new SpinnerNumberModel(70, 25, 300, 5));
    private final JComboBox<WorkerPresetChoice> workerRolePreset =
            new JComboBox<WorkerPresetChoice>(WorkerPresetChoice.values());
    private final JComboBox<WorkerPresetChoice> radialRolePreset =
            new JComboBox<WorkerPresetChoice>(WorkerPresetChoice.values());
    private final java.util.List<JCheckBox> workerJobCheckBoxes =
            new java.util.ArrayList<JCheckBox>();
    private final java.util.Map<String, JCheckBox> workerJobCheckBoxByKey =
            new java.util.HashMap<String, JCheckBox>();

    public ConstructionRevampTestPanel() {
        ViewportWidthPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(18, 16, 18, 16));
        content.setMinimumSize(new Dimension(0, 0));

        content.add(ConsoleTheme.titleLabel("CON REVAMP"));
        content.add(Box.createVerticalStrut(4));
        content.add(ConsoleTheme.subtitleLabel("Construction Revamp runtime harness"));
        content.add(Box.createVerticalStrut(16));
        content.add(createRuntimeCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createStorageTestCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createObjectProbeCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createRadialSelectionCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createWorkerRingStyleCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createWorkerSelectorCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createAllowedJobsCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createNeedsCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createProgressionCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createHousingCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createStatusCard());
        content.add(Box.createVerticalGlue());

        setViewportView(content);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(this);
    }

    private JPanel createRuntimeCard() {
        JPanel card = ConsoleTheme.createCard("Settlement controls");
        card.add(Box.createVerticalStrut(10));

        JButton enter = new JButton("Enter Settlement");
        JButton exit = new JButton("Exit Settlement");
        JButton settlementStatus = new JButton("Settlement Status");
        JButton palette = new JButton("Open Build Palette");

        styleButton(enter);
        styleButton(exit);
        styleButton(settlementStatus);
        styleButton(palette);

        enter.addActionListener(e -> queue(
                "itembrowser settlement enter",
                "Enter Settlement queued."));
        exit.addActionListener(e -> queue(
                "itembrowser settlement exit",
                "Exit Settlement queued."));
        settlementStatus.addActionListener(e -> queue(
                "itembrowser settlement status",
                "Settlement Status queued. Check the game chat response."));
        palette.addActionListener(e -> {
            ConstructionPaletteOverlay.show();
            setStatus("Construction build palette opened.");
        });

        JPanel buttons = new JPanel(new GridLayout(0, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.add(enter);
        buttons.add(exit);
        buttons.add(settlementStatus);
        buttons.add(palette);
        card.add(buttons);

        card.add(Box.createVerticalStrut(10));
        card.add(ConsoleTheme.createWrappedText(
                "Completed Phase-1 and Bundle-2.1 verification controls were removed from this tab. "
                + "Their server-side commands remain available if a future regression needs them.",
                3));
        return card;
    }

    private JPanel createStorageTestCard() {
        JPanel card = ConsoleTheme.createCard("Storage Test Controls — Bundle 2.2");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Developer-only controls for the real persistent settlement storage. "
                + "Reset/Prime mutate the current save; Storage Self-Test is disposable and does not mutate it.",
                4));
        card.add(Box.createVerticalStrut(8));

        JButton storageStatus = new JButton("Storage Status");
        JButton resetStorage = new JButton("Reset All Storage");
        JButton primeWood = new JButton("Prime Wood 99/100");
        JButton storageSelfTest = new JButton("Storage Self-Test");

        styleButton(storageStatus);
        styleButton(resetStorage);
        styleButton(primeWood);
        styleButton(storageSelfTest);

        storageStatus.addActionListener(e -> queue(
                "itembrowser settlement resources",
                "Storage Status queued. Check game chat for per-resource amounts."));
        resetStorage.addActionListener(e -> queue(
                "itembrowser settlement storagereset",
                "Storage reset queued. Wood/Food/Stone/Basic ore should all become 0."));
        primeWood.addActionListener(e -> queue(
                "itembrowser settlement storageset wood 99",
                "Wood 99/100 prime queued. Pause/idle workers first for a deterministic final-slot retest."));
        storageSelfTest.addActionListener(e -> queue(
                "itembrowser settlement resourceselftest",
                "Storage Self-Test queued. Expect PASS; this test is disposable."));

        JPanel buttons = new JPanel(new GridLayout(2, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.add(storageStatus);
        buttons.add(resetStorage);
        buttons.add(primeWood);
        buttons.add(storageSelfTest);
        card.add(buttons);
        return card;
    }

    private JPanel createObjectProbeCard() {
        JPanel card = ConsoleTheme.createCard("Asset Studio / Object Probe");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Asset Studio is the primary rail/content discovery workflow: capture the live client scene "
                + "around the player, browse exact ID/type/rotation/slot/tile values, preview a selected object, "
                + "filter rail candidates and export the whole session without writing values down. "
                + "The original server Object Probe remains as a diagnostic fallback.",
                6));
        card.add(Box.createVerticalStrut(8));

        JButton studio = new JButton("Open Asset Studio");
        JButton scanTile = new JButton("Probe Current Tile");
        JButton scanNearby = new JButton("Probe Nearby 3x3");
        JButton logNearby = new JButton("Log Probe 3x3");

        styleButton(studio);
        styleButton(scanTile);
        styleButton(scanNearby);
        styleButton(logNearby);

        studio.addActionListener(e -> {
            ObjectLabWindow.openEmpty();
            setStatus("Matrix3 Asset Studio opened.");
        });
        scanTile.addActionListener(e -> queue(
                "itembrowser objectprobe tile",
                "Current-tile diagnostic probe queued."));
        scanNearby.addActionListener(e -> queue(
                "itembrowser objectprobe nearby",
                "Nearby 3x3 diagnostic probe queued."));
        logNearby.addActionListener(e -> queue(
                "itembrowser objectprobe lognearby",
                "Nearby 3x3 diagnostic probe log queued."));

        JPanel buttons = new JPanel(new GridLayout(2, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.add(studio);
        buttons.add(scanTile);
        buttons.add(scanNearby);
        buttons.add(logNearby);
        card.add(buttons);
        return card;
    }

    private JPanel createRadialSelectionCard() {
        JPanel card = ConsoleTheme.createCard("Radial Worker Selection — RWS-5");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Drag-select live settlement workers in world space. Release commits the exact runtime NPC indexes "
                + "inside the circle; server batch commands then resolve those transient indexes back to this "
                + "settlement's persistent Worker IDs before changing any saved policy. "
                + "Escape cancels only the active drag and preserves the previous committed selection.",
                7));
        card.add(Box.createVerticalStrut(8));

        radialDragButton.setMaximumSize(new Dimension(150, 30));
        radialDragButton.setAlignmentX(LEFT_ALIGNMENT);
        radialDragButton.setFocusable(false);
        radialDragButton.setSelectedItem(ConstructionRadialSelection.DragButton.LEFT);
        radialDragButton.addActionListener(e -> {
            Object selected = radialDragButton.getSelectedItem();
            if (selected instanceof ConstructionRadialSelection.DragButton) {
                ConstructionRadialSelection.setDragButton(
                        (ConstructionRadialSelection.DragButton) selected);
                setStatus("RWS-5 drag button: " + selected + ".");
            }
        });

        card.add(ConsoleTheme.createWrappedText("Drag button:", 2));
        card.add(Box.createVerticalStrut(4));
        card.add(radialDragButton);
        card.add(Box.createVerticalStrut(8));

        JButton enable = new JButton("Enable Worker Control");
        JButton disable = new JButton("Disable Worker Control");
        JButton radialStatus = new JButton("Selection Status");
        JButton clear = new JButton("Clear Selection");

        styleButton(enable);
        styleButton(disable);
        styleButton(radialStatus);
        styleButton(clear);

        enable.addActionListener(e -> {
            Object selected = radialDragButton.getSelectedItem();
            if (selected instanceof ConstructionRadialSelection.DragButton) {
                ConstructionRadialSelection.setDragButton(
                        (ConstructionRadialSelection.DragButton) selected);
            }
            ConstructionRadialSelection.setWorkerControlEnabled(true);
            setStatus(ConstructionRadialSelection.getStatus());
        });
        disable.addActionListener(e -> {
            ConstructionRadialSelection.setWorkerControlEnabled(false);
            setStatus(ConstructionRadialSelection.getStatus());
        });
        radialStatus.addActionListener(e -> {
            if (ConstructionRadialSelection.hasCommittedWorkerSelection()) {
                queueRadialBatch("workerselectionstatus", null,
                        "Selected-worker Status queued for "
                                + ConstructionRadialSelection.getCommittedWorkerCount()
                                + " radial worker(s). Check game chat for persistent Worker IDs.");
            } else {
                setStatus(ConstructionRadialSelection.getStatus());
            }
        });
        clear.addActionListener(e -> {
            ConstructionRadialSelection.clearCommittedRadius();
            setStatus(ConstructionRadialSelection.getStatus());
        });

        JPanel buttons = new JPanel(new GridLayout(2, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.add(enable);
        buttons.add(disable);
        buttons.add(radialStatus);
        buttons.add(clear);
        card.add(buttons);

        card.add(Box.createVerticalStrut(10));
        card.add(ConsoleTheme.createWrappedText(
                "Batch actions target only the workers in the last committed drag selection. "
                + "Role presets still rewrite only Allowed Jobs; batch Pause/Resume leaves those jobs unchanged.",
                4));
        card.add(Box.createVerticalStrut(6));

        radialRolePreset.setMaximumSize(new Dimension(180, 30));
        radialRolePreset.setAlignmentX(LEFT_ALIGNMENT);
        radialRolePreset.setFocusable(false);

        JButton applySelectedPreset = new JButton("Apply to Selection");
        styleButton(applySelectedPreset);
        applySelectedPreset.addActionListener(e -> {
            WorkerPresetChoice choice =
                    (WorkerPresetChoice) radialRolePreset.getSelectedItem();
            if (choice == null) {
                return;
            }
            applyPresetVisual(choice);
            workerRolePreset.setSelectedItem(choice);
            queueRadialBatch("workerselectionpreset", choice.key,
                    choice.displayName + " queued for the committed radial selection.");
        });

        JPanel presetRow = new JPanel(new GridLayout(1, 2, 7, 7));
        presetRow.setOpaque(false);
        presetRow.setAlignmentX(LEFT_ALIGNMENT);
        presetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        presetRow.add(radialRolePreset);
        presetRow.add(applySelectedPreset);
        card.add(presetRow);
        card.add(Box.createVerticalStrut(7));

        JButton pauseSelected = new JButton("Pause Selected");
        JButton resumeSelected = new JButton("Resume Selected");
        styleButton(pauseSelected);
        styleButton(resumeSelected);
        pauseSelected.addActionListener(e ->
                queueRadialBatch("workerselectionpause", "on",
                        "Pause queued for the committed radial selection. Allowed Jobs unchanged."));
        resumeSelected.addActionListener(e ->
                queueRadialBatch("workerselectionpause", "off",
                        "Resume queued for the committed radial selection."));

        JPanel batchButtons = new JPanel(new GridLayout(1, 2, 7, 7));
        batchButtons.setOpaque(false);
        batchButtons.setAlignmentX(LEFT_ALIGNMENT);
        batchButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        batchButtons.add(pauseSelected);
        batchButtons.add(resumeSelected);
        card.add(batchButtons);
        return card;
    }

    private JPanel createWorkerRingStyleCard() {
        JPanel card = ConsoleTheme.createCard("RWS-4 Worker Ring Style — GFX 4171");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "RWS-4 reuses the proven GFX 4171 for every selection ring. "
                + "The large drag ring has its own color, while detected-worker outer/inner layers are independent "
                + "per-call clones with separate scale and color. All changes are client-only and leave the cache definition untouched.",
                6));
        card.add(Box.createVerticalStrut(8));

        workerOuterRingScale.setMaximumSize(new Dimension(110, 30));
        workerInnerRingScale.setMaximumSize(new Dimension(110, 30));
        workerOuterRingScale.setFocusable(false);
        workerInnerRingScale.setFocusable(false);

        workerOuterRingScale.addChangeListener(e -> {
            int percent = ((Number) workerOuterRingScale.getValue()).intValue();
            ConstructionRadialSelection.setWorkerOuterRingScalePercent(percent);
            setStatus(ConstructionRadialSelection.getWorkerRingStyleStatus());
        });
        workerInnerRingScale.addChangeListener(e -> {
            int percent = ((Number) workerInnerRingScale.getValue()).intValue();
            ConstructionRadialSelection.setWorkerInnerRingScalePercent(percent);
            setStatus(ConstructionRadialSelection.getWorkerRingStyleStatus());
        });

        JPanel scales = new JPanel(new GridLayout(2, 2, 7, 7));
        scales.setOpaque(false);
        scales.setAlignmentX(LEFT_ALIGNMENT);
        scales.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        scales.add(ConsoleTheme.createWrappedText("Outer scale %", 1));
        scales.add(workerOuterRingScale);
        scales.add(ConsoleTheme.createWrappedText("Inner scale %", 1));
        scales.add(workerInnerRingScale);
        card.add(scales);
        card.add(Box.createVerticalStrut(8));

        JButton dragColor = new JButton("Drag Ring Color");
        JButton outerColor = new JButton("Outer Color Picker");
        JButton innerColor = new JButton("Inner Color Picker");
        JButton resetColors = new JButton("Use Original Colors");
        JButton styleStatus = new JButton("Ring Style Status");

        styleButton(dragColor);
        styleButton(outerColor);
        styleButton(innerColor);
        styleButton(resetColors);
        styleButton(styleStatus);

        dragColor.addActionListener(e -> {
            Color initial = ConstructionRadialSelection.getDragRingColor();
            if (initial == null) {
                initial = Color.RED;
            }
            Color chosen = JColorChooser.showDialog(
                    this, "Choose drag-ring color", initial);
            if (chosen != null) {
                ConstructionRadialSelection.setDragRingColor(chosen);
                setStatus(ConstructionRadialSelection.getWorkerRingStyleStatus());
            }
        });
        outerColor.addActionListener(e -> {
            Color initial = ConstructionRadialSelection.getWorkerOuterRingColor();
            if (initial == null) {
                initial = Color.RED;
            }
            Color chosen = JColorChooser.showDialog(
                    this, "Choose outer worker-ring color", initial);
            if (chosen != null) {
                ConstructionRadialSelection.setWorkerOuterRingColor(chosen);
                setStatus(ConstructionRadialSelection.getWorkerRingStyleStatus());
            }
        });
        innerColor.addActionListener(e -> {
            Color initial = ConstructionRadialSelection.getWorkerInnerRingColor();
            if (initial == null) {
                initial = Color.YELLOW;
            }
            Color chosen = JColorChooser.showDialog(
                    this, "Choose inner worker-ring color", initial);
            if (chosen != null) {
                ConstructionRadialSelection.setWorkerInnerRingColor(chosen);
                setStatus(ConstructionRadialSelection.getWorkerRingStyleStatus());
            }
        });
        resetColors.addActionListener(e -> {
            ConstructionRadialSelection.resetRingColors();
            setStatus(ConstructionRadialSelection.getWorkerRingStyleStatus());
        });
        styleStatus.addActionListener(e ->
                setStatus(ConstructionRadialSelection.getWorkerRingStyleStatus()));

        JPanel buttons = new JPanel(new GridLayout(0, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 126));
        buttons.add(dragColor);
        buttons.add(outerColor);
        buttons.add(innerColor);
        buttons.add(resetColors);
        buttons.add(styleStatus);
        card.add(buttons);
        return card;
    }

    private JPanel createWorkerSelectorCard() {
        JPanel card = ConsoleTheme.createCard("Worker Inspector — Single Worker");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Inspection-only persistent Worker ID selector for Jobs Status, AI Status, Needs and Progress. "
                + "Worker command ownership belongs to the committed RWS-5 drag selection; changing this spinner does not change who receives commands.",
                5));
        card.add(Box.createVerticalStrut(8));

        workerSelector.setMaximumSize(new Dimension(120, 30));
        workerSelector.setAlignmentX(LEFT_ALIGNMENT);
        workerSelector.addChangeListener(e ->
                setStatus("Inspecting Worker #" + selectedWorkerId()
                        + ". RWS-5 drag selection remains the command target."));
        card.add(workerSelector);
        card.add(Box.createVerticalStrut(8));

        JButton selectedJobs = new JButton("Selected Jobs Status");
        JButton selectedAi = new JButton("Selected AI Status");
        JButton selectedNeeds = new JButton("Selected Needs");
        JButton selectedProgress = new JButton("Selected Progress");
        JButton allWorkers = new JButton("All Worker Status");

        styleButton(selectedJobs);
        styleButton(selectedAi);
        styleButton(selectedNeeds);
        styleButton(selectedProgress);
        styleButton(allWorkers);

        selectedJobs.addActionListener(e -> queue(
                "itembrowser settlement workerjobs " + selectedWorkerId(),
                "Selected worker Jobs Status queued."));
        selectedAi.addActionListener(e -> queue(
                "itembrowser settlement workerai " + selectedWorkerId(),
                "Selected worker AI Status queued."));
        selectedNeeds.addActionListener(e -> queue(
                "itembrowser settlement workerneeds " + selectedWorkerId(),
                "Selected worker Needs Status queued."));
        selectedProgress.addActionListener(e -> queue(
                "itembrowser settlement workerprogress " + selectedWorkerId(),
                "Selected worker Progress Status queued."));
        allWorkers.addActionListener(e -> queue(
                "itembrowser settlement workerallstatus",
                "All Worker Status queued. Jobs, needs, skills, AI and per-resource storage will appear in game chat."));

        JPanel buttons = new JPanel(new GridLayout(0, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 126));
        buttons.add(selectedJobs);
        buttons.add(selectedAi);
        buttons.add(selectedNeeds);
        buttons.add(selectedProgress);
        buttons.add(allWorkers);
        card.add(buttons);
        return card;
    }

    private JPanel createAllowedJobsCard() {
        JPanel card = ConsoleTheme.createCard("Allowed Jobs — RWS-5 Drag Selection");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Command surface for the last committed drag selection. Role presets, Pause/Resume and Allowed Jobs changes apply to every selected worker. "
                + "The single-worker spinner above is inspection-only and never changes this command target.",
                5));
        card.add(Box.createVerticalStrut(8));

        workerRolePreset.setMaximumSize(new Dimension(180, 30));
        workerRolePreset.setAlignmentX(LEFT_ALIGNMENT);
        workerRolePreset.setFocusable(false);

        JButton applyPreset = new JButton("Apply to Drag Selection");
        styleButton(applyPreset);
        applyPreset.addActionListener(e -> {
            WorkerPresetChoice choice = (WorkerPresetChoice) workerRolePreset.getSelectedItem();
            if (choice == null) {
                return;
            }
            applyPresetVisual(choice);
            radialRolePreset.setSelectedItem(choice);
            queueRadialBatch("workerselectionpreset", choice.key,
                    choice.displayName + " queued for the committed RWS-5 selection.");
        });

        JPanel presetRow = new JPanel(new GridLayout(1, 2, 7, 7));
        presetRow.setOpaque(false);
        presetRow.setAlignmentX(LEFT_ALIGNMENT);
        presetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        presetRow.add(workerRolePreset);
        presetRow.add(applyPreset);
        card.add(presetRow);
        card.add(Box.createVerticalStrut(8));

        JButton pauseWorker = new JButton("Pause Drag Selection");
        JButton resumeWorker = new JButton("Resume Drag Selection");
        styleButton(pauseWorker);
        styleButton(resumeWorker);

        pauseWorker.addActionListener(e ->
                queueRadialBatch("workerselectionpause", "on",
                        "Pause queued for the committed RWS-5 selection. Allowed Jobs remain unchanged."));
        resumeWorker.addActionListener(e ->
                queueRadialBatch("workerselectionpause", "off",
                        "Resume queued for the committed RWS-5 selection."));

        JPanel pauseRow = new JPanel(new GridLayout(1, 2, 7, 7));
        pauseRow.setOpaque(false);
        pauseRow.setAlignmentX(LEFT_ALIGNMENT);
        pauseRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        pauseRow.add(pauseWorker);
        pauseRow.add(resumeWorker);
        card.add(pauseRow);
        card.add(Box.createVerticalStrut(8));

        card.add(ConsoleTheme.createWrappedText(
                "Checkboxes are batch command toggles for the current drag selection. Use Selection Status to read the authoritative saved policies when selected workers may differ.",
                4));
        card.add(Box.createVerticalStrut(6));

        JPanel checks = new JPanel(new GridLayout(0, 1, 4, 4));
        checks.setOpaque(false);
        checks.setAlignmentX(LEFT_ALIGNMENT);
        checks.add(createJobCheckBox("Gather Wood", "gather-wood"));
        checks.add(createJobCheckBox("Gather Food", "gather-food"));
        checks.add(createJobCheckBox("Gather Stone", "gather-stone"));
        checks.add(createJobCheckBox("Gather Basic Ore", "gather-basic-ore"));
        checks.add(createJobCheckBox("Haul", "haul"));
        card.add(checks);

        card.add(Box.createVerticalStrut(8));

        JButton jobsStatus = new JButton("Selection Jobs Status");
        JButton enableAll = new JButton("Enable All Selected Jobs");
        JButton disableAll = new JButton("Disable All Selected Jobs");

        styleButton(jobsStatus);
        styleButton(enableAll);
        styleButton(disableAll);

        jobsStatus.addActionListener(e ->
                queueRadialBatch("workerselectionstatus", null,
                        "Selection Jobs Status queued. Authoritative worker policies will appear in game chat."));
        enableAll.addActionListener(e -> {
            for (JCheckBox checkBox : workerJobCheckBoxes) {
                checkBox.setSelected(true);
            }
            queueRadialBatch("workerselectionjobsall", "on",
                    "Enable All Jobs queued for the committed RWS-5 selection.");
        });
        disableAll.addActionListener(e -> {
            for (JCheckBox checkBox : workerJobCheckBoxes) {
                checkBox.setSelected(false);
            }
            queueRadialBatch("workerselectionjobsall", "off",
                    "Disable All Jobs queued for the committed RWS-5 selection.");
        });

        JPanel buttons = new JPanel(new GridLayout(2, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.add(jobsStatus);
        buttons.add(enableAll);
        buttons.add(disableAll);
        card.add(buttons);
        return card;
    }

    private JCheckBox createJobCheckBox(String label, String jobKey) {
        JCheckBox checkBox = new JCheckBox(label);
        checkBox.setOpaque(false);
        checkBox.setForeground(ConsoleTheme.TEXT);
        checkBox.setFont(ConsoleTheme.BODY_FONT);
        checkBox.setFocusable(false);
        workerJobCheckBoxes.add(checkBox);
        workerJobCheckBoxByKey.put(jobKey, checkBox);
        checkBox.addActionListener(e ->
                queueRadialBatch("workerselectionjob",
                        jobKey + " " + (checkBox.isSelected() ? "on" : "off"),
                        label + "=" + (checkBox.isSelected() ? "ON" : "OFF")
                                + " queued for the committed RWS-5 selection."));
        return checkBox;
    }

    private JPanel createNeedsCard() {
        JPanel card = ConsoleTheme.createCard("Worker Needs — Selected Worker + HUD Prototype");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Persistent server-owned Hunger / Thirst / Energy. Hunger and Thirst rise with work; Energy falls. "
                + "The HUD section below is deliberately DEMO ONLY until a clean per-worker server-to-client needs metadata seam is added.",
                6));
        card.add(Box.createVerticalStrut(8));

        JButton needsStatus = new JButton("Needs Status");
        JButton hungerCritical = new JButton("Set Hunger Critical");
        JButton thirstCritical = new JButton("Set Thirst Critical");
        JButton energyCritical = new JButton("Set Energy Critical");
        JButton resetNeeds = new JButton("Reset Needs");

        styleButton(needsStatus);
        styleButton(hungerCritical);
        styleButton(thirstCritical);
        styleButton(energyCritical);
        styleButton(resetNeeds);

        needsStatus.addActionListener(e -> queue(
                "itembrowser settlement workerneeds " + selectedWorkerId(),
                "Needs Status queued for Worker #" + selectedWorkerId() + "."));
        hungerCritical.addActionListener(e -> queue(
                "itembrowser settlement workerneed " + selectedWorkerId() + " hunger 80",
                "Hunger set critical for Worker #" + selectedWorkerId() + "."));
        thirstCritical.addActionListener(e -> queue(
                "itembrowser settlement workerneed " + selectedWorkerId() + " thirst 80",
                "Thirst set critical for Worker #" + selectedWorkerId() + "."));
        energyCritical.addActionListener(e -> queue(
                "itembrowser settlement workerneed " + selectedWorkerId() + " energy 20",
                "Energy set critical for Worker #" + selectedWorkerId() + "."));
        resetNeeds.addActionListener(e -> queue(
                "itembrowser settlement workerneedsreset " + selectedWorkerId(),
                "Worker #" + selectedWorkerId()
                        + " needs reset to Hunger 0 / Thirst 0 / Energy 100."));

        JPanel buttons = new JPanel(new GridLayout(0, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 126));
        buttons.add(needsStatus);
        buttons.add(hungerCritical);
        buttons.add(thirstCritical);
        buttons.add(energyCritical);
        buttons.add(resetNeeds);
        card.add(buttons);

        card.add(Box.createVerticalStrut(12));
        card.add(ConsoleTheme.createWrappedText(
                "Needs HUD visual prototype — same proven GFX 4171 cut into three arches on one shared circumference. "
                + "Hunger is orange, Thirst is cyan, Energy is yellow; each shortens and trends red as wellbeing is lost. "
                + "Demo values and one shared HUD scale update live so we can judge readability before wiring server sync.",
                7));
        card.add(Box.createVerticalStrut(8));

        JSpinner[] needsSpinners = {
                demoHungerValue, demoThirstValue, demoEnergyValue, needsArcScale
        };
        for (JSpinner spinner : needsSpinners) {
            spinner.setMaximumSize(new Dimension(100, 30));
            spinner.setFocusable(false);
        }

        javax.swing.event.ChangeListener needsPreviewChange = e -> {
            ConstructionRadialSelection.setWorkerNeedsPreviewValues(
                    ((Number) demoHungerValue.getValue()).intValue(),
                    ((Number) demoThirstValue.getValue()).intValue(),
                    ((Number) demoEnergyValue.getValue()).intValue());
            ConstructionRadialSelection.setWorkerNeedsArcScalePercent(
                    ((Number) needsArcScale.getValue()).intValue());
            setStatus(ConstructionRadialSelection.getWorkerNeedsPreviewStatus());
        };
        for (JSpinner spinner : needsSpinners) {
            spinner.addChangeListener(needsPreviewChange);
        }

        JPanel needGrid = new JPanel(new GridLayout(4, 2, 7, 7));
        needGrid.setOpaque(false);
        needGrid.setAlignmentX(LEFT_ALIGNMENT);
        needGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 102));
        needGrid.add(ConsoleTheme.createWrappedText("Need", 1));
        needGrid.add(ConsoleTheme.createWrappedText("Demo value", 1));
        needGrid.add(ConsoleTheme.createWrappedText("Hunger", 1));
        needGrid.add(demoHungerValue);
        needGrid.add(ConsoleTheme.createWrappedText("Thirst", 1));
        needGrid.add(demoThirstValue);
        needGrid.add(ConsoleTheme.createWrappedText("Energy", 1));
        needGrid.add(demoEnergyValue);
        card.add(needGrid);
        card.add(Box.createVerticalStrut(8));

        JPanel scaleRow = new JPanel(new GridLayout(1, 2, 7, 7));
        scaleRow.setOpaque(false);
        scaleRow.setAlignmentX(LEFT_ALIGNMENT);
        scaleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        scaleRow.add(ConsoleTheme.createWrappedText("Inner needs/status scale %", 1));
        scaleRow.add(needsArcScale);
        card.add(scaleRow);
        card.add(Box.createVerticalStrut(8));

        JButton enableHud = new JButton("Enable Needs HUD Preview");
        JButton disableHud = new JButton("Disable Needs HUD Preview");
        JButton hudStatus = new JButton("Needs HUD Status");
        styleButton(enableHud);
        styleButton(disableHud);
        styleButton(hudStatus);

        enableHud.addActionListener(e -> {
            needsPreviewChange.stateChanged(null);
            ConstructionRadialSelection.setWorkerNeedsPreviewEnabled(true);
            setStatus(ConstructionRadialSelection.getWorkerNeedsPreviewStatus());
        });
        disableHud.addActionListener(e -> {
            ConstructionRadialSelection.setWorkerNeedsPreviewEnabled(false);
            setStatus(ConstructionRadialSelection.getWorkerNeedsPreviewStatus());
        });
        hudStatus.addActionListener(e ->
                setStatus(ConstructionRadialSelection.getWorkerNeedsPreviewStatus()));

        JPanel hudButtons = new JPanel(new GridLayout(0, 2, 7, 7));
        hudButtons.setOpaque(false);
        hudButtons.setAlignmentX(LEFT_ALIGNMENT);
        hudButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        hudButtons.add(enableHud);
        hudButtons.add(disableHud);
        hudButtons.add(hudStatus);
        card.add(hudButtons);
        return card;
    }

    private JPanel createProgressionCard() {
        JPanel card = ConsoleTheme.createCard("Worker Progression / Construction XP");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Permanent per-worker skill XP uses RuneScape-style levels. Gathering awards the selected worker's mapped skill; successful hauling awards Hauling XP and exactly 1 base Construction XP per stored resource. Idle/blocked work awards none.",
                5));
        card.add(Box.createVerticalStrut(8));

        JButton progressStatus = new JButton("Progress Status");
        styleButton(progressStatus);

        progressStatus.addActionListener(e -> queue(
                "itembrowser settlement workerprogress " + selectedWorkerId(),
                "Progress Status queued for Worker #" + selectedWorkerId()
                        + ". Worker skill XP/levels and player Construction XP will appear in game chat."));

        JPanel buttons = new JPanel(new GridLayout(1, 1, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        buttons.add(progressStatus);
        card.add(buttons);
        return card;
    }

    private JPanel createHousingCard() {
        JPanel card = ConsoleTheme.createCard("Bundle 2.3 — Housing + Population Capacity");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Persistent capacity foundation. Each saved housing bed adds +1 population capacity after the starter shelter. "
                + "The current Add/Remove Bed controls are developer harnesses until a verified bed object is wired into normal Construction placement.",
                5));
        card.add(Box.createVerticalStrut(8));

        JButton housingStatus = new JButton("Housing Status");
        JButton addBed = new JButton("Add Bed Capacity");
        JButton removeBed = new JButton("Remove Bed Capacity");
        JButton recruit = new JButton("Recruit Next Worker");
        JButton selfTest = new JButton("Bundle 2.3 Self-Test");
        JButton liveCheck = new JButton("Bundle 2.3 Check");
        JButton capture = new JButton("Capture W3 Baseline");
        JButton check = new JButton("Check W3 Baseline");

        styleButton(housingStatus);
        styleButton(addBed);
        styleButton(removeBed);
        styleButton(recruit);
        styleButton(selfTest);
        styleButton(liveCheck);
        styleButton(capture);
        styleButton(check);

        housingStatus.addActionListener(e -> queue(
                "itembrowser settlement housing",
                "Housing Status queued. Bed count, capacity and population will appear in game chat."));
        addBed.addActionListener(e -> queue(
                "itembrowser settlement housingbedadd",
                "Add Bed Capacity queued. One persistent bed should raise population capacity by 1."));
        removeBed.addActionListener(e -> queue(
                "itembrowser settlement housingbedremove",
                "Remove Bed Capacity queued. Occupied capacity cannot be removed."));
        recruit.addActionListener(e -> queue(
                "itembrowser settlement populationrecruit",
                "Recruit Next Worker queued. With one bed and two existing workers, expect Worker #3."));
        selfTest.addActionListener(e -> queue(
                "itembrowser settlement housing23selftest",
                "Bundle 2.3 Self-Test queued. Expect PASS for persistent bed capacity and Worker #3 serialization."));
        liveCheck.addActionListener(e -> queue(
                "itembrowser settlement housing23check",
                "Bundle 2.3 live check queued. Expect PASS at beds=1, capacity=3, saved=3/runtime=3."));
        capture.addActionListener(e -> queue(
                "itembrowser settlement housing23baseline",
                "Worker #3 housing baseline capture queued."));
        check.addActionListener(e -> queue(
                "itembrowser settlement housing23baselinecheck",
                "Worker #3 housing baseline check queued. Expect PASS after re-entry/relog."));

        JPanel buttons = new JPanel(new GridLayout(0, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        buttons.add(housingStatus);
        buttons.add(addBed);
        buttons.add(removeBed);
        buttons.add(recruit);
        buttons.add(selfTest);
        buttons.add(liveCheck);
        buttons.add(capture);
        buttons.add(check);
        card.add(buttons);
        return card;
    }

    private JPanel createStatusCard() {
        JPanel card = ConsoleTheme.createCard("Test output");
        card.add(Box.createVerticalStrut(9));
        status.setForeground(ConsoleTheme.ACCENT);
        card.add(status);
        return card;
    }

    private void applyPresetVisual(WorkerPresetChoice choice) {
        if (choice == null) {
            return;
        }
        for (java.util.Map.Entry<String, JCheckBox> entry :
                workerJobCheckBoxByKey.entrySet()) {
            entry.getValue().setSelected(choice.allows(entry.getKey()));
        }
    }

    private long selectedWorkerId() {
        Object value = workerSelector.getValue();
        return value instanceof Number ? ((Number) value).longValue() : 1L;
    }

    private void styleButton(JButton button) {
        ConsoleTheme.styleButton(button);
        button.setFocusable(false);
    }

    private void queue(String command, String success) {
        String error = ClientConsoleBridge.queueConsoleCommand(command);
        setStatus(error == null ? success : error);
    }

    private void queueRadialBatch(String operation, String argument, String success) {
        if (!ConstructionRadialSelection.hasCommittedWorkerSelection()) {
            setStatus("RWS-5: drag over one or more live settlement workers and release first.");
            return;
        }
        StringBuilder command = new StringBuilder("itembrowser settlement ")
                .append(operation);
        if (argument != null && argument.trim().length() > 0) {
            command.append(' ').append(argument.trim());
        }
        queue(command.toString(), success + " Server-owned selection contains "
                + ConstructionRadialSelection.getCommittedWorkerCount() + " worker(s).");
    }

    private void setStatus(String message) {
        final Point viewPosition = getViewport().getViewPosition();
        status.setText(message == null ? "" : message);
        SwingUtilities.invokeLater(() -> {
            if (getViewport().getView() != null) {
                getViewport().setViewPosition(viewPosition);
            }
        });
    }

    private enum WorkerPresetChoice {
        LUMBERJACK("Lumberjack", "lumberjack",
                "gather-wood", "haul"),
        FORAGER("Forager", "forager",
                "gather-food", "haul"),
        STONE_MINER("Stone Miner", "stone-miner",
                "gather-stone", "haul"),
        ORE_MINER("Ore Miner", "ore-miner",
                "gather-basic-ore", "haul"),
        HAULER_ONLY("Hauler Only", "hauler-only",
                "haul"),
        IDLE("Idle", "idle");

        private final String displayName;
        private final String key;
        private final java.util.Set<String> jobs =
                new java.util.HashSet<String>();

        WorkerPresetChoice(String displayName, String key, String... jobs) {
            this.displayName = displayName;
            this.key = key;
            if (jobs != null) {
                for (String job : jobs) {
                    if (job != null) {
                        this.jobs.add(job);
                    }
                }
            }
        }

        private boolean allows(String jobKey) {
            return jobs.contains(jobKey);
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {

        private static final long serialVersionUID = -4531105733940656014L;

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            int extent = orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
            return Math.max(16, extent - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
