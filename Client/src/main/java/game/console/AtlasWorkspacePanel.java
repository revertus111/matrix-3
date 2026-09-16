package game.console;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * Small Client Console workspace that keeps the established Atlas browser and
 * runtime-evidence workflow under one persistent Atlas rail destination.
 */
public final class AtlasWorkspacePanel extends JPanel {

    private static final long serialVersionUID = -6654115799021089316L;

    private static final String CARD_BROWSER = "browser";
    private static final String CARD_RUNTIME = "runtime";

    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private final JButton browserButton = new JButton("Browser");
    private final JButton runtimeButton = new JButton("Runtime evidence");

    private AtlasRuntimeEvidencePanel runtimePanel;
    private String activeCard = CARD_BROWSER;

    public AtlasWorkspacePanel() {
        super(new BorderLayout());
        setBackground(ConsoleTheme.PANEL);

        cardHost.setBackground(ConsoleTheme.PANEL);
        cardHost.add(new AtlasPanel(), CARD_BROWSER);

        add(buildSwitcher(), BorderLayout.NORTH);
        add(cardHost, BorderLayout.CENTER);
        showCard(CARD_BROWSER);
    }

    private JPanel buildSwitcher() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        bar.setBackground(ConsoleTheme.PANEL);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ConsoleTheme.BORDER));

        ConsoleTheme.styleButton(browserButton);
        ConsoleTheme.styleButton(runtimeButton);
        browserButton.addActionListener(e -> showCard(CARD_BROWSER));
        runtimeButton.addActionListener(e -> showCard(CARD_RUNTIME));

        bar.add(browserButton);
        bar.add(runtimeButton);
        return bar;
    }

    private void showCard(String card) {
        if (CARD_RUNTIME.equals(card) && runtimePanel == null) {
            runtimePanel = new AtlasRuntimeEvidencePanel();
            cardHost.add(runtimePanel, CARD_RUNTIME);
        }
        activeCard = CARD_RUNTIME.equals(card) ? CARD_RUNTIME : CARD_BROWSER;
        cards.show(cardHost, activeCard);
        browserButton.setSelected(CARD_BROWSER.equals(activeCard));
        runtimeButton.setSelected(CARD_RUNTIME.equals(activeCard));
        cardHost.revalidate();
        cardHost.repaint();
    }
}
