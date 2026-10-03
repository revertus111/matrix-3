package game.console;

import game.DevModeBridge;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/**
 * Responsive Client Console presentation for Live Inspect.
 *
 * Scene target ownership remains in LiveInspectOverlay / DevModeBridge. This
 * panel only reads immutable display snapshots and invokes the existing inspector
 * actions.
 */
public final class LiveInspectPanel extends JScrollPane {

    private static final long serialVersionUID = 5326796721667417358L;
    private static final long ACTION_STATUS_MILLIS = 2500L;

    private final JToggleButton enabledButton = new JToggleButton();
    private final JButton lockButton = new JButton();
    private final JButton copyButton = new JButton("Copy snapshot");
    private final JButton openToolButton = new JButton("Open tool");
    private final JButton placeButton = new JButton("Live place object");
    private final JLabel status = new JLabel();

    private final JLabel stateValue = valueLabel();
    private final JLabel typeValue = valueLabel();
    private final JLabel nameValue = valueLabel();
    private final JLabel definitionValue = valueLabel();
    private final JLabel modelsValue = valueLabel();
    private final JLabel animationsValue = valueLabel();
    private final JLabel relationshipValue = valueLabel();
    private final JLabel routeValue = valueLabel();
    private final JLabel visualTimeValue = valueLabel();
    private final JLabel worldTileValue = valueLabel();
    private final JLabel regionValue = valueLabel();
    private final JLabel chunkValue = valueLabel();
    private final JLabel runtimeValue = valueLabel();

    private final Timer refreshTimer = new Timer(150, e -> refreshFromInspector());
    private long statusOverrideUntil;

    public LiveInspectPanel() {
        ViewportWidthPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(20, 18, 20, 18));
        content.setMinimumSize(new Dimension(0, 0));

        content.add(ConsoleTheme.titleLabel("LIVE INSPECT"));
        content.add(Box.createVerticalStrut(4));
        content.add(ConsoleTheme.subtitleLabel("Live Matrix3 scene target inspection"));
        content.add(Box.createVerticalStrut(18));
        content.add(createControlCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createTargetCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createAssetsCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createWorldCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createRuntimeCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createActionsCard());
        content.add(Box.createVerticalGlue());

        setViewportView(content);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(this);

        refreshTimer.setCoalesce(true);
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) {
                return;
            }
            if (isShowing()) {
                refreshFromInspector();
                refreshTimer.start();
            } else {
                refreshTimer.stop();
            }
        });

        refreshFromInspector();
    }

    private JPanel createControlCard() {
        JPanel card = ConsoleTheme.createCard("Inspector");

        enabledButton.setAlignmentX(LEFT_ALIGNMENT);
        enabledButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        ConsoleTheme.styleButton(enabledButton);
        enabledButton.addActionListener(e -> {
            LiveInspectOverlay.setEnabled(enabledButton.isSelected());
            statusOverrideUntil = 0L;
            refreshFromInspector();
            if (enabledButton.isSelected() && !DevModeBridge.isEnabled()) {
                setActionStatus("Live Inspect is ON, but Dev Mode must be enabled in Settings for normal world targets.");
            }
        });

        ConsoleTheme.styleStatus(status, true);

        JLabel note = wrappedValue(
                "F10 toggles Live Inspect without opening this panel. Inspection keeps using Matrix3's existing scene/menu target ownership.");

        card.add(Box.createVerticalStrut(9));
        card.add(enabledButton);
        card.add(Box.createVerticalStrut(9));
        card.add(status);
        card.add(Box.createVerticalStrut(9));
        card.add(note);
        return card;
    }

    private JPanel createTargetCard() {
        JPanel card = ConsoleTheme.createCard("Target");
        addField(card, "State", stateValue);
        addField(card, "Type", typeValue);
        addField(card, "Name", nameValue);
        addField(card, "Definition ID", definitionValue);
        return card;
    }

    private JPanel createAssetsCard() {
        JPanel card = ConsoleTheme.createCard("Assets");
        addField(card, "Model IDs", modelsValue);
        addField(card, "Animation IDs", animationsValue);
        return card;
    }

    private JPanel createWorldCard() {
        JPanel card = ConsoleTheme.createCard("World");
        addField(card, "World tile", worldTileValue);
        addField(card, "Region", regionValue);
        addField(card, "Chunk", chunkValue);
        return card;
    }

    private JPanel createRuntimeCard() {
        JPanel card = ConsoleTheme.createCard("Runtime");
        addField(card, "Relationship", relationshipValue);
        addField(card, "Open route", routeValue);
        addField(card, "Visual time", visualTimeValue);
        addField(card, "Runtime owner", runtimeValue);
        return card;
    }

    private JPanel createActionsCard() {
        JPanel card = ConsoleTheme.createCard("Actions");

        configureAction(lockButton, e -> {
            LiveInspectOverlay.DisplayState before = LiveInspectOverlay.getDisplayState();
            if (!before.isEnabled()) {
                setActionStatus("Enable Live Inspect before locking a target.");
            } else if (!before.hasTarget() && !before.isLocked()) {
                setActionStatus("Hover a world target before locking it.");
            } else {
                LiveInspectOverlay.toggleLock();
                setActionStatus(LiveInspectOverlay.isLocked()
                        ? "Target locked. Hover updates are paused."
                        : "Target unlocked. Hover updates resumed.");
            }
            refreshFromInspector();
        });

        configureAction(copyButton, e -> setActionStatus(
                LiveInspectOverlay.copyCurrentToClipboard()
                        ? "Current Live Inspect snapshot copied."
                        : "Nothing is available to copy."));

        configureAction(openToolButton, e -> setActionStatus(
                LiveInspectOverlay.openCurrentTool()
                        ? "Opened the verified specialist tool route."
                        : "The current target has no verified direct tool route."));

        configureAction(placeButton, e -> {
            String result = LiveInspectOverlay.armCurrentObjectPlacement();
            setActionStatus(result == null
                    ? "Live place is available only for an inspected object."
                    : result);
        });

        JLabel shortcuts = wrappedValue(
                "F9 lock/unlock   F8 open tool   Ctrl+C copy   O live-place object   F10 toggle   Ctrl+F8 step   Ctrl+F9 pause/resume   Ctrl+F10 speed");

        card.add(Box.createVerticalStrut(9));
        card.add(lockButton);
        card.add(Box.createVerticalStrut(7));
        card.add(copyButton);
        card.add(Box.createVerticalStrut(7));
        card.add(openToolButton);
        card.add(Box.createVerticalStrut(7));
        card.add(placeButton);
        card.add(Box.createVerticalStrut(10));
        card.add(shortcuts);
        return card;
    }

    private void configureAction(JButton button, java.awt.event.ActionListener listener) {
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        ConsoleTheme.styleButton(button);
        button.addActionListener(listener);
    }

    private void addField(JPanel card, String labelText, JLabel value) {
        JLabel label = new JLabel(labelText);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        label.setAlignmentX(LEFT_ALIGNMENT);

        value.setAlignmentX(LEFT_ALIGNMENT);

        card.add(Box.createVerticalStrut(9));
        card.add(label);
        card.add(Box.createVerticalStrut(2));
        card.add(value);
    }

    private void refreshFromInspector() {
        LiveInspectOverlay.DisplayState state = LiveInspectOverlay.getDisplayState();

        enabledButton.setSelected(state.isEnabled());
        enabledButton.setText(state.isEnabled() ? "Live Inspect: ON" : "Live Inspect: OFF");
        lockButton.setText(state.isLocked() ? "Unlock target" : "Lock target");

        setValue(stateValue, state.getState());
        setValue(typeValue, state.getType());
        setValue(nameValue, state.getName());
        setValue(definitionValue, state.getDefinitionId());
        setValue(modelsValue, state.getModelIds());
        setValue(animationsValue, state.getAnimationIds());
        setValue(relationshipValue, state.getRelationship());
        setValue(routeValue, state.getRoute());
        setValue(visualTimeValue, state.getVisualTime());
        setValue(worldTileValue, state.getWorldTile());
        setValue(regionValue, state.getRegion());
        setValue(chunkValue, state.getChunk());
        setValue(runtimeValue, state.getRuntime());

        lockButton.setEnabled(state.isEnabled() && (state.hasTarget() || state.isLocked()));
        copyButton.setEnabled(state.isEnabled() && state.hasTarget());
        openToolButton.setEnabled(state.isEnabled() && state.canOpenTool());
        placeButton.setEnabled(state.isEnabled() && state.canPlaceObject());

        if (System.currentTimeMillis() >= statusOverrideUntil) {
            setStatusText(baseStatus(state));
        }
    }

    private static String baseStatus(LiveInspectOverlay.DisplayState state) {
        if (!state.isEnabled()) {
            return "Live Inspect is OFF. Enable it here or press F10 in the game view.";
        }
        if (!DevModeBridge.isEnabled()) {
            return "Live Inspect is ON. Enable Dev Mode in Settings for normal world target inspection.";
        }
        if (state.isLocked()) {
            return "LOCKED - the current target is held until F9 or Unlock target.";
        }
        if (state.hasTarget()) {
            return "HOVER - live target data is updating from Matrix3 scene ownership.";
        }
        return "HOVER - move the cursor over the game world.";
    }

    private void setActionStatus(String text) {
        statusOverrideUntil = System.currentTimeMillis() + ACTION_STATUS_MILLIS;
        setStatusText(text);
    }

    private void setStatusText(String text) {
        String safe = text == null ? "" : text;
        status.setText("<html>" + escapeHtml(safe) + "</html>");
        status.setToolTipText(safe);
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel("-");
        label.setFont(ConsoleTheme.BODY_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        label.setVerticalAlignment(SwingConstants.TOP);
        return label;
    }

    private static JLabel wrappedValue(String value) {
        JLabel label = valueLabel();
        setValue(label, value);
        return label;
    }

    private static void setValue(JLabel label, String value) {
        String safe = value == null ? "-" : value;
        label.setText("<html>" + escapeHtml(safe) + "</html>");
        label.setToolTipText(safe);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 113004339580339056L;

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
