# Matrix3 Dev Mode

## Goal

Turn Matrix3 Dev Mode into one coherent live-world creation environment: select something in the running game, inspect it, manipulate/test it safely, route into specialist editors/assets, and save only through verified Matrix3 authorities.

`DEV_MODE.md` is the detailed product/design specification. This `PROJECT.md` is the authoritative execution roadmap, status, checklist, and Resume Here state.

## Canonical Main-Goal Status

This table is the authoritative user-facing milestone table across chats.

| Main-goal area | Status |
| --- | --- |
| Spawn + Tile foundation | ✅ Complete |
| Interaction / target selection | ✅ Complete |
| Move / Rotate / Duplicate / Paint | 🔵 In Progress |
| Contextual Inspector + NPC editor | 🟡 Foundation |
| World / Tile creation | 🟡 Foundation |
| Assets / relationships | ❌ Not started |
| History / undo / saved-vs-live | ❌ Not started |

## Scope

### In scope

- Owner-only live-world Dev targeting for tiles, NPCs, objects, and later items.
- Runtime spawn/manipulation workflows with strong ownership safeguards.
- Contextual Inspector/editor routing.
- Tile/world-building tools where Matrix3 ownership is verified.
- Contextual links into existing specialist tools instead of duplicating them.
- Clear live-vs-saved state and reversible/history tooling where safe.

### Out of scope

- Replacing Matrix3 world/map/entity ownership.
- Guessing persistent map, clipping, definition, or spawn save paths.
- A second game engine or generic raw-cache editor.
- Boss-specific systems that belong in BossLabs.

## Architecture / ownership

- Matrix3 remains authoritative for scene picking, NPC/object runtime ownership, map objects, definitions, clipping, spawning, and persistence.
- Client Dev Mode mirrors already-resolved Matrix3 targets through `DevModeBridge`.
- Owner-only server mutations route through the existing Client Console command bridge and narrow Dev runtime helpers.
- Dev-created runtime entities may be tracked explicitly so destructive actions can be limited to proven Dev ownership.
- Detailed UX/design direction remains in `docs/dev-mode/DEV_MODE.md`.

## Verified foundation

### VERIFIED

- Dev Mode session toggle and owner/Admin+ gating.
- Tile right-click Dev Spawn/Tile Editor flow.
- Searchable NPC/object/item Spawn Browser and runtime placement.
- Item thumbnails through the existing Item Browser render path.
- NPC/object right-click Inspect/Edit/Copy ID/Copy Tile routing.
- Shared Inspector retargeting to live NPC/object targets.
- User runtime-confirmed the NPC/object Inspector targeting flow on 2026-09-07.

### verified-static

- Client NPC runtime index is the same index Matrix3 sends for normal NPC interaction; server resolves it with `World.getNPCs().get(npcIndex)`.
- Object definition ID is carried by Matrix3's existing object menu UID.
- `World.spawnObject`, `World.removeObject`, `World.isSpawnedObject`, and `World.getObjectWithId` expose the narrow runtime object path needed for guarded Dev placement mutation.
- `NPC.finish()`, `NPC.setNextWorldTile(...)`, and `NPC.setRespawnTile(...)` provide runtime NPC removal/movement primitives.

## Unknown / research needed

### HYPOTHESIS

- Safe reusable off-screen NPC/object thumbnail rendering remains unverified.
- Persistent NPC/object placement saving remains unverified.
- Tile clipping/terrain mutation ownership remains unverified.

### UNKNOWN

- Exact persistent world-map authoring/save workflow that should eventually back permanent object Move/Rotate/Delete.

## Dependencies

- Client: `DevModeBridge`, `DevInspectorWindow`, tile menu hook, `ClientConsoleBridge`.
- Server: `ItemBrowserCommandBridge`, Matrix3 `World`/`NPC`/`WorldObject` runtime APIs.
- Runtime: Admin+ owner session with Dev Mode enabled.

## Development plan

### Phase 1 - Live Interaction and World Manipulation

**Purpose:** Establish one shared target model and safe editor-style runtime manipulation.

**Status:** ACTIVE

**Exit conditions:**

- Shared target selection is runtime-verified.
- Move/Rotate/Duplicate/Delete-dev-spawn is runtime-verified.
- Continuous/Paint placement reaches a usable runtime checkpoint.

#### Bundle 1.1 - NPC/Object Target + Inspector Foundation

**Status:** DONE

**Checklist / patches:**

- [x] Add owner-only NPC/object Dev menu routes.
- [x] Resolve shared NPC/object target context from Matrix3-owned menu data.
- [x] Add one reusable contextual Inspector.
- [x] Runtime-verify target name/ID/tile and retargeting.

#### Bundle 1.2 - World Manipulation

**Purpose:** Make selected entities manipulable without bypassing Matrix3 ownership.

**Status:** ACTIVE

**Checklist / patches:**

- [ ] Move selected NPC to a chosen destination tile.
- [ ] Move Dev-owned runtime object to a chosen destination tile.
- [ ] Rotate Dev-owned runtime object in place.
- [ ] Duplicate selected NPC/object into a new Dev-owned placement.
- [ ] Delete only proven Dev-owned NPC/object placements.
- [ ] Add right-click and Inspector manipulation controls using the shared target.
- [ ] Track Dev-created NPC/object instances server-side for ownership-safe destructive actions.
- [ ] Consolidate runtime tests and regression checks.

**Runtime tests:**

- One client/server launch covering NPC move/duplicate/delete, object duplicate/move/rotate/delete, permanent-object delete protection, stacked NPC targeting, Inspector retargeting, and normal interaction regressions.

#### Bundle 1.3 - Continuous / Paint Placement

**Status:** READY

**Checklist / patches:**

- [ ] Spawn Once / Continuous / Paint modes.
- [ ] Last-used placement target.
- [ ] Escape/cancel placement behavior.
- [ ] Object rotation placement options.

### Phase 2 - Contextual Editors

**Status:** PLANNED

**Purpose:** Expand the shared Inspector into verified live NPC/object/item editing surfaces.

### Phase 3 - World / Tile Creation

**Status:** PLANNED

**Purpose:** Build scene hierarchy, clipping visualization, and verified gameplay-zone/world-building tools.

### Phase 4 - Contextual Assets and Relationships

**Status:** PLANNED

**Purpose:** Navigate from live targets to exact models/animations/graphics/definitions and related specialist tools.

### Phase 5 - Change Management

**Status:** PLANNED

**Purpose:** Add live-vs-saved comparison, history, undo/redo where safe, and lightweight sessions/workspaces.

## Current execution state

- Phase: Phase 1 - Live Interaction and World Manipulation
- Phase status: ACTIVE
- Bundle: Bundle 1.2 - World Manipulation
- Bundle status: ACTIVE
- Approval state: AAA approved 2026-09-07
- Current checklist item: Implement the approved World Manipulation bundle.
- Current objective: Runtime-safe Move/Rotate/Duplicate/Delete using the shared NPC/object target foundation.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| NPC/Object target + Inspector foundation | 1 | 1.1 | DONE | Runtime-confirmed by user. |
| Dev runtime ownership tracking | 1 | 1.2 | ACTIVE | Track exact Dev-created NPC/Object instances server-side. |
| NPC Move | 1 | 1.2 | READY | Exact client NPC index is verified-static. |
| Object Move | 1 | 1.2 | READY | Destructive source removal restricted to Dev-owned spawned object. |
| Object Rotate | 1 | 1.2 | READY | Dev-owned object only. |
| NPC/Object Duplicate | 1 | 1.2 | READY | Source remains untouched; new copy becomes Dev-owned. |
| Delete Development Spawn | 1 | 1.2 | READY | Block ordinary/persistent objects and non-Dev NPCs. |
| World Manipulation runtime gate | 1 | 1.2 | READY | Consolidated one-session test after implementation. |
| Continuous/Paint placement | 1 | 1.3 | READY | Next bundle after manipulation runtime gate. |

## Decisions / new ideas

### Decision log

- 2026-09-07: Bundle compatible world-manipulation work instead of patching Move/Rotate/Duplicate/Delete separately.
- 2026-09-07: Destructive object operations must require explicit server-side proof that the object was created by Dev Mode.
- 2026-09-07: Duplicate may use an ordinary selected target because it does not alter the source; the created copy is tracked as Dev-owned.
- 2026-09-07: Permanent map editing remains a later verified-authority task rather than being simulated by deleting cache/map objects.

## Testing

### Quick/high-value checks

1. Spawn a Dev NPC/object, manipulate each, then delete each.
2. Duplicate one ordinary NPC and one ordinary map object; confirm the originals remain untouched.
3. Attempt destructive object actions on an ordinary map object and confirm they are blocked.

### Deeper checks

1. Stack identical NPCs and verify the selected NPC runtime index is the one moved/duplicated.
2. Retarget the Inspector repeatedly while placement is armed/cancelled.
3. Cross region/chunk boundaries with a Dev object move.

### Smoke/regression checks

- Normal NPC/object right-click actions remain intact.
- Normal Walk Here remains intact.
- Existing Dev Spawn/Tile Editor and Item Browser remain functional.
- Server restart removes runtime Dev placements and does not persist them as map/source data.

## Carryover / blockers

### CARRYOVER

- None currently.

### BLOCKED

- Permanent map-object Move/Rotate/Delete: blocked until the authoritative persistent map/save path is verified. Runtime Dev-owned object manipulation can proceed independently.

## Resume Here

**Last completed:**

- Bundle 1.1 shared NPC/object target + Inspector foundation; user runtime-confirmed it works.

**Current phase:**

- Phase 1 - Live Interaction and World Manipulation.

**Active bundle:**

- Bundle 1.2 - World Manipulation.

**Next checklist item:**

- Implement server-side Dev placement ownership tracking and manipulation commands, then wire shared client placement/actions.

**Current state / next action:**

- AAA approved. Implementation path is established; patch the known client/server Dev Mode files without rescanning adjacent systems.

**Files/systems already inspected:**

- `Client/src/main/java/game/DevModeBridge.java`
- `Client/src/main/java/game/Class592.java`
- `Client/src/main/java/game/console/DevInspectorWindow.java`
- `Client/src/main/java/game/console/DevSpawnBrowserWindow.java`
- `Server/src/main/java/com/rs/game/player/content/commands/ItemBrowserCommandBridge.java`
- `Server/src/main/java/com/rs/game/World.java`
- `Server/src/main/java/com/rs/game/WorldObject.java`
- `Server/src/main/java/com/rs/game/npc/NPC.java`
- `Server/src/main/java/com/rs/net/decoders/handlers/NPCHandler.java`

**Do not re-scan without new evidence:**

- Client NPC/object target encoding/menu dispatch.
- Server NPC interaction index mapping.
- Basic World object spawn/remove APIs.

**Pending runtime verification:**

- Entire Bundle 1.2 after implementation.

**Blockers:**

- Permanent map-object mutation only; does not block runtime Dev-owned manipulation.

**Important remaining uncertainty:**

- Persistent map/save authority for eventually committing world-editor changes.

## Next recommended work

Complete Bundle 1.2 World Manipulation, then run its consolidated runtime gate before entering Continuous/Paint placement.
