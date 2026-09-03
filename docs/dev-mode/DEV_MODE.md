# Matrix3 Dev Mode UI Suite

## Purpose

Dev Mode is the in-game developer editing suite for Matrix3.

The goal is to make the live game world itself the entry point for development work. While Dev Mode is enabled, the owner can right-click supported world targets and open polished editing, inspection, spawning, and placement tools without replacing Matrix3's normal interaction systems.

Dev Mode is developer tooling only. Matrix3 remains the authority for game behavior, definitions, world state, clipping, combat, spawning, and persistence. Dev Mode should call narrow Matrix3-native APIs rather than becoming a second engine.

## Primary Direction

This document defines **Option B: the Dev Mode UI Suite**.

The main suite consists of:

- Dev Mode right-click entry points.
- Tile Editor.
- Spawn Browser.
- NPC Inspector / Editor links.
- Object Inspector / Editor links.
- Item Inspector / Editor links.
- Thumbnail/model previews where technically supported.
- Recent and favorite development assets.
- Live temporary edits versus explicitly saved changes.

The visual target is a polished dark-theme development interface, not a basic admin prompt or debug dialog.

---

## Dev Mode State

### Dev Mode OFF

The client behaves normally. No development interaction entries should be injected into ordinary gameplay menus.

### Dev Mode ON

Supported right-click menus receive a `Dev >` submenu in addition to normal Matrix3 options.

Examples of supported targets:

- Tile / ground location.
- NPC.
- Object.
- Inventory item.
- Ground item.
- Equipment item where practical.
- Additional targets may be added later through the same registration model.

Dev Mode should never require replacing normal interaction options merely to expose development tools.

---

# Tile Dev Menu

Any visible/right-clickable tile should be usable as a Dev Mode target.

Example:

```text
Walk here
Dev >
    Inspect Tile
    Edit Tile
    Copy Coordinates
    Teleport Here
    Spawn >
        NPC
        Object
        Item
    Mark Tile
    Clear Tile >
        NPCs
        Objects
        Ground Items
```

The exact menu structure may be adjusted during implementation to fit Matrix3 menu limits, but the workflow should remain direct and fast.

---

# Tile Editor

The Tile Editor is a first-class Dev Mode tool rather than a small coordinate popup.

## Core Tile Information

Where Matrix3 exposes the data safely, the Tile Editor should display:

- World X.
- World Y.
- Plane.
- Region.
- Chunk/local coordinates where useful.
- Height information where available.
- Movement clipping flags.
- Projectile clipping flags.
- Objects occupying the tile.
- NPCs occupying the tile.
- Ground items occupying the tile.
- Active developer markers/effects associated with the tile.

## Tile Actions

Initial useful actions:

- Copy tile coordinates.
- Teleport to tile.
- Spawn NPC on tile.
- Spawn object on tile.
- Spawn ground item on tile.
- Open an NPC/Object/Item inspector for contents on the tile.
- Mark/unmark tile.
- Clear selected temporary development entities from the tile.

Future editing candidates, only after source ownership is verified:

- Movement clipping editing.
- Projectile clipping editing.
- Overlay/underlay editing.
- Height editing.
- Ground-effect placement.
- BossLabs tile/arena integration.

These future capabilities must not bypass Matrix3's actual map/clipping ownership.

---

# Spawn Browser

Right-clicking a tile and selecting `Dev > Spawn` should open a polished Spawn Browser rather than prompting only for an ID.

The Spawn Browser should support:

- NPCs.
- Objects.
- Items.

The selected tile becomes the active placement target.

## Search Behavior

No separate ID/name search mode should be required.

- Numeric input is treated as an ID search.
- Text input is treated as a name search.
- Results update automatically while typing where performance allows.

Example:

```text
Search: arax

[preview] Araxxor        ID: 19457
[preview] Araxxor        ID: 19458
[preview] Araxxor        ID: 19459
```

## Spawn Browser Layout

Preferred layout:

### Left / Search Area

- Search field.
- NPC / Object / Item category selector.
- Search result list.
- Favorites filter.
- Recent filter.

### Center / Preview Area

- Larger selected asset preview.
- Name.
- ID.
- Relevant definition summary.

### Right / Action Area

Context-specific spawn settings and placement actions.

The interface should use the project's dark-theme developer-tool style with clear spacing, readable controls, and no light-theme default.

---

# Thumbnail and Preview System

The Spawn Browser should show visual previews where Matrix3's client model/sprite systems allow it.

## Items

Desired preview:

- Existing item sprite/icon.
- Item name.
- Item ID.

Item previews are expected to be the simplest preview type because the client already needs inventory/interface item rendering.

## NPCs

Desired preview:

- Rendered NPC model thumbnail or small live model preview.
- NPC name.
- NPC ID.
- Optional combat level/size when useful.

## Objects

Desired preview:

- Rendered object model thumbnail or small live model preview.
- Object name.
- Object ID.
- Optional type/size metadata where useful.

## Performance Strategy

Do not eagerly render every NPC/object thumbnail at startup.

Preferred strategy:

1. Populate search results from definitions first.
2. Render previews lazily for visible results.
3. Cache successful preview renders.
4. Reuse cached previews on later searches.
5. Keep rendering off the critical game loop where practical.

## Current Evidence Classification

- Item thumbnail integration: **HYPOTHESIS** until the exact reusable Matrix3 sprite/render hook is traced.
- NPC thumbnail integration: **HYPOTHESIS** until the client NPC model/render path is inspected.
- Object thumbnail integration: **HYPOTHESIS** until the client object model/render path is inspected.

The design should not claim these hooks are VERIFIED before the implementation scan establishes them.

---

# NPC Spawn Workflow

Selecting an NPC in the Spawn Browser should allow spawning it on the exact tile that opened the browser.

Initial controls:

- NPC result selection.
- Spawn count.
- Direction if supported by the spawn API.
- Temporary live spawn.
- Spawn and immediately open editor/inspector.

Future controls after ownership is verified:

- Wander radius.
- Respawn configuration.
- Persistent spawn saving.
- Aggression/content-specific options.

Example actions:

```text
[ Spawn ]
[ Spawn + Edit ]
[ Favorite ]
```

---

# Object Spawn Workflow

Selecting an object should support placement on the selected tile.

Initial controls:

- Object result selection.
- Rotation.
- Object type where required by Matrix3.
- Temporary live placement.
- Place and immediately edit/inspect.

Desired later workflow:

- Live placement preview before commit.
- Move existing object.
- Rotate existing object.
- Duplicate existing object.
- Replace existing object.
- Persistent world-spawn saving through the correct Matrix3 owner.

Example actions:

```text
[ Place ]
[ Place + Edit ]
[ Favorite ]
```

---

# Item Spawn Workflow

Selecting an item should allow it to be placed on the selected world tile.

Initial controls:

- Item result selection.
- Quantity.
- Spawn on selected tile.
- Favorite.

Future options, only where Matrix3 exposes the distinction cleanly:

- Private ground item.
- Public ground item.
- Temporary versus persistent development spawn.

---

# Recent, Favorites, and Last Used

The Spawn Browser should optimize repeated world-building work.

## Favorites

The owner can favorite commonly used:

- NPCs.
- Objects.
- Items.

Favorites should persist across restart when the Dev Mode persistence layer is introduced.

## Recent

Keep a small recent-history list per asset type.

Examples:

- Recent NPCs.
- Recent objects.
- Recent items.

## Last Used

The tile Dev menu may expose shortcuts such as:

```text
Dev >
    Place Last NPC
    Place Last Object
    Place Last Item
```

This is especially useful while building an area with repeated scenery or test actors.

---

# Placement Mode

A later quality-of-life feature should support continuous placement.

Example workflow:

1. Choose an object in the Spawn Browser.
2. Enter Placement Mode.
3. Click tiles to place repeated copies.
4. Rotate or adjust between placements if needed.
5. Press Escape to leave Placement Mode.

Placement Mode must remain explicitly developer-only and should make temporary versus persistent placement obvious.

---

# Inspectors and Linked Editors

Dev Mode should route targets into the correct specialist editor rather than duplicating every editor inside one giant window.

Examples:

```text
NPC -> NPC Inspector / BossLabs when applicable
Object -> Object Inspector / Object Editor
Item -> Item Inspector / Item Browser
Tile -> Tile Editor
```

This keeps Dev Mode as the universal entry point while allowing focused tools to retain their own ownership.

## Universal Inspector Goals

Right-click `Dev > Inspect` should expose useful runtime and definition data for the selected target.

Example NPC information:

- Name.
- NPC ID.
- Tile.
- Plane.
- Current HP.
- Current animation.
- Current graphics/effects where available.
- Current target/action where available.
- Definition metadata.
- Spawn metadata.
- Owning script/handler when Matrix3 exposes it safely.

Useful actions:

- Edit.
- Copy ID.
- Copy tile.
- Reload where supported.
- Open linked specialist tool.

---

# Live vs Saved Changes

Every editor that can alter runtime state should clearly distinguish temporary testing from persistence.

Preferred model:

```text
[ APPLY LIVE ]    [ SAVE ]
```

## Apply Live

- Alters the running development state only.
- Intended for fast testing/tuning.
- Restart should discard the change unless it was explicitly saved elsewhere.

## Save

- Writes through the authoritative Matrix3 persistence/configuration path for that subject.
- Must only be enabled once the correct owner and save format are verified.

The UI should make unsaved live modifications visually obvious so temporary experiments are not mistaken for persistent content.

---

# Dev Target Architecture

The right-click system should be extensible instead of hardcoded separately for every future tool.

Conceptual target types:

```text
DEV TARGET
    TILE
    NPC
    OBJECT
    ITEM
    PLAYER      (future)
    INTERFACE   (future)
    PROJECTILE  (future)
    GRAPHIC     (future)
```

Each supported target should register the Dev Mode actions relevant to that type.

This avoids repeatedly rebuilding the core right-click integration when new developer tools are added.

---

# Safety and Ownership Rules

Dev Mode must follow these project rules:

1. Matrix3 remains the authoritative game architecture.
2. Dev Mode is tooling, not a replacement engine.
3. Existing working systems should be called through narrow bridges/APIs.
4. Temporary runtime changes must not silently become persistent.
5. Persistent saves must use the verified owner for that data.
6. Do not globally alter clipping, object loading, NPC loading, item definitions, or map ownership merely to make the editor easier.
7. Java 8 / Eclipse compatibility remains required.
8. Build the suite in small vertical slices.
9. Preserve original obfuscated/decompiled Matrix3 names unless renaming is explicitly approved.
10. Use `VERIFIED`, `verified-static`, and `HYPOTHESIS` accurately during reverse engineering.

---

# Proposed Vertical Implementation Order

## Phase 1 - Dev Mode Entry Foundation

- Dev Mode toggle/state.
- Tile right-click `Dev >` entry.
- Basic Spawn Browser shell.
- Search NPC/object/item by name or ID.
- Temporary spawn onto selected tile.

Acceptance goal: stand in-game, right-click a tile, search an NPC/object/item, and spawn it onto that exact tile.

## Phase 2 - Tile Editor + Visual Browser

- Tile Inspector.
- Tile Editor shell.
- Favorites.
- Recents.
- Last-used shortcuts.
- Thumbnail/preview integration after render hooks are verified.
- Spawn + Edit routing.

Acceptance goal: world building can be performed primarily through the Dev Mode UI instead of raw commands.

## Phase 3 - Object Placement Workflow

- Placement preview.
- Rotation controls.
- Move/duplicate/delete development actions.
- Continuous Placement Mode.
- Clear-tile development actions.

Acceptance goal: scenery and test layouts can be built quickly from inside the live world.

## Phase 4 - Persistence and Deeper Tile Editing

Only after the owning systems are verified:

- Persistent NPC/object placement saving.
- Safe tile/clipping editing where architecturally appropriate.
- Overlay/underlay/height tooling if technically justified.
- BossLabs tile integration.
- Ground-effect placement/editing.

Acceptance goal: Dev Mode can graduate tested live changes into real content without bypassing Matrix3's authoritative data paths.

---

# Non-Goals for the Initial Slice

The first implementation should not attempt to solve all of these at once:

- Full map editor.
- Full clipping rewrite.
- Full object-definition editor.
- Full NPC combat editor.
- Every possible runtime target type.
- Persistent editing before ownership is understood.
- Pre-rendering every cache model at startup.

The first usable milestone is intentionally small: **right-click a tile -> open the polished Spawn Browser -> search -> preview where supported -> spawn the selected NPC/object/item on that tile.**

That workflow becomes the foundation for the rest of Dev Mode.
