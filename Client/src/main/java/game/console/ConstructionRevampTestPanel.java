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
 * Focused live-development controls for the Construction Revamp workstream.
 *
 * Completed phase/bundle verification controls stay available through the raw
 * command bridge and do not remain in this panel once their runtime gate passes.
 * This panel owns no settlement gameplay logic.
 */
public final class ConstructionRevampTestPanel extends JScrollPane {

    private static final long serialVersionUID = -8031161601344297457L;

    private final JTextArea status = ConsoleTheme.createWrappedText(
            "Ready. Construction development controls loaded.", 4);

    private final JSpinner workerSelector =
            new JSpinner(new SpinnerNumberModel(1, 1, 999999, 1));

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
        content.add(ConsoleTheme.subtitleLabel("Construction Development"));
        content.add(Box.createVerticalStrut(16));

        content.add(createSettlementCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createWorkerControlCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createAllowedJobsCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createWorkerDiagnosticsCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createStorageCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createProcessingCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createNeedsHudCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createDevelopmentToolsCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createStatusCard());
        content.add(Box.createVerticalGlue());

        setViewportView(content);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(this);
    }

    private JPanel createSettlementCard() {
        JPanel card = ConsoleTheme.createCard("Settlement");
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
                "Settlement Status queued. Check game chat."));
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
        return card;
    }

    private JPanel createWorkerControlCard() {
        JPanel card = ConsoleTheme.createCard("Worker Control");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "LMB drag selects workers. Single ground click does not move them; double-click ground moves the committed selection. "
                + "Use the world right-click Clear Selection action to deselect.",
                4));
        card.add(Box.createVerticalStrut(8));

        JButton enable = new JButton("Enable Worker Control");
        JButton disable = new JButton("Disable Worker Control");
        JButton selectionStatus = new JButton("Selection Status");

        styleButton(enable);
        styleButton(disable);
        styleButton(selectionStatus);

        enable.addActionListener(e -> {
            ConstructionRadialSelection.setDragButton(ConstructionRadialSelection.DragButton.LEFT);
            ConstructionRadialSelection.setWorkerControlEnabled(true);
            setStatus(ConstructionRadialSelection.getStatus());
        });
        disable.addActionListener(e -> {
            ConstructionRadialSelection.setWorkerControlEnabled(false);
            setStatus(ConstructionRadialSelection.getStatus());
        });
        selectionStatus.addActionListener(e -> {
            if (ConstructionRadialSelection.hasCommittedWorkerSelection()) {
                queueRadialBatch("workerselectionstatus", null,
                        "Selection Status queued. Check game chat for persistent Worker IDs.");
            } else {
                setStatus(ConstructionRadialSelection.getStatus());
            }
        });

        JPanel buttons = new JPanel(new GridLayout(0, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.add(enable);
        buttons.add(disable);
        buttons.add(selectionStatus);
        card.add(buttons);
        return card;
    }

    private JPanel createAllowedJobsCard() {
        JPanel card = ConsoleTheme.createCard("Selected Worker Commands");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Role presets, Pause/Resume and Allowed Jobs apply to the committed drag selection only.",
                3));
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
            queueRadialBatch("workerselectionpreset", choice.key,
                    choice.displayName + " queued for the committed selection.");
        });

        JPanel presetRow = new JPanel(new GridLayout(1, 2, 7, 7));
        presetRow.setOpaque(false);
        presetRow.setAlignmentX(LEFT_ALIGNMENT);
        presetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        presetRow.add(workerRolePreset);
        presetRow.add(applyPreset);
        card.add(presetRow);
        card.add(Box.createVerticalStrut(8));

        JButton pause = new JButton("Pause Selection");
        JButton resume = new JButton("Resume Selection");
        styleButton(pause);
        styleButton(resume);

        pause.addActionListener(e -> queueRadialBatch(
                "workerselectionpause", "on",
                "Pause queued for the committed selection."));
        resume.addActionListener(e -> queueRadialBatch(
                "workerselectionpause", "off",
                "Resume queued for the committed selection."));

        JPanel pauseRow = new JPanel(new GridLayout(1, 2, 7, 7));
        pauseRow.setOpaque(false);
        pauseRow.setAlignmentX(LEFT_ALIGNMENT);
        pauseRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        pauseRow.add(pause);
        pauseRow.add(resume);
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

        JButton jobsStatus = new JButton("Selection Jobs Status");
        JButton enableAll = new JButton("Enable All Jobs");
        JButton disableAll = new JButton("Disable All Jobs");

        styleButton(jobsStatus);
        styleButton(enableAll);
        styleButton(disableAll);

        jobsStatus.addActionListener(e -> queueRadialBatch(
                "workerselectionstatus", null,
                "Selection Jobs Status queued."));
        enableAll.addActionListener(e -> {
            for (JCheckBox checkBox : workerJobCheckBoxes) {
                checkBox.setSelected(true);
            }
            queueRadialBatch("workerselectionjobsall", "on",
                    "Enable All Jobs queued for the committed selection.");
        });
        disableAll.addActionListener(e -> {
            for (JCheckBox checkBox : workerJobCheckBoxes) {
                checkBox.setSelected(false);
            }
            queueRadialBatch("workerselectionjobsall", "off",
                    "Disable All Jobs queued for the committed selection.");
        });

        JPanel buttons = new JPanel(new GridLayout(0, 2, 7, 7));
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
        checkBox.addActionListener(e -> queueRadialBatch(
                "workerselectionjob",
                jobKey + " " + (checkBox.isSelected() ? "on" : "off"),
                label + "=" + (checkBox.isSelected() ? "ON" : "OFF")
                        + " queued for the committed selection."));
        return checkBox;
    }

    private JPanel createWorkerDiagnosticsCard() {
        JPanel card = ConsoleTheme.createCard("Worker Diagnostics");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Inspection-only Worker ID. Command ownership remains with the committed world selection.",
                3));
        card.add(Box.createVerticalStrut(8));

        workerSelector.setMaximumSize(new Dimension(120, 30));
        workerSelector.setAlignmentX(LEFT_ALIGNMENT);
        workerSelector.addChangeListener(e ->
                setStatus("Inspecting Worker #" + selectedWorkerId() + "."));
        card.add(workerSelector);
        card.add(Box.createVerticalStrut(8));

        JButton ai = new JButton("AI Status");
        JButton needs = new JButton("Needs Status");
        JButton progress = new JButton("Progress Status");
        JButton all = new JButton("All Worker Status");

        styleButton(ai);
        styleButton(needs);
        styleButton(progress);
        styleButton(all);

        ai.addActionListener(e -> queue(
                "itembrowser settlement workerai " + selectedWorkerId(),
                "AI Status queued for Worker #" + selectedWorkerId() + "."));
        needs.addActionListener(e -> queue(
                "itembrowser settlement workerneeds " + selectedWorkerId(),
                "Needs Status queued for Worker #" + selectedWorkerId() + "."));
        progress.addActionListener(e -> queue(
                "itembrowser settlement workerprogress " + selectedWorkerId(),
                "Progress Status queued for Worker #" + selectedWorkerId() + "."));
        all.addActionListener(e -> queue(
                "itembrowser settlement workerallstatus",
                "All Worker Status queued. Check game chat."));

        JPanel buttons = new JPanel(new GridLayout(0, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.add(ai);
        buttons.add(needs);
        buttons.add(progress);
        buttons.add(all);
        card.add(buttons);
        return card;
    }

    private JPanel createStorageCard() {
        JPanel card = ConsoleTheme.createCard("Storage");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Live persistent settlement storage controls.",
                2));
        card.add(Box.createVerticalStrut(8));

        JButton statusButton = new JButton("Storage Status");
        JButton reset = new JButton("Reset All Storage");

        styleButton(statusButton);
        styleButton(reset);

        statusButton.addActionListener(e -> queue(
                "itembrowser settlement resources",
                "Storage Status queued. Check game chat."));
        reset.addActionListener(e -> queue(
                "itembrowser settlement storagereset",
                "Settlement storage reset queued."));

        JPanel buttons = new JPanel(new GridLayout(1, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        buttons.add(statusButton);
        buttons.add(reset);
        card.add(buttons);
        return card;
    }

    private JPanel createProcessingCard() {
        JPanel card = ConsoleTheme.createCard("Processing");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Phase 3 processing core. Current test recipe: 2 Wood -> 1 Plank. "
                + "Physical workstation/worker binding comes after this storage transaction passes.",
                4));
        card.add(Box.createVerticalStrut(8));

        JButton statusButton = new JButton("Processing Status");
        JButton primeWood = new JButton("Prime 10 Wood");
        JButton processOne = new JButton("Saw 1 Plank");
        JButton selfTest = new JButton("Processing Self-Test");

        styleButton(statusButton);
        styleButton(primeWood);
        styleButton(processOne);
        styleButton(selfTest);

        statusButton.addActionListener(e -> queue(
                "itembrowser settlement processing",
                "Processing Status queued. Check game chat."));
        primeWood.addActionListener(e -> queue(
                "itembrowser settlement storageset wood 10",
                "Wood storage set to 10 for processing tests."));
        processOne.addActionListener(e -> queue(
                "itembrowser settlement process saw-planks 1",
                "Saw Planks x1 queued."));
        selfTest.addActionListener(e -> queue(
                "itembrowser settlement processingselftest",
                "Processing Self-Test queued. Expect PASS."));

        JPanel buttons = new JPanel(new GridLayout(2, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.add(statusButton);
        buttons.add(primeWood);
        buttons.add(processOne);
        buttons.add(selfTest);
        card.add(buttons);
        return card;
    }

    private JPanel createNeedsHudCard() {
        JPanel card = ConsoleTheme.createCard("Needs HUD Debug");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Client-only Hunger / Thirst / Energy demo values remain here until the live per-worker metadata seam replaces them.",
                4));
        card.add(Box.createVerticalStrut(8));

        JSpinner[] spinners = {
                demoHungerValue, demoThirstValue, demoEnergyValue, needsArcScale
        };
        for (JSpinner spinner : spinners) {
            spinner.setMaximumSize(new Dimension(100, 30));
            spinner.setFocusable(false);
        }

        javax.swing.event.ChangeListener previewChange = e -> {
            ConstructionRadialSelection.setWorkerNeedsPreviewValues(
                    ((Number) demoHungerValue.getValue()).intValue(),
                    ((Number) demoThirstValue.getValue()).intValue(),
                    ((Number) demoEnergyValue.getValue()).intValue());
            ConstructionRadialSelection.setWorkerNeedsArcScalePercent(
                    ((Number) needsArcScale.getValue()).intValue());
            setStatus(ConstructionRadialSelection.getWorkerNeedsPreviewStatus());
        };
        for (JSpinner spinner : spinners) {
            spinner.addChangeListener(previewChange);
        }

        JPanel values = new JPanel(new GridLayout(4, 2, 7, 7));
        values.setOpaque(false);
        values.setAlignmentX(LEFT_ALIGNMENT);
        values.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        values.add(ConsoleTheme.createWrappedText("Hunger", 1));
        values.add(demoHungerValue);
        values.add(ConsoleTheme.createWrappedText("Thirst", 1));
        values.add(demoThirstValue);
        values.add(ConsoleTheme.createWrappedText("Energy", 1));
        values.add(demoEnergyValue);
        values.add(ConsoleTheme.createWrappedText("HUD scale %", 1));
        values.add(needsArcScale);
        card.add(values);
        card.add(Box.createVerticalStrut(8));

        JButton enable = new JButton("Enable Needs HUD");
        JButton disable = new JButton("Disable Needs HUD");
        JButton hudStatus = new JButton("Needs HUD Status");

        styleButton(enable);
        styleButton(disable);
        styleButton(hudStatus);

        enable.addActionListener(e -> {
            previewChange.stateChanged(null);
            ConstructionRadialSelection.setWorkerNeedsPreviewEnabled(true);
            setStatus(ConstructionRadialSelection.getWorkerNeedsPreviewStatus());
        });
        disable.addActionListener(e -> {
            ConstructionRadialSelection.setWorkerNeedsPreviewEnabled(false);
            setStatus(ConstructionRadialSelection.getWorkerNeedsPreviewStatus());
        });
        hudStatus.addActionListener(e ->
                setStatus(ConstructionRadialSelection.getWorkerNeedsPreviewStatus()));

        JPanel buttons = new JPanel(new GridLayout(0, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.add(enable);
        buttons.add(disable);
        buttons.add(hudStatus);
        card.add(buttons);
        return card;
    }

    private JPanel createDevelopmentToolsCard() {
        JPanel card = ConsoleTheme.createCard("Development Tools");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Asset Studio remains the primary Construction object/rail discovery tool.",
                3));
        card.add(Box.createVerticalStrut(8));

        JButton studio = new JButton("Open Asset Studio");
        styleButton(studio);
        studio.addActionListener(e -> {
            ObjectLabWindow.openEmpty();
            setStatus("Matrix3 Asset Studio opened.");
        });

        JPanel buttons = new JPanel(new GridLayout(1, 1, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        buttons.add(studio);
        card.add(buttons);
        return card;
    }

    private JPanel createStatusCard() {
        JPanel card = ConsoleTheme.createCard("Output");
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
            setStatus("Worker Control: drag-select one or more live settlement workers first.");
            return;
        }
        StringBuilder command = new StringBuilder("itembrowser settlement ")
                .append(operation);
        if (argument != null && argument.trim().length() > 0) {
            command.append(' ').append(argument.trim());
        }
        queue(command.toString(), success + " Selected workers="
                + ConstructionRadialSelection.getCommittedWorkerCount() + ".");
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
