# Client Console Flyout Test List

## Quick runtime acceptance
- Launch Matrix3 and open the Client Console.
- Verify the console now uses the revision-830-era skin: near-black panels, bronze/gold borders and active states, cream primary text and blue secondary/status text.
- Hover the Test rail button: the primary Test Console flyout should open to the left of the rail.
- Hover Con Revamp: the secondary Con Revamp flyout should open to the left of the primary flyout.
- Verify selected rows use the gold/bronze active treatment and hovered rows highlight without clipping.
- Click Settlement, Workers, Production, Conveyors, Debug and Tools in turn; each must load the existing Con Revamp page with no horizontal Con Revamp tab strip visible.
- Click Rail Studio, Rail Classifier, Object Explorer, Live Inspect, Construction, Player, Items, Interfaces, Visual Explorer, Atlas, Boss Research and Ports UI; each must load its existing workspace with no top Test Console tab strip.
- Resize the Client Console while a flyout is open; both flyouts should close and the console should remain usable.
- Collapse/reopen the console and confirm Test navigation still opens and the last selected workspace/section remains selected for the current client session.

## Regression checks
- Owner, Commands, Client Console home and Settings rail buttons must still open/collapse normally.
- Existing buttons, cards, fields, lists and popup menus outside Test Console must remain readable after the shared ConsoleTheme reskin.
- Game canvas/camera input outside the flyout rectangles must remain unchanged.
- Existing Con Revamp buttons, scroll panes and Output status must still function.
