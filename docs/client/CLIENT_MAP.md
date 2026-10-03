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
- `verified-static` — directly established from source/data but not runtime-confirmed.
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

_No mappings recorded yet._

## Input / mouse / keyboard

_No mappings recorded yet._

## Menu / actions / context options

_No mappings recorded yet._

## Interfaces / components / scripts

_No mappings recorded yet._

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
