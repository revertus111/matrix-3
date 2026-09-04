# Matrix3 Client Console

## Purpose

The Matrix3 Client Console is the primary in-client developer control surface for Matrix3. It should keep the convenience of the old 718 Client Console while being cleaner, easier to navigate, and much more deliberate about system ownership.

The visual direction is a RuneLite-style developer sidebar: a narrow vertical icon rail attached to the client with one active tool panel beside it. This is a layout/workflow reference only; do not copy RuneLite code, assets, or architecture blindly.

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
10. Resizing, DPI/display scaling, persisted layout state, and workspace restoration are architecture requirements, not late polish.
11. A clean shutdown must preserve the complete valid client/console workspace geometry so the next launch restores the user's layout without requiring repeated resizing.
12. Preference/layout saving must be quiet and debounced; dragging a divider or resizing a window must not write to disk continuously on every mouse event.
13. Client Console work must not stall the game/render thread or Swing event-dispatch thread with file I/O, heavy searches, large scans, or slow tool initialization.
14. Panels should initialize lazily when first opened unless there is a verified reason to initialize them at startup.
15. Console text fields and controls must own keyboard focus while active; game input must not also consume those keystrokes. Returning focus to the game must restore normal game input cleanly.
16. A failure in one developer panel should be isolated where practical so it can report/log its error without taking down the running game client.
17. Client Console UI state should use one small versioned preferences/settings authority rather than separate ad-hoc settings files per panel.
18. Resizing the console must not rebuild/reload unrelated client systems, interfaces, or rendering state. It should feel continuous and immediate.
19. Modern presentation should favor immediate response and restrained transitions; animation must never make common console actions feel delayed or interfere with rendering/input.
20. The rail must use real, consistent icons in finished UI. Single-letter placeholders such as `C`, `O`, `P`, `I`, and `S` are acceptable only during implementation, not as the long-term presentation.
21. Do not rewrite the working shell architecture merely for visual polish. V2 should improve presentation and workflow on top of the established shell/theme/preferences/panel boundaries.

## Target layout

```text
+-------------------------------------------------------+
| Matrix 3                                              |
|                                                       |
|                                      +----+----------+|
|                                      | [] |          ||
|                                      | [] |          ||
|              GAME VIEW               | [] |  ACTIVE  ||
|                                      | [] |  PANEL   ||
|                                      | [] |          ||
|                                      | [] |          ||
|                                      +----+----------+|
+-------------------------------------------------------+
```

The icon rail lives on the side of the client. Selecting an icon opens its panel. Selecting the active icon again should close/collapse the panel where practical.

## Interaction goals

- Thin vertical icon rail.
- Real recognizable icons instead of permanent text-letter buttons.
- One active panel at a time.
- Selected icon is visually obvious through a consistent selected treatment/indicator.
- Tooltips on every rail icon.
- Consistent hover, pressed, disabled, and selected states.
- Scrollable panel content.
- Consistent spacing, controls, and section headers.
- Resizable panel width.
- Remember the complete valid workspace state across clean restarts.
- Search/filter boxes for large data sets such as commands.
- Avoid modal dialogs for common actions.
- Graceful reflow/collapse/scrolling when horizontal or vertical space becomes limited.
- Important actions remain reachable at every supported client size.
- Resizing and panel switching should feel immediate and should not trigger unrelated client reload/rebuild work.
- Keyboard-heavy workflows should be possible where they materially save development time.

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

The console also must protect a sensible minimum game-view size. Dragging the console wider must not allow the actual RuneScape viewport to collapse into an unusable strip. If the requested console width conflicts with the minimum game view, clamp the divider to the nearest valid position.

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
- Rail icons should normally render at a consistent roughly 20-24px visual size inside the existing rail hit target.
- Icons should remain legible at 100%, 125%, and 150% display scaling and should not rely on Unicode glyph availability.
- Use original/project-owned or appropriately licensed icon assets; do not copy RuneLite's icon assets merely to imitate its appearance.
- Rounded or otherwise modernized controls where practical without destabilizing the client.

### Explicitly avoid

- Default Swing/Metal-looking buttons as finished UI.
- Permanent single-letter rail buttons as finished navigation.
- Beveled/1990s Java borders.
- Random per-panel fonts/colors.
- Giant walls of controls with no visual grouping.
- Frequent `JOptionPane`-style modal flows for normal actions.
- Styling duplicated independently in every panel.
- Long/decorative animations that delay common actions.

Swing may remain the implementation technology, but it must be treated as a rendering/toolkit layer rather than accepted as the visual design.

## Resizing and customization

The user should be able to shape the console around the task instead of being forced into one fixed layout.

### Required V1 customization

- Resize the main Client Console width with a draggable boundary where compatible with the Matrix3 frame.
- Collapse/reopen the console.
- Resize internal split regions where a panel genuinely needs multiple work areas.
- Collapse/expand logical panel sections.
- Preserve usable minimum sizes for both console and game view so resizing cannot permanently hide or crush important UI.
- Provide a safe reset-to-default layout path if persisted geometry becomes invalid.

### Workspace and layout persistence

The client must persist its complete valid window/workspace geometry across a clean shutdown and restore it on the next startup.

At minimum, the V1 preference authority should be able to remember, when the existing Matrix3 frame/settings path safely permits it:

- client window x/y position,
- client window width/height,
- maximized/restored state,
- whether the Client Console was open/collapsed,
- console width,
- last active panel,
- expanded/collapsed section state,
- splitter positions that materially affect workflow,
- command favorites when the Commands panel gains them.

Later detachable/external tools should follow the same principle for their own valid window size/position where useful.

The intended behavior is simple: arrange the client and console once, close Matrix3 normally, and the next launch should return to the same valid workspace without requiring manual resizing again.

Persisted geometry must always be validated and clamped against current monitor/client bounds. A layout saved on an ultrawide or second monitor must not reopen with controls stranded off-screen when that monitor is missing or the available resolution has changed.

### Single versioned preference authority

Do not create unrelated preference files for every panel. Prefer one small Client Console settings authority, conceptually similar to:

```text
client-console settings
  version
  window
    x / y / width / height / maximized
  console
    open / width / activePanel
  owner
    collapsedSections
  commands
    splitter / favorites
```

The exact storage format must be chosen only after the Matrix3 settings/preference path is scanned. The principle is one versioned authority with safe defaults and migration/fallback behavior, not a mandatory JSON implementation.

If the settings are missing, malformed, from an unsupported version, or contain impossible geometry, fall back to known-good defaults rather than preventing client startup.

### Quiet/debounced saving

Resize and splitter movement can generate many events. Do not persist every event immediately.

Prefer a debounced/coalesced approach: allow the user to resize continuously, then save after movement settles and/or at clean shutdown. Saving UI preferences must be small and must not introduce visible hitching in the client.

### Reset layout

Provide a clear `Reset Client Console Layout` path once persistence exists. Reset should restore known-good window/console geometry and panel defaults without requiring the user to find and delete settings files manually.

### Later customization, not V1

Only when real use justifies it:

- sidebar icon reordering,
- hide/show unused panels,
- customizable accent color,
- icon-size choices,
- detachable panels,
- named workspace layouts.

Possible future workspaces might include Boss Development or Item Work, but V1 must not become a workspace designer.

## Smoothness and lifecycle contract

The console should feel like part of the client, not a separate utility fighting it.

### Threading and responsiveness

- Do not perform slow file I/O, large data scans, expensive filtering, cache enumeration, or heavy initialization on the game/render thread.
- Do not perform long-running work on the Swing event-dispatch thread.
- UI component mutation should occur on the correct UI thread.
- Any game-state mutation must use the established Matrix3 client/game-thread path rather than bypassing thread ownership for convenience.
- If a panel needs asynchronous work, report loading/error state cleanly and return results to the appropriate owner thread.

The exact thread bridge must be chosen from verified Matrix3 client architecture during the implementation scan.

### Search responsiveness

Search should feel responsive without rebuilding expensive result models for every keystroke.

- Item Browser's current V2 target debounce is **500ms**.
- 500ms is the default safety/performance compromise for the existing item-definition browser unless runtime profiling gives evidence for a different value.
- A substantially longer pause such as the current 1.25-second behavior should not be accepted as finished UX merely because it is safe.
- Cheap local command filtering should remain effectively immediate where it does not create measurable UI/game hitching.
- Debounce expensive model/index/thumbnail work, not basic text feedback such as the visible query/status indicator.

### Lazy panel initialization

The shell should start quickly and should not initialize every current/future developer tool during client startup.

Panels should normally be constructed/initialized on first use and then reused for the session. An unopened expensive panel should not add meaningful startup time or create unrelated failure risk.

### Resize behavior

Dragging the console boundary or an internal splitter should resize existing components/layout continuously. It must not repeatedly rebuild the client frame, reload game interfaces, recreate unrelated render state, or reinitialize panels merely because a divider moved.

Avoid visible flicker and unnecessary allocation/revalidation loops where the existing client architecture permits.

### Input and focus ownership

When a Client Console text field, search box, spinner, shortcut editor, or similar control has focus, its keyboard input must not also trigger game movement/chat/hotkeys.

When the user clicks/focuses the game again, normal game keyboard and mouse handling must resume cleanly. Panel open/close transitions must not leave the client in a stuck mouse/keyboard focus state.

This must be explicitly runtime-tested because input focus bugs can make otherwise-correct tooling unusable.

### Panel failure isolation

Developer tooling is lower authority than the running game client. Where practical, panel creation/refresh/action boundaries should isolate panel-specific failures:

- log/report the error,
- show a small panel-level error state when useful,
- keep the sidebar/client alive,
- avoid taking down normal gameplay because one optional developer panel failed.

Do not hide errors silently; isolate and surface them.

### Restrained animation

Modern does not mean slow. Common navigation, panel opening, resizing, search, and button actions should feel immediate.

Small visual transitions may be used when they improve clarity, but do not add animation loops or delays that compete with the game renderer, complicate resize behavior, or make tooling feel sluggish.

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
      -> icon treatment
  -> shell
      -> sidebar
      -> panel host
      -> layout state
      -> preference authority
  -> panels
      -> dashboard
      -> owner
      -> commands
      -> player
      -> items
      -> settings
```

This is a responsibility model, not a requirement to create empty packages/classes. Use the smallest file structure that keeps shared theme/layout behavior centralized and prevents each panel from inventing its own UI conventions.

## V1 scope

V1 deliberately stayed small:

1. **Sidebar shell**
2. **Owner panel**
3. **Commands panel**
4. **Player panel**
5. **Debug/diagnostic foundation**

Specialist panels/tools should still be added only when an actual content task needs them.

## Panel responsibilities

### Dashboard / Client Console home

The shell/home panel should mature from implementation scaffolding into a useful developer dashboard.

V2 direction:

- Matrix3/client identity and current high-level state.
- Logged-in account/rights summary where already safely available.
- Server/player running-state indicator where a stable bridge already exists.
- Renderer/status information such as the active renderer when safely available.
- Current world position/region summary where already exposed by the Player bridge.
- Quick launch buttons for high-value specialist tools such as BossLabs and CacheEditor.
- Small pinned/recent-tools area only if it can reuse the existing preference authority without becoming a workspace framework.
- Small status/error indicators when they help the developer understand whether a tool/bridge is ready.

The old permanent `Shell foundation` language and focus-test field are implementation scaffolding. Once focus behavior is runtime-proven, they should not occupy primary dashboard space. Keep focus testing in the test list/diagnostics rather than as permanent home-page content.

### Owner

Owner-only quick actions and development controls.

Suggested groups:

- Account/rights summary.
- Quick actions such as heal/restore/teleport where an existing authoritative action already exists.
- Save-related actions where Matrix3 exposes a safe existing path.
- Small owner/developer toggles that do not belong in another panel.

The Owner panel must not implement its own permission or save systems.

Boss-specific/cache-research tooling should not accumulate in Owner long-term. The existing RoTS scan/deep-scan controls are useful research scaffolding, but their durable home should be the dedicated BossLabs/NPC/boss-research workflow once that replacement is ready. Remove/move the Owner copy only after the dedicated tool provides equivalent or better access; do not break the current research workflow during migration.

### Commands

A searchable front end to the existing Matrix3 command authority.

The current button catalog remains valid functionality, but V2 should bias toward a command-palette workflow for speed:

```text
Search commands... [ tele ]

> ::tele
  ::teleto
  ::teletome
```

Desired keyboard flow where practical:

```text
Type query -> Up/Down -> Enter to select -> type arguments -> Enter to execute
```

Structured arguments may still be exposed when useful, for example:

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
- Search/header/action areas should remain reachable while long command results scroll independently where practical.
- Keep dangerous-command confirmation behavior even if presentation changes.
- Prefer fast search/keyboard selection over forcing the user to scroll hundreds of command buttons.

### Player

Fast developer visibility into the local player.

Potential data:

- Username/display name.
- Rights level.
- Position/plane.
- Region information if already available through a stable client path.
- Basic state useful for debugging.

This panel should remain read-only unless a real development need justifies an action.

### Item Browser

The Item Browser is a strong reference for the intended Client Console workflow: searchable definitions, real previews, persistent organization, context actions, and authoritative client/server bridges.

Keep:

- automatic ID/name search without a mode dropdown,
- real Matrix3-rendered thumbnails,
- favorites,
- custom multi-membership categories,
- presets with saved quantities,
- Inventory/Bank quantity actions,
- right-click organization/spawn actions,
- lazy/bounded thumbnail work,
- persisted organization state.

V2 search target: **500ms debounce** for the current expensive item-result refresh path unless runtime evidence supports a better value.

### Debug / diagnostics

Small diagnostic controls and identifiers that help current development.

Potential examples:

- Coordinate display.
- NPC/object/interface identifiers where Matrix3 already exposes the needed data.
- Focus/input test controls that should no longer occupy the main dashboard after validation.
- Focused debug toggles.
- Recent diagnostic state useful for active work.

Do not turn diagnostics into a packet sniffer, full cache editor, or giant logging framework.

## V2 polish direction

V2 is a **polish pass over the established architecture**, not a Client Console rewrite.

Priority order:

1. **Real icon rail**
   - Replace `C`, `O`, `>_`, `P`, `I`, `S` placeholders with consistent recognizable icons.
   - Keep tooltips and make selected/hover state obvious.
   - Use one shared icon treatment rather than styling each panel independently.

2. **Real dashboard**
   - Replace `Shell foundation`/permanent focus-test scaffolding with useful live development status and high-value tool launchers.

3. **Item Browser responsiveness**
   - Change the intended debounce from 1.25 seconds to **500ms** and runtime-check broad searches for hitching.

4. **Command palette workflow**
   - Preserve the authoritative command bridge and destructive-command safeguards.
   - Improve search-first navigation and keyboard operation instead of presenting 300+ commands primarily as a long button wall.

5. **Owner cleanup**
   - Keep Owner focused on account/rights/admin/development controls.
   - Migrate RoTS/boss-specific research into the dedicated boss/NPC research workflow when that workflow is ready.

6. **Shared visual components**
   - Standardize panel title/subtitle treatment, cards, search fields, action buttons, result rows, context menus, status labels/chips, and empty/loading/error states through `ConsoleTheme` or equally small shared helpers.
   - Do not create a giant custom UI framework merely for visual consistency.

7. **High-value workflow polish**
   - Add small pinned/recent-tool conveniences and readiness/status indicators only where they measurably reduce repeated developer clicks.
   - Keep the shell fast and avoid turning it into a general plugin/workspace platform unless real future usage proves that abstraction is needed.

V2 exit condition: the existing Client Console should **look and feel intentionally designed**, while preserving the current working shell, preference authority, lazy panel creation, client-thread bridges, permissions, and Matrix3 ownership boundaries.

## Architecture target

Conceptually:

```text
Client
  -> ClientConsole
      -> ConsoleSidebar
      -> Dashboard
      -> OwnerPanel
      -> CommandsPanel
      -> PlayerPanel
      -> ItemBrowserPanel
      -> SettingsPanel
      -> ExternalToolLaunchers
```

Preferred panel registration/descriptor concept may eventually include panel identity, icon, title, order, and lazy creation, but this is not approval to build a plugin framework merely for V2 polish.

A minimal conceptual contract remains:

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
- Lazy panel lifecycle/hosting.
- Console-side focus handoff and error boundaries where practical.

### Individual panels own

- Their controls.
- Their display formatting.
- Direct calls into documented Matrix3/client-console bridges.
- Their own lightweight state within the shared preference authority where needed.

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

External windows must follow the same dark-theme, resizing, DPI, minimum-size, persistence/clamping, focus, and no-cutoff rules where applicable.

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
- Identify the input/focus ownership path between Swing controls and game input.
- Verify whether panel initialization can be lazy without disturbing client lifecycle.
- Verify what styling hooks are safe without disturbing game rendering.
- Report exact files/hooks before implementation.

### Phase 2 - shell, theme, and workspace foundation

- Add sidebar rail.
- Add panel host with lazy panel initialization.
- Add open/close/select behavior.
- Add responsive/resizable console boundary with minimum game-view protection.
- Establish centralized permanent dark-theme primitives/shared controls needed by the shell.
- Ensure content can scroll/reflow rather than clip.
- Add one minimal versioned/validated UI preference authority using the safest existing settings path.
- Persist/restore valid client window geometry and Client Console geometry/state.
- Debounce/coalesce geometry persistence rather than writing on every resize event.
- Add safe reset-to-default layout behavior.
- Establish clean console/game focus handoff.
- Add panel-level failure isolation boundaries where practical.

Exit: an empty/placeholder panel can dock, resize, scroll/reflow, collapse, restore its valid workspace after client restart, and accept/release input focus without affecting gameplay/client rendering. Unopened panels do not add unnecessary startup work.

### Phase 3 - V1 panels

Add Owner, Commands, Player, and focused diagnostics one at a time as separate vertical slices.

Each panel must have its own exit criteria and targeted runtime test, including non-maximized/resized behavior, focus/input behavior, lazy initialization, and failure handling where relevant.

### Phase 4 - refinement

Only after V1 behavior is stable:

- favorites/search refinement,
- fine visual consistency,
- keyboard/UX polish,
- additional customization justified by actual use.

Core dark-theme, resize, no-cutoff, workspace restore, thread/focus safety, lazy loading, and basic persistence requirements are not Phase 4 polish; they belong in the shell foundation.

### Phase 5 - V2 polish

Implement the `V2 polish direction` above in small vertical slices.

Recommended first slice:

1. real icon rail,
2. 500ms Item Browser debounce,
3. dashboard cleanup,
4. command-palette refinement,
5. Owner research-tool migration only after the dedicated replacement exists.

Do not bundle these into one risky rewrite. Preserve the known-good shell and verify each slice independently.

## Out of scope for V1/V2 polish

- Porting the old 718 Client Console wholesale.
- Rebuilding ForgeLabs.
- Building every future editor up front.
- Replacing Matrix3 command/rights/persistence systems.
- Refactoring unrelated client frame/rendering code.
- Large combat/cache/network changes.
- Building a general-purpose UI designer or workspace system.
- Building a plugin framework solely because the console now has several panels.
- Adding animation for its own sake.

## V1/V2 success criteria

The Client Console is successful when:

- the console docks cleanly beside the game view,
- the sidebar can open/close and switch panels reliably,
- the rail uses recognizable, consistent real icons rather than permanent letter placeholders,
- Owner/Commands/Player/Items/Settings/diagnostics remain separated and understandable,
- the home panel behaves as a useful dashboard rather than permanent implementation scaffolding,
- command UI routes through authoritative Matrix3 behavior rather than duplicating it,
- Commands supports a fast search-first workflow appropriate for hundreds of entries,
- Item Browser search uses the 500ms target without causing a visible game/client hitch,
- boss-specific research has a deliberate dedicated home instead of accumulating indefinitely in Owner,
- the normal client remains usable with the console closed,
- the console does not disturb login, rendering, input, resizing, or normal gameplay,
- adding a future panel does not require rewriting the shell,
- no required Client Console control depends on fullscreen/maximized mode to remain reachable,
- controls remain accessible through responsive layout, scrolling, collapsing, or resizing at supported window sizes,
- the dark theme is consistently applied rather than falling back to default Swing presentation,
- shared visual components make panels feel like one product rather than unrelated Swing utilities,
- resizing and splitter movement do not create permanently hidden/unreachable UI,
- resizing does not repeatedly reload/rebuild unrelated game/interface/render state,
- the game viewport retains a usable minimum size while the console is resized,
- client window size/position/state and Client Console geometry restore automatically after a clean restart,
- persisted layout state restores safely and is clamped when current window/monitor bounds differ,
- malformed/outdated UI settings fall back safely and a reset-layout path restores known-good defaults,
- resize/splitter persistence is coalesced/debounced rather than writing continuously during drag,
- unopened panels do not materially slow startup and panels initialize lazily where appropriate,
- slow console work does not block the game/render thread or Swing event-dispatch thread,
- typing in focused console controls does not also trigger game input and returning focus restores game controls cleanly,
- a panel-specific failure can be surfaced without unnecessarily terminating the running client where practical,
- 100%, 125%, and 150% Windows scaling do not cause overlapping or unreachable core controls,
- 1280x720, 1600x900, 1920x1080, and ultrawide layouts are explicitly checked where supported by the base client.

## Priority reminder

The Client Console exists to make content development faster. V2 polish should make existing workflows faster and cleaner, not become an excuse for endless tool-building. Once the console feels deliberate and the active content workflow is supported, return to content.