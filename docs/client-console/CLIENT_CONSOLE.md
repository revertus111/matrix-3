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
- Remember last selected panel and panel width where practical.
- Search/filter boxes for large data sets such as commands.
- Avoid modal dialogs for common actions.

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
- Shared visual treatment.
- Small persisted UI preferences.

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
- Report exact files/hooks before implementation.

### Phase 2 - shell

- Add sidebar rail.
- Add panel host.
- Add open/close/select behavior.
- Add minimal UI preference persistence if a stable existing preference path exists.

Exit: an empty/placeholder panel can dock and collapse without affecting gameplay/client rendering.

### Phase 3 - V1 panels

Add Owner, Commands, Player, and Debug one at a time as separate vertical slices.

Each panel must have its own exit criteria and targeted runtime test.

### Phase 4 - polish

Only after V1 behavior is stable:

- panel width persistence,
- favorites/search refinement,
- visual consistency,
- keyboard/UX polish.

## Out of scope for V1

- Porting the old 718 Client Console wholesale.
- Rebuilding ForgeLabs.
- Building every future editor up front.
- Replacing Matrix3 command/rights/persistence systems.
- Refactoring unrelated client frame/rendering code.
- Large combat/cache/network changes.

## V1 success criteria

V1 is successful when:

- the console docks cleanly beside the game view,
- the sidebar can open/close and switch panels reliably,
- Owner/Commands/Player/Debug are separated and understandable,
- command UI routes through authoritative Matrix3 behavior rather than duplicating it,
- the normal client remains usable with the console closed,
- the console does not disturb login, rendering, input, resizing, or normal gameplay,
- adding a future panel does not require rewriting the shell.

## Priority reminder

The Client Console exists to make content development faster. Once V1 provides the controls needed for active work, return to content rather than continuously expanding the tool.
