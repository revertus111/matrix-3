# Construction Build Hotbar

Status: ACTIVE foundation
Workstream: Construction Revamp
Owner: temporary native-action-bar Construction UX; gameplay placement remains owned by existing ConstructionPlacementController/server settlement APIs.

## Goal

Reuse Matrix3's native RuneScape action bar (interface 1430) as a temporary Construction build bar while the Build Palette is open.

Construction does not overwrite the player's saved combat/action-bar shortcuts. The existing ActionBar.shortcuts[][] remain persistent gameplay state; Construction temporarily replaces only the client-facing slot visuals/input routing, then restores the player's real bar when build mode closes.

The build bar is the player's explicit build-tool selector. It should make Construction feel like a building/automation game: fast number-key/tool changes, RuneScape-native presentation, and no geometry guessing when a rail interaction requires a special tool.

## Core interaction rule

Normal build tools do not infer special overlap intent.

For rails:
- Rail may draw ordinary track, extend track, and use the currently accepted branch behavior.
- Rail may connect into existing authored rail at first contact.
- Rail may not pass through existing authored rail.
- Crossing an existing rail requires an explicit Crossing tool/piece.
- Dedicated Junction / Splitter tools will own special connection semantics once accepted cache art is classified.
- Physical overlap and logical rail connectivity are separate concepts. A future Crossing can visually occupy the same crossing area without automatically becoming a switch.
- Cart routing must continue using logical graph edges, never object rotation as travel direction.

## V1 hotbar layout

Nine slots, keyboard 1-9 while the Construction palette is open:

1. Rail
2. Junction
3. Crossing
4. Splitter
5. Object
6. Eraser
7. Rotate
8. Undo
9. Favorites

V1 behavior:
- Rail: arms the existing basic-rail tool.
- Object: re-arms the most recently selected non-rail Construction build piece.
- Eraser: arms the existing server-authoritative eraser.
- Rotate: rotates the currently armed build piece +90 degrees.
- Undo: sends the existing settlement undo command.
- Junction / Crossing / Splitter / Favorites are reserved visible slots until their owning assets/behavior exist. They must not fake placement.
- Clicking a normal build-piece card updates the Object slot.
- Number keys are owned by the build hotbar only while the Construction palette is open.

## UX

- Native host: interface 1430, mounted by Matrix3 at root 1477 component 35.
- NIS: reuse the action bar in its normal gameframe location.
- Legacy: Construction explicitly remounts/unhides interface 1430 while the Build Palette is open. It must not change the player's Legacy preference.
- Closing Construction restores the real action-bar vars, lock state and interface-mode vars.
- ConstructionBuildHotbar is now a controller/input bridge only; the old floating JWindow renderer is superseded.
- Mouse click and 1-9 hotkeys call the same Construction tool actions.
- Reserved/unimplemented slots remain present as temporary icon tokens but do not fake placement.
- Current icon item IDs are visual-only tokens; they are not inventory items, persistent shortcut definitions or Construction gameplay identity. Custom sprites/labels may replace them later.

## Rail overlap policy

The current first-contact clamp remains the safe default until special rail tools exist:

```
ordinary Rail:
NEW ----------->
               X  first existing rail contact = endpoint
EXISTING ======X========
```

Future Crossing:
```
NEW ----------->
               |
EXISTING ======+========
               |
logical networks remain independent unless a Junction/Splitter explicitly connects them
```

Future Junction:
- Explicitly converts one selected rail location into a 3-way connection.
- Junction art and occupied footprint must be cache-verified before runtime placement is enabled.

Future Splitter/Merge:
- Explicit route-selection node for automation/cart routing.
- Must define incoming/outgoing logical edges independently from object rotation.

## Architecture

```
Build Palette open
      |
      +--> client ConstructionBuildHotbar.show()
      |        |
      |        +--> ::settlementbuildbaropen
      |                  |
      |                  +--> server ActionBar.beginConstructionMode()
      |                           |
      |                           +--> mount/unhide native 1430 @ 1477:35
      |                           +--> temporary build icons in client vars
      |                           +--> persistent shortcuts[][] untouched
      |
native 1430 slot click
      |
      +--> ButtonHandler
               |
               +--> ActionBar construction-mode intercept
                        |
                        +--> packet 69: constructionbar <slot>
                                  |
                                  +--> PacketsDecoder
                                           |
                                           +--> ConstructionBuildHotbar.activate()
                                                    |
                                                    +--> ConstructionPlacementController
                                                    +--> settlement undo
                                                    +--> future special rail tools
```

The hotbar is UX/input routing only. It does not own persistent builds, rail topology persistence, object placement, settlement state, combat shortcut persistence or cart routing.

## Phases

### Phase H1 - Native action-bar foundation
- [x] Define explicit nine-slot tool contract.
- [x] Supersede the floating Construction JWindow with Matrix3 native interface 1430.
- [x] Preserve the player's persistent ActionBar.shortcuts[][] while Construction temporarily owns client slot visuals/input.
- [x] Add 1-9 hotkeys while palette is open.
- [x] Route native 1430 slot clicks back into the same Construction tool actions.
- [x] Wire Rail/Object/Eraser/Rotate/Undo to existing owners.
- [x] Keep Junction/Crossing/Splitter/Favorites reserved and non-fake.
- [x] Force native 1430 visible during Construction in Legacy without switching the player's interface preference.
- [x] Restore real action-bar vars/lock state and Legacy/NIS mode vars on close/settlement teardown.
- [ ] Runtime NIS + Legacy UI/input smoke test.

### Phase H2 - Explicit rail special tools
- [ ] Classify/accept Junction art.
- [ ] Classify/accept Crossing art or approved same-area overlay solution.
- [ ] Classify/accept Splitter/Merge art.
- [ ] Add explicit rail tool mode/policy enum.
- [ ] Replace temporary generic branch placeholder where accepted art exists.
- [ ] Add cart-routing semantics for special nodes.

### Phase H3 - Player-customizable build slots
- [ ] Allow build objects/prefabs to be assigned to slots.
- [ ] Persist per-player hotbar assignments.
- [ ] Add Favorites/browser assignment workflow.
- [ ] Keep special tools available without colliding with saved build items.

## Runtime gate for Phase H1

1. NIS: open Construction palette; the separate floating BUILD HOTBAR must be gone and native interface 1430 must show the temporary Construction icons.
2. Press 1 -> Rail is armed. Click native slot 1 -> the same Rail action fires.
3. Select Bed/Fence/etc. from palette; press 1 then 5 -> swaps Rail <-> last object without reopening category/search.
4. Press/click 6 -> Eraser; re-arm an object and press/click 7 -> Rotate; press/click 8 -> existing settlement Undo.
5. Slots 2/3/4/9 remain reserved and do not perform fake placement.
6. Close Build Palette -> the player's original combat/action-bar contents and lock state return exactly.
7. Legacy: switch to Legacy before opening the palette. Opening Construction must force native 1430 visible without changing Legacy mode; closing Construction must restore the Legacy presentation.
8. Exit/teleport/logout from the settlement while Construction is active -> teardown restores the player's normal action bar.
9. Reopen Construction after both NIS and Legacy tests; persistent combat shortcuts must be unchanged.

## Resume Here

Runtime-test the native 1430 retrofit in both NIS and Legacy. Do not invent Junction/Crossing/Splitter assets. Preserve the current Rail first-contact clamp until Phase H2 has accepted special-node art and semantics.
