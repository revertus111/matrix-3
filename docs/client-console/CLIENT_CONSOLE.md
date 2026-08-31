# Matrix3 Client Console

## Purpose

The Matrix3 Client Console is the primary in-client developer control surface for Matrix3. It should keep the convenience of the old 718 Client Console while being cleaner, easier to navigate, and much more deliberate about system ownership.

The visual direction is a RuneLite-style developer sidebar: a narrow vertical icon rail attached to the client with one active tool panel beside it. This is a layout/workflow reference only; do not copy RuneLite code or architecture blindly.

The console is a **developer tool**, not a second game engine. It must call existing Matrix3 client/server APIs, commands, and content systems instead of quietly becoming an alternate owner of gameplay behavior.

## Core rules

1. Matrix3 remains authoritative for gameplay, commands, permissions, networking, persistence, combat, and world behavior.
2. The 718 Client Console is reference material for useful ideas and lessons only.
3. Keep the console modular. The shell owns navigation/layout; each panel owns only its own UI and direct feature wiring.
4. Do not create giant catch-all classes or panels.
5. Add new sections because real development work needs them, not because they might be useful someday.
6. Large specialist tools may open external windows; the sidebar should remain a fast control/navigation surface.
7. Owner-only actions must still respect the authoritative Matrix3 rights/command path.
8. The console must remain fully usable when the client is not maximized; no important control may become unreachable because of window size.
9. The console uses a permanent intentional dark theme. Default Java/Swing appearance is not an acceptable finished visual style.
10. Resizing, DPI/display scaling, and persisted layout state are architecture requirements, not late polish.

## Target layout

```text
+-------------------------------------------------------+
| Matrix 3                                              |
|                                                       |
|                                      +----+----------+|
|                                      | O  |          ||
|                                      | >_ |          ||
|              GAME VIEW               | P  |  ACTIVE  ||
|                                      | W  |  PANEL   ||
|                                      | D  |          ||
|                                      | T  |          ||
|                                      +----+----------+|
+-------------------------------------------------------+
```

The icon rail lives on the side of the client. Selecting an icon opens its panel. Selecting the active icon again should close/collapse the panel where practical.

## Interaction goals

- Thin vertical icon rail.
- One active panel at a time.
- Selected icon is visually obvious.
- Tooltips on icons.
- Scrollable panel content.
- Consistent spacing, controls, and section headers.
- Resizable panel width.
- Remember last selected panel and panel width.
- Search/filter boxes for large data sets such as commands.
- Avoid modal dialogs for common actions.
- Graceful reflow/collapse/scrolling when horizontal or vertical space becomes limited.
- Important actions remain reachable at every supported client size.

## UI/UX contract

These requirements are mandatory from the first shell implementation.

### Never cut controls off

The Client Console must never require fullscreen or maximized mode to expose functionality.

If available space becomes too small, the UI must respond through one or more of:

- layout reflow,
- scrollable content,
- collapsible groups,
- compact/icon-only navigation,
- draggable/resizable regions,
- sensible minimum sizes.

A button, field, status area, or other important control must not simply fall outside the visible client bounds.

Normal console UI must not rely on fixed absolute `x/y` positioning. Use appropriate layout managers and containers such as `BorderLayout`, `CardLayout`, `GridBagLayout`, `BoxLayout`, `JScrollPane`, `JSplitPane`, or an equally robust Matrix3-compatible layout approach. Fixed coordinates are only acceptable where the existing game/rendering architecture genuinely requires them and must not be used as the normal developer-panel layout strategy.

### Supported window-size behavior

The layout must be intentionally tested at multiple practical client sizes rather than only while maximized. Initial acceptance targets:

- 1280x720 minimum practical desktop target,
- 1600x900 normal,
- 1920x1080 large,
- 3440x1440 ultrawide,
- maximized/native monitor size.

The exact minimum may be adjusted later from verified Matrix3 client constraints, but no implementation may quietly assume fullscreen.

At smaller sizes, preserving access to controls is more important than preserving the widest visual presentation.

### DPI and display scaling

The console must tolerate common Windows display scaling, including at least:

- 100%,
- 125%,
- 150%.

Acceptance means text remains readable, controls do not overlap, bottom rows remain reachable, navigation remains usable, and resize behavior does not depend on one font/DPI measurement.

Do not hard-code heights/widths solely around one developer machine's font metrics.

## Permanent dark theme and modern visual system

The Client Console is dark-theme only unless the project explicitly changes this authority later.

The intended presentation is closer to modern developer tooling such as RuneLite, Discord, or a modern IDE than to default Java Swing controls.

### Theme direction

- Charcoal/near-black primary surfaces.
- Slightly lighter nested panels/cards.
- Restrained, consistent accent treatment.
- Subtle borders/separators rather than heavy bevels.
- Clear hover, pressed, focused, disabled, and selected states.
- Consistent typography and hierarchy.
- Consistent spacing/padding tokens.
- Clean monochrome/simple icons where practical.
- Rounded or otherwise modernized controls where practical without destabilizing the client.

### Explicitly avoid

- Default Swing/Metal-looking buttons as finished UI.
- Beveled/1990s Java borders.
- Random per-panel fonts/colors.
- Giant walls of controls with no visual grouping.
- Frequent `JOptionPane`-style modal flows for normal actions.
- Styling duplicated independently in every panel.

Swing may remain the implementation technology, but it must be treated as a rendering/toolkit layer rather than accepted as the visual design.

## Resizing and customization

The user should be able to shape the console around the task instead of being forced into one fixed layout.

### Required V1 customization

- Resize the main Client Console width with a draggable boundary where compatible with the Matrix3 frame.
- Collapse/reopen the console.
- Resize internal split regions where a panel genuinely needs multiple work areas.
- Collapse/expand logical panel sections.
- Preserve usable minimum sizes so resizing cannot permanently hide controls.
- Provide a safe reset-to-default layout path if persisted geometry becomes invalid.

### Layout persistence

Where a stable existing settings/preferences path exists, remember at least:

- console width,
- last active panel,
- expanded/collapsed section state,
- splitter positions that materially affect workflow,
- command favorites when the Commands panel gains them.

Persisted state must be validated/clamped against the current monitor/client bounds. A layout saved on a large display must not reopen with controls stranded off-screen on a smaller display.

### Later customization, not V1

Only when real use justifies it:

- sidebar icon reordering,
- hide/show unused panels,
- customizable accent color,
- icon-size choices,
- detachable panels,
- named workspace layouts.

Possible future workspaces might include Boss Development or Item Work, but V1 must not become a workspace designer.

## Presentation architecture

Presentation/styling must be separated from feature behavior.

Conceptually:

```text
Client Console
  -> theme
      -> colors
      -> typography
      -> spacing
      -> shared controls
  -> shell
      -> sidebar
      -> panel host
      -> layout state
  -> panels
      -> owner
      -> commands
      -> player
      -> debug
```

This is a responsibility model, not a requirement to create empty packages/classes. Use the smallest file structure that keeps shared theme/layout behavior centralized and prevents each panel from inventing its own UI conventions.

## V1 scope

V1 deliberately stays small:

1. **Sidebar shell**
2. **Owner panel**
3. **Commands panel**
4. **Player panel**
5. **Debug panel**

Do not add World, Combat, NPC, Item, Model, FX, Boss, or other specialist panels until an actual content task needs them.

## Panel responsibilities

### Owner

Owner-only quick actions and development controls.

Suggested first groups:

- Account/rights summary.
- Quick actions such as heal/restore/teleport where an existing authoritative action already exists.
- Save-related actions where Matrix3 exposes a safe existing path.
- Small developer toggles that do not belong in another panel.

The Owner panel must not implement its own permission or save systems.

### Commands

A searchable, clickable front end to the existing Matrix3 command authority.

Desired workflow:

```text
Search commands...

Favorites
  tele
  item
  npc

Player
  item
  bank
  heal

World
  tele
  coords

Admin
  kick
  mute
```

Selecting a command may expose structured arguments, for example:

```text
item
----------------
Item ID: [ 4151 ]
Amount:  [ 1    ]

[ Execute ]
```

Rules:

- Do not duplicate command logic in the client console.
- Prefer routing execution through the same authoritative server command/permission path.
- Command metadata/search/favorites are UI concerns; command behavior remains server-owned.
- Only expose commands proven safe/appropriate for the current rights level.
- Search/header/action areas should remain reachable while long command lists scroll independently where practical.

### Player

Fast developer visibility into the local player.

Potential V1 data:

- Username/display name.
- Rights level.
- Position/plane.
- Region information if already available through a stable client path.
- Basic state useful for debugging.

This panel should start read-only unless a real development need justifies an action.

### Debug

Small diagnostic controls and identifiers that help current development.

Potential examples:

- Coordinate display.
- NPC/object/interface identifiers where Matrix3 already exposes the needed data.
- Focused debug toggles.
- Recent diagnostic state useful for active work.

Do not turn V1 Debug into a packet sniffer, full cache editor, or giant logging framework.

## Architecture target

Conceptually:

```text
Client
  -> ClientConsole
      -> ConsoleSidebar
      -> OwnerPanel
      -> CommandsPanel
      -> PlayerPanel
      -> DebugPanel
```

The exact class names and hooks must be chosen only after scanning the Matrix3 client frame/bootstrap and the minimum relevant old 718 Client Console references.

Preferred panel contract concept:

```java
interface ConsolePanel {
    String getName();
    Icon getIcon();
    JComponent getComponent();
}
```

This is a design direction, not a pre-approved exact API. Use the smallest structure that fits the actual Matrix3 client.

## Separation of concerns

### Console shell owns

- Sidebar placement.
- Panel selection.
- Open/close behavior.
- Shared sizing/layout.
- Shared visual treatment/theme application.
- Validated persisted UI preferences.
- Responsive behavior when available size changes.

### Individual panels own

- Their controls.
- Their display formatting.
- Direct calls into documented Matrix3/client-console bridges.

### Console does not own

- Command semantics.
- Server permissions.
- Player persistence.
- Combat calculations.
- NPC logic.
- Cache decoding.
- World lifecycle.

If a panel needs one of those systems, it calls the existing authority.

## External tool rule

Large specialist tools should not be forced into the sidebar.

Examples that may remain/open as external windows when eventually needed:

- Model viewer/editor.
- NPC editor.
- Item editor.
- Animation tools.
- FX/particle tooling.
- Boss/encounter tooling.

The sidebar may provide a simple `Open ...` action for those tools.

External windows must follow the same dark-theme, resizing, DPI, minimum-size, persistence/clamping, and no-cutoff rules where applicable.

## Future sections

Only add these when development proves a need:

- World
- Combat
- NPC
- Items
- Interfaces
- Tools launcher
- Cache/data diagnostics

A future section is not automatically a future project.

## Implementation sequence

### Phase 1 - scan/design verification

- Inspect only the Matrix3 client frame/bootstrap and immediate layout ownership.
- Inspect only the minimum old 718 Client Console files needed for UI/workflow reference.
- Identify the safest docking hook and client-thread requirements.
- Identify resize/layout ownership and any existing settings/preference path.
- Verify what styling hooks are safe without disturbing game rendering.
- Report exact files/hooks before implementation.

### Phase 2 - shell and theme foundation

- Add sidebar rail.
- Add panel host.
- Add open/close/select behavior.
- Add responsive/resizable console boundary.
- Establish centralized permanent dark-theme primitives/shared controls needed by the shell.
- Ensure content can scroll/reflow rather than clip.
- Add minimal validated UI preference persistence if a stable existing preference path exists.

Exit: an empty/placeholder panel can dock, resize, scroll/reflow, and collapse without affecting gameplay/client rendering, and remains usable in both maximized and non-maximized client windows.

### Phase 3 - V1 panels

Add Owner, Commands, Player, and Debug one at a time as separate vertical slices.

Each panel must have its own exit criteria and targeted runtime test, including non-maximized/resized behavior.

### Phase 4 - refinement

Only after V1 behavior is stable:

- favorites/search refinement,
- fine visual consistency,
- keyboard/UX polish,
- additional customization justified by actual use.

Core dark-theme, resize, no-cutoff, and basic persistence requirements are not Phase 4 polish; they belong in the shell foundation.

## Out of scope for V1

- Porting the old 718 Client Console wholesale.
- Rebuilding ForgeLabs.
- Building every future editor up front.
- Replacing Matrix3 command/rights/persistence systems.
- Refactoring unrelated client frame/rendering code.
- Large combat/cache/network changes.
- Building a general-purpose UI designer or workspace system.

## V1 success criteria

V1 is successful when:

- the console docks cleanly beside the game view,
- the sidebar can open/close and switch panels reliably,
- Owner/Commands/Player/Debug are separated and understandable,
- command UI routes through authoritative Matrix3 behavior rather than duplicating it,
- the normal client remains usable with the console closed,
- the console does not disturb login, rendering, input, resizing, or normal gameplay,
- adding a future panel does not require rewriting the shell,
- no required Client Console control depends on fullscreen/maximized mode to remain reachable,
- controls remain accessible through responsive layout, scrolling, collapsing, or resizing at supported window sizes,
- the dark theme is consistently applied rather than falling back to default Swing presentation,
- resizing and splitter movement do not create permanently hidden/unreachable UI,
- persisted layout state restores safely and is clamped when current window/monitor bounds differ,
- 100%, 125%, and 150% Windows scaling do not cause overlapping or unreachable core controls,
- 1280x720, 1600x900, 1920x1080, and ultrawide layouts are explicitly checked where supported by the base client.

## Priority reminder

The Client Console exists to make content development faster. Once V1 provides the controls needed for active work, return to content rather than continuously expanding the tool.
