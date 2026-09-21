# Matrix3 Construction — Radial Worker Selection

## Purpose

Add an RTS-style world-space radial selection mechanic for settlement workers.

The player holds a mouse button on the ground, drags outward to expand or shrink a circular selection radius, and releases to select every eligible settlement worker inside that area.

This system should feel native to RuneScape/Matrix3 rather than like a detached desktop overlay.

---

## Core Interaction

1. Player enters Worker Control mode.
2. Player presses and holds the configured mouse button on valid ground.
3. The initial world position becomes selection edge A.
4. The current dragged world position becomes edge B.
5. The circular reticule continuously moves to the midpoint between A and B while its radius becomes half of the A-to-B distance.
6. Workers currently inside the live circle receive selection feedback.
7. Releasing the mouse commits the final center/radius selection geometry.
8. Escape/cancel aborts the drag without changing the current selection.

Conceptually:

```text
Mouse Down
    ↓
Edge A

Drag
    ↓
Edge B

Midpoint(A, B) + distance(A, B) / 2
    ↓
Move + scale ground reticule

Workers inside live circle
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

- startWorldX / startWorldY (edge A)
- currentWorldX / currentWorldY (edge B)
- centerWorldX / centerWorldY
- radius

Example:

```text
center = midpoint(startWorld, currentWorld)
radius = distance(startWorld, currentWorld) / 2
```

Worker membership:

```text
distance(center, workerPosition) <= radius
```

The press point is therefore one edge of the circular selection, not the center. Dragging across the settlement makes the circle span from the press location to the current dragged location.

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

Implementation:

- client-only `ConstructionRadialSelection` proof renderer
- uses target-reticule GFX `4171`
- mirrors Matrix3's already-resolved world hover tile; no second picker
- obtains a per-call GFX model clone through `GraphicsDefinition.method7764(...)`
- applies a second per-instance X/Z `Model.method1464(...)` scale while leaving Y at stock scale
- direct-renders through the already-proven `Class578.method6834(...)` Construction scene seam
- does not mutate cache definitions, combat targets, server state, scene registration, collision or persistence
- Con Revamp exposes Show / Apply Scale / Status / Hide controls with 25%-1200% proof range

Test:

- spawn/display reticule without combat
- change scale at runtime
- verify terrain projection
- verify camera rotation/zoom
- verify removal/cleanup

Status: RUNTIME VERIFIED

---

### RWS-2 — Radial Drag Input

Goal:
Create the world-space drag interaction.

Implementation:

- dedicated client-side Worker Control mode
- configurable Left mouse / Right mouse drag ownership
- press captures the current Matrix3-resolved world ground tile as fixed edge A and is consumed by Worker Control
- drag-motion events are intentionally NOT consumed so Matrix3's existing mouse/menu path can keep resolving the action-23 ground hover used as edge B
- selection span no longer uses AWT pixel distance or pixels-per-tile calibration; live A-to-B distance comes directly from the resolved world coordinates
- release remains consumed by Worker Control, preventing the owned gesture from becoming a normal ground click
- later action-23 world-hover changes recalibrate pixels-per-tile against exact world distance and update the live world direction
- the reticule center is a fractional world-space midpoint and moves continuously along the A-to-B line
- the reticule radius is half of the live A-to-B span
- runtime model scale is derived from the cloned GFX model's actual X/Z bounds (`method1380/method1381/method1384/method1508`), converting the desired world radius into an exact `Model.method1464(...)` scale
- before scaling, the per-call GFX clone is recentered with `Model.method1358(...)` using the midpoint of its real X/Z bounds; this removes model-origin bias that otherwise makes one visual edge drift during scale
- after recentering, rendered visual radius equals the midpoint offset, so edge A can remain visually pinned while B moves
- the former 12-tile radius stop is replaced by a 64-tile safety cap, which is non-limiting for the 64x64 starter settlement
- release commits edge A + final center + radius
- Escape cancels only the active drag and preserves the previous committed radius
- release commits origin + radius data but immediately hides the large area reticule; only active drag renders the area ring
- committed radius remains available internally for RWS-3 worker resolution even though the area ring is hidden after release
- disabling Worker Control stops owning the configured mouse button without deleting the committed radius data
- no worker detection, server selection, combat target state or cache mutation is included in RWS-2

Requirements:

- mouse down captures edge A
- mouse movement updates edge B, midpoint and half-span radius
- the ring spans from A to B rather than expanding equally around A
- long normal settlement drags do not stop at the old 12-tile radius
- mouse release commits final center/radius data
- release hides the large area reticule immediately
- Escape cancels
- dragging back shrinks/repositions the ring naturally

Status: IMPLEMENTED / NEEDS RUNTIME TEST

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

#### GFX 4187 double-ring visual preflight

User-selected candidate: GFX `4187`, a visible red/yellow double-ring reticule.

Implemented probe:

- client-only; does not enter combat or mutate the cache definition
- reuses the proven Construction immediate-render seam
- loads a per-call GFX 4187 model clone with `0x80000` colour-copy isolation
- samples the clone's packed face-colour array when the active renderer exposes `AbstractModel`
- ignores `0xFFFF` sentinel faces and ranks the remaining colours by face count
- `Color A` and `Color B` independently call `Model.method1393(...)` on the two most frequent sampled colours
- A/B use deliberately obvious diagnostic replacement colours only; they are not final hunger/thirst/energy colours
- Probe Status reports the sampled packed colours/counts and render/recolor state

Runtime question:

- Do Color A and Color B map cleanly enough to the visible outer/inner rings that those rings can be independently recolored?
- If yes, GFX 4187 can remain the base visual candidate for selection + worker-needs presentation.
- If not, do not force the asset; retain the proven custom-render fallback for independent status arcs.

Probe status: IMPLEMENTED / NEEDS RUNTIME TEST.

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
- [ ] Mouse-down point remains edge A while current drag point acts as edge B
- [ ] Reticule center tracks the A/B midpoint and diameter spans A -> B
- [ ] Dragging outward expands/repositions reticule smoothly
- [ ] Dragging inward shrinks/repositions reticule smoothly
- [ ] Dragging beyond the old 12-tile radius continues normally
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
- RWS-1 direct-rendered target-reticule GFX 4171 successfully outside combat.
- The same reticule model runtime-scaled cleanly from 25% through at least 725% without switching GFX IDs.
- The reticule remained world/terrain anchored across visibly different camera framing/zoom during the runtime test.
- Runtime scaling therefore proves the intended single-asset radial-selection foundation is viable.
- RWS-2 input architecture reuses Matrix3's already-resolved action-23 ground tile for edge A/world-direction calibration and the proven global AWT event-listener pattern for temporary mouse ownership; no second scene picker is introduced.
- Runtime video of the first midpoint implementation verified a failure mode: radius changed with cursor distance, but the ring center stayed nearly fixed because the global listener consumed `MOUSE_DRAGGED` before Matrix3 could refresh the action-23 hover tile.

### verified-static

- RWS-2 press/release remain consumed by Worker Control, while allowing `MOUSE_DRAGGED` through preserves Matrix3's existing cursor/menu update path needed to refresh edge B without adding a second picker.
- `AbstractModel.method10016()` proves `method1380/method1381` expose horizontal X min/max and `method1384/method1508` expose horizontal Z min/max; RWS-2 now derives the cloned reticule's real base horizontal radius from those bounds before applying instance scale.
- Desired visual radius is `liveRadiusTiles * tileSize` world units, so the resulting runtime scale makes visual radius equal the A-to-midpoint distance instead of relying on a guessed percent-per-tile mapping.
- Target reticule uses `Graphics`.
- `CombatDefinitions.getTargetReticule(Entity)` selects reticule GFX.
- `LocalNPCUpdate` and `LocalPlayerUpdate` contain dedicated target-reticule masks.
- Existing system uses separate GFX IDs for predefined entity sizes.
- `GraphicsDefinition.method7762(...)` clones the cached model before instance transforms.
- `GraphicsDefinition` already uses `Model.method1464(...)` for definition scale.
- RWS-1 applies an additional X/Z-only `Model.method1464(...)` transform to the per-call clone, so the proof does not mutate the cache definition or cached base model.

### HYPOTHESIS

- Very large radii beyond the currently tested 725% range will remain visually acceptable.
- The static/frozen reticule frame will be sufficient for persistent Construction selection, or its animation controller will need to be added after visual acceptance.

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
