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

_No mappings recorded yet._

## Scene / renderer

_No mappings recorded yet._

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

_No mappings recorded yet._

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

_No mappings recorded yet._

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
