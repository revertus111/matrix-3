package game.console;

import game.Mario64Diagnostics;
import game.MarioEquipmentWorkbench;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Locale;
import java.util.function.DoubleConsumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.Timer;

/**
 * N64 developer workspace. Each imported N64 game owns a sub-tab while Matrix3
 * remains the host runtime. Mario currently exposes both the flight recorder and
 * the live foreign-character equipment workbench.
 */
public final class N64Panel extends JPanel {

    private static final long serialVersionUID = -1975211390458618127L;
    private static final int REFRESH_MS = 100;

    private final JTabbedPane gameTabs = new JTabbedPane();
    private final Timer refreshTimer;

    private final JLabel modeValue = valueLabel();
    private final JLabel characterValue = valueLabel();
    private final JLabel playerValue = valueLabel();
    private final JLabel suppressValue = valueLabel();
    private final JLabel airborneValue = valueLabel();
    private final JLabel heightValue = valueLabel();

    private final JLabel bridgeValue = valueLabel();
    private final JLabel failureValue = valueLabel();
    private final JLabel sequenceValue = valueLabel();
    private final JLabel ageValue = valueLabel();
    private final JLabel actionValue = valueLabel();
    private final JLabel animationValue = valueLabel();
    private final JLabel positionValue = valueLabel();
    private final JLabel velocityValue = valueLabel();
    private final JLabel triangleValue = valueLabel();

    private final JLabel cameraValue = valueLabel();
    private final JLabel moveValue = valueLabel();
    private final JLabel physicalValue = valueLabel();
    private final JLabel forwardedValue = valueLabel();

    private final JTextArea eventLog = new JTextArea();
    private final JLabel recorderStatus = new JLabel("Recorder live");
    private final JToggleButton pauseDisplay = new JToggleButton("Pause display");
    private final JCheckBox autoScroll = new JCheckBox("Auto-scroll", true);

    // Mario equipment workbench.
    private final JLabel workbenchModeValue = valueLabel();
    private final JLabel helmetItemValue = valueLabel();
    private final JLabel head3dValue = valueLabel();
    private final JLabel frozenValue = valueLabel();
    private final JLabel faceAngleValue = valueLabel();
    private final JLabel maskCountValue = valueLabel();
    private final JLabel protocolValue = valueLabel();
    private final JLabel semanticValue = valueLabel();
    private final JLabel coverageValue = valueLabel();
    private final JLabel semanticReferenceValue = valueLabel();
    private final JLabel protectedPartsValue = valueLabel();
    private final JLabel removablePartsValue = valueLabel();
    private final JLabel workbenchStatus = new JLabel("Ready");
    private final JToggleButton freezePose = new JToggleButton("Freeze pose");
    private final JCheckBox coverageOnlyWithHelmet = new JCheckBox(
            "Apply semantic coverage only while a helmet is equipped", true);

    // Legacy protocol-v1 geometric fallback controls.
    private final JCheckBox enableHeadMask = new JCheckBox("Enable legacy geometric cut");
    private final JCheckBox maskOnlyWithHelmet = new JCheckBox("Legacy cut only with helmet", true);

    private NumericControl scaleControl;
    private NumericControl xControl;
    private NumericControl yControl;
    private NumericControl zControl;
    private NumericControl yawControl;
    private NumericControl maskStartControl;
    private NumericControl maskRadiusControl;

    private long renderedEventVersion = Long.MIN_VALUE;

    public N64Panel() {
        super(new BorderLayout());
        setBackground(ConsoleTheme.PANEL);
        setOpaque(true);

        add(createHeader(), BorderLayout.NORTH);

        gameTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        gameTabs.setFont(ConsoleTheme.SMALL_FONT);
        gameTabs.setForeground(ConsoleTheme.TEXT);
        gameTabs.setBackground(ConsoleTheme.PANEL);
        gameTabs.addTab("Mario 64", createMarioTab());
        add(gameTabs, BorderLayout.CENTER);

        refreshTimer = new Timer(REFRESH_MS, e -> refresh());
        refreshTimer.setCoalesce(true);
        refresh();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (refreshTimer != null) {
            refreshTimer.start();
        }
    }

    @Override
    public void removeNotify() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        super.removeNotify();
    }

    private JPanel createHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ConsoleTheme.PANEL);
        header.setBorder(ConsoleTheme.panelPadding(16, 18, 8, 18));
        header.add(ConsoleTheme.titleLabel("N64"));
        header.add(Box.createVerticalStrut(3));
        header.add(ConsoleTheme.subtitleLabel(
                "Imported-game diagnostics and development workspaces"));
        return header;
    }

    private JPanel createMarioTab() {
        JPanel host = new JPanel(new BorderLayout());
        host.setBackground(ConsoleTheme.PANEL);

        JTabbedPane marioTabs = new JTabbedPane();
        marioTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        marioTabs.setFont(ConsoleTheme.SMALL_FONT);
        marioTabs.setForeground(ConsoleTheme.TEXT);
        marioTabs.setBackground(ConsoleTheme.PANEL);
        marioTabs.addTab("Runtime", createMarioRuntimeTab());
        marioTabs.addTab("Equipment Workbench", createEquipmentWorkbenchTab());
        host.add(marioTabs, BorderLayout.CENTER);
        return host;
    }

    private JPanel createMarioRuntimeTab() {
        JPanel host = new JPanel(new BorderLayout());
        host.setBackground(ConsoleTheme.PANEL);

        JScrollPane detailsScroll = createDetailsScroll();
        JPanel recorder = createRecorderPanel();

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, detailsScroll, recorder);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setBackground(ConsoleTheme.PANEL);
        split.setDividerSize(6);
        split.setResizeWeight(0.48D);
        split.setContinuousLayout(true);
        host.add(split, BorderLayout.CENTER);
        return host;
    }

    private JScrollPane createDetailsScroll() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(10, 8, 10, 8));

        content.add(createPresentationCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createNativeCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createInputCard());
        content.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(scroll);
        return scroll;
    }

    private JPanel createPresentationCard() {
        JPanel card = ConsoleTheme.createCard("Matrix presentation");
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createValueRow("Controller mode", modeValue));
        card.add(ConsoleTheme.createValueRow("Active character", characterValue));
        card.add(ConsoleTheme.createValueRow("Local player", playerValue));
        card.add(ConsoleTheme.createValueRow("Suppress RuneScape body", suppressValue));
        card.add(ConsoleTheme.createValueRow("Airborne", airborneValue));
        card.add(ConsoleTheme.createValueRow("Height offset", heightValue));
        return card;
    }

    private JPanel createNativeCard() {
        JPanel card = ConsoleTheme.createCard("libsm64 bridge / native frame");
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createValueRow("Bridge", bridgeValue));
        card.add(ConsoleTheme.createValueRow("Failure", failureValue));
        card.add(ConsoleTheme.createValueRow("Sequence", sequenceValue));
        card.add(ConsoleTheme.createValueRow("Frame age", ageValue));
        card.add(ConsoleTheme.createValueRow("Action", actionValue));
        card.add(ConsoleTheme.createValueRow("Animation", animationValue));
        card.add(ConsoleTheme.createValueRow("Position XYZ", positionValue));
        card.add(ConsoleTheme.createValueRow("Velocity XYZ", velocityValue));
        card.add(ConsoleTheme.createValueRow("Triangles", triangleValue));
        return card;
    }

    private JPanel createInputCard() {
        JPanel card = ConsoleTheme.createCard("Controller input");
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createValueRow("Camera forward X/Z", cameraValue));
        card.add(ConsoleTheme.createValueRow("Sampled move X/Y", moveValue));
        card.add(ConsoleTheme.createValueRow("Physical Space/F/Shift", physicalValue));
        card.add(ConsoleTheme.createValueRow("Forwarded A/B/Z", forwardedValue));
        return card;
    }

    private JPanel createRecorderPanel() {
        JPanel host = new JPanel(new BorderLayout(0, 6));
        host.setBackground(ConsoleTheme.PANEL);
        host.setBorder(ConsoleTheme.panelPadding(6, 8, 8, 8));
        host.setMinimumSize(new Dimension(0, 140));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        toolbar.setOpaque(false);

        JButton clear = new JButton("Clear events");
        JButton copySnapshot = new JButton("Copy snapshot");
        JButton copyEvents = new JButton("Copy events");
        ConsoleTheme.styleButton(clear);
        ConsoleTheme.styleButton(copySnapshot);
        ConsoleTheme.styleButton(copyEvents);
        ConsoleTheme.styleButton(pauseDisplay);

        styleCheckBox(autoScroll);
        ConsoleTheme.styleStatus(recorderStatus, true);

        clear.addActionListener(e -> {
            Mario64Diagnostics.clearEvents();
            renderedEventVersion = Long.MIN_VALUE;
            refreshEvents(true);
            recorderStatus.setText("Events cleared; recorder still live");
        });
        copySnapshot.addActionListener(e -> copyToClipboard(
                Mario64Diagnostics.formatSnapshot(Mario64Diagnostics.getSnapshot()),
                "Snapshot copied"));
        copyEvents.addActionListener(e -> copyToClipboard(
                Mario64Diagnostics.getEventLogText(),
                "Event log copied"));
        pauseDisplay.addActionListener(e -> recorderStatus.setText(
                pauseDisplay.isSelected()
                        ? "Display paused; recorder still capturing"
                        : "Recorder live"));

        toolbar.add(clear);
        toolbar.add(copySnapshot);
        toolbar.add(copyEvents);
        toolbar.add(pauseDisplay);
        toolbar.add(autoScroll);
        toolbar.add(recorderStatus);
        host.add(toolbar, BorderLayout.NORTH);

        eventLog.setEditable(false);
        eventLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        eventLog.setForeground(ConsoleTheme.TEXT);
        eventLog.setBackground(ConsoleTheme.INPUT);
        eventLog.setCaretColor(ConsoleTheme.TEXT);
        eventLog.setLineWrap(false);
        eventLog.setBorder(ConsoleTheme.panelPadding(6, 6, 6, 6));

        JScrollPane scroll = new JScrollPane(eventLog);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(scroll);
        scroll.getViewport().setBackground(ConsoleTheme.INPUT);
        host.add(scroll, BorderLayout.CENTER);
        return host;
    }

    private JPanel createEquipmentWorkbenchTab() {
        JPanel host = new JPanel(new BorderLayout());
        host.setBackground(ConsoleTheme.PANEL);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(10, 8, 14, 8));

        content.add(createEquipmentStateCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createHelmetTransformCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createHeadMaskCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createOrientationCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createWorkbenchSaveCard());
        content.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(scroll);
        host.add(scroll, BorderLayout.CENTER);
        return host;
    }

    private JPanel createEquipmentStateCard() {
        JPanel card = ConsoleTheme.createCard("Active equipment / preview state");
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createValueRow("Mario mode", workbenchModeValue));
        card.add(ConsoleTheme.createValueRow("Helmet", helmetItemValue));
        card.add(ConsoleTheme.createValueRow("3D head tracking", head3dValue));
        card.add(ConsoleTheme.createValueRow("Presentation frozen", frozenValue));
        card.add(ConsoleTheme.createValueRow("Bridge protocol", protocolValue));
        card.add(ConsoleTheme.createValueRow("Semantic geometry", semanticValue));
        card.add(ConsoleTheme.createValueRow("Coverage profile", coverageValue));
        card.add(ConsoleTheme.createValueRow("Shared FACE W/H/D", semanticReferenceValue));
        card.add(ConsoleTheme.createValueRow("Masked source triangles", maskCountValue));
        card.add(Box.createVerticalStrut(8));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        ConsoleTheme.styleButton(freezePose);
        freezePose.addActionListener(e -> {
            MarioEquipmentWorkbench.setPresentationFrozen(freezePose.isSelected());
            workbenchStatus.setText(freezePose.isSelected()
                    ? "Pose frozen for calibration"
                    : "Live Mario presentation resumed");
        });
        actions.add(freezePose);
        card.add(actions);
        return card;
    }

    private JPanel createHelmetTransformCard() {
        JPanel card = ConsoleTheme.createCard("Helmet transform calibration");
        card.add(Box.createVerticalStrut(6));
        card.add(ConsoleTheme.createWrappedText(
                "The semantic FACE reference seeds the automatic fit. These live session values are only the final visual correction.", 2));
        card.add(Box.createVerticalStrut(8));

        scaleControl = new NumericControl(
                "Scale multiplier", 0.10D, 5.00D, 0.02D, 2,
                value -> MarioEquipmentWorkbench.setScale((float) value));
        xControl = new NumericControl(
                "Head-local X", -300.0D, 300.0D, 2.0D, 1,
                value -> MarioEquipmentWorkbench.setOffsetX((float) value));
        yControl = new NumericControl(
                "Head-local Y", -300.0D, 300.0D, 2.0D, 1,
                value -> MarioEquipmentWorkbench.setOffsetY((float) value));
        zControl = new NumericControl(
                "Head-local Z", -300.0D, 300.0D, 2.0D, 1,
                value -> MarioEquipmentWorkbench.setOffsetZ((float) value));
        yawControl = new NumericControl(
                "Yaw delta (degrees)", -360.0D, 360.0D, 5.0D, 1,
                value -> MarioEquipmentWorkbench.setYawDegrees((float) value));

        card.add(scaleControl);
        card.add(xControl);
        card.add(yControl);
        card.add(zControl);
        card.add(yawControl);
        card.add(Box.createVerticalStrut(8));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        JButton reset = new JButton("Reset / recalc fit");
        JButton flip = new JButton("Flip helmet 180°");
        JButton copy = new JButton("Copy profile");
        ConsoleTheme.styleButton(reset);
        ConsoleTheme.styleButton(flip);
        ConsoleTheme.styleButton(copy);
        reset.addActionListener(e -> {
            MarioEquipmentWorkbench.resetHelmetTransform();
            workbenchStatus.setText("Helmet transform reset; automatic fit will recalculate");
        });
        flip.addActionListener(e -> {
            MarioEquipmentWorkbench.flipHelmetYaw180();
            workbenchStatus.setText("Helmet yaw changed by 180°");
        });
        copy.addActionListener(e -> copyToClipboard(
                MarioEquipmentWorkbench.formatProfileMarkdown(),
                "Mario equipment profile copied"));
        actions.add(reset);
        actions.add(flip);
        actions.add(copy);
        card.add(actions);
        return card;
    }

    private JPanel createHeadMaskCard() {
        JPanel card = ConsoleTheme.createCard("Semantic helmet coverage / nose protection");
        card.add(Box.createVerticalStrut(6));
        card.add(ConsoleTheme.createWrappedText(
                "Protocol v2 tags Mario geometry before libsm64 flattens it. FULL_HELM_SAFE removes only the cap and named hair parts. FACE, EYES and MOUSTACHE are always protected, so Mario's nose stays with the face mesh.", 4));
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createValueRow("Protected native parts", protectedPartsValue));
        card.add(ConsoleTheme.createValueRow("Removable native parts", removablePartsValue));
        card.add(Box.createVerticalStrut(8));

        styleCheckBox(coverageOnlyWithHelmet);
        coverageOnlyWithHelmet.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        coverageOnlyWithHelmet.addActionListener(e ->
                MarioEquipmentWorkbench.setCoverageOnlyWithHelmet(coverageOnlyWithHelmet.isSelected()));
        card.add(coverageOnlyWithHelmet);
        card.add(Box.createVerticalStrut(8));

        JPanel coverageActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        coverageActions.setOpaque(false);
        JButton keepAll = new JButton("Keep all Mario head parts");
        JButton fullHelm = new JButton("Full helm safe");
        JButton reset = new JButton("Reset coverage");
        ConsoleTheme.styleButton(keepAll);
        ConsoleTheme.styleButton(fullHelm);
        ConsoleTheme.styleButton(reset);
        keepAll.addActionListener(e -> {
            MarioEquipmentWorkbench.useKeepAllCoverage();
            workbenchStatus.setText("Semantic coverage: KEEP_ALL");
        });
        fullHelm.addActionListener(e -> {
            MarioEquipmentWorkbench.useFullHelmSafeCoverage();
            workbenchStatus.setText("Semantic coverage: FULL_HELM_SAFE - face/nose protected");
        });
        reset.addActionListener(e -> {
            MarioEquipmentWorkbench.resetHeadMask();
            workbenchStatus.setText("Coverage and legacy mask reset");
        });
        coverageActions.add(keepAll);
        coverageActions.add(fullHelm);
        coverageActions.add(reset);
        card.add(coverageActions);
        card.add(Box.createVerticalStrut(12));

        card.add(ConsoleTheme.createWrappedText(
                "Legacy protocol-v1 fallback only. These geometric controls are ignored whenever semantic v2 metadata is available; do not use them as the normal fitting path.", 3));
        card.add(Box.createVerticalStrut(6));
        styleCheckBox(enableHeadMask);
        styleCheckBox(maskOnlyWithHelmet);
        enableHeadMask.addActionListener(e -> {
            MarioEquipmentWorkbench.setHeadMaskEnabled(enableHeadMask.isSelected());
            workbenchStatus.setText(enableHeadMask.isSelected()
                    ? "Legacy geometric cut enabled (v1 fallback only)"
                    : "Legacy geometric cut disabled");
        });
        maskOnlyWithHelmet.addActionListener(e ->
                MarioEquipmentWorkbench.setMaskOnlyWithHelmet(maskOnlyWithHelmet.isSelected()));
        enableHeadMask.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        maskOnlyWithHelmet.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        card.add(enableHeadMask);
        card.add(maskOnlyWithHelmet);
        card.add(Box.createVerticalStrut(6));

        maskStartControl = new NumericControl(
                "Legacy cut body height %", 50.0D, 95.0D, 1.0D, 0,
                value -> MarioEquipmentWorkbench.setMaskStartPercent((float) value));
        maskRadiusControl = new NumericControl(
                "Legacy cut radius %", 10.0D, 75.0D, 1.0D, 0,
                value -> MarioEquipmentWorkbench.setMaskRadiusPercent((float) value));
        card.add(maskStartControl);
        card.add(maskRadiusControl);
        return card;
    }

    private JPanel createOrientationCard() {
        JPanel card = ConsoleTheme.createCard("Orientation truth / anti-backwards diagnostics");
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createValueRow("Native faceAngle", faceAngleValue));
        card.add(ConsoleTheme.createValueRow("Animated head delta", fixedValue("INVERSE / TRANSPOSED")));
        card.add(ConsoleTheme.createValueRow("Delta evidence", fixedValue("VERIFIED V4 backwards -> corrected")));
        card.add(ConsoleTheme.createValueRow("Helmet base yaw", fixedValue("180° + item yaw delta")));
        card.add(ConsoleTheme.createValueRow("Matrix presentation Y", fixedValue("libsm64 Y -> Matrix -Y")));
        card.add(ConsoleTheme.createValueRow("Native face-local axes", fixedValue("Z left/right; +Y face-out (static source)")));
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createWrappedText(
                "Runtime-proven transform signs live in docs/n64/TRANSFORM_CONVENTIONS.md. Native face-local axes are source-derived and are not substitutes for faceAngle when validating world-facing direction.", 3));
        return card;
    }

    private JPanel createWorkbenchSaveCard() {
        JPanel card = ConsoleTheme.createCard("Save / handoff");
        card.add(Box.createVerticalStrut(6));
        card.add(ConsoleTheme.createWrappedText(
                "Save writes the current helmet transform, semantic coverage, part counts and fallback settings to docs/n64/MARIO_EQUIPMENT_RUNTIME.md.", 2));
        card.add(Box.createVerticalStrut(8));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        JButton save = new JButton("Save profile .md");
        JButton copy = new JButton("Copy markdown");
        ConsoleTheme.styleButton(save);
        ConsoleTheme.styleButton(copy);
        save.addActionListener(e -> {
            String result = MarioEquipmentWorkbench.saveProfileMarkdown();
            workbenchStatus.setText(result.startsWith("Save failed") || result.startsWith("Could not")
                    ? result : "Saved: " + result);
        });
        copy.addActionListener(e -> copyToClipboard(
                MarioEquipmentWorkbench.formatProfileMarkdown(),
                "Mario equipment markdown copied"));
        actions.add(save);
        actions.add(copy);
        card.add(actions);
        card.add(Box.createVerticalStrut(8));
        ConsoleTheme.styleStatus(workbenchStatus, true);
        workbenchStatus.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        card.add(workbenchStatus);
        return card;
    }

    private void refresh() {
        refreshWorkbench();

        if (pauseDisplay.isSelected()) {
            return;
        }

        Mario64Diagnostics.Snapshot value = Mario64Diagnostics.getSnapshot();
        modeValue.setText(value.mode);
        characterValue.setText(value.activeCharacter);
        playerValue.setText(value.localPlayerPresent ? "PRESENT" : "NONE");
        suppressValue.setText(value.suppressRuneScape ? "YES" : "NO");
        airborneValue.setText(value.airborne ? "YES" : "NO");
        heightValue.setText(formatFloat(value.heightOffset));

        bridgeValue.setText(value.bridgeFailed
                ? "FAILED"
                : value.bridgeReady ? "READY" : "WAITING / STOPPED");
        failureValue.setText(value.failureReason == null ? "-" : value.failureReason);
        sequenceValue.setText(value.frameSequence < 0L ? "-" : Long.toString(value.frameSequence));
        ageValue.setText(value.frameAgeMillis < 0L ? "-" : value.frameAgeMillis + " ms");
        actionValue.setText(formatAction(value.action));
        animationValue.setText(value.animId < 0
                ? "-"
                : value.animId + " / frame " + value.animFrame);
        positionValue.setText(formatVector(value.x, value.y, value.z));
        velocityValue.setText(formatVector(value.vx, value.vy, value.vz));
        triangleValue.setText(Integer.toString(value.triangleCount));

        cameraValue.setText(formatVector2(value.cameraForwardX, value.cameraForwardZ));
        moveValue.setText(formatVector2(value.moveX, value.moveY));
        physicalValue.setText(booleanTriplet(
                value.physicalJump, value.physicalPrimary, value.physicalModifier));
        forwardedValue.setText(booleanTriplet(value.sentA, value.sentB, value.sentZ));

        refreshEvents(false);
    }

    private void refreshWorkbench() {
        MarioEquipmentWorkbench.Snapshot value = MarioEquipmentWorkbench.getSnapshot();
        workbenchModeValue.setText(value.marioMode ? "MARIO" : "OFF");
        helmetItemValue.setText(value.itemId < 0
                ? "NONE"
                : value.itemId + " - " + value.itemName);
        head3dValue.setText(value.head3d ? "ACTIVE" : "WAITING / FALLBACK");
        frozenValue.setText(value.frozen ? "YES" : "NO");
        faceAngleValue.setText(formatFloat(value.faceAngle));
        protocolValue.setText(value.protocolVersion <= 0 ? "-" : "v" + value.protocolVersion);
        semanticValue.setText(value.semanticAvailable ? "AVAILABLE" : "UNAVAILABLE / LEGACY V1");
        coverageValue.setText(value.coverageName);
        semanticReferenceValue.setText(formatVector(
                value.semanticWidth, value.semanticHeight, value.semanticDepth));
        maskCountValue.setText(Integer.toString(value.maskedTriangles));
        protectedPartsValue.setText("FACE " + value.faceTriangles
                + " / EYES " + value.eyesTriangles
                + " / MOUSTACHE " + value.mustacheTriangles);
        removablePartsValue.setText("CAP " + value.capTriangles
                + " / SIDEBURN " + value.sideburnTriangles
                + " / BACK HAIR " + value.backHairTriangles
                + " / OTHER " + value.unknownTriangles);

        freezePose.setSelected(value.frozen);
        coverageOnlyWithHelmet.setSelected(value.coverageOnlyWithHelmet);
        enableHeadMask.setSelected(value.maskEnabled);
        maskOnlyWithHelmet.setSelected(value.maskOnlyWithHelmet);

        if (scaleControl != null) scaleControl.setValue(value.scale);
        if (xControl != null) xControl.setValue(value.x);
        if (yControl != null) yControl.setValue(value.y);
        if (zControl != null) zControl.setValue(value.z);
        if (yawControl != null) yawControl.setValue(value.yawDegrees);
        if (maskStartControl != null) maskStartControl.setValue(value.maskStartPercent);
        if (maskRadiusControl != null) maskRadiusControl.setValue(value.maskRadiusPercent);
    }

    private void refreshEvents(boolean force) {
        long version = Mario64Diagnostics.getEventVersion();
        if (!force && version == renderedEventVersion) {
            return;
        }
        renderedEventVersion = version;
        eventLog.setText(Mario64Diagnostics.getEventLogText());
        if (autoScroll.isSelected()) {
            eventLog.setCaretPosition(eventLog.getDocument().getLength());
        }
    }

    private void copyToClipboard(String text, String successMessage) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection(text == null ? "" : text), null);
            recorderStatus.setText(successMessage);
            workbenchStatus.setText(successMessage);
        } catch (RuntimeException ex) {
            String message = "Clipboard unavailable: " + ex.getClass().getSimpleName();
            recorderStatus.setText(message);
            workbenchStatus.setText(message);
        }
    }

    private static void styleCheckBox(JCheckBox box) {
        box.setFont(ConsoleTheme.SMALL_FONT);
        box.setForeground(ConsoleTheme.TEXT);
        box.setBackground(ConsoleTheme.CARD);
        box.setOpaque(true);
        box.setFocusPainted(false);
    }

    private static JLabel valueLabel() {
        JLabel label = ConsoleTheme.createValueLabel();
        label.setText("-");
        return label;
    }

    private static JLabel fixedValue(String text) {
        JLabel label = valueLabel();
        label.setText(text);
        return label;
    }

    private static String booleanTriplet(boolean a, boolean b, boolean c) {
        return (a ? "1" : "0") + " / " + (b ? "1" : "0") + " / " + (c ? "1" : "0");
    }

    private static String formatAction(long action) {
        if (action < 0L) {
            return "-";
        }
        return String.format(Locale.ROOT, "0x%08X", Long.valueOf(action & 0xffffffffL));
    }

    private static String formatVector(float a, float b, float c) {
        if (!finite(a) || !finite(b) || !finite(c)) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.2f / %.2f / %.2f",
                Float.valueOf(a), Float.valueOf(b), Float.valueOf(c));
    }

    private static String formatVector2(float a, float b) {
        if (!finite(a) || !finite(b)) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.2f / %.2f",
                Float.valueOf(a), Float.valueOf(b));
    }

    private static String formatFloat(float value) {
        if (!finite(value)) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.2f", Float.valueOf(value));
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    /** Compact RuneScape-console styled numeric editor with direct-entry support. */
    private final class NumericControl extends JPanel {
        private static final long serialVersionUID = 1L;

        private final double min;
        private final double max;
        private final double step;
        private final int decimals;
        private final DoubleConsumer consumer;
        private final JTextField field = new JTextField();

        NumericControl(
                String labelText,
                double min,
                double max,
                double step,
                int decimals,
                DoubleConsumer consumer) {
            super(new BorderLayout(8, 0));
            this.min = min;
            this.max = max;
            this.step = step;
            this.decimals = decimals;
            this.consumer = consumer;

            setBackground(ConsoleTheme.CARD);
            setOpaque(true);
            setAlignmentX(JComponent.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

            JLabel label = new JLabel(labelText);
            label.setFont(ConsoleTheme.SMALL_FONT);
            label.setForeground(ConsoleTheme.MUTED_TEXT);
            add(label, BorderLayout.CENTER);

            JPanel editor = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            editor.setOpaque(false);
            JButton minus = new JButton("-");
            JButton plus = new JButton("+");
            ConsoleTheme.styleButton(minus);
            ConsoleTheme.styleButton(plus);
            minus.setPreferredSize(new Dimension(38, 28));
            plus.setPreferredSize(new Dimension(38, 28));

            field.setPreferredSize(new Dimension(84, 29));
            field.setHorizontalAlignment(JTextField.RIGHT);
            ConsoleTheme.styleTextField(field);
            field.addActionListener(e -> commitField());
            field.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    commitField();
                }
            });
            minus.addActionListener(e -> adjust(-step));
            plus.addActionListener(e -> adjust(step));

            editor.add(minus);
            editor.add(field);
            editor.add(plus);
            add(editor, BorderLayout.EAST);
        }

        void setValue(double value) {
            if (!field.hasFocus()) {
                field.setText(formatNumber(value));
            }
        }

        private void adjust(double delta) {
            double current = parseField();
            apply(current + delta);
        }

        private void commitField() {
            apply(parseField());
        }

        private double parseField() {
            try {
                return Double.parseDouble(field.getText().trim());
            } catch (Exception ignored) {
                return min;
            }
        }

        private void apply(double value) {
            double clamped = value < min ? min : value > max ? max : value;
            field.setText(formatNumber(clamped));
            consumer.accept(clamped);
        }

        private String formatNumber(double value) {
            return String.format(Locale.ROOT, "% ." + decimals + "f", Double.valueOf(value)).trim();
        }
    }
}
