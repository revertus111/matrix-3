package game.console;

import game.ClientConsoleBridge;
import game.ConstructionPaletteOverlay;
import game.ConstructionRadialSelection;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
            "Ready. Bundle 2.2 multi-worker control bundle is active.", 4);
    private final JComboBox<ConstructionRadialSelection.DragButton> radialDragButton =
            new JComboBox<ConstructionRadialSelection.DragButton>(ConstructionRadialSelection.DragButton.values());
    private final JSpinner workerSelector =
            new JSpinner(new SpinnerNumberModel(1, 1, 999999, 1));
    private final JComboBox<WorkerPresetChoice> workerRolePreset =
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
        content.add(createReticule4187ProbeCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createWorkerSelectorCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createAllowedJobsCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createNeedsCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createProgressionCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createBundle22Card());
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
        JPanel card = ConsoleTheme.createCard("Radial Worker Selection — RWS-2");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Worker Control drag primitive. Hold the configured mouse button on valid game ground, "
                + "drag outward/inward to resize the world-space reticule, then release to commit the radius. "
                + "The area ring is visible only while held; release hides it immediately. "
                + "Escape cancels only the active drag and preserves the previous committed radius.",
                6));
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
                setStatus("RWS-2 drag button: " + selected + ".");
            }
        });

        card.add(ConsoleTheme.createWrappedText("Drag button:", 2));
        card.add(Box.createVerticalStrut(4));
        card.add(radialDragButton);
        card.add(Box.createVerticalStrut(8));

        JButton enable = new JButton("Enable Worker Control");
        JButton disable = new JButton("Disable Worker Control");
        JButton radialStatus = new JButton("Radial Status");
        JButton clear = new JButton("Clear Radius");

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
        radialStatus.addActionListener(e ->
                setStatus(ConstructionRadialSelection.getStatus()));
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
        return card;
    }

    private JPanel createReticule4187ProbeCard() {
        JPanel card = ConsoleTheme.createCard("GFX 4187 Worker Status Recolor Probe");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Client-only RWS-4 preflight for the red/yellow double-ring candidate. "
                + "Original samples the cloned model's visible packed face colours. "
                + "Color A and Color B independently replace the two most frequent non-sentinel colours "
                + "on the isolated per-call clone so we can see whether the inner and outer rings are independently addressable. "
                + "The probe temporarily disables Worker Control while visible; move the mouse over valid world ground.",
                7));
        card.add(Box.createVerticalStrut(8));

        JButton original = new JButton("Show 4187 Original");
        JButton colorA = new JButton("Highlight Color A");
        JButton colorB = new JButton("Highlight Color B");
        JButton probeStatus = new JButton("4187 Probe Status");
        JButton hide = new JButton("Hide 4187 Probe");

        styleButton(original);
        styleButton(colorA);
        styleButton(colorB);
        styleButton(probeStatus);
        styleButton(hide);

        original.addActionListener(e -> {
            ConstructionRadialSelection.setReticule4187ProbeMode(
                    ConstructionRadialSelection.Reticule4187ProbeMode.ORIGINAL);
            setStatus(ConstructionRadialSelection.getReticule4187ProbeStatus());
        });
        colorA.addActionListener(e -> {
            ConstructionRadialSelection.setReticule4187ProbeMode(
                    ConstructionRadialSelection.Reticule4187ProbeMode.COLOR_A);
            setStatus(ConstructionRadialSelection.getReticule4187ProbeStatus());
        });
        colorB.addActionListener(e -> {
            ConstructionRadialSelection.setReticule4187ProbeMode(
                    ConstructionRadialSelection.Reticule4187ProbeMode.COLOR_B);
            setStatus(ConstructionRadialSelection.getReticule4187ProbeStatus());
        });
        probeStatus.addActionListener(e ->
                setStatus(ConstructionRadialSelection.getReticule4187ProbeStatus()));
        hide.addActionListener(e -> {
            ConstructionRadialSelection.setReticule4187ProbeMode(
                    ConstructionRadialSelection.Reticule4187ProbeMode.OFF);
            setStatus(ConstructionRadialSelection.getReticule4187ProbeStatus());
        });

        JPanel buttons = new JPanel(new GridLayout(0, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 126));
        buttons.add(original);
        buttons.add(colorA);
        buttons.add(colorB);
        buttons.add(probeStatus);
        buttons.add(hide);
        card.add(buttons);
        return card;
    }

    private JPanel createWorkerSelectorCard() {
        JPanel card = ConsoleTheme.createCard("Multi-worker control");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Select the persistent Worker ID controlled by Allowed Jobs, Needs, AI Status and Progress Status below. "
                + "Changing selection clears local checkbox visuals so one worker's UI state is never mistaken for another worker's authoritative saved policy.",
                5));
        card.add(Box.createVerticalStrut(8));

        workerSelector.setMaximumSize(new Dimension(120, 30));
        workerSelector.setAlignmentX(LEFT_ALIGNMENT);
        workerSelector.addChangeListener(e -> {
            for (JCheckBox checkBox : workerJobCheckBoxes) {
                checkBox.setSelected(false);
            }
            setStatus("Selected Worker #" + selectedWorkerId()
                    + ". Job checkbox visuals reset; use Jobs Status for authoritative readback.");
        });
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
        JPanel card = ConsoleTheme.createCard("Allowed Jobs — Selected Worker");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Server-authoritative allowlist. Role presets are convenience writes into this same saved policy; "
                + "they do not create a second role system or change Pause, Needs or Progression.",
                4));
        card.add(Box.createVerticalStrut(8));

        workerRolePreset.setMaximumSize(new Dimension(180, 30));
        workerRolePreset.setAlignmentX(LEFT_ALIGNMENT);
        workerRolePreset.setFocusable(false);

        JButton applyPreset = new JButton("Apply Role Preset");
        styleButton(applyPreset);
        applyPreset.addActionListener(e -> {
            WorkerPresetChoice choice = (WorkerPresetChoice) workerRolePreset.getSelectedItem();
            if (choice == null) {
                return;
            }
            applyPresetVisual(choice);
            queue("itembrowser settlement workerpreset " + selectedWorkerId()
                    + " " + choice.key,
                    choice.displayName + " preset queued for Worker #" + selectedWorkerId()
                            + ". Allowed Jobs updated atomically.");
        });

        JPanel presetRow = new JPanel(new GridLayout(1, 2, 7, 7));
        presetRow.setOpaque(false);
        presetRow.setAlignmentX(LEFT_ALIGNMENT);
        presetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        presetRow.add(workerRolePreset);
        presetRow.add(applyPreset);
        card.add(presetRow);
        card.add(Box.createVerticalStrut(8));

        JButton pauseWorker = new JButton("Pause Worker");
        JButton resumeWorker = new JButton("Resume Worker");
        styleButton(pauseWorker);
        styleButton(resumeWorker);

        pauseWorker.addActionListener(e -> queue(
                "itembrowser settlement workerpause " + selectedWorkerId() + " on",
                "Pause queued for Worker #" + selectedWorkerId()
                        + ". Allowed Jobs remain unchanged."));
        resumeWorker.addActionListener(e -> queue(
                "itembrowser settlement workerpause " + selectedWorkerId() + " off",
                "Resume queued for Worker #" + selectedWorkerId()
                        + ". Existing Allowed Jobs will resume."));

        JPanel pauseRow = new JPanel(new GridLayout(1, 2, 7, 7));
        pauseRow.setOpaque(false);
        pauseRow.setAlignmentX(LEFT_ALIGNMENT);
        pauseRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        pauseRow.add(pauseWorker);
        pauseRow.add(resumeWorker);
        card.add(pauseRow);
        card.add(Box.createVerticalStrut(8));

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

        JButton jobsStatus = new JButton("Jobs Status");
        JButton enableAll = new JButton("Enable All Jobs");
        JButton disableAll = new JButton("Disable All Jobs");

        styleButton(jobsStatus);
        styleButton(enableAll);
        styleButton(disableAll);

        jobsStatus.addActionListener(e -> queue(
                "itembrowser settlement workerjobs " + selectedWorkerId(),
                "Jobs Status queued for Worker #" + selectedWorkerId()
                        + ". Authoritative saved permissions will appear in game chat."));
        enableAll.addActionListener(e -> queue(
                "itembrowser settlement workerjobsall " + selectedWorkerId() + " on",
                "Enable All Jobs queued for Worker #" + selectedWorkerId()
                        + ". Use Jobs Status to confirm saved state."));
        disableAll.addActionListener(e -> queue(
                "itembrowser settlement workerjobsall " + selectedWorkerId() + " off",
                "Disable All Jobs queued for Worker #" + selectedWorkerId()
                        + ". Use Jobs Status to confirm saved state."));

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
        checkBox.addActionListener(e -> queue(
                "itembrowser settlement workerjob " + selectedWorkerId() + " "
                        + jobKey + " " + (checkBox.isSelected() ? "on" : "off"),
                "Worker #" + selectedWorkerId() + " " + label + "="
                        + (checkBox.isSelected() ? "ON" : "OFF")
                        + " queued. Use Jobs Status for authoritative readback."));
        return checkBox;
    }

    private JPanel createNeedsCard() {
        JPanel card = ConsoleTheme.createCard("Worker Needs — Selected Worker");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Persistent server-owned Hunger / Thirst / Energy. Hunger and Thirst rise with work; Energy falls. "
                + "Critical Hunger consumes settlement Food, critical Thirst uses the Phase-1 starter shelter water supply, and critical Energy rests at home.",
                5));
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

    private JPanel createBundle22Card() {
        JPanel card = ConsoleTheme.createCard("Bundle 2.2 — Remaining Persistence Gate");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "All worker-control/concurrency checks are already runtime verified. Remaining gate:\n"
                + "1. Prepare + Capture Baseline disables all jobs for both workers, resets their Needs, and captures the live two-worker snapshot.\n"
                + "2. Exit/re-enter -> Check Baseline must PASS.\n"
                + "3. Logout/relog/re-enter -> Check Baseline must PASS.",
                7));
        card.add(Box.createVerticalStrut(8));

        JButton prepareCapture = new JButton("Prepare + Capture Baseline");
        JButton check = new JButton("Check 2-Worker Baseline");

        styleButton(prepareCapture);
        styleButton(check);

        prepareCapture.addActionListener(e -> queue(
                "itembrowser settlement bundle22preparebaseline",
                "Preparing both workers and capturing the Bundle 2.2 baseline. Expect PREPARED + BASELINE SAVED."));
        check.addActionListener(e -> queue(
                "itembrowser settlement bundle22check",
                "Bundle 2.2 baseline check queued. Expect PASS after re-entry/relog."));

        JPanel buttons = new JPanel(new GridLayout(1, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        buttons.add(prepareCapture);
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
