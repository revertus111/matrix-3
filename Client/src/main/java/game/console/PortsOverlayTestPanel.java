package game.console;

import game.PlayerOwnedPortsTestOverlay;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/**
 * Launch controls for the static 576x324 Player-Owned Ports UI reference.
 */
public final class PortsOverlayTestPanel extends JPanel {

    private static final long serialVersionUID = 2911086224166753057L;

    private final JTextArea status = ConsoleTheme.createWrappedText(
            "Ready. The overlay uses the screenshot's exact 576x324 reference coordinate space.", 4);

    public PortsOverlayTestPanel() {
        super(new BorderLayout());
        setBackground(ConsoleTheme.PANEL);
        setOpaque(true);
        setBorder(ConsoleTheme.panelPadding(16, 16, 16, 16));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        content.add(ConsoleTheme.titleLabel("PORTS UI TEST"));
        content.add(Box.createVerticalStrut(4));
        content.add(ConsoleTheme.subtitleLabel("Player-Owned Ports 576x324 overlay reference"));
        content.add(Box.createVerticalStrut(16));

        JPanel card = ConsoleTheme.createCard("Reference Overlay");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Draws the left Resources panel, six-button command cluster and right Visitors panel directly over the live Matrix3 canvas at 1:1 reference coordinates. The center of the game view remains uncovered.",
                5));
        card.add(Box.createVerticalStrut(10));

        JButton show = new JButton("Show 1:1 Overlay");
        JButton hide = new JButton("Hide Overlay");
        ConsoleTheme.styleButton(show);
        ConsoleTheme.styleButton(hide);
        show.setFocusable(false);
        hide.setFocusable(false);

        show.addActionListener(e -> {
            PlayerOwnedPortsTestOverlay.show();
            status.setText(PlayerOwnedPortsTestOverlay.getStatus());
        });
        hide.addActionListener(e -> {
            PlayerOwnedPortsTestOverlay.hide();
            status.setText(PlayerOwnedPortsTestOverlay.getStatus());
        });

        JPanel buttons = new JPanel(new GridLayout(1, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.add(show);
        buttons.add(hide);
        card.add(buttons);
        card.add(Box.createVerticalStrut(10));

        JTextArea note = ConsoleTheme.createWrappedText(
                "This first test intentionally keeps the reference geometry fixed. Icons and portraits are lightweight vector stand-ins until the original cache sprite/interface assets are identified.",
                4);
        note.setForeground(ConsoleTheme.MUTED_TEXT);
        card.add(note);

        content.add(card);
        content.add(Box.createVerticalStrut(12));

        JPanel output = ConsoleTheme.createCard("Output");
        output.add(Box.createVerticalStrut(9));
        status.setForeground(ConsoleTheme.ACCENT);
        output.add(status);
        content.add(output);
        content.add(Box.createVerticalGlue());

        add(content, BorderLayout.NORTH);
    }
}
