package game.console;

import game.Mario64Diagnostics;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.Timer;

/**
 * N64 developer workspace. Each imported N64 game owns a sub-tab while Matrix3
 * remains the host runtime. The first tab is a read-only Mario 64 flight recorder.
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

        autoScroll.setFont(ConsoleTheme.SMALL_FONT);
        autoScroll.setForeground(ConsoleTheme.TEXT);
        autoScroll.setBackground(ConsoleTheme.PANEL);
        autoScroll.setOpaque(true);
        autoScroll.setFocusPainted(false);

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

    private void refresh() {
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
        } catch (RuntimeException ex) {
            recorderStatus.setText("Clipboard unavailable: " + ex.getClass().getSimpleName());
        }
    }

    private static JLabel valueLabel() {
        JLabel label = ConsoleTheme.createValueLabel();
        label.setText("-");
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
}
