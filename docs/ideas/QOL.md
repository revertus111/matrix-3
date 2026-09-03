# QOL Settings Ideas

## Status

Idea/design document only. No client or server implementation is implied by this document.

## Goal

Add optional quality-of-life features to the Matrix3 Settings tab without turning the settings interface into one large unorganized list.

The guiding rule is:

> QOL should remove repetitive friction without removing the gameplay itself.

Where possible, features should be implemented as reusable client-side systems rather than one-off hacks.

---

## Proposed Settings Organization

```text
QOL
|
|-- Inventory
|   |-- Shift-click Drop
|   |-- Shift-click Configuration
|   |-- Inventory Tags
|   `-- Item Overlays
|
|-- Menu & Interaction
|   |-- Menu Entry Customizer
|   |-- NPC Actions
|   |-- Object Actions
|   `-- Item Actions
|
|-- Ground Items
|   |-- Loot Highlighting
|   |-- Loot Beams
|   |-- Value Thresholds
|   `-- Notifications
|
|-- Bank
|   |-- Bank Tags
|   |-- Custom Tabs / Groups
|   `-- Shift-click Actions
|
|-- Indicators
|   |-- NPC Highlights
|   |-- Player Highlights
|   |-- Tile Markers
|   `-- Target Indicators
|
`-- Notifications
    |-- Rare Drops
    |-- Idle
    |-- Low HP
    |-- Low Prayer
    `-- Personal Bests
```

The exact Settings UI can change later, but features should stay grouped by purpose instead of being added as unrelated checkboxes.

---

# 1. Inventory QOL

## Shift-click to Drop

Optional setting allowing the player to hold Shift and click an inventory item to drop it.

Initial behavior:

- Off by default.
- When enabled, Shift + left-click uses the item's existing Drop action when available.
- Must not invent a Drop action for items that cannot normally be dropped.
- Normal left-click behavior remains unchanged.

This should preferably be built on top of the reusable menu-action customization system described below rather than as isolated hardcoded input logic.

## Custom Shift-click Action

Future extension of Shift-click Drop.

Allow a player to configure the Shift-click action for an item from actions the item already exposes, such as:

- Drop
- Use
- Equip / Wear / Wield
- Eat / Drink
- Bury
- Teleport
- Examine
- Other existing item-specific actions

Possible interaction:

```text
Shift + Right-click item

Configure Shift-click
  Drop
  Use
  Wield
  Examine
```

The selected action should be persisted across client restarts.

## Inventory Tags

Allow players to visually tag inventory items for quick recognition.

Possible uses:

- Melee
- Ranged
- Magic
- Skilling
- Boss loadout groups
- Food
- Potions
- Switches

Tags should be cosmetic/client-side unless a future gameplay feature explicitly needs them.

## Item Overlays

Potential optional overlays:

- Improved stack quantity display
- Charges
- Durability
- Degrade state
- Other item state where the client already has reliable information

Avoid showing guessed state or information the client cannot verify.

---

# 2. Menu & Interaction QOL

## Menu Entry Customizer

This is the preferred reusable foundation for several QOL features.

The player should be able to change which existing menu action is treated as the preferred action for supported entities.

Supported categories can eventually include:

- Inventory items
- Equipment items
- NPCs
- Objects
- Bank interactions
- Shop interactions
- Travel / teleport interactions

The customizer should only reorder or select actions that already exist. It should not create gameplay actions that the server does not provide.

### Example: NPC

```text
Banker

* Bank
  Talk-to
  Examine
```

Choosing `Bank` makes Bank the preferred click action for that supported NPC/menu context.

### Example: Object

```text
Portal

* Enter
  Configure
  Examine
```

### Example: Inventory Item

```text
Bones

  Bury
* Use
  Drop
  Examine
```

## Left-click and Shift-click Profiles

Where practical, support two independently configurable preferences:

- Preferred left-click action
- Preferred Shift-click action

Examples:

- Banker -> left-click Bank
- Bones -> Shift-click Bury
- Junk item -> Shift-click Drop
- Equipment -> Shift-click Wear
- Teleport item -> Shift-click preferred teleport action

## Persistence

Custom menu preferences should persist across client restart.

Persistence should be isolated from authoritative server gameplay state because this is a client interaction preference.

## Safety / Compatibility Rules

- Do not bypass server-side requirements.
- Do not invoke hidden or unavailable menu actions.
- Do not automate repeated actions.
- Do not silently change player preferences after updates.
- If an action disappears because content changes, fall back to the normal menu behavior.

---

# 3. Ground Items / Loot QOL

## Ground Item Highlighting

Optional ground-item visibility improvements.

Possible settings:

- Show item value
- Highlight items above a configured value
- Always highlight selected items
- Always hide selected junk items
- Highlight untradeables
- Highlight rare drops
- Highlight the item's ground tile

## Loot Beams

Add optional RS3-style visual loot beams for valuable or important drops.

Example configuration:

```text
Loot Highlighting

Minimum highlighted value: 100,000 gp
Loot beam threshold:       1,000,000 gp

[x] Show item value
[x] Highlight valuable drops
[x] Loot beam
[x] Highlight tile
[x] Rare-drop notification
```

Future beam tiers could represent different values or rarity levels.

Possible examples:

- Common valuable
- High value
- Rare
- Unique / collection-log item

Exact colors/effects should remain configurable or follow a consistent client theme.

## Value Source

Before implementation, define the value authority used for thresholds.

Possible sources include:

- Server-defined item value
- Cache/item-definition value
- A future custom economy value table

Do not mix multiple value authorities without explicitly defining precedence.

---

# 4. Bank QOL

## Bank Tags

Allow items to belong to custom organizational groups independent of their physical bank position.

Example tags:

- RoTS
- Nex
- Slayer
- Skilling
- Melee
- Ranged
- Magic

An item may belong to multiple tags.

This could pair well with Matrix3's existing preset/loadout direction without replacing the preset system.

## Custom Bank Groups / Tabs

Potential UI built from Bank Tags so players can quickly view all items associated with a selected activity.

Example:

```text
[ RoTS ] [ Nex ] [ Slayer ] [ Skilling ]
```

Selecting `RoTS` would display items tagged for that activity.

## Bank Shift-click Actions

Possible configurable Shift-click behavior:

- Withdraw-1
- Withdraw-5
- Withdraw-10
- Withdraw-X
- Withdraw-All
- Deposit equivalents where applicable

The action must still use normal bank interaction handling and server validation.

---

# 5. Indicators

Potential optional indicators inspired by modern RuneScape client QOL.

## NPC Indicators

Possible options:

- Highlight selected NPCs
- Highlight current target
- Highlight aggressive NPCs where that information is reliably available
- Boss-specific highlighting

## Player Indicators

Possible options:

- Local player true tile
- Current target
- Friends / clan / party indicators if reliable data exists

## Object Indicators

Allow selected world objects to be marked/highlighted.

Possible uses:

- Entrances
- Portals
- Resource nodes
- Boss mechanics
- Important interactable objects

## Tile Indicators

Possible options:

- Destination tile
- Player tile / true tile
- Custom marked tiles
- Target tile
- Boss mechanic tiles

Keep developer/debug tile overlays separate from normal player-facing QOL where appropriate.

---

# 6. Notifications

Optional notifications should be individually configurable.

Potential options:

- Rare drop
- Valuable drop
- Collection-log entry
- Player idle
- Low HP
- Low Prayer
- Poison / status warning
- Boss kill
- Personal best
- Achievement / milestone

Avoid excessive default notifications. Players should be able to disable noisy categories.

---

# 7. Tracking / Session QOL

Potential later additions:

- XP tracker
- Session XP
- XP/hour
- Boss kill counter
- Boss kill timer
- Personal-best timer
- Loot tracker
- Session loot value
- Slayer task progress display

These should be considered later and should not delay the first core QOL interaction features.

---

# 8. Additional RuneLite-inspired Ideas Worth Evaluating

These are ideas to evaluate for Matrix3 rather than automatic implementation requirements.

- Inventory tags
- Ground item labels
- Ground item value display
- Ground markers
- Object markers
- NPC indicators
- Opponent information
- Item charge overlays
- Idle notifications
- Mouse tooltips
- Key remapping
- Screenshot on rare drop / personal best
- Loot tracker
- XP/session tracker
- Configurable menu entries

Matrix3/RS3 behavior remains authoritative. RuneLite is only a UX inspiration source.

---

# 9. Architecture Direction

## Prefer a Reusable Menu-action Framework

Do not build every convenience feature as a separate hardcoded click handler.

For example, instead of independently implementing:

- Shift-click Drop
- Shift-click Bury
- Banker left-click Bank
- Shopkeeper left-click Trade
- Portal left-click Enter

build one menu-preference layer capable of selecting from existing actions.

Conceptually:

```text
Entity/menu context
        |
        v
Available normal actions
        |
        v
Player preference lookup
        |
        v
Preferred left-click / Shift-click action
        |
        v
Existing normal action dispatch
```

The QOL layer should choose an existing action; the existing client/server interaction path should still perform the action.

This keeps gameplay authority in the existing Matrix3 systems and avoids creating duplicate action implementations.

## Persistence Boundary

Client-only preferences can include:

- Preferred menu entries
- Highlight lists
- Hidden ground items
- Loot value thresholds
- Indicator preferences
- Notification preferences
- Tags

Server-authoritative gameplay state should not be moved into client preference storage.

## Performance

QOL systems should avoid expensive full-world scans every frame.

Prefer:

- Existing entity/menu hooks
- Cached preference lookups
- Event-driven updates where practical
- Lazy loading for larger configuration lists

---

# 10. Suggested Implementation Order

## Phase 1 - Menu Interaction Foundation

1. Menu Entry Customizer foundation
2. Preferred left-click action support
3. Preferred Shift-click action support
4. Preference persistence
5. Shift-click Drop as the first real use case

This proves the reusable framework before adding many special cases.

## Phase 2 - Ground Items

1. Ground item labels
2. Item value display
3. Highlight / hide lists
4. Value threshold
5. Loot beams
6. Drop notifications

## Phase 3 - Indicators

1. Target indicator
2. NPC indicators
3. Object markers
4. Tile markers / destination tile

## Phase 4 - Bank / Inventory Organization

1. Inventory tags
2. Bank tags
3. Custom bank groups/tabs
4. Bank Shift-click preferences

## Phase 5 - Tracking and Notifications

1. Idle / HP / Prayer notifications
2. Boss timers
3. Kill counters
4. XP/session tracking
5. Loot tracking
6. Screenshot triggers

---

# 11. Initial Priority Set

The strongest first QOL batch is:

1. **Menu Entry Customizer**
2. **Shift-click Drop**
3. **Ground Item Highlighting**
4. **Loot Beams**

The Menu Entry Customizer should be treated as the foundation because Shift-click Drop and many future interaction shortcuts can reuse it.

---

# 12. Non-goals

This QOL system should not become:

- Automated combat
- Automated skilling
- Automatic repeated interaction loops
- Server-authority bypasses
- Hidden action invocation
- A second implementation of Matrix3 gameplay systems

The objective is faster and cleaner player interaction, not automation of gameplay.
