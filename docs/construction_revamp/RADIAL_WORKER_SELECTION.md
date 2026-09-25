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
- action-23 world-hover changes update live edge B directly; there is no remaining pixels-per-tile calibration in RWS-2 geometry
- the reticule center is a fractional world-space midpoint and moves along the A-to-B line at resolved-world-tile granularity
- the reticule radius is half of the live A-to-B span
- the per-call GFX clone is recentered with `Model.method1358(...)` using the midpoint of its real X/Z bounds, removing model-origin bias
- GFX 4171's four decorative cardinal diamonds extend beyond the circular ring, so full-model X/Z bounds are not the circle radius
- when the renderer exposes `AbstractModel`, RWS-2 sorts cloned vertex radial distances and detects the large outer gap separating the dense circular-ring cluster from the decorative diamond cluster; the last radius before that gap becomes the ring-body radius
- non-`AbstractModel` renderer models use a bounded GFX-4171 fallback fraction rather than reverting to the incorrect full-marker extent
- runtime scale converts the resolved ring-body radius to the exact world-space selection radius through `Model.method1464(...)`, so the circular ring itself—not the outer diamonds—owns A/B
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

Status: RUNTIME VERIFIED

---

### RWS-3 — Worker Detection

Goal:
Detect settlement workers inside the radius.

Implementation:

- client-only live preview; persistent worker IDs/server authority remain deferred to RWS-5
- iterates Matrix3's bounded active-local NPC list (`client.anIntArray8626` / `anInt8625`) rather than scanning definitions or the whole world
- filters to same-plane NPC definition 1 with the server-overridden display name `Settler`
- converts each active worker's scene tile to world coordinates through the current scene base
- tests worker center membership with exact `distanceSquared <= liveRadiusSquared`
- while dragging, every detected worker receives a temporary small GFX 4171 direct-render marker
- Radial Status reports `workersInCircle=<count>` plus detected runtime NPC indexes
- preview state clears on a new drag/cancel; release hides the drag-time visualization
- no server command, worker AI, combat target, persistence or cache definition is changed

Requirements:

- active NPC list only
- settlement workers only
- distance calculated in world space
- live preview updates while dragging
- workers outside the circle are not preview-marked
- preview markers disappear when the active drag ends

Status: RUNTIME VERIFIED

Runtime evidence:

- live screenshot verified both active settlement workers detected simultaneously inside the large RWS-2 circle
- each detected worker received the temporary small GFX 4171 preview marker
- the large radial selector and per-worker preview markers rendered together correctly

---

### RWS-4 — Selection Reticules

Goal:
Show selected workers using RuneScape-native visual feedback with the same proven reticule asset.

Decision:
GFX `4187` is no longer part of the RWS-4 plan. Do not continue the double-ring probe.

Implementation:

- reuse GFX `4171` for all radial-selection visuals
- the large drag ring remains one independently tinted 4171 clone
- each detected worker renders two additional independent 4171 clones at the same world position
- outer worker ring default scale: 100%
- inner worker ring default scale: 70%
- outer and inner worker scales are independently adjustable from 25%-300% in Con Revamp
- drag, outer and inner ring colors are independently configurable through Swing `JColorChooser` pickers
- selected RGB is converted to Matrix3 packed-model H/S/L targets and applied through `Model.method1396(..., weight=128)`
- runtime proved GFX 4171's visible red artwork is texture-driven enough that face-HSL tint alone is not visibly effective
- custom-color mode therefore uses clone flag `0x8000` to own a private face-texture ID array, detaches the clone's textures to `-1`, then applies HSL to the exposed face colours
- renderer-aware texture enumeration now supports `AbstractModel.aShortArray10821`, `Class89_Sub2.aShortArray10591`, and `OpenGLModel.aShortArray10306`; OpenGL was previously unhandled and is the strongest remaining renderer hypothesis after the first two fixes had no visible effect; active-renderer ownership remains pending runtime confirmation
- native/original mode never strips textures; `0x80000` still isolates face colours and `0x8000` isolates texture IDs from the cached source model
- choosing Use Original Colors restores native GFX 4171 colors without touching scale
- RWS-3 live detection immediately previews the current two-layer worker style during drag
- no cache definition, combat reticule, worker AI, server selection or persistence owner changes

Runtime acceptance:

- drag-ring color can change independently
- outer and inner worker rings render simultaneously on each detected worker
- changing outer scale does not change inner scale
- changing inner scale does not change outer scale
- outer/inner color pickers recolor only their own cloned layer
- Use Original Colors restores native 4171 appearance
- layered rings remain terrain anchored and disappear with RWS-3 preview when the drag ends

Runtime evidence:

- layered outer + inner GFX 4171 rings render simultaneously on multiple detected workers
- independent outer/inner scale controls work at runtime
- renderer-aware recolor now covers AbstractModel, Class89_Sub2 and OpenGLModel
- user runtime screenshot/confirmation verified the chosen colors visibly apply to the layered worker rings

Status: RUNTIME VERIFIED

---

### Worker Needs HUD — Visual Prototype

Goal:
Find a readable ground HUD for persistent worker Hunger / Thirst / Energy before adding a new server-to-client metadata channel.

Server semantics already VERIFIED:

- Hunger is pressure: 0 = satisfied, 100 = critical; current critical threshold = 80
- Thirst is pressure: 0 = satisfied, 100 = critical; current critical threshold = 80
- Energy is reserve: 100 = rested, 0 = exhausted; current critical threshold = 20
- persistent values remain owned by `SettlementWorkerState`

Rejected prototype:

- three concentric full GFX 4171 rings rendered successfully
- user runtime screenshot showed the result was too busy / visually wrong for the intended HUD
- do not return to the three-full-ring layout

Active arch prototype:

- reuses the proven GFX `4171` recolor/scale renderer
- the HUD has exactly two visual levels while Needs HUD is enabled: outer full ring = selection, inner/status circumference = needs arches
- the old full inner selection ring is suppressed while Needs HUD is enabled
- all three needs share the former inner-ring circumference at a default 70% worker-relative scale
- each need owns a separate ~100-degree angular slot with ~20-degree gaps
- Hunger slot: orange -> red as pressure rises; arch length uses `(100 - hunger)%`
- Thirst slot: cyan -> red as pressure rises; arch length uses `(100 - thirst)%`
- Energy slot: yellow -> red as reserve falls; arch length uses `energy%`
- current slot centers are 30 / 150 / 270 degrees so the three arches are separated evenly
- OpenGL GFX clones use isolated per-face alpha (`0x100` clone capability) to hide faces outside each angular span
- OpenGL face masking inverts `anIntArray10329/aShortArray10330` to map render vertices back to original X/Z model vertices
- first arch runtime video proved angular masking alone still included 4171's outer decorative diamond/spike geometry, producing chunky colored fragments
- corrected mask resolves the same ring-body radius seam used by the working drag ring, then requires every vertex of a candidate face to remain inside the circular ring-body annulus before applying the angular need slot; centroid-only radial acceptance was too permissive for decorative spike/diamond triangles
- visible ring-body faces retain the already runtime-verified recolor path; all decorative/out-of-slot faces are alpha 255
- Con Revamp exposes live demo Hunger / Thirst / Energy values plus one shared arch-scale control
- preview can render with Worker Control disabled
- selection / drag rings are unchanged
- values remain DEMO ONLY; no NPC-name/combat-level/config encoding and no fake live sync
- non-OpenGL arc masking is intentionally not guessed in this slice; the current runtime target is the renderer path already proven by the 4171 recolor fix

Runtime acceptance:

- every active Settler shows three separated arches rather than concentric full rings
- no need produces a full 360-degree ring
- Hunger / Thirst / Energy are distinguishable by both angular slot and color
- lowering wellbeing shortens only that need's arch
- severity color still trends red near the authoritative threshold
- shared scale resizes the whole needs HUD without changing slot ownership
- committed selection rings remain readable with the needs arches
- disabling preview removes all needs arches immediately

Next architectural step after visual acceptance:
Add a clean per-worker server -> client needs metadata seam and replace only the demo values.

Status: IMPLEMENTED / NEEDS RUNTIME TEST

---

### RWS-5 — Server-Authoritative Selection

Goal:
Send committed selection to the server safely.

Implemented:

- release commits only the render-thread-detected settlement NPC indexes plus optional local-player membership
- SettlementInstance validates runtime NPC ownership and converts worker NPC indexes to persistent Worker IDs
- committed selection remains transient to the active settlement instance
- selected-unit GFX 4171 rings persist after release and ordinary clicks do not replace the selection
- world right-click menus expose one Clear Selection option while a selection exists; it clears both local visualization state and server transient selection
- settlement scene exit/rebuild invalidates the transient selection and requires a new drag

Status: RUNTIME VERIFIED

---

### RWS-6 — Group Command Foundation

Goal:
Use Matrix3's normal world interaction surface to command the committed selection.

Implemented:

- action 23 ground movement uses a dedicated double-click gate: the first click is consumed, and a second click within 375 ms on the same/adjacent tile mirrors a transient move order to selected workers
- a completed radial selection drag clears the double-click state and its release is consumed, so selection can never accidentally become move-click #1
- if self is selected, only the accepted second click continues into vanilla player Walk Here; otherwise only the selected workers move
- normal first-option skilling interactions mirror starter Wood 1276, Stone 11933 and Basic ore 11936 object nodes plus Food NPC 327
- selecting the same skill option from the vanilla right-click menu produces the same worker order as direct clicking
- worker manual Move/Gather orders are runtime overrides only; normal Allowed Jobs policy resumes after completion
- server validates the exact active SettlementResourceNode before assigning a gather order

Status: RUNTIME VERIFIED

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
- [ ] Single ground click preserves selection and moves nobody
- [ ] Double-click ground commands the selected workers and preserves selection
- [ ] A radial drag release never seeds or triggers the double-click move gesture
- [ ] Vanilla Wood / Stone / Ore / Food skill options command the selected workers
- [ ] Right-click world menus show one Clear Selection option while a group is committed
- [ ] Clear Selection removes the rings and server transient selection without changing worker policy
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
