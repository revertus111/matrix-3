package game.console;

import game.ClientConsoleInterfaceBridge;
import game.ClientConsoleInterfaceBridge.ComponentSnapshot;
import game.ClientConsoleInterfaceBridge.InterfaceSnapshot;
import game.VisualExplorerSpriteBridge;
import game.VisualExplorerSpriteBridge.SpriteSnapshot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.util.EnumSet;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/**
 * Read-only sprite browse/preview module. Editing intentionally remains absent
 * until a later sprite-editor bundle has a safe write/export authority.
 */
public final class SpriteVisualModulePanel extends JScrollPane implements VisualExplorerModule {

    private static final long serialVersionUID = 6193113621453670189L;
    private static final int PREVIEW_MAX = 240;

    private final javax.swing.JTextField idField = new javax.swing.JTextField();
    private final JLabel preview = new JLabel("No sprite loaded.", SwingConstants.CENTER);
    private final JLabel idValue = ConsoleTheme.createValueLabel();
    private final JLabel sizeValue = ConsoleTheme.createValueLabel();
    private final JLabel contentValue = ConsoleTheme.createValueLabel();
    private final JLabel rangeValue = ConsoleTheme.createValueLabel();
    private final JLabel referenceValue = ConsoleTheme.createValueLabel();
    private final JLabel status = new JLabel("Enter a sprite ID.");

    private final Timer refreshTimer = new Timer(200, e -> refreshFromBridge());

    private int currentSpriteId = -1;
    private long lastSequence = -1L;

    public SpriteVisualModulePanel() {
        ViewportWidthPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(14, 14, 14, 14));

        content.add(createBrowserCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createPreviewCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createInspectorCard());
        content.add(Box.createVerticalGlue());

        setViewportView(content);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(this);

        refreshTimer.setCoalesce(true);
        refreshTimer.start();
    }

    private JPanel createBrowserCard() {
        JPanel card = ConsoleTheme.createCard("Sprite");
        card.add(Box.createVerticalStrut(8));

        JPanel row = new JPanel(new BorderLayout(7, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        ConsoleTheme.styleTextField(idField);
        idField.setToolTipText("Sprite cache group ID.");
        idField.addActionListener(e -> loadTyped());

        JButton load = new JButton("Load");
        ConsoleTheme.styleButton(load);
        load.addActionListener(e -> loadTyped());

        row.add(idField, BorderLayout.CENTER);
        row.add(load, BorderLayout.EAST);
        card.add(row);
        card.add(Box.createVerticalStrut(7));

        JButton previous = new JButton("Previous");
        JButton next = new JButton("Next");
        ConsoleTheme.styleButton(previous);
        ConsoleTheme.styleButton(next);
        previous.addActionListener(e -> request(Math.max(0, currentSpriteId - 1), "previous"));
        next.addActionListener(e -> request(Math.max(0, currentSpriteId + 1), "next"));

        JPanel navigation = new JPanel(new java.awt.GridLayout(1, 2, 7, 7));
        navigation.setOpaque(false);
        navigation.setAlignmentX(LEFT_ALIGNMENT);
        navigation.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        navigation.add(previous);
        navigation.add(next);
        card.add(navigation);

        card.add(Box.createVerticalStrut(7));
        ConsoleTheme.styleStatus(status, false);
        card.add(status);
        return card;
    }

    private JPanel createPreviewCard() {
        JPanel card = ConsoleTheme.createCard("Preview");
        card.add(Box.createVerticalStrut(8));

        preview.setOpaque(true);
        preview.setBackground(ConsoleTheme.INPUT);
        preview.setForeground(ConsoleTheme.MUTED_TEXT);
        preview.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
        preview.setAlignmentX(LEFT_ALIGNMENT);
        preview.setPreferredSize(new Dimension(PREVIEW_MAX, PREVIEW_MAX));
        preview.setMinimumSize(new Dimension(120, 120));
        preview.setMaximumSize(new Dimension(Integer.MAX_VALUE, PREVIEW_MAX));
        card.add(preview);
        return card;
    }

    private JPanel createInspectorCard() {
        JPanel card = ConsoleTheme.createCard("Sprite Details");
        card.add(Box.createVerticalStrut(8));
        addValue(card, "ID", idValue);
        addValue(card, "Canvas", sizeValue);
        addValue(card, "Content", contentValue);
        addValue(card, "Cache range", rangeValue);
        addValue(card, "Current iface", referenceValue);
        card.add(Box.createVerticalStrut(8));

        JButton copy = new JButton("Copy Sprite ID");
        ConsoleTheme.styleButton(copy);
        copy.setAlignmentX(LEFT_ALIGNMENT);
        copy.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        copy.addActionListener(e -> {
            if (currentSpriteId < 0) {
                return;
            }
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(Integer.toString(currentSpriteId)), null);
            setStatus("Copied sprite ID " + currentSpriteId + ".", true);
        });
        card.add(copy);
        return card;
    }

    private void addValue(JPanel card, String name, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel label = new JLabel(name);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        label.setPreferredSize(new Dimension(88, 26));

        row.add(label, BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        card.add(row);
    }

    private void loadTyped() {
        String raw = idField.getText() == null ? "" : idField.getText().trim();
        try {
            int id = Integer.parseInt(raw);
            if (id < 0) {
                throw new NumberFormatException();
            }
            request(id, "typed");
        } catch (NumberFormatException ex) {
            setStatus("Sprite ID must be zero or greater.", false);
        }
    }

    private void request(int spriteId, String reason) {
        currentSpriteId = spriteId;
        idField.setText(Integer.toString(spriteId));
        lastSequence = -1L;
        preview.setIcon(null);
        preview.setText("Loading sprite " + spriteId + "...");
        VisualExplorerSpriteBridge.requestSprite(spriteId);
        setStatus("Queued sprite " + spriteId + " (" + reason + ").", true);
    }

    private void refreshFromBridge() {
        if (!isShowing()) {
            return;
        }

        SpriteSnapshot snapshot = VisualExplorerSpriteBridge.getLatestSnapshot();
        if (snapshot.getSequence() == lastSequence || snapshot.getSpriteId() != currentSpriteId) {
            return;
        }

        lastSequence = snapshot.getSequence();
        setStatus(snapshot.getStatus(), snapshot.isLoaded());
        idValue.setText(snapshot.getSpriteId() < 0 ? "—" : Integer.toString(snapshot.getSpriteId()));
        rangeValue.setText(snapshot.getTotalGroups() <= 0
                ? "—" : "0-" + (snapshot.getTotalGroups() - 1));

        if (!snapshot.isLoaded()) {
            sizeValue.setText("—");
            contentValue.setText("—");
            referenceValue.setText(currentInterfaceReferences(currentSpriteId));
            preview.setIcon(null);
            preview.setText("No preview");
            return;
        }

        sizeValue.setText(snapshot.getWidth() + "x" + snapshot.getHeight());
        contentValue.setText(snapshot.getContentWidth() + "x" + snapshot.getContentHeight());
        referenceValue.setText(currentInterfaceReferences(currentSpriteId));
        renderPreview(snapshot);
    }

    private void renderPreview(SpriteSnapshot snapshot) {
        int width = snapshot.getWidth();
        int height = snapshot.getHeight();
        int[] pixels = snapshot.getPixels();

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);

        double scale = Math.min((double) PREVIEW_MAX / Math.max(1, width),
                (double) PREVIEW_MAX / Math.max(1, height));
        scale = Math.min(8.0D, scale);
        int drawWidth = Math.max(1, (int) Math.round(width * scale));
        int drawHeight = Math.max(1, (int) Math.round(height * scale));

        Image scaled = image.getScaledInstance(drawWidth, drawHeight, Image.SCALE_FAST);
        preview.setText("");
        preview.setIcon(new ImageIcon(scaled));
    }

    private String currentInterfaceReferences(int spriteId) {
        if (spriteId < 0) {
            return "—";
        }

        InterfaceSnapshot snapshot = ClientConsoleInterfaceBridge.getLatestSnapshot();
        if (snapshot.getInterfaceId() < 0) {
            return "none loaded";
        }

        StringBuilder refs = new StringBuilder();
        int count = 0;
        for (ComponentSnapshot component : snapshot.getComponents()) {
            if (component.getSpriteId() != spriteId) {
                continue;
            }
            if (count < 4) {
                if (refs.length() > 0) {
                    refs.append(", ");
                }
                refs.append(snapshot.getInterfaceId()).append(':').append(component.getComponentId());
            }
            count++;
        }

        if (count == 0) {
            return "none in interface " + snapshot.getInterfaceId();
        }
        if (count > 4) {
            refs.append(" +").append(count - 4);
        }
        return refs.toString();
    }

    private void setStatus(String value, boolean accent) {
        status.setText(value == null ? "" : value);
        status.setForeground(accent ? ConsoleTheme.ACCENT : ConsoleTheme.MUTED_TEXT);
    }

    @Override
    public String getModuleId() {
        return "sprites";
    }

    @Override
    public String getTitle() {
        return "Sprites";
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    @Override
    public EnumSet<Capability> getCapabilities() {
        return EnumSet.of(Capability.BROWSE, Capability.PREVIEW,
                Capability.INSPECT, Capability.REFERENCES);
    }

    @Override
    public boolean supports(VisualExplorerAssetRef asset) {
        return asset != null && asset.getKind() == VisualExplorerAssetRef.Kind.SPRITE;
    }

    @Override
    public void openAsset(VisualExplorerAssetRef asset) {
        if (supports(asset)) {
            request(asset.getPrimaryId(),
                    asset.getSource().length() == 0 ? "cross-link" : asset.getSource());
        }
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = -8105752057388666275L;

        @Override
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }
        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            int extent = orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
            return Math.max(16, extent - 16);
        }
        @Override
        public boolean getScrollableTracksViewportWidth() { return true; }
        @Override
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
