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
- Owner-only server mutations route through the existing Client Console command bridge and `DevModeRuntimeManager`.
- `DevModeRuntimeManager` tracks exact Dev-created runtime NPC/Object instances so destructive actions can require proven Dev ownership.
- `DevSpawnPlacement` owns only client-side placement-session state; all actual NPC/object/item placement continues through the existing validated server `devspawn` bridge.
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
- `World.spawnObject`, `World.removeObject`, `World.isSpawnedObject`, and `World.getObjectWithId` provide the guarded runtime object mutation path.
- `NPC.finish()`, `NPC.resetWalkSteps()`, `NPC.setNextWorldTile(...)`, and `NPC.setRespawnTile(...)` provide the runtime NPC removal/movement path.
- Bundle 1.2 client/server manipulation implementation is Java-8-compatible by static inspection and preserves the existing Matrix3 interaction owners.
- Bundle 1.3 reuses the existing server `devspawn` command only; no new world/server ownership was introduced.
- Paint placement observes Matrix3's already-verified normal tile action 23 and returns `false` from the Dev hook so the normal Matrix3 action remains authoritative after the Dev spawn is queued.
- The Escape cancellation listener is non-consuming AWT observation; explicit browser/tile cancellation remains available if the runtime key path behaves differently than expected.

## Unknown / research needed

### HYPOTHESIS

- Safe reusable off-screen NPC/object thumbnail rendering remains unverified.
- Persistent NPC/object placement saving remains unverified.
- Tile clipping/terrain mutation ownership remains unverified.

### UNKNOWN

- Exact persistent world-map authoring/save workflow that should eventually back permanent object Move/Rotate/Delete.

## Dependencies

- Client: `DevModeBridge`, `DevSpawnPlacement`, `DevInspectorWindow`, `DevSpawnBrowserWindow`, tile menu hook, `ClientConsoleBridge`.
- Server: `ItemBrowserCommandBridge`, `DevModeRuntimeManager`, Matrix3 `World`/`NPC`/`WorldObject` runtime APIs.
- Runtime: Admin+ owner session with Dev Mode enabled.

## Development plan

### Phase 1 - Live Interaction and World Manipulation

**Purpose:** Establish one shared target model and safe editor-style runtime manipulation.

**Status:** NEEDS TEST

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

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Implement Move selected NPC to a chosen destination tile.
- [x] Implement Move Dev-owned runtime object to a chosen destination tile.
- [x] Implement Rotate Dev-owned runtime object in place.
- [x] Implement Duplicate selected NPC/object into a new Dev-owned placement.
- [x] Implement Delete only for proven Dev-owned NPC/object placements.
- [x] Add right-click and Inspector manipulation controls using the shared target.
- [x] Track Dev-created NPC/object instances server-side for ownership-safe destructive actions.
- [x] Consolidate runtime tests and regression checks in `testlist.txt`.
- [ ] Runtime-verify the complete bundle in one client/server session. **Deferred by user on 2026-09-08 due unavailable PC/runtime time.**

**Runtime tests:**

- One client/server launch covering NPC move/duplicate/delete, object duplicate/move/rotate/delete, permanent-object delete/move/rotate protection, stacked NPC targeting, placement cancel/re-arm, and normal interaction regressions.

#### Bundle 1.3 - Continuous / Paint Placement

**Purpose:** Turn Dev Spawn from one-shot placement into a reusable world-building workflow without changing server world authority.

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Add Spawn Once / Continuous / Paint modes.
- [x] Add session last-used placement target and contextual Place Last tile action.
- [x] Add explicit Cancel plus Escape cancellation for armed placement.
- [x] Add object Fixed / Cycle / Random repeated-placement rotation behavior.
- [x] Make Paint place from normal tile action 23 while allowing normal Walk Here to continue.
- [x] Keep Move/Duplicate and Spawn placement modes mutually exclusive so stale modes cannot overlap.
- [x] Consolidate Bundle 1.3 tests into the deferred runtime queue.
- [ ] Runtime-verify Bundle 1.3. **Deferred by user on 2026-09-08 due unavailable PC/runtime time.**

**Runtime tests:**

- Once parity with the existing spawn workflow.
- Continuous repeated placement for NPC/object/item.
- Paint left-click placement while normal Walk Here remains active.
- Fixed/Cycle/Random object rotation behavior.
- Place Last, explicit cancel, Escape cancel, mode replacement, and Dev Mode disable/re-enable cleanup.

### Phase 2 - Contextual Editors

**Status:** PLANNED

**Purpose:** Expand the shared Inspector into verified live NPC/object/item editing surfaces.

**Safe-independent note:** the user explicitly requested development continue while Phase 1 runtime testing is deferred. Phase 2 work may be entered before the Phase 1 runtime gate only when the chosen slice depends solely on already-VERIFIED foundation (for example read-only/runtime Inspector data), does not rely on unverified manipulation/paint correctness, and Phase 1 remains `NEEDS TEST` rather than being treated as complete.

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
- Phase status: NEEDS TEST
- Bundle: Bundle 1.3 - Continuous / Paint Placement
- Bundle status: NEEDS TEST
- Approval state: SAP AAA approved 2026-09-08; implementation complete statically.
- Current checklist item: Deferred combined Bundle 1.2 + 1.3 runtime gate.
- Current objective: Preserve all pending runtime checks in one queue while allowing safe independent development to continue when the user says `next`.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| NPC/Object target + Inspector foundation | 1 | 1.1 | DONE | Runtime-confirmed by user. |
| Dev runtime ownership tracking | 1 | 1.2 | NEEDS TEST | Exact Dev-created NPC/Object instances tracked server-side; stale placements pruned. |
| NPC Move | 1 | 1.2 | NEEDS TEST | Exact client/server NPC runtime index; ordinary NPC move remains runtime-only. |
| Object Move | 1 | 1.2 | NEEDS TEST | Server refuses source removal unless object is proven Dev-owned. |
| Object Rotate | 1 | 1.2 | NEEDS TEST | Dev-owned runtime object only; left/right wraps 0-3. |
| NPC/Object Duplicate | 1 | 1.2 | NEEDS TEST | Ordinary source may be copied; created copy is Dev-owned. |
| Delete Development Spawn | 1 | 1.2 | NEEDS TEST | Ordinary map objects and non-Dev NPCs are blocked. |
| Once / Continuous / Paint modes | 1 | 1.3 | NEEDS TEST | Existing `devspawn` authority reused for each placement. |
| Last-used placement target | 1 | 1.3 | NEEDS TEST | Session-only Place Last tile action. |
| Placement cancellation | 1 | 1.3 | NEEDS TEST | Browser/tile explicit cancel plus non-consuming AWT Escape observation. |
| Object placement rotation | 1 | 1.3 | NEEDS TEST | Fixed, Cycle, Random 0-3. |
| Phase 1 combined runtime gate | 1 | 1.2 + 1.3 | NEEDS TEST | Intentionally deferred; accumulated queue is in `docs/dev-mode/testlist.txt`. |

## Decisions / new ideas

### Decision log

- 2026-09-07: Bundle compatible world-manipulation work instead of patching Move/Rotate/Duplicate/Delete separately.
- 2026-09-07: Destructive object operations require explicit server-side proof that the object was created by Dev Mode.
- 2026-09-07: Duplicate may use an ordinary selected target because it does not alter the source; the created copy is tracked as Dev-owned.
- 2026-09-07: NPC Move may target an exact ordinary live NPC but remains runtime-only; only Dev-owned NPCs receive the moved runtime respawn tile.
- 2026-09-07: Permanent map editing remains a later verified-authority task rather than being simulated by deleting cache/map objects.
- 2026-09-07: Move/Duplicate use the game world as the placement surface: arm the action, then right-click the destination tile.
- 2026-09-08: User explicitly deferred runtime testing because PC/runtime time is unavailable and requested safe independent development continue; this does not satisfy or bypass the Phase 1 verification gate.
- 2026-09-08: Continuous uses repeated explicit tile Dev placement; Paint uses normal left-click tile action 23 and does not consume Matrix3 Walk Here.
- 2026-09-08: Starting Move/Duplicate cancels active spawn placement and starting Continuous/Paint cancels active Move/Duplicate, giving Dev Mode one active placement tool at a time.

## Testing

The authoritative accumulated runtime queue is `docs/dev-mode/testlist.txt`. The top `DEFERRED RUNTIME QUEUE` section is the short test session to run when PC time becomes available; detailed checks remain below it for failures/deeper validation.

### Quick/high-value checks

1. Clean/build Client and Server under Eclipse/Java 8.
2. Run the Bundle 1.2 manipulation high-value checks.
3. Run the Bundle 1.3 Once/Continuous/Paint high-value checks.
4. Run the short regression/persistence checks.

### Smoke/regression checks

- Normal NPC/object right-click actions remain intact.
- Normal Walk Here remains intact, including during Paint.
- Existing Dev Spawn/Tile Editor/Inspector and Item Browser remain functional.
- Server restart removes runtime Dev placements and does not persist them as map/source data.

## Carryover / blockers

### CARRYOVER

- Task: Phase 1 runtime verification for Bundles 1.2 and 1.3.
- Phase/bundle: Phase 1 / Bundles 1.2 + 1.3.
- Current state: implementation and static inspection complete; runtime not currently available to user.
- Remaining work: run the accumulated deferred queue in `docs/dev-mode/testlist.txt`, fix any runtime regressions, then close the Phase 1 gate.
- Likely files/systems if a failure appears: the already-known Dev Mode bridge/placement/Inspector/server runtime manager paths; do not rescan unrelated systems first.
- Next action: test when user has PC/runtime time. This carryover does not block safe independent read-only/contextual editor work explicitly authorized by the user.

### BLOCKED

- Permanent map-object Move/Rotate/Delete: blocked until the authoritative persistent map/save path is verified. Runtime Dev-owned object manipulation can proceed independently.

## Resume Here

**Last completed:**

- Bundle 1.3 Continuous / Paint Placement implementation and static verification. Once/Continuous/Paint state, last-used target, object rotation behaviors, contextual tile actions, Paint-on-action-23, explicit/Escape cancellation, documentation, and deferred tests are committed.

**Current phase:**

- Phase 1 - Live Interaction and World Manipulation (`NEEDS TEST`).

**Active bundle:**

- No active implementation bundle. Bundles 1.2 and 1.3 are both `NEEDS TEST` with runtime intentionally deferred.

**Next checklist item:**

- Preferred gate: run the combined deferred Phase 1 runtime queue when PC time is available.
- If the user says `next` before testing: choose only a safe independent Phase 2 contextual-editor slice that depends on the already-VERIFIED target/Inspector foundation, and keep Phase 1 `NEEDS TEST`.

**Current state / next action:**

- Do not ask the user to test immediately unless they indicate runtime time is available. Preserve the queue and continue safe independent work when explicitly requested.

**Files/systems already inspected:**

- `Client/src/main/java/game/DevModeBridge.java`
- `Client/src/main/java/game/DevSpawnPlacement.java`
- `Client/src/main/java/game/Class592.java`
- `Client/src/main/java/game/console/DevInspectorWindow.java`
- `Client/src/main/java/game/console/DevSpawnBrowserWindow.java`
- `Client/src/main/java/game/console/ConsoleTheme.java`
- `Server/src/main/java/com/rs/game/player/content/commands/ItemBrowserCommandBridge.java`
- `Server/src/main/java/com/rs/game/player/content/commands/DevModeRuntimeManager.java`
- `Server/src/main/java/com/rs/game/World.java`
- `Server/src/main/java/com/rs/game/WorldObject.java`
- `Server/src/main/java/com/rs/game/npc/NPC.java`
- `Server/src/main/java/com/rs/game/Entity.java`
- `Server/src/main/java/com/rs/net/decoders/handlers/NPCHandler.java`

**Do not re-scan without new evidence:**

- Client NPC/object target encoding/menu dispatch.
- Server NPC interaction index mapping.
- Matrix3 runtime NPC movement/removal primitives.
- Basic World object spawn/remove/lookup APIs.
- Existing owner-only `devspawn` server validation/placement path.
- Normal tile action 23 ownership/coordinate path.

**Pending runtime verification:**

- Bundle 1.2 World Manipulation.
- Bundle 1.3 Continuous / Paint Placement.
- Combined Phase 1 gate.

**Blockers:**

- Permanent map-object mutation only; does not block runtime Dev-owned manipulation or safe independent contextual-editor work.

**Important remaining uncertainty:**

- Persistent map/save authority for eventually committing world-editor changes.
- Runtime behavior of the AWT Escape observation path until the deferred test session.

## Next recommended work

When runtime time is available, run the short deferred queue. If testing remains unavailable and the user explicitly says `next`, the next safe independent implementation target is the Phase 2 contextual NPC Inspector/read-only runtime-data slice, while Phase 1 remains `NEEDS TEST`.
