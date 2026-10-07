package game.console;

import game.AlternateCharacterController;
import game.TpLinkController;
import game.TpLinkWorkbench;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Locale;
import java.util.function.DoubleConsumer;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTextField;
import javax.swing.Timer;

/** Responsive TP Link development workspace inside the N64 console. */
final class TpLinkPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int REFRESH_MS = 100;

    private final JLabel controllerValue = valueLabel();
    private final JLabel animationValue = valueLabel();
    private final JLabel movementValue = valueLabel();
    private final JLabel facingValue = valueLabel();
    private final JLabel targetValue = valueLabel();
    private final JLabel attackValue = valueLabel();
    private final JLabel assetValue = valueLabel();
    private final JLabel geometryValue = valueLabel();
    private final JLabel textureValue = valueLabel();
    private final JLabel failureValue = valueLabel();

    private final JCheckBox runeScapeClipping = new JCheckBox("RuneScape clipping (tile authority)");
    private final JCheckBox textureSampling = new JCheckBox("Use DMK UV/palette colour sampling", true);

    private NumericControl worldScale;
    private NumericControl widthScale;
    private NumericControl heightScale;
    private NumericControl depthScale;
    private NumericControl yaw;
    private NumericControl offsetX;
    private NumericControl offsetY;
    private NumericControl offsetZ;
    private NumericControl turnSpeed;
    private NumericControl contactFrame;
    private NumericControl animationSpeed;
    private NumericControl movementHold;

    private final Timer refreshTimer;

    TpLinkPanel() {
        super(new BorderLayout());
        setBackground(ConsoleTheme.PANEL);
        setOpaque(true);

        VerticalScrollPanel content = new VerticalScrollPanel();
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(10, 8, 14, 8));

        content.add(createStatusCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createFitCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createMovementCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createCombatCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createAnimationRenderCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createPlacementCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createDiagnosticsCard());
        content.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(scroll);
        add(scroll, BorderLayout.CENTER);

        syncControls();
        refreshTimer = new Timer(REFRESH_MS, e -> refreshStatus());
        refreshTimer.setCoalesce(true);
        refreshStatus();
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

    private JPanel createStatusCard() {
        JPanel card = ConsoleTheme.createCard("TP Link runtime");
        card.add(Box.createVerticalStrut(6));
        card.add(ConsoleTheme.createWrappedText(
                "Ctrl+Shift+L toggles TP Link. WASD uses the shared alternate-character controller, F plays the authentic sword action, and Shift holds Matrix target lock.", 3));
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createValueRow("Controller", controllerValue));
        card.add(ConsoleTheme.createValueRow("Animation", animationValue));
        card.add(ConsoleTheme.createValueRow("Movement", movementValue));
        card.add(ConsoleTheme.createValueRow("Facing", facingValue));
        card.add(ConsoleTheme.createValueRow("Target", targetValue));
        card.add(ConsoleTheme.createValueRow("Attack", attackValue));
        return card;
    }

    private JPanel createFitCard() {
        JPanel card = ConsoleTheme.createCard("Player fit");
        card.add(Box.createVerticalStrut(6));
        card.add(ConsoleTheme.createWrappedText(
                "World scale 5.0 is the accepted height baseline. Width/depth remain model-local so Link keeps the same proportions while turning.", 3));
        card.add(Box.createVerticalStrut(8));

        worldScale = numeric("World scale", 0.10D, 20.0D, 0.05D, 2,
                value -> TpLinkWorkbench.setWorldScale((float) value));
        widthScale = numeric("Body width", 0.25D, 2.0D, 0.01D, 2,
                value -> TpLinkWorkbench.setModelWidth((float) value));
        heightScale = numeric("Body height", 0.25D, 2.0D, 0.01D, 2,
                value -> TpLinkWorkbench.setModelHeight((float) value));
        depthScale = numeric("Body depth", 0.25D, 2.0D, 0.01D, 2,
                value -> TpLinkWorkbench.setModelDepth((float) value));
        yaw = numeric("Yaw correction", -360.0D, 360.0D, 1.0D, 0,
                value -> TpLinkWorkbench.setYawOffsetDegrees((float) value));
        card.add(worldScale);
        card.add(widthScale);
        card.add(heightScale);
        card.add(depthScale);
        card.add(yaw);
        card.add(Box.createVerticalStrut(8));

        JPanel actions = actionGrid(2);
        JButton rsFit = button("RS fit");
        JButton nativeFit = button("TP proportions");
        JButton reset = button("Reset fit");
        rsFit.addActionListener(e -> {
            TpLinkWorkbench.useRuneScapeFitProportions();
            syncControls();
        });
        nativeFit.addActionListener(e -> {
            TpLinkWorkbench.useNativeProportions();
            syncControls();
        });
        reset.addActionListener(e -> {
            TpLinkWorkbench.resetPresentation();
            syncControls();
        });
        actions.add(rsFit);
        actions.add(nativeFit);
        actions.add(reset);
        card.add(actions);
        return card;
    }

    private JPanel createMovementCard() {
        JPanel card = ConsoleTheme.createCard("Movement");
        card.add(Box.createVerticalStrut(6));
        card.add(ConsoleTheme.createWrappedText(
                "TP Link does not own X/Z integration or a private movement-speed profile. The shared AlternateCharacterController owns camera-relative WASD, timing, horizontal state, clipping and restore behavior.", 4));
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createValueRow(
                "Movement owner", fixedValue("SHARED AlternateCharacterController")));

        turnSpeed = numeric("Facing turn speed (deg/sec)", 30.0D, 2160.0D, 30.0D, 0,
                value -> TpLinkWorkbench.setControllerTurnSpeed((float) value));
        card.add(turnSpeed);
        card.add(Box.createVerticalStrut(6));

        styleCheckBox(runeScapeClipping);
        runeScapeClipping.addActionListener(e -> AlternateCharacterController.setRuneScapeClippingEnabled(
                runeScapeClipping.isSelected()));
        card.add(runeScapeClipping);
        card.add(Box.createVerticalStrut(8));

        JPanel actions = actionGrid(2);
        JButton reset = button("Reset facing");
        reset.addActionListener(e -> {
            TpLinkWorkbench.resetFacingTuning();
            syncControls();
        });
        actions.add(reset);
        card.add(actions);
        return card;
    }

    private JPanel createCombatCard() {
        JPanel card = ConsoleTheme.createCard("Sword combat");
        card.add(Box.createVerticalStrut(6));
        card.add(ConsoleTheme.createWrappedText(
                "F plays the authentic TP sword clip once. At the contact frame Matrix resolves the locked/forward NPC and sends the existing server-authoritative manual melee intent. RuneScape damage, accuracy, cooldown and XP remain authoritative.", 5));
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createValueRow("Verified sword socket", fixedValue("0x9 handL / 0xA weaponL")));

        contactFrame = numeric("Contact frame", 0.0D, 120.0D, 1.0D, 0,
                value -> TpLinkWorkbench.setCombatContactFrame((float) value));
        card.add(contactFrame);
        card.add(Box.createVerticalStrut(8));

        JPanel actions = actionGrid(2);
        JButton preview = button("Preview sword");
        JButton stop = button("Stop / AUTO");
        preview.addActionListener(e -> TpLinkController.playSwordPreview());
        stop.addActionListener(e -> TpLinkController.cancelSwordAction());
        actions.add(preview);
        actions.add(stop);
        card.add(actions);
        return card;
    }

    private JPanel createAnimationRenderCard() {
        JPanel card = ConsoleTheme.createCard("Animation / render");
        card.add(Box.createVerticalStrut(6));
        animationSpeed = numeric("Animation speed", 0.05D, 4.0D, 0.05D, 2,
                value -> TpLinkWorkbench.setAnimationSpeed((float) value));
        movementHold = numeric("Walk -> idle hold (ms)", 0.0D, 2000.0D, 10.0D, 0,
                value -> TpLinkWorkbench.setMovementHoldMillis((int) Math.round(value)));
        card.add(animationSpeed);
        card.add(movementHold);
        card.add(Box.createVerticalStrut(6));

        styleCheckBox(textureSampling);
        textureSampling.addActionListener(e -> TpLinkWorkbench.setTextureColorSamplingEnabled(
                textureSampling.isSelected()));
        card.add(textureSampling);
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createValueRow("Texture path", textureValue));
        card.add(ConsoleTheme.createValueRow("Smoothing", fixedValue("NORMAL/WELD PASS STILL REQUIRED")));
        card.add(Box.createVerticalStrut(8));

        JPanel actions = actionGrid(2);
        JButton reload = button("Reload DMK");
        JButton resetAnim = button("Reset animation");
        reload.addActionListener(e -> TpLinkWorkbench.requestAssetReload());
        resetAnim.addActionListener(e -> {
            TpLinkWorkbench.resetAnimation();
            syncControls();
        });
        actions.add(reload);
        actions.add(resetAnim);
        card.add(actions);
        return card;
    }

    private JPanel createPlacementCard() {
        JPanel card = ConsoleTheme.createCard("Fine placement");
        card.add(Box.createVerticalStrut(6));
        offsetX = numeric("Local X", -1000.0D, 1000.0D, 1.0D, 0,
                value -> TpLinkWorkbench.setOffsetX((float) value));
        offsetY = numeric("Local Y", -1000.0D, 1000.0D, 1.0D, 0,
                value -> TpLinkWorkbench.setOffsetY((float) value));
        offsetZ = numeric("Local Z", -1000.0D, 1000.0D, 1.0D, 0,
                value -> TpLinkWorkbench.setOffsetZ((float) value));
        card.add(offsetX);
        card.add(offsetY);
        card.add(offsetZ);
        return card;
    }

    private JPanel createDiagnosticsCard() {
        JPanel card = ConsoleTheme.createCard("Diagnostics");
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createValueRow("DMK", assetValue));
        card.add(ConsoleTheme.createValueRow("Geometry", geometryValue));
        card.add(ConsoleTheme.createValueRow("Last failure", failureValue));
        return card;
    }

    private void syncControls() {
        set(worldScale, TpLinkWorkbench.getWorldScale());
        set(widthScale, TpLinkWorkbench.getModelWidth());
        set(heightScale, TpLinkWorkbench.getModelHeight());
        set(depthScale, TpLinkWorkbench.getModelDepth());
        set(yaw, TpLinkWorkbench.getYawOffsetDegrees());
        set(offsetX, TpLinkWorkbench.getOffsetX());
        set(offsetY, TpLinkWorkbench.getOffsetY());
        set(offsetZ, TpLinkWorkbench.getOffsetZ());
        set(turnSpeed, TpLinkWorkbench.getControllerTurnSpeed());
        set(contactFrame, TpLinkWorkbench.getCombatContactFrame());
        set(animationSpeed, TpLinkWorkbench.getAnimationSpeed());
        set(movementHold, TpLinkWorkbench.getMovementHoldMillis());
        textureSampling.setSelected(TpLinkWorkbench.isTextureColorSamplingEnabled());
        runeScapeClipping.setSelected(AlternateCharacterController.isRuneScapeClippingEnabled());
    }

    private void refreshStatus() {
        controllerValue.setText(TpLinkController.isActive() ? "TP_LINK ACTIVE" : "OFF");
        animationValue.setText(TpLinkWorkbench.getActiveClip() + " frame "
                + TpLinkWorkbench.getActiveFrame() + " | idle "
                + TpLinkWorkbench.getIdleFrames() + " walk "
                + TpLinkWorkbench.getWalkFrames() + " sword "
                + TpLinkWorkbench.getSwordFrames());
        movementValue.setText(TpLinkController.isMoving() ? "MOVING / SHARED" : "IDLE / SHARED");
        facingValue.setText(String.format(Locale.ROOT, "%.1f deg",
                Float.valueOf(TpLinkController.getFacingYawDegrees())));
        int target = TpLinkController.getLockedTargetIndex();
        targetValue.setText(target < 0 ? "NONE" : "NPC " + target);
        attackValue.setText(TpLinkController.getAttackStatus());
        assetValue.setText(TpLinkWorkbench.isAssetLoaded() ? "Loaded" : "Not loaded");
        geometryValue.setText(TpLinkWorkbench.getVertexCount() + " v / "
                + TpLinkWorkbench.getTriangleCount() + " tri / "
                + TpLinkWorkbench.getJointCount() + " joints");
        textureValue.setText(TpLinkWorkbench.isTexturePresent()
                ? (TpLinkWorkbench.isTextureColorSamplingEnabled()
                        ? "DMK UV sample -> face colour" : "vertex colour only")
                : "No TEXT chunk");
        failureValue.setText(TpLinkWorkbench.getLastFailure());
        failureValue.setToolTipText(TpLinkWorkbench.getAssetPath());
        runeScapeClipping.setSelected(AlternateCharacterController.isRuneScapeClippingEnabled());
        textureSampling.setSelected(TpLinkWorkbench.isTextureColorSamplingEnabled());

        set(worldScale, TpLinkWorkbench.getWorldScale());
        set(widthScale, TpLinkWorkbench.getModelWidth());
        set(heightScale, TpLinkWorkbench.getModelHeight());
        set(depthScale, TpLinkWorkbench.getModelDepth());
        set(yaw, TpLinkWorkbench.getYawOffsetDegrees());
        set(offsetX, TpLinkWorkbench.getOffsetX());
        set(offsetY, TpLinkWorkbench.getOffsetY());
        set(offsetZ, TpLinkWorkbench.getOffsetZ());
        set(turnSpeed, TpLinkWorkbench.getControllerTurnSpeed());
        set(contactFrame, TpLinkWorkbench.getCombatContactFrame());
        set(animationSpeed, TpLinkWorkbench.getAnimationSpeed());
        set(movementHold, TpLinkWorkbench.getMovementHoldMillis());
    }

    private static NumericControl numeric(String label, double min, double max,
            double step, int decimals, DoubleConsumer consumer) {
        return new NumericControl(label, min, max, step, decimals, consumer);
    }

    private static void set(NumericControl control, double value) {
        if (control != null) {
            control.setValue(value);
        }
    }

    private static JPanel actionGrid(int columns) {
        JPanel panel = new JPanel(new GridLayout(0, columns, 6, 4)) {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getMaximumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }
        };
        panel.setOpaque(false);
        panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return panel;
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        ConsoleTheme.styleButton(button);
        return button;
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

    private static void styleCheckBox(JCheckBox checkBox) {
        checkBox.setFont(ConsoleTheme.SMALL_FONT);
        checkBox.setForeground(ConsoleTheme.TEXT);
        checkBox.setBackground(ConsoleTheme.CARD);
        checkBox.setOpaque(true);
        checkBox.setFocusPainted(false);
        checkBox.setAlignmentX(JComponent.LEFT_ALIGNMENT);
    }

    /** Width-tracking vertical content: no horizontal Client Console scrolling. */
    private static final class VerticalScrollPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        VerticalScrollPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(18, visibleRect.height - 18);
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

    /** Compact Client Console numeric editor: direct entry plus +/- buttons. */
    private static final class NumericControl extends JPanel {
        private static final long serialVersionUID = 1L;

        private final double min;
        private final double max;
        private final double step;
        private final int decimals;
        private final DoubleConsumer consumer;
        private final JTextField field = new JTextField();

        NumericControl(String labelText, double min, double max, double step,
                int decimals, DoubleConsumer consumer) {
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
            minus.setPreferredSize(new Dimension(34, 28));
            plus.setPreferredSize(new Dimension(34, 28));

            field.setPreferredSize(new Dimension(76, 29));
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
                field.setText(format(value));
            }
        }

        private void adjust(double delta) {
            apply(parse() + delta);
        }

        private void commitField() {
            apply(parse());
        }

        private double parse() {
            try {
                return Double.parseDouble(field.getText().trim());
            } catch (RuntimeException ignored) {
                return min;
            }
        }

        private void apply(double value) {
            double clamped = value < min ? min : value > max ? max : value;
            field.setText(format(clamped));
            consumer.accept(clamped);
        }

        private String format(double value) {
            return String.format(Locale.ROOT, "% ." + decimals + "f",
                    Double.valueOf(value)).trim();
        }
    }
}
