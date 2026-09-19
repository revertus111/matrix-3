package game.console;

import game.ClientConsoleBridge;
import game.ConstructionPaletteOverlay;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
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
            "Ready. Bundle 1.4 Worker #1 arrival is active.", 4);

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

        JPanel buttons = new JPanel(new GridLayout(0, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 376));
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
        card.add(buttons);

        card.add(Box.createVerticalStrut(10));
        card.add(ConsoleTheme.createWrappedText(
                "These buttons use the existing server-authoritative settlement command bridge. No settlement behavior is duplicated in the Client Console.",
                3));
        return card;
    }

    private JPanel createPersistenceCard() {
        JPanel card = ConsoleTheme.createCard("Bundle 1.4 Worker #1 arrival");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "1. Enter Settlement. Worker #1 should arrive automatically because Starter Shelter is already COMPLETE.\n"
                + "2. Run Worker Arrival Check; expect PASS for exactly one persistent worker and exactly one live NPC projection.\n"
                + "3. Worker Status shows stable id/name/definition/home coordinates plus saved/runtime counts.\n"
                + "4. Exit and re-enter; the same Worker #1 id must rebuild without creating a duplicate.\n"
                + "5. This slice intentionally keeps the worker stationary/non-combat; Allowed Jobs and gather/haul AI are the next Bundle 1.4 patch.",
                8));
        return card;
    }

    private JPanel createStatusCard() {
        JPanel card = ConsoleTheme.createCard("Test output");
        card.add(Box.createVerticalStrut(9));
        status.setForeground(ConsoleTheme.ACCENT);
        card.add(status);
        return card;
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
