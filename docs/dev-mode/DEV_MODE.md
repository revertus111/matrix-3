# Matrix3 Dev Mode UI Suite

## Purpose

Dev Mode is the main in-game creation and editing environment for Matrix3.

The goal is not to build another generic cache editor or a collection of disconnected admin panels. The running game world itself should be the primary viewport and entry point for development work.

While Dev Mode is enabled, the owner should be able to select or right-click supported targets, understand what they are, edit them live, test the result immediately, save through the correct Matrix3 authority, and undo or revert changes where supported.

Matrix3 remains the authority for game behavior, definitions, world state, clipping, combat, spawning, persistence, and cache decoding. Dev Mode is developer tooling layered over those systems through narrow Matrix3-native bridges.

---

# Product Identity

Dev Mode should feel like **Matrix3 Creation Mode**, not a Java debug panel.

The design target is:

**See something in the live game -> select it -> inspect everything connected to it -> modify it -> test it live -> save it.**

This is intentionally different from a Frosty-style workflow of browsing thousands of raw cache fields first and trying to work out what they affect afterward.

The UI should be contextual, visual, live-world-first, dark themed, and built around the thing currently being edited.

---

# Current Working Foundation

The following foundation already exists and should be preserved:

- Dev Mode owner-only toggle.
- Tile right-click Dev actions.
- Tile Editor foundation.
- Searchable NPC/object/item Spawn Browser.
- Runtime spawning of NPCs on the selected tile.
- Runtime spawning of objects on the selected tile.
- Runtime spawning of ground items on the selected tile.
- Item thumbnails through the existing Item Browser render path.
- NPC/object names and IDs through the existing definition bridge.

Current evidence status:

- Item thumbnail integration: **VERIFIED** in the current Dev Spawn Browser.
- NPC model thumbnail integration: **HYPOTHESIS** until a safe reusable off-screen Matrix3 render path is established.
- Object model thumbnail integration: **HYPOTHESIS** until a safe reusable off-screen Matrix3 render path is established.
- Persistent NPC/object placement saving: **HYPOTHESIS** until the correct Matrix3 save authority is verified.
- Tile clipping/terrain mutation: **HYPOTHESIS** until the owning Matrix3 map/clipping path is verified.

---

# Main Dev Mode Shell

Dev Mode should grow into one coherent studio instead of opening unrelated windows for every task.

The primary concepts are:

## 1. World / Scene

Shows the live context around the player and the current selection.

Useful scene information may include:

```text
Current Region
|- NPCs
|  |- Araxxor
|  |- Spider
|  `- Spider
|- Objects
|  |- Web
|  |- Rock
|  `- Entrance
|- Ground Items
`- Effects
```

Selecting an entry should select or focus the same entity in the live game where practical.

The World panel should also track:

- Selected tile.
- Selected NPC.
- Selected object.
- Selected item.
- Nearby entities.
- Current region/chunk.
- Active development markers/effects.

## 2. Inspector

The Inspector is always contextual.

Selecting an NPC shows NPC properties.
Selecting an object shows object properties.
Selecting an item shows item properties.
Selecting a tile shows tile properties.

The goal is to avoid separate giant windows when one contextual property surface can route into specialist tools only when needed.

## 3. Universal Browser

One searchable browser for development assets.

Target categories may include:

- NPC.
- Object.
- Item.
- Animation.
- Graphic.
- Model.
- Sprite.
- Texture.
- Sound where supported.

Search rules:

- Numeric input -> ID search.
- Text input -> name search.
- Results update automatically where performance allows.
- Favorites and recents are first-class filters.
- Visual previews are shown where technically supported.

## 4. History

Dev Mode should record meaningful live edits during a session.

Example:

```text
17:31 Spawned NPC 8349
17:32 HP 5000 -> 7500
17:32 Added drop 995 x 10000
17:33 Moved NPC
17:34 Spawned object 12345
```

Desired actions:

- Undo.
- Redo.
- Revert selected change.
- Revert selected entity.
- Save all supported changes.

Undo/redo must be implemented only where the target system has a safe reversible mutation path.

---

# Dev Interaction Model

## Dev Mode OFF

The game behaves normally. No development interaction entries are injected into ordinary gameplay menus.

## Dev Mode ON

Normal Matrix3 interactions remain intact and supported targets receive additional Dev actions.

Target types:

```text
DEV TARGET
    TILE
    NPC
    OBJECT
    ITEM
    PLAYER       future
    INTERFACE    future
    PROJECTILE   future
    GRAPHIC      future
```

Each target type should register only the Dev actions relevant to it instead of hardcoding one giant menu implementation.

---

# Right-Click Dev Menus

## Tile

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
    Place Last NPC
    Place Last Object
    Place Last Item
    Mark Tile
    Clear Tile >
        NPCs
        Objects
        Ground Items
```

The exact menu nesting may be adjusted for Matrix3 menu limits, but the workflow should remain fast.

## NPC

```text
Dev >
    Inspect NPC
    Edit NPC
    Copy ID
    Copy Tile
    Move
    Duplicate
    Delete Development Spawn
    Open Linked Assets
```

## Object

```text
Dev >
    Inspect Object
    Edit Object
    Copy ID
    Copy Tile
    Move
    Rotate
    Duplicate
    Replace
    Delete Development Spawn
    Open Linked Assets
```

## Inventory / Equipment / Bank / Ground Item

Dev Mode must not stop at world-ground items.

The same item should expose the same core Dev actions regardless of where it is visible.

Desired item actions:

```text
Dev >
    Inspect Item
    Edit Item
    Copy ID
    Duplicate
    Set Quantity
    Spawn on Ground
    Add to Bank
    Open in Item Browser
    Open Linked Assets
```

Inventory Dev integration is a required part of the main Dev Mode design.

---

# Editor Tool Modes

Dev Mode should support proper editor-style tools rather than relying only on right-click menus.

Primary tool strip:

```text
SELECT
MOVE
ROTATE
SPAWN
PAINT
ERASE
INSPECT
```

## Select

Select a live target and populate the Inspector immediately.

A dedicated Select Mode should allow fast clicking during active development without requiring a right-click for every entity.

## Move

Select an NPC/object, then choose a destination tile.

## Rotate

Rotate supported objects in-place using simple controls or hotkeys.

## Spawn

Spawn the currently selected browser asset.

## Paint

Continuously place repeated objects/NPCs/items or supported tile effects until placement mode is exited.

## Erase

Remove supported development/runtime entities with clear safeguards against deleting authoritative map content accidentally.

## Inspect

Read-only selection mode for reverse engineering and diagnostics.

---

# Spawn Modes

The existing Spawn Browser should expand beyond one-shot spawning.

A selected NPC/object/item should support:

```text
Spawn Mode

( ) Once
( ) Continuous
( ) Paint Placement
```

## Once

Place one entity and exit the placement action.

## Continuous

Keep the same selected entity active after placement so every next tile click can place another copy until the user exits the mode.

This is useful for:

- NPC groups.
- scenery placement.
- repeated test actors.
- area decoration.

## Paint Placement

Fast repeated placement intended for world-building workflows.

For objects, useful options may include:

- Random rotation.
- Snap rotation.
- Fixed rotation.
- Replace occupied development object.

Escape should leave continuous/paint placement cleanly.

---

# Spawn Browser Visual Direction

The Spawn Browser should not look like a basic debug form.

Preferred structure:

## Search / Results

- Large search field.
- NPC/Object/Item filters.
- Favorites.
- Recents.
- Thumbnail cards or clean visual rows.

## Preview

- Large selected asset preview.
- Name.
- ID.
- Useful definition summary.
- Favorite control.

## Context Controls

NPC examples:

- Spawn count.
- Direction.
- Spawn Once / Continuous / Paint.
- Spawn + Edit.

Object examples:

- Rotation.
- Type.
- Placement mode.
- Place + Edit.

Item examples:

- Quantity.
- Ground placement.
- Spawn + Edit.

The UI should use dark charcoal surfaces, clear hierarchy, consistent spacing, readable typography, thumbnail previews, contextual controls, and minimal raw-field clutter.

---

# Thumbnail and Preview System

## Items

Current item thumbnails should continue using the existing Item Browser render path.

Show:

- Item icon.
- Name.
- ID.

## NPCs

Desired preview:

- Rendered NPC model thumbnail or live model preview.
- Name.
- ID.
- Optional combat level/size.

Do not add fake or misleading previews. The exact safe render bridge must be verified first.

## Objects

Desired preview:

- Rendered object model thumbnail or live model preview.
- Name.
- ID.
- Optional type/size/rotation metadata.

Do not pre-render every cache asset on startup.

Preferred performance strategy:

1. Load search result metadata first.
2. Render visible previews lazily.
3. Cache successful renders.
4. Reuse cached previews.
5. Keep preview work away from the critical game loop where practical.

---

# NPC Editor

The NPC Editor should become one of the major Dev Mode editors.

The live selected NPC should provide both runtime and definition information where available.

## Identity

- NPC ID.
- Name.
- Examine text where exposed.
- Combat level.
- Size.

## Runtime

- Current HP.
- Max HP.
- Current target.
- Current tile.
- Facing/direction.
- Current animation.
- Current graphic/effect.
- Current action/state where safely discoverable.

## Combat Stats

Desired editable fields where owned by a verified Matrix3 definition/config path:

- HP.
- Attack.
- Strength.
- Defence.
- Magic.
- Ranged.
- Accuracy/armour where applicable.
- Attack speed.
- Attack range.

## Behavior

Potential controls where the real owner is verified:

- Aggressive toggle.
- Aggression radius.
- Movement/wander radius.
- Respawn delay.
- Chase range.
- Immunities/resistances.

## Combat Presentation

Where exposed:

- Attack animation.
- Defence animation.
- Death animation.
- Projectile.
- Graphics.
- Hit delay.
- Damage ranges.

Boss-specific mechanics should link into BossLabs rather than duplicating the entire BossLabs system inside the generic NPC Inspector.

---

# Visual Drop Editor

NPC drops should use a proper visual editor instead of raw comma-separated IDs.

Example row:

```text
[item icon] Coins
            ID 995
            10,000 - 50,000
            Always
```

Each drop row should expose:

- Item thumbnail.
- Item name.
- Item ID.
- Minimum quantity.
- Maximum quantity.
- Chance/weight.
- Always/Common/Uncommon/Rare/Custom presentation where appropriate.

`+ Add Drop` should open the item browser so items are chosen visually rather than requiring memorized IDs.

When probability can be represented accurately, the UI may show a calculated estimated chance such as `1 / 128` and `0.781%`.

Saving must go through the actual Matrix3 drop-table owner once verified.

---

# Item Editor Integration

Selecting an item from inventory, equipment, bank, ground, shop, or the browser should resolve to the same item definition context.

Desired editable groups where supported:

## Definition

- Name.
- Examine.
- Value.
- Stackable.
- Tradable.
- Options.

## Appearance

- Inventory model.
- Male/female wear models.
- Model rotations.
- Offsets.
- Zoom.
- Recolors.
- Retextures.

## Gameplay

- Bonuses.
- Requirements.
- Weapon type.
- Attack speed.
- Combat animations.

The existing Item Browser should be reused rather than replaced.

---

# Object Editor

Object editing should support both runtime placement and definition inspection.

Useful controls:

- Object ID.
- Name.
- Tile.
- Plane.
- Type.
- Rotation.
- Size where exposed.
- Interaction options.
- Animation/transform data where exposed.
- Clipping summary.

Development actions:

- Move.
- Rotate.
- Duplicate.
- Replace.
- Delete development placement.
- Open linked definition/assets.

Persistent world-map saving must not bypass Matrix3's authoritative object/map ownership.

---

# Tile Editor

The Tile Editor should become a first-class world-building surface.

## Core Information

Where Matrix3 safely exposes the data:

- World X/Y.
- Plane.
- Region.
- Chunk/local coordinates.
- Height.
- Movement clipping.
- Projectile clipping.
- Objects occupying tile.
- NPCs occupying tile.
- Ground items occupying tile.
- Development markers/effects.

## Standard Actions

- Copy coordinates.
- Teleport to tile.
- Spawn NPC/object/item.
- Open inspectors for tile contents.
- Mark/unmark tile.
- Clear selected temporary development entities.

## Tile Painting / Gameplay Zones

A later verified tile system should support visually painting development/gameplay areas.

Potential tile types:

- Movement blocked.
- Projectile blocked.
- NPC blocked.
- Damage zone.
- Healing zone.
- Safe zone.
- Teleport tile.
- Trigger tile.
- Boss mechanic tile.
- Custom tag.

Example damage tile configuration:

```text
Damage: 250
Interval: 1 tick
Effect: Fire
```

This is especially useful for BossLabs encounters and area mechanics.

These gameplay-zone concepts should use explicit Matrix3/BossLabs content data, not silently mutate cache terrain unless that is the verified owner.

---

# Entity Relationships

Dev Mode should show how the selected thing connects to the rest of the game.

Example NPC relationship view:

```text
Araxxor
|- Spawn: Araxxor Cave
|- Drop table: Araxxor
|- Combat script: AraxxorCombat
|- Animations: 19431, 19432, 19433
|- Graphics: 4982, 4983
`- Linked objects: Web 1234
```

Example item relationship view:

```text
Noxious Scythe
|- Inventory model
|- Male wear model
|- Female wear model
|- Weapon definition
|- Combat animations
|- Requirements
`- Special attack
```

Clicking a relationship should open or focus the linked asset/tool where practical.

This relationship view is a major part of making Dev Mode useful for reverse engineering rather than just editing values.

---

# Live vs Saved Changes

Every editor capable of mutation must clearly distinguish temporary testing from persistent data.

Preferred actions:

```text
[ APPLY LIVE ]    [ SAVE ]    [ REVERT ]
```

## Apply Live

- Changes the running development state.
- Intended for rapid testing.
- Restart discards it unless explicitly saved through the correct owner.

## Save

- Persists through the verified Matrix3 owner/configuration path.
- Must not be enabled speculatively before ownership is established.

## Compare View

Changed values should be easy to see:

```text
CURRENT          SAVED
HP 7500          HP 5000
Attack 120       Attack 100
Respawn 20       Respawn 30
```

Dev Mode should also show a global unsaved-change count when practical.

---

# Workspaces / Sessions

Dev Mode may support named development workspaces for larger content tasks.

Example:

```text
Workspace: Rise of the Six
```

A workspace may remember:

- Recent NPCs.
- Recent objects.
- Recent items.
- Favorites.
- Selected entities.
- Tile markers.
- Unsaved live edits.
- Search history where useful.

This should remain lightweight and should not become a separate project format unless real development needs justify it.

---

# Command Palette

A keyboard command palette can complement the visual UI for fast development.

Possible shortcut:

```text
Ctrl + P
```

Example commands:

```text
> spawn tormented demon
> teleport 3200 3200
> open npc 8349
> inspect current tile
> search animation 12345
> reload selected
```

Mouse-first visual editing remains the main workflow; the command palette is for speed once the owner knows what they want.

---

# Assets Instead of a Generic Cache Editor

The existing cache editor concept belongs inside Dev Mode, but it should not be the centerpiece and should not simply imitate Frosty.

Use a contextual **Assets** section instead.

Possible categories:

```text
Assets
    Definitions
    Models
    Animations
    Graphics
    Sprites
    Textures
    Sounds
```

The preferred workflow is not:

**Open Cache Editor -> browse raw data -> guess what it affects.**

It is:

**Select Araxxor -> Appearance/Assets -> open the exact model/animation/definition used by Araxxor.**

The raw asset browser still exists for direct work, but contextual navigation should be the normal path.

---

# Dev Mode Visual Rules

Dev Mode should avoid generic Java-tool styling.

Preferred direction:

- Dark charcoal theme.
- Strong visual hierarchy.
- Clean borders and spacing.
- Clear typography.
- Thumbnail cards/rows.
- Large contextual preview when useful.
- Icon buttons where they improve recognition.
- Collapsible property groups.
- Search everywhere practical.
- Minimal giant walls of raw fields.
- Sliders for continuous visual values where appropriate.
- Toggles for binary states.
- Numeric fields for exact technical values.
- Breadcrumb/context trail showing what is currently selected.
- Obvious LIVE / SAVED / UNSAVED state.

The interface should look like a game creation tool, not an admin form.

---

# Safety and Ownership Rules

1. Matrix3 remains the authoritative game architecture.
2. Dev Mode is tooling, not a replacement engine.
3. Existing working systems are accessed through narrow bridges/APIs.
4. Temporary runtime changes must not silently become persistent.
5. Persistent saves must use the verified owner for that data.
6. Do not globally rewrite clipping, object loading, NPC loading, item definitions, cache decoding, or map ownership to make the editor easier.
7. Java 8 / Eclipse compatibility remains required.
8. Build the suite in small vertical slices.
9. Preserve original obfuscated/decompiled Matrix3 names unless renaming is explicitly approved.
10. Use `VERIFIED`, `verified-static`, and `HYPOTHESIS` accurately during reverse engineering.
11. Do not let Dev Mode become an excuse to duplicate BossLabs, Item Browser, or other specialist tools; link them contextually instead.

---

# Revised Implementation Direction

The initial spawn milestone is already working, so future work should build upward from that foundation instead of recreating it.

## Stage 1 - Interaction Coverage

- Add Dev actions to inventory items.
- Extend to equipment/bank/ground-item contexts where practical.
- Add NPC/object Dev target menus.
- Add universal Inspect/Edit routing.

Acceptance goal: anything important visible in normal gameplay can be selected and routed into Dev Mode consistently.

## Stage 2 - Persistent Tool Modes

- Spawn Once / Continuous / Paint.
- Move.
- Rotate.
- Duplicate.
- Safe development erase.
- Last-used placement.

Acceptance goal: repeated world-building no longer requires reopening the browser for every placement.

## Stage 3 - Main Inspector + NPC Editor

- Contextual Inspector shell.
- Runtime NPC HP/stats display.
- Safe live HP/stat editing where the Matrix3 owner is verified.
- Behavior/spawn information.
- Visual Drop Editor foundation.
- BossLabs link for boss-specific mechanics.

Acceptance goal: right-click/select an NPC and tune its normal gameplay properties without leaving the live development workflow.

## Stage 4 - World / Tile Creation Tools

- Scene/region hierarchy.
- Expanded Tile Editor.
- Tile content inspection.
- Tile painting for explicit Dev/BossLabs gameplay zones.
- Clipping visualization.
- Clipping mutation only if the owning Matrix3 path is verified and safe.

Acceptance goal: Dev Mode can build and debug encounter spaces visually.

## Stage 5 - Contextual Assets

- NPC/object model previews when a safe render bridge is verified.
- Contextual models/animations/graphics navigation.
- Fold cache-facing tools under Assets.
- Relationship/dependency views.

Acceptance goal: the owner can move from a live entity to the exact assets that drive it without manually hunting cache IDs.

## Stage 6 - Change Management

- Live vs Saved comparison.
- Unsaved-change tracking.
- History.
- Undo/redo where reversible.
- Lightweight workspaces/sessions.

Acceptance goal: larger live editing sessions remain understandable and recoverable.

---

# Non-Goals

Dev Mode should not become:

- A second Matrix3 engine.
- A full engine rewrite.
- A giant raw cache field browser with prettier buttons.
- A replacement for BossLabs.
- A replacement for the Item Browser.
- A reason to modify working Matrix3 ownership without evidence.
- A monolithic feature that must be completed all at once.

The long-term goal is a coherent creation environment where the live Matrix3 world is the viewport, every important entity is selectable, specialist tools plug into one contextual workflow, and changes can be tested immediately without losing sight of what is live, saved, or still experimental.
