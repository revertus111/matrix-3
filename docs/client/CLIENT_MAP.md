# Matrix3 Client Map

## Purpose

This is the persistent searchable technical map of the Matrix3 client.

It is **not** intended to be read front-to-back during normal development. Search it for the exact subsystem, class, method, field, ID, opcode, cache index, renderer, behavior, or semantic term relevant to the current task, then read only the matching entry/context.

The goal is to preserve established client knowledge so future chats do not repeatedly reverse-engineer the same ownership and mappings.

## Usage rules

- Search before tracing unfamiliar client behavior.
- Prefer exact class/field/method/ID searches, then subsystem/semantic searches.
- Do not read the full document unless the task is explicitly a client-map audit/reorganization.
- Do not retrace `VERIFIED` or `verified-static` mappings without contradictory evidence, repository changes, or a runtime reason to re-verify them.
- Update existing entries instead of creating duplicates.
- Preserve original obfuscated/decompiled names unless renaming is explicitly approved elsewhere.
- Record relationships and ownership only when supported by evidence.
- Do not promote assumptions into facts.

## Evidence classification

- `VERIFIED` — runtime-confirmed behavior.
- `verified-static` — directly established from source/data without runtime confirmation.
- `HYPOTHESIS` — plausible interpretation that still needs proof.
- `UNKNOWN` — unresolved or insufficiently established.

When stronger evidence arrives, update the existing entry and its classification.

## Preferred search terms

Useful searches include:

- exact class name
- exact field name
- exact method name
- interface/component ID
- opcode/packet identifier
- cache index/archive/group/file ID
- object/NPC/item/animation/GFX/model/sprite ID
- renderer/scene/camera/input/minimap/compass semantic term
- subsystem tags listed in an entry

## Subsystem index

Populate these sections as knowledge is established. Empty sections are intentional and must not be filled with guesses.

- Camera / viewport
- Scene / renderer
- Minimap / compass
- Input / mouse / keyboard
- Menu / actions / context options
- Interfaces / components / scripts
- Player rendering
- NPC rendering
- Object rendering
- Ground items
- Projectiles
- GFX / spot animations
- Animation system
- Models
- Textures / materials
- Sprites
- Particles
- Lighting / shadows
- World map
- Pathing / movement presentation
- Audio
- Networking / packets
- Cache / definitions
- Client lifecycle / game state
- Miscellaneous verified mappings

---

# Mapping Entries

## Entry template

Copy this structure when adding a reusable mapping. Remove fields that genuinely do not apply; do not invent values to fill the template.

```md
### <Primary searchable name>

**Subsystem:** <subsystem>
**Evidence:** VERIFIED | verified-static | HYPOTHESIS | UNKNOWN
**Tags:** <comma-separated useful search terms>

**Exact symbols / IDs**
- Class: `<exact class>`
- Method: `<exact method>`
- Field: `<exact field>`
- IDs: `<relevant IDs>`

**Established responsibility**
- <what the evidence actually establishes>

**Relationships / call flow**
- <caller -> owner -> downstream behavior>

**Runtime evidence**
- <runtime confirmation when applicable>

**Static evidence**
- <source/data evidence when applicable>

**Matrix3 usage / ownership notes**
- <how current Matrix3 code uses or should respect this mapping>

**Do not assume**
- <important nearby semantics that remain unverified>

**Related entries**
- <other search terms/entries>
```

## Camera / viewport

### Live world viewport update seam — Class343.method4302(...)

**Subsystem:** Camera / viewport, client lifecycle  
**Evidence:** verified-static  
**Tags:** Class343, method4302, viewport tick, live render, ConstructionBuildCamera, MarioJumpController, scene render

**Exact symbols / IDs**
- Method: `Class343.method4302(int, int, int, int, boolean, byte)`
- Existing client extension: `ConstructionBuildCamera.tick()`
- Mario POC extension: `MarioJumpController.tick()`

**Established responsibility**
- `Class343.method4302(...)` is the live world-viewport render/update path that prepares camera state and submits the active scene.
- Matrix3 already uses this seam to tick the Construction detached camera immediately before the scene camera/renderer state is consumed.
- The Mario vertical-movement POC uses the same established per-viewport seam and does not introduce a parallel Swing timer or secondary render loop.

**Static evidence**
- Source reads the local player's `Class240` transform near method entry, resolves active camera state, then calls `ConstructionBuildCamera.tick()` before building `Class403` camera state and rendering the world scene.
- Historical Matrix3 commit `e028def6d22edf47f2978c1ccd44c85531f088b5` intentionally added the Construction tick at this point as the live viewport integration seam.

**Matrix3 usage / ownership notes**
- This is a client-presentation/update seam, not server gameplay authority.
- New alternate-controller presentation should remain narrowly scoped here unless stronger evidence establishes another owner.

**Do not assume**
- A viewport tick is not automatically the correct owner for server-authoritative movement, collision, packets, or persistence.
- The Mario POC runtime behavior remains unverified until tested in the live client.

**Related entries**
- Local-player scene transform — `Class611` / `Class456`
- Matrix3 held-key state — `Class549_Sub1`

### Construction detached camera ownership — ConstructionBuildCamera / Class24

**Subsystem:** Camera / viewport  
**Evidence:** VERIFIED  
**Tags:** ConstructionBuildCamera, Class24, Class411_Sub1, aClass411_Sub1_158, RTS camera, Free Build camera, detached camera, camera forward, AlternateCharacterController

**Exact symbols / IDs**
- Construction owner: `ConstructionBuildCamera`
- Active/requested state: `ConstructionBuildCamera.isRequested()`
- Rendered detached camera: `Class24.aClass411_Sub1_158`
- Camera position controller: `Class411_Sub1.method4990(...) -> Class423_Sub2`
- Camera look controller: `Class411_Sub1.method4991(...) -> Class658_Sub2`
- Look point: `Class658_Sub2.method7736(...)`

**Established responsibility**
- While Construction Free/RTS camera mode is active, the rendered Construction view is owned by `Class24.aClass411_Sub1_158`.
- That ownership is independent of the normal Matrix detached-camera flags used by stock camera-mode detection; consumers that need the *rendered* Construction view must check Construction ownership explicitly before falling back to vanilla camera state.
- The real ground-plane camera-forward direction is obtained from the detached camera's actual look point minus its actual position, then normalized.

**Relationships / call flow**
- `ConstructionBuildCamera.tick()` -> mutate/manage `Class24.aClass411_Sub1_158` -> viewport renders detached camera.
- camera-relative consumer -> if Construction active, select `Class24.aClass411_Sub1_158` -> position/look controllers -> normalized look-minus-position vector.
- `AlternateCharacterController.getCameraForward()` now follows this ownership order before normal detached/vanilla fallback.

**Runtime evidence**
- 2026-10-04 Mario steering video showed the rendered Construction/RTS camera rotating while the old alternate-character sampler stayed effectively frozen near `cameraForward=(-0.006, 1.000)`.
- This proved stale vanilla camera sampling was the active steering defect; the rendered Construction camera itself was rotating correctly.

**Static evidence**
- `ConstructionBuildCamera` directly manages the Class24 detached camera and already derives screen-relative editor movement from its real position/look direction.
- `Class343.method4302(...)` ticks `ConstructionBuildCamera` immediately before consuming/rendering the active scene camera.

**Matrix3 usage / ownership notes**
- Camera-relative gameplay/editor consumers should use the camera that actually owns the rendered view, not infer ownership solely from stock camera-mode flags.
- This mapping is reusable for Mario, Link, and future imported-character drivers through the shared alternate-character controller.

**Do not assume**
- Construction detached-camera ownership does not grant server movement, collision, pathfinding, or gameplay authority.
- This does not imply every detached camera is Construction-owned; outside active Construction mode, normal Matrix detached/vanilla ownership rules still apply.

**Related entries**
- Live world viewport update seam — `Class343.method4302(...)`
- Stock world minimap renderer — `Class464.method5484(...)`
- Stock compass renderer — `Class107.method2061(...)`

## Scene / renderer

### Scene-entry release/recycle seam — Class578.method6834(...)

**Subsystem:** Scene / renderer  
**Evidence:** verified-static  
**Tags:** Class578, method6834, Class531, Class545, Class523, scene entry, recycle, wrapper pool, aStack5931, ConstructionGhostPreview, ConstructionRadialSelection, direct render, preview hook

**Exact symbols / IDs**
- Release/recycle method: `Class578.method6834(Class531, int)`
- Wrapper type: `Class531`
- Wrapped scene entity: `Class531.aClass456_Sub1_5929`
- Reuse pool: `Class531.aStack5931`
- Ordered wrapper owner: `Class545.aList6110`
- Scene normal-terrain guard: `Class523.aClass174Array5875 == Class523.aClass174Array5838`

**Established responsibility**
- `Class578.method6834(...)` releases a temporary `Class531` scene-entry wrapper.
- The wrapped `Class456_Sub1` reference is cleared before the wrapper is returned to the shared `Class531.aStack5931` reuse pool.
- The reuse pool is capped at 200 wrappers.
- `Class545` calls this seam when ordered scene entries are removed or its list is drained; `Class523` also reaches it from immediate/nonqueued scene-entry cleanup paths.
- Matrix3 Construction/developer rendering currently uses the pre-recycle point as a narrow direct-render hook for client-only previews.

**Relationships / call flow**
- scene entity -> `Class531` temporary wrapper -> optional `Class545` ordered list -> `Class578.method6834(...)` -> clear wrapped entity -> return wrapper to capped pool.
- normal world-terrain scene -> pre-recycle Construction/dev preview dispatch -> `ConstructionGhostPreview`, `DevObjectPlacementPreview`, `ConstructionRadialSelection`, `ObjectLabPreview`, `LiveModelEditorPreview`, `ObjectCompositePreview`, `RailRoutePreview`, Live Model Editor gizmo overlay -> normal wrapper recycle.

**Static evidence**
- `Class545.method6449(...)`, `method6450()` and `method6451()` remove/drain `Class531` entries through `Class578.method6834(...)`.
- `Class523` source reaches `Class578.method6834(...)` from both an ordered-list drain and an immediate wrapper path.
- `Class578.method6834(...)` nulls `class531.aClass456_Sub1_5929`, synchronizes on `Class531.aStack5931`, and pushes the wrapper only while pool size is below `200`.
- Preview dispatch is guarded by an active `Class523` plus `scene.aClass174Array5875 == scene.aClass174Array5838`, establishing the normal-world-terrain restriction for this hook.

**Matrix3 usage / ownership notes**
- Construction ghost, radial-selection and editor previews are client presentation only; this hook does not grant them scene ownership, collision, persistence, or gameplay authority.
- Preserve the normal-terrain scene guard when using this direct-render seam so overlays are not submitted into unrelated alternate scene passes.
- Wrapper recycling remains Matrix3-owned. Preview code must run before the wrapped reference is cleared and must not retain or mutate the recycled wrapper as persistent state.

**Do not assume**
- `Class578` is not established as a scene-only class; it also contains unrelated CS2/decompiled utility behavior.
- `Class531` has not yet been semantically renamed; its broader lifetime and every use are not fully classified by this mapping.
- The current Construction preview hook is a proven project integration point, not evidence that every future overlay belongs in this seam.

**Related entries**
- Construction ghost preview
- Construction radial worker selection
- Live Model Editor preview
- `Class523` scene ownership

## Minimap / compass

### Stock world minimap renderer — Class464.method5484(...)

**Subsystem:** Minimap / compass  
**Evidence:** VERIFIED  
**Tags:** Class464, method5484, minimap, raster, map icons, NPC dots, player dots, destination, RTS minimap, ConstructionBuildCamera, 14-bit yaw, 0x3fff

**Exact symbols / IDs**
- Class: `Class464`
- Method: `Class464.method5484(Class106, InterfaceDefinitions, int, int, int)`
- Construction bridge: `ConstructionBuildCamera.getRtsMinimapCenterSceneUnitsPacked()`
- Construction bridge: `ConstructionBuildCamera.getRtsMinimapYawUnits()`

**Established responsibility**
- `Class464.method5484(...)` is the stock world-minimap rendering pipeline for an interface component.
- One scene-space center, heading and zoom value drive the base minimap raster and the relative positions of map icons, object/map markers, NPC/player dots and destination overlays.
- Normal mode derives the center from the local-player scene transform. The method also contains the stock detached-camera heading branch.
- Minimap heading is maintained in the RuneScape 14-bit `0..16383` domain (`& 0x3fff`) and is shifted left two bits when passed into the raster sprite renderer.

**Relationships / call flow**
- player/detached camera state -> shared minimap center/yaw/zoom -> raster draw -> icon/object-marker loops -> NPC/player/destination overlay helpers.
- Construction RTS -> `ConstructionBuildCamera` center/yaw bridge -> same stock minimap renderer and overlay pipeline.

**Runtime evidence**
- Construction RTS runtime testing confirmed that substituting the shared center makes the minimap follow the RTS camera instead of the player.
- Runtime testing also confirmed the corrected RTS yaw handedness keeps the raster, compass, NPC/player/map-icon overlays and rotated minimap click-to-focus aligned.

**Static evidence**
- `i_16_` / `i_17_` are consumed by the raster center calculation and reused throughout icon/object/NPC/player/destination relative-position calculations.
- `i_18_` is masked with `0x3fff` and passed to the raster renderer as `i_18_ << 2`.
- `i_19_` is the stock zoom value (`4096 - client.anInt8670 * 626807696`) outside the special camera-mode branch.

**Matrix3 usage / ownership notes**
- Construction RTS intentionally substitutes only minimap presentation center/yaw. Stock rendering, masks, zoom, icon iteration and scene ownership remain authoritative.
- Future minimap work should extend this owner rather than creating a second minimap renderer.

**Do not assume**
- The current obfuscated local variable names are not approved semantic renames yet.
- `Class464` itself is a mixed utility/decompiler class and is not established as a minimap-only class owner.

**Related entries**
- Stock compass renderer — `Class107.method2061(...)`
- `ConstructionBuildCamera`

### Stock compass renderer — Class107.method2061(...)

**Subsystem:** Minimap / compass  
**Evidence:** VERIFIED  
**Tags:** Class107, method2061, compass, sprite 4290, detached camera, RTS compass, ConstructionBuildCamera, 14-bit yaw, 0x3fff

**Exact symbols / IDs**
- Class: `Class107`
- Method: `Class107.method2061(InterfaceDefinitions, int, int, int)`
- Compass sprite ID: `4290`
- Construction bridge: `ConstructionBuildCamera.getRtsMinimapYawUnits()`

**Established responsibility**
- `Class107.method2061(...)` renders the stock compass inside its interface-component mask.
- Camera mode `1` derives heading from `Class133_Sub1.aClass411_Sub1_9827`; otherwise the normal vanilla camera yaw path is used.
- Construction RTS may substitute the same authoritative 14-bit yaw consumed by the minimap.
- The client rotation offset is applied, the heading is wrapped with `0x3fff`, then shifted left two bits before compass sprite `4290` is rendered.

**Relationships / call flow**
- vanilla/detached camera heading -> optional Construction RTS yaw substitution -> client rotation offset -> 14-bit wrap -> sprite-domain shift -> compass sprite `4290` draw through component mask.

**Runtime evidence**
- Construction RTS runtime testing confirmed the compass rotates with the RTS camera after the handedness correction and remains aligned with the minimap raster.

**Static evidence**
- Source directly selects detached-vs-normal heading, applies `ConstructionBuildCamera.getRtsMinimapYawUnits()`, masks with `0x3fff`, shifts left two bits and renders sprite `4290` through `Class121.aClass161_1478.method2605(...)`.

**Matrix3 usage / ownership notes**
- Compass presentation should continue to consume the same authoritative heading domain as the stock minimap.
- Construction RTS is a presentation override only; it does not own general compass rendering or camera authority outside RTS mode.

**Do not assume**
- `Class107` is not established as a compass-only class; it also contains unrelated decompiled utility behavior.
- `i_12_` is semantically the compass heading inside this method, but no source rename is approved in this patch.

**Related entries**
- Stock world minimap renderer — `Class464.method5484(...)`
- `ConstructionBuildCamera`

## Input / mouse / keyboard

### Matrix3 held-key state — Class549_Sub1 / Class108.aClass549_1426

**Subsystem:** Input / mouse / keyboard  
**Evidence:** verified-static  
**Tags:** Class549_Sub1, Class108, aClass549_1426, method6514, keyDown, Space, key 83, KeyEvent, ConstructionBuildCamera, MarioJumpController

**Exact symbols / IDs**
- Keyboard implementation: `Class549_Sub1`
- Active keyboard owner reference: `Class108.aClass549_1426`
- Held-state query used by Matrix3 extensions: `Class549.method6514(int, byte)` / `Class549_Sub1.method6514(int, byte)`
- Java Space code: `KeyEvent.VK_SPACE == 32`
- Matrix3 internal Space code: `83`
- Mapping table: `Class549_Sub1.anIntArray8901`

**Established responsibility**
- `Class549_Sub1` receives AWT key press/release events, normalizes Java key codes through `anIntArray8901`, queues them, and maintains a 112-entry held-key state array.
- Java Space index `32` maps to Matrix3 internal key `83`.
- Existing Matrix3 client extensions poll the active keyboard owner through `Class108.aClass549_1426.method6514(...)` rather than installing a second keyboard owner.

**Relationships / call flow**
- AWT `KeyEvent` -> `Class549_Sub1.method8084(...)` -> normalized internal key -> queued event -> held-state array -> `method6514(...)` query -> client extension such as `ConstructionBuildCamera` / `MarioJumpController`.

**Static evidence**
- `Class549_Sub1.anIntArray8901[32]` is `83`.
- `keyPressed` and `keyReleased` both route through `method8084(...)`.
- `method6514(...)` returns the held state for the requested internal key when it is within `0..111`.
- `ConstructionBuildCamera.keyDown(...)` already calls `Class108.aClass549_1426.method6514(...)`, establishing this as a project-used held-key seam.

**Matrix3 usage / ownership notes**
- Prefer polling this existing owner for held gameplay/developer keys rather than adding another AWT listener when event consumption is not required.
- Input polling alone does not grant gameplay/server authority.

**Do not assume**
- Internal key numbers are Java/AWT key codes; they are Matrix3's normalized domain.
- The Mario POC currently has no explicit mode gate, so Space can request a visual jump even while another UI/text context is active; that is a known POC limitation, not an input-owner mapping issue.

**Related entries**
- Live world viewport update seam — `Class343.method4302(...)`
- Local-player scene transform — `Class611` / `Class456`

## Menu / actions / context options

_No mappings recorded yet._

## Interfaces / components / scripts

### NIS minigame HUD host — 1477:368

**Subsystem:** Interfaces / components / scripts  
**Evidence:** verified-static  
**Tags:** 1477, 368, MINIGAME_HUD_COMPONENT_ID, InterfaceManager, InterfaceDefinitions, Class348, aClass73Array917, client.aClass676_8760, RTS Control

**Exact symbols / IDs**
- Server class: `com.rs.game.player.InterfaceManager`
- Client classes: `InterfaceDefinitions`, `Class348`, `ConstructionRtsControlOverlay`
- Root/interface slot: `1477:368`
- Child array: `InterfaceDefinitions.aClass73Array917`
- Mounted-subinterface table: `client.aClass676_8760`

**Established responsibility**
- Server `InterfaceManager.MINIGAME_HUD_COMPONENT_ID` is component `368` on root `1477`.
- `InterfaceManager.sendMinigameInterface(int)` mounts player-facing interfaces into that slot.
- `Class348.method4343(...)` recursively renders `InterfaceDefinitions.aClass73Array917` children and separately resolves mounted subinterfaces from `client.aClass676_8760` keyed by component UID.

**Relationships / call flow**
- Root `1477` component `368` -> native component tree children (`aClass73Array917`) -> `Class348.method4343(...)` render recursion.
- Mounted minigame interface ownership is separate: component UID -> `client.aClass676_8760` -> mounted subinterface render.

**Static evidence**
- Server constants and `sendMinigameInterface(...)` establish `1477:368` as the minigame-HUD slot.
- Client renderer source establishes child recursion plus separate mounted-subinterface dispatch.

**Matrix3 usage / ownership notes**
- Player-facing RTS Control may use `1477:368` only while the slot is structurally available and has no mounted subinterface.
- If another real minigame HUD is mounted, RTS Control must yield instead of drawing on top of it or falling back to an unrelated root component.
- Preserve/restore pre-existing `aClass73Array917` children exactly when temporary RTS children are attached.

**Do not assume**
- This static mapping does not prove every NIS layout gives `1477:368` enough dimensions for the RTS panel; runtime acceptance is still required.
- This mapping does not make client-side RTS presentation authoritative for worker gameplay.

## Player rendering

### Local-player scene transform — Class611 / Player / Class456

**Subsystem:** Player rendering, pathing / movement presentation  
**Evidence:** verified-static  
**Tags:** Class611, Player, Class456, method5394, method5395, Class240, aFloat2653, aFloat2656, aFloat2657, local player, player transform, scene Y, Mario jump

**Exact symbols / IDs**
- Local player: `Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976`
- Concrete type: `Player`
- Transform read: `Class456.method5394().aClass240_2647`
- Translation write: `Class456.method5395(float, float, float)`
- Translation vector type: `Class240`
- One movement interpolation caller: `Class611.method7272(Entity, short)`

**Established responsibility**
- The local player is stored in `Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976` and inherits the generic scene transform implementation from `Class456`.
- `method5394()` exposes the current transformed `Class238`, whose `aClass240_2647` carries scene translation components.
- `method5395(x,y,z)` writes the object's translation through `Class240.method3268(...)` and invalidates dependent cached transforms.
- Player model rendering consumes the same transform lineage; `Player` rendering calls `method5394()` while constructing/submitting model transforms.
- `Class611.method7272(...)` proves at least one normal entity movement interpolation path updates X/Z while explicitly preserving the current Y value.

**Relationships / call flow**
- local player pointer -> `Player` -> `Entity` -> `Class456` scene transform -> `Class240` translation -> renderer/model transform.
- movement interpolation -> read current `Class240` -> calculate X/Z -> `method5395(newX, currentY, newZ)`.

**Static evidence**
- `Class611` declares the local-player field as `Player`.
- `Class456.method5395(...)` directly writes `aClass238_5189.aClass240_2647.method3268(...)` and calls transform invalidation.
- `Class611.method7272(...)` reads `method5394().aClass240_2647` and passes the existing `aFloat2656` as the middle/Y argument to `method5395(...)`.
- `Class343.method4302(...)` uses the local player's X/Z transform for world bounds/culling while stock camera height math uses terrain height minus camera height, supporting the established smaller-Y-is-higher convention used by the jump POC.

**Matrix3 usage / ownership notes**
- The Mario Jump POC changes only local presentation Y through this existing transform API; normal X/Z, plane, clipping/pathing and server authority remain owned by Matrix3.
- Future alternate-controller work should preserve this ownership boundary unless runtime evidence requires a different seam.

**Do not assume**
- One Y-preserving interpolation path does not prove every movement/grounding path preserves a custom Y offset.
- `aFloat2656` is established as the vertical translation component in the relevant transform/camera usage, but broad semantic renaming of `Class240` fields has not been approved.
- The current Mario jump is not `VERIFIED` until runtime confirms visible lift, landing and movement coexistence.

**Related entries**
- Live world viewport update seam — `Class343.method4302(...)`
- Matrix3 held-key state — `Class549_Sub1`

## NPC rendering

_No mappings recorded yet._

## Object rendering

_No mappings recorded yet._

## Ground items

_No mappings recorded yet._

## Projectiles

_No mappings recorded yet._

## GFX / spot animations

_No mappings recorded yet._

## Animation system

_No mappings recorded yet._

## Models

_No mappings recorded yet._

## Textures / materials

_No mappings recorded yet._

## Sprites

_No mappings recorded yet._

## Particles

_No mappings recorded yet._

## Lighting / shadows

_No mappings recorded yet._

## World map

_No mappings recorded yet._

## Pathing / movement presentation

_No mappings recorded yet._

## Audio

_No mappings recorded yet._

## Networking / packets

_No mappings recorded yet._

## Cache / definitions

_No mappings recorded yet._

## Client lifecycle / game state

_No mappings recorded yet._

## Miscellaneous verified mappings

_No mappings recorded yet._

---

# Research intake checklist

When a dedicated research chat is populating this map:

1. Pick one bounded subsystem.
2. Read root `AGENTS.md`, `agents/client-engine.md`, and `agents/reverse-engineering.md`.
3. Search this map before tracing source.
4. Inspect only the smallest source/reference set needed for that subsystem.
5. Record exact symbols/IDs and evidence classification.
6. Stop tracing once the mapping can be classified accurately.
7. Update an existing entry instead of duplicating it.
8. Leave unresolved semantics as `HYPOTHESIS` or `UNKNOWN` with the next useful verification target.
9. Do not modify runtime behavior unless separately requested and AAA-approved.


## Mario native combat socket / Class261 matrix boundary

**Evidence:** verified-static; focused transform/native checks pass, in-game acceptance pending.
**Tags:** MarioWeaponCombat, Sm64BridgeSession, MarioVisualRenderer, Class261, method3572, method3582, right hand, native socket, custom animation

- Pinned libsm64 `geo_process_animated_part` owns temporary local XYZ animation channels. The Matrix patch adds offsets before matrix construction and captures native hand matrix after parent composition. Physics/action ownership stays native.
- `MarioVisualRenderer.render` calls weapon rendering with the exact immutable body GeometryFrame. Socket origin is rebased from native Mario XYZ using the existing body Y mirror and mesh scale.
- `Class261.method3572` accepts nine values in column order: arguments 1/2/3 contribute X input to output X/Y/Z; 4/5/6 contribute Y input; 7/8/9 contribute Z input. `method3582` directly establishes point multiplication; do not pass a row-major array unchanged.
- Weapon basis uses `S * nativeHandBasis * calibration * S`, S=(1,-1,1), retaining native scale; translation uses `S*(socketPosition-nativeMarioPosition)*modelScale + MatrixPlayerPosition`.
- Detailed source pins, joint identity guards, protocol v3 and uncertainty: `docs/n64/CUSTOM_COMBAT.md`.
