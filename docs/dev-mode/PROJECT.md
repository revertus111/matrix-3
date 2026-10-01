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
| Assets / relationships | 🟡 Foundation |
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

- Client: `DevModeBridge`, `DevSpawnPlacement`, `DevObjectLibrary`, Live Inspect, Live Model Editor, tile menu hook, `ClientConsoleBridge`.
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


#### Bundle 1.4 - Real Object Placement Preview

**Purpose:** Give Dev Spawn a Build-Palette-style object placement cursor without creating a second world/object owner.

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Add Live Inspect object hotkey `O` as the primary Live Place entry; the standalone Spawn Browser is removed from normal Dev Mode entry points.
- [x] Render the selected object through Matrix3's normal ObjectDefinitions model factory at the currently hovered world tile.
- [x] Keep the preview client-only: no scene registration, clipping, persistence or server object exists before confirmation.
- [x] Render the object with its normal material/lighting; do not apply Construction ghost tint or face alpha.
- [x] Left-click the hovered world tile to place through the existing owner-validated `devspawn object` path and consume Walk Here while the tool owns placement.
- [x] Keep placement armed after confirmation for rapid repeated object spawning.
- [x] 1 / 2 step the active raw object definition ID backward/forward; each ID change re-resolves its cache-declared shape/type automatically while preserving rotation.
- [x] Escape / Cancel / Dev Mode OFF cleanly clears the placement preview.
- [x] Keep Live Place mutually exclusive with ordinary Once/Continuous/Paint and Move/Duplicate placement modes.
- [ ] Eclipse/Java 8 Client clean-build.
- [x] Resolve placement type from ObjectDefinitions' opcode-1 shape/model-group table instead of hardcoding type 10.
- [x] Remove the old Dev > Spawn... tile entry and Tile Editor Spawn Browser button so the standalone JFrame is no longer a normal workflow.
- [x] Harden Live Inspect O hotkey ownership to the active Matrix3 game window instead of requiring the KeyEvent source to be the Canvas.
- [x] Seed Live Place from the inspected object's exact world tile so the preview appears immediately when O arms it.
- [ ] Runtime verify O-entry, immediate seeded preview, auto type resolution across mixed object IDs, 1/2 cycling, repeated LMB placement and Escape cleanup.

**Ownership note:** `DevObjectPlacementPreview` owns only transient client presentation. `DevSpawnPlacement` + the existing server `devspawn` bridge remain the placement authority.

### Phase 2 - Contextual Editors

**Status:** ACTIVE

**Purpose:** Expand the shared Inspector into verified live NPC/object/item editing surfaces.

**Safe-independent note:** the user explicitly requested development continue while Phase 1 runtime testing is deferred. Phase 2 work may be entered before the Phase 1 runtime gate only when the chosen slice depends solely on already-VERIFIED foundation (for example read-only/runtime Inspector data), does not rely on unverified manipulation/paint correctness, and Phase 1 remains `NEEDS TEST` rather than being treated as complete.

#### Bundle 2.1 - Live Inspect V1

**Purpose:** Replace repetitive right-click inspection/copy actions with a fast non-destructive live hover inspector that can be locked and copied as one diagnostic block.

**Status:** RUNTIME PARTIAL / FOLLOW-UP ACTIVE

**Checklist / patches:**

- [x] Add owner-only F10 Live Inspect toggle using the existing Dev Mode input listener.
- [x] Reuse Matrix3-resolved NPC/object targets from `DevModeBridge`; do not add a second scene picker.
- [x] Add compact in-client hover HUD with type, name, definition ID, object model IDs, object animation IDs, world tile, and runtime reference.
- [x] Add F9 lock/unlock so the current target remains inspectable while the cursor moves.
- [x] Add Ctrl+C copy of the complete current inspection block for direct sharing/debugging.
- [x] While Live Inspect is active, suppress redundant Inspect/Copy ID/Copy Tile Dev menu entries while preserving Edit/Live Model/Move/Rotate/Duplicate/Delete tools.
- [x] Disabling Dev Mode also closes Live Inspect and clears transient lock/hover state.
- [x] Runtime proof: user confirmed the overlay opens, NPC hover resolves Overseer 8904 / runtime index 79, F9 lock works, and the locked block copies successfully.
- [ ] Full acceptance remains pending after the V1.1 layout/flicker correction below.

#### Bundle 2.2 - Live Inspect Polish + Target Expansion

**Purpose:** Make Live Inspect stable enough to leave on during normal development and expand the same zero-right-click workflow to ground items and tiles.

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Remove the 180 ms hover-expiry behavior that could clear a valid target after the mouse stopped.
- [x] Stop repacking/resizing the heavyweight overlay every refresh tick; bounds now change only when the canvas/window geometry actually changes.
- [x] Widen the compact card and use bounded two-column rows/tooltips so long values stay inside the overlay.
- [x] Keep Ctrl+C valid in HOVER state; locking is optional and only freezes target selection.
- [x] Add Region + region-local and Chunk + chunk-local context to both the HUD and copied block.
- [x] Add live world-tile inspection from verified Matrix3 action 23.
- [x] Add ground-item inspection from verified-static Matrix3 actions 18-22/1004 using the existing item-definition bridge for names.
- [x] Add per-pointer target priority so NPC/object > ground item > tile and the ordinary Walk Here tile cannot overwrite a more specific hovered target.
- [ ] Runtime-verify stable rendering, unlocked copy, tile targeting, ground-item targeting, and priority behavior.

**Runtime tests:**

- Hover an NPC/object without locking and press Ctrl+C; paste should report `State: HOVER`.
- Stop moving the mouse over a target for several seconds; the overlay should remain stable with no flashing.
- Verify long names/model lists stay inside the card; full values remain available through the copied block/tooltips.
- Hover empty ground and verify Tile + world/region/chunk coordinates.
- Hover a ground item and verify its item name/definition ID/tile replaces the lower-priority Tile target.
- Hover an NPC/object on a tile containing a ground item and verify the entity wins the display priority.

#### Bundle 2.3 - Live Inspect GFX + Projectile Targets

**Purpose:** Inspect transient visual entities that Matrix3 deliberately excludes from normal right-click/menu picking.

**Status:** NEEDS TEST

**Verified-static ownership:**

- `Class456_Sub1_Sub2_Sub4` is the stationary GraphicsDefinition-backed scene visual (GFX/spot animation). Its normal `method8297(...)` pick path returns false.
- `Class456_Sub1_Sub2_Sub5` is the moving GraphicsDefinition-backed projectile entity with ballistic/target tracking. Its normal `method8297(...)` pick path also returns false.
- Both render owners already build the real Matrix3 `Model` + `Class261` transform required by `Model.method1376(...)` screen hit testing.

**Checklist / patches:**

- [x] Capture live canvas pointer X/Y only while Live Inspect is enabled.
- [x] Add a bounded renderer-side model hit test through Matrix3 `Model.method1376(...)`; no custom geometry/raycast implementation.
- [x] Publish GFX/SpotAnim targets from `Class456_Sub1_Sub2_Sub4.method10600(...)`.
- [x] Publish Projectile targets from `Class456_Sub1_Sub2_Sub5.method10639(...)`.
- [x] Resolve GraphicsDefinition model ID + animation ID from the same definition used by the real renderer.
- [x] Convert current scene-model position into world tile/region/chunk context for the copied Live Inspect block.
- [x] Priority order is Projectile > GFX/SpotAnim > NPC/Object > Ground Item > Tile.
- [ ] Runtime-verify at least one stationary GFX and one moving projectile.

**Runtime tests:**

- Trigger any known stationary GFX/spot animation, place the cursor directly over its visible model, and confirm Type=`GFX / SpotAnim` with graphics/model/animation IDs.
- Fire or trigger any visible projectile and hover its model while moving; confirm Type=`Projectile` and the world tile follows its current position.
- F9-lock either transient visual and verify it stays copyable after the visual moves/expires.
- Confirm F10 OFF adds no visual hit-test behavior and normal rendering/combat/menu interaction is unchanged.

#### Bundle 2.4 - Live Inspect Relationships + Contextual Open

**Purpose:** Turn Live Inspect from a passive ID readout into the front door for existing Matrix3 dev tools without adding right-click clutter.

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Add Relationship and Open Route rows to the HUD and copied diagnostic block.
- [x] Object relationship: Object definition -> model ID(s) -> animation ID(s).
- [x] GFX/Projectile relationship: GraphicsDefinition -> model ID -> animation ID.
- [x] NPC relationship: NPC definition -> exact runtime NPC index.
- [x] Ground-item relationship: item definition identity.
- [x] Tile relationship: world tile -> region/local -> chunk/local.
- [x] Add F8 contextual-open shortcut.
- [x] F8 Object -> existing Live Model Editor.
- [x] F8 NPC -> existing Dev Inspector.
- [x] F8 Tile -> existing Tile Editor.
- [x] Ground Item and GFX/Projectile explicitly show no verified direct specialist route yet rather than guessing one.
- [ ] Runtime-verify F8 Object/NPC/Tile routing and copied relationship text.

**Deferred from Bundle 2.3:**

- GFX/Projectile hover acceptance remains `NEEDS TEST` but does not block Bundle 2.4.
- Reason: transient effects/projectiles are too short-lived for a practical manual hover gate at normal game speed.
- Resume that test after a safe client pause/slow-time/freeze developer control exists; do not mark the render mapping VERIFIED before then.

#### Bundle 2.5 - Visual Dev Time V1

**Purpose:** Make transient GFX/projectile inspection practical without freezing Matrix3 networking, input, rendering, or server simulation.

**Status:** NEEDS TEST

**Safety boundary / verified-static:**

- The full client logic tick cannot be paused safely because it owns networking/input/session work in addition to visual simulation.
- V1 controls only transient client visual simulation: stationary GraphicsDefinition-backed spot animations and moving projectiles.
- The real `client.cycles` counter continues normally. Rendering and Live Inspect continue normally.
- Projectile start/end cycles are shifted on skipped visual ticks so paused/slowed projectiles do not expire against the still-running real client clock.

**Controls:**

- `Ctrl+F8` - arm one visual simulation tick and remain paused.
- `Ctrl+F9` - pause/resume visual simulation.
- `Ctrl+F10` - cycle 1.00x -> 0.50x -> 0.25x -> 0.10x -> 1.00x.
- F10 Live Inspect shows the current Visual Time state.
- Closing Live Inspect/F10 resets Visual Time to RUN 1.00x so a hidden paused state cannot be left behind.

**Checklist / patches:**

- [x] Add one visual-time decision per real Matrix3 client cycle without gating the real client tick.
- [x] Gate the verified stationary GFX animation-advance owner.
- [x] Hold projectile motion/lifetime safely on skipped visual ticks while keeping already-started projectiles rendered.
- [x] Patch both equivalent Matrix3 projectile update aliases found in the decompiled client.
- [x] Add pause/resume, speed cycle, and one-tick step controls without taking over Matrix3's existing bare F5/F6/F7 bindings.
- [x] Surface Visual Time status in Live Inspect and Ctrl+C diagnostics.
- [x] Auto-reset Visual Time when Live Inspect closes/Dev Mode disables.
- [ ] Runtime-verify pause, 0.50x/0.25x/0.10x, one-step, projectile lifetime hold, and normal networking/input/rendering.
- [ ] Re-run deferred Bundle 2.3 GFX/Projectile hover inspection while paused/slowed.

**Explicit V1 limit:**

- This is **Visual Dev Time**, not whole-world/server time dilation.
- NPC/player movement, workers, construction AI, conveyors, server processing and multiplayer world simulation are unchanged.
- Broader time control requires separate server-authority design and is not implied by this patch.

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

- Phase: Phase 1 priority side-slice; Phase 2 Contextual Editors remains ACTIVE carryover.
- Phase status: NEEDS TEST
- Bundle: Bundle 1.4 - Real Object Placement Preview
- Bundle status: NEEDS TEST
- Approval state: AAA approved 2026-09-30; implementation complete statically.
- Current checklist item: Eclipse/Java 8 clean-build, then runtime-check one multi-result object search with real-material mouse-follow preview, 1/2 cycling, repeated left-click placement and Escape cleanup.
- Current objective: Make Dev Spawn object placement behave like an in-world build palette while retaining the existing validated Dev Spawn server authority.
- Carryover: Bundle 2.5 Visual Dev Time V1 remains NEEDS TEST; no Visual Dev Time state was discarded by this priority change.

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
| Real object placement preview | 1 | 1.4 | NEEDS TEST | Live Inspect O arms a fully rendered client-only object; 1/2 browse raw IDs with auto type, Ctrl+S saves snapshots, and the in-client Object Library supports editable labels. |
| Phase 1 combined runtime gate | 1 | 1.2 + 1.3 | NEEDS TEST | Intentionally deferred; accumulated queue is in `docs/dev-mode/testlist.txt`. |
| Live Inspect V1 | 2 | 2.1 | NEEDS TEST | Runtime partially proven: overlay/NPC hover/F9 lock/locked Ctrl+C work; final acceptance waits on V1.1 polish retest. |
| Live Inspect polish + Tile/Ground Item targets | 2 | 2.2 | NEEDS TEST | Object + Ground Item display/copy runtime-proven; tile/flicker final gate remains pending. |
| Live Inspect GFX + Projectile targets | 2 | 2.3 | NEEDS TEST | Static implementation complete; runtime hover gate deferred until pause/slow-time/freeze makes transient targets practical to inspect. |
| Live Inspect relationships + contextual open | 2 | 2.4 | NEEDS TEST | Relationship chain + F8 handoff to Object/NPC tools; Tile remains inspection-only after Tile Editor retirement. |
| Visual Dev Time V1 | 2 | 2.5 | NEEDS TEST | F5 step/F6 pause/F7 speed controls transient GFX/projectile simulation only; networking/input/rendering/server simulation remain live. |

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
- 2026-09-30: Live Inspect becomes the preferred fast read-only inspection path: F10 toggles, F9 locks the current hover target, and Ctrl+C copies one shareable diagnostic block. It reuses existing Matrix3-resolved Dev targets rather than adding another picker.
- 2026-09-30: While Live Inspect is active, redundant Inspect/Copy ID/Copy Tile right-click actions are hidden; mutation/editor routes remain available. Turning Live Inspect off restores the legacy menu actions.
- 2026-09-30: Runtime proved the first Live Inspect target/copy concept, but exposed two UX defects: long row values clipped outside the 360px card and the overlay flashed. V1.1 removes per-tick pack/resize plus the stale-hover expiry instead of masking the symptoms.
- 2026-09-30: Live Inspect target expansion remains read-only and reuses normal Matrix3 menu resolution. Tile action 23 feeds scene coordinates; ground-item actions 18-22/1004 feed item ID + tile; entity targets outrank item targets, which outrank tiles.
- 2026-09-30: User runtime-confirmed Object + Ground Item Live Inspect output including Definition ID, world tile, Region and Chunk context.
- 2026-09-30: GFX/projectiles cannot reuse menu picking because their scene classes deliberately return false from `method8297(...)`. Live Inspect therefore uses their already-built renderer Model + transform and Matrix3's native `Model.method1376(...)` hit test only while F10 inspection is enabled.
- 2026-09-30: Manual GFX/projectile hover verification is deferred until safe pause/slow-time/freeze tooling exists; normal-speed transient visuals are not a reasonable acceptance gate.
- 2026-09-30: F8 is Live Inspect's contextual-open key for supported entity tools (Object -> Live Model Editor, NPC -> Dev Inspector). Tile Editor was later retired; tile targets remain read-only inspection context.
- 2026-09-30: Dev Time V1 is intentionally visual-only. The full client logic tick remains live because it owns networking/input/session work; only spot-animation/projectile advancement is paused/slowed/stepped.
- 2026-09-30: Projectile timelines are shifted forward on skipped visual ticks so real `client.cycles` can continue without causing frozen projectiles to expire.
- 2026-10-01: Dev tool save actions should converge on Ctrl+S where practical. Live Place now saves object-definition snapshots with Ctrl+S instead of a right-click save action; Object Library uses Ctrl+S for label edits.

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
- Existing Dev Spawn/Inspector and Item Browser remain functional; Tile Editor is intentionally retired.
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

- Phase 1 Bundle 1.4 Real Object Placement Preview: standardized saving on Ctrl+S, added draggable Object Library positioning, and split editable labels from real cache names while preserving existing saved entries.

**Current phase:**

- Phase 1 priority side-slice is `NEEDS TEST`; Phase 2 - Contextual Editors remains `ACTIVE` carryover rather than being reset.

**Active bundle:**

- Bundle 1.4 Real Object Placement Preview is `NEEDS TEST`.
- Bundle 2.5 Visual Dev Time V1 remains `NEEDS TEST` and is unchanged.

**Next checklist item:**

- One short Client runtime gate: Live Place -> Ctrl+S several IDs -> open Object Library -> drag it, label unnamed entries and Ctrl+S, reopen to verify persistence, then check Enter/Place, F8 editor handoff, Delete removal, and no Tile Editor route.

**Current state / next action:**

- Bundle 1.4 is statically implemented. Preserve all previous Visual Dev Time / Live Inspect runtime carryover after this focused placement check.

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

- Bundle 1.4 Real Object Placement Preview.
- Bundle 2.5 Visual Dev Time V1.
- Bundle 2.4 Live Inspect Relationships + Contextual Open.
- Bundle 2.3 Live Inspect GFX + Projectile Targets (now testable with Bundle 2.5 controls).
- Bundle 2.2 Tile/flicker final acceptance (Object + Ground Item are runtime-proven).
- Bundle 2.1 Live Inspect V1 final acceptance after polish retest.
- Bundle 1.2 World Manipulation.
- Bundle 1.3 Continuous / Paint Placement.
- Combined Phase 1 gate.

**Blockers:**

- Permanent map-object mutation only; does not block runtime Dev-owned manipulation or safe independent contextual-editor work.

**Important remaining uncertainty:**

- Persistent map/save authority for eventually committing world-editor changes.
- Runtime behavior of the AWT Escape observation path until the deferred test session.

## Next recommended work

Run one short combined acceptance session for Visual Dev Time V1 + the GFX/Projectile Live Inspect gate + F8 contextual routing. After that, decide whether to broaden time control to additional client animation families or design a separate guarded server/dev simulation clock.

### Live Model Editor — Restore Cache Original — 2026-10-01

- Status: **IMPLEMENTED / NEEDS RUNTIME TEST** under AAA.
- Problem: reopening/rebuilding the same object could preserve the current Live Model Editor part session, so object 46298 could reopen as the previously modified conveyor/sawmill assembly instead of the untouched cache source.
- Added **Object / Source -> Restore Original** as a separate destructive action from Reset Object and Rebuild Parts.
- Restore Original:
  - prompts before destructive reset;
  - resets whole-object editor controls to defaults and restores the preferred cache object type;
  - clears the in-memory LiveModelEditorParts source/session, including part transforms, hidden/deleted state, duplicates, replacements, conveyor-role tags, selection/isolate state and undo history;
  - immediately re-decodes the current object/type from Matrix3's cache-backed ObjectDefinitions/model source.
- Saved files under dev-model-projects and dev-model-assets are intentionally untouched and may be loaded again afterward.
- Rebuild Parts remains non-destructive for the current session; Reset Object remains the ordinary whole-object transform reset.
- No cache writing, object-definition mutation, world mutation, Construction transport ownership or persistent player state changed.
- **Resume Here (Live Model Editor):** edit object 46298, intentionally move/delete/duplicate several parts, click Restore Original and accept the warning. Verify the untouched sawmill returns, the part count/list is rebuilt, Undo cannot resurrect the discarded session, and a previously saved project can still be loaded afterward.

### Live Model Editor — Ctrl+Y Redo — 2026-10-01

- Status: **IMPLEMENTED / NEEDS RUNTIME TEST** under explicit SAP AAA.
- Reused the existing `LiveModelEditorParts` snapshot authority; no second editor-history system was introduced.
- Added a bounded redo stack paired with the existing 64-step undo history.
- `Ctrl+Z` now moves the current Part/Multi state onto redo before restoring the previous snapshot; `Ctrl+Y` moves forward through those undone states one edit at a time.
- Any new Part/Multi edit after an undo clears the redo branch, matching normal editor behavior.
- Source/session resets clear both undo and redo, so Restore Original cannot resurrect discarded authored state.
- Selection, duplicates, hidden/deleted state, replacements, conveyor roles, transforms and isolate state continue to travel through the existing snapshot format.
- No Construction Ctrl+Z path, cache writer, server/world state, or persistent settlement ownership changed.
- **Resume Here (Live Model Editor):** make three visible Part/Multi edits, press Ctrl+Z twice, Ctrl+Y twice, then undo once and make a new edit; verify Ctrl+Y reports Nothing to redo after the branch changes.

### Live Model Editor — Stable ROT Pivot + 90° Snap — 2026-10-01

- Status: **RUNTIME VERIFIED** under explicit AAA.
- Runtime video originally showed Part/Multi ROT visually orbiting/drifting away from the white rotation gizmo instead of rotating around one fixed pivot; the user has now confirmed the corrected pivot behavior and 90-degree snapping work at runtime.
- **verified-static root cause:** the gizmo, selection pivot and Multi rotation math use each component's cached bounds center, while `transformVertices(...)` recomputed an average-vertex centroid and rotated/scaled the visible mesh around that different point.
- Corrected `LiveModelEditorParts.transformVertices(...)` so yaw rotates the visible component around the same cached bounds center already owned by the editor gizmo and shared Multi transform math. Existing per-part scale still uses its prior average-vertex centroid, preserving saved scale placement at yaw=0.
- Reused the existing transform snap system; rotation angle snap now defaults to **90 degrees** and the UI identifies the control as ROT SNAP.
- SNAP behavior is unchanged otherwise: SNAP ON applies the configured move/rotation steps; Ctrl temporarily bypasses it. With SNAP OFF, Ctrl temporarily applies snap.
- No new transform/history system, Construction undo owner, cache writer, world mutation or server persistence path was introduced.
- **Runtime acceptance:** stable ROT pivot + 90-degree snap are accepted. Optional deeper checks remain for Ctrl snap/free inversion and Ctrl+Z/Ctrl+Y around snapped rotations.
