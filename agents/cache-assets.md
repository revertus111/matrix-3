# Matrix3 Cache and Asset Rules

Read root `AGENTS.md` first. This file applies to cache data, revision-830 imports, models, animations, sprites, textures, definitions, maps, GFX/particles, and asset tooling/runtime loading.

## Authority

- Matrix3 remains the game/engine authority.
- Revision-830 cache/data is data authority where applicable; it does not automatically replace Matrix3 runtime ownership.
- Do not port an older 718 cache subsystem merely because it already exists there.
- Keep cache/data transformation separate from engine/runtime ownership.

## Asset pipeline

Prefer one traceable pipeline:

`discover -> classify -> import -> validate -> preview -> edit/transform -> save definition/data -> runtime load -> regression check`

- Preserve source IDs and provenance when practical so imported assets can be traced back to their origin.
- Validate definitions and model/animation relationships before treating an import as correct.
- Separate raw imported data from Matrix3-specific overrides or repaired definitions.
- Prefer reusable import/validation paths over one-off asset fixes when repeated content exposes the same issue.
- Do not let developer tooling become the runtime owner of cache behavior.
- When an asset mapping is uncertain, classify it with the root evidence labels rather than guessing semantics.

## Cross-domain work

- If the task requires unfamiliar decompiled cache/decoder behavior, also read `agents/reverse-engineering.md`.
- If it changes runtime client loading/rendering, also read `agents/client-engine.md`.
- If it changes ForgeLabs/editor workflow, also read `agents/tools-editors.md`.
