package game.console;

import game.ClientConsoleBridge;
import game.ConstructionPaletteOverlay;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

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
    private final JSpinner workerSelector =
            new JSpinner(new SpinnerNumberModel(1, 1, 999999, 1));
    private final java.util.List<JCheckBox> workerJobCheckBoxes =
            new java.util.ArrayList<JCheckBox>();

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
        content.add(createPopulationCard());
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
        content.add(createBundle15Card());
        content.add(Box.createVerticalStrut(12));
        content.add(createPersistenceCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createStatusCard());
        content.add(Box.createVerticalGlue());

        setViewportView(content);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(this);
    }

    private JPanel createRuntimeCard() {
        JPanel card = ConsoleTheme.createCard("Settlement runtime");
        card.add(Box.createVerticalStrut(10));

        JButton enter = new JButton("Enter Settlement");
        JButton settlementStatus = new JButton("Settlement Status");
        JButton exit = new JButton("Exit Settlement");
        JButton palette = new JButton("Open Build Palette");
        JButton savedPieces = new JButton("Saved Pieces");
        JButton savedStateAudit = new JButton("Saved State Audit");
        JButton stateSelfTest = new JButton("State Self-Test");
        JButton finalAutoCheck = new JButton("Final Auto Check");
        JButton resourceStatus = new JButton("Resource Status");
        JButton resourceSelfTest = new JButton("Resource Self-Test");
        JButton shelterStatus = new JButton("Shelter Status");
        JButton shelterSelfTest = new JButton("Shelter Self-Test");
        JButton bundle13FinalCheck = new JButton("Bundle 1.3 Final Check");
        JButton workerStatus = new JButton("Worker Status");
        JButton workerSelfTest = new JButton("Worker Self-Test");
        JButton workerArrivalCheck = new JButton("Worker Arrival Check");
        JButton workerAiStatus = new JButton("Worker AI Status");

        ConsoleTheme.styleButton(enter);
        ConsoleTheme.styleButton(settlementStatus);
        ConsoleTheme.styleButton(exit);
        ConsoleTheme.styleButton(palette);
        ConsoleTheme.styleButton(savedPieces);
        ConsoleTheme.styleButton(savedStateAudit);
        ConsoleTheme.styleButton(stateSelfTest);
        ConsoleTheme.styleButton(finalAutoCheck);
        ConsoleTheme.styleButton(resourceStatus);
        ConsoleTheme.styleButton(resourceSelfTest);
        ConsoleTheme.styleButton(shelterStatus);
        ConsoleTheme.styleButton(shelterSelfTest);
        ConsoleTheme.styleButton(bundle13FinalCheck);
        ConsoleTheme.styleButton(workerStatus);
        ConsoleTheme.styleButton(workerSelfTest);
        ConsoleTheme.styleButton(workerArrivalCheck);
        ConsoleTheme.styleButton(workerAiStatus);

        enter.addActionListener(e -> queue(
                "itembrowser settlement enter",
                "Enter Settlement queued."));
        settlementStatus.addActionListener(e -> queue(
                "itembrowser settlement status",
                "Settlement Status queued. Check the game chat response."));
        exit.addActionListener(e -> queue(
                "itembrowser settlement exit",
                "Exit Settlement queued."));
        palette.addActionListener(e -> {
            ConstructionPaletteOverlay.show();
            setStatus("Construction build palette opened.");
        });
        savedPieces.addActionListener(e -> queue(
                "itembrowser settlement list",
                "Saved Pieces queued. Piece ids / plot coordinates / rotation are in game chat."));
        savedStateAudit.addActionListener(e -> queue(
                "itembrowser settlement audit",
                "Saved State Audit queued. PASS/FAIL will appear in game chat and the server console."));
        stateSelfTest.addActionListener(e -> queue(
                "itembrowser settlement selftest",
                "State Self-Test queued. PASS/FAIL will appear in game chat and the server console."));
        finalAutoCheck.addActionListener(e -> queue(
                "itembrowser settlement finalcheck",
                "Final Auto Check queued. PASS/FAIL will appear in game chat and the server console."));
        resourceStatus.addActionListener(e -> queue(
                "itembrowser settlement resources",
                "Resource Status queued. Settlement-only totals will appear in game chat."));
        resourceSelfTest.addActionListener(e -> queue(
                "itembrowser settlement resourceselftest",
                "Resource Self-Test queued. PASS/FAIL will appear in game chat and the server console."));
        shelterStatus.addActionListener(e -> queue(
                "itembrowser settlement shelter",
                "Shelter Status queued. Exact milestone progress will appear in game chat."));
        shelterSelfTest.addActionListener(e -> queue(
                "itembrowser settlement shelterselftest",
                "Shelter Self-Test queued. PASS/FAIL will appear in game chat and the server console."));
        bundle13FinalCheck.addActionListener(e -> queue(
                "itembrowser settlement bundle13check",
                "Bundle 1.3 Final Check queued. PASS/NOT READY/FAIL will appear in game chat and the server console."));
        workerStatus.addActionListener(e -> queue(
                "itembrowser settlement workers",
                "Worker Status queued. Persistent/runtime worker state will appear in game chat."));
        workerSelfTest.addActionListener(e -> queue(
                "itembrowser settlement workerselftest",
                "Worker Self-Test queued. PASS/FAIL will appear in game chat and the server console."));
        workerArrivalCheck.addActionListener(e -> queue(
                "itembrowser settlement workercheck",
                "Worker Arrival Check queued. PASS/NOT READY/FAIL will appear in game chat and the server console."));
        workerAiStatus.addActionListener(e -> queue(
                "itembrowser settlement workerai " + selectedWorkerId(),
                "Selected Worker AI Status queued. Live work/carry state and storage totals will appear in game chat."));

        JPanel buttons = new JPanel(new GridLayout(0, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 420));
        buttons.add(enter);
        buttons.add(settlementStatus);
        buttons.add(exit);
        buttons.add(palette);
        buttons.add(savedPieces);
        buttons.add(savedStateAudit);
        buttons.add(stateSelfTest);
        buttons.add(finalAutoCheck);
        buttons.add(resourceStatus);
        buttons.add(resourceSelfTest);
        buttons.add(shelterStatus);
        buttons.add(shelterSelfTest);
        buttons.add(bundle13FinalCheck);
        buttons.add(workerStatus);
        buttons.add(workerSelfTest);
        buttons.add(workerArrivalCheck);
        buttons.add(workerAiStatus);
        card.add(buttons);

        card.add(Box.createVerticalStrut(10));
        card.add(ConsoleTheme.createWrappedText(
                "These buttons use the existing server-authoritative settlement command bridge. No settlement behavior is duplicated in the Client Console.",
                3));
        return card;
    }

    private JPanel createPopulationCard() {
        JPanel card = ConsoleTheme.createCard("Phase 2 Population");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Starter shelter capacity is 2 workers. Worker #1 remains the automatic starter worker; "
                + "Recruit Worker #2 uses the new persistent population-capacity owner. New Worker #2 starts with every Allowed Job OFF.",
                5));
        card.add(Box.createVerticalStrut(8));

        JButton statusButton = new JButton("Population Status");
        JButton recruitButton = new JButton("Recruit Worker #2");
        JButton selfTestButton = new JButton("Population Self-Test");
        JButton checkButton = new JButton("Population Check");

        ConsoleTheme.styleButton(statusButton);
        ConsoleTheme.styleButton(recruitButton);
        ConsoleTheme.styleButton(selfTestButton);
        ConsoleTheme.styleButton(checkButton);

        statusButton.addActionListener(e -> queue(
                "itembrowser settlement population",
                "Population Status queued. Capacity/recruitment state will appear in game chat."));
        recruitButton.addActionListener(e -> queue(
                "itembrowser settlement populationrecruit",
                "Worker #2 recruitment queued. Read the authoritative result in game chat."));
        selfTestButton.addActionListener(e -> queue(
                "itembrowser settlement populationselftest",
                "Population Self-Test queued. Read PASS/FAIL in game chat."));
        checkButton.addActionListener(e -> queue(
                "itembrowser settlement populationcheck",
                "Population Check queued. Expect saved=2/runtime=2 after recruitment."));

        JPanel buttons = new JPanel(new GridLayout(2, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.add(statusButton);
        buttons.add(recruitButton);
        buttons.add(selfTestButton);
        buttons.add(checkButton);
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

        ConsoleTheme.styleButton(selectedJobs);
        ConsoleTheme.styleButton(selectedAi);
        ConsoleTheme.styleButton(selectedNeeds);
        ConsoleTheme.styleButton(selectedProgress);
        ConsoleTheme.styleButton(allWorkers);

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
                "All Worker Status queued. Jobs, needs, skills, AI and shared storage will appear in game chat."));

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
                "Server-authoritative allowlist. New workers start with every job OFF. "
                + "Checkbox clicks explicitly set the saved permission; use Jobs Status for authoritative readback.",
                4));
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
        JButton jobsSelfTest = new JButton("Jobs Self-Test");
        JButton enableAll = new JButton("Enable All Jobs");
        JButton disableAll = new JButton("Disable All Jobs");

        ConsoleTheme.styleButton(jobsStatus);
        ConsoleTheme.styleButton(jobsSelfTest);
        ConsoleTheme.styleButton(enableAll);
        ConsoleTheme.styleButton(disableAll);

        jobsStatus.addActionListener(e -> queue(
                "itembrowser settlement workerjobs " + selectedWorkerId(),
                "Jobs Status queued for Worker #" + selectedWorkerId()
                        + ". Authoritative saved permissions will appear in game chat."));
        jobsSelfTest.addActionListener(e -> queue(
                "itembrowser settlement workerjobselftest",
                "Jobs Self-Test queued. PASS/FAIL will appear in game chat and the server console."));
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
        buttons.add(jobsSelfTest);
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
        JButton needsSelfTest = new JButton("Needs Self-Test");
        JButton hungerCritical = new JButton("Set Hunger Critical");
        JButton thirstCritical = new JButton("Set Thirst Critical");
        JButton energyCritical = new JButton("Set Energy Critical");
        JButton resetNeeds = new JButton("Reset Needs");

        ConsoleTheme.styleButton(needsStatus);
        ConsoleTheme.styleButton(needsSelfTest);
        ConsoleTheme.styleButton(hungerCritical);
        ConsoleTheme.styleButton(thirstCritical);
        ConsoleTheme.styleButton(energyCritical);
        ConsoleTheme.styleButton(resetNeeds);

        needsStatus.addActionListener(e -> queue(
                "itembrowser settlement workerneeds " + selectedWorkerId(),
                "Needs Status queued for Worker #" + selectedWorkerId() + "."));
        needsSelfTest.addActionListener(e -> queue(
                "itembrowser settlement workerneedselftest",
                "Needs Self-Test queued. PASS/FAIL will appear in game chat and the server console."));
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
        buttons.add(needsSelfTest);
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
        JButton progressSelfTest = new JButton("Progress Self-Test");
        ConsoleTheme.styleButton(progressStatus);
        ConsoleTheme.styleButton(progressSelfTest);

        progressStatus.addActionListener(e -> queue(
                "itembrowser settlement workerprogress " + selectedWorkerId(),
                "Progress Status queued for Worker #" + selectedWorkerId()
                        + ". Worker skill XP/levels and player Construction XP will appear in game chat."));
        progressSelfTest.addActionListener(e -> queue(
                "itembrowser settlement workerprogressselftest",
                "Progress Self-Test queued. PASS/FAIL will appear in game chat and the server console."));

        JPanel buttons = new JPanel(new GridLayout(1, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        buttons.add(progressStatus);
        buttons.add(progressSelfTest);
        card.add(buttons);
        return card;
    }

    private JPanel createBundle22Card() {
        JPanel card = ConsoleTheme.createCard("Bundle 2.2 Final Gate");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "One-launch acceptance:\n"
                + "1. Bundle 2.2 Self-Test -> PASS.\n"
                + "2. Worker #1: Disable All, then Food + Haul ON. Worker #2: Disable All, then Wood + Haul ON.\n"
                + "3. All Worker Status until both workers show independent work/deposits and shared storage rises.\n"
                + "4. Disable Worker #1 jobs; Worker #2 must continue.\n"
                + "5. Disable both workers + Reset Needs, then Capture 2-Worker Baseline.\n"
                + "6. Exit/re-enter -> Check Baseline PASS; logout/relog/re-enter -> Check Baseline PASS.",
                10));
        card.add(Box.createVerticalStrut(8));

        JButton selfTest = new JButton("Bundle 2.2 Self-Test");
        JButton allStatus = new JButton("All Worker Status");
        JButton capture = new JButton("Capture 2-Worker Baseline");
        JButton check = new JButton("Check 2-Worker Baseline");

        ConsoleTheme.styleButton(selfTest);
        ConsoleTheme.styleButton(allStatus);
        ConsoleTheme.styleButton(capture);
        ConsoleTheme.styleButton(check);

        selfTest.addActionListener(e -> queue(
                "itembrowser settlement bundle22selftest",
                "Bundle 2.2 Self-Test queued. Expect PASS for independent worker targeting/state."));
        allStatus.addActionListener(e -> queue(
                "itembrowser settlement workerallstatus",
                "All Worker Status queued."));
        capture.addActionListener(e -> queue(
                "itembrowser settlement bundle22baseline",
                "Bundle 2.2 baseline capture queued. Disable jobs for both workers first."));
        check.addActionListener(e -> queue(
                "itembrowser settlement bundle22check",
                "Bundle 2.2 baseline check queued. Expect PASS after re-entry/relog."));

        JPanel buttons = new JPanel(new GridLayout(2, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.add(selfTest);
        buttons.add(allStatus);
        buttons.add(capture);
        buttons.add(check);
        card.add(buttons);
        return card;
    }

    private JPanel createBundle15Card() {
        JPanel card = ConsoleTheme.createCard("Bundle 1.5 Final Gate");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "1. Run Self-Test: expect PASS for exact costs plus insufficient/reserved/invalid/occupied no-mutation.\n"
                + "2. In the settlement, wait for carried=none, Disable All Jobs and Reset Needs if needed.\n"
                + "3. Capture Build Baseline.\n"
                + "4. Exit, re-enter, then Check Build Baseline: expect PASS for exact layout/resources/Construction XP.\n"
                + "5. After that PASS, click Exit + Outside Rejection: expect 'You must be inside your settlement to build.'",
                8));
        card.add(Box.createVerticalStrut(8));

        JButton selfTest = new JButton("Bundle 1.5 Self-Test");
        JButton capture = new JButton("Capture Build Baseline");
        JButton check = new JButton("Check Build Baseline");
        JButton outsideReject = new JButton("Exit + Outside Rejection");

        ConsoleTheme.styleButton(selfTest);
        ConsoleTheme.styleButton(capture);
        ConsoleTheme.styleButton(check);
        ConsoleTheme.styleButton(outsideReject);

        selfTest.addActionListener(e -> queue(
                "itembrowser settlement bundle15selftest",
                "Bundle 1.5 Self-Test queued. Read PASS/FAIL in game chat."));
        capture.addActionListener(e -> queue(
                "itembrowser settlement bundle15baseline",
                "Bundle 1.5 baseline capture queued. Read BASELINE/NOT READY in game chat."));
        check.addActionListener(e -> queue(
                "itembrowser settlement bundle15check",
                "Bundle 1.5 baseline comparison queued. Read PASS/FAIL in game chat."));
        outsideReject.addActionListener(e -> {
            String error = ClientConsoleBridge.queueConsoleCommands(new String[] {
                    "itembrowser settlement exit",
                    "settlementbuild wood-fence-test 0 0 0 0"
            });
            setStatus(error == null
                    ? "Exit + outside build rejection queued. Expect the outside-settlement rejection in game chat."
                    : error);
        });

        JPanel buttons = new JPanel(new GridLayout(2, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.add(selfTest);
        buttons.add(capture);
        buttons.add(check);
        buttons.add(outsideReject);
        card.add(buttons);
        return card;
    }

    private JPanel createPersistenceCard() {
        JPanel card = ConsoleTheme.createCard("Bundle 1.4 Final Gate");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "1. Wait for carried=none, then Disable All Jobs. Let any in-flight action settle.\n"
                + "2. Click Capture Stable Baseline. It refuses to arm if any job is ON or a need is critical.\n"
                + "3. Wait a few seconds, then Check Baseline: expect PASS. This proves idle/blocked time awarded no Worker/Construction XP.\n"
                + "4. Exit/re-enter and Check Baseline again: expect PASS.\n"
                + "5. Logout/relog without restarting the server, re-enter, and Check Baseline once more: expect PASS.\n"
                + "6. Re-enable your desired Allowed Jobs after the final PASS.\n"
                + "Optional carryover: zero-Food Hunger block/resupply may still be checked separately.",
                10));
        card.add(Box.createVerticalStrut(8));

        JButton captureGate = new JButton("Capture Stable Baseline");
        JButton checkGate = new JButton("Check Baseline");
        ConsoleTheme.styleButton(captureGate);
        ConsoleTheme.styleButton(checkGate);

        captureGate.addActionListener(e -> queue(
                "itembrowser settlement bundle14gatebaseline",
                "Bundle 1.4 baseline capture queued. Read BASELINE/NOT READY in game chat."));
        checkGate.addActionListener(e -> queue(
                "itembrowser settlement bundle14gatecheck",
                "Bundle 1.4 baseline comparison queued. Read PASS/FAIL in game chat."));

        JPanel buttons = new JPanel(new GridLayout(1, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        buttons.add(captureGate);
        buttons.add(checkGate);
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

    private long selectedWorkerId() {
        Object value = workerSelector.getValue();
        return value instanceof Number ? ((Number) value).longValue() : 1L;
    }

    private void queue(String command, String success) {
        String error = ClientConsoleBridge.queueConsoleCommand(command);
        setStatus(error == null ? success : error);
    }

    private void setStatus(String message) {
        status.setText(message == null ? "" : message);
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
