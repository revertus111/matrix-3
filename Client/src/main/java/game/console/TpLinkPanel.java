package game.console;

import game.TpLinkWorkbench;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;

/** Developer workspace for the presentation-only TP Link bridge. */
final class TpLinkPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int REFRESH_MS = 100;

    private final JLabel rendererValue = valueLabel();
    private final JLabel assetValue = valueLabel();
    private final JLabel geometryValue = valueLabel();
    private final JLabel animationValue = valueLabel();
    private final JLabel movementValue = valueLabel();
    private final JLabel textureValue = valueLabel();
    private final JLabel failureValue = valueLabel();

    private final JSpinner worldScale = spinner(5.0D, 0.10D, 20.0D, 0.05D);
    private final JSpinner widthScale = spinner(0.92D, 0.25D, 2.0D, 0.01D);
    private final JSpinner heightScale = spinner(1.0D, 0.25D, 2.0D, 0.01D);
    private final JSpinner depthScale = spinner(0.95D, 0.25D, 2.0D, 0.01D);
    private final JSpinner offsetX = spinner(0.0D, -1000.0D, 1000.0D, 1.0D);
    private final JSpinner offsetY = spinner(0.0D, -1000.0D, 1000.0D, 1.0D);
    private final JSpinner offsetZ = spinner(0.0D, -1000.0D, 1000.0D, 1.0D);
    private final JSpinner yaw = spinner(0.0D, -360.0D, 360.0D, 1.0D);

    private final JSpinner animationSpeed = spinner(1.0D, 0.05D, 4.0D, 0.05D);
    private final JSpinner movementThreshold = spinner(0.25D, 0.001D, 5.0D, 0.01D);
    private final JSpinner movementHold = spinner(180.0D, 0.0D, 2000.0D, 10.0D);
    private final JComboBox<TpLinkWorkbench.PreviewAnimation> preview =
            new JComboBox<TpLinkWorkbench.PreviewAnimation>(TpLinkWorkbench.PreviewAnimation.values());
    private final JCheckBox textureSampling = new JCheckBox(
            "Use DMK UV/palette color sampling", true);

    private final Timer refreshTimer;
    private boolean syncing;

    TpLinkPanel() {
        super(new BorderLayout());
        setBackground(ConsoleTheme.PANEL);
        setOpaque(true);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.setFont(ConsoleTheme.SMALL_FONT);
        tabs.setForeground(ConsoleTheme.TEXT);
        tabs.setBackground(ConsoleTheme.PANEL);
        tabs.addTab("Presentation", createPresentationTab());
        tabs.addTab("Animation", createAnimationTab());
        tabs.addTab("Movement", createMovementTab());
        tabs.addTab("Combat", createCombatTab());
        tabs.addTab("Materials", createMaterialsTab());
        tabs.addTab("Diagnostics", createDiagnosticsTab());
        add(tabs, BorderLayout.CENTER);

        bindControls();
        syncControlsFromState();
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

    private JComponent createPresentationTab() {
        JPanel content = verticalContent();
        JPanel card = ConsoleTheme.createCard("TP Link presentation calibration");
        card.add(Box.createVerticalStrut(6));
        card.add(ConsoleTheme.createWrappedText(
                "Height at world scale 5.0 is runtime accepted. Width/depth are applied in TP model space before player yaw, so Link stays the same proportions while turning.", 3));
        card.add(Box.createVerticalStrut(8));
        card.add(spinnerRow("World scale", worldScale));
        card.add(spinnerRow("Body width (local X)", widthScale));
        card.add(spinnerRow("Body height (local Y)", heightScale));
        card.add(spinnerRow("Body depth (local Z)", depthScale));
        card.add(spinnerRow("Local offset X", offsetX));
        card.add(spinnerRow("Local offset Y", offsetY));
        card.add(spinnerRow("Local offset Z", offsetZ));
        card.add(spinnerRow("Yaw correction degrees", yaw));
        card.add(Box.createVerticalStrut(8));

        JPanel buttons = actionRow();
        JButton rsFit = button("RS fit");
        JButton nativeFit = button("Native TP proportions");
        JButton reset = button("Reset presentation");
        rsFit.addActionListener(e -> {
            TpLinkWorkbench.useRuneScapeFitProportions();
            syncControlsFromState();
        });
        nativeFit.addActionListener(e -> {
            TpLinkWorkbench.useNativeProportions();
            syncControlsFromState();
        });
        reset.addActionListener(e -> {
            TpLinkWorkbench.resetPresentation();
            syncControlsFromState();
        });
        buttons.add(rsFit);
        buttons.add(nativeFit);
        buttons.add(reset);
        card.add(buttons);
        content.add(card);
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JComponent createAnimationTab() {
        JPanel content = verticalContent();
        JPanel card = ConsoleTheme.createCard("Authentic BCK playback");
        card.add(Box.createVerticalStrut(6));
        card.add(ConsoleTheme.createWrappedText(
                "AUTO uses Matrix player motion to select authentic idle/walk. IDLE/WALK/SWORD force a local visual preview only; they do not change RuneScape gameplay authority.", 3));
        card.add(Box.createVerticalStrut(8));
        ConsoleTheme.styleComboBox(preview);
        card.add(labeledComponent("Preview clip", preview));
        card.add(spinnerRow("Playback speed", animationSpeed));
        card.add(spinnerRow("Movement threshold", movementThreshold));
        card.add(spinnerRow("Walk hold after movement (ms)", movementHold));
        card.add(Box.createVerticalStrut(8));
        JPanel buttons = actionRow();
        JButton reset = button("Reset animation tuning");
        reset.addActionListener(e -> {
            TpLinkWorkbench.resetAnimation();
            syncControlsFromState();
        });
        buttons.add(reset);
        card.add(buttons);
        content.add(card);
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JComponent createMovementTab() {
        JPanel content = verticalContent();
        JPanel card = ConsoleTheme.createCard("Movement integration checkpoint");
        card.add(Box.createVerticalStrut(6));
        card.add(ConsoleTheme.createWrappedText(
                "Bundle 2.1 remains presentation-only: RuneScape currently owns movement, collision and input. Phase 3 will add TP_LINK to the already-fixed shared camera-relative alternate-character controller instead of creating another WASD owner.", 5));
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createValueRow("Motion detected", movementValue));
        card.add(ConsoleTheme.createWrappedText(
                "The Animation tab's threshold and hold controls tune when presentation switches between idle and locomotion during this phase.", 3));
        content.add(card);
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JComponent createCombatTab() {
        JPanel content = verticalContent();
        JPanel card = ConsoleTheme.createCard("Sword / future RuneScape combat seam");
        card.add(Box.createVerticalStrut(6));
        card.add(ConsoleTheme.createWrappedText(
                "Verified GameCube sword side: 0x9 handL / 0xA weaponL. The DMK sword clip can be previewed here now. Damage, hit windows, attack speed, NPC authority and RuneScape weapon replacement remain later combat work.", 5));
        card.add(Box.createVerticalStrut(8));
        JButton sword = button("Preview authentic sword BCK");
        JButton auto = button("Return to AUTO");
        sword.addActionListener(e -> {
            TpLinkWorkbench.setPreviewAnimation(TpLinkWorkbench.PreviewAnimation.SWORD);
            syncControlsFromState();
        });
        auto.addActionListener(e -> {
            TpLinkWorkbench.setPreviewAnimation(TpLinkWorkbench.PreviewAnimation.AUTO);
            syncControlsFromState();
        });
        JPanel buttons = actionRow();
        buttons.add(sword);
        buttons.add(auto);
        card.add(buttons);
        content.add(card);
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JComponent createMaterialsTab() {
        JPanel content = verticalContent();
        JPanel textureCard = ConsoleTheme.createCard("Current texture path");
        textureCard.add(Box.createVerticalStrut(6));
        styleCheckBox(textureSampling);
        textureCard.add(textureSampling);
        textureCard.add(Box.createVerticalStrut(6));
        textureCard.add(ConsoleTheme.createWrappedText(
                "This is the current DMK fallback: UV/palette texture color is sampled and baked into Matrix face colors. Turning it off shows vertex-color-only output. Full Matrix texture/material binding is still a separate implementation step.", 5));
        textureCard.add(ConsoleTheme.createValueRow("DMK texture", textureValue));
        content.add(textureCard);
        content.add(Box.createVerticalStrut(10));

        JPanel smoothCard = ConsoleTheme.createCard("Smoothing / normals");
        smoothCard.add(Box.createVerticalStrut(6));
        smoothCard.add(ConsoleTheme.createWrappedText(
                "Current DMK geometry is triangle-expanded (three vertices per triangle), so a cosmetic Smooth checkbox would be fake. Proper smoothing needs imported normals or a UV/material-aware vertex weld with an angle rule. This tab records that boundary instead of silently damaging seams.", 6));
        content.add(smoothCard);
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JComponent createDiagnosticsTab() {
        JPanel content = verticalContent();
        JPanel card = ConsoleTheme.createCard("Live TP renderer state");
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createValueRow("Renderer replacement", rendererValue));
        card.add(ConsoleTheme.createValueRow("DMK", assetValue));
        card.add(ConsoleTheme.createValueRow("Geometry", geometryValue));
        card.add(ConsoleTheme.createValueRow("Animation", animationValue));
        card.add(ConsoleTheme.createValueRow("Movement", movementValue));
        card.add(ConsoleTheme.createValueRow("Texture", textureValue));
        card.add(ConsoleTheme.createValueRow("Last failure", failureValue));
        card.add(Box.createVerticalStrut(8));
        JButton reload = button("Reload local DMK");
        reload.addActionListener(e -> TpLinkWorkbench.requestAssetReload());
        JPanel actions = actionRow();
        actions.add(reload);
        card.add(actions);
        content.add(card);
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private void bindControls() {
        worldScale.addChangeListener(e -> apply(() -> TpLinkWorkbench.setWorldScale(value(worldScale))));
        widthScale.addChangeListener(e -> apply(() -> TpLinkWorkbench.setModelWidth(value(widthScale))));
        heightScale.addChangeListener(e -> apply(() -> TpLinkWorkbench.setModelHeight(value(heightScale))));
        depthScale.addChangeListener(e -> apply(() -> TpLinkWorkbench.setModelDepth(value(depthScale))));
        offsetX.addChangeListener(e -> apply(() -> TpLinkWorkbench.setOffsetX(value(offsetX))));
        offsetY.addChangeListener(e -> apply(() -> TpLinkWorkbench.setOffsetY(value(offsetY))));
        offsetZ.addChangeListener(e -> apply(() -> TpLinkWorkbench.setOffsetZ(value(offsetZ))));
        yaw.addChangeListener(e -> apply(() -> TpLinkWorkbench.setYawOffsetDegrees(value(yaw))));
        animationSpeed.addChangeListener(e -> apply(() -> TpLinkWorkbench.setAnimationSpeed(value(animationSpeed))));
        movementThreshold.addChangeListener(e -> apply(() -> TpLinkWorkbench.setMovementThreshold(value(movementThreshold))));
        movementHold.addChangeListener(e -> apply(() -> TpLinkWorkbench.setMovementHoldMillis(
                ((Number) movementHold.getValue()).intValue())));
        preview.addActionListener(e -> apply(() -> TpLinkWorkbench.setPreviewAnimation(
                (TpLinkWorkbench.PreviewAnimation) preview.getSelectedItem())));
        textureSampling.addActionListener(e -> apply(() -> TpLinkWorkbench.setTextureColorSamplingEnabled(
                textureSampling.isSelected())));
    }

    private void apply(Runnable action) {
        if (!syncing) {
            action.run();
        }
    }

    private void syncControlsFromState() {
        syncing = true;
        try {
            worldScale.setValue((double) TpLinkWorkbench.getWorldScale());
            widthScale.setValue((double) TpLinkWorkbench.getModelWidth());
            heightScale.setValue((double) TpLinkWorkbench.getModelHeight());
            depthScale.setValue((double) TpLinkWorkbench.getModelDepth());
            offsetX.setValue((double) TpLinkWorkbench.getOffsetX());
            offsetY.setValue((double) TpLinkWorkbench.getOffsetY());
            offsetZ.setValue((double) TpLinkWorkbench.getOffsetZ());
            yaw.setValue((double) TpLinkWorkbench.getYawOffsetDegrees());
            animationSpeed.setValue((double) TpLinkWorkbench.getAnimationSpeed());
            movementThreshold.setValue((double) TpLinkWorkbench.getMovementThreshold());
            movementHold.setValue((double) TpLinkWorkbench.getMovementHoldMillis());
            preview.setSelectedItem(TpLinkWorkbench.getPreviewAnimation());
            textureSampling.setSelected(TpLinkWorkbench.isTextureColorSamplingEnabled());
        } finally {
            syncing = false;
        }
    }

    private void refreshStatus() {
        rendererValue.setText(TpLinkWorkbench.isReplacementReady() ? "ACTIVE" : "Not replacing");
        assetValue.setText(TpLinkWorkbench.isAssetLoaded() ? "Loaded" : "Not loaded");
        geometryValue.setText(TpLinkWorkbench.getVertexCount() + " v / "
                + TpLinkWorkbench.getTriangleCount() + " tri / "
                + TpLinkWorkbench.getJointCount() + " joints");
        animationValue.setText(TpLinkWorkbench.getActiveClip() + " frame "
                + TpLinkWorkbench.getActiveFrame() + " | idle "
                + TpLinkWorkbench.getIdleFrames() + " walk "
                + TpLinkWorkbench.getWalkFrames() + " sword "
                + TpLinkWorkbench.getSwordFrames());
        movementValue.setText(TpLinkWorkbench.isMovementDetected() ? "moving" : "idle");
        textureValue.setText(TpLinkWorkbench.isTexturePresent()
                ? (TpLinkWorkbench.isTextureColorSamplingEnabled()
                        ? "DMK sample -> face colour" : "vertex colour only")
                : "No TEXT chunk");
        failureValue.setText(TpLinkWorkbench.getLastFailure());
        failureValue.setToolTipText(TpLinkWorkbench.getAssetPath());
    }

    private static JPanel verticalContent() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(10, 8, 14, 8));
        return content;
    }

    private static JScrollPane scroll(JPanel content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(scroll);
        return scroll;
    }

    private static JPanel spinnerRow(String label, JSpinner spinner) {
        return labeledComponent(label, spinner);
    }

    private static JPanel labeledComponent(String labelText, JComponent component) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(ConsoleTheme.CARD);
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        JLabel label = new JLabel(labelText);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        row.add(label, BorderLayout.WEST);
        row.add(component, BorderLayout.EAST);
        return row;
    }

    private static JSpinner spinner(double value, double min, double max, double step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.setFont(ConsoleTheme.BODY_FONT);
        spinner.setBackground(ConsoleTheme.INPUT);
        spinner.setForeground(ConsoleTheme.TEXT);
        spinner.setPreferredSize(new Dimension(105, 28));
        return spinner;
    }

    private static JPanel actionRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return row;
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        ConsoleTheme.styleButton(button);
        return button;
    }

    private static JLabel valueLabel() {
        return ConsoleTheme.createValueLabel();
    }

    private static void styleCheckBox(JCheckBox checkBox) {
        checkBox.setFont(ConsoleTheme.SMALL_FONT);
        checkBox.setForeground(ConsoleTheme.TEXT);
        checkBox.setBackground(ConsoleTheme.CARD);
        checkBox.setOpaque(true);
        checkBox.setFocusPainted(false);
        checkBox.setAlignmentX(JComponent.LEFT_ALIGNMENT);
    }

    private static float value(JSpinner spinner) {
        return ((Number) spinner.getValue()).floatValue();
    }
}
