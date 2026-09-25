# Construction Build Hotbar

Status: ACTIVE foundation
Workstream: Construction Revamp
Owner: client-side Construction build UX; gameplay placement remains owned by existing ConstructionPlacementController/server settlement APIs.

## Goal

Add a dedicated Construction build hotbar inspired by RuneScape's action-bar readability without taking over the real combat/action bar.

The hotbar is the player's explicit build-tool selector. It should make Construction feel like a building/automation game: fast number-key tool changes, clear selected state, and no geometry guessing when a rail interaction requires a special tool.

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

- Separate lightweight overlay; do not modify or replace Matrix3's combat/action bar.
- Anchor near the lower center of the game canvas, clear of the Construction palette.
- Selected/armed slot must be visually obvious.
- Reserved/unimplemented slots stay visible but subdued.
- Hotbar follows palette show/hide lifecycle.
- Mouse click and 1-9 hotkeys must call the same tool actions.

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
ConstructionBuildHotbar
        |
        +--> ConstructionPlacementController (Rail/Object/Erase/Rotate)
        |
        +--> existing settlement undo command
        |
        +--> future Rail tool mode (Junction/Crossing/Splitter)
                    |
                    +--> logical rail topology
                    +--> physical-object resolver
```

The hotbar is UX only. It does not own persistent builds, rail topology persistence, object placement, settlement state, or cart routing.

## Phases

### Phase H1 - Hotbar foundation
- [x] Define explicit nine-slot tool contract.
- [ ] Add separate ConstructionBuildHotbar overlay.
- [ ] Add 1-9 hotkeys while palette is open.
- [ ] Wire Rail/Object/Eraser/Rotate/Undo to existing owners.
- [ ] Keep Junction/Crossing/Splitter/Favorites visibly reserved, non-fake.
- [ ] Runtime UI/input smoke test.

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

1. Open Construction palette; hotbar appears separately.
2. Press 1 -> Rail is armed.
3. Select Bed/Fence/etc. from palette; slot 5 reflects that selected object.
4. Press 1 then 5 -> swaps Rail <-> last object without reopening category/search.
5. Press 6 -> Eraser arms.
6. Press 7 with an object armed -> rotation advances.
7. Press 8 -> existing settlement undo executes.
8. Slots 2/3/4/9 clearly indicate reserved/unavailable and do not place fake content.
9. Close Construction palette -> build hotbar hides and number keys return to normal Matrix3 ownership.

## Resume Here

Implement Phase H1 only. Do not invent Junction/Crossing/Splitter assets. Preserve the current Rail first-contact clamp until Phase H2 has accepted special-node art and semantics.
