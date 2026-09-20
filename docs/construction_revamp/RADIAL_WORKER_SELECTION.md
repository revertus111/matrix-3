# Matrix3 Construction — Radial Worker Selection

## Purpose

Add an RTS-style world-space radial selection mechanic for settlement workers.

The player holds a mouse button on the ground, drags outward to expand or shrink a circular selection radius, and releases to select every eligible settlement worker inside that area.

This system should feel native to RuneScape/Matrix3 rather than like a detached desktop overlay.

---

## Core Interaction

1. Player enters Worker Control mode.
2. Player presses and holds the configured mouse button on valid ground.
3. The initial world position becomes the selection origin.
4. Moving the mouse away from the origin changes the selection radius continuously.
5. A circular ground reticule expands/shrinks with the cursor.
6. Workers currently inside the radius receive live selection feedback.
7. Releasing the mouse commits the selection.
8. Escape/cancel aborts the drag without changing the current selection.

Conceptually:

```text
Mouse Down
    ↓
Selection origin

Drag
    ↓
World-space distance from origin to hovered ground position

Radius
    ↓
Scale ground reticule

Workers inside radius
    ↓
Preview as selected

Mouse Release
    ↓
Commit worker selection
```

---

## Selection Geometry

Selection must be calculated in WORLD SPACE, not screen pixels.

Required values:

- selectionOriginX
- selectionOriginY
- currentHoverX
- currentHoverY
- radius

Example:

```text
radius = distance(selectionOrigin, currentHover)
```

Worker membership:

```text
distance(selectionOrigin, workerPosition) <= radius
```

This ensures the selection radius remains correct regardless of:

- camera rotation
- camera pitch
- camera zoom
- screen resolution
- interface scaling

---

## Ground Reticule

Reuse the existing RuneScape Target Reticule visual family where practical.

Verified existing system:

- Combat target reticules are real `Graphics` effects.
- `CombatDefinitions.getTargetReticule(Entity)` selects existing GFX IDs.
- NPC/player update masks transmit target reticule graphics.
- Existing reticules already render correctly on world terrain.

Known reticule GFX families currently include:

Current target:
- 4171
- 4173
- 4175
- 4177

Secondary/other target:
- 4179
- 4181
- 4183
- 4185

Mutual combat target:
- 4187
- 4189
- 4191
- 4193

The exact preferred visual for Construction must be runtime tested.

---

## Runtime Scaling Goal

Do NOT use Jagex's fixed-size-GFX approach for radial selection.

Construction should use one suitable reticule/model and dynamically scale it to the active world-space radius.

Target behavior:

- 1 tile radius
- 2 tile radius
- 3.5 tile radius
- 8 tile radius
- 12 tile radius
- etc.

The ring should resize continuously while dragging rather than jumping between predefined sizes.

Desired abstraction:

```text
drawRadialSelection(origin, radius)
```

rather than:

```text
if size == 1 -> gfx A
if size == 2 -> gfx B
if size == 3 -> gfx C
```

---

## GFX Investigation

The existing GFX editor has already demonstrated that GFX/model transforms and scale can be manipulated.

Before implementation, trace the smallest relevant client GFX render path to determine:

- where GFX model scale is applied
- whether scale can be overridden per runtime instance
- whether X/Z scale can be changed independently of Y
- whether the reticule model can remain flat on terrain while expanding horizontally
- whether recolor/tint/alpha can be controlled per instance
- whether animation timing must be disabled or frozen for persistent selection use

Do not edit the original cache definition merely to resize the selection radius.

Preferred result:

```text
one original reticule asset
+
Construction-owned runtime transform
```

---

## Live Worker Preview

While dragging:

- eligible workers inside the current radius show a selection marker
- workers leaving the radius immediately lose preview state
- non-settlement NPCs are ignored
- other players are ignored
- objects are ignored

The preview selection is temporary until mouse release.

Example:

```text
[Worker 1] selected preview
[Worker 2] selected preview
[Worker 3] outside radius
[Random NPC] ignored
```

---

## Committed Selection

On release, the preview set becomes the active worker-selection set.

Selection should use persistent settlement worker identity server-side.

Client/runtime NPC indexes may be used for immediate interaction, but the server must validate:

- NPC exists
- NPC is a `SettlementWorkerNpc`
- worker belongs to the player's active settlement
- worker resolves to a valid persistent worker ID

The server remains authoritative.

---

## Multiple Worker Commands

The radial selector becomes the input layer for group worker commands.

Future commands can operate on the committed selection:

- Move
- Pause
- Resume
- Assign Wood
- Assign Mining
- Assign Food
- Assign Hauling
- Assign Work Area
- Clear Work Area
- Assign Building
- Assign Production Station

Worker AI remains owned by the existing settlement system.

The radial selector must not duplicate worker AI.

---

## Work-Area Reuse

The radial ground selection primitive should be reusable later for:

- sawmill work radius
- fishing-hut radius
- mining zones
- farming zones
- guard areas
- hauling zones
- storage pickup/drop zones
- machine influence ranges
- settlement automation areas

Worker selection is the first implementation.

Do not bundle all later area systems into the first patch.

---

## Input Ownership

Radial selection must not permanently steal normal RuneScape mouse input.

Preferred ownership:

Worker Control mode OFF:
- normal RuneScape behavior

Worker Control mode ON:
- configured drag input begins radial worker selection
- normal clicks continue where they do not conflict
- Escape/cancel exits active drag

Exact mouse-button choice remains configurable until runtime acceptance.

---

## Performance Rules

During an active drag:

- inspect only active/visible settlement worker NPCs
- do not scan the full NPC world list
- do not perform server queries every frame
- do not invoke worker pathfinding
- do not modify worker AI

Selection math is lightweight distance testing.

Target cost should be negligible even with dozens of settlement workers.

---

## Architecture

### Client owns

- mouse drag state
- selection origin
- hovered world position
- live radius
- radial visual
- temporary worker preview
- active visual selection state

### Server owns

- settlement worker identity
- validation
- persistent worker IDs
- worker assignments
- worker commands
- worker AI
- persistence

---

## Proposed Implementation Phases

### RWS-1 — Reticule Scale Proof

Goal:
Prove an existing target reticule can be rendered independently and runtime-scaled.

Test:

- spawn/display reticule without combat
- change scale at runtime
- verify terrain projection
- verify camera rotation/zoom
- verify removal/cleanup

Status: PLANNED

---

### RWS-2 — Radial Drag Input

Goal:
Create the world-space drag interaction.

Requirements:

- mouse down captures origin
- mouse movement updates radius
- mouse release commits
- Escape cancels
- ring grows/shrinks smoothly

Status: PLANNED

---

### RWS-3 — Worker Detection

Goal:
Detect settlement workers inside the radius.

Requirements:

- active NPC list only
- settlement workers only
- distance calculated in world space
- live preview updates while dragging

Status: PLANNED

---

### RWS-4 — Selection Reticules

Goal:
Show selected workers using RuneScape-native visual feedback.

Requirements:

- preview marker during drag
- persistent marker after commit
- removed when deselected
- no interference with combat reticules

Status: PLANNED

---

### RWS-5 — Server-Authoritative Selection

Goal:
Send committed selection to the server safely.

Requirements:

- validate NPC ownership
- convert runtime NPC index to persistent worker ID
- reject unrelated NPCs
- maintain active worker-selection set

Status: PLANNED

---

### RWS-6 — Group Command Foundation

Goal:
Allow one command to target all selected workers.

Initial test command:

Pause / Resume selected workers

This proves the multi-worker control path without adding new production systems.

Status: PLANNED

---

## Automated / Consolidated Test

Add a Construction Revamp test action that reports:

- radial system enabled
- selection origin captured
- current radius
- workers detected
- selected worker IDs
- invalid NPC filtering
- server selection validation

Runtime acceptance should be consolidated into one client/server launch.

---

## Acceptance Checklist

- [ ] Worker Control mode activates safely
- [ ] Ground press establishes selection origin
- [ ] Reticule appears at correct world location
- [ ] Dragging outward expands reticule smoothly
- [ ] Dragging inward shrinks reticule smoothly
- [ ] Camera rotation does not change world radius
- [ ] Camera zoom does not change world radius
- [ ] Workers entering radius preview as selected
- [ ] Workers leaving radius lose preview
- [ ] Random NPCs are ignored
- [ ] Release commits correct workers
- [ ] Cancel preserves previous selection
- [ ] Selected workers retain visible markers
- [ ] Combat reticules remain unaffected
- [ ] Group Pause affects selected workers only
- [ ] Group Resume affects selected workers only
- [ ] Existing single-worker controls still work
- [ ] No noticeable client performance regression

---

## Evidence Classification

### VERIFIED

- Existing combat target reticule is visible in live Matrix3 runtime.

### verified-static

- Target reticule uses `Graphics`.
- `CombatDefinitions.getTargetReticule(Entity)` selects reticule GFX.
- `LocalNPCUpdate` and `LocalPlayerUpdate` contain dedicated target-reticule masks.
- Existing system uses separate GFX IDs for predefined entity sizes.

### HYPOTHESIS

- Existing reticule model can be cleanly runtime-scaled per Construction instance.
- Existing GFX model can be reused for arbitrary large radial visualization without cache modification.

These must be proven before the implementation depends on them.

---

## Non-Goals

This first system does NOT include:

- sawmill automation radius
- fishing-hut automation radius
- mining work zones
- full group-order UI
- formation movement
- combat squad controls
- cache interface editing
- replacing RuneScape combat reticules

Those systems may reuse this foundation later.

---

## Intended End Result

The mechanic should feel like a RuneScape-native RTS control:

```text
Hold → drag → world ring expands → workers inside light up → release → group selected.
```

The same world-space radial primitive then becomes reusable throughout the Construction settlement and automation systems.
